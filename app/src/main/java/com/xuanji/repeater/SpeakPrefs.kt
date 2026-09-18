package com.xuanji.repeater

import android.content.Context
import android.content.SharedPreferences

/**
 * 发音**音色**设置（0917 新增，v44 改成「跟着识别服务商走」）。
 *
 * 🔴🔴【0919 阿亮拍板删掉「朗读用哪个声音」那一项】这里为什么只剩音色：
 *   原来那一项是「自动 / 只用本地发音」两个选项，他质疑"没什么必要"，我核了代码，**他对**：
 *     · 「只用本地发音」**名不副实** —— 系统 TTS 读不出来时它会照常联网（在线合成 / 有道切块）。
 *       那个兜底是修 0917「选了它就彻底没声」那个 bug 时加的，功能是救回来了，
 *       但这个开关承诺的"离线"也就没了 ⇒ 真到了没网/不想联网的场景，它恰恰保证不了。
 *     · 对「本地 TTS 用不了」的机器（阿亮真机就是），它 **100% 等价于「自动」**。
 *     · ⇒ 它不是"第二种声音"，只是"在自动链路前面插一道先试本地"。
 *   ⇒ 发音**没有开关了**，怎么走在 [Pronounce.speakInternal] 里写死；解释搬进 App 内
 *     「软件介绍 → 朗读是怎么发音的」。**别再加类似的开关**，加之前先想清楚它承诺什么。
 *   ⚠️ 老版本存档里可能还留着 `source` 键，已不读、无害 —— **别为它写迁移代码**。
 *
 * 剩下这个「音色」只作用于**在线合成**那条通道：
 *  - **有道人声**（`dict.youdao.com/dictvoice`）：真人音，但**美音约 15 字符 / 英音约 45~68 字符
 *    就是上限**，超了直接 HTTP 500。它自带英美音两套，**不受这里选的音色影响**。
 *  - **在线 TTS**（v21 起是长句主力）：合成音、不挑长度（Groq 例外），**这里选的音色就是给它用的**。
 *    端点 / 模型 / 音色表全部来自当前 [AsrProvider]，共用「识别」那把 key。
 *  - **本地 TTS**（系统 `TextToSpeech`）：离线，嗓子是手机自带的，**也跟这里选的音色无关**。
 *  ⇒ 所以"有时候音色不对"= 长句没走成在线合成、掉到后两道去了（详见 `HANDBOOK.md`）。
 */
object SpeakPrefs {

    private const val NAME = "speak_prefs"

    /** v44 之前只有一个音色键（当时只支持硅基流动）。留这个名字只为做一次迁移。 */
    private const val KEY_SF_VOICE_LEGACY = "sf_voice"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

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
