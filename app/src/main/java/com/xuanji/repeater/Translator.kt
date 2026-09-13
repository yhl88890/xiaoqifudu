package com.xuanji.repeater

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 在线翻译（免费端点，无需 key，无第三方依赖）：
 * - MyMemory api.mymemory.translated.net 与 有道 aidemo.youdao.com 并发竞速（先成功者胜，通常 0.3~1.5s 出结果）
 * - 两者都失败 → Google translate.googleapis.com 境外兜底（国内不可达）
 * - 结果按原文 hash 内存缓存，重复查看/加入生词本不重复请求。
 */
/** 单词词典信息（有道词典：美音/英音音标 + 词性释义） */
data class WordInfo(
    val usphone: String?,   // 美音音标
    val ukphone: String?,   // 英音音标
    val definition: String?, // 词性释义（多行）
) {
    /** 兼容旧调用：仅美音音标（无英音时退化） */
    val phonetic: String? get() = usphone ?: ukphone
}
object Translator {

    private const val TIMEOUT_MS = 4000

    private val cache = mutableMapOf<String, String>()
    private val cacheLock = Any()

    private val wordCache = mutableMapOf<String, WordInfo>()
    private val wordCacheLock = Any()

    private val WORD_ONLY = Regex("[A-Za-z]+(?:['’\\-][A-Za-z]+)*")

    /** 查内存缓存（同步，UI 先取旧值，命中则免网络） */
    fun cached(text: String): String? {
        val key = text.trim()
        if (key.isEmpty()) return null
        synchronized(cacheLock) {
            return cache[key.lowercase()]
        }
    }

    /** 翻译：并发竞速（有道优先）→ 失败重试有道 → Google 境外兜底；总超时 6s，全部失败返回 null */
    suspend fun translate(text: String): String? {
        val key = text.trim()
        if (key.isEmpty()) return null
        val cacheKey = key.lowercase()
        synchronized(cacheLock) {
            cache[cacheKey]?.let { return it }
        }
        // 总超时 6s：移动网络下海外域名 DNS 解析可能卡 30s+（connectTimeout 管不到 DNS），必须兜底，避免 UI 一直转圈
        val result = withTimeoutOrNull(6000) { withContext(Dispatchers.IO) {
            raceTranslate(key) ?: translateYoudao(key) ?: translateGoogle(key)
        } }
        if (result != null) {
            synchronized(cacheLock) { cache[cacheKey] = result }
        }
        return result
    }

    private val raceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** 查单词词典缓存（同步，UI 先取旧值） */
    fun lookupWordCached(text: String): WordInfo? {
        val key = text.trim().lowercase()
        if (key.isEmpty()) return null
        synchronized(wordCacheLock) {
            return wordCache[key]
        }
    }

    /** 单词词典查询（有道 dict.youdao.com/jsonapi：音标 + 词性释义；非英文单词返回 null） */
    suspend fun lookupWord(text: String): WordInfo? {
        val key = text.trim()
        if (key.isEmpty() || !WORD_ONLY.matches(key)) return null
        val cacheKey = key.lowercase()
        synchronized(wordCacheLock) {
            wordCache[cacheKey]?.let { return it }
        }
        val info = withContext(Dispatchers.IO) { fetchWordInfo(key) }
        if (info != null) {
            synchronized(wordCacheLock) { wordCache[cacheKey] = info }
        }
        return info
    }

