package com.xuanji.repeater

import com.xuanji.repeater.SentenceSplitter.rawSegmentsFromBoundaries
import com.xuanji.repeater.SentenceSplitter.sentencesFromBoundaries
import com.xuanji.repeater.SentenceSplitter.silenceRuns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * `SentenceSplitter` **纯算法部分**的单元测试 —— 跑在电脑上，不需要手机、不需要音频文件。
 *
 * ## 这个文件解决什么问题
 *
 * 断句规则（静音门槛 / 最短句长 / 最长句长 / 边界吸附）原来和解码流程（`MediaExtractor` +
 * `MediaCodec`）缠在同一个 `split()` 里 ⇒ **只能装机听一遍才能验证**，
 * 改一次靠猜、错一次重来。而这一层恰恰是"改一次错一次"的重灾区。
 *
 * 它的入参其实只有 `FloatArray + Long`，一个 Android API 都不碰 ⇒ 2026-10-10 把这部分
 * 抽成了 [SentenceSplitter.silenceRuns] / [SentenceSplitter.sentencesFromBoundaries] /
 * [SentenceSplitter.rawSegmentsFromBoundaries] 三个纯函数，于是就能在这里跑断言了。
 *
 * ## 本文件最重要的一组：等价性验证（不是"能测"就行，得证明"搬运没走样"）
 *
 * 抽取这种事，最大的风险不是"抽错了编译不过"，而是**搬运时手滑把某个 `>=` 改成了 `>`**
 * ——编译照过、测试照绿（因为测试是我照新代码写的），但线上行为已经变了，只有真机上听得出来。
 *
 * ⇒ 所以在下面把**抽取前的原文逻辑逐字抄了一份**（`ref*` 系列，从 git 历史来的），
 *   再用**同一批输入**跑新旧两边、断言结果**完全相同**。
 *   这才是"纯搬运"的机器证据，而不是我嘴上说一句"我没改"。
 *
 * 顺带还证明了一件事：原来 [SentenceSplitter.silenceRuns] 那段逻辑在 `split()` 和
 * `silenceValleysWithLen()` 里**各写了一遍**（两份拷贝）⇒ `refRunsFromSplit` 与
 * `refRunsFromValleys` 必须互相一致，否则说明"两份拷贝本来就分岔了"。
 *
 * ## 能保证什么 / 不能保证什么
 *
 * ✅ 能保证：从 RMS 数组往下的一切（静音判定、边界时间、句子长度约束、原始段切分）行为被钉住；
 *    谁改坏判定，这里立刻红。
 * ❌ **不能保证解码正确** —— `decodeRmsFrames` 那段（`MediaCodec`）测不了，仍然只能真机验证。
 *    换句话说：**这个文件让"解码之后"可测，"解码"本身还是黑的**。
 *
 * ⚠️ 搬运时**判定一个字没改**；尤其 `MIN_SILENCE_MS = 350ms`（慢速教学音频基线）**别动**。
 */
class SentenceSplitterTest {

    // ==================================================================
    // 参照实现 —— 抽取前的原文逻辑
    //
    // 🔴 下面 4 个 `ref*` 函数是**逐字抄来的原文**（抽取前的 `split()` / `silenceValleysWithLen()`），
    //    只当"标准答案"用。**不要**顺手把它们改成"看起来更对"的写法 ——
    //    它们的全部价值就在于"和抽取前的线上行为一模一样"。
    // ⚠️ 原文里的私有常量抄不了（`private const`），这里按原值写死：
    //    静音比例 0.075、FRAME_MS 50。改这些值等于换了一套标准答案 ⇒ 别改。
    // ==================================================================

    /** 抽取前 `split()` 里那段"收集静音段中心点"的原文（注意这里比的是 **Double**）。 */
    private fun refRunsFromSplit(rms: FloatArray, totalMs: Long, minSilenceMs: Long): List<Long> {
        val peak = rms.max()
        if (peak <= 0f) return emptyList()
        val frameMsReal = if (rms.size > 0) (totalMs.toDouble() / rms.size).coerceIn(20.0, 60.0) else 50.0
        val threshold = peak * 0.075f
        val silent = BooleanArray(rms.size) { rms[it] < threshold }
        val boundaries = mutableListOf<Long>()
        var i = 0
        val n = rms.size
        while (i < n) {
            if (silent[i]) {
                var j = i
                while (j < n && silent[j]) j++
                if ((j - i) * frameMsReal >= minSilenceMs) {
                    boundaries.add(((i + j) / 2 * frameMsReal).toLong())
                }
                i = j
            } else {
                i++
            }
        }
        return boundaries
    }

