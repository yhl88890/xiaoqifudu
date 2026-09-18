package com.xuanji.repeater

/** 句子表的文本序列化 + 短句合并工具（与 SubtitleLibrary 共用，不引新依赖）。 */
fun encodeSentences(list: List<Sentence>): String =
    list.joinToString("\n") { "${it.startMs}\t${it.endMs}\t${it.text.replace("\n", " ")}" }
fun decodeSentences(s: String): List<Sentence> =
    if (s.isBlank()) emptyList() else s.lines().mapNotNull { line ->
        val p = line.split("\t")
        if (p.size == 3) runCatching { Sentence(p[0].toLong(), p[1].toLong(), p[2]) }.getOrNull() else null
    }

/**
 * 视频 ASR 短句合并：TeleSpeechASR 的 segment 粒度偶发偏碎（一两词一句），
 * 把时长 < 1200ms 的短句与相邻句合并，字幕行更整、点句复读听感更自然。
 * 逻辑对齐 PlayerService.mergeShortSentences（优先并下一个，无则并上一个）。
 */
internal fun mergeShortAsrSentences(list: List<Sentence>): List<Sentence> {
    val shortMs = 1200L
    // 相邻两句间隔超过 5 秒就别硬并：分片识别时若中间某段失败被跳过，
    // 前后两句会隔着整个失败段（10 分钟），并起来就是一句点下去要播十分钟的巨无霸
    val gapLimitMs = 5000L
    val out = list.toMutableList()
    var changed = true
    while (changed) {
        changed = false
        var k = 0
        while (k < out.size) {
            val n = out[k]
            if (n.endMs - n.startMs < shortMs && out.size > 1 && n.text.isNotEmpty()) {
                val next = out.getOrNull(k + 1)
                if (next != null && next.text.isNotEmpty() && next.startMs - n.endMs <= gapLimitMs) {
                    // 注意必须写 ${n.text}：写成 "$n.text" 会被 Kotlin 解析成 ${n} 的 toString 再拼上字面量 ".text"，
                    // 结果字幕里出现 "Sentence(startMs=..., text=xxx).text" 这种鬼文本
                    out[k] = Sentence(n.startMs, next.endMs, "${n.text} ${next.text}".trim())
                    out.removeAt(k + 1)
                    changed = true
                    continue
                }
                val prev = out.getOrNull(k - 1)
                if (prev != null && prev.text.isNotEmpty() && n.startMs - prev.endMs <= gapLimitMs) {
                    out[k - 1] = Sentence(prev.startMs, n.endMs, "${prev.text} ${n.text}".trim())
                    out.removeAt(k)
                    changed = true
                    k--
                    continue
                }
            }
            k++
        }
    }
    return out
}
