package com.xuanji.repeater

/**
 * 长句切分共享工具：音频模式（PlayerService）与视频模式（MainActivity ASR 生成字幕）共用同一套算法，
 * 保证两边断句行为一致。视频无波形静音 / LRC，调用时把「原始 ASR segment 之间的间隙」当 silenceSource 传入，
 * lyrics 传空列表即可（走「静音点最近中点」分支，与音频的优先级一致）。
 *
 * 注意：本文件只放「长句切分」逻辑；波形断句（按 RMS 能量切静音段）在 SentenceSplitter.kt 的 object 里，
 * 两者职责不同，不要混。
 */

/** 短句合并阈值：句长低于此值（ms）与相邻句合并，避免复读太碎 */
internal const val SHORT_SENTENCE_MS = 2500L
/** 长句切分阈值：句长超过此值（ms）或字符数超过 LONG_SENTENCE_CHARS，在逗号处切分 */
internal const val LONG_SENTENCE_MS = 6000L
internal const val LONG_SENTENCE_CHARS = 40

/** 长句切分：> LONG_SENTENCE_MS 或 > LONG_SENTENCE_CHARS 的句子，
 *  在句内最近的静音点处切分（切点=真实语音停顿，听感自然）；
 *  合并句（含多行歌词）优先按歌词行边界切分：时间切点=行边界附近静音点，
 *  文本按行完整归属，避免"下一句句首词串到上一句句尾"；
 *  句内无切点或切出的段太短（<短句阈值）时不硬切，保持整句。 */
internal fun splitLongSentences(
    sentences: List<Sentence>,
    silenceSource: List<Sentence>,
    lyrics: List<LrcLine>,
): List<Sentence> {
    // 切点只取"长停顿"：相邻波形句间隙 ≥500ms 的静音段（句间停顿，逗号等短停顿不切，
    // 避免把语义半句切碎 → 复读只听到逗号前的内容）
    val MIN_GAP_MS = 500L
    val silences = silenceSource.zipWithNext()
        .filter { it.second.startMs - it.first.endMs >= MIN_GAP_MS }
        .flatMap { listOf(it.first.endMs, it.second.startMs) }
        .filter { it > 0 }
        .distinct()
        .sorted()
    val out = mutableListOf<Sentence>()
    for (s in sentences) out.addAll(splitOneLong(s, silences, lyrics))
    return out
}

/**
 * 视频专用：直接传「音轨波形真实静音点」(ms) 列表当切点，替代音频模式用的「ASR segment 间隙」。
 * 慢速教学音频 ASR 段少、段间间隙不够长，靠 segment 间隙切不动；波形静音点是真正的停顿，
 * 长句(>6s 或 >40 字)能在最近静音点自然切开（splitOneLong 内部按中点就近取点 + 过短段保护）。
 */
internal fun splitLongSentencesByPoints(
    sentences: List<Sentence>,
    silencePoints: List<Long>,
    lyrics: List<LrcLine>,
): List<Sentence> {
    return sentences.flatMap { splitOneLong(it, silencePoints, lyrics) }
}

/** 句间停顿最短算多长：低于它的静音算句中换气，**不做吸附目标**（吸到换气口会把句子切在词中间）。 */
internal const val SENTENCE_GAP_MS = 400L
/** 边界吸附窗口：ASR 边界离最近句间停顿超过这个距离就不动（太远多半不是同一处停顿）。 */
internal const val SNAP_WINDOW_MS = 2500L

/**
 * 【v96】ASR 段边界吸附到真实句间停顿。
 *
 * 为什么：TeleSpeech 偶发「文本记到下一个 segment、时间边界却没跟着走」——
 * 实测（1005 阿亮真机）：'and as always' 的发音落在上一句区间内，文本却在下一句开头，
 * 下一句 startMs 又设在 'thank you' ⇒ 点上一句会读到下句的词、点下一句跳过句头。
 * 波形静音点是真实停顿（永远准）：把相邻两句的公共边界吸附到最近的句间停顿，
 * 文本与声音自然对齐。前半段模型标得准的边界本来就在停顿上，吸附距离≈0，零影响。
 *
 * 保守策略：
 *  · 吸附目标只用 ≥ [SENTENCE_GAP_MS] 的长静音（句间停顿），换气口不碰；
 *  · 窗口 [SNAP_WINDOW_MS] 外不动；
 *  · 吸附后 start ≥ end、或越过邻句边界 ⇒ 该边放弃吸附，回退原值。
 */
