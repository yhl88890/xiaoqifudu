package com.xuanji.repeater

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.min

/**
 * B站视频导入编排：把用户粘贴的 B站 链接变成小七复读机可直接播放的本地文件 + 双语字幕。
 *
 * 流程（参考 bilibili-english-app 的逻辑）：
 *  1) 有 B站 CC 英文字幕 → 直接用，并就近对齐中文字幕轨得到双语；
 *  2) 没有 CC → 下载音频 → 转 WAV(16k 单声道) → 云端 ASR(TeleSpeech) 逐句识别；
 *  3) 字幕中文翻译：有 SiliconFlow Key 时用 chat 模型（主模型排队/限流自动切 HY3 限时免费版），
 *     否则退回免费翻译通道（逐句）。
 *  4) 下载视频+音频流，用 MediaMuxer 合成单个 MP4（无视频则音频-only MP4），与字幕同目录。
 *  5) 产出 BiliImportResult，由 UI 打开（视频模式；纯音频兜底进音频模式）。
 */
object BiliImport {

    data class Result(
        val mediaFile: File,     // 合成后的 MP4（视频或音频-only）
        val srtFile: File?,      // 双语 SRT（可能为空：无字幕且未翻译）
        val title: String,
        val videoMode: Boolean,  // true=有画面（视频模式）；false=纯音频
        val fromCc: Boolean,     // 字幕是否来自 B站 CC
        val note: String = "",
    )

    suspend fun import(
        context: Context,
        inputUrl: String,
        translate: Boolean,
        onProgress: (String) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        val cookie = AsrPrefs.loadBiliSessdata(context).ifBlank { null }

        // 1) 标题 + B站 自带字幕轨
        // 🔴【0929】"CC 字幕"这个叫法不准确：B站 的轨不只有 UP 主上传的人工字幕，
        //   还有 `ai-zh` 这种 B站 AI 听译生成的（ai_type=1）。现在**有任何轨就拿**，
        //   不再要求必须是英文轨（原实现 pickLan("en") 把只有 ai-zh 的视频误判成"没字幕"）。
        onProgress("解析视频信息…")
        var title: String
        var ccLines: List<SubLine>? = null
        var ccError: String? = null
        try {
            val (t, lines) = BiliClient.fetchTimedSubtitle(inputUrl, cookie)
            title = t
            ccLines = lines
            onProgress("已取到 B站字幕轨（${lines.size} 句）")
        } catch (e: Exception) {
            ccError = e.message
            title = runCatching { BiliClient.fetchTitle(inputUrl, cookie) }.getOrDefault("B站视频")
            // 让失败原因看得见（原先只在末尾 note 里体现，用户不知道为啥在跑识别）
            onProgress("无自带字幕，改用语音识别")
        }

        // 2) 播放地址
        onProgress("获取播放地址…")
        val play = BiliClient.fetchPlayUrl(inputUrl, cookie)

        // 3) 输出目录（Download/XuanjiBili，媒体库会扫到；同时支持 file:// 直接打开）
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "XuanjiBili")
        dir.mkdirs()
        val safe = safeName(title)
        val mp4File = File(dir, "$safe.mp4")
        val audioFallback = File(dir, "$safe.m4a")

        // 4) 字幕来源：B站 字幕轨直接用；没轨才跑 ASR
        //    【v62】ASR 分支新增「借 B站 轨的时间边界重切」——见下面 recutByBiliRuler 的说明。
        val subLines: List<SubLine> = if (ccLines != null) {
            ccLines
        } else {
            onProgress("无自带字幕，下载音频做语音识别…")
            val key = AsrPrefs.loadKey(context)
            if (key.isBlank()) throw RuntimeException("未配置识别 Key：去设置页填写 ASR Key 才能自动识别无字幕视频。")
            val audioTmp = File(dir, "$safe._audio.m4a")
            BiliClient.downloadAudio(inputUrl, cookie, audioTmp)
            onProgress("转换音频…")
            val wav = WavConverter.convertToWav(audioTmp, dir) { onProgress("转换音频 ${(it * 100).toInt()}%") }
            audioTmp.delete()
            // 先定服务商：切片大小要按它给（切出来的每片必须能过 transcribe 的上传闸门）
            val provider = AsrProvider.fromName(AsrPrefs.loadProvider(context))
            val chunks = WavConverter.splitWavIfNeeded(
                wav, File(dir, "chunks"), SpeechRecognizer.maxUploadBytes(provider),
            )
            val merged = mutableListOf<AsrSegment>()
            var offsetMs = 0L
            for (c in chunks) {
                val out = SpeechRecognizer.transcribe(c.absolutePath, provider, key)
                    ?: throw RuntimeException("语音识别失败（网络/超时/文件超限），请稍后重试。")
                when (out) {
                    is AsrOutcome.Timed -> out.segments.forEach {
                        merged.add(AsrSegment(offsetMs + it.startMs, offsetMs + it.endMs, it.text.trim()))
                    }
                    is AsrOutcome.Plain -> { /* 无时间戳：跳过（少数字幕） */ }
                }
                offsetMs += wavChunkMs(c)
            }
            if (merged.isEmpty()) throw RuntimeException("识别未返回时间戳，无法生成字幕（可换有 B站字幕的视频，或稍后重试）。")
            recutByBiliRuler(context, inputUrl, merged, onProgress)
        }

