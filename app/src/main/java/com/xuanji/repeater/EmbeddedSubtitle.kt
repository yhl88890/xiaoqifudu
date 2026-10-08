package com.xuanji.repeater

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mkv.MatroskaExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.security.MessageDigest

/**
 * 内封字幕（软字幕轨）提取。【v120 新建】
 *
 * ## 阿亮要什么
 *
 * "我从任何地方下的片子，只要它里头自带字幕，往手机里一丢就自动出字幕 —— 不用上电脑，
 *  也不管它旁边有没有 .srt。"
 *
 * 之前 App 只认**外挂字幕文件**（[SubtitleParser.findBestSubtitle]）；MKV/MP4 里内封的字幕轨
 * 一律看不见。这个文件就是把那些轨道读出来。
 *
 * ## 先说清楚：什么能扒，什么扒不了
 *
 * - **软字幕（内封）= 独立轨道** ⇒ 能扒。本文档全程只干这件事。
 * - **硬字幕（烧录/压制）= 已经画进画面像素** ⇒ 扒不出来（等于要扒画面）。那种只能走 ASR。
 *   分辨法：播放器里**有字幕开关 / 能切字幕** = 软字幕；**永远在、没得关** = 硬字幕。
 * - 图片类字幕（蓝光原盘的 PGS / DVD 的 VobSub）**也是独立轨道，但它存的是图片**，
 *   没有文本可读 ⇒ [isTextMime] 里直接排除。
 *
 * ## 🔴🔴 为什么不用系统自带的 MediaExtractor 读字幕（1008 实机实测的血泪）
 *
 * 一开始我用的是 `android.media.MediaExtractor`（跟 [VideoAudioExtractor] 同一套路）。
 * 在小米 Note 12T Pro / Android 15 上实测，**它对 MKV 只能报出 video 一条轨**：
 * ```
 * track 0 mime=video/avc
 * tracks: 0 条文字轨 / 共 1 轨      ← 音频轨、5 条字幕轨全都不见了
 * ```
 * 拿只有「视频+音频」的 `eac3test.mkv` 对照也一样（只报 video/hevc）。这不是字幕特有的毛病，
 * 是**这台设备上框架的 MKV 解析器会静默丢掉不认识的轨** —— 跟 v119 查出来的
 * 「MediaPlayer 的 MKV 解析器不认 AC3/EAC3 ⇒ MKV 必哑」**是同一个根因**。
 *
 * ⇒ 改用 Media3（ExoPlayer 那套）的 `MatroskaExtractor`，v119 已经因为它才能播杜比音轨。
 *   MKV/WebM 走 Media3；其它容器（MP4 的 tx3g 之类）仍走系统 MediaExtractor（那边没问题）。
 *
 * ### 为什么用 `FLAG_EMIT_RAW_SUBTITLE_DATA`
 *
 * 翻字节码确认过它的语义（不是猜的）：`parseSubtitlesDuringExtraction = (flags & 2) == 0`，
 * 置位后 `init()` **根本不创建** `SubtitleTranscodingExtractorOutput` ⇒ 不做解析、**不校验
 * `supportsFormat`**、原始 payload 原样喂给 TrackOutput。正是我们要的：
 * 只拿"生字幕"，清洗/合并/丢垃圾样式全部交给 [SubtitleParser]（踩过坑的唯一真值）。
 * ⚠️ 它必须与"允许 seek for cues"并存（Media3 里有硬校验），所以要处理 `RESULT_SEEK`。
 *
 * ## 实测数据（1008 阿亮那批《老友记》，两条真实轨道长这样）
 *
 * ```
 * ASS 轨（mime text/x-ssa）：ReadOrder,Layer,Style,Name,MarginL,MarginR,MarginV,Effect,Text
 *   0,0,chs,NTP,0000,0000,0000,,{\fn…\blur3}那真的很棒{\r}\N{\fn…}- Okay.{\r}
 *   ↑ 文本前正好 8 个逗号字段；中英双语在**同一条** payload 里、用字面 `\N` 分隔
 * SRT 轨（mime application/x-subrip）：payload 就是**纯文本**，没序号也没时间戳
 * ```
 *
 * ⇒ ASS 轨把「前 8 个逗号字段」当壳剥掉；SRT 轨**自己补序号和时间戳**；两条路最后都合到
 *   同一份**合成 ASS 文本**上，交给 [SubtitleParser.parseAss]。
 *
 * ## 🔴 铁律：扒不到必须**静默退回**
 *
 * 用户下载的视频千奇百怪（zlib 压缩的字幕轨、没索引的 MKV、加密流…）。任何一步失败都返回 null，
 * **一个字都不提示**，界面跟以前一模一样（"没有字幕，点这里选一份"）。
 * 绝不能因为"想加个新功能"把原本能用的场景搞坏 —— 这就是本文件所有 `return null` 的用意。
 */
internal object EmbeddedSubtitle {

    private const val TAG = "XJ_EMBED"

