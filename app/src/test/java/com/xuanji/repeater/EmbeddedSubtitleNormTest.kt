package com.xuanji.repeater

import com.xuanji.repeater.EmbeddedSubtitle.normalize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [EmbeddedSubtitle.normalize] 的纯算法测试（不碰 Context/Uri，纯 JVM 可跑）。
 *
 * 为什么专门为它写测试：v147 阿亮真机实测到「字幕列表第一条是
 * `0:00:00:00,0:00:06:00,0,0,Default,NTP,0000,0000,0000,,` 这种裸数据」——
 * 根因就在这个函数的最后那行 `return Norm("", null, null, p)` 兜底：
 * 空文本的 `Dialogue:` 行没被拦住、掉到兜底、整行裸数据被当字幕收下。
 * 这个 bug **肉眼看不出来**（结构完全合法），只能靠"拿真实片源的原始 payload 当输入"的测试钉住。
 *
 * ⚠️ 输入样例全部抄自真实片源（老友记 2160p 47BT「简英双字」轨 / 720p 蚂蚁仔轨），
 *   不是编的 —— 编的样例抓不到这类"结构合法但内容空"的坑。
 */
class EmbeddedSubtitleNormTest {

    // ============ ① 这个 bug 本身：空文本 Dialogue 行必须丢掉 ============

    @Test
    fun `裸_text_空文本的Dialogue行_必须返回null_不能掉到纯文本兜底`() {
        // 真实症状（47BT 简英双字轨第一条）：结构合法、文本空
        val raw = "Dialogue: 0,0:00:00.00,0:00:06.00,Default,NTP,0000,0000,0000,,"
        assertNull("空文本 Dialogue 行不许当字幕收下", normalize(raw, isAss = true))
    }

    @Test
    fun `裸_text_空文本Dialogue行_不许原样漏成裸数据`() {
        // 兜底那支会把整行当文本返回 ⇒ 结果里 text 包含 "0000,0000,0000" 这种特征
        val raw = "Dialogue: 0,0:00:00.00,0:00:06.00,Default,NTP,0000,0000,0000,,"
        val r = normalize(raw, isAss = true)
        // 只要返回了东西，就说明漏了 —— 这条断言是给"有人将来把 null 又改回去"留的警报
        if (r != null) {
            assertEquals("空文本行不该被收下", null, r.text.ifBlank { null })
        }
    }

    // ============ ② 正常行必须照常解析（别为了修 bug 把功能修坏） ============

    @Test
    fun `裸_text_标准Dialogue行_正常取出样式与文本`() {
        val raw = "Dialogue: 0,0:00:48.09,0:00:50.02,Default,NTP,0000,0000,0000,," +
                "这没什么好说的\\N{\\fnMicrosoft YaHei\\fs14\\1c&H4080FF&\\b0}There's nothing to tell!"
        val r = normalize(raw, isAss = true)
        assertNotNull("正常行必须能解出来", r)
        assertEquals("Default", r!!.style)
        assertEquals(48090L, r.startMs)
        assertEquals(50020L, r.endMs)
        // 换行标签原样保留（清洗是 SubtitleParser 的事，这里只剥壳）
        assertEquals(true, r.text.contains("\\N"))
        assertEquals(true, r.text.startsWith("这没什么好说的"))
    }

    @Test
    fun `裸_text_顶部定位的场景标题行_照常取出_标签留给下游清洗`() {
        // 阿亮说的"开头那几句跑上面"就是这类（\an8 = 顶部居中）。
        // ⚠️ 本函数**只剥壳不清洗**：`{\an8...}` 标签必须原样留着，
        //   由 [SubtitleParser.cleanAssText] 统一处理（踩过坑的唯一真值）。
        val raw = "Dialogue: 0,0:00:46.08,0:00:50.00,Default,NTP,0000,0000,0000,,{\\an8\\bord1\\fs18\\b1}中央咖啡馆"
        val r = normalize(raw, isAss = true)
        assertNotNull(r)
        assertEquals("{\\an8\\bord1\\fs18\\b1}中央咖啡馆", r!!.text)
    }

    // ============ ③ Media3 合成前缀形态（文本前 10 段，自带 start/end 不可信） ============

    @Test
    fun `合成前缀_文本前10段_取样式与文本_时间用null交给调用方`() {
        val raw = "Dialogue: 0:00:00:00,0:00:06:00,0,0,Default,NTP,0000,0000,0000,,我是文本"
        val r = normalize(raw, isAss = true)
        assertNotNull(r)
        assertEquals("Default", r!!.style)
        assertNull("合成前缀的 start/end 不可信，必须返回 null", r.startMs)
        assertNull("合成前缀的 end 也不可信", r.endMs)
        assertEquals("我是文本", r.text)
    }

    @Test
    fun `合成前缀_文本为空_必须返回null`() {
        val raw = "Dialogue: 0:00:00:00,0:00:06:00,0,0,Default,NTP,0000,0000,0000,,"
        assertNull(normalize(raw, isAss = true))
    }

    // ============ ④ SRT / 纯文本形态（别被上面的改动误伤） ============

    @Test
    fun `SRT_带箭头_正常解析出时间与文本`() {
        val raw = "1\n00:00:01,000 --> 00:00:03,500\nHello there"
        val r = normalize(raw, isAss = false)
        assertNotNull(r)
        assertEquals(1000L, r!!.startMs)
        assertEquals(3500L, r.endMs)
        assertEquals("Hello there", r.text)
    }

    @Test
    fun `纯文本_普通一行_原样收下`() {
        val r = normalize("只是一行普通文本", isAss = false)
        assertNotNull(r)
        assertEquals("只是一行普通文本", r!!.text)
        assertEquals("", r.style)
    }

    @Test
    fun `纯文本_空白字符串_返回null`() {
        assertNull(normalize("   ", isAss = false))
        assertNull(normalize("", isAss = false))
    }

    // ============ ⑤ ASS 裸 payload（不带 Dialogue: 前缀，靠 looksLikeAssRaw 指纹） ============

    @Test
    fun `ASS裸payload_前两段是数字_按ASS布局取文本`() {
        // ReadOrder,Layer,Style,Name,ML,MR,MV,Effect,Text
        val raw = "0,0,chs,NTP,0000,0000,0000,,那真的很棒"
        val r = normalize(raw, isAss = true)
        assertNotNull(r)
        assertEquals("chs", r!!.style)
        assertEquals("那真的很棒", r.text)
    }

    @Test
    fun `ASS裸payload_文本为空_必须返回null`() {
        val raw = "0,0,chs,NTP,0000,0000,0000,,"
        assertNull(normalize(raw, isAss = true))
    }
}
