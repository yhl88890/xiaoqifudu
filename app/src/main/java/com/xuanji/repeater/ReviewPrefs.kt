package com.xuanji.repeater

import android.content.Context
import android.content.SharedPreferences

/**
 * 复习页设置（0916 新增）：自动发音 开/关 + 用哪个口音。
 *
 * 【为什么单独一个 prefs 文件】复习这块的偏好跟播放器、ASR、忽略列表都不是一回事，
 * 混进别人的 prefs 里以后找起来费劲。就一条开关 + 一个口音，独立文件最省事。
 *
 * 【默认值为什么是"关 + 美音"】
 *  - 默认关：发音是要联网下载音频的（有道接口），有人可能在流量下用，
 *    不打招呼就出声会让人措手不及 ⇒ 让用户自己开一次。
 *  - 默认美音：他平时听的教材 / 美剧基本都是美音（他自己也说过常看美剧练听力）。
 */
object ReviewPrefs {

    private const val NAME = "review_prefs"
    private const val KEY_AUTO_SPEAK = "auto_speak"
    private const val KEY_ACCENT = "accent"
    private const val KEY_BATCH = "batch_size"

    /** 口音取值，跟 [Pronounce.speak] 的 accent 参数一致 */
    const val ACCENT_US = "us"
    const val ACCENT_UK = "uk"

    /**
     * 每轮复习多少个（0917 阿亮要求可调）。
     * 【为什么可选值跨度要拉这么开】他原话："有些人像我这种可能只学 10 个，但有些人可能学 100 个"。
     * ⇒ 只给 10/20/30/40/50 不够用，词库大的人一轮要来回点好几次「再来一轮」。
     * 注意 100 档对短句卡片来说一轮挺长，但那是用户自己的选择，别去限制他。
     */
    val BATCH_CHOICES = listOf(10, 20, 30, 50, 100)

    /** 默认 20：够一次碎片时间的量，太小要反复点开始、太大一轮走不完 */
    const val BATCH_DEFAULT = 20

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** 复习时自动朗读当前这张卡 */
    fun loadAutoSpeak(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_AUTO_SPEAK, false)

    fun saveAutoSpeak(ctx: Context, on: Boolean) =
        sp(ctx).edit().putBoolean(KEY_AUTO_SPEAK, on).apply()

    fun loadAccent(ctx: Context): String =
        sp(ctx).getString(KEY_ACCENT, ACCENT_US) ?: ACCENT_US

    fun saveAccent(ctx: Context, accent: String) =
        sp(ctx).edit().putString(KEY_ACCENT, accent).apply()

    /**
     * 每轮复习多少个。
     * ⚠️ `takeIf { it in 1..500 }` 是防脏数据的：读到一个 0 或者负数会让
     * `take(batchSize)` 直接拿到空队列 ⇒ 开场就"没东西可练"，还很难查。
     */
    fun loadBatchSize(ctx: Context): Int =
        sp(ctx).getInt(KEY_BATCH, BATCH_DEFAULT).takeIf { it in 1..500 } ?: BATCH_DEFAULT

    fun saveBatchSize(ctx: Context, n: Int) =
        sp(ctx).edit().putInt(KEY_BATCH, n).apply()
}
