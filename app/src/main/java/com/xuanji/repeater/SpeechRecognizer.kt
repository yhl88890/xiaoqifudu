package com.xuanji.repeater

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** 单句识别结果：时间区间（毫秒）+ 文本 */
data class AsrSegment(val startMs: Long, val endMs: Long, val text: String)

/**
 * 在线语音识别服务商。
 * 三家都用 OpenAI 兼容的 multipart 上传转录接口，仅 URL 与模型名不同。
 * - 硅基流动：国内直连、免信用卡、新用户送免费额度；其语音接口只回纯文本（无段落时间戳），
 *   且提供的识别模型是 FunAudioLLM/SenseVoiceSmall（不是 whisper-large-v3），故不发送 verbose_json 参数。
 * - Groq / OpenAI：走 verbose_json + segment 时间戳，解析出每句精确时间轴。
 *
 * 🔴🔴【v44 它同时是「翻译」和「长句发音」的总开关】阿亮的原话：
 *   "你选哪个模型就填哪个模型的 API 吗？总不能是代码里地址写死的呢。"
 *   他是对的。改之前只有**识别**跟着这个选，翻译和发音都把硅基流动地址写死在代码里 ⇒
 *   切到 Groq 再把 Groq 的 Key 填进去，识别正常、**翻译和发音一起静默失败**
 *   （401 不算"排队"，不进兜底分支，界面上什么都看不出来）。
 *   ⇒ 现在 `chatBaseUrl` / `tts*` 都挂在这上面，**三处同源**。
 *   ⚠️ 以后加新家：这几组字段必须一起填全，漏一个就是那种"一半好一半坏"的状态。
 */
enum class AsrProvider(
    val label: String,
    val endpoint: String,
    val model: String,
    /** 字幕翻译用的 chat/completions 基址（OpenAI 兼容）。🔴 别在别的文件里再写死地址 */
    val chatBaseUrl: String,
    /** 是否发送 verbose_json / timestamp_granularities / language 参数 */
    val supportsVerboseJson: Boolean = true,
    /** 时间戳粒度：segment（句子级）或 word（词级）。TeleSpeech 只支持 segment */
    val granularity: String = "segment",
    /** 语音合成端点。空串 = 这家不提供 TTS ⇒ 长句直接跳过"在线合成"那一道 */
    val ttsEndpoint: String = "",
    /** 语音合成模型名。具体值查过的官方文档见各条目注释 */
    val ttsModel: String = "",
    /** voice 字段写法：true = `模型名:音色`（硅基流动 CosyVoice 要这个前缀），false = 直接写音色名 */
    val ttsVoicePrefixed: Boolean = false,
    /** 合成结果格式，同时决定缓存文件后缀 */
    val ttsFormat: String = "mp3",
    /** 单次合成的输入上限（字符）；0 = 不限。Groq 官方硬上限就是 200 */
    val ttsMaxChars: Int = 0,
    /** 这个接口认不认 `speed` 参数。Groq 官方只列了 model/input/voice/response_format ⇒ 别多塞 */
    val ttsSupportsSpeed: Boolean = true,
    /** 长句可选音色：id → 显示标签，顺序 = 设置页下拉里的顺序。🔴 写错就是 4xx ⇒ 先核对官方文档 */
    val ttsVoices: List<Pair<String, String>> = emptyList(),
) {
    SILICONFLOW(
        label = "硅基流动·星辰",
        endpoint = "https://api.siliconflow.cn/v1/audio/transcriptions",
        model = "TeleAI/TeleSpeechASR",
        chatBaseUrl = "https://api.siliconflow.cn/v1",
        supportsVerboseJson = true,
        granularity = "segment",
        // FunAudioLLM/CosyVoice2-0.5B 在硅基流动官方免费模型名单里，8 个预置音色
        ttsEndpoint = "https://api.siliconflow.cn/v1/audio/speech",
        ttsModel = "FunAudioLLM/CosyVoice2-0.5B",
        ttsVoicePrefixed = true,
        ttsFormat = "mp3",
        ttsVoices = listOf(
            "anna" to "沉稳女声", "bella" to "热情女声", "claire" to "温柔女声",
            "diana" to "活泼女声", "alex" to "沉稳男声", "benjamin" to "低沉男声",
            "charles" to "磁性男声", "david" to "阳光男声",
        ),
    ),
    GROQ(
        label = "Groq",
        endpoint = "https://api.groq.com/openai/v1/audio/transcriptions",
        model = "whisper-large-v3",
        chatBaseUrl = "https://api.groq.com/openai/v1",
        supportsVerboseJson = true,
        granularity = "word",
        // canopylabs/orpheus-v1-english：英语 TTS，6 个音色。$22 / 百万字符
        // 🔴 两个官方限制，别当没看见：① input **最多 200 字符** ② 只输出 **wav**
        ttsEndpoint = "https://api.groq.com/openai/v1/audio/speech",
        ttsModel = "canopylabs/orpheus-v1-english",
        ttsFormat = "wav",
        ttsMaxChars = 200,
        ttsSupportsSpeed = false,
        ttsVoices = listOf(
            "autumn" to "Autumn（女）", "diana" to "Diana（女）", "hannah" to "Hannah（女）",
            "austin" to "Austin（男）", "daniel" to "Daniel（男）", "troy" to "Troy（男）",
        ),
    ),
    OPENAI(
        label = "OpenAI",
        endpoint = "https://api.openai.com/v1/audio/transcriptions",
        model = "whisper-1",
        chatBaseUrl = "https://api.openai.com/v1",
        supportsVerboseJson = true,
        granularity = "word",
        // tts-1：低延迟版，支持 9 个音色（tts-1-hd 同套；gpt-4o-mini-tts 音色更多但要另配）
        ttsEndpoint = "https://api.openai.com/v1/audio/speech",
        ttsModel = "tts-1",
        ttsFormat = "mp3",
        ttsMaxChars = 4096,
        ttsVoices = listOf(
            "alloy" to "Alloy", "ash" to "Ash", "coral" to "Coral", "echo" to "Echo",
            "fable" to "Fable", "nova" to "Nova", "onyx" to "Onyx", "sage" to "Sage",
            "shimmer" to "Shimmer",
        ),
    ),
    ;

    /** 这家能不能做长句语音合成 */
    val hasTts: Boolean get() = ttsEndpoint.isNotBlank() && ttsModel.isNotBlank()

    /** 这段文本在不在单次合成的长度上限内（超了就别发请求，省一次必然失败的网络往返） */
    fun ttsFits(text: String): Boolean = hasTts && (ttsMaxChars <= 0 || text.length <= ttsMaxChars)

    /** 默认音色 id（音色表第一个） */
    val defaultTtsVoice: String get() = ttsVoices.firstOrNull()?.first ?: ""

    /** 音色 id → 显示标签；认不出来就原样返回 */
    fun voiceLabel(id: String): String = ttsVoices.firstOrNull { it.first == id }?.second ?: id

    companion object {
        fun fromName(name: String): AsrProvider =
            values().firstOrNull { it.name == name } ?: SILICONFLOW

        /** 当前设置里选的那家（省得每个调用点都写 fromName(loadProvider())） */
        fun current(ctx: android.content.Context): AsrProvider = fromName(AsrPrefs.loadProvider(ctx))
    }
}

