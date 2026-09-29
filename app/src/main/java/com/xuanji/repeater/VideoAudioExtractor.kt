package com.xuanji.repeater

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * 视频音轨抽取：从视频 Uri 解码音频为 16kHz 单声道 16-bit PCM WAV。
 * 复用 SentenceSplitter 的 MediaExtractor + MediaCodec 解码套路（读的是 content Uri，无需先拷贝整段视频）。
 * 输出 WAV 是为了喂给 ASR（硅基流动/TeleSpeechASR 只收 WAV/MP3，m4a 直接 500）。
 *
 * 【重要 · 必须流式】解码一帧 → 重采样 → 追加写文件，绝不把整段 PCM 攒在内存。
 * 早期实现用 `MutableList<Float>` 逐样本装箱攒全量：3 分钟视频 ≈ 800 万个 Float 对象 ≈ 160MB+，
 * 中低端机直接 OutOfMemoryError 闪退；且 OOM 是 Error 不是 Exception，外层 catch(Exception) 抓不住。
 * 改为流式后内存恒定在几十 KB，与视频长度无关。
 *
 * 【分片】支持 [startUs, endUs) 区间抽取（长视频分片识别用）：seek 到起点后解码，到终点停。
 * seek 用 SEEK_TO_CLOSEST_SYNC，音频帧极小（AAC 1024 帧 ≈ 23ms），起点误差 <100ms，对句子级字幕无影响。
 *
 * 返回 null = 成功（WAV 已写到 dst）；返回非空字符串 = 失败原因（可直接显示给用户）。
 */
object VideoAudioExtractor {

    private const val OUT_RATE = 16000
    /**
     * 抽音轨的**兜底**上限（只在调用方没传 maxBytes 时生效）。
     * 🔴 正常路径一律由调用方按服务商传（`SpeechRecognizer.maxUploadBytes(provider)`）——
     * 上传闸门和抽音轨上限必须同源，否则会抽出自己过不了闸的音频，白抽一遍还报错。
     * 这里保留 23MB 是给"没有 provider 上下文"的调用兜底，别当通用值。
     */
    private const val MAX_BYTES = 23L * 1024 * 1024

