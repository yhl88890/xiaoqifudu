package com.xuanji.repeater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.Charset

/**
 * `SubtitleParser` 的单元测试 —— **跑在电脑上，不需要手机**。
 *
 * 这个文件覆盖两条最容易出事的链路：
 * 1. **`parseAss`**：ASS/SSA 解析（去 `{\...}` 标签、`\N` 换行、垃圾样式过滤、双语合并、字段按名取值）
 * 2. **`pickBest`**：字幕自动匹配（同名 → 去尾巴 → 互含 → 集号对齐 → 目录唯一 → **不猜**）
 *
 * ## 为什么第 2 条特别值得测
 *
 * 2026-10-08 那次：老代码 `findBestSrtFile` 找不到同名就 `all.firstOrNull()` ——
 * **一个目录里放多个视频时，会把别的视频的字幕加载进来，而且界面上完全看不出来**。
 * 现在改成"最后一级不猜、返回 null"。这条安全行为**必须被测试钉住**，
 * 否则哪天有人"顺手简化"成取第一个，就又是一次静默错配。
 *
 * ⚠️ 这里只测 `SubtitleParser`。**`SentenceSplitter` 测不了**（JVM 单测）——
 *   它的核心逻辑全绑在 `MediaExtractor`/`MediaCodec` 上（要真解码音频取 PCM），
 *   跑 JVM 单测只会得到一堆 not-mocked。要测它得先把"RMS 数组 → 静音区间"那段算法
 *   从解码流程里抽出来变成纯函数（属于改源码，未做）。
 */
class SubtitleParserTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /**
     * 拼一份最小可用的 ASS，`dialogueLines` 直接塞进 `[Events]` 段。
     *
     * ⚠️ 这里**故意不用三引号 + `trimIndent()`**：
     *   多行插值 `"${...}"` 展开后，**只有第一行**会带上模板本身的缩进，
     *   后面几行是裸的 ⇒ `trimIndent()` 算出的"公共缩进"会变成 0
     *   ⇒ 前面的 `[Events]`、`Format:` 行都留着前导空格
     *   ⇒ `parseAss` 里 `line.startsWith("[")` 判不出来 ⇒ **整份内容一条都解析不到**，
     *   表现出来就是"一堆测试挂了，但解析器其实没毛病"。
     *   按行拼字符串最稳，也最好读。
     */
    private fun ass(vararg dialogueLines: String) = (
        listOf(
            "[Script Info]",
            "Title: test",
            "[V4+ Styles]",
            "Format: Name, Fontname",
            "Style: Default,Arial",
            "[Events]",
            "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text",
        ) + dialogueLines
        ).joinToString("\n")

    // ==================================================================
    // ① parseAss —— 基础解析
    // ==================================================================

    @Test
    fun `ass_解析单条_时间和文本都对`() {
        val list = SubtitleParser.parseAss(
            ass("Dialogue: 0,0:00:01.00,0:00:03.50,Default,,0,0,0,,Hello world")
        )
        assertEquals(1, list.size)
        assertEquals(1000L, list[0].startMs)
        assertEquals(3500L, list[0].endMs)
        assertEquals("Hello world", list[0].text)
    }

    @Test
    fun `ass_花括号覆盖标签要整段删掉`() {
        // {\pos(10,10)\blur3} 这类只影响画面渲染；留在文本里会上屏成乱码、
        // 还会被 ClickableWordsRow 当成一个"可点的单词"。
        val list = SubtitleParser.parseAss(
            ass("""Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,{\pos(10,10)\blur3}Hello{\r}""")
        )
        assertEquals("Hello", list[0].text)
    }

    @Test
    fun `ass_反斜杠N要还原成真换行`() {
        // ASS 里双语常写成一条 Dialogue 里用 \N 分隔 ⇒ 必须变成真换行，界面才显示两行。
        val list = SubtitleParser.parseAss(
            ass("""Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hello\N你好""")
        )
        // 英文在上、中文在下（orderBilingual 在 parseAss 末尾过了一遍）
        assertEquals("Hello\n你好", list[0].text)
    }

    @Test
    fun `ass_小写反斜杠n也要换行_反斜杠h还原成空格`() {
        val list = SubtitleParser.parseAss(
            ass("""Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,a\nb\hc""")
        )
        assertEquals("a\nb c", list[0].text)
    }

    @Test
    fun `ass_文本行首尾空白被裁掉_空行被丢掉`() {
        val list = SubtitleParser.parseAss(
            ass("""Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,  spaced  """)
        )
        assertEquals("spaced", list[0].text)
    }

    // ==================================================================
    // ② parseAss —— 时间轴
    // ==================================================================

    @Test
    fun `ass_两位小数按百分秒算_不是毫秒`() {
        // 🔴 关键：ASS 的 `0:00:01.50` 是 **1.5 秒**；若当毫秒解析，整条时间轴会差 10 倍。
        val list = SubtitleParser.parseAss(
            ass("Dialogue: 0,0:00:01.50,0:00:02.00,Default,,0,0,0,,x")
        )
        assertEquals(1500L, list[0].startMs)
    }

    @Test
    fun `ass_三位小数按毫秒算`() {
        val list = SubtitleParser.parseAss(
            ass("Dialogue: 0,0:00:01.005,0:00:02.000,Default,,0,0,0,,x")
        )
        assertEquals(1005L, list[0].startMs)
        assertEquals(2000L, list[0].endMs)
    }

    @Test
    fun `ass_小时位参与计算`() {
        val list = SubtitleParser.parseAss(
            ass("Dialogue: 0,1:02:03.00,1:02:04.00,Default,,0,0,0,,x")
        )
        assertEquals((3600 + 120 + 3) * 1000L, list[0].startMs)
    }

    @Test
    fun `ass_结束时间不晚于开始时间_丢掉该条`() {
        val list = SubtitleParser.parseAss(
            ass("Dialogue: 0,0:00:05.00,0:00:05.00,Default,,0,0,0,,same")
        )
        assertTrue(list.isEmpty())
    }

    @Test
    fun `ass_文本为空_丢掉该条`() {
        val list = SubtitleParser.parseAss(
            ass("Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,")
        )
        assertTrue(list.isEmpty())
    }

    @Test
    fun `ass_输出按开始时间排序_哪怕输入是乱的`() {
        val list = SubtitleParser.parseAss(
            ass(
                "Dialogue: 0,0:00:09.00,0:00:10.00,Default,,0,0,0,,third",
                "Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,first",
                "Dialogue: 0,0:00:05.00,0:00:06.00,Default,,0,0,0,,second",
            )
        )
        assertEquals(listOf("first", "second", "third"), list.map { it.text })
    }

    // ==================================================================
    // ③ parseAss —— 字段顺序（ASS 与 SSA 不一样，不能写死下标）
    // ==================================================================

    @Test
    fun `ass_字段顺序按Format行取_顺序打乱也能读对`() {
        // 写死下标就会读错列 —— 这里故意把字段顺序改成非标准顺序。
        val content = """
            [Events]
            Format: Start, End, Text, Style
            Dialogue: 0:00:01.00,0:00:02.00,Hello world,Default
        """.trimIndent()
        val list = SubtitleParser.parseAss(content)
        assertEquals(1, list.size)
        assertEquals("Hello world", list[0].text)
        assertEquals(1000L, list[0].startMs)
    }

    @Test
    fun `ass_没有Format行_用保底10字段顺序`() {
        val content = """
            [Events]
            Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Fallback
        """.trimIndent()
        val list = SubtitleParser.parseAss(content)
        assertEquals(1, list.size)
        assertEquals("Fallback", list[0].text)
    }

    @Test
    fun `ass_Text字段里的逗号不能把这一行切碎`() {
        // Text 是最后一个字段、内部可以含逗号 ⇒ split 必须设 limit。
        val list = SubtitleParser.parseAss(
            ass("Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,a, b, c")
        )
        assertEquals("a, b, c", list[0].text)
    }

    // ==================================================================
    // ④ parseAss —— 过滤不该显示的内容
    // ==================================================================

    @Test
    fun `ass_Comment行不是对白_忽略`() {
        val list = SubtitleParser.parseAss(
            ass("Comment: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,这是注释不该显示")
        )
        assertTrue(list.isEmpty())
    }

    @Test
    fun `ass_特效歌词等垃圾样式要丢掉`() {
        val list = SubtitleParser.parseAss(
            ass(
                "Dialogue: 0,0:00:01.00,0:00:02.00,Sign,,0,0,0,,sign text",
                "Dialogue: 0,0:00:03.00,0:00:04.00,OP,,0,0,0,,op text",
                "Dialogue: 0,0:00:05.00,0:00:06.00,歌词,,0,0,0,,歌词文本",
                "Dialogue: 0,0:00:07.00,0:00:08.00,Default,,0,0,0,,真正的对白",
            )
        )
        // 只剩 Default 那条
        assertEquals(1, list.size)
        assertEquals("真正的对白", list[0].text)
    }

    @Test
    fun `ass_空样式当对白保留`() {
        val list = SubtitleParser.parseAss(
            ass("Dialogue: 0,0:00:01.00,0:00:02.00,,,0,0,0,,no style")
        )
        assertEquals(1, list.size)
        assertEquals("no style", list[0].text)
    }

    @Test
    fun `ass_不在Events段的Dialogue_忽略`() {
        // 有些文件在别的段里也可能出现 "Dialogue:" 字样，别误收。
        val content = """
            [V4+ Styles]
            Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,不该收
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:03.00,0:00:04.00,Default,,0,0,0,,该收
        """.trimIndent()
        val list = SubtitleParser.parseAss(content)
        assertEquals(1, list.size)
        assertEquals("该收", list[0].text)
    }

    // ==================================================================
    // ⑤ parseAss —— 同时刻多条（双语合并）
    // ==================================================================

    @Test
    fun `ass_同一时刻中英两条_合并成一句_英文在上`() {
        // 双语 ASS 常见形态：中文一条、英文一条，时间完全相同 ⇒ 合成一句、英文在上。
        val list = SubtitleParser.parseAss(
            ass(
                "Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,你好世界",
                "Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hello world",
            )
        )
        assertEquals(1, list.size)
        assertEquals("Hello world\n你好世界", list[0].text)
    }

    @Test
    fun `ass_同一时刻超过两条_只留两条`() {
        // 三条同刻（比如中/英/日）⇒ 只取最"像英文"的两条，避免一句字幕堆三行。
        val list = SubtitleParser.parseAss(
            ass(
                "Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,你好世界",
                "Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hello world",
                "Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Bonjour le monde",
            )
        )
        assertEquals(1, list.size)
        // 只留 2 行（不是 3 行）
        assertEquals(2, list[0].text.split("\n").size)
    }

    @Test
    fun `ass_同一时刻内容完全重复_去重`() {
        val list = SubtitleParser.parseAss(
            ass(
                "Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hello",
                "Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hello",
            )
        )
        assertEquals(1, list.size)
        assertEquals("Hello", list[0].text)
    }

    @Test
    fun `ass_空内容_返回空表`() {
        assertTrue(SubtitleParser.parseAss("").isEmpty())
        assertTrue(SubtitleParser.parseAss("[Events]\n").isEmpty())
    }

    // ==================================================================
    // ⑥ pickBest —— 字幕自动匹配
    // ==================================================================

    @Test
    fun `匹配_完全同名优先命中`() {
        val subs = listOf(File("other.srt"), File("movie.srt"), File("movie.ass"))
        val hit = SubtitleParser.pickBest("movie.mp4", subs)
        assertEquals("movie.srt", hit?.name)
    }

    @Test
    fun `匹配_扩展名优先级_srt优先于ass`() {
        // EXTS = [srt, ass, ssa] ⇒ 同基名时按这个顺序取。
        val subs = listOf(File("movie.ass"), File("movie.srt"))
        assertEquals("movie.srt", SubtitleParser.pickBest("movie.mp4", subs)?.name)
    }

    @Test
    fun `匹配_大小写不敏感`() {
        val subs = listOf(File("MOVIE.SRT"))
        assertEquals("MOVIE.SRT", SubtitleParser.pickBest("movie.mp4", subs)?.name)
    }

    @Test
    fun `匹配_去掉版本尾巴后同名`() {
        val subs = listOf(File("Friends.S01E01.chs&eng.ass"))
        val hit = SubtitleParser.pickBest("Friends.S01E01.1080p.WEB-DL.mp4", subs)
        assertEquals("Friends.S01E01.chs&eng.ass", hit?.name)
    }

    @Test
    fun `匹配_集号对齐_名字毫无共同前缀也能配上`() {
        // 🔴 这是 1008 拿老友记真实文件实测出来的场景：同一集的视频与字幕来自不同压制组，
        //   归一化后互不包含，只有「S01E01」是同一个 —— 集号才是剧集文件真正的身份。
        val video = "S01.E01.1994.BluRay.2160p.10Bit.DV.HDR.HEVC.DDP5.1.mkv"
        val subs = listOf(
            File("Friends.S01E01.The.One.Where.Monica.Gets.A.New.Roommate.DVDRip.AC3.3.2ch.JOG.srt")
        )
        val hit = SubtitleParser.pickBest(video, subs)
        assertNotNull(hit)
        assertEquals("Friends.S01E01.The.One.Where.Monica.Gets.A.New.Roommate.DVDRip.AC3.3.2ch.JOG.srt", hit?.name)
    }

    @Test
    fun `匹配_集号对齐_同一集有两份字幕时不武断挑一个`() {
        // 有歧义（同一集放了中英两份）⇒ 这一级不采用，继续往下走；目录不唯一 ⇒ 最后返回 null。
        val video = "Show.S02E03.1080p.mkv"
        val subs = listOf(File("A.S02E03.chs.srt"), File("B.S02E03.eng.srt"))
        assertNull(SubtitleParser.pickBest(video, subs))
    }

    @Test
    fun `匹配_中文集号也能对`() {
        val subs = listOf(File("某剧.第01集.中字.ass"))
        val hit = SubtitleParser.pickBest("某剧.第01集.1080p.mp4", subs)
        assertNotNull(hit)
    }

    @Test
    fun `匹配_目录里只有一个字幕_就是它`() {
        val subs = listOf(File("随便什么名字.srt"))
        assertEquals("随便什么名字.srt", SubtitleParser.pickBest("movie.mp4", subs)?.name)
    }

    @Test
    fun `匹配_多个候选且都不像_必须返回null_绝不猜`() {
        // 🔴🔴 这是本文件最重要的一条：老代码在这里 `all.firstOrNull()`，
        //   一个目录放多个视频时会把**别的视频的字幕**加载进来，界面上完全看不出来。
        //   谁要是"顺手简化"成取第一个，这条测试立刻红。
        val subs = listOf(File("aaa.srt"), File("bbb.srt"), File("ccc.srt"))
        assertNull(SubtitleParser.pickBest("movie.mp4", subs))
    }

    @Test
    fun `匹配_空字幕表_返回null`() {
        assertNull(SubtitleParser.pickBest("movie.mp4", emptyList()))
    }

    // ------------------------------------------------------------------
    // 【1011】ASR 自动落地到视频旁边 ⇒ 退让名必须能被自动认得
    //
    // 🔴🔴 这两条用例的**边界**（我试错两轮才搞明白，写清楚免得下次又来一遍）：
    //   退让名是「原名 + 后缀」形式（`xxx-ASR.srt` / `xxx-识别.srt`），而 `pickBest` 第 3 级
    //   是**基名互相包含**判定 ⇒ `xxxasr`.contains(`xxx`) 恒为真 ⇒ **第 3 级天然兜住**。
    //   结论：`NOISE` 里加 `asr/识别/生成` 对这两个命名是**冗余保险**（不是必需修复）——
    //   我拿变异验证砸过 `asr` 那一行，用例**仍然是绿的**，证明它没被真正测到。
    //   之所以保留：① 万一将来有人收紧第 3 级（比如要求互含也唯一），它就是唯一防线；
    //   ② `SubtitleExport.writeBesideImpl` 的早退分支靠 `NS` 比对，同规则不打架。
    //   ⚠️ 所以**别把这些用例当成"NOISE 的测试"** —— 它们测的是「退让名能被认出来」这个
    //       用户可见行为。真要测 NOISE 本身，得让两边**互不包含**，那是另一回事。
    //   ⚠️ 每条都故意放一份**干扰字幕**，否则会走到最后一级「目录唯一 ⇒ 只能是它」，
    //       连名字匹配都不参与，用例等于没测（第一版就这么写错的）。
    // ------------------------------------------------------------------

    @Test
    fun `匹配_ASR退让名_能被自动配上`() {
        // 场景：视频旁边是我们 ASR 生成的那份 `xxx-ASR.srt`，同目录还有别的课的字幕。
        // 认不出来 = 下次打开视频字幕"凭空消失"，用户会以为生成的字幕丢了。
        val subs = listOf(File("新概念3.1-ASR.srt"), File("别的课.srt"))
        val hit = SubtitleParser.pickBest("新概念3.1.mp3", subs)
        assertEquals("新概念3.1-ASR.srt", hit?.name)
    }

    @Test
    fun `匹配_抓的字幕与ASR副本共存_优先命中原名那份`() {
        // 先抓了字幕（`xxx.srt`），后来又 ASR 生成 ⇒ 退让成 `xxx-ASR.srt`。
        // 两份都在时**同名的抓取版优先**（更准），绝不能被 ASR 版抢走 —— 走第 1 级完全同名。
        val subs = listOf(File("新概念3.1-ASR.srt"), File("新概念3.1.srt"))
        val hit = SubtitleParser.pickBest("新概念3.1.mp3", subs)
        assertEquals("新概念3.1.srt", hit?.name)
    }

    @Test
    fun `匹配_点分与识别后缀的退让名_都能配上`() {
        // 顺带钉住另外两种写法：点分隔 `movie.ASR.srt`、手动导出的 `movie-识别.srt`。
        assertEquals(
            "movie.ASR.srt",
            SubtitleParser.pickBest("movie.mp4", listOf(File("movie.ASR.srt"), File("other.srt")))?.name,
        )
        assertEquals(
            "movie-识别.srt",
            SubtitleParser.pickBest("movie.mp4", listOf(File("movie-识别.srt"), File("other.srt")))?.name,
        )
    }

    @Test
    fun `匹配_短名字不参与互含_防乱命中`() {
        // 基名互相包含有最短长度门槛（>=6）⇒ 短名字不会因为"包含"就乱配上。
        val subs = listOf(File("ab.srt"), File("cd.srt"))
        assertNull(SubtitleParser.pickBest("x.mp4", subs))
    }

    @Test
    fun `匹配_完整流程_findBestSubtitle按目录找`() {
        val dir = tmp.newFolder("media")
        File(dir, "movie.mp4").writeText("x")
        File(dir, "movie.srt").writeText("1\n00:00:01,000 --> 00:00:02,000\nhi\n")
        File(dir, "unrelated.srt").writeText("1\n00:00:01,000 --> 00:00:02,000\nno\n")
        val hit = SubtitleParser.findBestSubtitle(File(dir, "movie.mp4").absolutePath)
        assertEquals("movie.srt", hit?.name)
    }

    @Test
    fun `匹配_hasAnySubtitle能判出有没有`() {
        val dir = tmp.newFolder("media2")
        File(dir, "movie.mp4").writeText("x")
        assertTrue(!SubtitleParser.hasAnySubtitle(File(dir, "movie.mp4").absolutePath))
        File(dir, "movie.srt").writeText("1\n00:00:01,000 --> 00:00:02,000\nhi\n")
        assertTrue(SubtitleParser.hasAnySubtitle(File(dir, "movie.mp4").absolutePath))
    }

    // ==================================================================
    // ⑦ parseFile —— 读文件（编码自适应）
    // ==================================================================

    @Test
    fun `读文件_UTF8的srt正常解析`() {
        val f = tmp.newFile("a.srt")
        f.writeText("1\n00:00:01,000 --> 00:00:02,000\nHello\n")
        val list = SubtitleParser.parseFile(f)
        assertEquals(1, list.size)
        assertEquals("Hello", list[0].text)
    }

    @Test
    fun `读文件_带BOM的UTF8_首行不带不可见字符`() {
        val f = tmp.newFile("bom.srt")
        val body = "1\n00:00:01,000 --> 00:00:02,000\nHello\n".toByteArray(Charsets.UTF_8)
        f.writeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + body)
        val list = SubtitleParser.parseFile(f)
        assertEquals(1, list.size)
        assertEquals("Hello", list[0].text)
    }

    @Test
    fun `读文件_GBK编码的中文字幕能正确回退`() {
        // 🔴 网上下的国产字幕组老片大量是 GBK/GB18030，按 UTF-8 硬读会得到满屏 ，
        //   字幕等于废的。readText 的策略是：出现替换字符就改用 GB18030 重读。
        val f = tmp.newFile("gbk.srt")
        val text = "1\n00:00:01,000 --> 00:00:02,000\n中文字幕\n"
        f.writeBytes(text.toByteArray(Charset.forName("GB18030")))
        val list = SubtitleParser.parseFile(f)
        assertEquals(1, list.size)
        assertEquals("中文字幕", list[0].text)
    }

    @Test
    fun `读文件_按扩展名分派_ass走ASS解析器`() {
        val f = tmp.newFile("c.ass")
        f.writeText(ass("Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,from ass"))
        val list = SubtitleParser.parseFile(f)
        assertEquals(1, list.size)
        assertEquals("from ass", list[0].text)
    }

    @Test
    fun `读文件_扩展名认不出_按内容猜SRT`() {
        // 手动选进来的副本后缀可能不可用、或用户把 .txt 改成了字幕 ⇒ 看内容猜一次。
        val f = tmp.newFile("weird.txt")
        f.writeText("1\n00:00:01,000 --> 00:00:02,000\nGuessed\n")
        val list = SubtitleParser.parseFile(f)
        assertEquals(1, list.size)
        assertEquals("Guessed", list[0].text)
    }

    @Test
    fun `读文件_空文件_返回空表不崩`() {
        val f = tmp.newFile("empty.srt")
        assertTrue(SubtitleParser.parseFile(f).isEmpty())
    }

    @Test
    fun `读文件_文件不存在_返回空表不崩`() {
        assertTrue(SubtitleParser.parseFile(File(tmp.root, "nope.srt")).isEmpty())
    }

    // ==================================================================
    // ⑧ listSubtitles —— 目录列举
    // ==================================================================

    @Test
    fun `列举_只收srt与ass_并按优先级排序`() {
        val dir = tmp.newFolder("subs")
        File(dir, "a.ass").writeText("x")
        File(dir, "b.srt").writeText("x")
        File(dir, "c.txt").writeText("x")
        File(dir, "d.mp4").writeText("x")
        val names = SubtitleParser.listSubtitles(dir).map { it.name }
        assertEquals(listOf("b.srt", "a.ass"), names)
    }

    @Test
    fun `列举_不是目录_返回空表`() {
        val f = tmp.newFile("notadir.srt")
        assertTrue(SubtitleParser.listSubtitles(f).isEmpty())
    }
}
