package com.xuanji.repeater

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * 音频预处理：B站下下来的原声是 m4a(AAC)，硅基流动的语音识别接口不收 m4a（会 500），
 * 必须转成它认的格式（WAV 16kHz 单声道）。全程流式，避免长音频把整个 PCM 堆进内存导致 OOM。
 * 移植自 bilibili-english-app 的 AudioConverter（仅保留 m4a->WAV + 按大小切分，供 ASR 使用）。
 */
object WavConverter {

    private const val TAG = "WavConverter"
    private const val OUT_SAMPLE_RATE = 16000
    private const val OUT_CHANNELS = 1
    private const val MAX_CHUNK_BYTES = 24L * 1024 * 1024 // 兜底值；实际按服务商走，见 splitWavIfNeeded 的 maxBytes 参数

    /**
     * 解码 m4a(AAC) -> 16kHz 单声道 WAV。解码与重采样均流式进行，内存不随音频时长增长。
     */
    fun convertToWav(input: File, outDir: File, onProgress: (Float) -> Unit = {}): File {
        if (!input.exists() || input.length() == 0L) throw RuntimeException("音频为空，无法转换。")
        outDir.mkdirs()

        var durationUs = 0L
        try {
            val probe = MediaExtractor()
            probe.setDataSource(input.absolutePath)
            for (i in 0 until probe.trackCount) {
                val fmt = probe.getTrackFormat(i)
                if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                    && fmt.containsKey(MediaFormat.KEY_DURATION)) {
                    durationUs = fmt.getLong(MediaFormat.KEY_DURATION)
                    break
                }
            }
            probe.release()
        } catch (_: Exception) {}

