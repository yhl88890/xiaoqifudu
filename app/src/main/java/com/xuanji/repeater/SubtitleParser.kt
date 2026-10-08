package com.xuanji.repeater

import java.io.File
import java.nio.charset.Charset

/**
 * 统一字幕「定位 + 解析」：SRT / ASS / SSA → [Sentence]。
 *
 * ## 为什么单独开这个文件
 *
 * 原来只有 [SrtParser]，定位逻辑也塞在它里面（`findBestSrtFile`）。1008 阿亮要：
 * ① 支持 ASS/SSA（他下载的字幕两种格式都有）；② 下载的字幕跟视频放一起或分开放都能用。
 * 所以把「找哪个文件」和「按格式解析」两件事收到这里，[SrtParser] 退化成"只认 SRT 语法"的解析器。
 *
 * ## 🔴 定位策略：多级放宽，**最后一级宁可不加载也不猜**
 *
 * 1. 完全同名（`老友记.S01E01.mp4` + `老友记.S01E01.srt`）
 * 2. 去掉「版本尾巴」后同名（`Friends.S01E01.1080p.WEB-DL.mp4` + `Friends.S01E01.chs&eng.ass`）
 * 3. 基名互相包含（有最短长度门槛，防短名字乱命中）
 * 4. **集号对齐**：两边的文件名都能认出「第几季第几集」，且**只有一个候选**时采用（见 [episodesOf]）
 * 5. 整个目录里**只有这一个**字幕文件（只有一个，猜也猜不错）
 * 6. 都不中 ⇒ 返回 null
 *
 * ⚠️ 第 6 条是针对老代码的坑改的：原 `SrtParser.findBestSrtFile` 找不到同名就 `all.firstOrNull()`，
 * 一个目录里放多个视频时会把**别的视频的字幕**加载进来，而且界面上完全看不出来。
 * 【1008 已核实】阿亮现有视频全部带严格同名 .srt（B站 导入自己写的）⇒ 去掉这条不影响存量。
 *
 * ⚠️ 第 4 条是【1008 老友记实测】补的，见下面 [episodesOf] 的说明 —— 剧集文件里，
 * 「同一集的视频」和「同一集的字幕」经常来自两个不同的组，文件名完全没有共同前缀。
 *
 * ## ASS/SSA 必须洗掉的东西（这些都不是"结构化样式字段"，**全都藏在文本里**）
 *
 * - **时间 `0:00:01.00` 是百分秒**（不是 SRT 的逗号毫秒）⇒ 当毫秒解析整条时间轴差 10 倍；
 * - `{\...}` 覆盖标签（颜色 `\c&H..&`、位置 `\pos`、字体 `\fn`、动画 `\move`）⇒ **整段删掉**。
 *   不删就会当乱码上屏，而且 [SentenceListView] / `ClickableWordsRow` 会把它当成一个"可点的单词"；
 * - `\N` / `\n` 硬换行、`\h` 硬空格 ⇒ 还原成真换行 / 空格（运行时能显示多行，全工程没有 maxLines 限制）；
 * - `Comment:` 行本来就不该显示；[JUNK_STYLES] 里的样式（Sign/Title/OP/ED/水印/歌词…）不当对白；
 * - 双语 ASS 同一时间有两条（中文一条、英文一条）⇒ 合成一句，**英文在上、中文在下**。
 *
 * 🔴 我们**不做字幕叠加渲染**：颜色/位置/层级在"列表式跟读"界面里没有任何意义，一律丢弃。
 * 想让它们在画面上显示出来是另一个量级的功能，别往这条路上带。
 */
internal object SubtitleParser {

    /** 认的扩展名（顺序 = 自动匹配优先级） */
    val EXTS = listOf("srt", "ass", "ssa")