    /** 抽取前 `silenceValleysWithLen()` 里那段**逐行一样**的拷贝（注意这里先 `toLong()` 再比）。 */
    private fun refRunsFromValleys(rms: FloatArray, totalMs: Long, minSilenceMs: Long): List<Pair<Long, Long>> {
        val peak = rms.max()
        if (peak <= 0f) return emptyList()
        val frameMsReal = (totalMs.toDouble() / rms.size).coerceIn(20.0, 60.0)
        val threshold = peak * 0.075f
        val silent = BooleanArray(rms.size) { rms[it] < threshold }
        val out = mutableListOf<Pair<Long, Long>>()
        var i = 0
        val n = rms.size
        while (i < n) {
            if (silent[i]) {
                var j = i
                while (j < n && silent[j]) j++
                val lenMs = ((j - i) * frameMsReal).toLong()
                if (lenMs >= minSilenceMs) {
                    out.add(Pair(((i + j) / 2 * frameMsReal).toLong(), lenMs))
                }
                i = j
            } else {
                i++
            }
        }
        return out
    }

    /**
     * 抽取前 `split()` 里"由边界生成句子（长度约束）"的原文。
     *
     * ⚠️【2026-10-10 起】**它不再对新实现整体成立**：这段原逻辑在「段长 ≥ 16500ms」时会整块漏掉
     *   15000ms（见 `句子_超长段_每块都有人认领_不许漏`），修复后新实现**有意**不再与它一致。
     *   ⇒ 本函数现在只当「**不触发超长强制切分**的输入」的标准答案：用来证明修复没有误伤普通判定。
     *   🔴 别为了让对比测试变绿去改这个函数 —— 它存在的全部意义就是"和修复前的线上行为一模一样"。
     */
    private fun refSentences(boundaries: List<Long>, totalMs: Long): List<Sentence> {
        val sentences = mutableListOf<Sentence>()
        var segStart = 0L
        for (b in boundaries) {
            val len = b - segStart
            if (len >= 1500L && len <= 15000L) {
                sentences.add(Sentence(segStart, b))
                segStart = b
            } else if (len > 15000L) {
                var cut = segStart + 15000L
                while (b - cut > 15000L) {
                    sentences.add(Sentence(cut - 15000L, cut))
                    cut += 15000L
                }
                if (b - cut >= 1500L) {
                    sentences.add(Sentence(cut, b))
                    segStart = b
                }
            }
        }
        if (totalMs - segStart >= 1500L) {
            sentences.add(Sentence(segStart, totalMs))
        } else if (sentences.isNotEmpty()) {
            val last = sentences.removeAt(sentences.size - 1)
            sentences.add(Sentence(last.startMs, totalMs))
        } else {
            sentences.add(Sentence(0L, totalMs))
        }
        return sentences
    }

    /** 抽取前 `split()` 里"原始边界段"的原文。 */
    private fun refRawSegments(boundaries: List<Long>, totalMs: Long): List<Sentence> {
        val rawSegments = mutableListOf<Sentence>()
        var rs = 0L
        for (b in boundaries) {
            while (b - rs > 15000L) {
                rawSegments.add(Sentence(rs, rs + 15000L))
                rs += 15000L
            }
            if (b - rs >= 200L) {
                rawSegments.add(Sentence(rs, b))
                rs = b
            }
        }
        if (totalMs > rs) {
            if (totalMs - rs < 200L && rawSegments.isNotEmpty()) {
                rawSegments[rawSegments.lastIndex] = Sentence(rawSegments.last().startMs, totalMs)
            } else {
                rawSegments.add(Sentence(rs, totalMs))
            }
        }
        if (rawSegments.isEmpty()) rawSegments.add(Sentence(0L, totalMs))
        return rawSegments
    }

    // ==================================================================
    // 输入构造小工具
    // ==================================================================

    /** 按 `(能量值, 帧数)` 拼 RMS 数组。阈值 = 峰值的 7.5% ⇒ 想造静音就给个远低于峰值的小值。 */
    private fun rmsOf(vararg parts: Pair<Float, Int>): FloatArray {
        val out = ArrayList<Float>(parts.sumOf { it.second })
        for ((v, n) in parts) repeat(n) { out.add(v) }
        return out.toFloatArray()
    }

