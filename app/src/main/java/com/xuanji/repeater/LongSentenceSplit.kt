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
