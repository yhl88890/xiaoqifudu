package com.xuanji.repeater

import com.xuanji.repeater.SubtitleState.asrMsg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 「视频生成字幕任务」的开始 / 结束状态归位入口的单元测试 —— 跑在电脑上，不需要手机。
 *
 * ## 为什么值得为这么两个小函数写测试
 *
 * `recognizing` 是「识别任务在跑」的唯一标志，UI 直接拿它当开关：
 *   · `MainActivity` 顶栏：`enabled = !genActive`（`genActive = recognizing`）
 *     ⇒ 🔴 **卡在 true 就是「生成字幕」按钮永久点不动**；
 *   · `VideoPage` 的转圈行 / 遮挡层同理。
 *
 * 而一次识别原来在**每个出口各手写一遍**归位代码（正常出口 + `catch (Throwable)`，两处逐行一样）。
 * 将来再加出口（取消 / 超时 / 提前返回）**漏一处** ⇒ 用户永久卡转圈 ——
 * 而且这种 bug 真机上像"偶发"、单测又抓不到，极难查。
 *
 * ## 钉住的两条不变量
 *
 * 1. `beginAsrRun()` 之后**一定**是「在跑」：四个字段一次到位，不靠调用方记；
 * 2. `endAsrRun()` 之后**一定**是「没在跑」，而且 🔴 **不许碰 `asrMsg`** ——
 *    结果文案（"识别完成，共 N 句" / "识别出错：…"）是在 `endAsrRun()` **之后**才写的，
 *    结束函数一旦"顺手"清一下 `asrMsg`，用户就永远看不到结果。
 *
 * ## 能保证什么 / 不能保证什么
 *
 * ✅ 能保证：两个入口各自的字段效果是对的；职责边界清楚（谁碰 `asrMsg`、谁不碰）。
 * ❌ **不能保证调用时机** —— "三条出口是不是都记得调 `endAsrRun()`"是控制流，单测管不到；
 *    仍要真机验（点一次「生成字幕」，看跑完/出错后按钮能不能恢复可点）。
 *
 * ⚠️ 这些测试直接读写 App 的全局 Compose 状态，所以每个用例自己摆前置状态，不依赖执行顺序。
 */
class AsrRunTest {

    @Before
    fun setUp() {
        // 从「没在跑 + 无残留」起步
        endAsrRun()
        asrProgress = 0
        asrMsg = ""
        asrStage = null
    }

    // ==================================================================
    // ① beginAsrRun：四个字段一次归位
    // ==================================================================

    @Test
    fun `开始识别_四个字段一次归位`() {
        // 先摆一轮「上一轮识别留下的东西」
        recognizing = false
        asrProgress = 87
        asrMsg = "识别完成，共 3 句"
        asrStage = "上传识别中"

        beginAsrRun()

        assertTrue("必须置为「在跑」，否则按钮点不动、转圈不出来", recognizing)
        assertEquals("进度从 0 开始", 0, asrProgress)
        assertEquals("上一轮的结果文案作废", "", asrMsg)
        assertNull("上一轮阶段名作废（否则 UI 一直挂着『上传识别中』）", asrStage)
    }

    @Test
    fun `开始识别_上一轮的错误文案要清掉`() {
        asrMsg = "识别出错：timeout"

        beginAsrRun()

        assertEquals("再点一次不该还挂着上次的报错", "", asrMsg)
    }

    // ==================================================================
    // ② endAsrRun：只归位，不许碰结果文案（最关键的一条）
    // ==================================================================

    @Test
    fun `结束识别_只归位两个字段_绝不能清掉结果文案`() {
        recognizing = true
        asrStage = "上传识别中"
        // 真实顺序：`endAsrRun()` 之后才由 videoGenerateLyrics 写入结果文案
        asrMsg = "识别完成，共 12 句（3/5 段成功）"

        endAsrRun()

        assertFalse("必须恢复「没在跑」，否则按钮永久禁用", recognizing)
        assertNull("阶段名要清", asrStage)
        assertEquals(
            "🔴 结果文案是在 endAsrRun() 之后写的 —— 结束函数若顺手清 asrMsg，用户就永远看不到识别结果",
            "识别完成，共 12 句（3/5 段成功）",
            asrMsg,
        )
    }

    @Test
    fun `结束识别_重复调也安全`() {
        endAsrRun()
        endAsrRun()

        assertFalse(recognizing)
        assertNull(asrStage)
    }

    @Test
    fun `结束识别_不碰进度值`() {
        // 进度归 beginAsrRun / postAsrProgress 管，结束函数不该越界去动它
        asrProgress = 42

        endAsrRun()

        assertEquals("职责边界：end 只归位 recognizing + asrStage", 42, asrProgress)
    }

    // ==================================================================
    // ③ 来回切换：识别 → 出错 → 再识别（状态不许串）
    // ==================================================================

    @Test
    fun `识别_出错_再识别_状态不串`() {
        beginAsrRun()
        assertTrue(recognizing)

        endAsrRun()
        asrMsg = "识别出错：boom"        // 异常出口写的文案
        assertFalse(recognizing)

        beginAsrRun()                    // 用户再点一次
        assertTrue(recognizing)
        assertEquals("上一轮的错误文案必须清掉", "", asrMsg)
        assertNull(asrStage)
    }

    // ==================================================================
    // ④ 顶栏按钮开关的两条依据
    // ==================================================================

    @Test
    fun `不变量_begin之后一定是「在跑」`() {
        beginAsrRun()

        assertTrue("按钮 disabled 依据：enabled = !genActive，genActive = recognizing", recognizing)
    }

    @Test
    fun `不变量_end之后一定是「没在跑」`() {
        beginAsrRun()
        endAsrRun()

        assertFalse("按钮恢复可点的依据", recognizing)
    }
}