    /** 让"真实帧长"恰好等于 [frameMs] 所需的总时长（帧长 = 总时长 ÷ 帧数，被夹在 20~60ms）。 */
    private fun msOf(frames: Int, frameMs: Double): Long = (frames * frameMs).toLong()

    /** 造一段"忽有声忽静音"的能量序列：块长 1~39 帧，块间交替。 */
    private fun randomRms(rnd: Random, frames: Int): FloatArray {
        val out = FloatArray(frames)
        var i = 0
        var silent = rnd.nextBoolean()
        while (i < frames) {
            val blockLen = rnd.nextInt(1, 40)
            val v = if (silent) rnd.nextFloat() * 0.01f else 0.3f + rnd.nextFloat() * 0.7f
            for (k in i until minOf(i + blockLen, frames)) out[k] = v
            i += blockLen
            silent = !silent
        }
        return out
    }

    /** 造一串递增的边界（真实边界也是递增的：静音段的中心点按时间先后排）。 */
    private fun randomBoundaries(rnd: Random, totalMs: Long): List<Long> {
        val n = rnd.nextInt(0, 8)
        val out = ArrayList<Long>(n)
        var cur = 0L
        repeat(n) {
            cur += rnd.nextInt(1, 25000)
            if (cur > totalMs) cur = totalMs
            out.add(cur)
        }
        return out
    }

    /**
     * 这批边界会不会走到「超长强制切分」那条分支（某段 > 最长句长 15000）？
     *
     * 用途：那条分支是 2026-10-10 的**修复点**，新旧实现**有意不同** ⇒ 等价性对比要把它排除，
     * 否则"证明没误伤"的测试会一直红。它自己的行为由 `句子_超长段_*` 三条测试单独盯着。
     */
    private fun triggersForcedSplit(boundaries: List<Long>): Boolean {
        var segStart = 0L
        for (b in boundaries) {
            val len = b - segStart
            when {
                len >= 1500L && len <= 15000L -> segStart = b
                len > 15000L -> return true
            }
        }
        return false
    }

    // ==================================================================
    // ① 等价性验证 —— 证明"抽取 = 纯搬家"，一行判定都没走样
    // ==================================================================

    @Test
    fun `等价_静音段_新实现与抽取前split版完全一致`() {
        val rms = rmsOf(1.0f to 1, 0f to 99)
        val total = msOf(100, 50.0)
        assertEquals(refRunsFromSplit(rms, total, 350L), silenceRuns(rms, total, 350L).map { it.first })
    }

    /**
     * 🔴 **本文件最关键的一条**：证明"原来那两份拷贝本来就是一致的"。
     *
     * 两份拷贝对同一个静音段用的判据写法不同 —— 一份是 `(帧数 × 帧长) >= 350`（**Double 比较**），
     * 另一份是先 `toLong()` 截断再比。两者数学上等价（`x>=350.0` ⇔ `floor(x)>=350`），
     * 我把它们合并成了一份。**这条测试就是"合并安全"的证据**：
     * 只要将来谁把合并后的判据改错，两份参照实现就会对不上，这里立刻红。
     */
    @Test
    fun `等价_静音段_原来是两份逐行一样的拷贝_合并是安全的`() {
        val rnd = Random(20261010)
        repeat(400) { case ->
            val frames = rnd.nextInt(1, 300)
            val rms = randomRms(rnd, frames)
            val total = (frames * rnd.nextInt(20, 61)).toLong()
            val minSil = listOf(180L, 350L)[rnd.nextInt(2)]
            assertEquals(
                "第 $case 组：两份拷贝结果不一致（只可能是判据被我改坏了）",
                refRunsFromSplit(rms, total, minSil),
                refRunsFromValleys(rms, total, minSil).map { it.first },
            )
        }
    }

    @Test
    fun `等价_静音段_新实现与抽取前silenceValleys版完全一致`() {
        val rms = rmsOf(1.0f to 1, 0f to 99)
        val total = msOf(100, 50.0)
        assertEquals(refRunsFromValleys(rms, total, 350L), silenceRuns(rms, total, 350L))
    }

