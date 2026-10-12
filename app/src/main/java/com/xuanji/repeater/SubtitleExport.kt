package com.xuanji.repeater

import java.io.File
import java.util.Locale

/**
 * 把当前句子表导成标准 SRT 文件。【1008 新建】阿亮："我们自己识别的这种，能导出 SRT。"
 *
 * 场景：视频没有字幕、识别出来后，这份结果以前**只活在 App 里**（SharedPreferences / 字幕库），
 * 换手机、清数据就没了，也没法拿去别的地方用。导出成 `.srt` 放在视频旁边：
 * ① 下次打开这个视频会被 [SubtitleParser] **自动匹配**到（同一个基名）；② 也能拿去别的播放器。
 *
 * ## 🔴 覆盖策略：**只写自己那一份（`<基名>-ASR.srt`），绝不碰 `<基名>.srt`**
 *
 * `<基名>.srt` 是"最好的字幕"的黄金位置（B站 抓的、自己下的都在那儿），机器识别结果**永远不去占它**。
 * 内容没变就跳过不写（反复点「重新识别」不会反复写盘、也不动 mtime）；内容变了就覆盖**自己那份**旧结果。
 *
 * ⚠️【1012 变更】以前是"优先抢 `<基名>.srt`、被占才退让 `-识别.srt`"——那条已经**删掉**，
 *   抢同名会让来源标志丢失（详见 [writeAsrBeside] 的说明）。别照着旧记忆改回来。
 *
 * ## 时间格式
 *
 * `HH:MM:SS,mmm --> HH:MM:SS,mmm`（逗号毫秒，SRT 标准）。**必须用 Locale.US 格式化** ——
 * 某些地区的默认 Locale 会输出阿拉伯-印度数字或本地数字符号，那种文件别的播放器读不了。
 */
internal object SubtitleExport {

    /** 导出结果：ok=false 时 message 是给人看的失败原因。 */
    internal data class Result(val ok: Boolean, val message: String, val file: File? = null)

    /** 句子表 → SRT 文本。空文本句直接跳过（不占序号）。 */
    fun toSrt(list: List<Sentence>): String {
        val sb = StringBuilder()
        list.asSequence()
            .filter { it.text.isNotBlank() }
            .sortedBy { it.startMs }
            .forEachIndexed { i, s ->
                val start = s.startMs.coerceAtLeast(0L)
                // 零长度句会让播放器整段不显示 ⇒ 兜一个最小可见时长
                val end = if (s.endMs > start) s.endMs else start + 800L
                sb.append(i + 1).append('\n')
                sb.append(msToSrt(start)).append(" --> ").append(msToSrt(end)).append('\n')
                // 文本内的换行正好是 SRT 的多行写法（双语字幕就是这么来的），原样保留
                sb.append(s.text.trim()).append("\n\n")
            }
        return sb.toString()
    }

    /**
     * 🔴【1011 阿亮】**ASR 生成完自动落地到视频旁边**用的入口。
     *
     * 背景（阿亮原话）："这 ASR 我们每次生成那就要把它放在源文件旁边了…就不能把它放在私人兜里了"。
     * 原来 ASR 结果**只存 `SubtitleLibrary`（SharedPreferences）**，有两个硬伤：
     *   ① **卸载 App 就全丢**（私有目录，安卓铁律改不了）；
     *   ② 库里 `MAX_ENTRIES = 200`，**攒够 200 条老的自动被挤掉**。
     * ⇒ 落地成"视频旁边的 .srt"是**唯一可靠的持久化**（公共存储，卸载不删、还能被自动匹配读到）。
     *
     * 🔴🔴【1012】**固定写 `<基名>-ASR.srt`，不再"优先抢 `<基名>.srt`"** —— 这是一处真正的逻辑修bug：
     *   抢同名会让**来源标志丢失**。`<基名>.srt` 是"最好的字幕"的黄金位置（B站抓的 / 用户自己下的），
     *   机器识别的结果一旦占了它，下次打开视频走「完全同名」匹配、会被当成**抓来的**
     *   （`loadReadySubtitles` ⇒ `subtitlesFromAsr = false`）：
     *     ▸ 菜单「下方字幕」三档**误变可用**（同一份内容，冷启是灰的、从媒体库打开能点 ⇒ 用户懵）
     *     ▸ 它还会被按"双语字幕"处理（ASR 是中英混杂，行序重排会搞乱）
     *   ⇒ 现在**各归各位**：`<基名>.srt` 永远留给好字幕，机器生成的住 `<基名>-ASR.srt`，
     *     打开视频时靠后缀就能认回来源（见 [isAsrExport]）。
     *
     * ⚠️ 带后缀**照样能被自动匹配**：靠 [SubtitleParser.pickBest] 第 3 级「基名互相包含」
     *   （`e04asr` 含 `e04`）。但若视频基名 normalize 后不足 4 字符、或不足 6 字符，该级会跳过
     *   ⇒ 这类超短名字匹配不上（**既有约束、非本次引入**，真遇上报再单开一轮）。
     *
     * 返回结果给调用方决定要不要 Toast；**写失败不影响库里那份**（调用方保证先存库）。
     */
    fun writeAsrBeside(mediaPath: String, list: List<Sentence>): Result {
        if (mediaPath.isBlank()) return Result(false, "拿不到视频路径，导出失败")
        val src = File(mediaPath)
        val dir = src.parentFile
        if (dir == null || !dir.isDirectory) return Result(false, "视频所在的文件夹读不到")
        val text = toSrt(list)
        if (text.isBlank()) return Result(false, "还没有字幕可以导出")

        val target = File(dir, src.name.substringBeforeLast('.') + ASR_SUFFIX + ".srt")
        // 内容没变就别重复写（反复点「重新识别」会命中这里；省一次磁盘写、也不动 mtime）
        val old = runCatching { target.readText() }.getOrNull()
        if (old != null && old.trim() == text.trim()) {
            return Result(true, "生成的字幕已经在视频旁边了", target)
        }
        return try {
            target.writeText(text)   // UTF-8，不带 BOM
            Result(true, "已导出 ${target.name}", target)
        } catch (_: Throwable) {
            // 分区存储下没有「所有文件访问」时，往别人目录写会被拒 —— 说清楚，别静默
            Result(false, "导出失败：这个文件夹不让写")
        }
    }

    /**
     * ASR 自动落地的后缀（`<基名>-ASR.srt`）。**打开视频时靠它认回"这份是机器生成的"**。
     * ⚠️ 改名要连 [isAsrExport] 一起想 —— 它是"来源判定"的锚点。
     */
    const val ASR_SUFFIX = "-ASR"

    /**
     * 这个文件名是不是我们 ASR 自动落地产生的那份？
     *
     * ⚠️ 这**不是**"拿文案当判据"（那条铁律禁止的是"看字符串猜来源"）——
     *   后缀是**我们自己写下的固定值**，性质等同 `srtName == ASR_SUBTITLE_NAME`（读回自己写的常量），
     *   不涉及任何"猜内容"。见 `SubtitleState.subtitlesFromAsr` 的注释。
     */
    fun isAsrExport(fileName: String): Boolean =
        fileName.substringBeforeLast('.').endsWith(ASR_SUFFIX, ignoreCase = true)

    private fun msToSrt(ms: Long): String {
        val c = ms.coerceAtLeast(0L)
        val h = c / 3_600_000
        val m = (c % 3_600_000) / 60_000
        val s = (c % 60_000) / 1000
        val f = c % 1000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, f)
    }
}
