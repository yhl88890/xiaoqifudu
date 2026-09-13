package com.xuanji.repeater

import android.content.Context
import android.content.SharedPreferences

/**
 * 在线语音识别配置：服务商 + API Key。
 * Key 仅存本机 SharedPreferences，不会上传到除识别服务商以外的任何地方。
 */
object AsrPrefs {
    private const val NAME = "asr_prefs"
    private const val KEY_PROVIDER = "provider"
    private const val KEY_KEY = "api_key"
    private const val KEY_TRANSLATE_MODEL = "translate_model"
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

    /** 字幕翻译模型（硅基流动 chat 模型）。默认 Qwen/Qwen2.5-7B-Instruct；排队时自动切 HY3。 */
    fun loadTranslateModel(ctx: Context): String =
        sp(ctx).getString(KEY_TRANSLATE_MODEL, "Qwen/Qwen2.5-7B-Instruct")
            ?: "Qwen/Qwen2.5-7B-Instruct"

    fun saveTranslateModel(ctx: Context, v: String) =
        sp(ctx).edit().putString(KEY_TRANSLATE_MODEL, v.trim()).apply()

    /** B站登录 SESSDATA（可选）：部分视频的字幕需登录才能取，留空走游客 cookie。 */
    fun loadBiliSessdata(ctx: Context): String = sp(ctx).getString(KEY_BILI_SESSDATA, "") ?: ""

    fun saveBiliSessdata(ctx: Context, v: String) =
        sp(ctx).edit().putString(KEY_BILI_SESSDATA, v.trim()).apply()
}