/**
 * 识别结果：
 * - Timed：接口返回了每句时间戳（最优，直接当句子用）
 * - Plain：接口只吐纯文本（无时间戳），由调用方按本地静音边界对齐
 */
sealed interface AsrOutcome {
    data class Timed(val segments: List<AsrSegment>) : AsrOutcome
    data class Plain(val text: String) : AsrOutcome
}

/**
 * 在线语音识别：把音频文件发给识别服务商，返回带/不带时间轴的文本。
 * 复用 Translator 的套路：HttpURLConnection + multipart + JSON + 超时。
 * 失败 / 无网络 / 超时 / 文件过大 → 返回 null（调用方回退静音断句）。
 */
object SpeechRecognizer {

    /** 上传体积上限（字节）。各家 Whisper 限制 25MB，留余量。超过则放弃，避免长时上传/超时 */
    private const val MAX_UPLOAD_BYTES = 24L * 1024 * 1024

    /**
     * 识别音频。成功返回 AsrOutcome（Timed/Plain），失败返回 null。
     * 在 IO 线程执行，带 90s 总超时。
     */
    suspend fun transcribe(
        audioPath: String,
        provider: AsrProvider,
        apiKey: String,
    ): AsrOutcome? = withContext(Dispatchers.IO) {
        val audioFile = File(audioPath)
        if (!audioFile.exists() || audioFile.length() == 0L) return@withContext null
        // 文件过大直接放弃（避免上传失败/超时），回退静音断句
        if (audioFile.length() > MAX_UPLOAD_BYTES) return@withContext null

        // 长音频整曲识别耗时可达 2-3 分钟（实测 20 分钟音频约 113s），超时放宽到 240s
        withTimeoutOrNull(240_000) {
            try {
                postTranscription(audioFile, provider, apiKey)
            } catch (e: Exception) {
                Log.e("SpeechRecognizer", "transcribe failed", e)
                null
            }
        }
    }

    /** 识别单个音频切片（几秒的小文件），返回纯文本；失败返回 null。
     *  用于"先断句、后逐段识别"：每段静音切出来的音频单独识别，文本天然对准该段时间。 */
    suspend fun transcribeSlice(
        audioPath: String,
        provider: AsrProvider,
        apiKey: String,
    ): String? = withContext(Dispatchers.IO) {
        val f = File(audioPath)
        if (!f.exists() || f.length() == 0L) return@withContext null
        withTimeoutOrNull(90_000) {
            try {
                when (val r = postTranscription(f, provider, apiKey)) {
                    is AsrOutcome.Timed -> r.segments.joinToString(" ") { it.text }.trim().ifEmpty { null }
                    is AsrOutcome.Plain -> r.text.ifEmpty { null }
                    null -> null
                }
            } catch (e: Exception) {
                Log.e("SpeechRecognizer", "transcribeSlice failed", e)
                null
            }
        }
    }

