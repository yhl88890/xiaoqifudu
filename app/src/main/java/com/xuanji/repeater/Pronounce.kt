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
 *   type=1 → 英音 (UK)，type=2 → 美音 (US)
 * 点一下播一次；再次点击同一词会重新播放（先停旧的）。
 *
 * 🔴 2026-09-16 修正：这两个 type 之前写反了（阿亮点两颗喇叭听出来"英音美音搞反了"）。
 * 有道官方约定是 type=1 英音、type=2 美音；我原来按"us→1 / uk→2"反着映射，
 * 导致点「美音」播英音、点「英音」播美音，从 0824 一直错到现在。
 * 多份资料一致确认（deepwiki 的 Youdao Pronunciation API 文档 + 多篇 CSDN 实测文章）。
 */
object Pronounce {

    private const val TAG = "XJPron"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile private var player: MediaPlayer? = null

    /** accent: "us" 美音 / "uk" 英音 */
    fun speak(word: String, accent: String) {
        val w = word.trim()
        if (w.isEmpty()) return
        // ⚠️ 别改回去：有道接口 type=1 是英音、type=2 是美音（反直觉，但是事实）
        val type = if (accent == "uk") 1 else 2
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
