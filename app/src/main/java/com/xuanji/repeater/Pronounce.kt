package com.xuanji.repeater

import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLEncoder
import kotlin.coroutines.resume

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
 *
 * 🔴🔴 2026-09-17 句子「完全没声」的真根因（阿亮报的）：
 *   有道 **美音 type=2 对约 15 字符以上的文本会返 HTTP 500**（英音能过 44 字符，接口层实测）。
 *   更要命的是遇到 500 时 `setDataSource` + 同步 `prepare()` **会一直阻塞、既不抛异常也不回调**
 *   （设备实测：进 play(type=2) 之后日志再无下文，十几秒都不返回）。
 *   ⇒ 所以"catch 异常再回退"是没用的：异常根本不来，回退永远不触发，整句彻底静默。
 *   ⇒ 改成用 `prepareAsync()` + 超时（[PREPARE_TIMEOUT_MS]）：到点拿不到音频就换另一个口音重试。
 *   ⚠️ 别改回同步 prepare()，会把 IO 线程挂死。
 */
object Pronounce {

    private const val TAG = "XJPron"

    /** 多久还没准备好就认定这路音频拿不到（正常 <1s，超时基本都是美音 500 卡住） */
    private const val PREPARE_TIMEOUT_MS = 3000L

    /**
     * 有道美音能处理的文本长度上限。
     * 实测：12 字符 OK、20 字符 500 ⇒ 取中间偏保守的 16。
     * 超过这个长度就别去试美音了 —— 试了也是白等超时。
     */
    private const val US_MAX_LEN = 16

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile private var player: MediaPlayer? = null

    /** accent: "us" 美音 / "uk" 英音 */
    fun speak(word: String, accent: String) {
        val w = word.trim()
        if (w.isEmpty()) return
        // ⚠️ 别改回去：有道接口 type=1 是英音、type=2 是美音（反直觉，但是事实）
        //
        // 🔴 口音选择（0917）：选了美音、但文本超过 [US_MAX_LEN] 时**直接用英音**。
        //    因为美音对长文本必然 500、而且会卡住 prepare 白等好几秒（实测 6s 都没返回），
        //    与其让阿亮对着静音等 6 秒，不如立刻用英音读出来。单词短，照旧走美音。
        val wantUs = accent != "uk"
        val primary = if (wantUs && w.length <= US_MAX_LEN) 2 else 1
        // 万一主口音还是拿不到音频，换另一个再试一次（超时兜底）
        val fallback = if (primary == 2) 1 else 2
        scope.launch(Dispatchers.IO) {
            // 先释放上一个，避免叠加
            releasePlayer()
            val ok = withTimeoutOrNull(PREPARE_TIMEOUT_MS) { tryPlay(w, primary) } ?: false
            if (!ok) {
                Log.i(TAG, "type=$primary 拿不到音频（超时/出错），回退 type=$fallback len=${w.length}")
                withTimeoutOrNull(PREPARE_TIMEOUT_MS) { tryPlay(w, fallback) }
            }
        }
    }

    /**
     * 用 prepareAsync + 挂起等待，成功 true。
     * ⚠️ 不能用同步 prepare()：美音 500 时它会一直阻塞不抛异常（0917 实测）。
     */
    private suspend fun tryPlay(w: String, type: Int): Boolean =
        suspendCancellableCoroutine { cont ->
            var settled = false
            val mp = MediaPlayer()

            fun releaseMine() {
                try { mp.stop() } catch (_: Exception) {}
                try { mp.release() } catch (_: Exception) {}
                if (player === mp) player = null
            }

            fun finish(ok: Boolean) {
                if (settled) return
                settled = true
                if (cont.isActive) cont.resume(ok)
            }

            try {
                val url = "https://dict.youdao.com/dictvoice?audio=" +
                    URLEncoder.encode(w, "UTF-8") + "&type=$type"
                mp.setOnPreparedListener {
                    player = mp
                    val dur = runCatching { mp.duration }.getOrDefault(-1)
                    mp.start()
                    Log.i(TAG, "play ok type=$type dur=$dur len=${w.length}")
                    finish(true)
                }
                mp.setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "play error what=$what extra=$extra type=$type len=${w.length}")
                    releaseMine()
                    finish(false)
                    true
                }
                mp.setOnCompletionListener { if (player === mp) releasePlayer() }
                // 超时/取消时在这里回收，别把播放器留在半死不活的状态
                cont.invokeOnCancellation {
                    Log.w(TAG, "prepare 超时，放弃 type=$type len=${w.length}")
                    releaseMine()
                }
                mp.setDataSource(url)
                mp.prepareAsync()
            } catch (e: Exception) {
                Log.w(TAG, "speak failed type=$type: ${e.message}")
                releaseMine()
                finish(false)
            }
        }

    private fun releasePlayer() {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
    }

    fun release() = releasePlayer()
}
