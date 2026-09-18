package com.xuanji.repeater

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.util.Log
import android.os.Binder
import android.os.Build
import android.os.IBinder
import java.io.File
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ===== ASR 文本对齐 / 段内断句 / 句子合并（从 PlayerService 纯搬迁；可见性 private->internal，逻辑零改动） =====

    /** 波形实际帧长（ms）：双声道 mp3 帧数翻倍（约 25ms/帧），单声道约 50ms/帧。
     *  用总时长/帧数校准，避免双声道换算错误。 */
internal fun frameMsOf(totalMs: Long, waveform: FloatArray): Double {
        if (waveform.isEmpty() || totalMs <= 0) return 50.0
        return (totalMs.toDouble() / waveform.size).coerceIn(20.0, 60.0)
    }

    /** 段内二次断句：段文本按句末标点拆子句（缩写保护），
     *  子句时间 = 段内短停顿（>=150ms）吸附 + 字符比例插值。
     *  段文本无标点且超长时按逗号/空格粗拆，避免整段一句。 */
internal fun splitSegmentWithin(seg: Sentence, text: String, waveform: FloatArray, totalMs: Long): List<Sentence> {
        val protected = asrAbbrevRegex.replace(text) { m -> m.value.replace('.', ASR_ABBREV_PLACEHOLDER[0]) }
        var subs = asrEmojiRegex.replace(protected, " ")
            .split(Regex("(?<=[.!?！？。])(?=\\s*[A-Z]|\\s+[a-z]|\\s*$)"))
            .map { it.trim().replace(ASR_ABBREV_PLACEHOLDER, ".") }
            .filter { it.isNotEmpty() }
        // 无标点且超长：按逗号切，再不行按 6 个词一刀粗拆（阈值放低，宁可碎一点也别整段一句）
        val tooLong = text.length > 45 || text.split(Regex("\\s+")).count { it.isNotEmpty() } > 9
        if (subs.size <= 1 && tooLong) {
            subs = text.split(Regex("[,;，；]")).flatMap { part ->
                val p = part.trim()
                if (p.length <= 25) listOf(p)
                else p.split(Regex("\\s+")).filter { it.isNotEmpty() }.chunked(6).map { it.joinToString(" ") }
            }.filter { it.isNotEmpty() }
        }
        if (subs.size <= 1) return listOf(seg.copy(text = text))

        // 段内短停顿（>=150ms）作为子边界吸附点（波形帧长按实际推断，双声道音频 25ms/帧）
        val frameMs = frameMsOf(totalMs, waveform)
        val startFrame = (seg.startMs / frameMs).toInt().coerceAtLeast(0)
        val endFrame = (seg.endMs / frameMs).toInt().coerceAtMost(waveform.size)
        val pauses = mutableListOf<Long>()
        if (endFrame > startFrame) {
            val segPeak = (startFrame until endFrame).maxOfOrNull { waveform[it] } ?: 1f
            val thr = (segPeak * 0.1f).coerceAtLeast(0.01f)
            var i = startFrame
            while (i < endFrame) {
                if (waveform[i] < thr) {
                    var j = i
                    while (j < endFrame && waveform[j] < thr) j++
                    if ((j - i) * frameMs >= 150) pauses.add((((i + j) / 2) * frameMs).toLong())
                    i = j
                } else i++
            }
        }

        val total = subs.sumOf { it.count { ch -> ch.isLetterOrDigit() } }.coerceAtLeast(1)
        val segMs = (seg.endMs - seg.startMs).coerceAtLeast(1L)
        val out = mutableListOf<Sentence>()
        var cursor = seg.startMs
        subs.forEachIndexed { idx, st ->
            val ratio = st.count { ch -> ch.isLetterOrDigit() }.toDouble() / total
            var end = if (idx == subs.size - 1) seg.endMs
            else (cursor + (segMs * ratio).toLong()).coerceAtLeast(cursor + 400L)
            // 吸附到段内最近的短停顿（1s 内），让子句边界落在真实停歇处
            if (idx < subs.size - 1) {
                val snap = pauses.minByOrNull { kotlin.math.abs(it - end) }
                if (snap != null && kotlin.math.abs(snap - end) < 1000) {
                    end = snap.coerceIn(cursor + 300L, seg.endMs - 200L)
                }
            }
            out.add(Sentence(cursor, end, st))
            cursor = end
        }
        return out
    }
