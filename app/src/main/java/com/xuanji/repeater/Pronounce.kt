package com.xuanji.repeater

import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.URL
import java.net.URLEncoder

/**
 * 在线真人发音播放器（小喇叭）。
 * 端点：有道词典发音接口（免费、无需 key、App 已有联网权限）
 *   type=1 → 美音 (US)，type=2 → 英音 (UK)
 * 点一下播一次；再次点击同一词会重新播放（先停旧的）。
 */
object Pronounce {

    private const val TAG = "XJPron"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile private var player: MediaPlayer? = null

    /** accent: "us" 美音 / "uk" 英音 */
    fun speak(word: String, accent: String) {
        val w = word.trim()
        if (w.isEmpty()) return
        val type = if (accent == "uk") 2 else 1
        scope.launch(Dispatchers.IO) {
            try {
                // 先释放上一个，避免叠加
                releasePlayer()
                val url = "https://dict.youdao.com/dictvoice?audio=" +
                    URLEncoder.encode(w, "UTF-8") + "&type=$type"
                val mp = MediaPlayer()
                mp.setDataSource(url)
                mp.setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "play error what=$what extra=$extra word=$w")
                    releasePlayer()
                    true
                }
                mp.setOnCompletionListener { releasePlayer() }
                mp.prepare()
                player = mp
                mp.start()
            } catch (e: Exception) {
                Log.w(TAG, "speak failed: ${e.message}")
                releasePlayer()
            }
        }
    }

    private fun releasePlayer() {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
    }

    fun release() = releasePlayer()
}
