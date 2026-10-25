package com.xuanji.repeater

import android.content.Context
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
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.text.SubtitleParser
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * 视频音轨的**读取层**，由 Media3（ExoPlayer 那套）自带解析器驱动。
 *
 * ## 为什么必须换成它（1016 阿亮《破产姐妹》报"这个视频没有音轨"）
 *
 * 「抽音轨」原来走系统 `android.media.MediaExtractor`，两个硬伤：
 *  1. **系统 MKV 解析器不认 AC3** —— 实测日志（副测机 `7042e0ef` / Android 17）：
 *     `W/MatroskaExtractor: A_AC3 is not supported.` ⇒ 那条音轨被**直接丢弃**
 *     ⇒ `trackCount` 里只剩视频轨 ⇒ 误报「这个视频没有音轨」。
 *     （破产姐妹 = `HEVC + AC3 5.1`，音轨明明白白在文件里，ffprobe 复验过。）
 *  2. **依赖系统 `media.extractor` 服务**，而那个服务的 servicemanager 注册**会掉**（1016 真事：
 *     进程活着、`init.svc=running`，但 `service list` 里搜不到该名字 ⇒ 客户端等 5 秒超时返回 NULL）
 *     ⇒ `MediaExtractor` 全盘不可用 ⇒ 报「读不到这个视频（可能已被删除或无权限）」。
 *     ⚠️ 那个错误跟文件毫无关系，是**服务**的问题（重启该服务即恢复，代价是 App 侧无从修复）。
 *
 * Media3 的解析器 ① 跑在**进程内**、不依赖任何系统服务；② 认 AC3
 * （扒过 `media3-extractor-1.11.1` 字节码：`MatroskaExtractor` 里 `A_AC3 → audio/ac3` 在册）；
 * ③ 本来就是 App 用来**播放**这个视频的同一套解析器（能播 = 能解析）。
 *
 * ## 对外接口（刻意做得跟 `MediaExtractor` 一样窄）
 *
 * 只暴露 [VideoAudioExtractor] 用到的那几个操作：音频轨 format / 顺序读样本 / seek / 时长。
 * 这样上层解码+重采样+写 WAV 的代码**一行都不用改**。
 *
 * ## 驱动方式
 *
 * 照 `EmbeddedSubtitle.runMkv()` 的成熟写法（`ExtractorOutput` + `TrackOutput` + `DefaultExtractorInput`）。
 * 🔴 那个类里踩过的坑原样继承：`DefaultExtractorInput` 第 2 个参数是**在整份文件里的绝对位置**，
 * 遇到 `RESULT_SEEK` 重新定位数据源时必须跟 `DataSpec` 的起点保持一致，否则提取器内部偏移全错。
 *
 * ⚠️ 样本是**按簇攒**的：`extractor.read()` 一次可能吐出一整个簇的音频样本，全放 [queue] 里。
 * 只保留音频轨，视频轨一个字节都不留（否则几百 MB 直接 OOM）。
 */
internal class Media3AudioReader(context: Context, private val uri: Uri) : AutoCloseable {

    private companion object {
        const val TAG = "Media3AudioReader"
        /** 找轨道时最多读多少次（正常几百次内就有结果，纯保险） */
        const val MAX_OPEN_LOOPS = 4000
        /** 单次 pump 最多读多少次（防止一次调用里无限膨胀） */
        const val MAX_PUMP_LOOPS = 64
        /** 丢弃用的小缓冲（视频样本可能很大，别按 length 分配） */
        const val DISCARD_CHUNK = 1 shl 16
    }

    /** 音频轨的 format（给 `MediaCodec.configure` 用）；null = 没找到音频轨 */
    var trackFormat: MediaFormat? = null
        private set

    private val appContext = context.applicationContext
    private val data = DefaultDataSource.Factory(appContext).createDataSource()
    private var extractor: Extractor? = null
    private var input: ExtractorInput? = null
    private val positionHolder = PositionHolder()
    private var seekMap: SeekMap? = null
    private var eof = false
    private var closed = false

    /** 已解析好、待消费的样本（按顺序） */
    private val queue = ArrayDeque<Sample>()
    private var current: Sample? = null

    private class Sample(val bytes: ByteArray, val timeUs: Long)

    /**
     * 打开并解析到"拿到音频轨 format"为止。
     * @return null = 成功；非空 = 失败原因（可直接显示给用户）
     */
    fun open(): String? {
        try {
            data.open(DataSpec(uri))
        } catch (t: Throwable) {
            Log.w(TAG, "数据源打不开", t)
            return "读不到这个视频（可能已被删除或无权限）"
        }
        val out = Out()
        // 🔴【踩过】`input` 必须**先**建好再嗅探 —— `pickExtractor()` 要靠它做 `sniff()`。
        //   上一版把嗅探写在建 input 之前 ⇒ 探针为 null ⇒ 一律"认不出容器" ⇒ 报"读不到这个视频"。
        input = DefaultExtractorInput(data, 0L, C.LENGTH_UNSET.toLong())
        val ex = try {
            pickExtractor()
        } catch (t: Throwable) {
            Log.w(TAG, "识别容器失败", t)
            null
        }
        if (ex == null) {
            Log.w(TAG, "没有任何解析器能认出这个容器")
            runCatching { data.close() }
            return "这个视频的封装格式认不出来"
        }
        extractor = ex
        ex.init(out)

        var loops = 0
        while (trackFormat == null && !eof && loops++ < MAX_OPEN_LOOPS) pump()
        if (trackFormat == null) return "这个视频没有音轨"
        return null
    }

