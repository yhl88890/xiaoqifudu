package com.xuanji.repeater

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.CancellationException
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
 *
 * 🔴 2026-09-17 本地缓存（阿亮定：「长句发音只做本地缓存」）：
 *   以前每次点发音都重新 `setDataSource(URL)` 走网络 —— 同一个词听第二遍照样要等、白耗流量，
 *   还容易被有道限流。现在改成 **先查本地缓存，没有才下，下完存起来**：
 *     命中 → 直接播 `cacheDir/voice/xxx.mp3`，本地文件，秒响；
 *     未命中 → HttpURLConnection 下到 `.part` 再 rename（**原子落盘**，避免半截文件被当成完整缓存）。
 *   顺带两个好处：
 *     ① **HTTP 状态码能直接看到** —— 500 就是 500，立刻知道该换口音，不用靠超时去猜；
 *     ② 500 的 URL 再也不会喂给 MediaPlayer ⇒ 之前那个"prepare 卡死"的坑天然绕开了。
 *   缓存上限 [MAX_CACHE_FILES] 个文件，超了删最旧的。
 */
object Pronounce {

    private const val TAG = "XJPron"

    /** 多久还没准备好就认定这路音频播不了（本地文件正常 <300ms） */
    private const val PREPARE_TIMEOUT_MS = 3000L

    /**
     * 有道美音能处理的文本长度上限。
     * 实测：12 字符 OK、20 字符 500 ⇒ 取中间偏保守的 16。
     * 超过这个长度就别去试美音了 —— 试了也是白等超时。
     */
    private const val US_MAX_LEN = 16

    // ---------- 本地缓存 ----------
    /** 缓存目录 `<cacheDir>/voice/`。用 cacheDir 不用 filesDir：系统空间紧张时清掉无所谓，重下就行 */
    private const val CACHE_DIR = "voice"
    /** 缓存文件数上限，超了删最旧的。单词音频平均十几 KB，800 个约十几 MB */
    private const val MAX_CACHE_FILES = 800
    private const val CONNECT_TIMEOUT_MS = 5000
    private const val READ_TIMEOUT_MS = 8000
    /** 整段下载的上限（配合 [fetchToCache] 里的 ensureActive 才能真正打断阻塞读） */
    private const val DOWNLOAD_TIMEOUT_MS = 8000L

    @Volatile private var appContext: Context? = null

    /** 由 [XuanjiApp.onCreate] 调一次，别的地方不用管 */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

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
        // 万一主口音还是拿不到音频，换另一个再试一次
        val fallback = if (primary == 2) 1 else 2
        scope.launch(Dispatchers.IO) {
            // 先释放上一个，避免叠加
            releasePlayer()

            val f1 = withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) { fetchToCache(w, primary) }
            val ok = f1 != null && withTimeoutOrNull(PREPARE_TIMEOUT_MS) { playFile(f1, primary) } == true
            if (!ok) {
                Log.i(TAG, "type=$primary 拿不到音频（下载失败/超时），回退 type=$fallback len=${w.length}")
                val f2 = withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) { fetchToCache(w, fallback) }
                if (f2 != null) withTimeoutOrNull(PREPARE_TIMEOUT_MS) { playFile(f2, fallback) }
            }
        }
    }

    private fun urlOf(w: String, type: Int): String =
        "https://dict.youdao.com/dictvoice?audio=" + URLEncoder.encode(w, "UTF-8") + "&type=$type"

    /**
     * 拿音频文件：命中缓存直接返回；没有就下下来存好再返回。
     * 拿不到（HTTP 非 200 / 网络错 / 被取消）返回 null，由调用方决定要不要换口音。
     */
    private suspend fun fetchToCache(w: String, type: Int): File? {
        val dir = appContext?.let { File(it.cacheDir, CACHE_DIR) } ?: return null
        if (!dir.exists() && !dir.mkdirs()) return null

        val file = File(dir, "t${type}_${md5(w)}.mp3")
        if (file.length() > 0) return file

        val tmp = File(dir, file.name + ".part")
        if (tmp.exists()) tmp.delete() // 上次没下完的残留

        var conn: HttpURLConnection? = null
        var ok = false
        try {
            conn = (URL(urlOf(w, type)).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
            }
            val code = conn.responseCode
            if (code == 200) {
                conn.inputStream.use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(8192)
                        while (true) {
                            // 每读一块看一眼有没有被取消 —— 否则外层 withTimeoutOrNull 对阻塞读无效
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                        }
                    }
                }
                ok = tmp.length() > 0
            } else {
                // 500 基本就是"这个口音读不了这段文本"，调用方会换另一个口音
                Log.w(TAG, "下载失败 HTTP $code type=$type len=${w.length}")
            }
        } catch (e: CancellationException) {
            tmp.delete()
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "下载异常 type=$type: ${e.message}")
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }

        if (!ok) {
            tmp.delete()
            return null
        }
        // 原子落盘：改完名才算一份完整缓存；中途被杀也只留个 .part，不会被当成好的用
        if (!tmp.renameTo(file)) {
            tmp.delete()
            return null
        }
        trimCacheIfNeeded(dir)
        return file
    }

    /** 缓存文件太多就删最旧的，别让它无限长 */
    private fun trimCacheIfNeeded(dir: File) {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".mp3") } ?: return
        if (files.size <= MAX_CACHE_FILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_CACHE_FILES)
            .forEach { it.delete() }
    }

    /**
     * 播一个本地音频文件，成功 true。
     * ⚠️ 仍然用 prepareAsync()：本地文件虽然不会 500，但解码出问题一样会卡，别改回同步的。
     */
    private suspend fun playFile(f: File, type: Int): Boolean =
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
                mp.setOnPreparedListener {
                    player = mp
                    val dur = runCatching { mp.duration }.getOrDefault(-1)
                    mp.start()
                    Log.i(TAG, "play ok type=$type dur=$dur src=cache")
                    finish(true)
                }
                mp.setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "play error what=$what extra=$extra type=$type")
                    releaseMine()
                    finish(false)
                    true
                }
                mp.setOnCompletionListener { if (player === mp) releasePlayer() }
                // 超时/取消时在这里回收，别把播放器留在半死不活的状态
                cont.invokeOnCancellation {
                    Log.w(TAG, "prepare 超时，放弃 type=$type")
                    releaseMine()
                }
                mp.setDataSource(f.absolutePath)
                mp.prepareAsync()
            } catch (e: Exception) {
                Log.w(TAG, "playFile failed type=$type: ${e.message}")
                releaseMine()
                finish(false)
            }
        }

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray())
            // ⚠️ 必须 and 0xFF：Byte 是带符号的，负数直接 format 会输出 8 位（ffffff80 这种）
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun releasePlayer() {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
    }

    fun release() = releasePlayer()
}
