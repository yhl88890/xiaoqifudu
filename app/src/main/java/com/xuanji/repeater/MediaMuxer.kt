package com.xuanji.repeater

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File

/**
 * 把 B站 DASH 分离的视频流 + 音频流合成单个 MP4（MediaMuxer，无需 ffmpeg）。
 * - 有视频轨 → 视频+音频 MP4（进视频模式播）。
 * - 无视频轨（仅音频）→ 音频-only MP4（同样进视频模式，只是没画面）。
 * - 合成失败（个别编码不支持）→ 返回 false，调用方退回纯音频模式。
 */
object Mp4Muxer {

    private const val TAG = "Mp4Muxer"

    /**
     * @param videoFile 视频流文件（可空；为空则只封装音频）
     * @param audioFile 音频流文件（必需）
     * @param outFile   输出的 MP4
     * @return 是否成功生成可播放的 MP4
     */
    fun mux(videoFile: File?, audioFile: File, outFile: File): Boolean {
        if (!audioFile.exists() || audioFile.length() == 0L) {
            Log.w(TAG, "音频文件为空，无法 mux")
            return false
        }
        outFile.delete()
        val haveVideo = videoFile != null && videoFile.exists() && videoFile.length() > 0L
        val videoExtractor = if (haveVideo) MediaExtractor() else null
        var videoTrackIdx = -1
        try {
            val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            if (videoExtractor != null) {
                videoExtractor.setDataSource(videoFile!!.absolutePath)
                for (i in 0 until videoExtractor.trackCount) {
                    val f = videoExtractor.getTrackFormat(i)
                    if (f.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                        videoExtractor.selectTrack(i)
                        videoTrackIdx = muxer.addTrack(f)
                        break
                    }
                }
                if (videoTrackIdx < 0) { videoExtractor.release(); /* 没找到视频轨，当纯音频 */ }
            }

            val audioExtractor = MediaExtractor()
            audioExtractor.setDataSource(audioFile.absolutePath)
            var audioTrackIdx = -1
            for (i in 0 until audioExtractor.trackCount) {
                val f = audioExtractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioExtractor.selectTrack(i)
                    audioTrackIdx = muxer.addTrack(f)
                    break
                }
            }
            if (audioTrackIdx < 0) {
                Log.w(TAG, "没找到音轨，无法 mux")
                audioExtractor.release()
                videoExtractor?.release()
                muxer.release()
                outFile.delete()
                return false
            }

            muxer.start()

            if (videoTrackIdx >= 0 && videoExtractor != null) {
                copyTrack(videoExtractor, videoTrackIdx, muxer, videoTrackIdx)
                videoExtractor.release()
            }
            copyTrack(audioExtractor, audioTrackIdx, muxer, audioTrackIdx)
            audioExtractor.release()

            muxer.stop()
            muxer.release()
            val ok = outFile.exists() && outFile.length() > 0L
            Log.d(TAG, "mux -> ${outFile.length()}B, video=$haveVideo, ok=$ok")
            return ok
        } catch (e: Exception) {
            Log.e(TAG, "mux failed", e)
            runCatching { outFile.delete() }
            return false
        }
    }

    private fun copyTrack(extractor: MediaExtractor, srcTrack: Int, muxer: MediaMuxer, dstTrack: Int) {
        val info = MediaCodec.BufferInfo()
        var buf = java.nio.ByteBuffer.allocate(2 * 1024 * 1024)
        var firstTimeUs = -1L
        while (true) {
            val size = extractor.readSampleData(buf, 0)
            if (size < 0) break
            if (size > buf.capacity()) buf = java.nio.ByteBuffer.allocate(size + 1024)
            if (firstTimeUs < 0) firstTimeUs = extractor.sampleTime
            info.size = size
            info.presentationTimeUs = (extractor.sampleTime - firstTimeUs).coerceAtLeast(0L)
            // 【0903 修复】必须保留同步帧(关键帧)标志！原来只传 EOS，视频轨没有 sync sample，
            // 部分播放器找不到关键帧 → 有声音没画面（黑屏）/ 解码异常出杂音。
            // MediaExtractor.SAMPLE_FLAG_SYNC(1) 与 MediaCodec.BUFFER_FLAG_KEY_FRAME(1) 同值。
            var flags = 0
            if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0)
                flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
            if ((extractor.sampleFlags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0)
                flags = flags or MediaCodec.BUFFER_FLAG_END_OF_STREAM
            info.flags = flags
            info.offset = 0
            muxer.writeSampleData(dstTrack, buf, info)
            extractor.advance()
        }
    }
}