    /** 整片时长（微秒）；读不到返回 -1 */
    fun durationUs(): Long = seekMap?.durationUs ?: -1L

    // ---------- 给解码循环用的窄接口 ----------

    /** 当前样本时间戳；已无样本返回 -1 */
    fun sampleTimeUs(): Long {
        ensure()
        return current?.timeUs ?: -1L
    }

    /** 把当前样本拷进 [buf] 的 [offset] 处，返回写入字节数；无样本返回 -1 */
    fun readSampleData(buf: ByteBuffer, offset: Int): Int {
        ensure()
        val s = current ?: return -1
        val cap = buf.capacity() - offset
        if (cap <= 0) return -1
        val n = if (s.bytes.size <= cap) s.bytes.size else cap
        buf.position(offset)
        buf.put(s.bytes, 0, n)
        return n
    }

    /** 消费掉当前样本，前进到下一个 */
    fun advance() {
        current = null
    }

    /** 跳到指定时间（微秒）：用 Media3 的索引（cue）换算成字节位置再定位 */
    fun seekTo(timeUs: Long) {
        val map = seekMap
        val point = if (map != null && map.isSeekable) {
            runCatching { map.getSeekPoints(timeUs).first }.getOrNull() ?: SeekPoint.START
        } else {
            SeekPoint.START
        }
        runCatching { data.close() }
        try {
            data.open(DataSpec(uri, point.position, C.LENGTH_UNSET.toLong()))
        } catch (t: Throwable) {
            Log.w(TAG, "seek 后重开数据源失败", t)
            eof = true
            return
        }
        input = DefaultExtractorInput(data, point.position, C.LENGTH_UNSET.toLong())
        runCatching { extractor?.seek(point.position, point.timeUs) }
        queue.clear()
        current = null
        eof = false
    }

    override fun close() {
        if (closed) return
        closed = true
        queue.clear()
        current = null
        runCatching { extractor?.release() }
        extractor = null
        runCatching { data.close() }
    }

    // ---------- 内部 ----------

    /** 嗅探出适配的解析器 */
    private fun pickExtractor(): Extractor? {
        val probe = input ?: return null
        logHead(probe)

        // ① MKV / WebM **优先**，用 App 里已验证过的那套配置（见 `EmbeddedSubtitle.runMkv`）。
        //    🔴【1016 踩过】原版是直接把 `DefaultExtractorsFactory().createExtractors()` 从头到尾挨个
        //      `sniff()`、**中间不复位 peek 位置** —— 实测把 MKV 误判成了
        //      `androidx.media3.extractor.ts.Ac3Extractor`（裸 AC3 单流解析器），
        //      接着 `Ac3Reader` 拿垃圾数据去解帧头 → `ArrayIndexOutOfBoundsException`（解析中断）。
        //      ⇒ 两条一起改：MKV 判定放最前 + 每次嗅探后强制 `resetPeekPosition()`。
        val mkv = try {
            MatroskaExtractor(
                SubtitleParser.Factory.UNSUPPORTED,
                MatroskaExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA,
            )
        } catch (t: Throwable) {
            Log.w(TAG, "建 MKV 解析器失败", t)
            null
        }
        if (mkv != null && sniffOrFalse(mkv, probe)) return pick(mkv)

        // ② 其它容器：通用嗅探。每个失败后都要复位，别把 peek 位置留给下一个嗅探器。
        val list = try {
            DefaultExtractorsFactory().createExtractors()
        } catch (t: Throwable) {
            Log.w(TAG, "建通用解析器列表失败", t)
            return null
        }
        for (e in list) {
            if (sniffOrFalse(e, probe)) return pick(e)
        }
        return null
    }

    private fun sniffOrFalse(e: Extractor, probe: ExtractorInput): Boolean {
        val ok = try {
            e.sniff(probe)
        } catch (t: Throwable) {
            Log.i(TAG, "嗅探 ${e.javaClass.name} 抛异常（忽略）", t)
            false
        }
        // ⚠️ 必须复位：嗅探器可能把 peek 位置留在半路（见 [pickExtractor] 注释）
        runCatching { probe.resetPeekPosition() }
        return ok
    }

    private fun pick(e: Extractor): Extractor {
        Log.i(TAG, "选用解析器 ${e.javaClass.name}")
        return e
    }

    /** 诊断：把输入最前面的字节打出来（MKV 正常应以 `1A45DFA3` 开头） */
    private fun logHead(probe: ExtractorInput) {
        val head = ByteArray(8)
        val got = runCatching { probe.peekFully(head, 0, head.size, true) }.getOrDefault(false)
        runCatching { probe.resetPeekPosition() }
        if (got) {
            Log.i(TAG, "文件头 = " + head.joinToString(" ") { "%02X".format(it) })
        } else {
            Log.w(TAG, "文件头读不出来（前 ${head.size} 字节）")
        }
    }

