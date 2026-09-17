package com.xuanji.repeater

import android.content.Context

/**
 * 生词本列表的**排序档**（0917 阿亮要求："按加入时间排序、按开头字母排序，这样找词就容易多了"）。
 *
 * 【为什么这一档**要**持久化，而隔壁 [VocabFilter] 那档刻意不持久化】
 * 两件事性质不一样：
 * - **筛选**会让"看不见的词"消失 —— 记住的话他进来只看到 3 条，第一反应是"我词怎么没了"，
 *   所以每次进生词本都从「全部」开始（见 VocabPage 里那段注释）。
 * - **排序**不会让任何词消失，只是换个顺序 —— 记住它反而更顺手，不用每次重选。
 *   （他 0917 明确选了"记住"。）
 *
 * ⚠️ `apply()` 只在**显示层**用，**绝不要去改 `VocabStore.entries` 本身**：
 * 那是落库顺序，改了会连带影响导出/备份的顺序。
 */
enum class VocabSort(val label: String) {
    /** 最新加入的排最前 —— **默认档，等价于改造前的老顺序**（`VocabStore.add` 是往头部插） */
    ADDED_DESC("最新在前"),

    ADDED_ASC("最早在前"),

    /** 按开头字母排。忽略大小写；单词和句子混在一起排（"Time flies." 按 t 算） */
    ALPHA("A→Z");

    /**
     * 排好序的一份**新列表**。
     *
     * 字母档加 `addedAt` 做第二关键字：同开头字母时（比如 Time flies. 和 The best way…）
     * 至少顺序稳定，不会因为底层列表抖动而每次重排不一样。
     */
    fun apply(list: List<VocabEntry>): List<VocabEntry> = when (this) {
        ADDED_DESC -> list.sortedByDescending { it.addedAt }
        ADDED_ASC -> list.sortedBy { it.addedAt }
        ALPHA -> list.sortedWith(compareBy({ it.text.trim().lowercase() }, { it.addedAt }))
    }
}

/** 生词本的显示偏好。目前只有"排序"一项。 */
object VocabPrefs {

    private const val NAME = "vocab_prefs"
    private const val KEY_SORT = "sort"

    fun loadSort(context: Context): VocabSort {
        // ⚠️ 存的是**枚举名**不是序号：以后加档位/改顺序时，存序号会让老值全错位到别的档
        val raw = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_SORT, null) ?: return VocabSort.ADDED_DESC
        return runCatching { VocabSort.valueOf(raw) }.getOrDefault(VocabSort.ADDED_DESC)
    }

    fun saveSort(context: Context, sort: VocabSort) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SORT, sort.name).apply()
    }
}
