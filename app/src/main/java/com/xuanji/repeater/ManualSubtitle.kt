package com.xuanji.repeater

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * 手动「选字幕」的记忆层。【1008 新建】阿亮："我选了这个字幕以后，那它就能……记住，下一次它就自动加进来，
 * 就不用老是要选"。
 *
 * ## 为什么是"复制一份"，而不是"记住那个 Uri"
 *
 * 用户从系统文件选择器（SAF）挑中的是一个 `content://` Uri。看着更省事，实际有两个坑：
 * 1. **权限会静默失效**：要长期读必须 `takePersistableUriPermission`，而这个 flag 有 provider 不支持、
 *    或者用户清理/重装后失效 ⇒ 下次打开视频"字幕没了"，界面上什么提示也没有（我们最忌讳这类静默失效）。
 * 2. **读取路径会分叉**：自动匹配走的是 `java.io.File`，手动选的走 `ContentResolver`，
 *    两条路要各写一套解析、编码、错误处理。
 *
 * 复制进 App 私有目录之后：它是**普通 File**，跟自动匹配的字幕走**完全相同**的读取链路，
 * 零新增风险；权限问题也不存在（自己目录，随时能读）。
 *
 * ## 铁律
 *
 * - 🔴 **不移动、不改名、不覆盖**用户自己的字幕文件 —— 只读一次、写一份副本到自己兜里。
 * - 🔴 复制完**立刻试解析**，解析不出就整件事作废（删副本、不记映射）。
 *   否则用户"选完了但界面没反应"，我们连原因都不知道。
 * - 🔴 映射以**音视频的绝对路径**为键：同一个视频换手机/改目录后自然失效，回到自动匹配，不会串味。
 *
 * ⚠️ 优先级高于自动匹配（见 `openVideoFromLibrary`）：用户显式指定过，就别让"猜"覆盖他的选择。
 */
internal object ManualSubtitle {

    private const val SP_NAME = "xuanji_submap"
    private const val DIR_NAME = "subtitles"

    /** 值里"副本文件名"和"原始文件名"的分隔符。用 U+0001（不可能出现在文件名里）。 */
    private const val SEP = "\u0001"

    private fun sp(context: Context) = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)

    private fun subDir(context: Context) = File(context.filesDir, DIR_NAME)

    /** 手动选过、且副本还在 ⇒ 返回那份副本；否则 null（调用方接着走自动匹配）。 */
    fun saved(context: Context, mediaPath: String): File? {
        if (mediaPath.isBlank()) return null
        val name = sp(context).getString(mediaPath, null)?.substringBefore(SEP) ?: return null
        if (name.isBlank()) return null
        val f = File(subDir(context), name)
        return if (f.isFile && f.length() > 0L) f else null
    }

    /** 手动选的那份字幕**原来的文件名**（界面上说清"现在用的是哪一份"）。没有则空串。 */
    fun displayName(context: Context, mediaPath: String): String {
        if (mediaPath.isBlank()) return ""
        return sp(context).getString(mediaPath, null)?.substringAfter(SEP, "") ?: ""
    }

    /**
     * 用户选中的字幕 Uri → 复制进私有目录 + 记映射。返回副本文件；读不出/解析不出返回 null。
     */
    fun put(context: Context, mediaPath: String, uri: Uri): File? {
        // ⚠️ 必须块体 + try 表达式：下面的 `return null` 都在 try 内，表达式体（`= try {`）不允许 return
        return try {
        if (mediaPath.isBlank()) return null
        val shown = displayNameOf(context, uri)
        // 扩展名只认我们支持的三种；认不出就写成 .srt —— 反正 [SubtitleParser.parseFile] 会按内容嗅探，
        // 后缀只是给"下次自动匹配"用的一个提示，不是解析依据。
        val ext = shown.substringAfterLast('.', "").lowercase(Locale.ROOT)
            .takeIf { it in SubtitleParser.EXTS } ?: "srt"
        val dir = subDir(context).apply { mkdirs() }
        val dst = File(dir, "${hashOf(mediaPath)}.$ext")
        context.contentResolver.openInputStream(uri)?.use { ins ->
            dst.outputStream().use { out -> ins.copyTo(out) }
        } ?: return null
        if (dst.length() == 0L) {
            dst.delete()
            return null
        }
        // 复制完立刻验一次：解析不出就作废（别留下"选了但没用"的哑状态）
        if (SubtitleParser.parseFile(dst).isEmpty()) {
            dst.delete()
            return null
        }
        // 换扩展名会生成新文件名 ⇒ 顺手删掉上一份，别在私有目录里攒垃圾
        sp(context).getString(mediaPath, null)?.substringBefore(SEP)?.let { old ->
            if (old.isNotBlank() && old != dst.name) File(dir, old).delete()
        }
        sp(context).edit().putString(mediaPath, dst.name + SEP + shown).apply()
        dst
    } catch (t: Throwable) {
        // 🔴【1008】整段包起来 + **打日志**：原来只在复制那段 catch，异常若出在扩展名/文件名计算
        // （t r y 之外）就会一路穿透到 launcher 回调里被系统静默吞掉 —— 表现是"选了字幕，啥也没发生"，
        // 一个字的提示都没有。宁可日志里难看，也不能让用户撞上"哑失败"。
        android.util.Log.e("XJ_SUB", "put failed", t)
        null
        }
    }

    /** 忘掉手动选择（回到自动匹配）。私有副本一并删掉。 */
    fun clear(context: Context, mediaPath: String) {
        if (mediaPath.isBlank()) return
        sp(context).getString(mediaPath, null)?.substringBefore(SEP)?.let { n ->
            if (n.isNotBlank()) File(subDir(context), n).delete()
        }
        sp(context).edit().remove(mediaPath).apply()
    }

    /** 极少数 provider 查不到 DISPLAY_NAME，退用 Uri 最后一段（可能带扩展名，够用了）。 */
    private fun displayNameOf(context: Context, uri: Uri): String = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst() && c.columnCount > 0) c.getString(0) else null }
            ?: uri.lastPathSegment.orEmpty()
    } catch (_: Throwable) {
        uri.lastPathSegment.orEmpty()
    }

    /** 音视频路径 → 固定的副本文件名（SHA-1 前 12 位十六进制）。同一视频永远同一个名字。 */
    private fun hashOf(p: String): String = try {
        MessageDigest.getInstance("SHA-1").digest(p.toByteArray(Charsets.UTF_8))
            .take(6).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    } catch (_: Throwable) {
        // 理论上不会走到（SHA-1 是 JDK 必备），保底也不能崩
        Integer.toHexString(p.hashCode())
    }
}