internal val asrEmojiRegex = Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u2600-\\u27BF\\u2190-\\u21FF\\uFE0F]")

    /** 常见英文缩写：拆句前把点换成占位符，避免 "Mr. Taylor" 被拆成两句 */
internal val asrAbbrevRegex = Regex("\\b(Mr|Mrs|Ms|Dr|St|Prof|Jr|Sr|vs|etc|eg|ie|No|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\.", RegexOption.IGNORE_CASE)
internal val ASR_ABBREV_PLACEHOLDER = "\uE000"
    /** 句并入邻组的代价惩罚（ms）：抑制"整篇并成几句" */
internal val ASR_MERGE_PENALTY_MS = 1500.0
    /** 段并入邻组的代价惩罚（ms）：抑制大量停顿段并入导致组超长 */
internal val ASR_SKIP_PENALTY_MS = 600.0
    /** 对齐组时长上限：超过则组内句子按估时比例拆开（防超长句） */
internal val ASR_MAX_GROUP_MS = 6000L
    /** 对齐组句数上限：超过则拆开 */
internal val ASR_MAX_GROUP_SENTENCES = 3
    /** 无时间戳 ASR 文本 ↔ 静音段 动态规划全局最优对齐：
     *  1) 剥离 emoji、按句末标点拆句（标点后只要不是小写字母就拆——ASR 文本常无空格，
     *     如 "everyone.Welcome"，句号后也可能直接跟 emoji 或下一句）；
     *  2) 每句估时 ≈ 总语音时长 × 字符占比；
     *  3) 与各静音段真实时长做单调序列比对（类似基因比对）：
     *     句配段代价 |估时-实际时长|、句并入邻组代价=其估时、段并入邻组代价=其时长，
     *     求全局最小总代价。相比逐句累计贪心，能纠正语速快慢造成的 ±1 段错位，
     *     避免"点的句子"和"实际播的句子"错开。 */
