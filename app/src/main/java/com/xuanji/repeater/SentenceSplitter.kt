package com.xuanji.repeater

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import kotlin.math.sqrt

/** 断句结果：句子列表 + 波形帧数据（用于绘制） */
data class SplitResult(
    val sentences: List<Sentence>,
    val waveform: FloatArray,
    val totalMs: Long,
    /** 原始边界段：仅按真实停顿（≥300ms 静音）切分，不做"最短句长 1500ms"合并，
     *  供无时间戳 ASR 文本对齐用——短句（如 "Hi."）被最短句长规则并进下一段会导致文本对不齐 */
    val rawSegments: List<Sentence> = emptyList(),
)

/**
 * 断句引擎：
 * - MediaExtractor + MediaCodec 解码 PCM
 * - 按 FRAME_MS=50ms 分帧计算 RMS 能量（双声道音频帧数翻倍，实际帧长 50ms/声道数，
 *   用时长的实际帧长换算边界时间，见 split() 的 frameMsReal）
 * - 静音阈值 = 峰值 × 7.5%
 * - 连续静音 ≥ 150ms 处断开（更细的真实停顿边界：每段=一个语音块，识别时时间天然对齐；
 *   短段由 MIN_SENTENCE_MS 合并兜底，不会切碎）
 * - 句子长度限制 1500ms ~ 15000ms
 */
object SentenceSplitter {

    private const val FRAME_MS = 50L
    // 静音门槛：连续静音 ≥ 该值才当作断句候选。
    // 150ms 太灵敏——句中换气/单词间停顿(150~350ms)也会被当断点，纯波形断句又碎又短。
    // 提到 350ms：只在句子级停顿下刀，句中短停顿并入同一句，断句更整、更长。
    // （有歌词/在线识别路径靠文字时间戳定边界，此值只影响兜底静音吸附粒度，几乎无感）
    private const val MIN_SILENCE_MS = 350L
    private const val MIN_SENTENCE_MS = 1500L
    private const val MAX_SENTENCE_MS = 15000L
    private const val SILENCE_RATIO = 0.075f

    fun split(path: String, onProgress: (Float) -> Unit): SplitResult {
        val (rms, totalMs) = decodeRmsFrames(path, onProgress)
        if (rms.isEmpty()) return SplitResult(emptyList(), FloatArray(0), totalMs)

        val peak = rms.max()
        if (peak <= 0f) return SplitResult(emptyList(), rms, totalMs)

        // 真实帧长：双声道音频帧数翻倍（25ms/帧），单声道 50ms/帧。
        // 用总时长/帧数校准，断句边界时间才与实际音频对齐
        val frameMsReal = if (rms.size > 0) (totalMs.toDouble() / rms.size).coerceIn(20.0, 60.0) else FRAME_MS.toDouble()
        val threshold = peak * SILENCE_RATIO
        val silent = BooleanArray(rms.size) { rms[it] < threshold }

        // 收集静音段（≥300ms）的中心点作为候选边界
        val boundaries = mutableListOf<Long>()
        var i = 0
        val n = rms.size
        while (i < n) {
            if (silent[i]) {
                var j = i
                while (j < n && silent[j]) j++
                if ((j - i) * frameMsReal >= MIN_SILENCE_MS) {
                    boundaries.add(((i + j) / 2 * frameMsReal).toLong())
                }
                i = j
            } else {
                i++
            }
        }

        // 由边界生成句子（长度约束）
        val sentences = mutableListOf<Sentence>()
        var segStart = 0L
        for (b in boundaries) {
            val len = b - segStart
            if (len >= MIN_SENTENCE_MS && len <= MAX_SENTENCE_MS) {
                sentences.add(Sentence(segStart, b))
                segStart = b
            } else if (len > MAX_SENTENCE_MS) {
                // 超长强制切分
                var cut = segStart + MAX_SENTENCE_MS
                while (b - cut > MAX_SENTENCE_MS) {
                    sentences.add(Sentence(cut - MAX_SENTENCE_MS, cut))
                    cut += MAX_SENTENCE_MS
                }
                if (b - cut >= MIN_SENTENCE_MS) {
                    sentences.add(Sentence(cut, b))
                    segStart = b
                }
            }
            // len < MIN：忽略该边界（并入下一句）
        }
        // 尾部收尾
        if (totalMs - segStart >= MIN_SENTENCE_MS) {
            sentences.add(Sentence(segStart, totalMs))
        } else if (sentences.isNotEmpty()) {
            val last = sentences.removeAt(sentences.size - 1)
            sentences.add(Sentence(last.startMs, totalMs))
        } else {
            sentences.add(Sentence(0L, totalMs))
        }

        // 波形归一化（供绘制）
        val waveform = FloatArray(rms.size) { (rms[it] / peak).coerceIn(0f, 1f) }

        // 原始边界段：相邻边界间的段（超 15s 强制切分，与上面一致；过短边界段并入下一段）
        val rawSegments = mutableListOf<Sentence>()
        var rs = 0L
        for (b in boundaries) {
            while (b - rs > MAX_SENTENCE_MS) {
                rawSegments.add(Sentence(rs, rs + MAX_SENTENCE_MS))
                rs += MAX_SENTENCE_MS
            }
            if (b - rs >= 200L) {
                rawSegments.add(Sentence(rs, b))
                rs = b
            }
        }
        if (totalMs > rs) {
            if (totalMs - rs < 200L && rawSegments.isNotEmpty()) {
                rawSegments[rawSegments.lastIndex] = Sentence(rawSegments.last().startMs, totalMs)
            } else {
                rawSegments.add(Sentence(rs, totalMs))
            }
        }
        if (rawSegments.isEmpty()) rawSegments.add(Sentence(0L, totalMs))

        return SplitResult(sentences, waveform, totalMs, rawSegments)
    }

