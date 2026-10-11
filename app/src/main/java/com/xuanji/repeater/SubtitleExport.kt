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
 * ## 🔴 覆盖策略：宁可多一个文件，也不覆盖用户的字幕
 *
 * 目标默认是 `<视频基名>.srt`（B站 导入的字幕、自己下的字幕都在这个位置上）。
 * 如果那个名字**已经存在**：
 * - 内容跟我们这份一模一样 ⇒ 当成"已经导过了"，不重复写；
 * - 内容不一样 ⇒ **绝不覆盖**（那可能是阿亮自己下的双语字幕，覆盖了就找不回来），改存 `<基名>-识别.srt`。
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

    /** 写到视频旁边：`<基名>.srt`，被占了就退让成 `<基名>-识别.srt`。 */
    fun writeBeside(mediaPath: String, list: List<Sentence>): Result =
        writeBesideImpl(mediaPath, list, fallbackSuffix = "-识别", sameContentMsg = "字幕已经在视频旁边了")

    /**
     * 🔴【1011 阿亮】**ASR 生成完自动落地到视频旁边**用的入口。
     *
     * 背景（阿亮原话）："这 ASR 我们每次生成那就要把它放在源文件旁边了…就不能把它放在私人兜里了"。
     * 原来 ASR 结果**只存 `SubtitleLibrary`（SharedPreferences）**，有两个硬伤：
     *   ① **卸载 App 就全丢**（私有目录，安卓铁律改不了）；
     *   ② 库里 `MAX_ENTRIES = 200`，**攒够 200 条老的自动被挤掉**。
     * ⇒ 落地成"视频旁边的 .srt"是**唯一可靠的持久化**（公共存储，卸载不删、还能被自动匹配读到）。
     *
     * 命名：优先 `<基名>.srt`；已被占（一般是抓的）⇒ 退让成 **`<基名>-ASR.srt`**。
     *   ⚠️ `SubtitleParser.NOISE` **已把 `asr`/`识别`/`生成` 当噪音词删掉** ⇒ 带这两种尾巴的
     *     字幕文件**照样能被自动匹配认出来**（不然有两份时会掉进"不猜"、字幕直接消失）。
     *
     * 返回结果给调用方决定要不要 Toast；**写失败不影响库里那份**（调用方保证先存库）。
     */
    fun writeAsrBeside(mediaPath: String, list: List<Sentence>): Result =
        writeBesideImpl(mediaPath, list, fallbackSuffix = "-ASR", sameContentMsg = "生成的字幕已经在视频旁边了")

    /**
     * 落地实现（两个入口共用）。
     * @param fallbackSuffix 同名被占时的退让后缀，**自带连接符**（`-识别` / `-ASR`）
     * @param sameContentMsg 内容已经一样时的提示语
     */
    private fun writeBesideImpl(
        mediaPath: String,
        list: List<Sentence>,
        fallbackSuffix: String,
        sameContentMsg: String,
    ): Result {
        if (mediaPath.isBlank()) return Result(false, "拿不到视频路径，导出失败")
        val src = File(mediaPath)
        val dir = src.parentFile
        if (dir == null || !dir.isDirectory) return Result(false, "视频所在的文件夹读不到")
        val text = toSrt(list)
        if (text.isBlank()) return Result(false, "还没有字幕可以导出")

        val base = src.name.substringBeforeLast('.')
        val fallback = File(dir, "$base$fallbackSuffix.srt")
        val sameName = File(dir, "$base.srt")

        // 先看自己的退让名在不在（ASR 反复生成时命中这里 ⇒ 内容一样就早退，别重复写）
        for (candidate in listOf(sameName, fallback)) {
            if (!candidate.isFile) continue
            val old = runCatching { candidate.readText() }.getOrNull()
            if (old != null && old.trim() == text.trim()) {
                return Result(true, sameContentMsg, candidate)
            }
        }
        // 同名被占 ⇒ 退让；退让名也被占 ⇒ 不覆盖，报清楚（不静默）
        val target = when {
            !sameName.isFile -> sameName
            !fallback.isFile -> fallback
            else -> return Result(false, "${fallback.name} 已经存在且内容不同，不覆盖")
        }
        return try {
            target.writeText(text)   // UTF-8，不带 BOM
            Result(true, "已导出 ${target.name}", target)
        } catch (_: Throwable) {
            // 分区存储下没有「所有文件访问」时，往别人目录写会被拒 —— 说清楚，别静默
            Result(false, "导出失败：这个文件夹不让写")
        }
    }

    private fun msToSrt(ms: Long): String {
        val c = ms.coerceAtLeast(0L)
        val h = c / 3_600_000
        val m = (c % 3_600_000) / 60_000
        val s = (c % 60_000) / 1000
        val f = c % 1000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, f)
    }
}