internal fun alignTextToSilence(text: String, segs: List<Sentence>): List<Sentence> {
        // 缩写保护：Mr. 等先换成占位符，避免被句末标点规则拆开
        val protected = asrAbbrevRegex.replace(text) { m -> m.value.replace('.', ASR_ABBREV_PLACEHOLDER[0]) }
        val sentences = asrEmojiRegex.replace(protected, " ")
            .split(Regex("(?<=[.!?！？。])(?=\\s*[A-Z]|\\s+[a-z]|\\s*$)"))
            .map { it.trim().replace(ASR_ABBREV_PLACEHOLDER, ".") }
            .filter { it.isNotEmpty() }
        if (sentences.isEmpty() || segs.isEmpty()) return segs

        // 估时按"字母数字字符数"（标点/空格不影响语音时长），比纯 length 更贴近真实语速
        fun speechChars(s: String): Int = s.count { it.isLetterOrDigit() }
        val totalChars = sentences.sumOf { speechChars(it) }.toDouble().coerceAtLeast(1.0)
        val spokenMs = segs.sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }.toDouble().coerceAtLeast(1.0)
        val est = DoubleArray(sentences.size) { spokenMs * speechChars(sentences[it]) / totalChars }
        val dur = DoubleArray(segs.size) { (segs[it].endMs - segs[it].startMs).coerceAtLeast(0L).toDouble() }

        val n = sentences.size
        val m = segs.size
        val inf = Double.POSITIVE_INFINITY
        // dp[i][j] = 前 i 句与前 j 段对齐的最小总代价；back 记录步进方向：
        // 1=句i-1与段j-1配对开新组  2=句i-1并入当前组  3=段j-1并入当前组
        val dp = Array(n + 1) { DoubleArray(m + 1) { inf } }
        val back = Array(n + 1) { ByteArray(m + 1) }
        dp[0][0] = 0.0
        for (i in 0..n) {
            for (j in 0..m) {
                val c = dp[i][j]
                if (c == inf) continue
                val start = i == 0 && j == 0 // 首句必须与首段配对开组（前奏静音归入首句）
                if (i < n && j < m) {
                    val v = c + kotlin.math.abs(est[i] - dur[j])
                    if (v < dp[i + 1][j + 1]) { dp[i + 1][j + 1] = v; back[i + 1][j + 1] = 1 }
                }
                if (i < n && !start) {
                    // 句并入组：加惩罚，抑制"整篇并成几句"
                    val v = c + est[i] + ASR_MERGE_PENALTY_MS
                    if (v < dp[i + 1][j]) { dp[i + 1][j] = v; back[i + 1][j] = 2 }
                }
                if (j < m && !start) {
                    // 段并入组：加小惩罚，避免大量停顿段并入
                    val v = c + dur[j] + ASR_SKIP_PENALTY_MS
                    if (v < dp[i][j + 1]) { dp[i][j + 1] = v; back[i][j + 1] = 3 }
                }
            }
        }

        // 回溯分组：每组 = 连续若干句 + 连续若干段；步进逆行时，
        // 遇到的 2/3 步属于"下一个（逆行方向）对角步"所开的组
        val groups = ArrayList<IntArray>() // [句起,句止,段起,段止]（闭区间）
        var i = n
        var j = m
        var ptHi = -1 // 尚未归属的句最大下标
        var psHi = -1 // 尚未归属的段最大下标
        while (i > 0 || j > 0) {
            when (back[i][j].toInt()) {
                1 -> {
                    groups.add(intArrayOf(i - 1, maxOf(i - 1, ptHi), j - 1, maxOf(j - 1, psHi)))
                    ptHi = -1; psHi = -1; i--; j--
                }
                2 -> { ptHi = maxOf(ptHi, i - 1); i-- }
                3 -> { psHi = maxOf(psHi, j - 1); j-- }
                else -> { if (i > 0) i-- else j-- } // 理论不可达，防御
            }
        }
        groups.reverse()

        val out = mutableListOf<Sentence>()
        for (g in groups) {
            val txts = (g[0]..g[1]).mapNotNull { sentences.getOrNull(it) }.filter { it.isNotEmpty() }
            val start = segs[g[2]].startMs
            val end = segs[g[3]].endMs
            if (txts.isEmpty()) {
                // 无文本段（纯停顿尾巴）：并入上一句结尾，避免空白可点句
                if (out.isNotEmpty()) out[out.lastIndex] = out.last().copy(endMs = end)
                continue
            }
            val groupMs = end - start
            val tooMany = txts.size > ASR_MAX_GROUP_SENTENCES
            val tooLong = groupMs > ASR_MAX_GROUP_MS
            if (tooMany || tooLong) {
                // 长组拆分：每句独立成句，组时长按句的估时比例分配，贴合各自语音时长
                val total = txts.sumOf { it.count { ch -> ch.isLetterOrDigit() } }.coerceAtLeast(1)
                var cursor = start
                txts.forEachIndexed { idx, st ->
                    val ratio = st.count { ch -> ch.isLetterOrDigit() }.toDouble() / total
                    val stEnd = if (idx == txts.size - 1) end
                    else (cursor + (groupMs * ratio).toLong()).coerceAtLeast(cursor + 400L)
                    out.add(Sentence(cursor, stEnd, st))
                    cursor = stEnd
                }
            } else {
                out.add(Sentence(start, end, txts.joinToString(" ")))
            }
        }
        return out
    }

    /** 按标点断句（LRC 文本 + 时间戳），再按长度规则调整：
     *  1) 连续 LRC 行累积，遇到句末标点（.?!）才闭合为一句 → 长句不被 LRC 行切碎
     *  2) 一行内含多个句末标点 → 按字符比例在行时间区间内插值切分
     *  3) 短句（<SHORT_SENTENCE_MS）与相邻句合并（二合一，避免复读太碎）
     *  4) 长句（>LONG_SENTENCE_MS 或超长字符）在逗号处切分，切点优先吸附最近静音边界
     *  5) 传入静音断句边界时，终点进入下一句（LRC 打点偏晚）则拉回句间停顿处 */
