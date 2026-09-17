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
    // ---- 复习（0916 新增，老数据缺失时走默认值 0，不影响读取）----
    /** 已连续答对次数：答对 +1，答错清零。攒到 [MASTERED_AT] 就算掌握了 */
    val correctStreak: Int = 0,
    /** 上一次复习的时间戳，0 = 从没复习过 */
    val lastReviewedAt: Long = 0L,
) {
    companion object {
        /** 连续答对这么多次，就算「已掌握」，日常复习队列里不再安排（列表里仍能看到） */
        const val MASTERED_AT = 3

        /** 列表里展示的星级上限（照欧路的五颗星做法） */
        const val MAX_STARS = 5
    }

    /** 是否已掌握 */
    val mastered: Boolean get() = correctStreak >= MASTERED_AT

    /**
     * 熟练度星级 0~[MAX_STARS]，只用来显示（欧路那种五颗星）。
     *
     * 映射刻意**不是** `min(streak, 5)`：那样"连对 3 次 = 掌握"就永远只到 3 星，
     * 星星再也涨不上去、看着像没练够。这里让 **3 星 = 已掌握**，
     * 继续复习还能涨到 4、5 星 —— 给"掌握之后还想再练"留出正反馈。
     *   0 次 → 0 星（列表里不显示）
     *   1 次 → 1 星
     *   2 次 → 2 星
     *   3 次 → 3 星（= 已掌握，退出复习队列）
     *   4 次 → 4 星
     *   5 次及以上 → 5 星（满星）
     */
    val stars: Int get() = correctStreak.coerceIn(0, MAX_STARS)

    /**
     * 【0916】搜索匹配：单词/句子正文、翻译、词性释义、来源，任意命中即可。
     *
     * 为什么要搜释义而不只搜正文：阿亮查词的习惯往往是"我记得翻译里有'勉强'这个词"，
     * 而不是记得拼写。只搜正文的话，词一多就只能靠翻。
     */
    fun matches(keyword: String): Boolean {
        val k = keyword.trim().lowercase()
        if (k.isEmpty()) return true
        return text.lowercase().contains(k) ||
            translation?.lowercase()?.contains(k) == true ||
            definition?.lowercase()?.contains(k) == true ||
            source.lowercase().contains(k)
    }
}

/**
 * 【0916】生词本列表筛选档。
 *
 * 为什么不做分页：分页要记页码、翻页会丢位置、删一条还要重算边界 —— 一堆状态。
 * 筛选取代分页：**列表本身不截断**（LazyColumn 天然只渲染可见项，200 条和 2000 条一样顺），
 * 用户只是缺"把不想看的先藏起来"的能力。想要什么，点一下就好，没有任何页码状态。
 */
enum class VocabFilter(val label: String) {
    ALL("全部"),
    UNMASTERED("未掌握"),
    MASTERED("已掌握"),
    WORD("单词"),
    SENTENCE("句子");

    fun match(entry: VocabEntry): Boolean = when (this) {
        ALL -> true
        UNMASTERED -> !entry.mastered
        MASTERED -> entry.mastered
        WORD -> !entry.isSentence
        SENTENCE -> entry.isSentence
    }
}

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

    /**
     * 【0916】记一次复习结果。
     * @param known true = 认识（连续次数 +1，攒够 [VocabEntry.MASTERED_AT] 算掌握）
     *              false = 不认识（连续次数清零，回到队列重来）
     */
    fun markReviewed(context: Context, id: Long, known: Boolean) {
        _entries.value = _entries.value.map { e ->
            if (e.id != id) e
            else e.copy(
                correctStreak = if (known) e.correctStreak + 1 else 0,
                lastReviewedAt = System.currentTimeMillis(),
            )
        }
        scope.launch {
            persist(context)
            // 「今天已复习」这个判据是**读文件**算出来的（见 VocabReminder.reviewedToday），
            // 所以必须等 persist 落盘之后再撤通知，否则刚好有闹钟挤在中间会读到旧文件。
            VocabReminder.onReviewed(context)
        }
    }

    /** 把某个词的「已掌握」状态清掉，重新进复习队列（长按 / 设置里用） */
    fun resetProgress(context: Context, id: Long) {
        _entries.value = _entries.value.map { e ->
            if (e.id != id) e else e.copy(correctStreak = 0, lastReviewedAt = 0L)
        }
        scope.launch { persist(context) }
    }

    /** 全局复习进度，给复习页顶部显示"还剩 N 个没掌握" */
    fun reviewStats(): Pair<Int, Int> {
        val all = _entries.value
        return all.count { !it.mastered } to all.size
    }

    fun clear(context: Context) {
        _entries.value = emptyList()
        scope.launch { persist(context) }
    }

    /**
     * 【0916】从备份批量恢复，返回**实际新增**的条数。
     *
     * 为什么不用循环调 [add]：
     * 1. [add] 里 id 取 `System.currentTimeMillis()`，连续导入会撞 id ——
     *    id 是 `remove()` / 补音标回写的唯一依据，撞了会张冠李戴。
     * 2. [add] 会把备份里已带全的音标、释义丢掉，重新走一遍网络查询。
     *
     * 这里的 id 保证**严格大于现有最大值**，且批次内不重复。
     * 已存在的（按 text 小写比对）跳过，不覆盖、不删任何现有数据。
     */
    fun restoreAll(context: Context, incoming: List<VocabEntry>): Int {
        ensureLoaded(context)
        val existKeys = _entries.value.map { it.text.trim().lowercase() }.toSet()
        val fresh = incoming
            .filter { it.text.isNotBlank() && it.text.trim().lowercase() !in existKeys }
            .distinctBy { it.text.trim().lowercase() }
        if (fresh.isEmpty()) return 0
        val base = (_entries.value.maxOfOrNull { it.id } ?: 0L)
            .coerceAtLeast(System.currentTimeMillis())
        val withIds = fresh.mapIndexed { i, e -> e.copy(id = base + i + 1) }
        _entries.value = withIds + _entries.value
        scope.launch { persist(context) }
        return withIds.size
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
                        .put("correctStreak", e.correctStreak)
                        .put("lastReviewedAt", e.lastReviewedAt)
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
                    // 老数据没有这两个字段 → 默认 0（从没复习过），不会崩也不会误判成已掌握
                    correctStreak = o.optInt("correctStreak", 0),
                    lastReviewedAt = o.optLong("lastReviewedAt", 0L),
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