    @Test
    fun `等价_句子生成_不触发超长切分时_与抽取前split版完全一致`() {
        val rnd = Random(777)
        val cases = listOf(
            emptyList<Long>() to 60000L,
            listOf(10000L) to 20000L,
            listOf(1000L, 12000L) to 20000L,
            listOf(8000L, 17000L, 26000L, 35000L) to 40000L,
            listOf(0L) to 0L,
            listOf(15000L) to 16500L,
            listOf(15000L) to 16501L,
            // 🔴 端点值：段长正好等于最短/最长句长。变异验证发现"只差一个等号"的改动
            //   就是靠这几种输入才抓得到（1000/12000 这种不卡端点的长度抓不到）。
            listOf(1500L) to 3000L,
            listOf(1500L) to 1500L,
            listOf(15000L) to 30000L,
            // ⚠️ 原来这里还有 `45000L to 60000L`、`16500L to 18000L` 两组 —— 它们会走到
            //    2026-10-10 的**修复点**（超长强制切分），新旧实现**有意不同** ⇒ 已移到
            //    `句子_超长段_*` 那两条测试，标准答案换成**期望行为**而不是历史实现。
        )
        for ((bs, total) in cases) {
            assertTrue("这个用例本身就不该触发超长切分：$bs", !triggersForcedSplit(bs))
            assertEquals("边界 $bs / 总长 $total", refSentences(bs, total), sentencesFromBoundaries(bs, total))
        }
        repeat(300) {
            val total = rnd.nextLong(0, 90000)
            val bs = randomBoundaries(rnd, total)
            if (triggersForcedSplit(bs)) return@repeat   // 修复点，另有三条测试专门盯
            assertEquals("随机边界 $bs / 总长 $total", refSentences(bs, total), sentencesFromBoundaries(bs, total))
        }
    }

    @Test
    fun `等价_原始段生成_新实现与抽取前split版完全一致`() {
        val rnd = Random(778)
        val cases = listOf(
            emptyList<Long>() to 0L,
            listOf(1000L) to 1100L,
            listOf(100L) to 2000L,
            listOf(40000L) to 50000L,
            listOf(500L, 800L, 3000L) to 3200L,
        )
        for ((bs, total) in cases) {
            assertEquals("边界 $bs / 总长 $total", refRawSegments(bs, total), rawSegmentsFromBoundaries(bs, total))
        }
        repeat(300) {
            val total = rnd.nextLong(0, 90000)
            val bs = randomBoundaries(rnd, total)
            assertEquals("随机边界 $bs / 总长 $total", refRawSegments(bs, total), rawSegmentsFromBoundaries(bs, total))
        }
    }

    @Test
    fun `等价_端到端随机400组_RMS进句子出_三类输出全一致`() {
        val rnd = Random(10102026)
        repeat(400) { case ->
            val frames = rnd.nextInt(1, 500)
            val rms = randomRms(rnd, frames)
            val total = (frames * rnd.nextInt(20, 61)).toLong()
            val minSil = listOf(180L, 350L)[rnd.nextInt(2)]

            val newRuns = silenceRuns(rms, total, minSil)
            assertEquals(
                "第 $case 组 silenceRuns(中心)",
                refRunsFromSplit(rms, total, minSil),
                newRuns.map { it.first },
            )
            assertEquals("第 $case 组 silenceRuns(含时长)", refRunsFromValleys(rms, total, minSil), newRuns)

            val bs = newRuns.map { it.first }
            assertEquals("第 $case 组 sentences", refSentences(bs, total), sentencesFromBoundaries(bs, total))
            assertEquals("第 $case 组 rawSegments", refRawSegments(bs, total), rawSegmentsFromBoundaries(bs, total))
        }
    }

    // ==================================================================
    // ② silenceRuns —— 静音判定与边界时间
    // ==================================================================

    @Test
    fun `静音_一条长静音_取段中心点`() {
        // 1 帧有声 + 99 帧静音，帧长 50ms ⇒ 静音段 = 第 1~99 帧
        // 中心 = (1+100)/2 × 50 = 2500ms，时长 = 99 × 50 = 4950ms
        val rms = rmsOf(1.0f to 1, 0f to 99)
        assertEquals(
            listOf(2500L to 4950L),
            silenceRuns(rms, msOf(100, 50.0), 350L),
        )
    }

    @Test
    fun `静音_全是有声_没有静音段`() {
        val rms = rmsOf(1.0f to 50)
        assertTrue(silenceRuns(rms, msOf(50, 50.0), 350L).isEmpty())
    }

    @Test
    fun `静音_整段能量为零_返回空表不崩`() {
        // 峰值 = 0 ⇒ 阈值也是 0 ⇒ "小于阈值"永远不成立，直接返回空（不是除零、不是崩）
        assertTrue(silenceRuns(rmsOf(0f to 30), 1500L, 350L).isEmpty())
    }

