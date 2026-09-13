package com.xuanji.repeater

import android.content.Context
import java.io.File

/** 排序方式：数字序（默认）/ 字母序 / 更新时间序 */
internal enum class FolderSortMode { NUMERIC, ALPHA, MTIME }

internal object FolderSortPrefs {
    private const val PREFS = "folder_sort_prefs"
    private const val KEY = "mode"

    fun load(context: Context): FolderSortMode = try {
        FolderSortMode.valueOf(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, FolderSortMode.NUMERIC.name) ?: FolderSortMode.NUMERIC.name
        )
    } catch (_: Exception) {
        FolderSortMode.NUMERIC
    }

    fun save(context: Context, mode: FolderSortMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, mode.name).apply()
    }
}

/** 自然排序：数字段按数值比较（085 < 101），其余按字符（忽略大小写）比较 */
internal fun naturalCompare(a: String, b: String): Int {
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val ca = a[i]
        val cb = b[j]
        if (ca.isDigit() && cb.isDigit()) {
            var ni = i
            while (ni < a.length && a[ni].isDigit()) ni++
            var nj = j
            while (nj < b.length && b[nj].isDigit()) nj++
            val sa = a.substring(i, ni).trimStart('0')
            val sb = b.substring(j, nj).trimStart('0')
            val cmp = if (sa.length != sb.length) sa.length - sb.length else sa.compareTo(sb)
            if (cmp != 0) return cmp
            i = ni
            j = nj
        } else {
            val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
            if (cmp != 0) return cmp
            i++
            j++
        }
    }
    return a.length - b.length
}

/** 字母序键：剥离文件名开头的数字/符号前缀（001&002－Excuse Me → Excuse Me） */
internal fun alphaKey(name: String): String {
    var i = 0
    while (i < name.length) {
        val c = name[i]
        if (c.isDigit() || c == '&' || c == '-' || c == '－' || c == ' ') i++ else break
    }
    return name.substring(i)
}

/** 按指定方式排序音频列表 */
internal fun sortedAudios(list: List<AudioItem>, mode: FolderSortMode): List<AudioItem> = when (mode) {
    FolderSortMode.NUMERIC -> list.sortedWith { a, b ->
        naturalCompare(fileNameWithoutExt(a.path), fileNameWithoutExt(b.path))
    }
    FolderSortMode.ALPHA -> list.sortedWith { a, b ->
        alphaKey(fileNameWithoutExt(a.path)).lowercase()
            .compareTo(alphaKey(fileNameWithoutExt(b.path)).lowercase())
    }
    FolderSortMode.MTIME -> list.sortedByDescending { File(it.path).lastModified() }
}

/** 视频列表用同一套排序规则（与音频保持一致，用户切来切去不会觉得顺序跳了） */
internal fun sortedVideos(list: List<VideoItem>, mode: FolderSortMode): List<VideoItem> = when (mode) {
    FolderSortMode.NUMERIC -> list.sortedWith { a, b ->
        naturalCompare(fileNameWithoutExt(a.path), fileNameWithoutExt(b.path))
    }
    FolderSortMode.ALPHA -> list.sortedWith { a, b ->
        alphaKey(fileNameWithoutExt(a.path)).lowercase()
            .compareTo(alphaKey(fileNameWithoutExt(b.path)).lowercase())
    }
    FolderSortMode.MTIME -> list.sortedByDescending { File(it.path).lastModified() }
}