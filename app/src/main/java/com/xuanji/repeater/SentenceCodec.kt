package com.xuanji.repeater

/**
 * 句子表的文本序列化 + 短句合并工具（与 SubtitleLibrary 共用，不引新依赖）。
 *
 * 🔴【1008 修】原来文本里的换行是直接 `replace("\n", " ")` **压成空格**的。
 * 单行字幕看不出问题，但**双语字幕**（英文一行 + 中文一行 —— SRT 里本来就是两行、ASS 双语也是这样）
 * 一存一读就并成一行，跟视频页当场显示的两行对不上（阿亮这次要支持下载来的双语字幕，
 * 这条不修，双语到第二天打开就"塌"成一行了）。
 * 现在改成转义保存、读取还原；制表符同理（它是字段分隔符，文本里出现会把这一行切坏）。
 *
 * ⚠️ **向后兼容**：老数据里不含反斜杠转义序列 ⇒ [decodeSentences] 会原样返回，不会读坏历史字幕。
 * （ASS 里的 `\N` 是大写 N，且在解析阶段已还原成真换行，不存在"存的是字面 \N"的情况。）
 */
fun encodeSentences(list: List<Sentence>): String =
    list.joinToString("\n") { "${it.startMs}\t${it.endMs}\t${escapeText(it.text)}" }
fun decodeSentences(s: String): List<Sentence> =
    if (s.isBlank()) emptyList() else s.lines().mapNotNull { line ->
        val p = line.split("\t")
        if (p.size == 3) runCatching {
            Sentence(p[0].toLong(), p[1].toLong(), unescapeText(p[2]))
        }.getOrNull() else null
    }

/** 存前转义：反斜杠**先**自己转义，再把换行/制表符变成 `\n`/`\t` 两个字面字符。顺序反了会双重转义。 */
private fun escapeText(t: String): String = t
    .replace("\\", "\\\\")
    .replace("\r\n", "\n")
    .replace("\r", "")
    .replace("\n", "\\n")
    .replace("\t", "\\t")

/**
 * 读时还原。**必须从左到右单次扫描** —— 若先 replace("\\n") 再 replace("\\\\")，
 * 那么文本里真正的「反斜杠 + n」（作者故意写的）会被读成换行。扫描法没有这个歧义。
 */
private fun unescapeText(s: String): String {
    if ('\\' !in s) return s
    val sb = StringBuilder(s.length)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '\\' && i + 1 < s.length) {
            when (s[i + 1]) {
                'n' -> { sb.append('\n'); i += 2 }
                't' -> { sb.append('\t'); i += 2 }
                'r' -> { i += 2 }
                '\\' -> { sb.append('\\'); i += 2 }
                // 认不出的转义（老数据里的普通反斜杠）：原样保留这个字符，继续往后走
                else -> { sb.append(c); i++ }
            }
        } else {
            sb.append(c); i++
        }
    }
    return sb.toString()
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
