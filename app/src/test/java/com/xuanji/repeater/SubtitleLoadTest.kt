package com.xuanji.repeater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 「字幕装载唯一入口」的单元测试 —— 跑在电脑上，不需要手机、不需要视频文件。
 *
 * ## 这个文件为什么存在（P2 第一刀）
 *
 * `sentences` + `srtName` + `subtitlesFromAsr` 这三个字段**永远一起变**，它们共同描述
 * 「当前装的是哪一份字幕、它是从哪来的」。可它们原来散在 **12 处**各自赋值，还有两处
 * **压根不设 `subtitlesFromAsr`**、靠"前面刚清空过一次"兜着（隐式依赖）。
 *
 * ⇒ 2026-10-10 收成 5 个具名入口（[clearSubtitles] / [loadReadySubtitles] /
 *   [loadAsrSubtitles] / [loadArchivedSubtitles] / [restoreSubtitles]），
 *   并且**故意不给调用方传布尔值** —— 传布尔就一定有人传错，不给就错不了。
 *
 * 这个文件把"错不了"这件事钉住：**任何人日后绕过入口直接给那三个字段赋值，或把某个入口的
 * 来源判定改反，这里立刻红。**
 *
 * ## 能保证什么 / 不能保证什么
 *
 * ✅ 能保证：5 个入口各自种下的"来源判定"是对的；入口之间不会互相污染（带着上一份的旧标志）；
 *    `restoreSubtitles` 的冷启回推与 v131 铁律（**ASR 生成的字幕不重排**）一致。
 * ❌ **不能保证调用时机对** —— 比如"换视频时有没有记得调 `clearSubtitles()`"，
 *    那是控制流，不是这几个函数能管的（仍要靠真机走一遍三条路径：生成字幕 / 选字幕 / 换视频）。
 *
 * ⚠️ 这些测试直接读写 App 的全局 Compose 状态（[sentences] / [srtName] / [subtitlesFromAsr] /
 *   [asrMsg]），所以每个用例都要自己摆好前置状态，不能依赖执行顺序。
 */
class SubtitleLoadTest {

    private val cnEn = listOf(Sentence(0L, 1000L, "中文\nEnglish"))

    @Before
    fun setUp() {
        clearSubtitles()
    }

    // ==================================================================
    // ① clearSubtitles
    // ==================================================================

    @Test
    fun `清空_四个字段一起归零`() {
        loadAsrSubtitles(listOf(Sentence(0, 1000, "x")))
        assertTrue(sentences.isNotEmpty())

        clearSubtitles()

        assertTrue(sentences.isEmpty())
        assertEquals("", srtName)
        assertFalse(subtitlesFromAsr)
        assertEquals("", asrMsg)
    }

    // ==================================================================
    // ② loadReadySubtitles —— 抓来的现成字幕
    // ==================================================================

    @Test
    fun `装现成字幕_来源标志必为false`() {
        loadAsrSubtitles(this.cnEn)          // 先弄成"ASR 的"，看它能不能翻回来
        assertTrue(subtitlesFromAsr)

        loadReadySubtitles(this.cnEn, "movie.srt")

        assertEquals(this.cnEn, sentences)
        assertEquals("movie.srt", srtName)
        assertFalse("抓来的现成字幕必须跟档位/行序 ⇒ 标志只能是 false", subtitlesFromAsr)
        assertEquals("换字幕要把上一条 ASR 的提示清掉，否则会一直显示「上次识别缺失」", "", asrMsg)
    }

    @Test
    fun `装现成字幕_名字原样保留_不做任何改写`() {
        // 手动选的字幕名字可能是「手动：xxx.srt」这种显示名，也可能就是文件名 ⇒ 入口不许动它
        loadReadySubtitles(this.cnEn, "手动：Friends.S01E01.chs&eng.ass")
        assertEquals("手动：Friends.S01E01.chs&eng.ass", srtName)
    }

    // ==================================================================
    // ③ loadAsrSubtitles —— 我们自己生成的字幕
    // ==================================================================

    @Test
    fun `装ASR字幕_名字固定为生成字幕且标志为true`() {
        loadAsrSubtitles(this.cnEn)

        assertEquals(this.cnEn, sentences)
        assertEquals(ASR_SUBTITLE_NAME, srtName)
        assertTrue(subtitlesFromAsr)
        assertEquals("", asrMsg)
    }

    @Test
    fun `装ASR字幕_不接受调用方传名字_防止名字与标志自相矛盾`() {
        // 这条是"编译期保证"的运行时影子：loadAsrSubtitles 的签名里**没有 name 参数**，
        // 所以不可能出现"名字说抓来的、标志说 ASR"这种组合。这里只能断言名字就是那个固定值。
        loadAsrSubtitles(this.cnEn)
        assertEquals(ASR_SUBTITLE_NAME, srtName)
    }

    // ==================================================================
    // ④ loadArchivedSubtitles —— 字幕库归档回退
    // ==================================================================

    @Test
    fun `装归档字幕_库里标记为ASR_标志跟它`() {
        loadArchivedSubtitles(this.cnEn, fromAsr = true)

        assertEquals(ASR_SUBTITLE_NAME, srtName)
        assertTrue(subtitlesFromAsr)
    }

