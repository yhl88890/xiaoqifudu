package com.xuanji.repeater

import android.content.Context
import android.content.SharedPreferences

/**
 * 发音通道设置（0917 新增）。
 *
 * 【为什么要这个设置】录发音有几条路，各有各的强项：
 *  - **有道人声**（`dict.youdao.com/dictvoice`）：真人音，好听。但①要联网 ②**美音约 15 字符、
 *    英音约 45~68 字符就是上限**，超了直接 HTTP 500，整句彻底没声。
 *  - **硅基流动 TTS**（`api.siliconflow.cn/v1/audio/speech`，v21 起是长句主力）：
 *    合成音，但**不挑长度**，音色可选（见 [SF_VOICES]）。共用「识别」那把 key。
 *  - **本地 TTS**（系统 `TextToSpeech`）：离线，但国产 ROM 常没英文语音包（会静默失败）。
 *
 * ⇒ 默认「自动」：短的走真人有道，**超过有道上限的长句走在线 TTS**（不然就是哑的）。
 * ⇒ 想完全离线（地铁里、流量紧）就切「只用本地发音」。
 *
 * 【默认为什么是 auto】绝大多数场景下有道音质更好，只有在它读不了的时候才该换人。
 */
object SpeakPrefs {

    private const val NAME = "speak_prefs"
    private const val KEY_SOURCE = "source"
    private const val KEY_SF_VOICE = "sf_voice"

    /** 自动：短的走有道人声，超过有道上限的长句自动交给在线 TTS */
    const val SOURCE_AUTO = "auto"
    /** 只用本地 TTS：完全不联网 */
    const val SOURCE_TTS = "tts"

    /**
     * 长句朗读用哪个音色（v21）。
     * 🔴 这些是**硅基流动 CosyVoice2 的预置音色**，用「识别」里那把 key 调 `/v1/audio/speech`。
     * 列表 = id 到中文标签，顺序就是设置页上的显示顺序。想加/改音色**先核对官方文档**，
     * 模型名和音色名写错接口会直接 4xx（然后长句就掉到"有道切块"那道兜底了，他不会知道原因）。
     */
    val SF_VOICES = listOf(
        "anna" to "沉稳女声",
        "bella" to "热情女声",
        "claire" to "温柔女声",
        "diana" to "活泼女声",
        "alex" to "沉稳男声",
        "benjamin" to "低沉男声",
        "charles" to "磁性男声",
        "david" to "阳光男声",
    )

    const val SF_VOICE_DEFAULT = "anna"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun loadSource(ctx: Context): String =
        sp(ctx).getString(KEY_SOURCE, SOURCE_AUTO) ?: SOURCE_AUTO

    fun saveSource(ctx: Context, v: String) =
        sp(ctx).edit().putString(KEY_SOURCE, v).apply()

    /** 长句音色。存的是音色 id（anna / alex …），拼到 `模型名:音色` 里用 */
    fun loadSfVoice(ctx: Context): String =
        sp(ctx).getString(KEY_SF_VOICE, SF_VOICE_DEFAULT) ?: SF_VOICE_DEFAULT

    fun saveSfVoice(ctx: Context, v: String) =
        sp(ctx).edit().putString(KEY_SF_VOICE, v).apply()
}
