package com.xuanji.repeater

import android.content.Context
import android.provider.MediaStore
import java.io.File

/** LRC 歌词行 */
data class LrcLine(
    val timeMs: Long,
    val text: String,
)

/**
 * LRC 歌词解析与加载：
 * - 解析标准 LRC（[mm:ss.xx]文本，忽略元数据标签 [al:]/[ar:]/[ti:]/[by:]）
 * - 加载策略：优先直接读文件路径，失败则走 MediaStore 兜底
 */
object LrcParser {

    private val TIME_TAG = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    fun parse(content: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        for (raw in content.lineSequence()) {
            val tags = TIME_TAG.findAll(raw)
            if (!tags.any()) continue
            val text = raw.replace(TIME_TAG, "").trim()
            if (text.isEmpty()) continue
            for (m in tags) {
                val min = m.groupValues[1].toInt()
                val sec = m.groupValues[2].toInt()
                val frac = if (m.groupValues[3].isEmpty()) 0
                else m.groupValues[3].padEnd(3, '0').take(3).toInt()
                lines.add(LrcLine(min * 60_000L + sec * 1_000L + frac, text))
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    /** 由音频路径推导同名 LRC 路径（替换扩展名） */
    fun lrcPathFor(audioPath: String): String {
        val dot = audioPath.lastIndexOf('.')
        return if (dot > 0) audioPath.substring(0, dot) + ".lrc" else audioPath + ".lrc"
    }

    /**
     * 加载音频对应的 LRC 歌词。
     * 优先直接读文件（部分设备/权限模型下可读），失败后走 MediaStore 查询兜底。
     */
    fun loadFor(context: Context, audioPath: String): List<LrcLine>? {
        // 1) 直接读文件
        try {
            val f = File(lrcPathFor(audioPath))
            if (f.exists() && f.canRead()) {
                return parse(f.readText())
            }
        } catch (_: Exception) {
        }
        // 2) MediaStore 兜底（Android13+ 分区存储下 .lrc 可能需经 MediaStore 读取）
        return try {
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val projection = arrayOf(MediaStore.Files.FileColumns._ID)
            val lrcName = lrcPathFor(audioPath).substringAfterLast('/')
            val dir = lrcPathFor(audioPath).substringBeforeLast('/')
            val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND " +
                "${MediaStore.Files.FileColumns.RELATIVE_PATH} = ?"
            val args = arrayOf(lrcName, "$dir/")
            var content: String? = null
            context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(0)
                    val uri = android.content.ContentUris.withAppendedId(collection, id)
                    content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }
            }
            content?.let { parse(it) }
        } catch (_: Exception) {
            null
        }
    }
}