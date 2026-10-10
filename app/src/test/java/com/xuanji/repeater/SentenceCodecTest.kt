package com.xuanji.repeater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `SentenceCodec` 的单元测试 —— **跑在电脑上，不需要手机**。
 *
 * ## 为什么这个文件非存在不可
 *
 * 这个文件里的每个函数都是**纯逻辑**（不碰 Android API），却是全工程「改一次错一次」的重灾区：
 * 句序归一、档位裁剪、ASR 豁免、双语往返 —— 踩过的坑全在这儿。
 *
 * 🔴 而在此之前，这块的验证方式是：**改代码 → 编译 → 装 APK → 真机长按 → 截图/dump 看结果**。
 *   2026-10-10 那天为了验「ASR 混杂字幕会不会被裁」，在真机上试了 8 次长按（列表一直在自动
 *   滚动、坐标拿到就过期），改了 xml、折腾 20 多分钟，测完还得还原。
 *   **同样的验证写成这里的一条断言 = 几毫秒，而且以后每次改代码都会自动重跑。**
 *
 * ## 这个文件能保证什么、不能保证什么（别期待错）
 *
 * - ✅ **能**：这些函数的**逻辑**对不对、边界情况覆没覆盖（单行/空串/纯单语/三行/混杂）。
 * - ❌ **不能**：这些函数有没有在**正确的时机被调用**。
 *   例：2026-10-10 那次「改解析器白折腾」——`orderBilingual` 本身没错，错在冷启动那条路
 *   **根本不过解析器**（它直接读 `xuanji_video.xml` 快照）。那属于**架构/集成**问题，
 *   单测管不着，只能靠日志或集成测试。
 *
 * ## 维护约定
 *
 * 改了 `SentenceCodec.kt` 里的任何规则 ⇒ **先跑 `gradlew testDebugUnitTest`**。
 * 如果某条断言红了，先想清楚「是我改错了，还是这条断言该跟着改」——**别顺手把断言删掉**。
 */
class SentenceCodecTest {

    // ==================================================================
    // ① orderBilingual —— 双语行序归一（英文提上）
    // ==================================================================

    @Test
    fun `中文在上_应改成英文在上`() {
        assertEquals("English\n中文", orderBilingual("中文\nEnglish"))
    }

    @Test
    fun `已经在英文在上_原样返回_且幂等`() {
        val t = "English\n中文"
        assertEquals(t, orderBilingual(t))
        // 幂等：跑两次结果不变（存量数据每次冷启都会过一遍这条路）
        assertEquals(t, orderBilingual(orderBilingual(t)))
    }

    @Test
    fun `单行_无论中英_都原样`() {
        assertEquals("English only", orderBilingual("English only"))
        assertEquals("只有中文", orderBilingual("只有中文"))
    }

    @Test
    fun `纯中文多行_必须原样_不能重排`() {
        // 没有英文行 ⇒ 提前返回。若误重排，两行的先后顺序会被打乱。
        val t = "第一行中文\n第二行中文"
        assertEquals(t, orderBilingual(t))
    }

    @Test
    fun `纯英文多行_原样`() {
        val t = "line one\nline two"
        assertEquals(t, orderBilingual(t))
    }

    @Test
    fun `三行混合_英文组整体在前_组内保持相对顺序`() {
        assertEquals("E1\nE2\n中", orderBilingual("中\nE1\nE2"))
        assertEquals("E1\nE2\n中", orderBilingual("E1\n中\nE2"))
    }

    @Test
    fun `空字符串_原样`() {
        assertEquals("", orderBilingual(""))
    }

    @Test
    fun `首尾空行会被丢掉_记录现有行为`() {
        // 输出只由「有效行」拼成（isNotBlank 过滤）⇒ 首尾空行不留。
        // 这是现有行为，写下来是为了「以后万一变了能立刻发现」，不是要求改成别的样子。
        assertEquals("English\n中文", orderBilingual("\n中文\nEnglish\n"))
    }

    // ==================================================================
    // ② filterByMode —— 字幕档位裁剪（只减行、不改字）
    // ==================================================================

    @Test
    fun `双语_只看英文_只留英文行`() {
        assertEquals("English", filterByMode("English\n中文", SubtitleMode.EN_ONLY))
    }

    @Test
    fun `双语_只看中文_只留中文行`() {
        assertEquals("中文", filterByMode("English\n中文", SubtitleMode.CN_ONLY))
    }

    @Test
    fun `双语_双语档_原样`() {
        val t = "English\n中文"
        assertEquals(t, filterByMode(t, SubtitleMode.BILINGUAL))
    }

    @Test
    fun `单行_任何档位都原样`() {
        assertEquals("English", filterByMode("English", SubtitleMode.CN_ONLY))
        assertEquals("中文", filterByMode("中文", SubtitleMode.EN_ONLY))
    }

