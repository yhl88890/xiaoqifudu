package com.xuanji.repeater

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
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

    /**
     * 【v66】B站 轨「平均段长超过多少毫秒就算太粗、宁可不用户」。
     *
     * 定这个数的依据（2026-09-29 实测）：
     *  · 我们要的是**能一按复读就念完**的一句 —— 经验上 6 秒以内舒服，8 秒以上就嫌长。
     *  · B站 自带 `en` 正文轨实测 **8.4s/条**（BV1Gf4y1y7wc）；`ai-zh` 听译轨 **2.54s/条**。
     *  · 所以 6000ms 这条线能把"粗正文轨"挡掉、把"细听译轨"放进来。
     *  ⚠️ 这个值是拍的，真用起来嫌松/嫌紧就调它（只有这一个地方用）。
     */
    private const val CC_TOO_COARSE_MS = 6000.0

    /** 一列字幕的平均段长（毫秒）。空表返回 0（⇒ 不会被判成"太粗"）。 */
    private fun avgSegMs(lines: List<SubLine>): Double {
        if (lines.isEmpty()) return 0.0
        val total = lines.sumOf { (it.end - it.start).coerceAtLeast(0.0) }
        return total / lines.size * 1000.0
    }

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
        // 🔴【v72】SESSDATA 在设置页存的是**纯值**，这里必须走规范化补上 `SESSDATA=` 前缀
        //   （v71 诊断实锤：少了这个前缀 → B站 nav 返回 -101 → 字幕轨永远取不到）
        val cookie = BiliClient.buildCookie(AsrPrefs.loadBiliSessdata(context))

        // 输出目录先建好（取轨失败时也要能往里写诊断文件）
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "XuanjiBili")
        dir.mkdirs()

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
            runCatching {
                File(dir, "_debug_取轨成功.txt").writeText(
                    "cookie 长度  ${cookie?.length ?: 0}\n轨句数      ${lines.size}\n"
                )
            }
        } catch (e: Exception) {
            ccError = e.message
            title = runCatching { BiliClient.fetchTitle(inputUrl, cookie) }.getOrDefault("B站视频")
            // 让失败原因看得见（原先只在末尾 note 里体现，用户不知道为啥在跑识别）
            onProgress("无自带字幕，改用语音识别")
            // 🔴【v68 诊断】把**取轨失败的原始原因**落盘，否则只看 SESSDATA 长度根本猜不出来。
            //   ⚠️ 文件名跟下面判据那个**必须不同** —— 否则判据那次会把这里的覆盖掉（踩过）。
            runCatching {
                File(dir, "_debug_取轨失败.txt").writeText(
                    buildString {
                        appendLine("cookie 长度  ${cookie?.length ?: 0}")
                        appendLine("异常类型    ${e::class.simpleName}")
                        appendLine("异常消息    ${e.message}")
                        appendLine("异常链路    ${e.stackTrace.take(4).joinToString(" < ")}")
                    }
                )
            }
        }

        // 2) 播放地址
        onProgress("获取播放地址…")
        val play = BiliClient.fetchPlayUrl(inputUrl, cookie)

        // 3) 输出目录（Download/XuanjiBili，媒体库会扫到；同时支持 file:// 直接打开）
        val safe = safeName(title)
        val mp4File = File(dir, "$safe.mp4")
        val audioFallback = File(dir, "$safe.m4a")

        // 4) 字幕来源：B站 字幕轨直接用；没轨才跑 ASR
        //    【v62】ASR 分支新增「借 B站 轨的时间边界重切」——见下面 recutByBiliRuler 的说明。
        //
        //    🔴【v66 阿亮拍板"乙方案"】轨**太粗就别用**。
        //
        //    真机实锤（BV1Gf4y1y7wc / TED-Ed 原子，2026-09-29）：
        //      B站 自带 `en` 轨 **27 条 / 226 秒 = 平均 8.4s 一条**，
        //      最长一条 16.6 秒 —— **比我们自己 ASR 的段还粗**。
        //      而 v62~v65 一直在第 2 层（`recutByBiliRuler`）做文章，
        //      第 1 层 `ccLines != null` 一短路就把第 2 层整个绕过去了 ⇒ 白改。
        //
        //    所以判据挪到**这里**：算出平均段长，超过 [CC_TOO_COARSE_MS] 就**主动放弃**轨，
        //    掉进 ASR 分支 → 走 `recutByBiliRuler`（v65 的细切逻辑这下就活了）。
        //    ⚠️ 轨只是"粗"，文本质量往往比 ASR 好 ⇒ 放弃前把它的文本留一份，
        //       等 ASR 出完按时间对齐，能救回来就用轨的文本（见下面 mergeCoarseTrackText）。
        val ccTooCoarse = ccLines != null && avgSegMs(ccLines) > CC_TOO_COARSE_MS
        if (ccTooCoarse && ccLines != null) {
            onProgress("B站 轨太粗（平均 ${(avgSegMs(ccLines) / 1000).toInt()}s/句），改用语音识别…")
        }
        // 🔴【诊断】把决策依据落到文件，真机排查用（`Download/XuanjiBili/_debug_字幕来源.txt`）。
        runCatching {
            File(dir, "_debug_字幕来源.txt").writeText(
                buildString {
                    appendLine("轨句数      ${ccLines?.size ?: -1}")
                    appendLine("轨平均段长  ${if (ccLines != null) "%.0f".format(avgSegMs(ccLines)) else "-"} ms")
                    appendLine("阈值        $CC_TOO_COARSE_MS ms")
                    appendLine("判定        ${if (ccLines == null) "无轨→ASR" else if (ccTooCoarse) "太粗→ASR" else "够细→直接用轨"}")
                }
            )
        }
        val subLines: List<SubLine> = if (ccLines != null && !ccTooCoarse) {
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
            // 细切：优先借 B站 细轨当尺子；没细轨就纯用 ASR 自己的段。
            val cut = recutByBiliRuler(context, inputUrl, merged, onProgress)
            // 【v66】轨"粗"但文本准 ⇒ 拿它当**文字稿**，按细切后的时间轴重新分配文本。
            //   效果：时间轴是 ASR 的细粒度，文字是 B站 的准确原文（专有名词/标点更对）。
            if (ccLines != null && ccTooCoarse) reseatTextByTimeline(cut, ccLines, onProgress) else cut
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

        val cnLines = tracks.filter { it.isChinese }.flatMap { it.lines }

        // ============ 【v66 改】挑「够细的英文轨」直接采用它的断句 ============
        // v65 的无条件「有英文轨就用」在乙方案下**会自相矛盾**：粗轨刚在上层被判定弃用，
        // 这里又整盘拿回来 ⇒ 白忙一场。所以补一道**细度门槛**：轨本身够细才直接采用。
        //   ✅ 细轨（如 `ai-zh` 听译轨，实测 2.54s/条）：文本和时间天然对齐，直接用它最好。
        //   ❌ 粗轨（`en` 正文轨，实测 8.4s/条）：交给"ASR 给时间轴 + 轨给文字"那条路（见下）。
        val enTrack = tracks.filter { it.isEnglish }
            .maxByOrNull { it.lines.size }
            ?.takeIf { t -> avgSegMs(t.lines.map { SubLine(it.text, it.start, it.end) }) <= CC_TOO_COARSE_MS }
        if (enTrack != null && enTrack.lines.isNotEmpty()) {
            val out = enTrack.lines
                .filter { it.text.isNotBlank() }
                .map { l ->
                    SubLine(
                        text = l.text,
                        start = l.start,
                        end = l.end,
                        cn = matchCn(cnLines, l.start, l.end),
                    )
                }
            if (out.isNotEmpty()) {
                onProgress("采用 B站 英文轨断句：${raw.size} → ${out.size} 句")
                if (out.any { it.cn.isNotBlank() }) onProgress("已带上 B站 中文对照")
                return out
            }
        }

        // ============ 兜底：用 B站 轨的时间点硬切我们的 ASR 文本 ============
        // 🔴【v66】尺子取**最细的那条轨**（逐条算平均段长，取最小者），不再把所有轨混在一起。
        //   理由：粗轨的时间点又稀又不准，混进来会把细轨的好点稀释掉。
        val rulerTrack = tracks
            .filter { it.lines.size >= 2 }
            .minByOrNull { avgSegMs(it.lines.map { l -> SubLine(l.text, l.start, l.end) }) }
        val ruler = rulerTrack?.lines?.map { (it.start * 1000).toLong() } ?: emptyList()
        if (ruler.isEmpty()) return raw

        val sentences = raw.map { Sentence((it.start * 1000).toLong(), (it.end * 1000).toLong(), it.text) }
        val cut = TimelineRecut.recut(sentences, ruler)
        if (cut.size == raw.size) {
            onProgress("识别完成 ${raw.size} 句")
            return raw
        }
        onProgress("按 B站 时间轴细切：${raw.size} → ${cut.size} 句")

        return cut.map { s ->
            SubLine(s.text, s.startMs / 1000.0, s.endMs / 1000.0, matchCn(cnLines, s.startMs / 1000.0, s.endMs / 1000.0))
        }.also { if (it.any { l -> l.cn.isNotBlank() }) onProgress("已带上 B站 中文对照") }
    }

    /**
     * 【v66】把 B站 轨的**准确文字**重新铺到细切后的时间轴上。
     *
     * ## 为什么要这一步
     *
     * 到这儿我们手上有两份东西，各有各的好：
     *  · 细切后的 [cut] —— **时间轴细**（ASR 真听出来的边界，2~3s 一段），
     *    但文字是 ASR 出来的，专有名词常错（实测把 `atom` 听成 `adams`）。
     *  · B站 轨 [trackLines] —— **文字准**（专有名词/标点都对），
     *    但时间轴粗（一条 8 秒，一按复读念不完）。
     *
     * ⇒ 取长补短：**时间轴用细的，文字用准的**。
     *
     * ## 怎么做（关键：不"猜"字符比例，而是"按已确定的语义单元内部分配"）
     *
     * 粗轨的一条 = 一个**语义完整的单元**（比如「Every atom has a core which is made up of
     * at least one positively charged particle called a proton」）。
     * 细切把它切成 3 段时，我们**只需在这**一条内部按时间比例分配字符 ——
     * 这不是早先 `cutTextAtWord` 那种"在整篇里瞎猜切点"，**边界已经被细切定死了**，
     * 分配只是在已知单元内做插值，风险小得多。
     *
     * ## 保守策略（宁可用 ASR 的，也不给错文字）
     *
     * 逐条细段去粗轨找**时间重叠最多**的那一条：
     *  · 重叠率 ≥ [MIN_OVERLAP] 才敢换文字；否则**保留 ASR 原文**（不动）。
     *  · 换来的文字如果**为空或异常短**（< 2 字符），也**放弃**、保留原文。
     * ⇒ 最坏情况就是"这段没换成，还是 ASR 的文字"，不会比现在更差。
     */
    private fun reseatTextByTimeline(
        cut: List<SubLine>,
        trackLines: List<SubLine>,
        onProgress: (String) -> Unit,
    ): List<SubLine> {
        if (cut.isEmpty() || trackLines.isEmpty()) return cut

        var replaced = 0
        val out = cut.map { seg ->
            // 找跟本段时间重叠最多的那条轨
            var best: SubLine? = null
            var bestOverlap = 0.0
            for (t in trackLines) {
                val ov = min(seg.end, t.end) - max(seg.start, t.start)
                if (ov > bestOverlap) {
                    bestOverlap = ov
                    best = t
                }
            }
            val t = best
            val segLen = (seg.end - seg.start).coerceAtLeast(0.001)
            if (t == null || bestOverlap / segLen < MIN_OVERLAP) return@map seg

            // 在本条轨内部按时间比例切出对应的一段字符
            val tLen = (t.end - t.start).coerceAtLeast(0.001)
            val lo = ((seg.start - t.start) / tLen).coerceIn(0.0, 1.0)
            val hi = ((seg.end - t.start) / tLen).coerceIn(0.0, 1.0)
            val text = t.text
            val a = (text.length * lo).toInt().coerceIn(0, text.length)
            val b = (text.length * hi).toInt().coerceIn(0, text.length)
            val piece = text.substring(a, b).trim()

            // 太短/为空 ⇒ 放弃（宁可留 ASR 原文，也别给一句残渣）
            if (piece.length < 2) return@map seg
            replaced++
            seg.copy(text = piece)
        }
        if (replaced > 0) onProgress("用 B站 原文校正了 $replaced 句文字")
        return out
    }

    /** 细段与轨的最小时间重叠率：低于它就**不换文字**，保留 ASR 原文。 */
    private const val MIN_OVERLAP = 0.5

    /** 在中文轨里按时间中点就近找对照（±4 秒内才算）。 */
    private fun matchCn(cnLines: List<SubLine>, start: Double, end: Double): String {
        if (cnLines.isEmpty()) return ""
        val mid = (start + end) / 2.0
        var best: SubLine? = null
        var bestGap = Double.MAX_VALUE
        for (l in cnLines) {
            val gap = abs((l.start + l.end) / 2.0 - mid)
            if (gap < bestGap) {
                bestGap = gap
                best = l
            }
        }
        return if (best != null && bestGap <= 4.0) best.text else ""
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