        // 5) 中文翻译（B站 轨已给中文的段**保留**，只补缺的 —— 阿亮 0929 定：B站 中文优先）
        val needTranslate = translate && subLines.any { it.cn.isBlank() }
        val finalLines = if (needTranslate) {
            val enTexts = subLines.map { if (it.cn.isBlank()) it.text else "" }   // 已有中文的段不重复翻
            val key = AsrPrefs.loadKey(context)
            val zh = if (key.isNotBlank()) {
                // 【v44】翻译跟着「识别服务商」走（地址 + 模型都由它推出来）
                val provider = AsrProvider.current(context)
                SubtitleTranslator.translateBatch(
                    enTexts, key, provider, AsrPrefs.loadTranslateModel(context, provider),
                )
            } else emptyList()
            subLines.mapIndexed { i, line ->
                if (line.cn.isNotBlank()) line          // B站 给的，原样保留
                else line.copy(
                    cn = if (i < zh.size && zh[i].isNotBlank()) zh[i]
                    else (Translator.translate(line.text) ?: ""),
                )
            }
        } else subLines

        // 6) 写 SRT（双语：英文行 + 中文行）
        val srtFile = File(dir, "$safe.srt")
        srtFile.writeText(buildSrt(finalLines))

        // 7) 下载并合成视频（或纯音频）
        val (mediaFile, videoMode) = if (play.hasVideo) {
            onProgress("下载视频…")
            val vTmp = File(dir, "$safe._v.mp4")
            val aTmp = File(dir, "$safe._a.tmp")
            try {
                BiliClient.downloadVideoStream(inputUrl, cookie, vTmp)
                if (play.audioUrl.isNotBlank()) BiliClient.downloadUrlTo(play.audioUrl, aTmp)
                onProgress("合成视频…")
                if (Mp4Muxer.mux(vTmp, aTmp, mp4File)) {
                    mp4File to true
                } else {
                    // 视频合成失败 → 退回纯音频（尽量封装成音频-only MP4，统一进视频模式）
                    onProgress("视频合成失败，改用纯音频")
                    BiliClient.downloadAudio(inputUrl, cookie, audioFallback)
                    if (Mp4Muxer.mux(null, audioFallback, mp4File)) mp4File to true else audioFallback to false
                }
            } catch (e: Exception) {
                onProgress("视频下载失败，改用纯音频")
                BiliClient.downloadAudio(inputUrl, cookie, audioFallback)
                if (Mp4Muxer.mux(null, audioFallback, mp4File)) mp4File to true else audioFallback to false
            } finally {
                vTmp.delete(); aTmp.delete()
            }
        } else {
            onProgress("下载音频…")
            BiliClient.downloadAudio(inputUrl, cookie, audioFallback)
            onProgress("封装音频…")
            if (Mp4Muxer.mux(null, audioFallback, mp4File)) mp4File to true else audioFallback to false
        }

        // 8) 让媒体库扫到（文件夹页以后也能看到）
        MediaScannerConnection.scanFile(context, arrayOf(mediaFile.absolutePath), null, null)

