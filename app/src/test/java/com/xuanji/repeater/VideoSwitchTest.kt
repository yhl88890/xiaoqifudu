package com.xuanji.repeater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 「换视频状态重置唯一入口」的单元测试 —— 跑在电脑上，不需要手机、不需要视频文件。
 *
 * ## 这个文件为什么存在（P2 第二刀）
 *
 * 打开/切换视频时有一组状态必须**一起**重置（当前是哪条视频 + 播到哪 + 后台交接残留 + 字幕），
 * 否则会出现这些真机上才看得见的毛病：
 *   · 换了视频但 Uri 没换 ⇒ 画面还是上一个（名字变了、内容没变）
 *   · `currentPos` 带过来 ⇒ 新视频一打开就跳到中间
 *   · `videoResumePos` / `videoHandedToBg` 带过来 ⇒ CREATE 续播跳到"上次后台的位置"（历史 bug 33246）
 *   · `sentences` 带过来 ⇒ **新视频挂着旧视频的字幕**，时间轴完全对不上
 *
 * 这组重置原来在 [openVideoFromLibrary] 和 [openVideoFileByPath] 里**各抄了一遍**（十几行 × 2），
 * 而且已经悄悄分叉了。⇒ 2026-10-10 收成 [switchToVideo] 一个入口。
 *
 * 这个文件把"一起变"这件事钉住：**任何人日后在入口里少清一个字段，这里立刻红。**
 *
 * ## 能保证什么 / 不能保证什么
 *
 * ✅ 能保证：[switchToVideo] 把该清的都清了；两条入口对这个函数的调用参数对得上（`Uri` 由调用方给）。
 * ❌ **不能保证调用时机对** —— 比如"两条入口是不是都记得调它"，那是控制流，
 *    仍要靠真机走一遍（换视频 / 生成字幕 / 选字幕）。
 * ⚠️ **不能覆盖 `videoUri` 的"换没换"** —— `android.net.Uri` 在 JVM 单测里造不出实例
 *    （`Uri.parse` 走桩返回 null）⇒ 这里只能钉"能接受 null 并原样存下"。
 *    真机上"新视频画面出不来"这类问题请走真机验收。
 * ⚠️ `videoBgResumePos` / `videoBgWasPlaying` 是 `private`，测试读不到 ⇒ 不在覆盖范围内
 *    （它们目前只有媒体库那条入口清，见 [switchToVideo] 的注释）。
 *
 * ⚠️ 这些测试直接读写 App 的全局 Compose 状态，所以每个用例都要自己摆好前置状态，
 *    不能依赖执行顺序（[makeDirty] 就是干这个的）。
 */
class VideoSwitchTest {

    @Before
    fun setUp() {
        // 不假设上一个用例留下了什么，从"干净"起步
        videoRestoreFailed = false
        videoPlaying = false
        videoPath = ""
        videoName = ""
        currentPos = 0L
        videoSentenceIndexGlobal = 0
        videoHandedToBg = false
        videoResumePos = 0L
        videoFolderVideos = emptyList()
        videoUri = null
        clearSubtitles()
    }

    /**
     * 摆成「上一个视频 + 上一轮后台交接」留下的脏状态。
     *
     * 值都是刻意挑的：非法下标(7)、后台交接位(33246，历史上真出现过)、非零位置(99000)，
     * 一眼能从断言里看出"到底清没清"。
     */
    private fun makeDirty() {
        videoRestoreFailed = true
        videoPlaying = false
        videoUri = null            // JVM 里造不出 Uri 实例，只能置空（见文件尾说明）
        videoPath = "/old/old.mp4"
        videoName = "old"
        currentPos = 99_000L
        videoSentenceIndexGlobal = 7
        videoHandedToBg = true
        videoResumePos = 33_246L
        videoFolderVideos = listOf(VideoItem(id = 1L, path = "/old/old.mp4", title = "old"))
        loadReadySubtitles(listOf(Sentence(0L, 1000L, "旧视频的字幕")), "old.srt")
    }

    // ==================================================================
    // ① 一次调用把整组状态清干净（这是这个收口存在的全部理由）
    // ==================================================================

    @Test
    fun `换视频_上一轮的脏状态被全部清掉`() {
        makeDirty()

        switchToVideo(null, "/new/new.mp4", emptyList())

        assertEquals("/new/new.mp4", videoPath)
        assertEquals("new", videoName)              // 取文件名、去扩展名
        assertEquals(0L, currentPos)                // 不能带着旧位置跳中间
        assertEquals(0, videoSentenceIndexGlobal)   // 上一句下标作废
        assertFalse(videoHandedToBg)                // 后台交接残留
        assertEquals(0L, videoResumePos)            // 后台交接残留（续播 bug 的源头）
    }

    // ==================================================================
    // ② 后台交接残留 —— 这一段专门钉住历史续播 bug
    // ==================================================================

