package com.xuanji.repeater

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * SRT 字幕解析：标准 SRT（序号 + "HH:MM:SS,mmm --> HH:MM:SS,mmm" + 文本，块间空行分隔）。
 * 解析结果直接复用音频模式的 [Sentence] 模型（startMs/endMs/text），视频模式与音频模式共用同一套句子时间轴逻辑。
 */
object SrtParser {

    /** 时间轴行：00:00:01,000 --> 00:00:04,000（小数点 , 或 . 均可） */
    private val TIME = Regex(
        """(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})"""
    )

    fun parse(content: String): List<Sentence> {
        val out = mutableListOf<Sentence>()
        // 按空行切分字幕块
        for (block in content.split(Regex("""\r?\n\r?\n"""))) {
            val lines = block.lineSequence().map { it.trimEnd() }.filter { it.isNotEmpty() }.toList()
            if (lines.size < 2) continue
            // 找到含 "-->" 的时间轴行
            var timeIdx = -1
            for (i in lines.indices) {
                if (lines[i].contains("-->")) { timeIdx = i; break }
            }
            if (timeIdx < 0) continue
            val m = TIME.find(lines[timeIdx]) ?: continue
            val startMs = toMs(m.groupValues[1], m.groupValues[2], m.groupValues[3], m.groupValues[4])
            val endMs = toMs(m.groupValues[5], m.groupValues[6], m.groupValues[7], m.groupValues[8])
            // 时间轴行之后的所有文本行拼成一句（保留换行，便于双语字幕）
            val text = lines.subList(timeIdx + 1, lines.size).joinToString("\n").trim()
            if (text.isEmpty()) continue
            out.add(Sentence(startMs, endMs, text))
        }
        return out.sortedBy { it.startMs }
    }

    private fun toMs(h: String, m: String, s: String, f: String): Long {
        val hh = h.toLongOrNull() ?: 0L
        val mm = m.toLongOrNull() ?: 0L
        val ss = s.toLongOrNull() ?: 0L
        val frac = (f + "000").take(3).toLongOrNull() ?: 0L
        return ((hh * 3600 + mm * 60 + ss) * 1000) + frac
    }

    /** 由视频路径推导同名 srt 路径（替换扩展名），供后续"同目录自动加载"扩展使用 */
    fun srtPathFor(videoPath: String): String {
        val dot = videoPath.lastIndexOf('.')
        return if (dot > 0) videoPath.substring(0, dot) + ".srt" else videoPath + ".srt"
    }

    /**
     * 【新路径】拿到视频真实文件路径时，直接查同目录的 .srt —— 这条最可靠。
     *
     * 为什么之前自动识别老是失效：视频是用系统相册（Photo Picker）选的
     * `content://media/picker_get_content/...`，拿不到真实路径，只能靠 DocumentsContract
     * 沿父目录枚举子文档，而 Photo Picker 的 Uri 根本不在文档树体系里，必然查不到。
     * 现在视频改成从媒体库列表里选，有真实 [videoPath]，直接列目录即可。
     *
     * 匹配优先级（**已弃用，见下**）：<基名>.srt → 同目录下第一个 .srt。
     *
     * 🔴【1008 弃用，别再调用】只认 `.srt`，而且**找不到同名就取同目录第一个 `.srt`** —— 一个目录里放多个
     * 视频时会把**别的视频的字幕**加载进来（张冠李戴，界面上还看不出来）。
     *
     * 请改用 [SubtitleParser.findBestSubtitle]：支持 SRT/ASS/SSA、去掉版本尾巴(`.chs`/`.1080p`/`.WEB-DL`…)
     * 再匹配、编码自适应（GBK 老字幕），且四级都不中时返回 null（**不猜**）。
     * 本函数已无调用点，留着只为少一次大改动。
     */
    @Deprecated(
        "改用 SubtitleParser.findBestSubtitle：支持 ASS/SSA，且不会张冠李戴",
        ReplaceWith("SubtitleParser.findBestSubtitle(videoPath)"),
    )
    fun findBestSrtFile(videoPath: String): java.io.File? {
        return try {
            val dir = java.io.File(videoPath).parentFile ?: return null
            if (!dir.isDirectory) return null
            val targetName = java.io.File(srtPathFor(videoPath)).name
            val all = dir.listFiles { f -> f.isFile && f.name.endsWith(".srt", ignoreCase = true) }
                ?: return null
            all.firstOrNull { it.name.equals(targetName, ignoreCase = true) } ?: all.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 同目录同名 .srt 自动定位（尽力而为，不引任何新依赖）。
     * 视频是用 SAF（GetContent）选的 content:// Uri，没有真实文件路径，但文档树里能沿父目录枚举同级文件。
     * 通过 DocumentsContract 取视频显示名 → 推导 <基名>.srt → 枚举父目录子文件找同名 → 返回其 Uri。
     * 任何权限/provider 不支持的情况都会抛异常，这里统一吞掉返回 null，交由手动「导入字幕」兜底。
     */
    fun findSiblingSrtUri(context: Context, videoUri: Uri): Uri? {
        return try {
            // 1) 取视频自身的显示名（如 clip.mp4），不依赖 lastPathSegment 那段丑路径
            val videoName = context.contentResolver.query(
                videoUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: return null
            val dot = videoName.lastIndexOf('.')
            val base = if (dot > 0) videoName.substring(0, dot) else videoName
            val target = "$base.srt"

            // 2) 取父目录 docId：文档 id 形如 "primary:Video/clip.mp4"，父目录为 "primary:Video"
            val docId = DocumentsContract.getDocumentId(videoUri)
            val slash = docId.lastIndexOf('/')
            val parentId = if (slash > 0) docId.substring(0, slash) else docId

            // 3) 构造 tree uri 并枚举同目录子文件，找同名 .srt（不同 provider 不支持时抛异常被下方 catch）
            val authority = videoUri.authority ?: return null
            val treeUri = Uri.Builder().scheme("content").authority(authority)
                .appendPath("tree").appendPath(parentId).build()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null, null, null
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (c.moveToNext()) {
                    if (c.getString(nameCol).equals(target, ignoreCase = true)) {
                        val childId = c.getString(idCol)
                        return DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 字幕自动定位(放宽版):同目录优先 <基名>.srt,找不到同名则取同目录第一个 .srt。
     * 视频与字幕不同名(如 clip.720p.mp4 + clip.srt、clip.zh.srt)也能自动加载。
     */
    fun findBestSrtUri(context: Context, videoUri: Uri): Uri? {
        return try {
            val videoName = context.contentResolver.query(
                videoUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: return null
            val dot = videoName.lastIndexOf('.')
            val base = if (dot > 0) videoName.substring(0, dot) else videoName
            val target = "$base.srt"
            val docId = DocumentsContract.getDocumentId(videoUri)
            val slash = docId.lastIndexOf('/')
            val parentId = if (slash > 0) docId.substring(0, slash) else docId
            val authority = videoUri.authority ?: return null
            val treeUri = Uri.Builder().scheme("content").authority(authority)
                .appendPath("tree").appendPath(parentId).build()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null, null, null
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                var firstSrt: Uri? = null
                while (c.moveToNext()) {
                    val name = c.getString(nameCol)
                    if (name.equals(target, ignoreCase = true)) {
                        val childId = c.getString(idCol)
                        return DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                    }
                    if (firstSrt == null && name.endsWith(".srt", ignoreCase = true)) {
                        firstSrt = DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(idCol))
                    }
                }
                firstSrt
            }
        } catch (_: Exception) {
            null
        }
    }
}