    /** 保证 [current] 有样本（没有就去解析） */
    private fun ensure() {
        if (current != null) return
        pump()
        current = queue.removeFirstOrNull()
    }

    /** 推进一步解析，直到 [queue] 里有样本 / 到头 / 抛错 */
    private fun pump() {
        if (eof || closed) return
        val ex = extractor ?: return
        var loops = 0
        while (queue.isEmpty() && !eof && loops++ < MAX_PUMP_LOOPS) {
            val r = try {
                ex.read(input ?: return, positionHolder)
            } catch (t: Throwable) {
                Log.w(TAG, "解析中断", t)
                eof = true
                return
            }
            when (r) {
                Extractor.RESULT_END_OF_INPUT -> eof = true
                Extractor.RESULT_SEEK -> {
                    // 🔴 绝对位置必须跟 DataSpec 的起点一致（见类注释）
                    runCatching { data.close() }
                    try {
                        data.open(DataSpec(uri, positionHolder.position, C.LENGTH_UNSET.toLong()))
                    } catch (t: Throwable) {
                        Log.w(TAG, "重定位失败", t)
                        eof = true
                        return
                    }
                    input = DefaultExtractorInput(data, positionHolder.position, C.LENGTH_UNSET.toLong())
                }
            }
        }
    }

    /** 轨道分发：音频轨收集样本，其余全丢 */
    private inner class Out : ExtractorOutput {
        private val sinks = LinkedHashMap<Int, TrackOutput>()
        private var audioSink: AudioSink? = null

        override fun track(id: Int, type: Int): TrackOutput = sinks.getOrPut(id) {
            if (type == C.TRACK_TYPE_AUDIO && audioSink == null) {
                AudioSink().also { audioSink = it }
            } else {
                DiscardSink()
            }
        }

        override fun endTracks() {}

        override fun seekMap(seekMap: SeekMap) {
            this@Media3AudioReader.seekMap = seekMap
        }
    }

    /** 收集音频样本 */
    private inner class AudioSink : TrackOutput {
        private val buf = ByteArrayOutputStream(1 shl 12)

        override fun format(format: Format) {
            val mime = format.sampleMimeType ?: return
            if (mime.startsWith("audio/")) trackFormat = toMediaFormat(format, mime)
        }

        override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, part: Int): Int {
            if (length <= 0) return 0
            val tmp = ByteArray(length)
            var read = 0
            while (read < length) {
                val r = try {
                    input.read(tmp, read, length - read)
                } catch (_: Throwable) {
                    -1
                }
                if (r == C.RESULT_END_OF_INPUT || r <= 0) break
                read += r
            }
            if (part == TrackOutput.SAMPLE_DATA_PART_MAIN) buf.write(tmp, 0, read)
            return read
        }

        override fun sampleData(data: ParsableByteArray, length: Int, part: Int) {
            if (part == TrackOutput.SAMPLE_DATA_PART_MAIN) {
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
            // 🔴 Media3 的顺序是「先 sampleData、后 sampleMetadata」（见 EmbeddedSubtitle.MkvSink），
            //   所以这里直接把攒好的字节定为一个样本。
            if (buf.size() == 0) return
            val bytes = buf.toByteArray()
            buf.reset()
            queue.add(Sample(bytes, timeUs))
        }
    }

    /** 其它轨（视频/字幕）：数据照常消费掉，但一个字节都不留 */
    private class DiscardSink : TrackOutput {
        private val scratch = ByteArray(DISCARD_CHUNK)

        override fun format(format: Format) {}

        override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, part: Int): Int {
            var read = 0
            while (read < length) {
                val want = if (length - read < scratch.size) length - read else scratch.size
                val r = try {
                    input.read(scratch, 0, want)
                } catch (_: Throwable) {
                    -1
                }
                if (r == C.RESULT_END_OF_INPUT || r <= 0) break
                read += r
            }
            return read
        }

        override fun sampleData(data: ParsableByteArray, length: Int, part: Int) {
            data.skipBytes(length)
        }

        override fun sampleMetadata(
            timeUs: Long,
            flags: Int,
            size: Int,
            offset: Int,
            cryptoData: TrackOutput.CryptoData?,
        ) {
        }
    }

    /** Media3 的 `Format` → 系统 `MediaFormat`（`MediaCodec.configure` 只吃后者） */
    private fun toMediaFormat(f: Format, mime: String): MediaFormat {
        val mf = MediaFormat.createAudioFormat(mime, f.sampleRate, f.channelCount)
        f.initializationData.forEachIndexed { i, csd ->
            if (csd.isNotEmpty()) {
                mf.setByteBuffer("csd-$i", ByteBuffer.wrap(csd))
            }
        }
        if (f.maxInputSize > 0) {
            mf.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, f.maxInputSize)
        }
        return mf
    }
}