internal fun snapBoundariesToSilence(
    sentences: List<Sentence>,
    gapCenters: List<Long>,
): List<Sentence> {
    if (gapCenters.isEmpty() || sentences.isEmpty()) return sentences
    val sorted = gapCenters.sorted()
    val out = ArrayList<Sentence>(sentences.size)
    for (idx in sentences.indices) {
        val s = sentences[idx]
        val prev = out.getOrNull(idx - 1)          // 前一句用吸附后的值判交叉
        val nextRaw = sentences.getOrNull(idx + 1) // 后一句还没吸附，用原值
        var startMs = snapOne(sorted, s.startMs) ?: s.startMs
        var endMs = snapOne(sorted, s.endMs) ?: s.endMs
        // 保护 1：长度为正
        if (startMs >= endMs) { out.add(s); continue }
        // 保护 2：不与已吸附的前句重叠
        if (prev != null && startMs < prev.endMs) startMs = s.startMs.coerceAtLeast(prev.endMs)
        if (startMs >= endMs) { out.add(s); continue }
        // 保护 3：end 不越过下一句的原始起点（下一句起点之后它自己还会吸附）
        if (nextRaw != null && endMs > nextRaw.startMs) endMs = s.endMs.coerceAtMost(nextRaw.startMs)
        if (endMs <= startMs) { out.add(s); continue }
        out.add(Sentence(startMs, endMs, s.text))
    }
    return out
}

/** 离 v 最近的吸附目标；距离超窗返回 null。 */
private fun snapOne(sorted: List<Long>, v: Long): Long? {
    var lo = 0
    var hi = sorted.size - 1
    var ans = -1
    while (lo <= hi) {
        val m = (lo + hi) / 2
        if (sorted[m] >= v) { ans = m; hi = m - 1 } else lo = m + 1
    }
    val nearest = when {
        ans < 0 -> sorted.last()
        ans > 0 && v - sorted[ans - 1] < sorted[ans] - v -> sorted[ans - 1]
        else -> sorted[ans]
    }
    return if (kotlin.math.abs(nearest - v) <= SNAP_WINDOW_MS) nearest else null
}

