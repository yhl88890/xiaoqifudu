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
}