internal fun buildPunctSentences(
        lyrics: List<LrcLine>,
        totalMs: Long,
        silenceBounds: List<Long> = emptyList(),
    ): List<Sentence> {
        // 句末标点（含后随闭合引号/括号）；逗号、分号不算句末
        val endPunct = Regex("""[.!?！？。][”"'』」）)]*""")
        // 自然句：时间区间 + 文本（供合并/切分）
        data class Natural(val start: Long, val end: Long, val text: String)

        val naturals = mutableListOf<Natural>()
        var segStartMs = -1L
        var buf = StringBuilder()
        for ((i, line) in lyrics.withIndex()) {
            if (segStartMs < 0) segStartMs = line.timeMs
            val prevLen = buf.length
            if (prevLen > 0) buf.append(' ')
            buf.append(line.text)
            // 从本行新增文本处开始找标点；行首时 offset=0
            var offset = if (prevLen > 0) prevLen + 1 else 0
            val nextMs = if (i < lyrics.size - 1) lyrics[i + 1].timeMs else totalMs
            val lineLen = (nextMs - line.timeMs).coerceAtLeast(1L)
            var lineCursor = line.timeMs
            while (true) {
                val m = endPunct.find(buf, offset) ?: break
                val endIdx = m.range.last + 1
                val consumedInLine = (endIdx - offset).coerceAtLeast(0)
                val ratio = consumedInLine.toDouble() / line.text.length.coerceAtLeast(1)
                var endMs = (lineCursor + (lineLen * ratio).toLong()).coerceAtLeast(segStartMs + 1)
                // 静音修正：终点进入下一句时拉回句间停顿
                if (silenceBounds.isNotEmpty()) {
                    silenceBounds.filter { it in segStartMs..endMs }
                        .maxOrNull()?.let { endMs = it.coerceAtLeast(segStartMs + 1) }
                }
                val sentText = buf.substring(offset, endIdx).trim()
                naturals.add(Natural(segStartMs, endMs, sentText))
                segStartMs = endMs
                lineCursor = endMs
                buf = StringBuilder(buf.substring(endIdx).trimStart())
                // 剩余部分属于本行，从头找下一个标点
                offset = 0
            }
        }
        // 尾部未闭合文本（最后一行无句末标点）：终点到音频结尾
        if (buf.isNotEmpty() && segStartMs >= 0) {
            naturals.add(Natural(segStartMs, totalMs.coerceAtLeast(segStartMs + 1), buf.toString().trim()))
        }

        // ---- 短句合并：< SHORT_SENTENCE_MS 时与下一句合并（末句与上一句合并），循环直到没有短句 ----
        var changed = true
        while (changed) {
            changed = false
            var k = 0
            while (k < naturals.size) {
                val n = naturals[k]
                if (n.end - n.start < SHORT_SENTENCE_MS && naturals.size > 1) {
                    if (k < naturals.size - 1) {
                        val next = naturals.removeAt(k + 1)
                        naturals[k] = n.copy(end = next.end, text = n.text + " " + next.text)
                    } else {
                        val prev = naturals.removeAt(k - 1)
                        naturals[k - 1] = prev.copy(end = n.end, text = prev.text + " " + n.text)
                        k--
                    }
                    changed = true
                }
                k++
            }
        }

        // ---- 长句切分：> LONG_SENTENCE_MS 或 > LONG_SENTENCE_CHARS 时在逗号/从句连接词处切 ----
        // 连接词需前后带空白，避免误匹配词内子串（如 understand 里的 and）
        val cutPoint = Regex("""[，,]|\s+(and|but|or|so|because|that|when|if|which|who|where|while|although|since|until|after|before)\s+""")
        val final = mutableListOf<Sentence>()
        for (n in naturals) {
            var segStart = n.start
            var segText = n.text
            var segEnd = n.end
            while (segEnd - segStart > LONG_SENTENCE_MS || segText.length > LONG_SENTENCE_CHARS) {
                val m = cutPoint.find(segText) ?: break
                val cutPos = m.range.first
                val ratio = (cutPos + 1).toDouble() / segText.length.coerceAtLeast(1)
                var cutMs = segStart + ((segEnd - segStart) * ratio).toLong()
                // 切点吸附最近静音边界（±800ms），时间更准
                if (silenceBounds.isNotEmpty()) {
                    silenceBounds.filter { it in (cutMs - 800)..(cutMs + 800) }
                        .minByOrNull { kotlin.math.abs(it - cutMs) }
                        ?.let { cutMs = it }
                }
                if (cutMs <= segStart || cutMs >= segEnd) break
                final.add(Sentence(segStart, cutMs))
                segStart = cutMs
                segText = segText.substring(cutPos + 1).trimStart()
            }
            final.add(Sentence(segStart, segEnd))
        }
        return final
    }

/** 波形-歌词融合断句：
     *  句子起点 = 歌词行时间戳（每句必有歌词文本，中间无空白）；
     *  终点 = 下一个波形静音点（贴合语音停顿，歌词对上波纹）；
     *  歌词密集时（下一行歌词早于静音点）按下一行歌词边界收尾；
     *  前奏/尾奏各合并为一个句子（纯音乐段留空一行，不再大片空白）。
     *  无 LRC 时退化为纯波形断句 + 短句合并。 */