    private fun postTranscription(audioFile: File, provider: AsrProvider, apiKey: String): AsrOutcome? {
        val boundary = "----XuanjiBoundary${System.currentTimeMillis()}"
        val conn = (URL(provider.endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 240_000
        }
        conn.outputStream.use { out ->
            fun part(name: String, value: String) {
                out.write("--$boundary\r\n".toByteArray())
                out.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
                out.write("$value\r\n".toByteArray())
            }
            part("model", provider.model)
            if (provider.supportsVerboseJson) {
                // 仅 OpenAI / Groq 支持 verbose_json 段落时间戳；硅基流动回纯文本，省略这些参数避免报错
                part("response_format", "verbose_json")
                // 时间戳粒度：segment（TeleSpeech 句子级，精确）/ word（Groq/OpenAI 词级）
                part("timestamp_granularities[]", provider.granularity)
            }
            // 指定英文：OpenAI/Groq 同样支持；硅基流动/SenseVoice 显式指定可避免把英文误识别成其他语种
            part("language", "en")
            out.write("--$boundary\r\n".toByteArray())
            out.write("Content-Disposition: form-data; name=\"file\"; filename=\"${audioFile.name}\"\r\n".toByteArray())
            out.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())
            audioFile.inputStream().use { it.copyTo(out) }
            out.write("\r\n".toByteArray())
            out.write("--$boundary--\r\n".toByteArray())
            out.flush()
        }

        val code = conn.responseCode
        if (code !in 200..299) {
            conn.errorStream?.bufferedReader()?.readText()?.let { Log.e("SpeechRecognizer", "err $code: $it") }
            return null
        }
        val body = conn.inputStream.bufferedReader().readText()
        return parseResult(body)
    }

    /** 清理识别文本：去掉 SenseVoice 等模型塞进来的 <|en|> <|NEUTRAL|> 之类的标记 */
    private fun cleanText(raw: String): String =
        raw.replace(Regex("<\\|[^|]*\\|>"), "").trim()

    /** 优先解析词级时间戳（精确聚合句子）；无 words 退回段落时间戳；再无则纯文本（交由调用方对齐本地静音边界） */
    private fun parseResult(json: String): AsrOutcome? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val segs = root.optJSONArray("segments")
        if (segs != null) {
            // 1) 词级时间戳：聚合为句子（按句末标点断句），时间精确到词
            val words = mutableListOf<Triple<Long, Long, String>>() // start, end, word
            for (i in 0 until segs.length()) {
                val o = segs.optJSONObject(i) ?: continue
                val ws = o.optJSONArray("words") ?: continue
                for (j in 0 until ws.length()) {
                    val w = ws.optJSONObject(j) ?: continue
                    val wt = cleanText(w.optString("word", "")).trim()
                    if (wt.isEmpty()) continue
                    val start = (w.optDouble("start", 0.0) * 1000).toLong()
                    val end = (w.optDouble("end", 0.0) * 1000).toLong()
                    if (end > start) words.add(Triple(start, end, wt))
                }
            }
            if (words.isNotEmpty()) {
                val list = mutableListOf<AsrSegment>()
                val sb = StringBuilder()
                var segStart = -1L
                var segEnd = -1L
                for ((start, end, wt) in words) {
                    if (segStart < 0) segStart = start
                    segEnd = end
                    if (sb.isNotEmpty()) sb.append(' ')
                    sb.append(wt)
                    if (wt.endsWith(".") || wt.endsWith("!") || wt.endsWith("?") || wt.endsWith("…")) {
                        list.add(AsrSegment(segStart, segEnd, sb.toString()))
                        sb.clear(); segStart = -1
                    }
                }
                if (sb.isNotEmpty() && segStart >= 0) list.add(AsrSegment(segStart, segEnd, sb.toString()))
                if (list.isNotEmpty()) return AsrOutcome.Timed(list)
            }
            // 2) 无词级 → 段落时间戳
            val list = mutableListOf<AsrSegment>()
            for (i in 0 until segs.length()) {
                val o = segs.optJSONObject(i) ?: continue
                val start = (o.optDouble("start", 0.0) * 1000).toLong()
                val end = (o.optDouble("end", 0.0) * 1000).toLong()
                val text = cleanText(o.optString("text", ""))
                if (text.isNotEmpty() && end > start) list.add(AsrSegment(start, end, text))
            }
            if (list.isNotEmpty()) return AsrOutcome.Timed(list)
        }
        val text = cleanText(root.optString("text", ""))
        return if (text.isNotEmpty()) AsrOutcome.Plain(text) else null
    }
}