    @Test
    fun `纯英文多行_选只看中文_必须原样_绝不能变空`() {
        // 🔴 这是「单语字幕任何档位都原样」那条提前返回的意义：
        //   纯英文字幕（如 NCE 复读素材）用户误切到「只看中文」，不能把整条字幕清空。
        val t = "line one\nline two"
        assertEquals(t, filterByMode(t, SubtitleMode.CN_ONLY))
    }

    @Test
    fun `纯中文多行_选只看英文_原样`() {
        val t = "中文一\n中文二"
        assertEquals(t, filterByMode(t, SubtitleMode.EN_ONLY))
    }

    @Test
    fun `三行_两英一中_只看英文_两行都留`() {
        assertEquals("E1\nE2", filterByMode("E1\n中\nE2", SubtitleMode.EN_ONLY))
    }

    @Test
    fun `空字符串_任何档位都原样`() {
        assertEquals("", filterByMode("", SubtitleMode.EN_ONLY))
    }

    @Test
    fun `ASR中英混杂_会被裁得只剩语气词_这就是豁免必须存在的理由`() {
        // 🔴🔴 这条断言是 v131 那道 `if (subtitlesFromAsr) 原样` 豁免的**存在证明**：
        //   ASR 出来的「正文英文 + 语气词中文」在 filterByMode 眼里**长得就像双语字幕**，
        //   于是选「只看中文」会把英文正文整行删掉、只剩一个「嗯」。
        //   ⇒ 所以 VideoPage.displayTextOf / ReadingPage 必须先判 subtitlesFromAsr 再决定裁不裁。
        //   ⚠️ 如果哪天有人觉得「这豁免多余」想删掉 —— 跑这条测试，看看后果是什么。
        val asrMixed = "Hi guys, welcome back\n嗯"
        assertEquals("嗯", filterByMode(asrMixed, SubtitleMode.CN_ONLY))
        // 反过来：只看英文会丢掉「嗯」，也是同样的问题（ASR 那份一律不裁）
        assertEquals("Hi guys, welcome back", filterByMode(asrMixed, SubtitleMode.EN_ONLY))
    }

    // ==================================================================
    // ③ isBilingual —— 决定「长按要不要调翻译」
    // ==================================================================

    @Test
    fun `中英各一行_是双语`() {
        assertTrue(isBilingual("English\n中文"))
    }

    @Test
    fun `纯英文两行_不是双语`() {
        assertFalse(isBilingual("line one\nline two"))
    }

    @Test
    fun `纯中文两行_不是双语`() {
        assertFalse(isBilingual("中文一\n中文二"))
    }

    @Test
    fun `单行_永远不是双语_哪怕中英混排`() {
        // 🔴 关键边界：ASR 常见「一行里中英夹杂」，那不是「中英对照的双语字幕」。
        assertFalse(isBilingual("Hi guys 嗯"))
    }

    @Test
    fun `空_不是双语`() {
        assertFalse(isBilingual(""))
    }

    // ==================================================================
    // ④ englishOnly —— 整句加生词本时只取英文
    // ==================================================================

    @Test
    fun `双语_只取英文`() {
        assertEquals("E1", englishOnly("E1\n中文"))
    }

    @Test
    fun `双语_多个英文行_用空格连成一句`() {
        // ⚠️ 注意这里是**空格**连接（不是换行）：加进生词本的是一整句英文。
        assertEquals("E1 E2", englishOnly("E1\n中文\nE2"))
    }

    @Test
    fun `单行_原样`() {
        assertEquals("English", englishOnly("English"))
    }

    @Test
    fun `纯中文多行_原样_不能返回空`() {
        val t = "中文一\n中文二"
        assertEquals(t, englishOnly(t))
    }

    // ==================================================================
    // ⑤ encodeSentences / decodeSentences —— 存取往返（双语最容易死在这儿）
    // ==================================================================

    @Test
    fun `往返_双语换行必须保住_不能塌成一行`() {
        // 🔴 1008 修过的坑：以前存的时候把 "\n" 直接压成空格 ⇒ 双语字幕当天正常、
        //   第二天打开变成「English 中文」一行 —— 用户以为字幕坏了。
        val src = listOf(Sentence(0, 1000, "English\n中文"))
        val decoded = decodeSentences(encodeSentences(src))
        assertEquals(1, decoded.size)
        assertEquals("English\n中文", decoded[0].text)
        assertEquals(0L, decoded[0].startMs)
        assertEquals(1000L, decoded[0].endMs)
    }

    @Test
    fun `往返_制表符与反斜杠都保得住`() {
        // 制表符是字段分隔符、反斜杠是转义引导符 —— 两个都是「存进去会破坏格式」的角色。
        val text = "a\tb\\c"
        val decoded = decodeSentences(encodeSentences(listOf(Sentence(0, 1, text))))
        assertEquals(text, decoded[0].text)
    }

    @Test
    fun `往返_字面反斜杠加n_不该被读成换行`() {
        // 文本里真的写着「反斜杠 + n」两个字符（比如 C:\new）。存的时候反斜杠会先转义，
        // 读回来必须还原成两个字符，而不是变成一个换行。
        val text = "C:\\new"
        val decoded = decodeSentences(encodeSentences(listOf(Sentence(0, 1, text))))
        assertEquals(text, decoded[0].text)
    }

