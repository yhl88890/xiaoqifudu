package com.xuanji.repeater

import android.content.Context
import android.content.SharedPreferences

/**
 * 复习页设置（0916 新增）：自动发音 开/关 + 用哪个口音 + 每轮多少个 + 上次练完一轮的时间。
 *
 * 【为什么单独一个 prefs 文件】复习这块的偏好跟播放器、ASR、忽略列表都不是一回事，
 * 混进别人的 prefs 里以后找起来费劲。就这么几条，独立文件最省事。
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

    /**
     * 「上一次**走完一整轮**是什么时候」。
     *
     * 🔴【0919 新增】生词提醒的"今天做完了"判据 —— 见 [VocabReminder.doneRoundToday]。
     * 老口径是读 `vocab.json` 里最大的 `lastReviewedAt`（= 答过任意一张卡就算），
     * 阿亮报"我只是打开一下、没把该复习的复习完，这算一次吗"⇒ 太松。
     * 他的口径：**"我得正经复习完一轮，比如说我定的是 10 个，复习完这 10 个这才算"**。
     *
     * 所以"完成"这个事实**不可能**再从 `vocab.json` 反推出来（词条上没这个信息），
     * 必须由复习页在**一轮真正走完那一刻**（`ReviewPage.consumeAndAdvance` 里 `finished = true`）
     * 主动写一笔。
     *
     * ⚠️ 为什么不在 `VocabStore.markReviewed` 里写：那个函数是"答完一张卡"，
     *    一轮有 10~100 张 ⇒ 写在那儿等于把老口径换个地方实现一遍，白改。
     */
    private const val KEY_ROUND_DONE = "last_round_done_at"

    /** 口音取值，跟 [Pronounce.speak] 的 accent 参数一致 */
    const val ACCENT_US = "us"
    const val ACCENT_UK = "uk"

    /**
     * 每轮复习多少个。
     * 【为什么范围是 10~100】他原话："有些人像我这种可能只学 10 个，但有些人可能学 100 个"。
     * 只给 10/20/30/40/50 不够用，词库大的人一轮要来回点好几次「再来一轮」。
     * 注意 100 档对短句卡片来说一轮挺长，但那是用户自己的选择，别去限制他。
     *
     * 【0917 第二轮：从设置页搬到复习页顶栏那个滑杆】所以这组常量现在是给
     * [ReviewPage] 的滑杆定范围的，别再塞回设置页做 chips ——
     * 他明确说"放设置里不合理"，而且**到复习页才想起来要调**，来回切页很难受。
     */
    const val BATCH_MIN = 10
    const val BATCH_MAX = 100

    /** 滑杆步长：5 ⇒ 10/15/…/100 共 19 档。倍数 10 都够得着，中间值也不至于太滑不动。 */
    const val BATCH_STEP = 5

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

    /**
     * 刚走完**一整轮**（一路做到结算页，错题也补完了）⇒ 记一笔。
     *
     * 🔴 **实际调用点只有一处**：`VocabReminder.onRoundDone`（它同时会撤掉通知栏那条），
     * 而它又只被 `ReviewPage.consumeAndAdvance` 里 `finished = true` 那个分支调用。
     * 别加到 `markReviewed`（答完单张）或"开始复习"上 —— 那正是 0917 那个太松的口径。
     */
    fun markRoundDone(ctx: Context) =
        sp(ctx).edit().putLong(KEY_ROUND_DONE, System.currentTimeMillis()).apply()

    /**
     * 上次走完一整轮的**时间戳**（0 = 从来没走完过）。
     * ⚠️ 返回原始时间戳而不是 Boolean："今天"这个边界由调用方拿自己的 [todayStart] 去判，
     *    prefs 这一层只管存数（跟这个文件里其它 load/save 一个风格）。
     */
    fun loadRoundDoneAt(ctx: Context): Long = sp(ctx).getLong(KEY_ROUND_DONE, 0L)
}
