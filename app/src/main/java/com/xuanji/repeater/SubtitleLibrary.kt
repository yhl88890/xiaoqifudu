package com.xuanji.repeater

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 一条归档字幕：来源（audio=音频模式 / video=视频模式）+ 标题 + 句子 + 入库时间 + 源文件路径（可空） */
data class SubtitleEntry(
    val source: String,
    val title: String,
    val sentences: List<Sentence>,
    val updatedAt: Long,
    val path: String? = null,   // 【0903】源文件路径：阅读模式「全部」点条目→定位文件→直接播放
    /**
     * 【0929】这份字幕是不是残缺的（分片识别中途失败/有段被跳过）。
     *
     * 为什么要存下来：当次识别能在 asrMsg 看到"3/5 段成功"，但**下次打开视频这句提示就没了**，
     * 界面上只剩"生成字幕"四个字 ⇒ 用户会一直以为字幕是全的，用到一半才发现少了。
     * 🔴 老数据没这个字段 ⇒ optBoolean 默认 false，兼容，不用迁移。
     */
    val incomplete: Boolean = false,
)

/**
 * 字幕库：把音频模式 / 视频模式播过（断句 / 识别 / 导入）的字幕统一归档到 SharedPreferences，
 * 供阅读模式「全部」标签页汇总复习。同 source+title 只留最新一份（去重更新），最多保留 200 条。
 * 复用 MainActivity 的 encodeSentences / decodeSentences 序列化句子，不引新依赖。
 */
object SubtitleLibrary {
    const val SOURCE_AUDIO = "audio"
    const val SOURCE_VIDEO = "video"

    private const val PREFS = "xuanji_subtitles"
    private const val KEY = "entries"
    private const val MAX_ENTRIES = 200

    private fun sp(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 归档一条字幕（source + title 作为去重键）。空标题或空句子直接忽略。 */
    fun save(
        context: Context,
        source: String,
        title: String,
        sentences: List<Sentence>,
        path: String? = null,
        incomplete: Boolean = false,
    ) {
        if (title.isBlank() || sentences.isEmpty()) return
        val all = loadAll(context).toMutableList()
        all.removeAll { it.source == source && it.title == title }
        all.add(0, SubtitleEntry(source, title, sentences, System.currentTimeMillis(), path, incomplete))
        val trimmed = if (all.size > MAX_ENTRIES) all.take(MAX_ENTRIES) else all
        val arr = JSONArray()
        for (e in trimmed) {
            arr.put(JSONObject().apply {
                put("source", e.source)
                put("title", e.title)
                put("updatedAt", e.updatedAt)
                put("path", e.path ?: "")
                put("incomplete", e.incomplete)
                put("sentences", encodeSentences(e.sentences))
            })
        }
        sp(context).edit().putString(KEY, arr.toString()).apply()
    }

    fun saveAudio(context: Context, title: String, sentences: List<Sentence>, path: String? = null) =
        save(context, SOURCE_AUDIO, title, sentences, path)

    fun saveVideo(
        context: Context,
        title: String,
        sentences: List<Sentence>,
        path: String? = null,
        incomplete: Boolean = false,
    ) = save(context, SOURCE_VIDEO, title, sentences, path, incomplete)

    /** 读出全部归档（按入库时间倒序：最新在前） */
    fun loadAll(context: Context): List<SubtitleEntry> {
        val raw = sp(context).getString(KEY, "") ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val out = mutableListOf<SubtitleEntry>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val src = o.optString("source", "")
                val title = o.optString("title", "")
                val ts = o.optLong("updatedAt", 0L)
                val p = o.optString("path", "").ifBlank { null }
                val inc = o.optBoolean("incomplete", false)   // 老数据没这字段 ⇒ false
                val sents = decodeSentences(o.optString("sentences", ""))
                if (title.isNotBlank() && sents.isNotEmpty()) out.add(SubtitleEntry(src, title, sents, ts, p, inc))
            }
            out
        }.getOrDefault(emptyList())
    }

    /** 仅取某来源（source）的归档，供「音频 / 视频」标签页回退展示 */
    fun loadBySource(context: Context, source: String): List<SubtitleEntry> =
        loadAll(context).filter { it.source == source }

    fun clear(context: Context) = sp(context).edit().remove(KEY).apply()
}
