package com.xuanji.repeater

/**
 * 用「外部细时间轴」重切 ASR 长句。
 *
 * ## 为什么需要它
 *
 * 直接把 B站 音频丢给 ASR，返回的 segment 粒度是**模型定的**（跟 `SentenceSplitter.MIN_SILENCE_MS=350ms` 无关）
 * —— 语速快的英语教学视频常被断成 10~15 秒一大段，复读起来一次听太多、跟不上。
 *
 * 而 B站 自己的 `ai-zh` 轨（AI 听译）实测**平均 2.54 秒/条、最长 6.86 秒**，这些是**真实语音停顿点**，
 * 比"按标点二次切分"可靠得多（标点只是猜测，这里是 B站 ASR 真听出来的边界）。
 *
 * ## 做法
 *
 * 拿 B站 轨的时间边界当「尺子」，去切我们 ASR 出来的英文长句：
 *   1. 对每条 ASR 句子，取出落在它区间内的尺子边界，按**目标段长** [TARGET_SEG_MS] 抽稀；
 *   2. 按抽稀后的刻度切成若干段，时间按尺子走（精准）；
 *   3. 文本**按时间比例**分配（我们没有逐词时间戳），落点往最近的**单词边界**吸附。
 *
 * ## 阿亮 0929 的决策
 *
 * - **「直接按时间硬切」**：尺子边界就是真实停顿点，照切即可。切出的半句偶尔不完整（从句被切开）
 *   是可以接受的代价，换来的是时间轴与中文严格对齐。
 * - 不追求"每段都是完整词组"——那会让时间戳和中文错开零点几秒，反而更乱。
 *
 * ## 🔴 两个已踩的坑（别改回去）
 *
 * 1. **抽稀不能按「相邻切点距离 ≥ MIN_SEG_MS」判**：尺子正好每秒一条时，相邻距离恰好 1000ms ≥ 900ms
 *    ⇒ 全部通过，切出 8 个 1 秒渣渣段（0929 离线实测踩到）。**必须按「距上一个保留点 ≥ TARGET_SEG_MS」**，
 *    即从保留点算起的**累计**距离，这样 1 秒密度的尺子会被抽成 ~2.5 秒一段。
 * 2. **句尾余量不足的点要丢**（`endMs - c < MIN_SEG_MS`），否则最后会剩个半秒尾巴。
 */
internal object TimelineRecut {

    /** 抽稀目标：两段之间的理想间隔（ms）。≈ B站 轨实测的 2.54s，取 2.2s 留点余量。 */
    const val TARGET_SEG_MS = 2200L

    /** 切出的绝对下限（ms）。低于此值的段视为"一闪而过"，并入邻段。 */
    const val MIN_SEG_MS = 1200L

    /** 尺子边界离句首/句尾要留的最小余量（ms）。 */
    private const val EDGE_MARGIN_MS = 500L

    /**
     * @param sentences ASR 原始句子（时间戳精确、文本完整，但粒度粗）
     * @param boundaries 尺子边界（ms，通常来自 B站 轨每条的 `from`）
     * @return 切细后的句子列表（时间升序）。尺子为空或切不动时原样返回。
     */
    fun recut(sentences: List<Sentence>, boundaries: List<Long>): List<Sentence> {
        if (sentences.isEmpty()) return sentences
        val ruler = boundaries.filter { it > 0 }.distinct().sorted()
        if (ruler.isEmpty()) return sentences

        val out = mutableListOf<Sentence>()
        for (s in sentences) out.addAll(recutOne(s, ruler))
        return out
    }

    private fun recutOne(s: Sentence, ruler: List<Long>): List<Sentence> {
        if (s.text.isBlank()) return listOf(s)
        val dur = s.endMs - s.startMs
        if (dur <= TARGET_SEG_MS) return listOf(s)

        // 句内尺子边界：两端各留余量，防止切出渣渣段
        val inner = ruler.filter {
            it > s.startMs + EDGE_MARGIN_MS && it < s.endMs - EDGE_MARGIN_MS
        }
        if (inner.isEmpty()) return listOf(s)

        // 🔴 按「距上一个保留点」的累计距离抽稀（不是相邻距离！）
        val kept = mutableListOf(s.startMs)
        for (c in inner) {
            if (c - kept.last() < TARGET_SEG_MS) continue      // 攒够目标段长才留
            if (s.endMs - c < MIN_SEG_MS) continue             // 剩的尾巴太短 ⇒ 不留这点
            kept.add(c)
        }
        if (kept.size < 2) return listOf(s)
        kept.add(s.endMs)

        // 按时间比例分配文本，落点吸附到最近的单词边界
        val pieces = mutableListOf<Sentence>()
        var consumed = 0                       // 已分配给前面各段的字符数
        for (i in 0 until kept.size - 1) {
            val from = kept[i]
            val to = kept[i + 1]
            val isLast = i == kept.size - 2
            val text = if (isLast) {
                s.text.substring(consumed.coerceAtMost(s.text.length)).trim()
            } else {
                val ratio = (to - s.startMs).toDouble() / dur
                val head = cutTextAtWord(s.text, ratio)       // 从头到切点的前缀
                val startIdx = consumed.coerceAtMost(head.length)
                val piece = head.substring(startIdx).trim()
                consumed = head.length
                piece
            }
            if (text.isNotEmpty()) pieces.add(Sentence(from, to, text))
        }

        // 内容校验：每段非空、且拼起来还原原文（字母数字层面）。不满足就整句退回，绝不产出错位碎片。
        if (pieces.isEmpty()) return listOf(s)
        val rebuilt = pieces.joinToString(" ") { it.text }
        val norm = { x: String -> x.filter { it.isLetterOrDigit() }.lowercase() }
        if (norm(rebuilt) != norm(s.text)) return listOf(s)
        return pieces
    }
}


