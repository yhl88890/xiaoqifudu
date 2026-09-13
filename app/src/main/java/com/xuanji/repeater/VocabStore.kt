package com.xuanji.repeater

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 生词本条目：一个单词或一个完整句子 */
data class VocabEntry(
    val id: Long,
    val text: String,
    val translation: String?,
    /** 单词美音音标（有道词典，如 həˈləʊ），仅单词条目 */
    val usphone: String? = null,
    /** 单词英音音标（有道词典，如 həˈləʊ），仅单词条目 */
    val ukphone: String? = null,
    /** 单词词性释义（有道词典，多行），仅单词条目 */
    val definition: String? = null,
    /** 来源（歌曲标题等） */
    val source: String,
    val addedAt: Long,
    val isSentence: Boolean,
)

/**
 * 生词本：JSON 文件持久化（filesDir/vocab.json）+ StateFlow 响应式。
 * 加入立即落库；翻译未命中缓存时后台补取并回写。
 */
object VocabStore {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _entries = MutableStateFlow<List<VocabEntry>>(emptyList())
    val entries: StateFlow<List<VocabEntry>> = _entries.asStateFlow()

    @Volatile
    private var loaded = false

    private fun file(context: Context): File = File(context.filesDir, "vocab.json")

    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        _entries.value = readFromFile(context)
        // 启动后为缺音标的单词（老数据 / 旧版只存了美音）补查有道，回填美音+英音标
        scope.launch {
            val needFix = _entries.value.filter { !it.isSentence && it.usphone == null && it.ukphone == null }
            if (needFix.isNotEmpty()) {
                var changed = false
                val updated = _entries.value.toMutableList()
                for (e in needFix) {
                    val info = Translator.lookupWord(e.text) ?: continue
                    val idx = updated.indexOfFirst { it.id == e.id }
                    if (idx >= 0) {
                        updated[idx] = updated[idx].copy(usphone = info.usphone, ukphone = info.ukphone)
                        changed = true
                    }
                }
                if (changed) {
                    _entries.value = updated
                    persist(context)
                }
            }
        }
    }

    fun contains(text: String): Boolean {
        val key = text.trim().lowercase()
        if (key.isEmpty()) return false
        return _entries.value.any { it.text.trim().lowercase() == key }
    }

    fun add(context: Context, text: String, source: String, isSentence: Boolean) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || contains(trimmed)) return
        val entry = VocabEntry(
            id = System.currentTimeMillis(),
            text = trimmed,
            translation = Translator.cached(trimmed),
            source = source,
            addedAt = System.currentTimeMillis(),
            isSentence = isSentence,
        )
        _entries.value = listOf(entry) + _entries.value
        scope.launch { persist(context) }

        // 单词：后台补取词典（音标 + 词性释义），随后回写
        if (!isSentence) {
            scope.launch {
                val info = Translator.lookupWord(trimmed)
                if (info != null) {
                    _entries.value = _entries.value.map {
                        if (it.id == entry.id) it.copy(usphone = info.usphone, ukphone = info.ukphone, definition = info.definition) else it
                    }
                    persist(context)
                }
            }
        }
        // 翻译未缓存：后台补取后回写（加入时立即生效，翻译随后出现）
        if (entry.translation == null) {
            scope.launch {
                val t = Translator.translate(trimmed)
                if (t != null) {
                    _entries.value = _entries.value.map {
                        if (it.id == entry.id) it.copy(translation = t) else it
                    }
                    persist(context)
                }
            }
        }
    }

    fun remove(context: Context, id: Long) {
        _entries.value = _entries.value.filterNot { it.id == id }
        scope.launch { persist(context) }
    }

    fun clear(context: Context) {
        _entries.value = emptyList()
        scope.launch { persist(context) }
    }

    private suspend fun persist(context: Context) = withContext(Dispatchers.IO) {
        try {
            val arr = JSONArray()
            for (e in _entries.value) {
                arr.put(
                    JSONObject()
                        .put("id", e.id)
                        .put("text", e.text)
                        .put("translation", e.translation ?: JSONObject.NULL)
                        .put("usphone", e.usphone ?: JSONObject.NULL)
                        .put("ukphone", e.ukphone ?: JSONObject.NULL)
                        .put("definition", e.definition ?: JSONObject.NULL)
                        .put("source", e.source)
                        .put("addedAt", e.addedAt)
                        .put("isSentence", e.isSentence)
                )
            }
            file(context).writeText(JSONObject().put("entries", arr).toString())
        } catch (_: Exception) {
        }
    }

    private fun readFromFile(context: Context): List<VocabEntry> {
        return try {
            val f = file(context)
            if (!f.exists()) return emptyList()
            val root = JSONObject(f.readText())
            val arr = root.getJSONArray("entries")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                // 兼容升级前老数据：旧字段 phonetic（仅美音）兜底；ukphone 暂用 phonetic 避免空杠，启动补查会填入真实英音
                val oldPh = if (o.isNull("phonetic")) null else o.optString("phonetic")
                VocabEntry(
                    id = o.optLong("id", System.currentTimeMillis() + i),
                    text = o.optString("text"),
                    translation = if (o.isNull("translation")) null else o.optString("translation"),
                    usphone = if (o.isNull("usphone")) oldPh else o.optString("usphone"),
                    ukphone = if (o.isNull("ukphone")) oldPh else o.optString("ukphone"),
                    definition = if (o.isNull("definition")) null else o.optString("definition"),
                    source = o.optString("source"),
                    addedAt = o.optLong("addedAt", 0L),
                    isSentence = o.optBoolean("isSentence", false),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

private val WORD_REGEX = Regex("[A-Za-z]+(?:['’\\-][A-Za-z]+)*")

/** 从歌词句子中提取英文单词（保留原大小写、按小写去重、保持出现顺序） */
fun extractLyricWords(text: String): List<String> {
    val seen = mutableSetOf<String>()
    val out = mutableListOf<String>()
    for (m in WORD_REGEX.findAll(text)) {
        val w = m.value
        if (seen.add(w.lowercase())) out.add(w)
    }
    return out
}
