package com.xuanji.repeater

import android.content.Context
import android.content.SharedPreferences

/**
 * 在线语音识别配置：服务商 + API Key。
 * 🔴【v44】**服务商这一项不只管识别** —— 字幕翻译的地址、长句发音的地址/模型/音色
 *   全都由它推出来（见 [AsrProvider]）。所以这里存的是"整套通道选哪家"。
 * Key 仅存本机 SharedPreferences，不会上传到除所选服务商以外的任何地方。
 */
object AsrPrefs {
    private const val NAME = "asr_prefs"
    private const val KEY_PROVIDER = "provider"
    private const val KEY_KEY = "api_key"
    /** v44 之前只有一个翻译模型键（当时只支持硅基流动）。留这个名字只为做一次迁移。 */
    private const val KEY_TRANSLATE_MODEL_LEGACY = "translate_model"
    private const val KEY_BILI_SESSDATA = "bili_sessdata"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** 服务商名：siliconflow / groq / openai（对应 AsrProvider.name）。默认硅基流动（国内直连、免信用卡） */
    fun loadProvider(ctx: Context): String =
        sp(ctx).getString(KEY_PROVIDER, AsrProvider.SILICONFLOW.name) ?: AsrProvider.SILICONFLOW.name

    fun saveProvider(ctx: Context, name: String) =
        sp(ctx).edit().putString(KEY_PROVIDER, name).apply()

    /** 用户填写的 API Key（明文存本机，仅用于向识别服务商鉴权） */
    fun loadKey(ctx: Context): String = sp(ctx).getString(KEY_KEY, "") ?: ""

    fun saveKey(ctx: Context, key: String) =
        sp(ctx).edit().putString(KEY_KEY, key.trim()).apply()

    /** 翻译模型按服务商分开存，键名形如 `translate_model_GROQ` */
    private fun translateKey(p: AsrProvider): String = "translate_model_" + p.name

    /**
     * 字幕翻译模型 —— **一家一套，互不通用**。
     *
     * 🔴【v44】硅基流动是 `Qwen/Qwen2.5-7B-Instruct`，Groq 是 `openai/gpt-oss-120b`，
     *   OpenAI 是 `gpt-4o-mini`。共用一个键的话，切了服务商还在拿别家的模型名发请求 ⇒
     *   400 ⇒ 而 400 **不算"排队"** ⇒ 兜底不触发 ⇒ **翻译静默变空，界面上看不出来**。
     *   ⇒ 分键存 + 读出来先验"这个 id 在不在当前这家的模型表里"，不在就回落这家的默认。
     *
     * 🔴【0918】`getString` 的默认值**只在"键不存在"时生效** —— 旧版手填框被清空会把**空串**存进来，
     *   读出来也是空串。所以这里一律用 `isNullOrBlank` 判，**保证返回值是个合法模型 id**。
     */
    fun loadTranslateModel(ctx: Context, p: AsrProvider): String {
        val s = sp(ctx)
        val saved = s.getString(translateKey(p), null)
        if (!saved.isNullOrBlank() && SubtitleTranslator.Model.isValidFor(p, saved)) return saved
        // 一次性迁移：老版本只有一个 translate_model 键，那会儿翻译只有硅基流动一家
        if (p == AsrProvider.SILICONFLOW) {
            val old = s.getString(KEY_TRANSLATE_MODEL_LEGACY, null)
            if (!old.isNullOrBlank() && SubtitleTranslator.Model.isValidFor(p, old)) {
                s.edit().putString(translateKey(p), old).remove(KEY_TRANSLATE_MODEL_LEGACY).apply()
                return old
            }
        }
        return SubtitleTranslator.Model.defaultFor(p).id
    }

    fun saveTranslateModel(ctx: Context, p: AsrProvider, v: String) =
        sp(ctx).edit().putString(translateKey(p), v.trim()).apply()

    /** B站登录 SESSDATA（可选）：部分视频的字幕需登录才能取，留空走游客 cookie。 */
    fun loadBiliSessdata(ctx: Context): String = sp(ctx).getString(KEY_BILI_SESSDATA, "") ?: ""

    fun saveBiliSessdata(ctx: Context, v: String) =
        sp(ctx).edit().putString(KEY_BILI_SESSDATA, v.trim()).apply()
}
