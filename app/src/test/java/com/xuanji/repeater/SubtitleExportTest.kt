package com.xuanji.repeater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [SubtitleExport] 的单测。【1011 新建】
 *
 * 为什么必须测：ASR 生成完**自动往视频旁边写 `.srt`** 是本轮新加的行为，
 * 它直接动用户的文件夹。三条分支（早退 / 退让 / 不覆盖）任何一条写错，
 * 轻则天天重复写文件，重则**把用户自己下的字幕盖掉**（找不回来）。
 * 这种事在真机上"偶尔"才看得到，必须靠单测钉住。
 *
 * ⚠️ 边界：这里测的是**文件落盘行为**，不测 `pickBest` 能不能认出退让名
 *   （那部分在 `SubtitleParserTest`）。两边分开，免得一处改动同时弄红一堆用例。
 */
class SubtitleExportTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun s(startMs: Long, endMs: Long, text: String) =
        Sentence(startMs = startMs, endMs = endMs, text = text)

    private val sample = listOf(s(0, 1000, "hello"), s(1000, 2000, "world"))

    // ==================================================================
    // ① 基本落盘
    // ==================================================================

    @Test
    fun `ASR导出_旁边没字幕时_写成基名srt`() {
        val dir = tmp.newFolder("media")
        val video = File(dir, "新概念3.1.mp4").apply { writeText("x") }

        val r = SubtitleExport.writeAsrBeside(video.absolutePath, sample)

        assertTrue(r.ok)
        val out = File(dir, "新概念3.1.srt")
        assertTrue("应该写成 <基名>.srt", out.isFile)
        assertEquals(out.canonicalPath, r.file?.canonicalPath)
        assertTrue(out.readText().contains("hello"))
    }

    @Test
    fun `ASR导出_内容格式是标准SRT`() {
        val dir = tmp.newFolder("media")
        val video = File(dir, "a.mp4").apply { writeText("x") }
        SubtitleExport.writeAsrBeside(video.absolutePath, sample)

        val text = File(dir, "a.srt").readText()
        // 序号 + 逗号毫秒时间轴 + 空行分隔，缺一个别的播放器都可能读不了
        assertTrue(text.startsWith("1\n00:00:00,000 --> 00:00:01,000\nhello\n\n2\n"))
    }

    // ==================================================================
    // ② 退让：同名被占 ⇒ 写 `<基名>-ASR.srt`，**绝不覆盖**
    // ==================================================================

    @Test
    fun `ASR导出_旁边已有抓的字幕_退让成减ASR且不动原文件`() {
        val dir = tmp.newFolder("media")
        val video = File(dir, "ep01.mp4").apply { writeText("x") }
        // 抓来的字幕（内容是双语，用户自己下的 / B站抓的，动了就找不回来）
        val grabbed = File(dir, "ep01.srt").apply { writeText("1\n00:00:00,000 --> 00:00:01,000\n抓来的双语") }

        val r = SubtitleExport.writeAsrBeside(video.absolutePath, sample)

        assertTrue(r.ok)
        val out = File(dir, "ep01-ASR.srt")
        assertTrue("应退让成 -ASR", out.isFile)
        // 🔴 最关键的一条：原来那份**一个字节都不能变**
        assertEquals("1\n00:00:00,000 --> 00:00:01,000\n抓来的双语", grabbed.readText())
    }

    @Test
    fun `ASR导出_反复生成内容一样_不重复写且报已存在`() {
        val dir = tmp.newFolder("media")
        val video = File(dir, "ep01.mp4").apply { writeText("x") }

        val first = SubtitleExport.writeAsrBeside(video.absolutePath, sample)
        assertTrue(first.ok)
        val out = File(dir, "ep01.srt")
        val stamp = out.lastModified()

        // 再生成一次，内容相同 ⇒ 命中早退分支，不该重写
        val second = SubtitleExport.writeAsrBeside(video.absolutePath, sample)
        assertTrue(second.ok)
        assertEquals("内容一样时不该重写文件", stamp, out.lastModified())
        assertEquals(out.canonicalPath, second.file?.canonicalPath)
    }

    @Test
    fun `ASR导出_退让名已存在且内容相同_也早退不重复写`() {
        val dir = tmp.newFolder("media")
        val video = File(dir, "ep01.mp4").apply { writeText("x") }
        File(dir, "ep01.srt").writeText("1\n00:00:00,000 --> 00:00:01,000\n抓来的")

        // 第一次：退让写 -ASR
        val first = SubtitleExport.writeAsrBeside(video.absolutePath, sample)
        assertTrue(first.ok)
        val out = File(dir, "ep01-ASR.srt")
        val stamp = out.lastModified()

        // 第二次：同名还占着、退让名已存在且内容相同 ⇒ 早退
        val second = SubtitleExport.writeAsrBeside(video.absolutePath, sample)
        assertTrue(second.ok)
        assertEquals(stamp, out.lastModified())
        assertEquals("ep01-ASR.srt", second.file?.name)
    }

    @Test
    fun `ASR导出_两个名字都被占且内容都不同_不覆盖并报错`() {
        val dir = tmp.newFolder("media")
        val video = File(dir, "ep01.mp4").apply { writeText("x") }
        File(dir, "ep01.srt").writeText("抓的")
        val asr = File(dir, "ep01-ASR.srt").apply { writeText("上次生成的别的内容") }

        val r = SubtitleExport.writeAsrBeside(video.absolutePath, sample)

        assertFalse("两个名字都占着且内容不同 ⇒ 必须失败而不是覆盖", r.ok)
        assertEquals("上次生成的别的内容", asr.readText())
        assertTrue(r.message.contains("不覆盖"))
    }

    // ==================================================================
    // ③ 两个入口互不串味（退让名不同）
    // ==================================================================

    @Test
    fun `手动导出_退让名是减识别_不是减ASR`() {
        val dir = tmp.newFolder("media")
        val video = File(dir, "ep01.mp4").apply { writeText("x") }
        File(dir, "ep01.srt").writeText("抓的")

        val r = SubtitleExport.writeBeside(video.absolutePath, sample)

        assertTrue(r.ok)
        assertEquals("ep01-识别.srt", r.file?.name)
        assertFalse("手动导出不该写出 -ASR", File(dir, "ep01-ASR.srt").exists())
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
        assertFalse(File(dir, "a.srt").exists())
    }

    @Test
    fun `导出_全是空白文本的句子_也算没字幕`() {
        val dir = tmp.newFolder("media")
        val video = File(dir, "a.mp4").apply { writeText("x") }

        val r = SubtitleExport.writeAsrBeside(video.absolutePath, listOf(s(0, 1000, "   ")))

        assertFalse(r.ok)
        assertFalse(File(dir, "a.srt").exists())
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
    fun `导出_视频名没有扩展名_也按基名写`() {
        val dir = tmp.newFolder("media")
        val video = File(dir, "noext").apply { writeText("x") }

        val r = SubtitleExport.writeAsrBeside(video.absolutePath, sample)

        assertTrue(r.ok)
        // substringBeforeLast('.') 在无点时返回原串 ⇒ 得到 `noext.srt`
        assertTrue(File(dir, "noext.srt").isFile)
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