internal fun buildWaveLyricSentences(
        silenceSentences: List<Sentence>,
        lyrics: List<LrcLine>,
        totalMs: Long,
    ): List<Sentence> {
        if (lyrics.isEmpty()) return mergeShortSentences(silenceSentences)
        // 波形断句边界（静音点，去重排序）
        val silences = silenceSentences.flatMap { listOf(it.startMs, it.endMs) }
            .filter { it > 0 && it < totalMs }
            .distinct()
            .sorted()
        val result = mutableListOf<Sentence>()
        for (i in lyrics.indices) {
            val start = lyrics[i].timeMs
            val nextLyric = if (i + 1 < lyrics.size) lyrics[i + 1].timeMs else totalMs
            // 行末停顿：本行终点前 ≤1.2s 的最近静音点（真实语音结束，贴合波纹）。
            // 行内逗号等短停顿离行末远，不会被误当行末 → 整行文本完整覆盖，不截断
            val rowEnd = silences.filter { it > start && it < nextLyric && nextLyric - it <= 1200 }.maxOrNull()
            val end = rowEnd ?: nextLyric // 无行末停顿（连唱）则按下一行歌词边界收尾
            if (end > start) result.add(Sentence(start, end, lyrics[i].text))
        }
        // 前奏：0 → 第一句起点（合并为一个句子，纯音乐段）。
        // 终点钳制到波形语音起点之前（LRC 打点晚于实际语音时，避免前奏句包进歌词开头）
        val firstStart = result.firstOrNull()?.startMs ?: return result
        val firstWaveStart = silenceSentences.firstOrNull()?.startMs ?: firstStart
        val introEnd = minOf(firstStart, firstWaveStart)
        if (introEnd > 0) result.add(0, Sentence(0L, introEnd))
        // 尾奏：最后句终点 → 音频末尾（合并为一个句子）。
        // 起点钳制到波形语音终点之后（避免尾奏句包进歌词结尾）
        val lastEnd = result.lastOrNull()?.endMs ?: return result
        val lastWaveEnd = silenceSentences.lastOrNull()?.endMs ?: lastEnd
        val outroStart = maxOf(lastEnd, lastWaveEnd)
        if (outroStart < totalMs - 100) result.add(Sentence(outroStart, totalMs))
        // 短句合并（保留拼接文本）；空文本句（前奏/尾奏）不参与合并
        return mergeShortSentences(result, protectEmpty = true)
    }

    /** 短句合并：< SHORT_SENTENCE_MS 的句子与相邻句合并
     * （末句与上一句合并），循环至无短句。避免复读太碎。
     *  protectEmpty=true 时，空文本句（前奏/尾奏占位）不参与合并，
     *  歌词短句也不与空文本句合并（保持纯音乐段独立、边界干净）。 */
internal fun mergeShortSentences(list: List<Sentence>, protectEmpty: Boolean = false): List<Sentence> {
        val merged = list.toMutableList()
        var changed = true
        while (changed) {
            changed = false
            var k = 0
            while (k < merged.size) {
                val n = merged[k]
                if (n.endMs - n.startMs < SHORT_SENTENCE_MS && merged.size > 1
                    && (!protectEmpty || n.text.isNotEmpty())
                ) {
                    // 合并目标：优先下一个（protectEmpty 时需非空）
                    val next = merged.getOrNull(k + 1)
                    if (next != null && (!protectEmpty || next.text.isNotEmpty())) {
                        merged.removeAt(k + 1)
                        merged[k] = Sentence(n.startMs, next.endMs, mergeText(n.text, next.text))
                        changed = true
                        continue // 合并后可能仍短，原地继续检查
                    }
                    // 下一个不可用（没有或为空文本）：找上一个可合并的
                    var p = k - 1
                    if (protectEmpty) {
                        while (p >= 0 && merged[p].text.isEmpty()) p--
                    }
                    if (p >= 0) {
                        val prev = merged[p]
                        merged[p] = Sentence(prev.startMs, n.endMs, mergeText(prev.text, n.text))
                        merged.removeAt(k)
                        changed = true
                        k = p // 从合并处继续检查
                        continue
                    }
                }
                k++
            }
        }
        return merged
    }

internal fun mergeText(a: String, b: String): String = when {
        a.isEmpty() -> b
        b.isEmpty() -> a
        else -> "$a $b"
    }