package com.xuanji.repeater

import android.content.Context
import android.content.SharedPreferences

/**
 * 发音通道设置（0917 新增）。
 *
 * 【为什么要这个设置】录发音有两条路，各有各的强项：
 *  - **有道人声**（`dict.youdao.com/dictvoice`）：真人音，好听。但①要联网 ②**美音约 15 字符、
 *    英音约 45~68 字符就是上限**，超了直接 HTTP 500，整句彻底没声。
 *  - **本地 TTS**（系统 `TextToSpeech`）：合成音，差一些。但**不挑长度、不用联网**，
 *    而且以后能开语速做慢速跟读。
 *
 * ⇒ 默认「自动」：短的走真人有道，**超长句自动落回本地 TTS**（不然就是哑的）。
 * ⇒ 想完全离线（地铁里、流量紧）就切「只用本地发音」。
 *
 * 【默认为什么是 auto】绝大多数场景下有道音质更好，只有在它读不了的时候才该换人。
 */
object SpeakPrefs {

    private const val NAME = "speak_prefs"
    private const val KEY_SOURCE = "source"

    /** 自动：短的走有道人声，超过有道上限的长句自动交给本地 TTS */
    const val SOURCE_AUTO = "auto"
    /** 只用本地 TTS：完全不联网 */
    const val SOURCE_TTS = "tts"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun loadSource(ctx: Context): String =
        sp(ctx).getString(KEY_SOURCE, SOURCE_AUTO) ?: SOURCE_AUTO

    fun saveSource(ctx: Context, v: String) =
        sp(ctx).edit().putString(KEY_SOURCE, v).apply()
}