    private fun fetchWordInfo(text: String): WordInfo? {
        return try {
            val url = "https://dict.youdao.com/jsonapi?q=" + URLEncoder.encode(text, "UTF-8")
            val conn = open(url)
            try {
                if (conn.responseCode != 200) return null
                val root = JSONObject(readAll(conn))
                val ec = root.optJSONObject("ec") ?: return null
                val words = ec.optJSONArray("word") ?: return null
                if (words.length() == 0) return null
                val w = words.optJSONObject(0) ?: return null
                val usphone = w.optString("usphone").ifEmpty { null }
                val ukphone = w.optString("ukphone").ifEmpty { null }
                val phonetic = usphone ?: ukphone
                val trs = w.optJSONArray("trs") ?: return null
                val sb = StringBuilder()
                var count = 0
                for (i in 0 until trs.length()) {
                    val trArr = trs.optJSONObject(i)?.optJSONArray("tr") ?: continue
                    if (trArr.length() == 0) continue
                    val l = trArr.optJSONObject(0)?.optJSONObject("l") ?: continue
                    val items = l.optJSONArray("i") ?: continue
                    val line = (0 until items.length()).joinToString("；") { items.optString(it) }.trim()
                    if (line.isNotEmpty()) {
                        if (count > 0) sb.append('\n')
                        sb.append(line)
                        count++
                        if (count >= 4) break
                    }
                }
                val def = sb.toString().ifEmpty { null }
                if (phonetic == null && def == null) null else WordInfo(usphone, ukphone, def)
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }
    /** MyMemory 与有道并发竞速：谁先成功用谁（全部失败返回 null，交给境外兜底）。
     *  注意用独立 scope 启动：赢家一旦产生立即返回，不等慢协程（结构化并发会等全部完成，竞速失效）。 */
    private suspend fun raceTranslate(text: String): String? {
        val jobs = listOf(
            raceScope.async { translateYoudao(text) },
            raceScope.async { delay(250); translateMyMemory(text) },
        )
        var result: String? = null
        while (result == null && jobs.any { !it.isCompleted }) {
            result = select {
                jobs.filter { !it.isCompleted }.forEach { d ->
                    d.onAwait { r -> r }
                }
            }
        }
        return result
    }

    // ---------- 有道 aidemo：POST q/from/to，errorCode=0 时取 translation[] ----------
    private fun translateYoudao(text: String): String? {
        return try {
            val url = "https://aidemo.youdao.com/trans"
            val body = "q=" + URLEncoder.encode(text, "UTF-8") + "&from=auto&to=auto"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            try {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode == 200) {
                    val root = JSONObject(readAll(conn))
                    if (root.optString("errorCode") == "0") {
                        val arr = root.optJSONArray("translation") ?: return null
                        if (arr.length() > 0) {
                            return arr.optString(0).trim().ifEmpty { null }
                        }
                    }
                }
                null
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    // ---------- MyMemory（海外备选，无匹配时会原样回吐原文，已在上面识别丢弃） ----------
    private fun translateMyMemory(text: String): String? {
        return try {
            val pair = if (containsCjk(text)) "zh-CN|en" else "en|zh-CN"
            val url = "https://api.mymemory.translated.net/get?q=" +
                URLEncoder.encode(text, "UTF-8") + "&langpair=" + pair
            val conn = open(url)
            try {
                if (conn.responseCode == 200) {
                    val root = JSONObject(readAll(conn))
                    if (root.optInt("responseStatus", -1) == 200) {
                        val t = root.optJSONObject("responseData")?.optString("translatedText") ?: return null
                        val cleaned = cleanHtml(t).trim()
                        // MyMemory 对记忆库无匹配的句子会原样回吐（匿名无 MT 额度）→ 视为失败，交给其他通道
                        if (cleaned.isEmpty() || sameText(cleaned, text)) return null
                        return cleaned
                    }
                }
                null
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    // ---------- Google（境外兜底） ----------
    private fun translateGoogle(text: String): String? {
        return try {
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=zh-CN&dt=t&q=" +
                URLEncoder.encode(text, "UTF-8")
            val conn = open(url)
            try {
                if (conn.responseCode == 200) {
                    val root = JSONArray(readAll(conn))
                    val outer = root.optJSONArray(0) ?: return null
                    val sb = StringBuilder()
                    for (i in 0 until outer.length()) {
                        val seg = outer.optJSONArray(i)
                        if (seg != null && seg.length() > 0) sb.append(seg.optString(0))
                    }
                    return sb.toString().trim().ifEmpty { null }
                }
                null
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
        return conn
    }

    private fun readAll(conn: HttpURLConnection): String {
        val input = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        input ?: return ""
        return input.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun containsCjk(text: String): Boolean =
        text.any { it.code in 0x3400..0x9FFF || it.code in 0xF900..0xFAFF }

    /** 宽松比较两段文本是否相同（忽略大小写/空白/标点），用于识别翻译接口"原样回吐" */
    private fun sameText(a: String, b: String): Boolean {
        fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        return norm(a) == norm(b)
    }

    /** 转义实体还原（MyMemory 偶发 &#39; 等） */
    private fun cleanHtml(s: String): String = s
        .replace("&#39;", "'")
        .replace("&quot;", "\"")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
}