    /** 界面上标来源用：扒出来的字幕显示成「内封·中上英下ASS」，一眼看出来自视频内部 */
    const val TAG_PREFIX = "内封·"

    private const val CACHE_DIR = "embedded_sub"

    /**
     * 单轨最多读多少条样本（保护，不是功能）。
     * 一集 45 分钟 ≈ 800 条；两万条已经远超任何正常片源，真到这一步说明读错轨了。
     */
    private const val MAX_SAMPLES = 20000

    /** extractor 的 read() 循环上限（每个 block 返回一次，一部剧约十万次；这是防死循环的兜底） */
    private const val MAX_READ_LOOPS = 3_000_000

    /** 单条样本缓冲 1MB（字幕样本都是几 KB）。tx3g 走框架 extractor 时必须用**直接缓冲**。 */
    private const val MAX_SAMPLE_BYTES = 1 shl 20

    /**
     * 一句话最长显示多久。
     * MKV 的时间轴里我们只能拿到**起点**（终点在 Block Duration 里）⇒ 用「下一条的起点」当终点。
     * 中间要是空了半分钟，上一句就会一直高亮到下一句出现 —— 跟读场景里这是实打实的干扰，夹个上限。
     */
    private const val MAX_CUE_MS = 10_000L

    /** MKV/WebM 的魔数（EBML 头四个字节） */
    private val EBML_MAGIC = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())

    // ==================================================================
    // 对外数据结构
    // ==================================================================

    /**
     * 一条文字字幕轨。
     * @param index 轨号。**MKV 走 Media3 时是 Media3 的 trackId；其它容器走系统 extractor 时是轨道下标**。
     *   同一文件里引擎固定，所以这个号原样传回去一定能对上。
     * @param title 轨道名，如「简英双字 - 47BT.COM」「中上英下ASS」；可能为空
     * @param language 语言标签，如 `chi`/`eng`；可能为空
     */
    data class Track(
        val index: Int,
        val mime: String,
        val title: String,
        val language: String,
    ) {
        /** 给用户看的名字：轨道名 > 语言名 > "字幕轨 N" */
        val label: String
            get() = title.substringBefore(" - ").trim()
                .ifBlank { languageName(language) }
                .ifBlank { "字幕轨 ${index + 1}" }
    }

    // ==================================================================
    // ① 枚举轨道
    // ==================================================================

    /** 列出视频里所有**文字类**字幕轨。读不出来返回空表（调用方按"没有内封字幕"处理）。 */
    fun tracks(context: Context, uri: Uri): List<Track> =
        if (isMatroska(context, uri)) mkvTracks(context, uri) else sysTracks(context, uri)

    /** 看文件头 4 字节判断是不是 MKV/WebM（决定用哪套解析器） */
    private fun isMatroska(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.openInputStream(uri)?.use { ins ->
            val b = ByteArray(4)
            ins.read(b) == 4 && b.contentEquals(EBML_MAGIC)
        } ?: false
    } catch (_: Throwable) {
        false
    }

    // ---------- MKV / WebM：Media3 MatroskaExtractor ----------

    private fun mkvTracks(context: Context, uri: Uri): List<Track> {
        // 只跑到 tracks 声明结束就停（不读整片）—— 轨道名/语言/编码在这一步就齐了
        val out = runMkv(context, uri, wantTrack = -1, stopAtTracks = true) ?: return emptyList()
        val list = ArrayList<Track>()
        for ((id, sink) in out.sinks) {
            val f = sink.format ?: continue
            val mime = f.sampleMimeType.orEmpty()
            val isText = sink.trackType == C.TRACK_TYPE_TEXT || isTextMime(mime)
            Log.d(TAG, "mkv track id=$id type=${sink.trackType} mime=$mime label=${f.label} lang=${f.language}")
            if (!isText) continue
            list.add(Track(id, mime, f.label.orEmpty(), f.language.orEmpty()))
        }
        Log.d(TAG, "mkv 共 ${out.sinks.size} 轨，其中文字轨 ${list.size} 条")
        return list
    }

    // ---------- 其它容器（MP4 的 tx3g 等）：系统 MediaExtractor ----------

    private fun sysTracks(context: Context, uri: Uri): List<Track> {
        val ex = MediaExtractor()
        return try {
            ex.setDataSource(context, uri, null)
            val out = ArrayList<Track>()
            for (i in 0 until ex.trackCount) {
                val f = runCatching { ex.getTrackFormat(i) }.getOrNull() ?: continue
                val mime = meta(f, MediaFormat.KEY_MIME)
                Log.d(TAG, "sys track $i mime=$mime title=${meta(f, "title")} lang=${meta(f, MediaFormat.KEY_LANGUAGE)}")
                if (!isTextMime(mime)) continue
                out.add(Track(i, mime, meta(f, "title"), meta(f, MediaFormat.KEY_LANGUAGE)))
            }
            Log.d(TAG, "sys 共 ${ex.trackCount} 轨，其中文字轨 ${out.size} 条")
            out
        } catch (t: Throwable) {
            Log.e(TAG, "sysTracks failed", t)
            emptyList()
        } finally {
            runCatching { ex.release() }
        }
    }

    /**
     * 文字类字幕的 mime 判定。
     * ⚠️ 各版本 Android / 各封装格式报的 mime 不统一（`text/x-ssa`、`application/x-subrip`、
     * `application/x-subtitle`、`application/x-quicktime-tx3g`…），所以用一个宽松白名单；
     * 但**图片类必须先排除**，否则会把 PGS 当文本轨去读、读出一堆乱码。
     */
    private fun isTextMime(mime: String): Boolean {
        val m = mime.lowercase()
        if (m.isEmpty()) return false
        if (m.contains("subpicture") || m.contains("pgs") || m.contains("vobsub") ||
            m.contains("dvbsub") || m.contains("hdmv") || m.contains("x-media3-cues")
        ) return false
        return m.startsWith("text/") || m.contains("subrip") || m.contains("subtitle") ||
                m.contains("ssa") || m.contains("tx3g") || m.contains("3gpp") || m.contains("srt")
    }

    private fun meta(f: MediaFormat, key: String): String =
        runCatching { if (f.containsKey(key)) f.getString(key).orEmpty() else "" }.getOrDefault("")

    private fun languageName(lang: String): String = when (lang.lowercase()) {
        "chi", "zho", "zh", "chs", "cht" -> "中文"
        "eng", "en" -> "英文"
        "jpn", "ja" -> "日文"
        "kor", "ko" -> "韩文"
        else -> lang
    }

    // ==================================================================
    // ② 自动挑一条（多轨时不能全塞，会重叠打架）
    // ==================================================================

    /**
     * 自动挑一条。打分见 [score]，同分取**靠前的轨**（原盘顺序通常第一条就是主字幕）。
     * 没有轨道返回 null。
     */
    fun pickAuto(list: List<Track>): Track? = list.maxByOrNull { score(it) }

    private val CN_TOKENS = setOf(
        "chs", "cht", "chi", "zho", "zh", "cn", "sc", "tc", "gb", "big5", "chinese",
    )
    private val EN_TOKENS = setOf("eng", "en", "english")

    /** 不该当对白的轨（听障版/注释/歌词/特效）—— 命中直接压到底 */
    private val JUNK_TOKENS = setOf(
        "forced", "force", "sdh", "cc", "sign", "signs", "song", "lyrics", "note", "notes", "op", "ed",
    )

    /**
     * 给一条轨打分（1008 实测那 7 种轨道名全部覆盖）：
     *
     * | 轨道名 | 判定 | 分 |
     * |---|---|---|
     * | 简英双字 | 双语+简体 | 115 |
     * | 中上英下ASS | 双语 | 105 |
     * | 英上中下ASS | 双语 | 105 |
     * | 简体中字SRT | 只有中文+简体 | 75 |
     * | 繁体中字ASS | 只有中文+繁体 | 50 |
     * | 英文字幕ASS | 只有英文 | 20 |
     *
     * ⚠️ 不用子串匹配英文（`en` 会命中 `sign`）⇒ ASCII 部分一律**按 token 精确比**。
     */
    private fun score(t: Track): Int {
        val title = t.title.substringBefore(" - ").trim()
        val lower = title.lowercase()
        val toks = asciiTokens(lower)
        val lang = t.language.lowercase()

        val cn = lower.contains("中") || lower.contains("简") || lower.contains("繁") ||
                toks.any { it in CN_TOKENS } || lang in CN_TOKENS
        val en = lower.contains("英") || toks.any { it in EN_TOKENS } || lang in EN_TOKENS

        var s = when {
            cn && en -> 100   // 双语最值钱（我们就是拿它做英语跟读的）
            cn -> 60
            en -> 20
            else -> 40        // 认不出来 ⇒ 中性，别比纯英文还低（可能只是没写标签）
        }
        if (lower.contains("简") || toks.any { it == "chs" || it == "sc" || it == "gb" }) s += 15
        if (lower.contains("繁") || toks.any { it == "cht" || it == "tc" || it == "big5" }) s -= 10
        if (cn && en && lower.contains("中") && lower.contains("上")) s += 5   // 中上英下 = 常见排版
        if (toks.any { it in JUNK_TOKENS }) s -= 500
        if (title.length > 40) s -= 3
        return s
    }

    /** 拆出纯 ASCII 词元（不走正则：Android 的 ICU 对正则比 OpenJDK 严，能不用就不用） */
    private fun asciiTokens(s: String): List<String> {
        val out = ArrayList<String>(4)
        val sb = StringBuilder(8)
        for (c in s.lowercase()) {
            if (c in 'a'..'z' || c in '0'..'9') {
                sb.append(c)
            } else if (sb.isNotEmpty()) {
                out.add(sb.toString()); sb.setLength(0)
            }
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }

    // ==================================================================
    // ③ 扒一条轨
    // ==================================================================

    /**
     * 把一条轨的全文扒出来 → [Sentence] 表。
     * **null = 读不出**（压缩的字幕轨 / 索引坏的 / 不是文字轨…），调用方静默退回。
     */
    fun extract(context: Context, uri: Uri, trackIndex: Int): List<Sentence>? {
        val pair = if (isMatroska(context, uri)) mkvSamples(context, uri, trackIndex)
        else sysSamples(context, uri, trackIndex)
        if (pair == null) return null
        val (mime, samples) = pair
        if (samples.isEmpty()) {
            Log.w(TAG, "轨道 $trackIndex 一条样本都没读到")
            return null
        }
        val cues = cuesOf(samples, mime)
        if (cues.isEmpty()) {
            Log.w(TAG, "轨道 $trackIndex 读到 ${samples.size} 条样本但一条有效字幕都拼不出来")
            return null
        }
        val list = runCatching { SubtitleParser.parseAss(buildAss(cues)) }.getOrDefault(emptyList())
        if (!qualityOk(list)) return null
        Log.d(TAG, "轨道 $trackIndex 扒到 ${list.size} 句（样本 ${samples.size} 条，mime=$mime）")
        return list
    }

    // ---------- MKV：Media3 ----------

    private fun mkvSamples(context: Context, uri: Uri, trackIndex: Int): Pair<String, List<Pair<Long, String>>>? {
        val out = runMkv(context, uri, wantTrack = trackIndex, stopAtTracks = false) ?: return null
        val sink = out.sinks[trackIndex] ?: return null
        val mime = sink.format?.sampleMimeType.orEmpty()
        return mime to sink.samples
    }

    /**
     * 跑一遍 Media3 的 `MatroskaExtractor`。
     * @param wantTrack 只收集这条轨的样本（**必须**，否则会把整片视频帧也攒进内存）。-1 = 都不要。
     * @param stopAtTracks true = 轨道声明解析完就停（列轨道用，不读整片）
     */
    private fun runMkv(
        context: Context,
        uri: Uri,
        wantTrack: Int,
        stopAtTracks: Boolean,
    ): MkvOut? {
        val data = DefaultDataSource.Factory(context).createDataSource()
        var extractor: MatroskaExtractor? = null
        return try {
            data.open(DataSpec(uri))
            val out = MkvOut(wantTrack)
            // 🔴 flags 只能是 2（EMIT_RAW）。Media3 里有硬校验：
            //    raw 输出必须允许 seek for cues，所以不能叠加 FLAG_DISABLE_SEEK_FOR_CUES。
            extractor = MatroskaExtractor(
                androidx.media3.extractor.text.SubtitleParser.Factory.UNSUPPORTED,
                MatroskaExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA,
            )
            extractor.init(out)
            var input: ExtractorInput = DefaultExtractorInput(data, 0L, C.LENGTH_UNSET.toLong())
            val pos = PositionHolder()
            var loops = 0
            while (loops++ < MAX_READ_LOOPS) {
                val r = try {
                    extractor.read(input, pos)
                } catch (t: Throwable) {
                    Log.w(TAG, "mkv read 中断 @$loops", t)
                    break
                }
                if (r == Extractor.RESULT_END_OF_INPUT) break
                if (r == Extractor.RESULT_SEEK) {
                    // extractor 要求跳到别处（建 cue 索引 / 跳 cluster）⇒ 重新定位数据源。
                    // 🔴【踩过】`DefaultExtractorInput` 的第 2 个参数是**在整份文件里的绝对位置**
                    //   （`getPosition()` 直接返回它）。上一版这里写死 0L，而数据源已经 seek 到 pos.position
                    //   ⇒ 提取器以为自己还在文件头，内部绝对偏移全错（实测：同一条轨 25 条样本读成了 372 条、
                    //   时间轴还全是 0）。必须跟 `DataSpec` 的开头保持一致。
                    runCatching { data.close() }
                    data.open(DataSpec(uri, pos.position, C.LENGTH_UNSET.toLong()))
                    input = DefaultExtractorInput(data, pos.position, C.LENGTH_UNSET.toLong())
                }
                if (stopAtTracks && out.tracksDone) break
            }
            out
        } catch (t: Throwable) {
            Log.e(TAG, "mkv 提取失败", t)
            null
        } finally {
            runCatching { extractor?.release() }
            runCatching { data.close() }
        }
    }

    /**
     * 跑 extractor 时收集轨道信息 / 样本的容器。
     * ⚠️ `wantTrack` 之外的轨**一个字节都不攒**：extractor 连视频帧也会照常吐出来，
     * 全攒下来就是几百 MB，直接 OOM。
     */
    private class MkvOut(private val want: Int) : ExtractorOutput {
        val sinks = LinkedHashMap<Int, MkvSink>()
        @Volatile var tracksDone = false
        override fun track(id: Int, type: Int): TrackOutput =
            sinks.getOrPut(id) { MkvSink(type, store = id == want) }
        override fun endTracks() { tracksDone = true }
        override fun seekMap(seekMap: SeekMap) { /* 不使用索引 */ }
    }

    private class MkvSink(val trackType: Int, private val store: Boolean) : TrackOutput {
        var format: Format? = null
        val samples = ArrayList<Pair<Long, String>>()
        private val buf = ByteArrayOutputStream(1 shl 12)

        override fun format(format: Format) { this.format = format }

        override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, part: Int): Int {
            val tmp = ByteArray(length)
            var read = 0
            while (read < length) {
                val r = try { input.read(tmp, read, length - read) } catch (_: Throwable) { -1 }
                if (r == C.RESULT_END_OF_INPUT) break
                read += r
            }
            if (store && part == TrackOutput.SAMPLE_DATA_PART_MAIN) buf.write(tmp, 0, read)
            return read
        }

        override fun sampleData(data: ParsableByteArray, length: Int, part: Int) {
            if (store && part == TrackOutput.SAMPLE_DATA_PART_MAIN) {
                val tmp = ByteArray(length)
                data.readBytes(tmp, 0, length)
                buf.write(tmp, 0, length)
            } else {
                data.skipBytes(length)
            }
        }

        override fun sampleMetadata(
            timeUs: Long,
            flags: Int,
            size: Int,
            offset: Int,
            cryptoData: TrackOutput.CryptoData?,
        ) {
            if (!store || buf.size() == 0) {
                buf.reset()
                return
            }
            val bytes = buf.toByteArray()
            buf.reset()
            samples.add((timeUs / 1000) to decodePayload(bytes, format?.sampleMimeType.orEmpty()))
        }
    }

    // ---------- 其它容器：系统 MediaExtractor ----------

    private fun sysSamples(context: Context, uri: Uri, trackIndex: Int): Pair<String, List<Pair<Long, String>>>? {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(context, uri, null)
            if (trackIndex < 0 || trackIndex >= ex.trackCount) return null
            val fmt = ex.getTrackFormat(trackIndex)
            val mime = meta(fmt, MediaFormat.KEY_MIME).lowercase()
            ex.selectTrack(trackIndex)
            // ⚠️ 必须**直接缓冲区**：readSampleData 的 JNI 只认 direct buffer
            val buf = ByteBuffer.allocateDirect(MAX_SAMPLE_BYTES)
            val out = ArrayList<Pair<Long, String>>()
            var n = 0
            while (n < MAX_SAMPLES) {
                buf.clear()
                val size = try {
                    ex.readSampleData(buf, 0)
                } catch (t: Throwable) {
                    Log.w(TAG, "readSampleData 中断 @样本$n", t)
                    -1
                }
                if (size <= 0) break
                val tUs = ex.sampleTime
                buf.position(0)
                buf.limit(size)
                val bytes = ByteArray(size)
                buf.get(bytes)
                out.add((tUs / 1000) to decodePayload(bytes, mime))
                n++
                if (!ex.advance()) break
            }
            return mime to out
        } catch (t: Throwable) {
            Log.e(TAG, "sysSamples failed track=$trackIndex", t)
            return null
        } finally {
            runCatching { ex.release() }
        }
    }

    /**
     * 一条样本的字节 → 字符串。
     * - `tx3g`（MP4/MOV 的内封文本字幕）：前 2 字节是大端文本长度，后面才是文本；
     * - 其它（MKV 的 ASS/SRT）：整块就是文本（UTF-8；老片有 GBK 的，兜一下）。
     */
    private fun decodePayload(bytes: ByteArray, mime: String): String {
        if (bytes.isEmpty()) return ""
        val body = if (mime.contains("tx3g") || mime.contains("3gpp")) {
            if (bytes.size < 2) return ""
            val len = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
            bytes.copyOfRange(2, (2 + len).coerceAtMost(bytes.size))
        } else {
            bytes
        }
        if (body.isEmpty()) return ""
        val raw = if (body.size >= 3 &&
            body[0] == 0xEF.toByte() && body[1] == 0xBB.toByte() && body[2] == 0xBF.toByte()
        ) body.copyOfRange(3, body.size) else body
        val utf8 = String(raw, Charsets.UTF_8)
        val text = if (utf8.contains('\uFFFD')) {
            runCatching { String(raw, Charset.forName("GB18030")) }.getOrDefault(utf8)
        } else utf8
        // MKV 里偶尔用 \0 或空格补齐定长，去掉（内部换行千万留着）
        return text.replace("\u0000", "").trimEnd()
    }

    /** 拆好的一条字幕（时间轴 + 样式名 + 原文，样式名留给 [SubtitleParser] 判垃圾样式） */
    private data class Cue(val startMs: Long, val endMs: Long, val style: String, val text: String)

    private fun cuesOf(samples: List<Pair<Long, String>>, mime: String): List<Cue> {
        val isAss = mime.contains("ssa") || mime.contains("subtitle") || mime.contains("ass")
        // 时间轴上同一时刻的多条（双语/多轨）必须拿到**同一个 end**：
        // parseAss 是按 (start,end) 分组的，给不一样就会把中英两句拆成各一句、在列表里各占一行。
        val starts = samples.map { it.first }.distinct().sorted()
        val out = ArrayList<Cue>(samples.size)
        for (i in samples.indices) {
            val tMs = samples[i].first
            val n = normalize(samples[i].second, isAss) ?: continue
            var end = n.endMs ?: run {
                val next = starts.firstOrNull { it > tMs }
                minOf(next ?: (tMs + MAX_CUE_MS), tMs + MAX_CUE_MS)
            }
            if (end <= tMs) end = tMs + 400
            val cue = Cue(n.startMs ?: tMs, end, n.style, n.text)
            // 去重：紧挨着的完全重复条（有的片源会把同一条写两遍）
            if (out.lastOrNull()?.let { it.startMs == cue.startMs && it.endMs == cue.endMs && it.text == cue.text } == true) {
                continue
            }
            out.add(cue)
        }
        return out
    }

    /** [normalize] 的结果；`startMs`/`endMs` 非空 = payload 自己带了时间轴（少数提取器会带） */
    private data class Norm(val style: String, val startMs: Long?, val endMs: Long?, val text: String)

    /**
     * 把一条 payload 拆成 (样式名, 自带起点, 自带终点, 文本)。认不出返回 null。
     * 顺序不能乱：先看"自带完整 Dialogue 行"，再看 SRT 形态，再看 ASS 裸 payload，最后当纯文本。
     */
    private fun normalize(raw: String, isAss: Boolean): Norm? {
        var p = raw.trim()
        if (p.isEmpty()) return null

        // ① payload 里带 `Dialogue:` 行。**两种形态都要认**：
        //    ①-a Media3 裸字幕模式的**合成前缀**（实测就是这样）：
        //        `Dialogue: <start>,<end>,ReadOrder,Layer,Style,Name,ML,MR,MV,Effect,Text`  ← 文本前 10 段
        //        🔴 它自带的 start/end **不可信**（实测 25 条全从 `0:00:00:00` 起，明显不是绝对时间）
        //           ⇒ 只取「样式名 + 文本」，时间一律用 `sampleMetadata` 给的 sampleTime。
        //        🔴 不单独认这一段的话，就会按标准布局去取第 9 段，**正好多切出一个前导逗号**
        //           （实测表现：每句字幕开头都多一个 `,`）。
        //    ①-b 标准 ASS 行：`Layer,Start,End,Style,Name,ML,MR,MV,Effect,Text`  ← 文本前 9 段
        if (p.startsWith("Dialogue:", ignoreCase = true)) {
            val rest = p.substringAfter(':')
            val wide = rest.split(',', limit = 11)
            if (wide.size == 11 && isAssTimeLike(wide[0]) && isAssTimeLike(wide[1])) {
                if (wide[10].isNotBlank()) return Norm(wide[4].trim(), null, null, wide[10])
            }
            val std = rest.split(',', limit = 10)
            if (std.size == 10 && isAssTimeLike(std[1]) && isAssTimeLike(std[2])) {
                if (std[9].isNotBlank()) {
                    return Norm(std[3].trim(), assMs(std[1]), assMs(std[2]), std[9])
                }
            }
            p = rest.trim()
        }

        // ② SRT 形态（payload 里带 `-->`）
        if (p.contains("-->")) {
            val lines = p.lines()
            val ti = lines.indexOfFirst { it.contains("-->") }
            if (ti >= 0) {
                val text = lines.drop(ti + 1).joinToString("\n").trim()
                if (text.isNotBlank()) {
                    val times = lines[ti].split("-->")
                    return Norm("", srtMs(times.getOrNull(0)), srtMs(times.getOrNull(1)), text)
                }
            }
        }

        // ③ ASS 裸 payload：ReadOrder,Layer,Style,Name,MarginL,MarginR,MarginV,Effect,Text
        if (isAss || looksLikeAssRaw(p)) {
            val parts = p.split(',', limit = 9)
            if (parts.size == 9 && parts[0].trim().toIntOrNull() != null) {
                val text = parts[8]
                if (text.isNotBlank()) return Norm(parts[2].trim(), null, null, text)
            }
        }

        // ④ 纯文本（MKV 的 SRT 轨、tx3g 都是这个形态）
        return Norm("", null, null, p)
    }

    /** 看着像 ASS 时间戳 `H:MM:SS.cc`（分隔符按 `:` 数，ASS 用 `.`、Media3 合成前缀用 `:`） */
    private fun isAssTimeLike(s: String): Boolean {
        val t = s.trim()
        if (t.length < 7) return false
        var sep = 0
        for (c in t) {
            when {
                c == ':' || c == '.' -> sep++
                c.isDigit() -> Unit
                else -> return false
            }
        }
        return sep == 3
    }

    /** 是不是"前两个字段都是数字"（ASS 裸 payload 的指纹） */
    private fun looksLikeAssRaw(p: String): Boolean {
        val head = p.take(40)
        val i = head.indexOf(',')
        if (i <= 0) return false
        if (head.substring(0, i).trim().toIntOrNull() == null) return false
        val j = head.indexOf(',', i + 1)
        if (j < 0) return false
        return head.substring(i + 1, j).trim().toIntOrNull() != null
    }

    private fun assMs(s: String?): Long? {
        val t = s?.trim() ?: return null
        val seg = t.split(':')
        if (seg.size != 3) return null
        val h = seg[0].toLongOrNull() ?: return null
        val mi = seg[1].toLongOrNull() ?: return null
        val secParts = seg[2].split('.', ',')
        val se = secParts.getOrNull(0)?.toLongOrNull() ?: return null
        val frac = secParts.getOrNull(1).orEmpty()
        val ms = when {
            frac.length >= 3 -> frac.take(3).toLongOrNull() ?: 0L
            frac.isNotEmpty() -> (frac.padEnd(2, '0').take(2).toLongOrNull() ?: 0L) * 10
            else -> 0L
        }
        return (h * 3600 + mi * 60 + se) * 1000 + ms
    }

    private fun srtMs(s: String?): Long? {
        val t = s?.trim() ?: return null
        val seg = t.split(':')
        if (seg.size != 3) return null
        val h = seg[0].toLongOrNull() ?: return null
        val mi = seg[1].toLongOrNull() ?: return null
        val secParts = seg[2].split(',', '.')
        val se = secParts.getOrNull(0)?.toLongOrNull() ?: return null
        val ms = (secParts.getOrNull(1).orEmpty() + "000").take(3).toLongOrNull() ?: 0L
        return (h * 3600 + mi * 60 + se) * 1000 + ms
    }

    /**
     * 合出一份**完整合法**的 ASS 文本，交给 [SubtitleParser.parseAss]。
     *
     * 为什么不直接自己拼 [Sentence]：`{\…}` 标签清洗、双语合并（英文在上中文在下）、垃圾样式
     * （Sign/歌词/水印…）过滤这三件事全在 [SubtitleParser.parseAss] 里，是踩过坑的真值。
     * 这边另写一套 = 早晚两边不一致。所以只负责"把壳摆正"：
     * 字段顺序自己定，`Format:` 行按名字声明（parseAss 是按**名字**取下标的，顺序随意）。
     */
    private fun buildAss(cues: List<Cue>): String {
        val sb = StringBuilder(1 shl 16)
        sb.append("[Script Info]\nScriptType: v4.00+\nWrapStyle: 0\n\n")
        sb.append("[V4+ Styles]\n")
        sb.append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour,")
        sb.append(" Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle,")
        sb.append(" Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n")
        sb.append("Style: Default,Arial,20,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1\n\n")
        sb.append("[Events]\n")
        sb.append("Format: ReadOrder,Layer,Style,Name,MarginL,MarginR,MarginV,Effect,Start,End,Text\n")
        for (c in cues) {
            sb.append("Dialogue: 0,0,")
                .append(c.style.replace(",", "").ifBlank { "Default" })
                .append(",,")                        // Name 留空
                .append("0000,0000,0000,,")          // MarginL, MarginR, MarginV, Effect
                .append(assTime(c.startMs)).append(',')
                .append(assTime(c.endMs)).append(',')
                .append(oneLine(c.text)).append('\n')
        }
        return sb.toString()
    }

    /** `H:MM:SS.cc`（cc = 百分秒） */
    private fun assTime(ms: Long): String {
        val t = ms.coerceAtLeast(0)
        return "%d:%02d:%02d.%02d".format(
            t / 3_600_000, (t / 60_000) % 60, (t / 1000) % 60, (t % 1000) / 10,
        )
    }

    /**
     * 压成**一行**：ASS 一条 Dialogue 必须是一行，真实换行会把它劈成两条非法行。
     * 换回字面 `\N`（[SubtitleParser.parseAss] 会再还原成换行）—— 跟内封原始形态完全一致。
     */
    private fun oneLine(s: String): String =
        s.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "\\N")

    /**
     * 🔴 最后一道闸：扒出来的东西**像不像正经字幕**。
     *
     * 会走到这里说明"能读到样本、也能拼出条目"，但内容可能是乱码（压缩轨解不开、读错轨、加密流）。
     * 判据：可见字符（字母/数字/空格）占比 < 50% ⇒ 认定是垃圾，返回 null 走静默退回。
     * 正常字幕里标点占比很低，这个阈值卡不到真字幕。
     */
    private fun qualityOk(list: List<Sentence>): Boolean {
        if (list.isEmpty()) return false
        var good = 0
        var total = 0
        for (s in list) {
            for (c in s.text) {
                total++
                if (c.isLetterOrDigit() || c == ' ') good++
            }
        }
        if (total == 0) return false
        val r = good.toDouble() / total
        if (r < 0.5) {
            Log.w(TAG, "扒出来的内容不像字幕（正常字符占比 ${"%.2f".format(r)}），丢弃")
            return false
        }
        return true
    }

    // ==================================================================
    // ④ 缓存（一次扒完，之后每次打开都是瞬开）
    // ==================================================================

    private fun cacheDir(context: Context) = File(context.filesDir, CACHE_DIR)

    /** 音视频路径 → 固定文件名（SHA-1 前 6 字节）。同一视频永远同一个名字。 */
    private fun hashOf(p: String): String = try {
        MessageDigest.getInstance("SHA-1").digest(p.toByteArray(Charsets.UTF_8))
            .take(6).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    } catch (_: Throwable) {
        Integer.toHexString(p.hashCode())
    }

    /** 标签塞进文件名（`<hash>__<标签>.srt`）⇒ 一个文件搞定，不用再维护一份映射 */
    private fun sanitize(s: String): String =
        s.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").trim('_').take(20)

    private fun cachedFile(context: Context, videoPath: String): File? = try {
        val prefix = hashOf(videoPath) + "__"
        cacheDir(context).listFiles { f: File -> f.isFile && f.name.startsWith(prefix) }?.firstOrNull()
    } catch (_: Throwable) {
        null
    }

    /**
     * 有缓存就直接用。返回 (显示名, 句子)；没有缓存 / 缓存读不出返回 null。
     * ⚠️ 读取是同步的（一个小文件），跟原来读同目录 .srt 一个量级，不用异步。
     */
    fun cachedLoad(context: Context, videoPath: String): Pair<String, List<Sentence>>? {
        val f = cachedFile(context, videoPath) ?: return null
        val label = f.name.substringAfter("__").substringBeforeLast(".")
        val list = runCatching { SubtitleParser.parseFile(f) }.getOrDefault(emptyList())
        if (list.isEmpty()) {
            f.delete()
            return null
        }
        Log.d(TAG, "命中内封字幕缓存：$label（${list.size} 句）")
        return (TAG_PREFIX + label) to list
    }

    private fun writeCache(context: Context, videoPath: String, label: String, list: List<Sentence>) {
        runCatching {
            val dir = cacheDir(context).apply { mkdirs() }
            // 一次只留一份（用户换过轨 ⇒ 旧的那份删掉，别攒垃圾）
            cachedFile(context, videoPath)?.delete()
            File(dir, "${hashOf(videoPath)}__${sanitize(label)}.srt")
                .writeText(toSrt(list), Charsets.UTF_8)
        }
    }

    private fun toSrt(list: List<Sentence>): String {
        val sb = StringBuilder(1 shl 16)
        list.forEachIndexed { i, s ->
            sb.append(i + 1).append('\n')
            sb.append(srtTime(s.startMs)).append(" --> ").append(srtTime(s.endMs)).append('\n')
            sb.append(s.text.replace("\r\n", "\n").replace('\r', '\n')).append("\n\n")
        }
        return sb.toString()
    }

    private fun srtTime(ms: Long): String {
        val t = ms.coerceAtLeast(0)
        return "%02d:%02d:%02d,%03d".format(
            t / 3_600_000, (t / 60_000) % 60, (t / 1000) % 60, t % 1000,
        )
    }

    // ==================================================================
    // ⑤ 对外入口
    // ==================================================================

    /**
     * 手动换轨（用户从字幕菜单里点了一条）：扒 + 写缓存。
     * **阻塞** ⇒ 调用方必须放到 IO 线程。返回 (显示名, 句子)；扒不出返回 null。
     */
    fun extractAndCache(
        context: Context,
        uri: Uri,
        videoPath: String,
        trackIndex: Int,
    ): Pair<String, List<Sentence>>? {
        val track = tracks(context, uri).firstOrNull { it.index == trackIndex } ?: return null
        val list = extract(context, uri, track.index) ?: return null
        writeCache(context, videoPath, track.label, list)
        return (TAG_PREFIX + track.label) to list
    }

    /** 串行化 + 作废令牌：换视频后，上一个视频的扒取任务完成了也不许写回界面 */
    @Volatile private var generation = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 打开视频时调用：**有缓存立刻给**（同步回调），没缓存**后台扒**，扒到了再回调。
     * 扒不到就**什么都不做** —— 界面保持原样（这是设计，不是漏了提示）。
     *
     * @param onReady 成功时回调（主线程），参数 = (显示名, 句子)
     */
    fun autoLoad(
        context: Context,
        videoPath: String,
        uri: Uri,
        onReady: (String, List<Sentence>) -> Unit,
    ) {
        val my = ++generation
        cachedLoad(context, videoPath)?.let { (name, list) ->
            onReady(name, list)
            return
        }
        val app = context.applicationContext
        scope.launch {
            val r = try {
                val list = tracks(app, uri)
                if (list.isEmpty()) null
                else pickAuto(list)?.let { t ->
                    extract(app, uri, t.index)?.also { writeCache(app, videoPath, t.label, it) }
                        ?.let { (TAG_PREFIX + t.label) to it }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "autoLoad 扒字幕失败", t)
                null
            }
            if (r == null) {
                Log.d(TAG, "没有可用的内封字幕轨（或扒不出），静默退回原逻辑")
                return@launch
            }
            withContext(Dispatchers.Main) {
                if (my != generation) {
                    Log.d(TAG, "结果回来时已经换视频了，丢弃")
                    return@withContext
                }
                onReady(r.first, r.second)
            }
        }
    }
}
