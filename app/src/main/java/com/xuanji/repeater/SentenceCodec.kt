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

/**
 * @param reorderBilingual 读出来的双语行序要不要归一（英文提上）。
 *   🔴【1010 阿亮定】**我们自己 ASR 生成的**字幕一律传 `false` —— 它是中英**混杂**、
 *   不是双语对照，重排会把"含汉字的那行"整行挪走，位置就不对了（阿亮：ASR 都不管）。
 *   抓来的现成字幕（手动/.srt/内封轨/B站CC）保持默认 `true`。
 */
fun decodeSentences(s: String, reorderBilingual: Boolean = true): List<Sentence> =
    if (s.isBlank()) emptyList() else s.lines().mapNotNull { line ->
        val p = line.split("\t")
        if (p.size == 3) runCatching {
            val t = p[2]
            // 🔴🔴【1010】**就地**把双语行序纠正成「英文在上」。
            // 为什么非在这里不可（这一版是第三次尝试，前两次都没治住，原因记下来）：
            //   前两版分别改在 `SubtitleParser`（解析侧）和 `SubtitleLibrary`（字幕库侧），
            //   实测都没用 —— 因为 **App 冷启动根本不走那两条路**：它直接读
            //   `xuanji_video.xml` 里的 `sentences` **快照**，而那份快照是上次退出时原样存下来的，
            //   解析器和字幕库都不参与。改代码当然不生效。
            //   实测铁证：字幕库里 E03 条目已是英文在上，SP 快照里还是中文在上 ⇒ 界面显示的是快照。
            // ⚠️ 顺带一个坑：`LaunchedEffect(page)` 里那次 saveVideoPrefs 看着能"顺手纠正"SP，
            //   但它在 `page` **没变化时根本不会跑**（冷启时 page 原本就是 VIDEO）⇒ 指望不上。
            // ⇒ 唯一可靠的收口点就是这里：**读出来就纠正**，不管谁读、读哪份。
            //   ⚠️【1010】但 ASR 生成的那份除外（`reorderBilingual = false`）—— 见上面参数说明。
            val raw = unescapeText(t)
            Sentence(p[0].toLong(), p[1].toLong(), if (reorderBilingual) orderBilingual(raw) else raw)
        }.getOrNull() else null
    }

/**
 * 🔴🔴【1010 双语行序归一】把一句里**多行文本**统一成「英文在上、中文在下」。
 *
 * **放在这里 = 唯一的收口点**（就是这次白折腾一轮换来的教训）：
 *   `decodeSentences` 是 **SP 快照（xuanji_video.xml）/ 字幕库（xuanji_subtitles.xml）/
 *   播放服务** 共用的读取口。中文在上的存量数据从哪条路进来都会经过它 ——
 *   只改解析侧 `SubtitleParser` 治不了存量快照（App 冷启动**直接复用快照、不过解析器**），
 *   上一版就是这么失败的：代码没错、但那条路根本不走它。
 *
 * 规则（**只重排，绝不删任何一行、绝不改文本**）：
 *   - 含汉字（`\u4e00-\u9fff`）的算**中文行**，其余算**英文行**；
 *   - 英文行整体在前、中文行整体在后，各自保持**原相对顺序**；
 *   - 有效行不足 2 行、或中文/英文缺一（纯单语字幕）⇒ **原样返回**，一个字都不动。
 *
 * 幂等：已经是英文在上的、单语的，跑多少次结果都一样。
 */
internal fun orderBilingual(text: String): String {
    if (text.isEmpty()) return text
    val valid = text.split('\n').filter { it.isNotBlank() }
    if (valid.size <= 1) return text
    val cn = ArrayList<String>(valid.size)
    val en = ArrayList<String>(valid.size)
    for (l in valid) {
        var hasHan = false
        for (c in l) if (c in '\u4e00'..'\u9fff') { hasHan = true; break }
        if (hasHan) cn.add(l) else en.add(l)
    }
    if (cn.isEmpty() || en.isEmpty()) return text
    return (en + cn).joinToString("\n")
}

/** 这一行是不是中文行（含汉字即算）—— 「只看中文/只看英文」和"点词选词只认英文"共用此判据。 */
internal fun hasHan(s: String): Boolean {
    for (c in s) if (c in '\u4e00'..'\u9fff') return true
    return false
}

/**
 * 🔴【1010】字幕显示模式 —— 「双语 / 只看英文 / 只看中文」。
 *
 * 只作用于**双语字幕**（一句里既有中文行又有英文行）。单语字幕任何档位都原样显示：
 * 因为挑不出对面那一行，硬挑就会把整条字幕变空。
 */
enum class SubtitleMode { BILINGUAL, EN_ONLY, CN_ONLY }

/**
 * 按显示模式裁一句字幕（**只减行、不改字**）。
 *
 * 规则：
 * - 单语（全中文 或 全英文）⇒ **任何档位都原样返回**（没得挑，别弄空）；
 * - [SubtitleMode.BILINGUAL] ⇒ 原样返回；
 * - [SubtitleMode.EN_ONLY] ⇒ 只留英文行；[SubtitleMode.CN_ONLY] ⇒ 只留中文行；
 * - 多行时保留满足条件的**全部**行（三行以上也不丢）。
 * - 换行丢失的空行原样跳过 —— 输出只由有效行拼成。
 */
internal fun filterByMode(text: String, mode: SubtitleMode): String {
    if (mode == SubtitleMode.BILINGUAL || text.isEmpty()) return text
    val valid = text.split('\n').filter { it.isNotBlank() }
    if (valid.size <= 1) return text          // 单行 ⇒ 没得挑
    val cn = valid.filter { hasHan(it) }
    val en = valid.filterNot { hasHan(it) }
    if (cn.isEmpty() || en.isEmpty()) return text   // 单语字幕 ⇒ 任何档位都原样
    val keep = if (mode == SubtitleMode.EN_ONLY) en else cn
    return keep.joinToString("\n")
}

/** 这句是**双语**吗（中文行、英文行都存在）—— 决定"长按要不要调翻译"。 */
internal fun isBilingual(text: String): Boolean {
    val valid = text.split('\n').filter { it.isNotBlank() }
    if (valid.size <= 1) return false
    var hasCn = false
    var hasEn = false
    for (l in valid) if (hasHan(l)) hasCn = true else hasEn = true
    return hasCn && hasEn
}

/**
 * 取这句的**英文部分**（给"整句加生词本"用）。
 * 双语 ⇒ 只返回英文行（阿亮：只加英文不加中文）；本来就没中文 ⇒ 原样返回。
 */
internal fun englishOnly(text: String): String {
    val valid = text.split('\n').filter { it.isNotBlank() }
    if (valid.size <= 1) return text
    val en = valid.filterNot { hasHan(it) }
    return if (en.isEmpty()) text else en.joinToString(" ")
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
