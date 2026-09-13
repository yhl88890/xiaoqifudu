package com.xuanji.repeater

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore

/** 扫描设备媒体库中的音频 */
object AudioScanner {

    /** 平铺扫描全部音频（过滤忽略的文件；选集页用） */
    fun scan(context: Context, ignored: Set<String> = emptySet()): List<AudioItem> {
        return scanAll(context).filter { it.path !in ignored }
    }

    /**
     * 按文件夹分组扫描：返回所有含音频的文件夹（过滤忽略的文件与文件夹）。
     * 文件夹按名称排序，组内音频保持标题排序。
     */
    fun scanByFolder(context: Context, ignored: Set<String> = emptySet()): List<FolderItem> {
        val byFolder = LinkedHashMap<String, MutableList<AudioItem>>()
        for (item in scanAll(context)) {
            if (item.path in ignored) continue
            val dir = item.path.substringBeforeLast('/')
            if (dir in ignored) continue
            byFolder.getOrPut(dir) { mutableListOf() }.add(item)
        }
        return byFolder.map { (dir, audios) ->
            FolderItem(
                name = dir.substringAfterLast('/').ifEmpty { dir },
                path = dir,
                audios = audios,
                totalMs = audios.sumOf { it.durationMs },
            )
        }.sortedBy { it.name.lowercase() }
    }

    /** 扫描某文件夹内的全部音频（不应用忽略过滤；文件夹删除/重命名用） */
    fun scanFolderAudios(context: Context, dir: String): List<AudioItem> =
        scanAll(context).filter { it.path.startsWith("$dir/") }

    private fun scanAll(context: Context): List<AudioItem> {
        val items = mutableListOf<AudioItem>()
        val collection = if (android.os.Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DURATION,
        )
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                collection, projection, null, null, sortOrder
            )
            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (c.moveToNext()) {
                    val path = c.getString(dataCol) ?: continue
                    // 过滤幽灵记录（文件已被移动/删除但媒体库记录残留）
                    if (!java.io.File(path).exists()) continue
                    val id = c.getLong(idCol)
                    val title = c.getString(titleCol) ?: path.substringAfterLast('/')
                    val duration = c.getLong(durCol)
                    items.add(
                        AudioItem(
                            id = id,
                            path = path,
                            title = title,
                            durationMs = duration,
                            hasLrc = java.io.File(LrcParser.lrcPathFor(path)).exists(),
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return items
    }
}