        val extractor = MediaExtractor()
        extractor.setDataSource(input.absolutePath)
        var audioTrack = -1
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) { audioTrack = i; break }
        }
        if (audioTrack < 0) throw RuntimeException("找不到音轨，无法转换。")
        extractor.selectTrack(audioTrack)
        val inFormat = extractor.getTrackFormat(audioTrack)
        val mime = inFormat.getString(MediaFormat.KEY_MIME)!!
        val srcRate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val srcChannels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(inFormat, null, null, 0)
        decoder.start()

        // 🔴【0929 修】原来这里叫 lastProgressTs，但**从来没被更新过**——只有声明和 checkStuck 里读它。
        // 于是只要转码超过 30 秒，不管解码多顺都会被判「卡住」抛异常。以前导入的 m4a 都短（<30s）
        // 侥幸没暴露；0929 真机导入 B站 长视频（938s）必踩，日志里 AAC 解码器每秒稳定吐 1000+ 帧、
        // 明明在正常前进，却被超时杀掉。
        // 判据改成「距上次**解出的音频时间戳前进**多久」：解码只要还在往前走就算活着，
        // 跟进度回调的节流（>2% 才报）解耦 —— 回调稀疏不代表卡住。
        var lastAliveTs = System.currentTimeMillis()
        var lastDecodedUs = -1L
        var lastReported = -1f
        fun report(p: Float) {
            val clamped = p.coerceIn(0f, 1f)
            if (clamped - lastReported > 0.02f || clamped >= 1f) {
                lastReported = clamped
                onProgress(clamped)
            }
        }
        /** 解码时间戳推进即视为「还活着」，刷新超时计时。 */
        fun markAlive(us: Long) {
            if (us > lastDecodedUs) {
                lastDecodedUs = us
                lastAliveTs = System.currentTimeMillis()
            }
        }
        fun checkStuck() {
            if (System.currentTimeMillis() - lastAliveTs > 30000L) {
                throw RuntimeException("转码卡住，已超时。该音频可能格式不兼容或解码器无响应，请换其他视频试试。")
            }
        }

        val tempPcm = File(outDir, "asr_audio_temp.pcm")
        FileOutputStream(tempPcm).use { pcmOut ->
            val info = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false
            var decodedUs = 0L
            while (!sawOutputEOS) {
                checkStuck()
                if (!sawInputEOS) {
                    val inIdx = decoder.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = decoder.getInputBuffer(inIdx)!!
                        buf.clear()
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = decoder.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx >= 0 -> {
                        val buf = decoder.getOutputBuffer(outIdx)!!
                        val chunk = ByteArray(info.size)
                        buf.get(chunk)
                        decoder.releaseOutputBuffer(outIdx, false)
                        val n = chunk.size / (2 * srcChannels)
                        val outBytes = ByteArray(n * 2)
                        for (i in 0 until n) {
                            var sum = 0
                            for (c in 0 until srcChannels) {
                                val off = (i * srcChannels + c) * 2
                                val s = ((chunk[off + 1].toInt() shl 8) or (chunk[off].toInt() and 0xFF)).toShort()
                                sum += s
                            }
                            val v = (sum / srcChannels).toShort()
                            outBytes[i * 2] = (v.toInt() and 0xFF).toByte()
                            outBytes[i * 2 + 1] = ((v.toInt() shr 8) and 0xFF).toByte()
                        }
                        pcmOut.write(outBytes)
                        if (info.presentationTimeUs > 0) decodedUs = info.presentationTimeUs
                        markAlive(decodedUs)
                        val prog = if (durationUs > 0) (decodedUs.toFloat() / durationUs).coerceIn(0f, 0.99f) else 0.5f
                        report(prog * 0.6f)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEOS = true
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { }
                    outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> { }
                }
            }
        }
        decoder.release()
        extractor.release()

        if (tempPcm.length() == 0L) throw RuntimeException("音频解码后为空，无法转换。")

        val out = File(outDir, "asr_audio.wav")
        resamplePcmFileToWav(tempPcm, out, srcRate, OUT_SAMPLE_RATE, OUT_CHANNELS) { report(it) }
        tempPcm.delete()

        Log.d(TAG, "convertToWav: ${input.length()}B -> ${out.length()}B, src=$srcRate/${srcChannels}ch -> 16k/mono")
        return out
    }

    /**
     * 超过单次上传上限就按样本边界切 WAV，每段带合法 WAV 头（超大文件兜底）。
     *
     * 🔴【0929】[maxBytes] 必须由调用方按服务商传（[SpeechRecognizer.maxUploadBytes]）：
     *   切出来的每片都要能过 `transcribe()` 的上传闸门，两边不同步就是自相矛盾。
     */
    fun splitWavIfNeeded(wav: File, outDir: File, maxBytes: Long = MAX_CHUNK_BYTES): List<File> {
        if (wav.length() <= maxBytes) return listOf(wav)
        outDir.mkdirs()
        val perSample = 2
        val maxDataBytes = (maxBytes - 44).toInt()
        val chunks = mutableListOf<File>()
        RandomAccessFile(wav, "r").use { raf ->
            raf.seek(44)
            var n = 0
            while (raf.filePointer < raf.length()) {
                val remaining = raf.length() - raf.filePointer
                var take = minOf(remaining.toInt(), maxDataBytes)
                take -= take % perSample
                if (take <= 0) break
                val chunkData = ByteArray(take)
                raf.readFully(chunkData)
                val chunkFile = File(outDir, "asr_chunk_$n.wav")
                writeWavFromData(chunkFile, chunkData, OUT_SAMPLE_RATE, OUT_CHANNELS)
                chunks.add(chunkFile)
                n++
            }
        }
        Log.d(TAG, "splitWav ${wav.length()}B -> ${chunks.size} chunks")
        return if (chunks.isEmpty()) listOf(wav) else chunks
    }

    private fun resamplePcmFileToWav(
        pcmFile: File, wavOut: File, srcRate: Int, dstRate: Int, channels: Int,
        onProgress: (Float) -> Unit = {}
    ) {
        val inSamples = (pcmFile.length() / 2).toInt()
        val ratio = srcRate.toDouble() / dstRate
        val outSamples = (inSamples / ratio).toInt().coerceAtLeast(0)

        FileOutputStream(wavOut).use { os ->
            val dataSize = outSamples * 2
            os.write(wavHeader(dataSize, dstRate, channels))
            if (outSamples == 0) return@use

            val outChunkSamples = dstRate
            val inChunkSamples = (outChunkSamples * ratio).toInt() + 3
            val buf = ShortArray(inChunkSamples)

            RandomAccessFile(pcmFile, "r").use { raf ->
                var outPos = 0
                while (outPos < outSamples) {
                    val thisChunk = minOf(outChunkSamples, outSamples - outPos)
                    val inStart = (outPos * ratio).toInt()
                    val inEnd = minOf(inStart + inChunkSamples, inSamples)
                    val inCount = inEnd - inStart
                    raf.seek(inStart * 2L)
                    for (i in 0 until inCount) {
                        val b0 = raf.read()
                        val b1 = raf.read()
                        buf[i] = (((b1 and 0xFF) shl 8) or (b0 and 0xFF)).toShort()
                    }
                    for (i in 0 until thisChunk) {
                        val pos = (outPos + i) * ratio
                        val localPos = pos - inStart
                        val i0 = localPos.toInt()
                        val frac = localPos - i0
                        val s0 = buf[i0].toInt()
                        val s1 = if (i0 + 1 < inCount) buf[i0 + 1].toInt() else s0
                        val v = (s0 + (s1 - s0) * frac).toInt().toShort()
                        os.write(v.toInt() and 0xFF)
                        os.write((v.toInt() shr 8) and 0xFF)
                    }
                    outPos += thisChunk
                    if (outSamples > 0) onProgress(0.6f + 0.4f * (outPos.toFloat() / outSamples))
                }
            }
        }
    }

    private fun wavHeader(dataSize: Int, sampleRate: Int, channels: Int): ByteArray {
        val byteRate = sampleRate * channels * 2
        val header = ByteArray(44)
        "RIFF".toByteArray().copyInto(header, 0); writeInt(header, 4, 36 + dataSize)
        "WAVE".toByteArray().copyInto(header, 8); "fmt ".toByteArray().copyInto(header, 12)
        writeInt(header, 16, 16); writeShort(header, 20, 1); writeShort(header, 22, channels)
        writeInt(header, 24, sampleRate); writeInt(header, 28, byteRate)
        writeShort(header, 32, channels * 2); writeShort(header, 34, 16)
        "data".toByteArray().copyInto(header, 36); writeInt(header, 40, dataSize)
        return header
    }

    private fun writeWavFromData(file: File, data: ByteArray, sampleRate: Int, channels: Int) {
        FileOutputStream(file).use { os ->
            os.write(wavHeader(data.size, sampleRate, channels))
            os.write(data)
        }
    }

    private fun writeInt(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte(); b[off + 1] = (v shr 8 and 0xFF).toByte()
        b[off + 2] = (v shr 16 and 0xFF).toByte(); b[off + 3] = (v shr 24 and 0xFF).toByte()
    }

    private fun writeShort(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte(); b[off + 1] = (v shr 8 and 0xFF).toByte()
    }
}