    @Test
    fun `往返_多条句子顺序不变`() {
        val src = listOf(
            Sentence(0, 500, "one"),
            Sentence(600, 1200, "two\n二"),
            Sentence(1300, 2000, "three"),
        )
        val decoded = decodeSentences(encodeSentences(src))
        assertEquals(src, decoded)
    }

    @Test
    fun `decode_空串或纯空白_返回空表`() {
        assertTrue(decodeSentences("").isEmpty())
        assertTrue(decodeSentences("   \n  ").isEmpty())
    }

    @Test
    fun `decode_字段数不对的行_跳过且不崩`() {
        // 老数据 / 被手动改坏的数据里可能有残行 —— 跳过它，别让整个字幕都读不出来。
        val good = encodeSentences(listOf(Sentence(0, 500, "good")))
        val decoded = decodeSentences("只有一段没有制表符\n$good")
        assertEquals(1, decoded.size)
        assertEquals("good", decoded[0].text)
    }

    @Test
    fun `decode_时间戳不是数字_跳过该行且不崩`() {
        // p[0].toLong() 会抛 ⇒ runCatching 吞掉这一行（历史脏数据容错）。
        assertTrue(decodeSentences("abc\t500\tbad").isEmpty())
    }

    @Test
    fun `decode_默认会重排_传false则不重排`() {
        // 这一对断言锁死 v131 的核心约定：
        //   抓来的字幕（默认 true）⇒ 英文提上；我们自己 ASR 生成的（false）⇒ 一个字都不许动。
        val line = "100\t200\t中文\\nEnglish"
        assertEquals("English\n中文", decodeSentences(line)[0].text)
        assertEquals("中文\nEnglish", decodeSentences(line, reorderBilingual = false)[0].text)
    }

    // ==================================================================
    // ⑥ mergeShortAsrSentences —— ASR 碎句合并
    // ==================================================================

    @Test
    fun `过短的句子_与下一句合并`() {
        val r = mergeShortAsrSentences(
            listOf(
                Sentence(0, 500, "Hi"),
                Sentence(600, 2000, "guys"),
            )
        )
        assertEquals(1, r.size)
        assertEquals("Hi guys", r[0].text)
        assertEquals(0L, r[0].startMs)
        assertEquals(2000L, r[0].endMs)
    }

    @Test
    fun `短句与下一句间隔超过5秒_不许硬并`() {
        // 🔴 分片识别时中间某段失败被跳过的话，前后两句会隔着整个失败段（可能十几分钟）。
        //   硬并起来就是「点一下要播十分钟」的巨无霸句子 —— gapLimit 就是为这个加的。
        val r = mergeShortAsrSentences(
            listOf(
                Sentence(0, 500, "Hi"),
                Sentence(100_000, 101_000, "guys"),
            )
        )
        assertEquals(2, r.size)
    }

    @Test
    fun `只有一个短句_没有可并的对象_保持原样`() {
        val r = mergeShortAsrSentences(listOf(Sentence(0, 500, "Hi")))
        assertEquals(1, r.size)
        assertEquals("Hi", r[0].text)
    }

    @Test
    fun `空表_返回空表`() {
        assertTrue(mergeShortAsrSentences(emptyList()).isEmpty())
    }

    @Test
    fun `短句有前一句时_优先并下一个`() {
        // 逻辑对齐 PlayerService.mergeShortSentences：优先并**下一个**，没有再并上一个。
        val r = mergeShortAsrSentences(
            listOf(
                Sentence(0, 3000, "long enough"),
                Sentence(3100, 3500, "short"),
                Sentence(3600, 5000, "next"),
            )
        )
        assertEquals(2, r.size)
        assertEquals("long enough", r[0].text)
        assertEquals("short next", r[1].text)
    }

    // ==================================================================
    // ⑦ hasHan —— 判「中文行」的唯一判据（档位/行序/点词选词全靠它）
    // ==================================================================

    @Test
    fun `汉字_判定`() {
        assertTrue(hasHan("中文"))
        assertTrue(hasHan("Hi 嗯"))
        assertFalse(hasHan("English"))
        assertFalse(hasHan(""))
    }

    @Test
    fun `只有全角标点或日文假名_不算中文行_记录现有行为`() {
        // ⚠️ 现有实现只认 CJK 统一汉字区 `\u4e00-\u9fff`：
        //   · 全角标点（，。！）在 U+FF00 区 —— **不算**中文行
        //   · 日文平假名/片假名（こんにちは）在 U+3040/U+30A0 区 —— **也不算**中文行
        //   ⇒ 后果：一份「日文行 + 英文行」的字幕会被当成「纯英文」，
        //     档位裁剪/行序归一对它不会起效（会整体当英文处理）。
        //   这是**现有行为**，此断言只是把它钉住；要不要支持日文是产品决定，不是 bug 修复。
        assertFalse(hasHan("，。！？"))
        assertFalse(hasHan("こんにちは"))
    }
}
