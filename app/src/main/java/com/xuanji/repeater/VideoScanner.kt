package com.xuanji.repeater

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore

/** 扫描设备媒体库中的视频 */
object VideoScanner {

    /** 媒体库 Uri：`content://media/external/video/media/<id>`（长期有效，不依赖 SAF 授权） */
    fun uriFor(id: Long): Uri = ContentUris.withAppendedId(collection(), id)

    private fun collection(): Uri = if (android.os.Build.VERSION.SDK_INT >= 29) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    /** 扫描全部视频（过滤已删除的幽灵记录） */
    fun scanAll(context: Context): List<VideoItem> {
        val items = mutableListOf<VideoItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
        )
        val sortOrder = "${MediaStore.Video.Media.TITLE} COLLATE NOCASE ASC"
        // 【1008】目录 → 该目录下的字幕文件。**一次列目录、批量为多个视频判「有没有字幕」**，
        // 避免每个视频都 listFiles 一遍（视频一多就是 O(n²) 次目录 IO）。
        val subCache = java.util.HashMap<String, List<java.io.File>>()
        fun subsOf(dir: java.io.File?): List<java.io.File> =
            if (dir == null) emptyList()
            else subCache.getOrPut(dir.absolutePath) { SubtitleParser.listSubtitles(dir) }
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                collection(), projection, null, null, sortOrder
            )
            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                while (c.moveToNext()) {
                    val path = c.getString(dataCol) ?: continue
                    val vf = java.io.File(path)
                    if (!vf.exists()) continue
                    val id = c.getLong(idCol)
                    val title = c.getString(titleCol) ?: path.substringAfterLast('/')
                    val duration = c.getLong(durCol)
                    items.add(
                        VideoItem(
                            id = id,
                            path = path,
                            title = title,
                            durationMs = duration,
                            // 【1008】角标改成和「实际自动加载」同一套判据（SRT/ASS/SSA + 去版本尾巴匹配）：
                            // 原来只判 `<基名>.srt` 存不存在 ⇒ 字幕放的是 .ass 时，明明有字幕却显示没字幕。
                            hasSrt = SubtitleParser.pickBest(vf.name, subsOf(vf.parentFile)) != null,
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return items
    }
}

/**
 * 统一媒体扫描：把音频和视频按父目录合并成一个文件夹列表。
 *
 * 这么做的目的（用户原话）：「音频跟视频都在同目录里……我点开音频选集，怎么确保这个文件夹里全是音频？」
 * —— 所以不按类型分成两个互不相干的列表，而是同一个文件夹里两类都带出来，
 *   进入详情后用「音频 / 视频」切换决定看哪一类，文件夹总览上同时标出两类数量。
 */
object MediaScanner {

    /** 按文件夹分组：音频 + 视频合并（应用忽略过滤） */
    fun scanFolders(context: Context, ignored: Set<String> = emptySet()): List<FolderItem> {
        val audioByDir = LinkedHashMap<String, MutableList<AudioItem>>()
        for (item in AudioScanner.scan(context, ignored)) {
            val dir = item.path.substringBeforeLast('/')
            if (dir in ignored) continue
            audioByDir.getOrPut(dir) { mutableListOf() }.add(item)
        }
        val videoByDir = LinkedHashMap<String, MutableList<VideoItem>>()
        for (item in VideoScanner.scanAll(context)) {
            if (item.path in ignored) continue
            val dir = item.path.substringBeforeLast('/')
            if (dir in ignored) continue
            videoByDir.getOrPut(dir) { mutableListOf() }.add(item)
        }
        val dirs = (audioByDir.keys + videoByDir.keys).distinct()
        return dirs.map { dir ->
            val audios = audioByDir[dir] ?: emptyList()
            val videos = videoByDir[dir] ?: emptyList()
            FolderItem(
                name = dir.substringAfterLast('/').ifEmpty { dir },
                path = dir,
                audios = audios,
                totalMs = audios.sumOf { it.durationMs },
                videos = videos,
            )
        }.sortedBy { it.name.lowercase() }
    }

    /** 重扫单个文件夹（详情页刷新用；两种类型的数量都可能变） */
    fun scanFolder(context: Context, path: String): FolderItem? {
        val ignored = IgnorePrefs.load(context)
        return scanFolders(context, ignored).firstOrNull { it.path == path }
    }
}
