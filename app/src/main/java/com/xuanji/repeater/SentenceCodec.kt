package com.xuanji.repeater

/** 句子表的文本序列化：用于把 List<Sentence> 存进 SP / JSON（与 SubtitleLibrary 共用，不引新依赖）。 */
fun encodeSentences(list: List<Sentence>): String =
    list.joinToString("\n") { "${it.startMs}\t${it.endMs}\t${it.text.replace("\n", " ")}" }
fun decodeSentences(s: String): List<Sentence> =
    if (s.isBlank()) emptyList() else s.lines().mapNotNull { line ->
        val p = line.split("\t")
        if (p.size == 3) runCatching { Sentence(p[0].toLong(), p[1].toLong(), p[2]) }.getOrNull() else null
    }
