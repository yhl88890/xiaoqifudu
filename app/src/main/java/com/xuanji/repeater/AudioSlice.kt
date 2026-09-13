package com.xuanji.repeater

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * 音频区间裁剪（不重编码）：
 * - wav（PCM）：按字节直接截取（头 44 字节 + 数据区间），输出 .wav
 * - 其他格式（mp3/m4a 等）：MediaExtractor + MediaMuxer 按时间截取，输出 .m4a
 * 供"逐段识别"使用：每段静音边界切出来的音频单独上传识别，
 * 文本天然对准该段时间，不需要事后对齐。
 */
object AudioSlice {

    /** 从 src 截取 [startMs, endMs) 写入 dst。成功返回 true */
    fun extract(src: String, startMs: Long, endMs: Long, dst: String): Boolean {
        return if (src.lowercase().endsWith(".wav")) extractWav(src, startMs, endMs, dst)
        else extractMuxed(src, startMs, endMs, dst)
    }

    /** wav：PCM 字节线性截取 */
    private fun extractWav(src: String, startMs: Long, endMs: Long, dst: String): Boolean {
        return try {
            val raf = RandomAccessFile(src, "r")
            val header = ByteArray(44)
            raf.readFully(header)
            if (header[0] != 'R'.code.toByte() || header[1] != 'I'.code.toByte() ||
                header[2] != 'F'.code.toByte() || header[3] != 'F'.code.toByte()
            ) {
                raf.close(); return false
            }
            val channels = (header[22].toInt() and 0xFF) or ((header[23].toInt() and 0xFF) shl 8)
            val sampleRate = (header[24].toInt() and 0xFF) or ((header[25].toInt() and 0xFF) shl 8) or
                ((header[26].toInt() and 0xFF) shl 16) or ((header[27].toInt() and 0xFF) shl 24)
            val bitsPerSample = (header[34].toInt() and 0xFF) or ((header[35].toInt() and 0xFF) shl 8)
            val bytesPerSample = channels * bitsPerSample / 8
            if (bytesPerSample <= 0 || sampleRate <= 0) { raf.close(); return false }
            val dataSize = (raf.length() - 44).coerceAtLeast(0)
            val startByte = (startMs * sampleRate / 1000) * bytesPerSample
            val endByte = ((endMs * sampleRate / 1000) * bytesPerSample).coerceAtMost(dataSize)
            if (endByte <= startByte) { raf.close(); return false }
            raf.seek(44 + startByte)
            val payload = ByteArray((endByte - startByte).toInt())
            raf.readFully(payload)
            raf.close()

            // 重建 wav 头（16-bit PCM 单声道/立体声）
            val out = RandomAccessFile(dst, "rw")
            val outHeader = ByteArray(44)
            val dataLen = payload.size
            outHeader[0] = 'R'.code.toByte(); outHeader[1] = 'I'.code.toByte()
            outHeader[2] = 'F'.code.toByte(); outHeader[3] = 'F'.code.toByte()
            writeIntLE(outHeader, 4, 36 + dataLen)
            outHeader[8] = 'W'.code.toByte(); outHeader[9] = 'A'.code.toByte()
            outHeader[10] = 'V'.code.toByte(); outHeader[11] = 'E'.code.toByte()
            outHeader[12] = 'f'.code.toByte(); outHeader[13] = 'm'.code.toByte()
            outHeader[14] = 't'.code.toByte(); outHeader[15] = ' '.code.toByte()
            writeIntLE(outHeader, 16, 16)
            writeShortLE(outHeader, 20, 1)          // PCM
            writeShortLE(outHeader, 22, channels)
            writeIntLE(outHeader, 24, sampleRate)
            writeIntLE(outHeader, 28, sampleRate * bytesPerSample)
            writeShortLE(outHeader, 32, bytesPerSample)
            writeShortLE(outHeader, 34, bitsPerSample)
            outHeader[36] = 'd'.code.toByte(); outHeader[37] = 'a'.code.toByte()
            outHeader[38] = 't'.code.toByte(); outHeader[39] = 'a'.code.toByte()
            writeIntLE(outHeader, 40, dataLen)
            out.write(outHeader)
            out.write(payload)
            out.close()
            File(dst).length() > 0
        } catch (_: Exception) {
            false
        }
    }

    /** 其他格式：MediaExtractor + MediaMuxer 截取为 m4a（不重编码） */
    private fun extractMuxed(src: String, startMs: Long, endMs: Long, dst: String): Boolean {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(src)
            var track = -1
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; break }
            }
            if (track < 0) { extractor.release(); return false }
            extractor.selectTrack(track)

            val muxer = MediaMuxer(dst, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outTrack = muxer.addTrack(extractor.getTrackFormat(track))
            muxer.start()

            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val buf = ByteBuffer.allocate(256 * 1024)
            val info = MediaCodec.BufferInfo()
            var wrote = false
            while (true) {
                val size = extractor.readSampleData(buf, 0)
                if (size < 0) break
                val timeUs = extractor.sampleTime
                if (timeUs >= endMs * 1000) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = timeUs
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(outTrack, buf, info)
                wrote = true
                extractor.advance()
            }
            muxer.stop()
            muxer.release()
            extractor.release()
            wrote && File(dst).length() > 0
        } catch (_: Exception) {
            false
        }
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