        val note = buildString {
            if (!videoMode) append("（无画面，已用纯音频模式）")
            if (ccLines == null) append("；字幕由语音识别生成")
            if (ccError != null && ccLines != null) { /* CC 成功，忽略 */ }
        }
        Result(mediaFile, srtFile, title, videoMode, ccLines != null, note)
    }

    // ---------- 工具 ----------

    /**
     * 借 B站 字幕轨的**时间边界**重切 ASR 长句（v62，方案①）。
     *
     * ## 为什么
     *
     * 走到这个分支说明 B站 没给出可用的**正文轨**（没轨 / 只有中文轨但我们要英文原文）。但
     * `ai-zh` 这种 AI 听译轨的**时间轴本身极细**（实测 365 条 / 927 秒 = 平均 2.54s/条），
     * 这些是 B站 ASR **真听出来的语音停顿点**，比我们自己按标点猜准得多。
     *
     * 拿它当尺子切我们 ASR 出来的英文长句 ⇒ 既保住英文原文，又拿到细断句。
     *
     * ## 行为
     *
     * 1. 试着取 B站 轨（含只有中文轨的情况，只要 `from` 时间）；取不到就**静默跳过**——退化成原来的长句，不报错。
     * 2. 用 [TimelineRecut] 按尺子切。
     * 3. 顺带把中文轨的文本按时间**对号入座**填进每段的 `cn`，这样后面就不用再花钱翻译。
     */
    private suspend fun recutByBiliRuler(
        context: Context,
        inputUrl: String,
        merged: List<AsrSegment>,
        onProgress: (String) -> Unit,
    ): List<SubLine> {
        val raw = merged.map { SubLine(it.text, it.startMs / 1000.0, it.endMs / 1000.0) }
        // 尺子和中文对照都从同一批轨里来；失败不影响主流程（只是断句保持原样）
        // 🔴【v63】用带诊断的版本：原来一律静默 return raw，SESSDATA 过期了用户也看不出来。
        val fetched = BiliClient.fetchAllTracksDetailed(context, inputUrl)
        val tracks = fetched.tracks
        if (tracks.isEmpty()) {
            // 有原因就照实说（SESSDATA 失效 / 该视频没轨），没有就只说识别完成
            if (fetched.note.isNotBlank()) onProgress("识别完成 ${raw.size} 句（未细切：${fetched.note}）")
            else onProgress("识别完成 ${raw.size} 句")
            return raw
        }

        val ruler = tracks.flatMap { it.lines }.map { (it.start * 1000).toLong() }
        if (ruler.isEmpty()) return raw

        val sentences = raw.map { Sentence((it.start * 1000).toLong(), (it.end * 1000).toLong(), it.text) }
        val cut = TimelineRecut.recut(sentences, ruler)
        if (cut.size == raw.size) {
            onProgress("识别完成 ${raw.size} 句")
            return raw
        }
        onProgress("按 B站 时间轴细切：${raw.size} → ${cut.size} 句")

        // 中文对照：B站 轨里属于中文的那些行，按时间就近配对
        val cnLines = tracks.filter { it.isChinese }.flatMap { it.lines }
        return cut.map { s ->
            val mid = (s.startMs + s.endMs) / 2000.0
            val cn = if (cnLines.isEmpty()) "" else {
                cnLines.minByOrNull { abs((it.start + it.end) / 2 - mid) }
                    ?.takeIf { abs((it.start + it.end) / 2 - mid) <= 4.0 }
                    ?.text ?: ""
            }
            SubLine(s.text, s.startMs / 1000.0, s.endMs / 1000.0, cn)
        }.also { if (it.any { l -> l.cn.isNotBlank() }) onProgress("已带上 B站 中文对照") }
    }

    private fun safeName(title: String): String {
        val base = title.trim().replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_")
            .replace(Regex("\\s+"), " ").trim()
        return if (base.isEmpty()) "bili_${System.currentTimeMillis()}" else base.take(80)
    }

    private fun wavChunkMs(wav: File): Long {
        // WAV 16k 单声道：32000 字节/秒
        val dataBytes = (wav.length() - 44).coerceAtLeast(0)
        return (dataBytes * 1000L) / (16000L * 2L)
    }

    private fun buildSrt(lines: List<SubLine>): String {
        val sb = StringBuilder()
        lines.forEachIndexed { i, l ->
            val start = msToSrt((l.start * 1000).toLong())
            val end = msToSrt((l.end * 1000).toLong())
            sb.append("${i + 1}\n")
            sb.append("$start --> $end\n")
            sb.append(l.text.trim())
            if (l.cn.isNotBlank()) sb.append("\n").append(l.cn.trim())
            sb.append("\n\n")
        }
        return sb.toString()
    }

    private fun msToSrt(ms: Long): String {
        val c = ms.coerceAtLeast(0L)
        val h = c / 3_600_000
        val m = (c % 3_600_000) / 60_000
        val s = (c % 60_000) / 1000
        val f = c % 1000
        return "%02d:%02d:%02d,%03d".format(h, m, s, f)
    }
}