    /**
     * 编译正则，**失败时打日志指出是哪一条**。
     *
     * 🔴【1008 血泪】原来这些正则写成 `private val XXX = Regex(...)` 直接放在 object 的初始化里。
     * 结果：**Android 的 ICU 正则引擎比 OpenJDK 严**（同一条 pattern 在电脑 JDK 17 上编译通过、
     * 在手机上编译失败），`<clinit>` 一抛异常，整个 [SubtitleParser] 类就变成**永久不可用**
     * （之后每次碰它都是 `NoClassDefFoundError`）。
     * 更坑的是：这个类在"冷启动恢复视频现场"这条路上**第一次被访问**恰好是「选字幕」——
     * 表现成"点了选字幕、选完文件、啥反应也没有"，一个字的提示都没有（异常在 launcher 回调里被静默吞掉）。
     *
     * ⇒ 两条防御：① 所有正则改 `by lazy`，编译推迟到真正用它的那一刻 —— 就算某条炸，
     *    也只炸那一个方法，不会把整个类拖死；② 炸的时候日志直接印出 pattern 原文，一眼定位。
     */
    private fun rx(pattern: String): Regex = try {
        Regex(pattern)
    } catch (t: Throwable) {
        android.util.Log.e("XJ_SUB", "regex compile FAILED: $pattern", t)
        throw t
    }

    // ======================= 读文件（编码自适应） =======================