internal fun splitOneLong(s: Sentence, silences: List<Long>, lyrics: List<LrcLine>): List<Sentence> {
    // 空文本句（前奏/尾奏占位）：不切分，保持 1 行（纯音乐段再长也不碎）
    if (s.text.isEmpty()) return listOf(s)
    if (s.endMs - s.startMs <= LONG_SENTENCE_MS && s.text.length <= LONG_SENTENCE_CHARS) {
        return listOf(s)
    }
    // 句内静音点
    val inner = silences.filter { it > s.startMs && it < s.endMs }
    // 句内歌词行边界（行起点落在句内的，不含句首行本身）
    val rows = lyrics.filter { it.timeMs >= s.startMs && it.timeMs < s.endMs }
    val bounds = rows.drop(1).map { it.timeMs }
    val mid = (s.startMs + s.endMs) / 2
    // 时间切点（真实停顿优先）+ 文本归属行边界（null=单行句无行边界）
    val cutMs: Long
    val cutBound: Long?
    if (bounds.isNotEmpty()) {
        // 1) 行边界附近(±1.2s)有静音点：时间切点=最近静音点（真实停顿，不砍语音），
        //    文本按该行边界整行拆分（不串词）——优先选最接近中点的行边界
        val b1 = bounds.filter { b -> inner.any { kotlin.math.abs(it - b) <= 1200 } }
            .minByOrNull { kotlin.math.abs(it - mid) }
        if (b1 != null) {
            cutBound = b1
            cutMs = inner.filter { kotlin.math.abs(it - b1) <= 1200 }
                .minByOrNull { kotlin.math.abs(it - b1) }!!
        } else {
            // 2) 无静音点靠近行边界（连唱）：时间=行边界兜底，文本按行拆
            cutBound = bounds.filter { b ->
                b - s.startMs >= SHORT_SENTENCE_MS && s.endMs - b >= SHORT_SENTENCE_MS
            }.minByOrNull { kotlin.math.abs(it - mid) }
                ?: bounds.minByOrNull { kotlin.math.abs(it - mid) }!!
            cutMs = cutBound
        }
    } else if (inner.isNotEmpty()) {
        cutBound = null
        cutMs = inner.minByOrNull { kotlin.math.abs(it - mid) }!!
    } else {
        return listOf(s) // 无切点，不硬切
    }
    // 放弃条件：合并句（有行边界）两段都短才放弃（句子>6s 时实际必切，避免超长）；
    // 单行句（静音点切）任一段太短放弃（避免把单行句切碎）
    if (cutBound != null) {
        if (cutMs - s.startMs < SHORT_SENTENCE_MS && s.endMs - cutMs < SHORT_SENTENCE_MS) return listOf(s)
    } else {
        if (cutMs - s.startMs < SHORT_SENTENCE_MS || s.endMs - cutMs < SHORT_SENTENCE_MS) return listOf(s)
    }
    // 文本切分：有行边界 → 按行拆（文本完整不串句）；单行句 → 按比例语义切
    val head: String
    val tail: String
    if (cutBound != null) {
        val splitIdx = rows.indexOfFirst { it.timeMs == cutBound }
        head = rows.take(splitIdx).joinToString(" ") { it.text }
        tail = rows.drop(splitIdx).joinToString(" ") { it.text }
    } else {
        val ratio = (cutMs - s.startMs).toDouble() / (s.endMs - s.startMs)
        head = cutTextAtWord(s.text, ratio)
        tail = s.text.removePrefix(head).trim()
    }
    return splitOneLong(Sentence(s.startMs, cutMs, head), silences, lyrics) +
        splitOneLong(Sentence(cutMs, s.endMs, tail), silences, lyrics)
}

/** 按切点附近最近的"语义停顿"切文本（逗号/句号/连接词优先，其次空格），
 *  避免按时间比例硬切导致"句头词"跑到上一句末尾。 */
internal fun cutTextAtWord(text: String, ratio: Double): String {
    if (text.isEmpty()) return ""
    val idx = (text.length * ratio).toInt().coerceIn(0, text.length)
    val range = (text.length / 4).coerceAtLeast(4) // ±25% 搜索范围
    // 1) 语义停顿候选：句末标点 / 逗号 / 从句连接词（前后带空白的独立词）
    val semantic = Regex("""[.!?。！？][”"'』」）)]*|[,;，；]|\s+(and|but|or|so|because|that|when|if|which|who|where|while|although|since|until)\s+""")
    val punctAt = semantic.findAll(text)
        .map { it.range.first + 1 }
        .filter { kotlin.math.abs(it - idx) <= range }
        .minByOrNull { kotlin.math.abs(it - idx) }
    if (punctAt != null && punctAt < text.length) {
        return text.substring(0, punctAt.coerceAtLeast(1)).trim()
    }
    // 2) 空格候选（最近的单词边界）
    val nextSpace = text.indexOf(' ', idx)
    val prevSpace = text.lastIndexOf(' ', idx)
    val cutAt = when {
        nextSpace >= 0 && prevSpace >= 0 ->
            if (nextSpace - idx <= idx - prevSpace) nextSpace else prevSpace
        nextSpace >= 0 -> nextSpace
        prevSpace >= 0 -> prevSpace
        else -> idx
    }
    return text.substring(0, cutAt.coerceAtLeast(1)).trim()
}
