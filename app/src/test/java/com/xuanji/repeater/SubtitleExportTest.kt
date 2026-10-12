package com.xuanji.repeater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [SubtitleExport] 的单测。【1011 新建；1012 按新落地规则重写】
 *
 * 为什么必须测：ASR 生成完**自动往视频旁边写 `.srt`**，它直接动用户的文件夹。
 * 写错轻则天天重复写文件，重则**把用户自己下的字幕盖掉**（找不回来）。
 * 这种事在真机上"偶尔"才看得到，必须靠单测钉住。
 *
 * 🔴🔴【1012 规则变更】**ASR 落地固定写 `<基名>-ASR.srt`，不再"优先抢 `<基名>.srt`"**。
 *   原因见 [SubtitleExport.writeAsrBeside] 的注释（抢同名会让**来源标志丢失**：
 *   下次打开走「完全同名」匹配、被当成抓来的 ⇒ 菜单档位误变可用）。
 *   ⇒ 好几条断言从"写基名.srt"改成了"永远写 -ASR"。
 *   ⇒ 并新增一整组 [SubtitleExport.isAsrExport] 用例 —— 它是"打开视频时认回 ASR 来源"的**唯一锚点**，
 *     判错 = 菜单状态错乱（真机表现：同一份字幕，冷启能点/从媒体库打开不能点）。
 *
 * ⚠️ 边界：这里测的是**文件落盘行为 + 后缀判据**，不测 `pickBest` 能不能认出这个后缀名
 *   （那部分在 `SubtitleParserTest`）。两边分开，免得一处改动同时弄红一堆用例。
 */
class SubtitleExportTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun s(startMs: Long, endMs: Long, text: String) =
        Sentence(startMs = startMs, endMs = endMs, text = text)

    private val sample = listOf(s(0, 1000, "hello"), s(1000, 2000, "world"))

    // ==================================================================
    // ① 落盘位置：**永远是 `<基名>-ASR.srt`**（不论旁边有没有别的字幕）
    // ==================================================================

    @Test
    fun `ASR导出_永远写成减ASR后缀`() {
        val dir = tmp.newFolder("media")
        val video = File(dir, "新概念3.1.mp4").apply { writeText("x") }

        val r = SubtitleExport.writeAsrBeside(video.absolutePath, sample)

        assertTrue(r.ok)
        val out = File(dir, "新概念3.1-ASR.srt")
        assertTrue("应写成 <基名>-ASR.srt", out.isFile)
        assertEquals(out.canonicalPath, r.file?.canonicalPath)
        assertTrue(out.readText().contains("hello"))
    }

    @Test
    fun `ASR导出_旁边有抓的字幕时_依然只碰减ASR那份`() {
        // 🔴 这是本次修 bug 的核心防线：以前会去抢 `<基名>.srt` 那个位置，
        //   跟"抓来的"撞名 ⇒ 下次打开视频被当成抓来的（来源标志丢失）。
        //   现在各归各位：用户那份**一个字节不动**，机器生成只写自己的后缀。
        val dir = tmp.newFolder("media")
        val video = File(dir, "ep01.mp4").apply { writeText("x") }
        val grabbed = File(dir, "ep01.srt")
            .apply { writeText("1\n00:00:00,000 --> 00:00:01,000\n抓来的双语") }

        val r = SubtitleExport.writeAsrBeside(video.absolutePath, sample)

        assertTrue(r.ok)
        assertEquals("ep01-ASR.srt", r.file?.name)
        assertEquals(
            "用户自己那份一个字节都不能变",
            "1\n00:00:00,000 --> 00:00:01,000\n抓来的双语",
            grabbed.readText(),
        )
    }

    @Test
    fun `ASR导出_旁边没字幕时也不占用基名位置`() {
        // 以前是"旁边没字幕 ⇒ 写 <基名>.srt"，现在一律 `-ASR`。
        // 目的：让 `<基名>.srt` 这个"黄金位置"**永远留给好字幕**（抓的 / 用户自己下的）。
        val dir = tmp.newFolder("media")
        val video = File(dir, "ep02.mp4").apply { writeText("x") }

        SubtitleExport.writeAsrBeside(video.absolutePath, sample)

        assertFalse("<基名>.srt 不该被 ASR 占用", File(dir, "ep02.srt").exists())
        assertTrue(File(dir, "ep02-ASR.srt").isFile)
    }

    @Test
    fun `ASR导出_内容格式是标准SRT`() {
        val dir = tmp.newFolder("media")
        val video = File(dir, "a.mp4").apply { writeText("x") }
        SubtitleExport.writeAsrBeside(video.absolutePath, sample)

        val text = File(dir, "a-ASR.srt").readText()
        // 序号 + 逗号毫秒时间轴 + 空行分隔，缺一个别的播放器都可能读不了
        assertTrue(text.startsWith("1\n00:00:00,000 --> 00:00:01,000\nhello\n\n2\n"))
    }

    // ==================================================================
    // ② 重复生成 / 内容变更（`-ASR` 是 ASR 的专属位置）
    // ==================================================================

    @Test
    fun `ASR导出_反复生成内容一样_不重复写且报已存在`() {
        val dir = tmp.newFolder("media")
        val video = File(dir, "ep01.mp4").apply { writeText("x") }

        val first = SubtitleExport.writeAsrBeside(video.absolutePath, sample)
        assertTrue(first.ok)
        val out = File(dir, "ep01-ASR.srt")
        val stamp = out.lastModified()

        // 再生成一次，内容相同 ⇒ 命中早退分支，不该重写
        val second = SubtitleExport.writeAsrBeside(video.absolutePath, sample)
        assertTrue(second.ok)
        assertEquals("内容一样时不该重写文件", stamp, out.lastModified())
        assertEquals(out.canonicalPath, second.file?.canonicalPath)
    }

    @Test
    fun `ASR导出_重新识别出不同内容_覆盖自己那份旧结果`() {
        // 和上面"内容一样不重写"互补：这是 ASR 的专属位置，内容变了就该更新。
        // ⚠️ 覆盖的是**自己上次的结果**，不涉及任何用户文件。
        val dir = tmp.newFolder("media")
        val video = File(dir, "ep01.mp4").apply { writeText("x") }
        File(dir, "ep01-ASR.srt").writeText("上一次识别出来的旧内容")

        val r = SubtitleExport.writeAsrBeside(video.absolutePath, sample)

        assertTrue(r.ok)
        val out = File(dir, "ep01-ASR.srt")
        assertTrue("应该更新成新内容", out.readText().contains("hello"))
        assertFalse("旧内容应该被换掉", out.readText().contains("上一次识别出来的旧内容"))
    }

    // ==================================================================
    // ③ isAsrExport —— 打开视频时"认回 ASR 来源"的唯一锚点
    // ==================================================================

    @Test
    fun `isAsrExport_认出自己落的减ASR文件`() {
        assertTrue(SubtitleExport.isAsrExport("ep01-ASR.srt"))
        assertTrue(SubtitleExport.isAsrExport("ep01-ASR.ass"))
        assertTrue("大小写不敏感", SubtitleExport.isAsrExport("ep01-asr.srt"))
    }

    @Test
    fun `isAsrExport_不误认抓来的和用户下的`() {
        assertFalse("普通字幕，不带后缀", SubtitleExport.isAsrExport("ep01.srt"))
        assertFalse("名字里含 ASR 字样但不是我们的后缀，不能误判", SubtitleExport.isAsrExport("Friends.S01E04.ASRip.srt"))
        assertFalse(SubtitleExport.isAsrExport("中上英下ASS.srt"))
    }

    @Test
    fun `isAsrExport_无扩展名也不崩`() {
        assertFalse(SubtitleExport.isAsrExport("noext"))
        assertTrue(SubtitleExport.isAsrExport("noext-ASR"))
    }

    // ==================================================================
    // ④ 早退/失败的各种前置条件
    // ==================================================================

    @Test
    fun `导出_没有字幕_报清楚不写文件`() {
        val dir = tmp.newFolder("media")
        val video = File(dir, "a.mp4").apply { writeText("x") }

        val r = SubtitleExport.writeAsrBeside(video.absolutePath, emptyList())

        assertFalse(r.ok)
        assertFalse(File(dir, "a-ASR.srt").exists())
    }

    @Test
    fun `导出_全是空白文本的句子_也算没字幕`() {
        val dir = tmp.newFolder("media")
        val video = File(dir, "a.mp4").apply { writeText("x") }

        val r = SubtitleExport.writeAsrBeside(video.absolutePath, listOf(s(0, 1000, "   ")))

        assertFalse(r.ok)
        assertFalse(File(dir, "a-ASR.srt").exists())
    }

    @Test
    fun `导出_媒体路径为空_报拿不到路径`() {
        val r = SubtitleExport.writeAsrBeside("", sample)
        assertFalse(r.ok)
        assertTrue(r.message.contains("路径"))
    }

    @Test
    fun `导出_视频所在目录不存在_报读不到不崩`() {
        val r = SubtitleExport.writeAsrBeside("/definitely/not/here/a.mp4", sample)
        assertFalse(r.ok)
        assertNull(r.file)
    }

    @Test
    fun `导出_视频名没有扩展名_也按基名拼后缀`() {
        val dir = tmp.newFolder("media")
        val video = File(dir, "noext").apply { writeText("x") }

        val r = SubtitleExport.writeAsrBeside(video.absolutePath, sample)

        assertTrue(r.ok)
        // substringBeforeLast('.') 在无点时返回原串 ⇒ 得到 `noext-ASR.srt`
        assertTrue(File(dir, "noext-ASR.srt").isFile)
    }

    // ==================================================================
    // ⑤ toSrt 本体
    // ==================================================================

    @Test
    fun `toSrt_按开始时间排序_零长度句兜最小时长`() {
        val text = SubtitleExport.toSrt(
            listOf(s(5000, 6000, "后"), s(0, 0, "零长度"))
        )
        // 排序：零长度在前
        assertTrue(text.startsWith("1\n00:00:00,000 --> 00:00:00,800\n零长度"))
        assertTrue(text.contains("2\n00:00:05,000 --> 00:00:06,000\n后"))
    }

    @Test
    fun `toSrt_文本内换行原样保留`() {
        val text = SubtitleExport.toSrt(listOf(s(0, 1000, "EN line\n中文行")))
        assertTrue(text.contains("EN line\n中文行"))
    }
}