    /** 解码音频并按帧计算 RMS，返回 (帧能量序列, 总时长ms)
     *  【v93】改 internal：B站导入的"无轨兜底波形细切"（BiliImport.recutLongByWaveform）复用同一解码。 */
    internal fun decodeRmsFrames(path: String, onProgress: (Float) -> Unit): Pair<FloatArray, Long> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(path)
        } catch (e: Exception) {
            return Pair(FloatArray(0), 0L)
        }

        var audioTrack = -1
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                audioTrack = i
                break
            }
        }
        if (audioTrack < 0) {
            extractor.release()
            return Pair(FloatArray(0), 0L)
        }

        extractor.selectTrack(audioTrack)
        val format = extractor.getTrackFormat(audioTrack)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: "audio/mpeg"
        val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        } else 44100
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
        // 声道数：决定实际帧长（双声道帧数翻倍 → 每帧实际 50ms/channels）
        val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1

        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(format, null, null, 0)
        decoder.start()

        val info = MediaCodec.BufferInfo()
        val frames = mutableListOf<Float>()
        val frameSize = (sampleRate * FRAME_MS / 1000).coerceAtLeast(1)
        var frameSum = 0.0
        var frameCount = 0
        var totalSamples = 0L
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            // 喂入数据
            if (!inputDone) {
                val inIdx = decoder.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val inBuf = decoder.getInputBuffer(inIdx)
                    if (inBuf == null) {
                        // 拿不到输入缓冲也必须把索引还回去，否则解码器输入缓冲池被耗尽后会永远解不出数据（卡死，不是崩溃）
                        decoder.queueInputBuffer(inIdx, 0, 0, 0, 0)
                        continue
                    }
                    val size = extractor.readSampleData(inBuf, 0)
                    if (size < 0) {
                        decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            // 取出解码数据
            val outIdx = decoder.dequeueOutputBuffer(info, 10_000)
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // 有些编码器输出格式变化后才是 PCM，忽略
                }
                outIdx >= 0 -> {
                    val outBuf = if (info.size > 0) decoder.getOutputBuffer(outIdx) else null
                    if (outBuf != null) {
                        outBuf.position(info.offset)
                        val shortCount = info.size / 2
                        if (shortCount > 0) {
                            val arr = ShortArray(shortCount)
                            outBuf.asShortBuffer().get(arr)
                            for (s in arr) {
                                val d = s.toDouble()
                                frameSum += d * d
                                frameCount++
                                totalSamples++
                                if (frameCount >= frameSize) {
                                    frames.add(sqrt(frameSum / frameCount).toFloat())
                                    frameSum = 0.0
                                    frameCount = 0
                                }
                            }
                        }
                    }
                    decoder.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                    if (frames.size % 100 == 0) {
                        // 总帧数按声道数校准：双声道帧数翻倍（每帧实际 50ms/channels）
                        val totalFrames = if (durationUs > 0) (durationUs * channels / 50_000).toInt() else 0
                        if (totalFrames > 0) onProgress((frames.size.toFloat() / totalFrames).coerceIn(0f, 1f))
                    }
                }
                outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (inputDone) {
                        // 输入结束但输出未完成，稍等
                        Thread.sleep(5)
                    }
                }
            }
        }
        // 尾部不足一帧的样本
        if (frameCount > 0) {
            frames.add(sqrt(frameSum / frameCount).toFloat())
        }

        decoder.stop()
        decoder.release()
        extractor.release()

        // 真实总时长优先用容器时长（双声道时 totalSamples 会翻倍，不能直接换算）；
        // 无时长元数据时兜底：样本数按声道数归一后再除采样率
        val totalMs = if (durationUs > 0) durationUs / 1000
        else totalSamples * 1000L / sampleRate / channels.coerceAtLeast(1)
        return Pair(frames.toFloatArray(), totalMs)
    }

    /** 把音频按时间段切成 16kHz 单声道 16bit WAV 切片（供逐段在线识别：
     *  每段单独识别 → 文本天然对准该段时间，无需再"对齐"）。
     *  返回与 segments 等长的文件列表；解码失败返回 null。 */
    fun sliceToWavs(path: String, segments: List<Sentence>, outDir: File): List<File>? {
        if (segments.isEmpty()) return null
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(path)
        } catch (e: Exception) {
            return null
        }
        var audioTrack = -1
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                audioTrack = i
                break
            }
        }
        if (audioTrack < 0) {
            extractor.release()
            return null
        }
        extractor.selectTrack(audioTrack)
        val format = extractor.getTrackFormat(audioTrack)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: run { extractor.release(); return null }
        val srcRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
        val channels = (if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1).coerceAtLeast(1)
        val decoder = try {
            MediaCodec.createDecoderByType(mime).also {
                it.configure(format, null, null, 0)
                it.start()
            }
        } catch (e: Exception) {
            extractor.release()
            return null
        }

        val outRate = 16000
        // 每段的源帧区间（帧 = 每声道各一个采样）
        val segStart = segments.map { it.startMs * srcRate / 1000 }
        val segEnd = segments.map { it.endMs * srcRate / 1000 }
        val buffers = ArrayList<java.io.ByteArrayOutputStream?>(segments.size)
        repeat(segments.size) { buffers.add(null) }

        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var frame = 0L          // 全局帧号
        var segIdx = 0          // 当前段下标
        var lastOutWritten = -1L // 已写出的 16k 采样号（抽取降采样用）

        fun currentBuf(): java.io.ByteArrayOutputStream? {
            while (segIdx < segments.size && frame >= segEnd[segIdx]) segIdx++
            if (segIdx >= segments.size || frame < segStart[segIdx]) return null
            var b = buffers[segIdx]
            if (b == null) { b = java.io.ByteArrayOutputStream(); buffers[segIdx] = b }
            return b
        }

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = decoder.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = decoder.getInputBuffer(inIdx)
                        if (inBuf == null) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, 0)
                            continue
                        }
                        val size = extractor.readSampleData(inBuf, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = decoder.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx >= 0 -> {
                        val outBuf = if (info.size > 0) decoder.getOutputBuffer(outIdx) else null
                        if (outBuf != null) {
                            outBuf.position(info.offset)
                            val shortCount = info.size / 2
                            if (shortCount > 0) {
                                val arr = ShortArray(shortCount)
                                outBuf.asShortBuffer().get(arr)
                                var p = 0
                                while (p + channels <= shortCount) {
                                    val buf = currentBuf()
                                    if (buf != null) {
                                        // 多声道取平均 → 单声道
                                        var sum = 0
                                        for (c in 0 until channels) sum += arr[p + c]
                                        val mono = (sum / channels).toShort()
                                        val lo = mono.toInt() and 0xFF
                                        val hi = (mono.toInt() shr 8) and 0xFF
                                        val j = frame * outRate / srcRate
                                        if (j > lastOutWritten) {
                                            // 源采样率低于 16k 时，重复当前采样补齐跳过的输出位
                                            while (lastOutWritten < j - 1) {
                                                lastOutWritten++
                                                buf.write(lo); buf.write(hi)
                                            }
                                            buf.write(lo); buf.write(hi)
                                            lastOutWritten = j
                                        }
                                    }
                                    frame++
                                    p += channels
                                }
                            }
                        }
                        decoder.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (inputDone) Thread.sleep(5)
                }
            }
        } catch (e: Exception) {
            decoder.stop(); decoder.release(); extractor.release()
            return null
        }
        decoder.stop()
        decoder.release()
        extractor.release()

        val files = mutableListOf<File>()
        for (i in segments.indices) {
            val f = File(outDir, "seg_%03d.wav".format(i))
            writeWav(f, buffers[i] ?: java.io.ByteArrayOutputStream(), outRate)
            files.add(f)
        }
        return files
    }

    /** 写 16bit 单声道 WAV（44 字节头 + PCM 数据，各数值字段小端序） */
    private fun writeWav(file: File, pcm: java.io.ByteArrayOutputStream, sampleRate: Int) {
        java.io.DataOutputStream(java.io.FileOutputStream(file)).use { o ->
            fun leInt(v: Int) {
                o.write(v and 0xFF); o.write((v ushr 8) and 0xFF)
                o.write((v ushr 16) and 0xFF); o.write((v ushr 24) and 0xFF)
            }
            fun leShort(v: Int) { o.write(v and 0xFF); o.write((v ushr 8) and 0xFF) }
            o.writeBytes("RIFF"); leInt(36 + pcm.size()); o.writeBytes("WAVE")
            o.writeBytes("fmt "); leInt(16); leShort(1); leShort(1)
            leInt(sampleRate); leInt(sampleRate * 2); leShort(2); leShort(16)
            o.writeBytes("data"); leInt(pcm.size())
            pcm.writeTo(o)
        }
    }

    /** 取音频中的静音段中心点（ms）：供视频模式长句切分当真实停顿点。
     *  解码 RMS 帧 → 找连续静音 ≥ MIN_SILENCE_MS(350ms) 的段 → 取其中心时间。
     *  仅算帧能量、不留存 PCM，内存安全（11 分钟视频约 1.3 万帧 Float，tiny）。 */
    fun silenceBoundariesMs(path: String): List<Long> {
        val (rms, totalMs) = decodeRmsFrames(path) { }
        if (rms.isEmpty() || totalMs <= 0) return emptyList()
        val peak = rms.max()
        if (peak <= 0f) return emptyList()
        val frameMsReal = (totalMs.toDouble() / rms.size).coerceIn(20.0, 60.0)
        val threshold = peak * SILENCE_RATIO
        val silent = BooleanArray(rms.size) { rms[it] < threshold }
        val out = mutableListOf<Long>()
        var i = 0
        val n = rms.size
        while (i < n) {
            if (silent[i]) {
                var j = i
                while (j < n && silent[j]) j++
                if ((j - i) * frameMsReal >= MIN_SILENCE_MS) {
                    out.add(((i + j) / 2 * frameMsReal).toLong())
                }
                i = j
            } else {
                i++
            }
        }
        return out
    }
}