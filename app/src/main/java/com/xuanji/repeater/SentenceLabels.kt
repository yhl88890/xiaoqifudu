package com.xuanji.repeater

/** 无文本（纯波形断句、无歌词）时给句子加阿拉伯数字序号标签：第1句 / 第2句 / … */
fun sentenceNumberLabel(index: Int): String {
    val n = (index + 1).coerceAtLeast(1)
    return "第${n}句"
}

/** 无文本段落右侧的小字提示：引导用户填写识别 API 以获取真实歌词 */
const val SENTENCE_NO_LYRIC_HINT = "（填写识别 API 可自动生成字幕）"