    @Test
    fun `静音_空数组_返回空表`() {
        assertTrue(silenceRuns(FloatArray(0), 0L, 350L).isEmpty())
    }

    @Test
    fun `静音_阈值是峰值的比例_整体音量很小也照样能断`() {
        // 🔴 阈值是**相对**的（峰值 × 7.5%），不是某个固定绝对值。
        //   录音音量偏小的素材（峰值只有 0.01）若按绝对值判就全是"静音"、完全断不开。
        val rms = rmsOf(0.01f to 1, 0f to 19)
        assertEquals(listOf(500L to 950L), silenceRuns(rms, msOf(20, 50.0), 350L))
    }

    @Test
    fun `静音_不够长的静音段不算边界`() {
        // 6 帧 × 50ms = 300ms < 350ms 门槛 ⇒ 不算；后面 20 帧 = 1000ms ⇒ 算
        val rms = rmsOf(1.0f to 1, 0f to 6, 1.0f to 1, 0f to 20)
        val total = msOf(28, 50.0)
        assertEquals(listOf(900L to 1000L), silenceRuns(rms, total, 350L))
    }

    @Test
    fun `静音_帧长按总时长校准_不是写死50ms`() {
        // 🔴 双声道音频帧数会翻倍 ⇒ 帧长要用"总时长 ÷ 帧数"校准，写死 50ms 会让边界时间整体错一倍。
        //   同样 100 帧、99 帧静音：总时长 2500ms ⇒ 帧长 25ms ⇒ 边界 1250ms（若写死 50ms 会得 2500ms）
        val rms = rmsOf(1.0f to 1, 0f to 99)
        assertEquals(listOf(1250L to 2475L), silenceRuns(rms, msOf(100, 25.0), 350L))
    }

    @Test
    fun `静音_校准出的帧长会被夹在20到60毫秒之间`() {
        // 总时长 ÷ 帧数 = 100ms ⇒ 夹到上限 60ms ⇒ 边界 = 50 × 60 = 3000ms
        val rms = rmsOf(1.0f to 1, 0f to 99)
        assertEquals(listOf(3000L to 5940L), silenceRuns(rms, msOf(100, 100.0), 350L))
        // 5ms ⇒ 夹到下限 20ms ⇒ 边界 = 50 × 20 = 1000ms
        assertEquals(listOf(1000L to 1980L), silenceRuns(rms, msOf(100, 5.0), 350L))
    }

    @Test
    fun `静音_门槛可调_350漏掉的短停顿180能抓到`() {
        // 🔴 这条钉住"视频生成字幕传 180ms"的理由：快语速素材句间停顿常只有 150~300ms，
        //   用慢速教学基线的 350ms 会把它们全漏掉 ⇒ 切点找不到 ⇒ 大段切不动。
        val rms = rmsOf(1.0f to 1, 0f to 6, 1.0f to 1, 0f to 20)
        val total = msOf(28, 50.0)
        assertTrue(silenceRuns(rms, total, 350L).none { it.second == 300L })
        assertTrue(silenceRuns(rms, total, 180L).any { it.second == 300L })
    }

    @Test
    fun `静音_静音段正好350ms_要算一个边界`() {
        // 🔴 端点值：350ms 门槛是**闭区间** ⇒ 正好 350ms 要**算**（帧长 50ms × 7 帧 = 正好 350ms）。
        //   把 `>=` 写成 `>` 就会漏掉这一档 —— 这种"只差一个等号"的改动普通长度抓不到。
        val rms = rmsOf(1.0f to 1, 0f to 7, 1.0f to 1, 0f to 20)
        assertEquals(
            listOf(200L to 350L, 950L to 1000L),
            silenceRuns(rms, msOf(29, 50.0), 350L),
        )
    }

    @Test
    fun `静音_多段静音_每段一个边界且按时间递增`() {
        // 63 帧 × 50ms：有声在第 0 / 21 / 42 帧，静音是 1~20、22~41、43~62
        // 中心 = ((起点 + 终点) / 2) × 50 ⇒ 550 / 1600 / 2650
        val rms = rmsOf(1.0f to 1, 0f to 20, 1.0f to 1, 0f to 20, 1.0f to 1, 0f to 20)
        val total = msOf(63, 50.0)
        val out = silenceRuns(rms, total, 350L)
        assertEquals(3, out.size)
        assertEquals(listOf(550L, 1600L, 2650L), out.map { it.first })
        assertTrue(out.all { it.second == 1000L })
    }