    /**
     * 读字幕文本，**自动处理编码**。
     *
     * 为什么需要：网上下的字幕（尤其国产字幕组/老片）大量是 **GBK/GB18030**，直接按 UTF-8 读会得到
     * 满屏 ``（替换字符），字幕等于废的。策略：先当 UTF-8 读，出现替换字符就改用 GB18030 重读
     * （GB18030 是 GBK 的超集，兼容 GB2312 ⇒ 一个编码兜住所有简体老字幕）。
     */
    fun readText(f: File): String {
        val bytes = runCatching { f.readBytes() }.getOrNull() ?: return ""
        // UTF-8 BOM：去掉再解码，否则首行会带一个不可见字符
        val body = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) bytes.copyOfRange(3, bytes.size) else bytes
        val utf8 = String(body, Charsets.UTF_8)
        if (!utf8.contains('\uFFFD')) return utf8
        return runCatching { String(body, Charset.forName("GB18030")) }.getOrDefault(utf8)
    }

    // ======================= 解析入口 =======================

    /** 按扩展名分派解析。读不到/解析不出就返回空表（调用方自己决定要不要提示）。 */
    fun parseFile(f: File): List<Sentence> = try {
        val text = readText(f)
        if (text.isBlank()) emptyList()
        else when (f.extension.lowercase()) {
            "ass", "ssa" -> parseAss(text)
            "srt" -> SrtParser.parse(text)
            // 扩展名认不出（手动选进来的副本可能后缀不可用、或用户把 .txt 改成了字幕）
            // ⇒ 看内容猜一次，别让一份好字幕因为"后缀不对"就用不了
            else -> parseByContent(text)
        }
    } catch (_: Throwable) {
        emptyList()
    }

    /**
     * 按**内容**判定格式：ASS 有 `[Events]`/`Dialogue:`；SRT 有时间轴箭头 `-->`。
     * 两种都不像就各解析一次、取句子多的那个（空结果不占便宜）。
     */
    private fun parseByContent(text: String): List<Sentence> {
        val head = text.take(5000)
        if (head.contains("[Events]", ignoreCase = true) ||
            head.contains("Dialogue:", ignoreCase = true)
        ) return parseAss(text)
        if (head.contains("-->")) return SrtParser.parse(text)
        val srt = SrtParser.parse(text)
        val ass = parseAss(text)
        return if (ass.size > srt.size) ass else srt
    }

    // ======================= ASS / SSA =======================

    /**
     * `{\...}` 覆盖标签：颜色/位置/字体/动画全在里面，整段删掉。
     * ⚠️ 收尾的 `}` 必须写成 `\}`：孤立的 `}` 在 OpenJDK 里当字面量放行，**Android 的 ICU 会直接报
     * "未匹配的右花括号"** —— 这正是 1008 那次整个类加载失败的头号嫌疑。
     */
    private val ASS_TAG by lazy { rx("""\{[^}]*\}""") }

    /** ASS 时间轴：`H:MM:SS.cc`（cc = 百分秒）—— 也容忍小数点和三位毫秒 */
    private val ASS_TIME by lazy { rx("""^(\d+):(\d{1,2}):(\d{1,2})[.:](\d{1,3})$""") }

    /** 中文/英文/数字以外的分隔符（样式名、文件名分词用） */
    private val TOKEN_SEP by lazy { rx("""[^a-z0-9\u4e00-\u9fff]+""") }

    /** 不当对白的样式名（**按 token 精确比**，别用子串匹配 —— "op" 会误伤 "Open Sans" 这种） */
    private val JUNK_STYLES = setOf(
        "sign", "signs", "title", "titles", "op", "ed", "opening", "ending",
        "note", "notes", "comment", "comments", "logo", "watermark", "staff", "info",
        "screen", "karaoke", "song", "lyrics", "lyric",
        "特效", "歌词", "注释", "水印", "标题", "字幕", "字幕组", "歌曲",
    )

    /**
     * 解析 ASS / SSA。
     *
     * 只认 `[Events]` 段，字段位置**读 Format 行**拿（ASS 和 SSA v4 的字段顺序不一样，
     * 写死下标就会读错列）。`Text` 是最后一个字段且**内部可能含逗号** ⇒ 用 `limit` 只切前面几个。
     */
    fun parseAss(content: String): List<Sentence> {
        var inEvents = false
        // 缺 Format 行时的保底（ASS 默认顺序；SSA v4 也是 10 个字段，只是第 1 个叫 Marked）
        var fieldCount = 10
        var iStart = 1
        var iEnd = 2
        var iStyle = 3
        var iText = 9

        // (start, end) → (样式, 文本)；同一时间可能有多条（双语/多轨）
        val grouped = LinkedHashMap<Pair<Long, Long>, MutableList<Pair<String, String>>>()

        for (raw in content.lineSequence()) {
            val line = raw.trimEnd()
            if (line.startsWith("[")) {
                inEvents = line.trim().equals("[Events]", ignoreCase = true)
                continue
            }
            if (!inEvents) continue

            val lower = line.lowercase()
            if (lower.startsWith("format:")) {
                val names = line.substringAfter(':').split(',').map { it.trim().lowercase() }
                if (names.isNotEmpty()) {
                    fieldCount = names.size
                    names.indexOf("start").takeIf { it >= 0 }?.let { iStart = it }
                    names.indexOf("end").takeIf { it >= 0 }?.let { iEnd = it }
                    names.indexOf("style").takeIf { it >= 0 }?.let { iStyle = it }
                    names.indexOf("text").takeIf { it >= 0 }?.let { iText = it }
                }
                continue
            }
            // Comment: / Picture: / Sound: / Movie: / Command: 一律不是对白
            if (!lower.startsWith("dialogue:")) continue

            val body = line.substringAfter(':')
            val parts = body.split(',', limit = fieldCount)
            if (parts.size < fieldCount) continue
            val start = parseAssTime(parts[iStart]) ?: continue
            val end = parseAssTime(parts[iEnd]) ?: continue
            if (end <= start) continue
            val style = parts.getOrNull(iStyle)?.trim().orEmpty()
            if (!isUsefulStyle(style)) continue
            val text = cleanAssText(parts[iText])
            if (text.isEmpty()) continue
            grouped.getOrPut(start to end) { mutableListOf() }.add(style to text)
        }

        if (grouped.isEmpty()) return emptyList()
        val out = mutableListOf<Sentence>()
        for ((key, list) in grouped) {
            val texts = if (list.size == 1) {
                listOf(list[0].second)
            } else {
                // 同时刻多条 ⇒ 只留两条：英文优先（我们做的是英语跟读），中文垫在下面
                list.asSequence()
                    .map { it.second }
                    .distinct()
                    .sortedByDescending { latinRatio(it) }
                    .take(2)
                    .toList()
            }
            val joined = texts.filter { it.isNotBlank() }.joinToString("\n")
            if (joined.isNotBlank()) out.add(Sentence(key.first, key.second, joined))
        }
        return out.sortedBy { it.startMs }
    }

    /** `H:MM:SS.cc`（cc = 百分秒）→ 毫秒。两位小数当百分秒；写三位就按毫秒。 */
    private fun parseAssTime(s: String): Long? {
        val m = ASS_TIME.find(s.trim()) ?: return null
        val h = m.groupValues[1].toLongOrNull() ?: return null
        val mi = m.groupValues[2].toLongOrNull() ?: return null
        val se = m.groupValues[3].toLongOrNull() ?: return null
        val frac = m.groupValues[4]
        val ms = if (frac.length >= 3) frac.take(3).toLong() else (frac + "00").take(2).toLong() * 10
        return (h * 3600 + mi * 60 + se) * 1000 + ms
    }

    /** 剥掉 `{\...}` 标签 + 还原换行/硬空格，再按行 trim 并丢掉空行 */
    private fun cleanAssText(s: String): String =
        ASS_TAG.replace(s, "")
            .replace("\\N", "\n")
            .replace("\\n", "\n")
            .replace("\\h", " ")
            .lines().map { it.trim() }.filter { it.isNotEmpty() }
            .joinToString("\n")
            .trim()

    /** 样式名是否可能是对白（空样式当对白；命中毒样式名单就丢） */
    private fun isUsefulStyle(style: String): Boolean {
        if (style.isBlank()) return true
        val tokens = style.lowercase().split(TOKEN_SEP).filter { it.isNotEmpty() }
        return tokens.none { it in JUNK_STYLES }
    }

    private fun latinRatio(s: String): Double {
        if (s.isEmpty()) return 0.0
        return s.count { it in 'a'..'z' || it in 'A'..'Z' }.toDouble() / s.length
    }

    // ======================= 自动匹配 =======================

    /** 列出目录下的字幕文件（按 [EXTS] 优先级排序）。给需要一次列目录、批量判多个视频的调用方用。 */
    fun listSubtitles(dir: File): List<File> = try {
        if (!dir.isDirectory) emptyList()
        else (dir.listFiles { f -> f.isFile && f.extension.lowercase() in EXTS } ?: emptyArray())
            .sortedBy { EXTS.indexOf(it.extension.lowercase()) }
    } catch (_: Throwable) {
        emptyList()
    }

    /** 自动匹配：给音视频路径找该用哪个字幕文件。找不到返回 null（**绝不"取第一个"**）。 */
    fun findBestSubtitle(mediaPath: String): File? {
        val f = File(mediaPath)
        return pickBest(f.name, f.parentFile?.let { listSubtitles(it) } ?: return null)
    }

    /**
     * 四级匹配（[subs] 是 [listSubtitles] 的结果，允许调用方缓存后复用）。
     * @param mediaName 音视频的**文件名**（带扩展名）
     */
    fun pickBest(mediaName: String, subs: List<File>): File? {
        if (subs.isEmpty()) return null
        val mediaBase = mediaName.substringBeforeLast('.')

        // 1) 完全同名
        for (ext in EXTS) {
            subs.firstOrNull { it.name.equals("$mediaBase.$ext", ignoreCase = true) }?.let { return it }
        }

        // 2) 去尾巴后同名 / 3) 基名互相包含
        val nv = normalize(mediaBase)
        if (nv.length >= 4) {
            var fuzzy: File? = null
            for (f in subs) {
                val ns = normalize(f.name.substringBeforeLast('.'))
                if (ns.isEmpty()) continue
                if (ns == nv) return f                                  // 2)
                if (fuzzy == null && ns.length >= 6 &&
                    (nv.contains(ns) || ns.contains(nv))
                ) fuzzy = f                                             // 3) 先记下，别急着返回
            }
            if (fuzzy != null) return fuzzy

            // 4) 集号对齐：两边都能认出「第几季第几集」时就按集号配。
            //    ⚠️ **只在候选唯一时采用**：同一集放了中英两份字幕 = 有歧义，不在这里武断挑一个，
            //    继续往下走（目录唯一 / 不猜）。媒体名自己抽不到集号（电影、B站 视频）⇒ 这一级直接跳过。
            val mediaEp = episodesOf(mediaBase)
            if (mediaEp.isNotEmpty()) {
                val hits = subs.filter { sameEpisode(mediaEp, episodesOf(it.name)) }
                if (hits.size == 1) return hits[0]
            }
        }

        // 4) 目录里只有一个字幕文件 ⇒ 只能是它
        if (subs.size == 1) return subs.first()

        // 5) 不猜
        return null
    }

    /** 目录里有没有任何字幕文件（给视频列表的「字幕」角标用）。 */
    fun hasAnySubtitle(mediaPath: String): Boolean {
        val f = File(mediaPath)
        return pickBest(f.name, f.parentFile?.let { listSubtitles(it) } ?: return false) != null
    }

    // ======================= 集号（判断"是不是同一集"最硬的信号） =======================

    /**
     * 从文件名里抽出「第几季第几集」。
     *
     * ## 为什么单开这一级（1008 阿亮拿老友记真实文件实测出来的）
     *
     * 同一集的**视频**和**字幕**经常来自两个不同的压制组/字幕组，文件名可以完全没有共同前缀：
     * ```
     * 视频：S01.E01.1994.BluRay.2160p.10Bit.DV.HDR.HEVC.DDP5.1.mkv   （归一化 → s01e011994ddp51）
     * 字幕：Friends.S01E01.The.One.Where.Monica.Gets.A.New.Roommate.DVDRip.AC3.3.2ch.JOG.srt
     *                                                                （归一化 → friendss01e01theone...）
     * ```
     * 两条归一化结果**互不包含** ⇒ 旧的「同名 / 去尾巴同名 / 基名互含」三级全部落空，
     * 明明下了字幕却一个都配不上（实测 4 组里 0 组能配上）。但「S01E01」是同一个 ——
     * **集号才是剧集文件真正的身份**，版本尾巴（分辨率/编码/压制组/年份）全是可以扔的。
     *
     * ## 认这几种写法（按优先级，命中一种就不再往下试）
     * - `S01E01` / `S01.E01` / `s1e1`（最常见）
     * - `Season 1 Episode 1` / `Season 1 Ep 02`
     * - `1x02`
     * - `第1季第01集` / `第01集`（中文命名；抽不到"第几季"时季号记 0 = 未知）
     *
     * ## 安全设计
     * - 每条正则都走 [rx] + `by lazy`：**编译失败只让这一级失效，绝不连累 [pickBest]**
     *   （1008 那次 `NoClassDefFoundError` 的教训：正则写在 object 初始化里会拖死整个类）；
     * - [episodesOf] 整体包了 try/catch：真出问题也只是"认不出集号"，返回空表，匹配退回原来的行为；
     * - 数字一律 [toIntOrNull]，不抛异常。
     */
    private val EP_SXXEYY by lazy { rx("""(?i)s(\d{1,2})[\s._\-]{0,2}e(\d{1,3})""") }
    private val EP_SEASON_EP by lazy {
        rx("""(?i)season[\s._\-]{0,3}(\d{1,2})[\s._\-]{0,4}(?:episode|ep)[\s._\-]{0,3}(\d{1,3})""")
    }
    // ⚠️ 用 `\b` 而不是前后瞻：ICU 上前后瞻语法受限，而 `\b` 足够挡住 1920x1080 这种分辨率
    private val EP_NXNN by lazy { rx("""\b(\d{1,2})x(\d{1,3})\b""") }
    private val EP_CN by lazy { rx("""第\s*(\d{1,3})\s*[集话話]""") }
    private val SEASON_CN by lazy { rx("""第\s*(\d{1,2})\s*季""") }

    /** 一条集号。`season = 0` 表示**这个文件名里没有季号**（配对时当通配用）。 */
    private data class Ep(val season: Int, val episode: Int)

    /** 抽一个文件名里的全部集号（`S01E16.S01E17` 这种合并集会有两条）。 */
    private fun episodesOf(rawName: String): List<Ep> = try {
        val out = ArrayList<Ep>(2)
        fun collect(r: Regex) {
            if (out.isNotEmpty()) return
            for (m in r.findAll(rawName)) {
                out.add(
                    Ep(
                        m.groupValues[1].toIntOrNull() ?: 0,
                        m.groupValues[2].toIntOrNull() ?: 0,
                    )
                )
            }
        }
        collect(EP_SXXEYY)
        collect(EP_SEASON_EP)
        collect(EP_NXNN)
        if (out.isEmpty()) {
            val season = SEASON_CN.find(rawName)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            for (m in EP_CN.findAll(rawName)) {
                out.add(Ep(season, m.groupValues[1].toIntOrNull() ?: 0))
            }
        }
        out.distinct()
    } catch (t: Throwable) {
        android.util.Log.e("XJ_SUB", "episodesOf failed: $rawName", t)
        emptyList()
    }

    /** 两个名字里有没有「同一集」：集号必须相同，季号有一边不知道就算匹配。 */
    private fun sameEpisode(a: List<Ep>, b: List<Ep>): Boolean = a.any { x ->
        b.any { y -> x.episode == y.episode && (x.season == y.season || x.season == 0 || y.season == 0) }
    }

    private val BRACKET by lazy { rx("""[\[\(【（].*?[\]\)】）]""") }

    /** 去掉「版本/编码/语言」这类尾巴再比。列表里的词命中就整块丢掉（**两边同规则 ⇒ 不会单边偏**）。 */
    private val NOISE = setOf(
        // 分辨率 / 帧率 / 动态范围
        "720p", "1080p", "1080i", "2160p", "480p", "4k", "8k", "60fps", "30fps", "24fps",
        "hdr", "hdr10", "dolby", "dv", "sdr", "hd", "fhd", "uhd",
        // 来源 / 压制
        "web", "webdl", "webrip", "dl", "bluray", "blu", "ray", "bdrip", "brrip", "hdtv", "dvdrip",
        "dvd", "remux", "repack", "proper", "internal", "hdtc", "amzn", "nf", "dsnp", "hdrip",
        "cam", "ts", "tc", "webdlrip",
        // 编码 / 音频
        "x264", "x265", "h264", "h265", "hevc", "avc", "xvid", "divx", "10bit", "8bit", "bit",
        "aac", "ac3", "eac3", "dts", "flac", "mp3", "ddp", "dtshd", "truehd", "atmos",
        // 语言 / 字幕类型
        "chs", "cht", "chi", "zho", "zh", "eng", "en", "gb", "big5", "sc", "tc",
        "简体", "繁体", "中文", "英文", "中英", "双语", "中字", "英字", "内嵌", "外挂", "字幕",
        // 常见中文尾巴
        "ai修复", "补帧", "修复", "高清", "超清", "蓝光",
    )

    private fun normalize(base: String): String {
        val s = BRACKET.replace(base.lowercase(), " ")
        val tokens = s.split(TOKEN_SEP).filter { it.isNotEmpty() }
        return tokens.filter { it !in NOISE }.joinToString("")
    }
}