    @Test
    fun `装归档字幕_老数据没有标记_当抓来的处理_名字仍是生成字幕`() {
        // 🔴 这是历史数据的真实情况（v131 之前归档的字幕没有 fromAsr 字段）：
        //   **名字说"生成字幕"、标志说"不是 ASR"** —— 看着矛盾，但这是**故意**的，
        //   因为拿不准就按"抓来的"处理更安全（会被裁档，但不会把英文正文连中文一起丢）。
        loadArchivedSubtitles(this.cnEn, fromAsr = false)

        assertEquals(ASR_SUBTITLE_NAME, srtName)
        assertFalse(subtitlesFromAsr)
    }

    @Test
    fun `装归档字幕_残缺提示按需传入_没有残缺时为空串`() {
        loadArchivedSubtitles(this.cnEn, true, "⚠️ 字幕缺失：上次识别未跑完，点「生成字幕」可重新识别")
        assertTrue(asrMsg.isNotBlank())

        loadArchivedSubtitles(this.cnEn, true)
        assertEquals("", asrMsg)
    }

    // ==================================================================
    // ⑤ restoreSubtitles —— 冷启恢复（这里藏着 v131 铁律）
    // ==================================================================

    @Test
    fun `冷启恢复_抓来的字幕_英文提上`() {
        // srtName 存档的是文件名 ⇒ 不是 ASR 的 ⇒ 要按双语行序把英文提上
        restoreSubtitles(encodeSentences(cnEn), "Friends.S01E01.chs&eng.ass")

        assertEquals(1, sentences.size)
        assertEquals("English\n中文", sentences.first().text)
        assertFalse(subtitlesFromAsr)
    }

    @Test
    fun `冷启恢复_ASR字幕_绝不重排`() {
        // 🔴🔴 本文件最重要的一条 —— v131 阿亮定的铁律：
        //   ASR 出来的是**中英混杂**（语气词中文、正文英文），不是双语对照。
        //   一旦按"英文提上"重排，那行含汉字的会被整行挪到下面，位置就不是原样了。
        //   ⇒ 冷启恢复时必须**先按 srtName 认出它是 ASR 的**，再拿这个结论去解句子。
        //   ⚠️ 顺序写反（先 decode 再定标志）这条就会红 —— 那正是原来散着写时最容易踩的坑。
        restoreSubtitles(encodeSentences(cnEn), ASR_SUBTITLE_NAME)

        assertEquals(1, sentences.size)
        assertEquals("中文\nEnglish", sentences.first().text)
        assertTrue(subtitlesFromAsr)
        assertEquals(ASR_SUBTITLE_NAME, srtName)
    }

    @Test
    fun `冷启恢复_空字幕_不崩且字段仍自洽`() {
        restoreSubtitles("", ASR_SUBTITLE_NAME)

        assertTrue(sentences.isEmpty())
        assertTrue(subtitlesFromAsr)
    }

    // ==================================================================
    // ⑥ 入口之间的隔离 + 不变量
    // ==================================================================

    @Test
    fun `关键_先装ASR再装现成_标志必须翻回来_不能带着旧值`() {
        // 这就是"12 处散着写"最容易出事的地方：装载新字幕时忘了重置来源 ⇒
        // 界面会拿旧标志去裁新字幕。收口后这件事由入口保证。
        loadAsrSubtitles(this.cnEn)
        assertTrue(subtitlesFromAsr)

        loadReadySubtitles(this.cnEn, "a.srt")
        assertFalse(subtitlesFromAsr)

        loadArchivedSubtitles(this.cnEn, fromAsr = false)
        assertFalse(subtitlesFromAsr)
    }

    @Test
    fun `关键_任何入口之后_标志为true则名字必须是生成字幕`() {
        // 不变量：`subtitlesFromAsr == true` ⇒ `srtName` 只能是那个固定名。
        //   反过来说"名字是生成字幕、标志是 false"是**允许**的（见 loadArchivedSubtitles 的老数据用例）。
        // 谁日后新增入口时把这个组合写反，这里就红。
        fun check(what: String) {
            if (subtitlesFromAsr) {
                assertEquals("$what：标志说 ASR、名字却不是固定名 —— 组合不自洽", ASR_SUBTITLE_NAME, srtName)
            }
        }

        clearSubtitles(); check("清空")
        loadReadySubtitles(cnEn, "a.srt"); check("现成字幕")
        loadAsrSubtitles(cnEn); check("当场 ASR")
        loadArchivedSubtitles(cnEn, fromAsr = true); check("归档(ASR)")
        loadArchivedSubtitles(cnEn, fromAsr = false); check("归档(老数据)")
        restoreSubtitles(encodeSentences(cnEn), ASR_SUBTITLE_NAME); check("冷启(ASR)")
        restoreSubtitles(encodeSentences(cnEn), "a.srt"); check("冷启(现成)")
    }

    @Test
    fun `关键_换视频清空后_一定是非ASR的全空状态`() {
        // openVideoFromLibrary / openVideoFileByPath 换视频时都走这里，
        // 之后无论抓到什么字幕都会重新走 loadReadySubtitles ⇒ 不会残留上一个视频的 ASR 标志
        loadAsrSubtitles(this.cnEn)
        clearSubtitles()

        assertTrue(sentences.isEmpty())
        assertFalse("换视频后来源必须作废", subtitlesFromAsr)
        assertEquals("", srtName)
    }
}