    @Test
    fun `静音_尾部静音能走到最后一帧`() {
        // 扫描 j 必须能到 n（否则最后一段静音会被漏掉）
        val rms = rmsOf(1.0f to 1, 0f to 20)
        assertEquals(listOf(550L to 1000L), silenceRuns(rms, msOf(21, 50.0), 350L))
    }

    // ==================================================================
    // ③ sentencesFromBoundaries —— 长度约束
    // ==================================================================

    @Test
    fun `句子_没有任何边界_整段一句`() {
        assertEquals(listOf(Sentence(0, 60000)), sentencesFromBoundaries(emptyList(), 60000L))
    }

    @Test
    fun `句子_单边界_切成两句`() {
        assertEquals(
            listOf(Sentence(0, 10000), Sentence(10000, 20000)),
            sentencesFromBoundaries(listOf(10000L), 20000L),
        )
    }

    @Test
    fun `句子_边界太近被忽略_并入下一句_不产生碎句`() {
        // 🔴 1000ms < 最短句长 1500ms ⇒ 这个断点**不能要**（否则会切出一个 1 秒的碎句）。
        //   正确行为是忽略它，让 0~12000 合成一句 —— 这就是"最短句长"这道兜底的意义。
        assertEquals(
            listOf(Sentence(0, 12000), Sentence(12000, 20000)),
            sentencesFromBoundaries(listOf(1000L, 12000L), 20000L),
        )
    }

    /**
     * 🔴🔴 **2026-10-10 修复**：原实现的「超长强制切分」会**整块漏掉 15000ms**（段长 ≥ 16500ms 时）。
     *
     * | 段长 | 修复前切出来的 | 漏掉的 |
     * |---|---|---|
     * | 45000ms | `(0,15000)` `(30000,45000)` `(45000,60000)` | `(15000,30000)` |
     * | 75000ms | `(0,15000)` `(15000,30000)` `(30000,45000)` `(60000,75000)` | `(45000,60000)` |
     *
     * 根因：切块 `while` 每轮把 `cut` 推进 15s，退出后只补尾巴 `(cut, b)` ——
     * 于是**倒数第二块 `(cut-15s, cut)` 永远没人认领**，而 `segStart` 又被推到 `b` ⇒ 那 15 秒彻底消失。
     * 不是"余数不巧"的偶发，是**凡超 16.5 秒必漏**。
     *
     * 后果：漏掉的 15 秒在跟读列表里**点不到**、也听不到（`Sentence` 里根本没有对应项）。
     *
     * 修法：`segStart` 要推进到 `b` 之前，**先把最后一个整块 `(cut-15s, cut)` 补上**。
     *
     * 对照组：`原始段_超15秒强制切_完整覆盖无空洞` —— `rawSegments` 那条分支用的是**无条件推进**的
     * `while`，覆盖一直是完整的 ⇒ 证明这确实是 `sentences` 这条分支自己的毛病，不是"超长段只能这样"。
     */
    @Test
    fun `句子_超长段_每块都有人认领_不许漏`() {
        // 45000ms：三块 15s 一个不少
        assertEquals(
            listOf(Sentence(0, 15000), Sentence(15000, 30000), Sentence(30000, 45000), Sentence(45000, 60000)),
            sentencesFromBoundaries(listOf(45000L), 60000L),
        )
        // 75000ms：五块
        assertEquals(
            listOf(
                Sentence(0, 15000), Sentence(15000, 30000), Sentence(30000, 45000),
                Sentence(45000, 60000), Sentence(60000, 75000),
            ),
            sentencesFromBoundaries(listOf(75000L), 75000L),
        )
        // 🔴 端点值：正好 16500（= 最长句长 15000 + 最短句长 1500）—— 修复前这里会整个丢掉 (0,15000)
        assertEquals(
            listOf(Sentence(0, 15000), Sentence(15000, 16500)),
            sentencesFromBoundaries(listOf(16500L), 16500L),
        )
        // 20000：尾巴 5000 ≥ 最短句长 ⇒ 独立成句
        assertEquals(
            listOf(Sentence(0, 15000), Sentence(15000, 20000)),
            sentencesFromBoundaries(listOf(20000L), 20000L),
        )
        // 🔴 两个边界、第一段超长的情形（修复前同样会漏掉前 15 秒）
        assertEquals(
            listOf(Sentence(0, 15000), Sentence(15000, 20000), Sentence(20000, 30000)),
            sentencesFromBoundaries(listOf(20000L, 30000L), 30000L),
        )
    }

