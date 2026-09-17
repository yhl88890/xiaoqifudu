package com.xuanji.repeater

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
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
 *
 * 🔴🔴 2026-09-17 长句改走**硅基流动 TTS**（v21，阿亮定：「句子就不走有道，走其他」）：
 *   上一版让长句走**系统 TTS**，可系统 TTS 在阿亮真机上是"哑"的 —— 引擎在、`isLanguageAvailable`
 *   返 AVAILABLE、`speak()` 也返 SUCCESS，但预置音色是 server 型（连不上就静默）⇒ 长句依旧没声。
 *   ⇒ 改成 [speakLong] **三道防线**，目标是"任何情况下都得有声"：
 *       ① **硅基流动 TTS**（CosyVoice2）—— 复用**「识别」里已经填过的那把 key**，不挑长度
 *       ② 系统 TTS —— 离线，但国产 ROM 常没英文包
 *       ③ **有道切块串播** —— 按语义切碎，每块都落在有道上限内，最差也是真人音
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

    /**
     * 🔴🔴 有道**英音**的长度上限（0917 二次实测才发现的，之前一直以为英音不限长）。
     * 实测：45 字符 → 200 OK；68 字符 → 500。边界在 45~68 之间，保守取 40。
     * ⚠️ 超过这条线的文本**美音英音会双双 500** —— 那个"失败就换另一个口音"的回退机制
     *    这时候等于白写，结果是**整句彻底静默**（阿亮报的"句子不响"就是这个）。
     *    ⇒ 所以超长文本直接交给本地 TTS，见 [speak] 里的分流。
     */
    private const val UK_MAX_LEN = 40

    /** TTS 语速。先固定 1.0；以后要做"慢速跟读"再把它开成设置项 */
    private const val TTS_SPEECH_RATE = 1.0f

    // ---------- 硅基流动 TTS（长句专用，v21）----------
    /** 语音合成端点。**跟「识别」是同一个 key**，所以阿亮不用再注册任何账号 */
    private const val SF_ENDPOINT = "https://api.siliconflow.cn/v1/audio/speech"
    /** 模型名。voice 字段要写成 `模型名:音色` 这种前缀形式 */
    private const val SF_MODEL = "FunAudioLLM/CosyVoice2-0.5B"
    /** 合成+下载的总超时。长句合成比下单词音频慢，给宽一点 */
    private const val SF_TIMEOUT_MS = 20000L
    /** socket 读超时（等服务器合成的时间） */
    private const val SF_READ_TIMEOUT_MS = 20000
    /** 切块串播时，单块的播放上限（一块约 40 字符 ≈ 8 秒，给足余量） */
    private const val PLAY_TIMEOUT_MS = 20000L
    /** 日志/缓存里给硅基流动用个标记，跟有道的 type 1/2 区分开 */
    private const val SF_TYPE = 9

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
        warmUpTts()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile private var player: MediaPlayer? = null

    // ---------- 本地 TTS ----------
    /**
     * 系统 TTS 引擎。**初始化是异步的，首次可能要 1~2 秒** ⇒ 在 [init] 里就预热，
     * 不然用户点第一下小喇叭会因为引擎还没就绪而没反应。
     */
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false

    /**
     * 发音总入口。**签名别改** —— 全 App 十几处喇叭按钮都调它。
     *
     * 分流（0917）：
     *   ① 设置里选了「只用本地发音」 ⇒ 直接 TTS（离线）；TTS 用不了才退回有道
     *   ② 文本超过 [UK_MAX_LEN] ⇒ 有道两种口音**必 500**，直接交给本地 TTS
     *      —— 这就是"单词能响、长句完全不响"的修法
     *   ③ 其余 ⇒ 走有道（短的走美音、长的走英音），带本地缓存
     */
    fun speak(word: String, accent: String) {
        val w = word.trim()
        if (w.isEmpty()) return

        val src = appContext?.let { SpeakPrefs.loadSource(it) } ?: SpeakPrefs.SOURCE_AUTO

        // ① 用户强制全用本地发音
        if (src == SpeakPrefs.SOURCE_TTS) {
            if (ttsSpeak(w, accent)) return
            Log.w(TAG, "设了「只用本地发音」但 TTS 用不了（没装英文语音包？），退回有道 len=${w.length}")
            speakYoudao(w, accent)
            return
        }

        // ② 超长文本：有道两种口音都会 500，那两轮请求就别发了 ⇒ 走长句专用通道
        if (w.length > UK_MAX_LEN) {
            speakLong(w, accent)
            return
        }

        // ③ 有道人声
        speakYoudao(w, accent)
    }

    /**
     * 长句专用通道（v21）。**三道防线，目标是"任何情况下都得有声"**：
     *   ① [sfFetchToCache] 硅基流动 TTS —— 复用「识别」那把 key，不挑长度，一句一次请求
     *   ② 系统 TTS —— 离线，但国产 ROM 常没英文语音包（阿亮真机就发不出声）
     *   ③ [speakYoudaoChunked] 有道切块串播 —— 切碎了喂给有道，最差也是真人音
     *
     * 【为什么必须三道】上一版只做了 ②，结果在阿亮机器上：引擎在、`speak()` 返 SUCCESS，
     * 但预置音色是 server 型、连不上 ⇒ 静默。长句等于没修。所以 ① 顶上去当主力，③ 兜底。
     */
    private fun speakLong(w: String, accent: String) {
        scope.launch(Dispatchers.IO) {
            releasePlayer()

            // ① 硅基流动
            val f = withTimeoutOrNull(SF_TIMEOUT_MS) { sfFetchToCache(w) }
            if (f != null && withTimeoutOrNull(PREPARE_TIMEOUT_MS) { playFile(f, SF_TYPE) } == true) {
                Log.i(TAG, "长句走硅基流动 TTS len=${w.length}")
                return@launch
            }

            // ② 系统 TTS
            if (ttsSpeak(w, accent)) {
                Log.i(TAG, "硅基流动拿不到，长句退回本地 TTS len=${w.length}")
                return@launch
            }

            // ③ 有道切块（最后一道，保证有声）
            Log.w(TAG, "硅基流动+本地TTS 都不行，长句改走有道切块 len=${w.length}")
            speakYoudaoChunked(w, accent)
        }
    }

    /**
     * 向硅基流动要一段语音：POST，body 里的 `input` 就是要读的原文。
     * 响应体**直接就是 mp3 二进制**（不是 JSON、不是 URL），所以照抄 [fetchToCache] 的
     * "下到 .part 再原子改名"那套即可，缓存文件同样进 `<cacheDir>/voice/`。
     *
     * 拿不到（没填 key / 401 / 402 余额不足 / 超时 / 非 200）返回 null，交给调用方往下降级。
     * ⚠️ 返回值是 `suspend` 的：整个合成 + 下载都在协程里，不会卡 UI。
     */
    private suspend fun sfFetchToCache(w: String): File? {
        val ctx = appContext ?: return null
        val key = AsrPrefs.loadKey(ctx).trim()
        if (key.isEmpty()) {
            Log.i(TAG, "没填过 API key（设置→识别），硅基流动 TTS 跳过")
            return null
        }

        val voice = SpeakPrefs.loadSfVoice(ctx)
        val dir = File(ctx.cacheDir, CACHE_DIR)
        if (!dir.exists() && !dir.mkdirs()) return null

        // 🔴 缓存名必须带音色：换了音色要重新合成，不能跟旧音色串着用
        val file = File(dir, "sf_${voice}_${md5(w)}.mp3")
        if (file.length() > 0) return file

        val tmp = File(dir, file.name + ".part")
        if (tmp.exists()) tmp.delete()

        // ⚠️ 别手拼 JSON：`input` 里但凡有引号/换行/中文，手拼出来的就是坏 JSON
        val payload = JSONObject().apply {
            put("model", SF_MODEL)
            put("input", w)
            put("voice", "$SF_MODEL:$voice")
            put("response_format", "mp3")
            put("speed", 1.0)
        }.toString()

        var conn: HttpURLConnection? = null
        var ok = false
        try {
            conn = (URL(SF_ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = SF_READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Authorization", "Bearer $key")
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code == 200) {
                conn.inputStream.use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(8192)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                        }
                    }
                }
                ok = tmp.length() > 0
            } else {
                // 401 key 错 / 402 余额不足 / 4xx 参数不对 —— 都降级，但日志要能看出是哪种
                val msg = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull()
                Log.w(TAG, "硅基流动 TTS 失败 HTTP $code ${msg?.take(160)}")
            }
        } catch (e: CancellationException) {
            tmp.delete()
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "硅基流动 TTS 异常: ${e.message}")
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }

        if (!ok) {
            tmp.delete()
            return null
        }
        if (!tmp.renameTo(file)) {
            tmp.delete()
            return null
        }
        trimCacheIfNeeded(dir)
        return file
    }

    /**
     * 最后一道兜底：把长句切成 ≤ [UK_MAX_LEN] 的几块，依次喂给有道。
     * 切点复用断句那套 [cutTextAtWord]（优先句末标点/逗号/连接词，其次词边界），
     * 所以听感上是**在自然停顿处换气**，不会切在半个单词中间。
     */
    private suspend fun speakYoudaoChunked(w: String, accent: String) {
        val parts = chunkForYoudao(w)
        Log.i(TAG, "长句切成 ${parts.size} 块：${parts.joinToString(" | ") { it.take(16) }}")
        for ((i, p) in parts.withIndex()) {
            // 短的照旧走美音，长的走英音 —— 跟单词那边同一套规则
            val type = if (accent != "uk" && p.length <= US_MAX_LEN) 2 else 1
            val f = withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) { fetchToCache(p, type) } ?: continue
            // 必须等这一块播完再播下一块，否则几块会叠在一起
            val done = withTimeoutOrNull(PLAY_TIMEOUT_MS) { playFileAndWait(f, type) } ?: false
            if (!done) {
                Log.w(TAG, "切块第 ${i + 1}/${parts.size} 块没播完，放弃剩下的")
                return
            }
        }
    }

    /**
     * 把文本切成若干块，每块 ≤ [maxLen]，**切出来的块拼起来 == 原文（不丢字）**。
     * 给 [cutTextAtWord] 一个"希望切在 maxLen 处"的比例，让它自己在附近找语义停顿。
     */
    private fun chunkForYoudao(text: String, maxLen: Int = UK_MAX_LEN): List<String> {
        val out = ArrayList<String>()
        var rest = text.trim()
        var guard = 0
        while (rest.length > maxLen && guard++ < 40) {
            val head = cutTextAtWord(rest, maxLen.toDouble() / rest.length)
            if (head.isEmpty() || head.length >= rest.length) {
                // 兜底：语义切点没找到（比如一长串没空格的字符）⇒ 硬切，但别切在词中间
                val hard = rest.substring(0, maxLen)
                val sp = hard.lastIndexOf(' ')
                val cut = if (sp > maxLen / 2) sp else maxLen
                out.add(rest.substring(0, cut).trim())
                rest = rest.substring(cut).trim()
            } else {
                out.add(head)
                rest = rest.removePrefix(head).trim()
            }
        }
        if (rest.isNotEmpty()) out.add(rest)
        return out
    }

    /** 有道人声这条路：本地缓存优先，拿不到就换另一个口音再试 */
    private fun speakYoudao(w: String, accent: String) {
        // 别跟 TTS 的声音叠在一起
        try { tts?.stop() } catch (_: Exception) {}
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

    /**
     * 播一个本地文件并**等它整段播完**才返回（切块串播专用）。
     * 跟 [playFile] 的区别：那个 prepared 就返回（点一下喇叭够用），
     * 这个要等到 `onCompletion` —— 否则几块会同时响，糊成一团。
     */
    private suspend fun playFileAndWait(f: File, type: Int): Boolean =
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
                    mp.start()
                }
                mp.setOnCompletionListener {
                    releaseMine()
                    finish(true)
                }
                mp.setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "切块播放出错 what=$what extra=$extra type=$type")
                    releaseMine()
                    finish(false)
                    true
                }
                cont.invokeOnCancellation { releaseMine() }
                mp.setDataSource(f.absolutePath)
                mp.prepareAsync()
            } catch (e: Exception) {
                Log.w(TAG, "playFileAndWait 失败 type=$type: ${e.message}")
                releaseMine()
                finish(false)
            }
        }

    // ---------- 本地 TTS ----------

    /** 预热引擎。初始化回调是异步的，就绪前 [speak] 不会走 TTS 分支 */
    private fun warmUpTts() {
        if (tts != null) return
        val ctx = appContext ?: return
        tts = TextToSpeech(ctx) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            Log.i(TAG, "TTS 初始化 status=$status ready=$ttsReady")
            if (ttsReady) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {}
                    override fun onError(utteranceId: String?) {
                        Log.w(TAG, "TTS 朗读失败 id=$utteranceId")
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        Log.w(TAG, "TTS 朗读失败 id=$utteranceId code=$errorCode")
                    }
                })
            }
        }
    }

    /**
     * 用本地 TTS 读。返回 true = 已经交给引擎了。
     * ⚠️ 有的引擎/ROM **没有独立的英音 voice** ⇒ 自动降级成美音，别当成设置没生效。
     * ⚠️ 系统里没有英文语音包时返回 false（设置页会提示去下载）。
     */
    private fun ttsSpeak(text: String, accent: String): Boolean {
        val t = tts
        if (t == null || !ttsReady) return false

        val want = if (accent == "uk") Locale.UK else Locale.US
        val loc = when {
            t.isLanguageAvailable(want) >= TextToSpeech.LANG_AVAILABLE -> want
            t.isLanguageAvailable(Locale.US) >= TextToSpeech.LANG_AVAILABLE -> {
                if (want != Locale.US) Log.i(TAG, "TTS 没有英音 voice，降级用美音")
                Locale.US
            }
            else -> {
                Log.w(TAG, "TTS 没有英文语音包，读不了")
                return false
            }
        }

        // 跟有道的播放器互斥，别叠着响
        releasePlayer()
        return try {
            t.language = loc
            t.setSpeechRate(TTS_SPEECH_RATE)
            val r = t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "xj${System.currentTimeMillis()}")
            if (r != TextToSpeech.SUCCESS) Log.w(TAG, "TTS speak 返回 $r")
            r == TextToSpeech.SUCCESS
        } catch (e: Exception) {
            Log.w(TAG, "TTS speak 异常: ${e.message}")
            false
        }
    }

    /**
     * 自检（设置页「试听」按钮用）：拿一句范文走一遍**跟真实长句完全一样**的通道，
     * 念出来 + 用大白话回报"这次用的是哪条通道"。
     *
     * 【为什么必须要有它】长句没声音这类毛病的失败是**静默的** —— 界面上什么都不会发生，
     * 只能靠远程看 logcat 去猜卡在哪一步，阿亮为此白跑了两轮。
     * 有了它：没填 key / key 不对 / 余额不足 / 网络不通 / 能下不能播，他自己点一下就看见。
     */
    fun testLongSentence(text: String, onResult: (String) -> Unit) {
        scope.launch(Dispatchers.Main) {
            val msg = withContext(Dispatchers.IO) {
                val ctx = appContext ?: return@withContext "App 还没就绪，稍后再试"
                releasePlayer()

                if (AsrPrefs.loadKey(ctx).isBlank()) {
                    if (ttsSpeak(text, "us")) "没填 API key（去上面「识别」那个框里填一个）——这次用的是系统本地发音"
                    else "没填 API key，系统里也没有英文语音包 —— 长句子会没声音"
                } else {
                    val f = withTimeoutOrNull(SF_TIMEOUT_MS) { sfFetchToCache(text) }
                    if (f != null && withTimeoutOrNull(PREPARE_TIMEOUT_MS) { playFile(f, SF_TYPE) } == true) {
                        "在线合成正常，音色「${sfVoiceLabel(ctx)}」"
                    } else if (ttsSpeak(text, "us")) {
                        "在线合成没成功（key 不对 / 余额不足 / 网络不通）——这次用的是系统本地发音"
                    } else {
                        "在线合成没成功，系统本地发音也不行 —— 长句会自动改成有道切块朗读"
                    }
                }
            }
            onResult(msg)
        }
    }

    /** 音色 id → 中文标签，找不到就原样返回（设置页显示用） */
    private fun sfVoiceLabel(ctx: Context): String {
        val id = SpeakPrefs.loadSfVoice(ctx)
        return SpeakPrefs.SF_VOICES.firstOrNull { it.first == id }?.second ?: id
    }

    /** 设置页拿它判断要不要提示"去系统里装英文语音包" */
    enum class TtsStatus { NotReady, Ok, NoEnglish }

    fun ttsStatus(): TtsStatus {
        val t = tts ?: return TtsStatus.NotReady
        if (!ttsReady) return TtsStatus.NotReady
        return if (t.isLanguageAvailable(Locale.US) >= TextToSpeech.LANG_AVAILABLE) TtsStatus.Ok
        else TtsStatus.NoEnglish
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
