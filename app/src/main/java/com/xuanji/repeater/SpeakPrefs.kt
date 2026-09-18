package com.xuanji.repeater

import android.content.Context
import android.content.SharedPreferences

/**
 * 发音通道设置（0917 新增，v44 改成「跟着识别服务商走」）。
 *
 * 【为什么要这个设置】录发音有几条路，各有各的强项：
 *  - **有道人声**（`dict.youdao.com/dictvoice`）：真人音，好听。但①要联网 ②**美音约 15 字符、
 *    英音约 45~68 字符就是上限**，超了直接 HTTP 500，整句彻底没声。
 *  - **在线 TTS**（v21 起是长句主力）：合成音，**不挑长度**（Groq 例外，见下），音色可选。
 *    地址 / 模型 / 音色表全部来自当前选的 [AsrProvider]，共用「识别」那把 key。
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

    /** v44 之前只有一个音色键（当时只支持硅基流动）。留这个名字只为做一次迁移。 */
    private const val KEY_SF_VOICE_LEGACY = "sf_voice"

    /** 自动：短的走有道人声，超过有道上限的长句自动交给在线 TTS */
    const val SOURCE_AUTO = "auto"
    /** 只用本地 TTS：完全不联网 */
    const val SOURCE_TTS = "tts"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun loadSource(ctx: Context): String =
        sp(ctx).getString(KEY_SOURCE, SOURCE_AUTO) ?: SOURCE_AUTO

    fun saveSource(ctx: Context, v: String) =
        sp(ctx).edit().putString(KEY_SOURCE, v).apply()

    /** 音色按服务商分开存，键名形如 `voice_GROQ` */
    private fun voiceKey(p: AsrProvider): String = "voice_" + p.name

    /**
     * 长句朗读用哪个音色 —— **一家一套，互不通用**。
     *
     * 🔴【v44 为什么必须分家】硅基流动是 `anna/alex…`，Groq 是 `autumn/troy…`，OpenAI 是 `nova/onyx…`。
     *   共用一个键的话，用户从 Grove 切回硅基流动时，存着的还是 Groq 的音色 id ⇒
     *   接口直接 4xx ⇒ **长句静默变哑**（就是阿亮最烦的那种"看着没事其实坏了"）。
     *   ⇒ 分键存 + 读出来先验一下"这个 id 在不在当前这家的音色表里"，不在就回落默认。
     *
     * ⚠️ 返回值**保证是当前这家音色表里的合法 id**，调用方不用再校验。
     */
    fun loadVoice(ctx: Context, p: AsrProvider): String {
        val s = sp(ctx)
        val saved = s.getString(voiceKey(p), null)
        if (!saved.isNullOrBlank() && p.ttsVoices.any { it.first == saved }) return saved
        // 一次性迁移：老版本只有一个 sf_voice 键，那会儿在线 TTS 只有硅基流动一家
        if (p == AsrProvider.SILICONFLOW) {
            val old = s.getString(KEY_SF_VOICE_LEGACY, null)
            if (!old.isNullOrBlank() && p.ttsVoices.any { it.first == old }) {
                s.edit().putString(voiceKey(p), old).remove(KEY_SF_VOICE_LEGACY).apply()
                return old
            }
        }
        return p.defaultTtsVoice
    }

    fun saveVoice(ctx: Context, p: AsrProvider, id: String) =
        sp(ctx).edit().putString(voiceKey(p), id).apply()
}