    /**
     * 尾巴不足最短句长（< 1500ms）时：**并进刚切出的整块**，不能把 `segStart` 留在原地。
     *
     * 修复前的行为：`while` 切出 `(0,15000)`，尾巴 1000 < 1500 ⇒ 不推进 `segStart` ⇒
     * 收尾时又补一句 `(0, 31000)` ⇒ 两句**整体重叠**（听感上开头 15 秒会被复读两遍）。
     */
    @Test
    fun `句子_超长段_尾巴不足最短句长时并进整块_不许与已切出的块重叠`() {
        // 31000 = 15000 整块 + 16000（又是 15000 整块 + 1000 尾巴）
        assertEquals(
            listOf(Sentence(0, 15000), Sentence(15000, 31000)),
            sentencesFromBoundaries(listOf(31000L), 31000L),
        )
        // 正好 16000 = 15000 + 1000 ⇒ 切不动，整段一句（不许留半截给下一句）
        assertEquals(
            listOf(Sentence(0, 16000)),
            sentencesFromBoundaries(listOf(16000L), 16000L),
        )
    }

    /**
     * **普适不变量**：不管边界怎么给，句子表都必须**从 0 开始、到总时长结束、逐句首尾相接、长度为正**。
     *
     * 这是比"和历史实现对比"更强的保护 —— 它不关心实现怎么写，只盯"覆盖必须完整"这一条。
     * 上面那个漏块 bug 就是被这条抓出来的（修复前 `first().startMs` 会是 15000 而不是 0）。
     */
    @Test
    fun `句子_任何边界输入_都完整覆盖且无缝无叠`() {
        val rnd = Random(10102026)
        val cases = mutableListOf(
            listOf(16500L) to 16500L,
            listOf(20000L) to 20000L,
            listOf(31000L) to 31000L,
            listOf(45000L) to 60000L,
            listOf(75000L) to 75000L,
            listOf(15000L) to 30000L,
            listOf(16000L) to 16000L,
            listOf(30000L) to 30000L,
            listOf(16000L, 20000L) to 40000L,
            listOf(16500L, 33000L) to 50000L,
            emptyList<Long>() to 60000L,
        )
        repeat(500) {
            val total = rnd.nextLong(1, 120000)
            cases.add(randomBoundaries(rnd, total) to total)
        }
        for ((bs, total) in cases) {
            val out = sentencesFromBoundaries(bs, total)
            val tag = "边界 $bs / 总长 $total"
            assertTrue("$tag：句子表不能为空", out.isNotEmpty())
            assertEquals("$tag：必须从 0 开始（漏块就是这里红）", 0L, out.first().startMs)
            assertEquals("$tag：必须覆盖到总时长", total, out.last().endMs)
            for (k in out.indices) {
                assertTrue("$tag：第 $k 句长度必须为正", out[k].endMs > out[k].startMs)
                if (k > 0) assertEquals("$tag：第 $k 句没接上上一句的尾巴", out[k - 1].endMs, out[k].startMs)
            }
        }
    }

    @Test
    fun `句子_段长正好等于最短句长_要成句不能被当碎句并掉`() {
        // 🔴 端点值：1500ms 是"最短句长"的**闭区间端点**，正好等于它时**必须成句**。
        //   把 `>=` 写成 `>` 就会把它当碎句并掉 —— 而用 1000/12000 这类不卡端点的长度根本抓不到
        //   （这是**变异验证实测漏掉过一次**才补上的用例，别删）。
        assertEquals(
            listOf(Sentence(0, 1500), Sentence(1500, 3000)),
            sentencesFromBoundaries(listOf(1500L), 3000L),
        )
    }

    @Test
    fun `句子_尾部不足最短句长_并进上一句末尾`() {
        // 末段只剩 1000ms < 1500ms ⇒ 不能独立成句，并进上一句（否则结尾留个 1 秒碎句）
        val out = sentencesFromBoundaries(listOf(10000L), 11000L)
        assertEquals(listOf(Sentence(0, 11000)), out)
    }

