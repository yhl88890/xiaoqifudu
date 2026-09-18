package com.xuanji.repeater

import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import java.util.concurrent.Executors
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

    // ---------- 在线 TTS（长句专用，v21；🔴 v44 起跟着「识别服务商」走）----------
    // 🔴🔴 端点 / 模型名 / 音色表 / 输出格式 / 长度上限**全在 `AsrProvider` 的 tts* 字段里**，
    //    这里一个都不许写死 —— 写死就等于"用户换了服务商之后，长句静默变哑"。
    //    （v44 之前这里写死 `api.siliconflow.cn/v1/audio/speech` + CosyVoice2，就是那个 bug。）
    /** 合成+下载的总超时。长句合成比下单词音频慢，给宽一点 */
    private const val SF_TIMEOUT_MS = 20000L
    /** socket 读超时（等服务器合成的时间） */
    private const val SF_READ_TIMEOUT_MS = 20000
    /** 切块串播时，单块的播放上限（一块约 40 字符 ≈ 8 秒，给足余量） */
    private const val PLAY_TIMEOUT_MS = 20000L
    /** 日志/缓存里给"在线合成"用个标记，跟有道的 type 1/2 区分开 */
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

    /**
     * 🔴🔴【0917 阿亮报"从复习出来回到播放页，整个界面都没反应了、播放暂停也不管用"】
     * **TTS 的活必须离开主线程。**
     *
     * 为什么：`TextToSpeech` 的 `isLanguageAvailable` / `setLanguage` / `setSpeechRate` / `speak`
     * / `stop` **全是 binder 调用** —— 它们会把调用线程一路挂到引擎进程上。引擎只要卡住
     * （国产 ROM 上很常见：引擎正在重启、在等语音包、或者 build 的绑定本身是坏的），
     * 调用线程就跟着一起挂死。
     *
     * 而 [speak] 的调用方全是主线程：`onClick = { Pronounce.speak(...) }`、复习页的
     * `LaunchedEffect(current.id)`。以前这些 binder 调用就在主线程上同步跑 ⇒
     * **整个界面冻住**，连播放键都没反应 —— 阿亮描述的"整个界面又没用了"。
     *
     * 用**单线程**执行器而不是 `Dispatchers.IO`：调用顺序仍然严格按点击先后走，
     * 不会出现"两句发音并发、后点的先出声 / setLanguage 被另一个线程改掉"。
     */
    private val ttsWorker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "xj-tts").apply { isDaemon = true }
    }

    // ---------- 本地 TTS ----------
    /**
     * 系统 TTS 引擎。**初始化是异步的，首次可能要 1~2 秒** ⇒ 在 [init] 里就预热，
     * 不然用户点第一下小喇叭会因为引擎还没就绪而没反应。
     */
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false

    /**
     * 引擎初始化走到哪一步。🔴 `Pending` 只代表"**还没回调**"，**不代表没有引擎** ——
     * 0917 那次误报就是把它当成"没引擎"了（见 [ttsStatus]）。
     */
    private enum class TtsInit { Pending, Ready, Failed }
    @Volatile private var ttsInit = TtsInit.Pending

    /**
     * 引擎有没有英文语音 —— **缓存值，不在主线程现查**。
     *
     * 🔴 为什么必须缓存：`isLanguageAvailable()` 是 binder 调用。老写法是设置页组合时
     * `remember(ttsVer) { Pronounce.ttsStatus() }` 里现查一次，而组合跑在主线程 ——
     * 引擎一卡，**设置页（乃至整个界面）当场冻住**。现在改成引擎就绪后由 [ttsWorker]
     * 查一次存下来，`ttsStatus()` 退化成纯内存读取，一个 binder 调用都不剩。
     */
    @Volatile private var hasEnglishUs = false
    @Volatile private var hasEnglishUk = false
    /** 语言表查过了没有（没查完时状态报 [TtsStatus.Checking]，别误报"没英文包"） */
    @Volatile private var langChecked = false

    private val _ttsVersion = MutableStateFlow(0)

    /**
     * 引擎状态每变一次就 +1。设置页拿它当 `remember` 的 key 重算 [ttsStatus]。
     *
     * 【为什么要有它】老写法是"进设置页后轮询 3 秒，拿不到就不再刷" ——
     * 引擎绑定慢一点就永远停在错误提示上，而且用户在系统里补装了语音包也不会自己变。
     * 改成事件驱动，回调一回来界面就刷新。
     */
    val ttsVersion: StateFlow<Int> = _ttsVersion.asStateFlow()

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
        // ⚠️ 别改回"直接在这里往下跑"：调用方全是主线程，而下面每一步 TTS 都是 binder 调用，
        //    引擎一卡就把主线程（= 整个界面）挂死。理由详见 [ttsWorker] 的注释。
        ttsWorker.execute { speakInternal(w, accent) }
    }

    /** [speak] 的真身。**跑在 [ttsWorker] 上，不在主线程** —— 里面可以直接做阻塞的 TTS 调用。 */
    private fun speakInternal(w: String, accent: String) {
        val src = appContext?.let { SpeakPrefs.loadSource(it) } ?: SpeakPrefs.SOURCE_AUTO

        // ① 用户强制全用本地发音
        if (src == SpeakPrefs.SOURCE_TTS) {
            if (ttsSpeak(w, accent)) return
            // 🔴🔴 TTS 用不了时的兜底**绝不能直接喂 `speakYoudao`**：长句在有道上两种口音都必 500
            //    ⇒ 一样是静音，等于"设了本地发音就彻底没声"。
            //    0917 阿亮报"切到只用本地发音，复习里新加的句子完全没发音"就是这条 ——
            //    走**正常分流**，长句至少还有硅基流动 / 有道切块两条路。
            Log.w(TAG, "设了「只用本地发音」但 TTS 用不了，按普通通道兜底 len=${w.length}")
            if (w.length > UK_MAX_LEN) speakLong(w, accent) else speakYoudao(w, accent)
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
     *   ① [ttsFetchToCache] 在线合成 —— 用**当前服务商**的地址/模型/音色，共用「识别」那把 key
     *   ② 系统 TTS —— 离线，但国产 ROM 常没英文语音包（阿亮真机就发不出声）
     *   ③ [speakYoudaoChunked] 有道切块串播 —— 切碎了喂给有道，最差也是真人音
     *
     * 【为什么必须三道】上一版只做了 ②，结果在阿亮机器上：引擎在、`speak()` 返 SUCCESS，
     * 但预置音色是 server 型、连不上 ⇒ 静默。长句等于没修。所以 ① 顶上去当主力，③ 兜底。
     *
     * ⚠️ ① 不是每次都发得出去：这家没 TTS、没填 key、或句子超过这家单次合成的长度上限
     *    （Groq 只有 200 字符）时 [ttsFetchToCache] 直接返 null，安静地往 ②③ 降级 —— 不会哑。
     */
    private fun speakLong(w: String, accent: String) {
        scope.launch(Dispatchers.IO) {
            releasePlayer()

            // ① 在线合成（服务商决定走哪家）
            val f = withTimeoutOrNull(SF_TIMEOUT_MS) { ttsFetchToCache(w) }
            if (f != null && withTimeoutOrNull(PREPARE_TIMEOUT_MS) { playFile(f, SF_TYPE) } == true) {
                Log.i(TAG, "长句走在线合成 len=${w.length}")
                return@launch
            }

            // ② 系统 TTS
            if (ttsSpeak(w, accent)) {
                Log.i(TAG, "在线合成拿不到，长句退回本地 TTS len=${w.length}")
                return@launch
            }

            // ③ 有道切块（最后一道，保证有声）
            Log.w(TAG, "在线合成+本地TTS 都不行，长句改走有道切块 len=${w.length}")
            speakYoudaoChunked(w, accent)
        }
    }

    /**
     * 向**当前服务商**要一段语音：POST，body 里的 `input` 就是要读的原文。
     * 响应体**直接就是音频二进制**（不是 JSON、不是 URL），所以照抄 [fetchToCache] 的
     * "下到 .part 再原子改名"那套即可，缓存文件同样进 `<cacheDir>/voice/`。
     *
     * 🔴【v44】端点 / 模型 / 音色 / 格式 / 长度上限**全部从 [AsrProvider] 读**。
     *   以前这些写死在硅基流动上 ⇒ 用户切到 Groq 并填了 Groq 的 Key 之后，
     *   这里会拿着 Groq 的 Key 去敲硅基流动 ⇒ 401 ⇒ **长句静默变哑**。
     *
     * 拿不到（没填 key / 这家没 TTS / 句子超过长度上限 / 401 / 402 余额不足 / 超时 / 非 200）
     * 一律返回 null，交给调用方往下降级（系统 TTS → 有道切块）—— **不会没声音**。
     * ⚠️ 返回值是 `suspend` 的：整个合成 + 下载都在协程里，不会卡 UI。
     */
    private suspend fun ttsFetchToCache(w: String): File? {
        val ctx = appContext ?: return null
        val key = AsrPrefs.loadKey(ctx).trim()
        if (key.isEmpty()) {
            Log.i(TAG, "没填过 API key（设置→识别），在线合成跳过")
            return null
        }

        val p = AsrProvider.current(ctx)
        if (!p.hasTts) {
            Log.i(TAG, "${p.label} 没有语音合成接口，长句跳过在线合成")
            return null
        }
        // 超长就别发：这家一定拒，省一次必然失败的网络往返（Groq Orpheus 上限就是 200 字符）
        if (!p.ttsFits(w)) {
            Log.w(TAG, "${p.label} 单次合成上限 ${p.ttsMaxChars} 字符，本句 ${w.length} ⇒ 跳过在线合成")
            return null
        }

        val voice = SpeakPrefs.loadVoice(ctx, p)
        val dir = File(ctx.cacheDir, CACHE_DIR)
        if (!dir.exists() && !dir.mkdirs()) return null

        // 🔴 缓存名必须带「服务商 + 音色」：换了谁都得重新合成，不能跟别家 / 别的音色串着用。
        //    硅基流动沿用老的 `sf_` 前缀 ⇒ v44 之前下过的缓存在升级后**继续命中**，不用重下。
        val stem = if (p == AsrProvider.SILICONFLOW) "sf" else "tts_" + p.name.lowercase()
        val file = File(dir, "${stem}_${voice}_${md5(w)}.${p.ttsFormat}")
        if (file.length() > 0) return file

        val tmp = File(dir, file.name + ".part")
        if (tmp.exists()) tmp.delete()

        // ⚠️ 别手拼 JSON：`input` 里但凡有引号/换行/中文，手拼出来的就是坏 JSON
        val payload = JSONObject().apply {
            put("model", p.ttsModel)
            put("input", w)
            // 硅基流动 CosyVoice 要 `模型名:音色` 这种前缀；Groq / OpenAI 直接写音色名
            put("voice", if (p.ttsVoicePrefixed) "${p.ttsModel}:$voice" else voice)
            put("response_format", p.ttsFormat)
            // ⚠️ 只在这家认这个参数时才发（Groq 官方只列了 model/input/voice/response_format）
            if (p.ttsSupportsSpeed) put("speed", 1.0)
        }.toString()

        var conn: HttpURLConnection? = null
        var ok = false
        try {
            conn = (URL(p.ttsEndpoint).openConnection() as HttpURLConnection).apply {
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
                // 401 key 错 / 402 余额不足 / 4xx 参数不对 —— 都降级，但日志要能看出是哪种、哪家
                val msg = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull()
                Log.w(TAG, "${p.label} 在线合成失败 HTTP $code ${msg?.take(160)}")
            }
        } catch (e: CancellationException) {
            tmp.delete()
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "${p.label} 在线合成异常: ${e.message}")
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
        // ⚠️ 必须 `if (ttsReady)` 守门：`tts` 对象在初始化**失败**时也是非空的（构造已赋值、
        //    只是回调返了 ERROR），对一个绑定失败的引擎调 `stop()` 是 binder 调用，
        //    可能把调用线程挂住。没就绪就别碰它。
        if (ttsReady) try { tts?.stop() } catch (_: Exception) {}
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
        ttsInit = TtsInit.Pending
        _ttsVersion.value += 1
        try {
            tts = TextToSpeech(ctx) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                // 回调回来了才能定案：成功 = Ready，失败 = 真没有引擎（而不是"还没好"）
                ttsInit = if (ttsReady) TtsInit.Ready else TtsInit.Failed
                _ttsVersion.value += 1
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
                    // 语言可用性**在后台查一次缓存住**，别在设置页组合时现查：
                    // `isLanguageAvailable` 是 binder 调用，在主线程上遇到卡住的引擎 = 界面冻住。
                    ttsWorker.execute { refreshLanguages() }
                }
            }
        } catch (e: Exception) {
            // 构造函数本身就可能抛（引擎服务不可用）。别让整个 App 起不来，记一笔就完了
            tts = null
            ttsReady = false
            ttsInit = TtsInit.Failed
            _ttsVersion.value += 1
            Log.w(TAG, "TTS 引擎构造失败: ${e.message}")
        }
    }

    /**
     * 设置页「重新检查」：把引擎关掉重建一次。
     *
     * 【为什么需要它】用户可能刚在系统设置里装好英文语音包 / 刚启用引擎，
     * 为了让它生效去重启 App 太蠢。重建一次就能拿到新状态。
     * 注意 `warmUpTts` 开头有 `if (tts != null) return`，所以这里必须先把 `tts` 置空。
     */
    fun retryTts() {
        runCatching { tts?.shutdown() }
        tts = null
        ttsReady = false
        ttsInit = TtsInit.Pending
        hasEnglishUs = false
        hasEnglishUk = false
        langChecked = false
        _ttsVersion.value += 1
        warmUpTts()
    }

    /** 在 [ttsWorker] 上查一次英文语言可用性并存下来。**别在主线程调** */
    private fun refreshLanguages() {
        val t = tts ?: return
        hasEnglishUs = runCatching {
            t.isLanguageAvailable(Locale.US) >= TextToSpeech.LANG_AVAILABLE
        }.getOrDefault(false)
        hasEnglishUk = runCatching {
            t.isLanguageAvailable(Locale.UK) >= TextToSpeech.LANG_AVAILABLE
        }.getOrDefault(false)
        langChecked = true
        _ttsVersion.value += 1   // 通知设置页重算状态
        Log.i(TAG, "TTS 语言表 us=$hasEnglishUs uk=$hasEnglishUk")
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
     * 有了它：没填 key / key 不对 / 余额不足 / 网络不通 / 能下不能播 /
     * **这句超过这家单次合成的长度上限**，他自己点一下就看见。
     */
    fun testLongSentence(text: String, onResult: (String) -> Unit) {
        scope.launch(Dispatchers.Main) {
            val msg = withContext(Dispatchers.IO) {
                val ctx = appContext ?: return@withContext "App 还没就绪，稍后再试"
                releasePlayer()

                val p = AsrProvider.current(ctx)
                when {
                    AsrPrefs.loadKey(ctx).isBlank() ->
                        if (ttsSpeak(text, "us")) "没填 API key（去上面「识别 · 翻译」那个框里填一个）—— 这次用的是系统本地发音"
                        else "没填 API key，系统里也没有英文语音包 —— 长句子会没声音"
                    // ⚠️ 这个分支现在是活的（v44 之前 TTS 只有硅基流动一家，永远走不到）
                    !p.hasTts ->
                        if (ttsSpeak(text, "us")) "${p.label} 没有语音合成接口 —— 这次用的是系统本地发音"
                        else "${p.label} 没有语音合成接口，系统本地发音也不行 —— 长句会自动改成有道切块朗读"
                    // 🔴 刻意点名这条：Groq 单次上限只有 200 字符，长句撞上就等于"换回别的嗓子"，
                    //    不是坏了。不写出来的话，他会以为设置没生效。
                    !p.ttsFits(text) ->
                        if (ttsSpeak(text, "us")) "${p.label} 单次最多合成 ${p.ttsMaxChars} 字符，这句 ${text.length} 字符超了 —— 这次用的是系统本地发音"
                        else "${p.label} 单次最多合成 ${p.ttsMaxChars} 字符（这句 ${text.length} 字符超了），系统本地发音也不行 —— 长句会自动改成有道切块朗读"
                    else -> {
                        val f = withTimeoutOrNull(SF_TIMEOUT_MS) { ttsFetchToCache(text) }
                        if (f != null && withTimeoutOrNull(PREPARE_TIMEOUT_MS) { playFile(f, SF_TYPE) } == true) {
                            "${p.label} 在线合成正常，音色「${p.voiceLabel(SpeakPrefs.loadVoice(ctx, p))}」"
                        } else if (ttsSpeak(text, "us")) {
                            "在线合成没成功（key 不对 / 余额不足 / 网络不通）—— 这次用的是系统本地发音"
                        } else {
                            "在线合成没成功，系统本地发音也不行 —— 长句会自动改成有道切块朗读"
                        }
                    }
                }
            }
            onResult(msg)
        }
    }

    /**
     * 设置页拿它决定提示什么。
     *
     * 🔴【0917 阿亮报："红字说系统里没有可用语音引擎，我去系统设置里看，我是有引擎的"】
     * 老版本只有 `NotReady` 一档，把「**还在初始化**」和「**真的没有引擎**」混成了同一件事，
     * 而且配的文案直接断言"系统里没有可用的语音引擎"。
     * 引擎初始化是**异步**的（首次要绑引擎服务，国产 ROM 上更慢），只要比设置页那次
     * 3 秒轮询慢一点，就会被判死刑并且**永远不再刷新** —— 看着就是误报。
     * 现在拆成四档：检查中 / 正常 / 没英文语音包 / 真没有引擎，且由 [ttsVersion] 事件驱动刷新。
     */
    enum class TtsStatus { Checking, Ok, NoEnglish, NoEngine }

    fun ttsStatus(): TtsStatus {
        if (!ttsReady) {
            // 还没收到 init 回调 = 正在检查（**不是**没引擎）；回调已回来但没成功 = 真没有
            return if (ttsInit == TtsInit.Pending) TtsStatus.Checking else TtsStatus.NoEngine
        }
        // ⚠️ 这里只读缓存，**不许再调 t.isLanguageAvailable()** —— 那是 binder 调用，
        //    而本函数是在设置页组合（主线程）里跑的，引擎一卡整个界面就冻住。
        if (!langChecked) return TtsStatus.Checking
        return if (hasEnglishUs) TtsStatus.Ok else TtsStatus.NoEnglish
    }

    /**
     * 🔴【0917 二次排查用】**系统里到底有几个 TTS 引擎，能不能被我们看见。**
     *
     * 这张诊断牌存在的唯一理由：分清下面两种"没引擎"，它们**修法完全不同**：
     *   - 返回**空列表** ⇒ 是 manifest 少了 `<queries>` 的**包可见性过滤**，引擎在但看不见
     *     （现象：系统「文字转语音设置」页能试听发声，我们 App 却报没引擎）；
     *   - 返回**有引擎**但我们初始化还是失败 ⇒ 是引擎服务本身有问题／默认引擎指向了一个坏引擎。
     *
     * 只在**出错时**才把它显示给阿亮看，正常（[TtsStatus.Ok]）一个字都不显示。
     */
    fun engineReport(): String {
        val ctx = appContext ?: return "App 还没就绪"
        // ① 我们**看得见**几个引擎（受包可见性过滤影响，所以才能反映 <queries> 有没有生效）
        val found = runCatching {
            ctx.packageManager.queryIntentServices(
                Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0,
            ).mapNotNull { it.serviceInfo?.packageName }.distinct()
        }.getOrDefault(emptyList())
        // ② 系统那边登记的默认引擎（读不到就跳过，不影响主结论）
        val def = runCatching {
            android.provider.Settings.Secure.getString(ctx.contentResolver, "tts_default_synth")
        }.getOrNull()
        // ③ 我们自己连上没、有没有英文包 —— 全是缓存值，零 binder 调用
        val mine = when {
            !ttsReady && ttsInit == TtsInit.Pending -> "我们还在连引擎"
            !ttsReady -> "**我们连接引擎失败**"
            !langChecked -> "我们已连上，还在查语音包"
            hasEnglishUs -> "我们已连上，英文可用"
            else -> "**我们连上了，但查不到英文包**"
        }
        return buildString {
            append("看得见 ")
            append(found.size)
            append(" 个引擎")
            if (found.isNotEmpty()) {
                append("（")
                append(found.joinToString("、") { it.substringAfterLast('.') })
                append("）")
            }
            if (!def.isNullOrBlank()) append("；系统默认=").append(def.substringAfterLast('.'))
            append("；").append(mine)
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
