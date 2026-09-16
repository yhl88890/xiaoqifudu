package com.xuanji.repeater

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 数据备份：把「生词本」和「字幕库」导出成一个 JSON 文件。
 *
 * 【0916】为什么先做这个：这两份数据是阿亮攒了最久、唯一无法再生的东西
 * （音频视频文件本身还在，但生词和断好的字幕没了就得重来）。
 * 而它们全存在 App 私有目录里 —— **换手机 / 清数据 / 卸载，就全没了**。
 *
 * 存储位置：
 * - 生词本：`context.filesDir/vocab.json`（`VocabStore`，通过 `VocabStore.entries` 读）
 * - 字幕库：SharedPreferences（`SubtitleLibrary.loadAll()`）
 *
 * 导出目的地由用户自选（SAF `ACTION_CREATE_DOCUMENT`），可以直接存网盘 / 电脑 /
 * 发给自己 —— 只有落到了 App 私有目录之外，才算真的"备份住了"。
 *
 * ⚠️ 只导出、不改动任何现有数据格式（纯读），风险最低。
 */
object DataBackup {

    /** 备份文件格式版本。以后字段有增减就 +1，导入时据此兼容。 */
    const val FORMAT_VERSION = 1

    /** 组装备份 JSON 字符串（不落盘，便于复用与测试） */
    fun buildJson(context: Context): String {
        // 生词本：先确保已从文件读入内存，再直接读 StateFlow 的当前值
        VocabStore.ensureLoaded(context)
        val vocab = JSONArray()
        VocabStore.entries.value.forEach { e ->
            vocab.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("text", e.text)
                    put("translation", e.translation ?: "")
                    put("usphone", e.usphone ?: "")
                    put("ukphone", e.ukphone ?: "")
                    put("definition", e.definition ?: "")
                    put("source", e.source)
                    put("addedAt", e.addedAt)
                    put("isSentence", e.isSentence)
                    put("correctStreak", e.correctStreak)
                    put("lastReviewedAt", e.lastReviewedAt)
                }
            )
        }

        // 字幕库：每条含 source / title / 断句表（startMs·endMs·text）
        val subs = JSONArray()
        SubtitleLibrary.loadAll(context).forEach { s ->
            val arr = JSONArray()
            s.sentences.forEach { sen ->
                arr.put(
                    JSONObject().apply {
                        put("startMs", sen.startMs)
                        put("endMs", sen.endMs)
                        put("text", sen.text)
                    }
                )
            }
            subs.put(
                JSONObject().apply {
                    put("source", s.source)
                    put("title", s.title)
                    put("updatedAt", s.updatedAt)
                    put("path", s.path ?: "")
                    put("sentences", arr)
                }
            )
        }

        return JSONObject().apply {
            put("app", "小七复读机")
            put("formatVersion", FORMAT_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("vocabCount", vocab.length())
            put("subtitleCount", subs.length())
            put("vocab", vocab)
            put("subtitles", subs)
        }.toString(2)
    }

    /** 默认文件名：小七复读机-备份-20260916-0400.json */
    fun defaultFileName(): String {
        val ts = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
        return "小七复读机-备份-$ts.json"
    }

    /**
     * 单词表（TXT）：**只取单词，不取整句**，一行一个。
     *
     * 为什么剔除 `isSentence`：整句塞进欧路 / 有道的单词本，人家会当成"一个词"来查，
     * 结果全是查不到的废条目。整句另有用途（跟读 / Anki 例句卡），不该混进来。
     *
     * 为什么不带中文释义：国内的单词本只吃"一行一个词"的纯词表，
     * 导进去之后释义和发音由它们自己的词典补，反而比我们存的更全。
     */
    fun buildWordList(context: Context): String {
        VocabStore.ensureLoaded(context)
        return VocabStore.entries.value
            .filter { !it.isSentence }
            .map { it.text.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .joinToString("\n")
    }

    /** 单词表文件名：小七复读机-单词表-20260916-0400.txt */
    fun wordListFileName(): String {
        val ts = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
        return "小七复读机-单词表-$ts.txt"
    }

    /** 单词条数（给 UI 显示"当前 N 个单词"，避免导出一个空文件还提示成功） */
    fun wordCount(context: Context): Int {
        VocabStore.ensureLoaded(context)
        return VocabStore.entries.value.count { !it.isSentence }
    }

    /** 生词本总条数（单词 + 整句），Anki CSV 用 */
    fun entryCount(context: Context): Int {
        VocabStore.ensureLoaded(context)
        return VocabStore.entries.value.size
    }

    // ------------------------------------------------------------------
    // Anki 卡片（CSV）
    // ------------------------------------------------------------------

    /**
     * 生成 Anki 可直接导入的 CSV：**两列 = 正面英文 / 背面中文**。
     *
     * 为什么是两列：Anki 自带的基础模板（Basic）正好是"正面 / 背面"两个字段，
     * 两列导进去零配置、不用手动调字段映射。多一列反而会让它的导入界面多问一轮。
     *
     * 背面按"越有用越靠前"排：中文 → 音标 → 词性释义 → 来源。
     * 空字段整行略过，不会留一堆空行。
     *
     * **整句也一起导**：收藏的句子做成"正面英文句 / 背面中文"的卡同样能复习，
     * 丢掉太可惜。所以这份 CSV 是"全部收藏"，跟只导单词的 TXT 口径不同。
     *
     * 分隔符用逗号（Anki 的默认），靠标准 CSV 转义处理内容里的逗号 / 引号 / 换行，
     * 所以不用手动改分隔符。不加 BOM —— 加了会让 Anki 第一条卡片的正面
     * 悄悄多一个看不见的字符。
     */
    fun buildAnkiCsv(context: Context): String {
        VocabStore.ensureLoaded(context)
        val sb = StringBuilder()
        VocabStore.entries.value.forEach { e ->
            val front = e.text.trim()
            if (front.isEmpty()) return@forEach

            val back = mutableListOf<String>()
            e.translation?.trim()?.takeIf { it.isNotEmpty() }?.let { back.add(it) }

            if (!e.isSentence) {
                val us = e.usphone?.trim().orEmpty()
                val uk = e.ukphone?.trim().orEmpty()
                when {
                    us.isNotEmpty() && uk.isNotEmpty() && us != uk -> back.add("$us / $uk")
                    us.isNotEmpty() -> back.add(us)
                    uk.isNotEmpty() -> back.add(uk)
                }
                e.definition?.trim()?.takeIf { it.isNotEmpty() }?.let { back.add(it) }
            }

            e.source.trim().takeIf { it.isNotEmpty() }?.let { back.add("来自：$it") }

            sb.append(csvField(front)).append(',').append(csvField(back.joinToString("\n")))
            sb.append('\n')
        }
        return sb.toString()
    }

    /** Anki 卡片文件名：小七复读机-Anki卡片-20260916-1300.csv */
    fun ankiCsvFileName(): String {
        val ts = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
        return "小七复读机-Anki卡片-$ts.csv"
    }

    /** Anki 卡片写到用户选中的 Uri */
    fun writeAnkiCsvTo(context: Context, uri: Uri): Boolean = try {
        val csv = buildAnkiCsv(context)
        val os = context.contentResolver.openOutputStream(uri) ?: return false
        os.use {
            it.write(csv.toByteArray(Charsets.UTF_8))
            it.flush()
        }
        true
    } catch (_: Exception) {
        false
    }

    /**
     * 标准 CSV 字段转义：含逗号 / 引号 / 换行时用双引号包起来，内部引号翻倍。
     * 中文释义里出现英文逗号是常事，不转义会把一列劈成两列、整份卡片错位。
     */
    private fun csvField(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else {
            s
        }

    /**
     * 把备份写到用户选中的 Uri（SAF `ACTION_CREATE_DOCUMENT` 回调给回来的）。
     * @return 是否写成功
     */
    fun writeTo(context: Context, uri: Uri): Boolean = try {
        val json = buildJson(context)
        val os = context.contentResolver.openOutputStream(uri) ?: return false
        os.use {
            it.write(json.toByteArray(Charsets.UTF_8))
            it.flush()
        }
        true
    } catch (_: Exception) {
        false
    }

    /** 把单词表写到用户选中的 Uri（SAF `ACTION_CREATE_DOCUMENT`） */
    fun writeWordListTo(context: Context, uri: Uri): Boolean = try {
        val txt = buildWordList(context)
        val os = context.contentResolver.openOutputStream(uri) ?: return false
        os.use {
            it.write(txt.toByteArray(Charsets.UTF_8))
            it.flush()
        }
        true
    } catch (_: Exception) {
        false
    }

    // ------------------------------------------------------------------
    // 导入（让"留底"真正能搬家）
    // ------------------------------------------------------------------

    /** 一次导入的结果，给 UI 拼提示语 */
    data class ImportResult(
        val ok: Boolean,
        val vocabAdded: Int = 0,
        val vocabSkipped: Int = 0,
        val subtitleNew: Int = 0,
        val subtitleUpdated: Int = 0,
        val message: String = "",
    )

    /**
     * 从用户选中的备份 JSON 恢复数据。
     *
     * **只合并、不覆盖**：
     * - 生词：按 text 大小写不敏感比对，已存在的跳过（保住你现有的翻译 / 音标）
     * - 字幕：按 `source + title` 比对，已有的用备份里的**更新**，没有的新增
     * - 全程不删任何现有条目 —— 导错了也不会让你丢东西
     */
    fun importFrom(context: Context, uri: Uri): ImportResult {
        val raw = try {
            context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        } catch (_: Exception) {
            null
        }
        if (raw.isNullOrBlank()) return ImportResult(false, message = "读不到这个文件")

        val root = try {
            JSONObject(raw)
        } catch (_: Exception) {
            return ImportResult(false, message = "这不是备份文件（解析失败）")
        }

        val ver = root.optInt("formatVersion", 0)
        if (ver <= 0) return ImportResult(false, message = "这不是小七的备份文件")
        if (ver > FORMAT_VERSION) {
            return ImportResult(false, message = "备份来自更新的版本（v$ver），请先升级 App")
        }

        // ---- 生词 ----
        VocabStore.ensureLoaded(context)
        val incoming = mutableListOf<VocabEntry>()
        root.optJSONArray("vocab")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val t = o.optString("text", "").trim()
                if (t.isEmpty()) continue
                incoming.add(
                    VocabEntry(
                        id = 0L, // 由 restoreAll 重新分配，保证不撞号
                        text = t,
                        translation = o.optString("translation", "").ifBlank { null },
                        usphone = o.optString("usphone", "").ifBlank { null },
                        ukphone = o.optString("ukphone", "").ifBlank { null },
                        definition = o.optString("definition", "").ifBlank { null },
                        source = o.optString("source", ""),
                        addedAt = o.optLong("addedAt", System.currentTimeMillis()),
                        isSentence = o.optBoolean("isSentence", false),
                        // 备份里带复习进度就一起恢复，老备份没有则为 0（从没复习过）
                        correctStreak = o.optInt("correctStreak", 0),
                        lastReviewedAt = o.optLong("lastReviewedAt", 0L),
                    )
                )
            }
        }
        val vocabAdded = VocabStore.restoreAll(context, incoming)
        val vocabSkipped = incoming.size - vocabAdded

        // ---- 字幕 ----
        val existingKeys = SubtitleLibrary.loadAll(context)
            .map { it.source to it.title }
            .toSet()
        var subNew = 0
        var subUpdated = 0
        root.optJSONArray("subtitles")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val source = o.optString("source", "").trim()
                val title = o.optString("title", "").trim()
                val sents = mutableListOf<Sentence>()
                o.optJSONArray("sentences")?.let { sa ->
                    for (j in 0 until sa.length()) {
                        val so = sa.optJSONObject(j) ?: continue
                        val txt = so.optString("text", "")
                        if (txt.isBlank()) continue
                        sents.add(
                            Sentence(
                                startMs = so.optLong("startMs", 0L),
                                endMs = so.optLong("endMs", 0L),
                                text = txt,
                            )
                        )
                    }
                }
                if (title.isBlank() || sents.isEmpty()) continue
                if ((source to title) in existingKeys) subUpdated++ else subNew++
                SubtitleLibrary.save(context, source, title, sents, o.optString("path", "").ifBlank { null })
            }
        }

        // 什么都没新增（重复导入 / 备份是空的）：别报"+0"这种让人犯糊涂的账，说清楚现状
        if (vocabAdded == 0 && subNew == 0) {
            val parts = mutableListOf<String>()
            if (vocabSkipped > 0) parts.add("生词 $vocabSkipped 条你这儿已经有了")
            if (subUpdated > 0) parts.add("字幕 $subUpdated 条已用备份里的覆盖")
            return ImportResult(
                true, 0, vocabSkipped, 0, subUpdated,
                if (parts.isEmpty()) "这个备份里没有可导入的数据"
                else "没有新东西要加：${parts.joinToString("，")}"
            )
        }

        val sb = StringBuilder("已导入：生词 +$vocabAdded")
        if (vocabSkipped > 0) sb.append("（跳过 $vocabSkipped 个重复的）")
        if (subNew > 0 || subUpdated > 0) {
            sb.append("，字幕 +$subNew")
            if (subUpdated > 0) sb.append("（更新 $subUpdated）")
        }
        return ImportResult(true, vocabAdded, vocabSkipped, subNew, subUpdated, sb.toString())
    }
}