    /**
     * 探测时长（微秒）；读不到返回 -1。用于决定整段识别还是分片识别。
     * 两条独立通道：①音轨 format 的 KEY_DURATION ②MediaMetadataRetriever（整片时长）。
     * 只靠 ① 的话，某些 mkv/网络流/索引损坏的文件读不到 → 上层按整段走 → 长视频撞 23MB 上限白抽一遍。
     */
    fun probeDurationUs(context: android.content.Context, uri: Uri): Long {
        // 通道 ①：音轨自己的 duration
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    if (f.containsKey(MediaFormat.KEY_DURATION)) {
                        val d = f.getLong(MediaFormat.KEY_DURATION)
                        if (d > 0) return d
                    }
                    break
                }
            }
        } catch (_: Throwable) {
            // 落到通道 ②
        } finally {
            try { extractor.release() } catch (_: Throwable) { }
        }
        // 通道 ②：元数据检索器拿整片时长（毫秒 → 微秒）
        var retriever: android.media.MediaMetadataRetriever? = null
        return try {
            retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val ms = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: -1L
            if (ms > 0) ms * 1000 else -1L
        } catch (_: Throwable) {
            -1L
        } finally {
            try { retriever?.release() } catch (_: Throwable) { }
        }
    }

    /**
     * @param startUs 起始时间（微秒），<0 表示从头
     * @param endUs 结束时间（微秒），<0 表示到尾
     * @param maxBytes 抽出的 WAV 上限（字节）。🔴【0929】必须由调用方按服务商传进来
     *   （[SpeechRecognizer.maxUploadBytes]），别再各自写死：上传闸门和抽音轨上限一旦不同步，
     *   就会抽出一段自己过不了闸的音频，白抽一遍还报错。
     * @return null=成功，非空=失败原因
     */
    fun extractToWav(
        context: android.content.Context,
        uri: Uri,
        dst: String,
        startUs: Long = -1L,
        endUs: Long = -1L,
        onProgress: (Int) -> Unit = {},
        maxBytes: Long = MAX_BYTES,
    ): String? {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var dos: DataOutputStream? = null
        return try {
            try {
                extractor.setDataSource(context, uri, null)
            } catch (_: Throwable) {
                return "读不到这个视频（可能已被删除或无权限）"
            }

            var audioTrack = -1
            var mime = ""
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioTrack = i
                    mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                    break
                }
            }
            if (audioTrack < 0) return "这个视频没有音轨"

            extractor.selectTrack(audioTrack)
            val fmt = extractor.getTrackFormat(audioTrack)
            val inRate = if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            val channels = if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
            val durUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else -1L

            // 分片起点：seek 过去（音频无关键帧概念，落到最近可用样本，误差可忽略）
            val baseUs = if (startUs > 0) startUs else 0L
            if (startUs > 0) {
                try { extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC) } catch (_: Throwable) { }
            }
            // 进度分母：优先用 [baseUs, endUs) 的实际跨度
            val spanUs = when {
                endUs > baseUs -> endUs - baseUs
                durUs > baseUs -> durUs - baseUs
                else -> -1L
            }

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(fmt, null, null, 0)
            decoder.start()

            val out = File(dst)
            dos = DataOutputStream(BufferedOutputStream(FileOutputStream(out), 1 shl 16))
            dos.write(ByteArray(44)) // 占位 header，数据写完后回填真实长度

            val ratio = OUT_RATE.toDouble() / inRate.toDouble()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var tooLong = false
            var lastPct = -1

            // 流式重采样状态（内存恒定，不攒全量）
            var prev = 0f        // 上一个输入样本 x[i-1]
            var inIndex = 0L     // 当前输入样本全局索引 i
            var nextOutIdx = 0L  // 下一个待生成的输出样本索引 m
            var totalOut = 0L

            val chunk = ByteArray(1 shl 14)
            var chunkPos = 0

            while (!outputDone && !tooLong) {
                if (!inputDone) {
                    val inIdx = decoder.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = decoder.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val t = extractor.sampleTime
                            // 到达分片终点就停止喂数据（发 EOS 让解码器收尾）
                            val size = if (endUs > 0 && t >= endUs) -1 else extractor.readSampleData(inBuf, 0)
                            if (size < 0) {
                                decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                decoder.queueInputBuffer(inIdx, 0, size, t, 0)
                                extractor.advance()
                            }
                        }
                    }
                }
                val outIdx = decoder.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { /* 输出即 PCM，忽略 */ }
                    outIdx >= 0 -> {
                        val outBuf = decoder.getOutputBuffer(outIdx)
                        if (outBuf != null && info.size > 0) {
                            outBuf.position(info.offset)
                            val n = info.size / 2
                            if (n > 0) {
                                val shorts = ShortArray(n)
                                outBuf.asShortBuffer().get(shorts)
                                var k = 0
                                while (k + channels <= n) {
                                    // 多声道下混为单声道
                                    var sum = 0
                                    for (c in 0 until channels) sum += shorts[k + c].toInt()
                                    val x = (sum.toFloat() / channels) / 32768f
                                    // 生成所有"输入坐标为 m/ratio 且落在 [i-1, i)"的输出样本，用 prev..x 线性插值
                                    while (nextOutIdx.toDouble() < inIndex * ratio) {
                                        val srcPos = nextOutIdx / ratio
                                        var frac = (srcPos - (inIndex - 1)).toFloat()
                                        if (frac < 0f) frac = 0f else if (frac > 1f) frac = 1f
                                        val s = clampShort(prev + (x - prev) * frac)
                                        if (chunkPos == chunk.size) { dos.write(chunk, 0, chunkPos); chunkPos = 0 }
                                        chunk[chunkPos++] = (s.toInt() and 0xFF).toByte()
                                        chunk[chunkPos++] = ((s.toInt() shr 8) and 0xFF).toByte()
                                        totalOut++
                                        nextOutIdx++
                                    }
                                    prev = x
                                    inIndex++
                                    k += channels
                                }
                            }
                        }
                        decoder.releaseOutputBuffer(outIdx, false)
                        if (spanUs > 0 && info.presentationTimeUs > baseUs) {
                            val pct = (((info.presentationTimeUs - baseUs) * 100) / spanUs).toInt().coerceIn(0, 100)
                            if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        if (totalOut * 2 > maxBytes) tooLong = true
                    }
                    outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> { if (inputDone) Thread.sleep(5) }
                }
            }

            if (chunkPos > 0) dos.write(chunk, 0, chunkPos)
            dos.flush()
            dos.close()
            dos = null

            when {
                tooLong -> { out.delete(); "视频太长：抽出的音频超出单片上限，且读不到时长无法自动分片（可先导入 .srt 字幕）" }
                totalOut == 0L -> { out.delete(); "没解出音频数据" }
                else -> {
                    // 回填 WAV header（此时才已知数据长度）
                    RandomAccessFile(out, "rw").use { raf ->
                        writeHeader(raf, (totalOut * 2).toInt(), OUT_RATE)
                    }
                    null
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("VideoAudioExtractor", "extractToWav failed", t)
            "抽取音轨失败：${t.message ?: t.javaClass.simpleName}"
        } finally {
            try { dos?.close() } catch (_: Throwable) { }
            try { decoder?.stop() } catch (_: Throwable) { }
            try { decoder?.release() } catch (_: Throwable) { }
            try { extractor.release() } catch (_: Throwable) { }
        }
    }

    private fun clampShort(v: Float): Short {
        val c = (v * 32767).coerceIn(-32768f, 32767f).toInt()
        return c.toShort()
    }

    /** 写 44 字节标准 WAV 头（PCM / 单声道 / 16-bit） */
    private fun writeHeader(raf: RandomAccessFile, dataLen: Int, rate: Int) {
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        writeIntLE(header, 4, 36 + dataLen)
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        writeIntLE(header, 16, 16)
        writeShortLE(header, 20, 1)       // PCM
        writeShortLE(header, 22, 1)       // 单声道
        writeIntLE(header, 24, rate)
        writeIntLE(header, 28, rate * 2)  // byte rate = rate * channels * bytesPerSample
        writeShortLE(header, 32, 2)       // block align
        writeShortLE(header, 34, 16)      // 16-bit
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        writeIntLE(header, 40, dataLen)
        raf.seek(0)
        raf.write(header)
    }

    private fun writeIntLE(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v shr 8) and 0xFF).toByte()
        b[off + 2] = ((v shr 16) and 0xFF).toByte()
        b[off + 3] = ((v shr 24) and 0xFF).toByte()
    }

    private fun writeShortLE(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v shr 8) and 0xFF).toByte()
    }
}