    @Test
    fun `换视频_后台交接残留要清掉_否则续播会跳到上一个视频的后台位置`() {
        // 历史现场：上一轮切后台停在 33246ms，这次换视频若不清 ⇒ CREATE 续播直接跳那儿
        videoHandedToBg = true
        videoResumePos = 33_246L

        switchToVideo(null, "/new/b.mp4", emptyList())

        assertFalse("videoHandedToBg 必须归位，否则换片会被当成「回前台续播」", videoHandedToBg)
        assertEquals("videoResumePos 必须归零，否则新视频一开就跳到旧视频的后台位置", 0L, videoResumePos)
    }

    // ==================================================================
    // ③ 字幕联动 —— 与 P2 第一刀的收口咬合
    // ==================================================================

    @Test
    fun `换视频_字幕要一起清掉_否则新视频挂着旧视频的字幕`() {
        loadReadySubtitles(listOf(Sentence(0L, 1000L, "旧字幕")), "old.srt")
        assertTrue(sentences.isNotEmpty())   // 前置：确实有旧字幕

        switchToVideo(null, "/new/c.mp4", emptyList())

        assertTrue("换视频必须清字幕（时间轴完全对不上）", sentences.isEmpty())
        assertEquals("", srtName)
        assertFalse("来源标志也要归位，否则新字幕会被按旧来源处理", subtitlesFromAsr)
        assertEquals("", asrMsg)
    }

    // ==================================================================
    // ④ 名字取自文件名，不是路径
    // ==================================================================

    @Test
    fun `换视频_视频名取文件名并去掉扩展名`() {
        switchToVideo(null, "/sdcard/Movies/085&086－Paris in the Spring.mp4", emptyList())

        assertEquals("085&086－Paris in the Spring", videoName)
        assertFalse("别把整条路径当名字", videoName.contains("/"))
    }

    // ==================================================================
    // ⑤ 一定会开始播放 + 恢复失败标志作废
    // ==================================================================

    @Test
    fun `换视频_一定会开始播放`() {
        videoPlaying = false

        switchToVideo(null, "/new/d.mp4", emptyList())

        assertTrue("打开/切换视频默认自动播放", videoPlaying)
    }

    @Test
    fun `换视频_上一轮的恢复失败标志要作废`() {
        // 【1003 加保险】恢复失败 ⇒ 跳过存盘。用户主动选了新视频，这个标志必须翻篇，
        // 否则新视频的播放位置永远存不下来（下次打开从头开始）。
        videoRestoreFailed = true

        switchToVideo(null, "/new/e.mp4", emptyList())

        assertFalse(videoRestoreFailed)
    }

    // ==================================================================
    // ⑥ 同级视频列表：按传入值替换（包括"传空也要替换"）
    // ==================================================================

    @Test
    fun `换视频_同级视频列表按传入值替换`() {
        val siblings = listOf(
            VideoItem(id = 1L, path = "/new/a.mp4", title = "a"),
            VideoItem(id = 2L, path = "/new/b.mp4", title = "b"),
        )

        switchToVideo(null, "/new/b.mp4", siblings)

        assertSame("要的是同一个列表实例，不是拷贝", siblings, videoFolderVideos)
        assertEquals(2, videoFolderVideos.size)
    }

    @Test
    fun `换视频_没传同级列表要清空而不是保留上一个文件夹的`() {
        videoFolderVideos = listOf(VideoItem(id = 9L, path = "/old/z.mp4", title = "z"))

        switchToVideo(null, "/new/f.mp4", emptyList())

        assertTrue("保留旧列表 ⇒ 按「下一个」会跳到上一个文件夹的视频", videoFolderVideos.isEmpty())
    }

    // ==================================================================
    // ⑦ 边界：空路径 / 无扩展名
    // ==================================================================

    @Test
    fun `换视频_空路径不崩且名字为空`() {
        switchToVideo(null, "", emptyList())

        assertEquals("", videoPath)
        assertEquals("", videoName)
        assertEquals(0L, currentPos)
        assertTrue(videoPlaying)
    }

    @Test
    fun `换视频_没有扩展名的路径名字就是原样`() {
        switchToVideo(null, "/sdcard/Movies/noext", emptyList())

        assertEquals("noext", videoName)
    }

    // ==================================================================
    // ⑧ Uri 参数原样存下（真机"换了视频画面没换"的护栏）
    // ==================================================================

    @Test
    fun `换视频_Uri 原样存下_传空就存空`() {
        // ⚠️ JVM 单测里 android.net.Uri 造不出实例（Uri.parse 走桩返回 null），
        // 所以这里只能钉「传 null 存 null」—— 关键是**不能保留上一个视频的 Uri**
        // （真机上那会表现为"点开新视频，画面/声音还是上一个"）。
        videoUri = null

        switchToVideo(null, "/new/g.mp4", emptyList())

        assertNull("换视频必须把 Uri 一起换掉，不能留上一个视频的", videoUri)
    }
}