    @Test
    fun `句子_常规输入_首尾相接且不重叠`() {
        val total = 60000L
        val out = sentencesFromBoundaries(
            listOf(8000L, 17000L, 26000L, 35000L, 44000L, 53000L),
            total,
        )
        assertEquals(0L, out.first().startMs)
        assertEquals(total, out.last().endMs)
        for (k in 1 until out.size) {
            assertEquals("第 $k 句没有接上一句的尾巴", out[k - 1].endMs, out[k].startMs)
        }
    }

    @Test
    fun `句子_总时长比最短句长还短_也至少给一句`() {
        assertEquals(listOf(Sentence(0, 800)), sentencesFromBoundaries(emptyList(), 800L))
    }

    @Test
    fun `句子_超长段切完余数正好等于最短句长_要保留`() {
        // 🔴 端点值：16500 = 最长句长 15000 + 最短句长 1500，正好卡在端点
        //   ⇒ 尾部那 1500ms 要**独立成句**、不能被丢。
        //   2026-10-10 修复前：这里只有后两句，前面 `(0,15000)` 整块消失。
        assertEquals(
            listOf(Sentence(0, 15000), Sentence(15000, 16500), Sentence(16500, 18000)),
            sentencesFromBoundaries(listOf(16500L), 18000L),
        )
    }

    // ==================================================================
    // ④ rawSegmentsFromBoundaries —— 给"无时间戳 ASR 文本对齐"用的那套
    // ==================================================================

    @Test
    fun `原始段_没有边界_整段一句`() {
        assertEquals(listOf(Sentence(0, 2000)), rawSegmentsFromBoundaries(emptyList(), 2000L))
    }

    @Test
    fun `原始段_不做最短句长合并_短句照样保留`() {
        // 🔴 这是 rawSegments 存在的**唯一理由**：ASR 文本对齐需要"一停顿 = 一段"，
        //   像 "Hi." 这种 300ms 的短句一旦被最短句长（1500ms）并进下一段，文本就再也对不齐了。
        //   对照：同样的边界喂给 sentencesFromBoundaries，短段会被并掉。
        val bs = listOf(500L, 800L, 3000L)
        val raw = rawSegmentsFromBoundaries(bs, 3200L)
        assertEquals(
            listOf(Sentence(0, 500), Sentence(500, 800), Sentence(800, 3000), Sentence(3000, 3200)),
            raw,
        )
        val merged = sentencesFromBoundaries(bs, 3200L)
        assertEquals("对照：合并版把 3 段短的并成了 1 句", listOf(Sentence(0, 3200)), merged)
    }

    @Test
    fun `原始段_过短的边界段并被下一段`() {
        // 100ms 的一段太碎（< 200ms 门槛）⇒ 不单独成段，划给后面
        assertEquals(listOf(Sentence(0, 2000)), rawSegmentsFromBoundaries(listOf(100L), 2000L))
    }

    @Test
    fun `原始段_边界段正好200ms_要保留`() {
        // 🔴 端点值：200ms 门槛是**闭区间**，正好 200ms 要保留（`>` 会把它并进下一段）
        assertEquals(
            listOf(Sentence(0, 200), Sentence(200, 2000)),
            rawSegmentsFromBoundaries(listOf(200L), 2000L),
        )
    }

    @Test
    fun `原始段_末尾不足200ms_并回上一段`() {
        assertEquals(listOf(Sentence(0, 1100)), rawSegmentsFromBoundaries(listOf(1000L), 1100L))
    }

    @Test
    fun `原始段_超15秒强制切_完整覆盖无空洞`() {
        // 🔴 跟 `sentencesFromBoundaries` 同一批边界对照：这条分支覆盖是**完整**的（0~50000 无空洞）
        val out = rawSegmentsFromBoundaries(listOf(40000L), 50000L)
        assertEquals(
            listOf(Sentence(0, 15000), Sentence(15000, 30000), Sentence(30000, 40000), Sentence(40000, 50000)),
            out,
        )
        assertEquals(0L, out.first().startMs)
        assertEquals(50000L, out.last().endMs)
        for (k in 1 until out.size) assertEquals(out[k - 1].endMs, out[k].startMs)
    }

    @Test
    fun `原始段_常规输入_首尾相接且不重叠`() {
        val total = 30000L
        val out = rawSegmentsFromBoundaries(listOf(2000L, 5000L, 11000L, 19500L, 28000L), total)
        assertEquals(0L, out.first().startMs)
        assertEquals(total, out.last().endMs)
        for (k in 1 until out.size) assertEquals(out[k - 1].endMs, out[k].startMs)
    }
}
