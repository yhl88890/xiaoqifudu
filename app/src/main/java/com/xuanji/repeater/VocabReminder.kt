package com.xuanji.repeater

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.io.File

/**
 * 生词提醒（0917 新增）：**定时在通知栏换一个还没掌握的生词**。
 *
 * 【它其实是个"每日任务"—— 0917 下午阿亮定的口径】
 * 一开始写成"没词就不发"，他打开开关看到「已开启，但生词本里暂时没有要复习的词」，
 * 于是一条通知都没有 ⇒ 在他看来等于"这开关是坏的"。他要的是**每日任务**：
 *   - **有没有词都要提醒**（生词本空的也要提一句"先去存几个词"）；
 *   - **只有"今天已经练完一整轮"才闭嘴**，安静到第二天。
 *      🔴【0919 收紧】以前是"复习过任意一张卡"就闭嘴 —— 阿亮报："我只是打开了一下，
 *      没把该复习的复习完，这也算一次？**我得正经复习完一轮，比如说我定的 10 个，
 *      复习完这 10 个这才算**。" ⇒ 判据换成 [doneRoundToday]。
 * 所以现在 [showNext] 有三个分支，其中两个是"发"，一个是"不发"。别改回"没词就 return"。
 *
 * 【为什么用 AlarmManager 而不是 WorkManager】
 * WorkManager 要加一个依赖，而这里的需求就是"隔一阵子换一条通知"，用系统闹钟完全够。
 * 代价是要自己处理"闹钟丢了"的三种情况：开机、装新版 APK、被系统清掉
 * —— 分别见 [VocabReminderReceiver] 和 [healIfNeeded]。
 *
 * 【为什么不用前台服务】
 * Android 14 起前台服务**必须声明类型**（mediaPlayback / dataSync / …），
 * 而"背单词提醒"不属于任何一类，硬塞会被系统警告甚至杀掉。
 * 用闹钟 + 普通通知就绕开了整件事，而且还省电。
 *
 * 【设计上的几个"别改"】
 * - 渠道 IMPORTANCE_LOW：**不响不震**。这是"瞄一眼"用的，不是催命符。
 * - 所有通知共用 [NOTIF_ID]：更新同一条，**不在通知栏堆一大排**。
 *   顺带一个好处 —— 用户不划掉的话，那条通知一直是"当前那个词"，天然就是"轮播"。
 * - 用 `ELAPSED_REALTIME`（**不加 _WAKEUP**）：设备睡着时就不发，醒来补发。
 *   静音通知没必要把手机唤醒，那样费电又没意义。
 * - **挑词的池子是"全本"（含已掌握的）**，见 [readPool] —— 0919 改的。
 *   以前排除已掌握 ⇒ 他词少，全练熟后池子永久为空 ⇒ 通知栏**永远只发一句中文引导**、
 *   一个单词都弹不出来。他的原话："没有我也要弹啊……没有也要按照某个复习的顺序往外弹单词"。
 * - **挑词顺序跟复习页同一把尺子**（`VocabStore.lastTouchAt`），0919 改的。见 [pickFrom]。
 */
object VocabReminder {

    const val CHANNEL_ID = "vocab_reminder"
    const val NOTIF_ID = 4002
    const val ALARM_REQ = 4002

    /** 广播 action：闹钟到点了 */
    const val ACTION_TICK = "com.xuanji.repeater.VOCAB_TICK"

    /** 点通知带来的 extra，MainActivity 收到就跳到复习页 */
    const val EXTRA_GOTO_REVIEW = "goto_review"

    /**
     * [setOn] 的结果。设置页拿它决定 Toast 说什么
     * —— 以前只返回 Boolean，于是"没权限"和"今天做完了"都只能报同一句话，说不清。
     */
    enum class Result {
        /** 真发出去了 */
        Posted,

        /** 开着，但今天已经练完一整轮 ⇒ 今天不再提醒（明天照旧） */
        DoneToday,

        /** 开着，但系统没给通知权限，发不出去 */
        NoPermission,

        /** 关掉了 */
        Off,
    }

    private const val NAME = "vocab_remind_prefs"
    private const val KEY_ON = "on"
    private const val KEY_MIN = "interval_min"
    private const val KEY_IDX = "rotate_idx"

    /** 「下次该响的墙钟毫秒」。**只当"闹钟是不是被清掉了"的探针用**，不参与真正排程。 */
    private const val KEY_DUE = "due_at"

    /**
     * 「本次开机的墙钟起点」= `now - elapsedRealtime`。
     *
     * 🔴 这个键是**判断 BOOT_COMPLETED 是不是真重启**的唯一依据，别删。
     * 实测（模拟器 Android 15）：`BOOT_COMPLETED` 会被系统**反复补发** ——
     * 装完 APK 补发、甚至**每次 App 进程启动都补发**（连开 3 次，3 次都收到）。
     * 所以"收到 BOOT_COMPLETED 就重排"是错的：倒计时会每次开 App 都被顶后，等于永远不提醒。
     * 而真·重启会让 `now - elapsedRealtime` 跳成新的开机时刻 ⇒ 用它就能区分开。
     */
    private const val KEY_BOOT = "boot_wall"

    /**
     * 自愈容差。不精确闹钟被系统批处理往后拖几分钟是常态，不能一超时就当它死了。
     * 60 秒只用来吸掉"刚好在边界上"的抖动，真正的失效是"过期好几分钟/几天"。
     * 顺带也当"开机起点变了没有"的容差 —— 用户手动改系统时间超过 1 分钟时会被当成重启，
     * 那只导致多排一次闹钟，无害。
     */
    private const val HEAL_SLACK = 60_000L

    /**
     * 间隔档位（分钟）。
     * 最短就是 15 —— 再密就成骚扰了，而且系统对重复闹钟本来也会做批处理。
     * 120 = 两小时，适合"我白天在忙，隔一阵提醒我看一眼"。
     */
    val INTERVAL_CHOICES = listOf(15, 30, 60, 120)

    const val INTERVAL_DEFAULT = 60

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // ---------- 设置 ----------

    fun isOn(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_ON, false)

    /**
     * 开关。开 = 立刻排程，**并马上发第一条**（不用干等一个间隔，开了就能在通知栏看到效果）。
     *
     * @return 见 [Result]。设置页拿这个返回值给提示 —— 否则"开了但没反应"没法解释。
     */
    fun setOn(ctx: Context, on: Boolean): Result {
        sp(ctx).edit().putBoolean(KEY_ON, on).apply()
        if (!on) {
            cancel(ctx)
            return Result.Off
        }
        schedule(ctx)
        return when {
            !hasNotifPermission(ctx) -> Result.NoPermission
            doneRoundToday(ctx) -> Result.DoneToday
            showNext(ctx) -> Result.Posted
            else -> Result.NoPermission
        }
    }

    fun loadInterval(ctx: Context): Int =
        sp(ctx).getInt(KEY_MIN, INTERVAL_DEFAULT)
            .takeIf { it in INTERVAL_CHOICES } ?: INTERVAL_DEFAULT

    /** 改间隔 ⇒ 重新排程。已经开着的话按新间隔重来。 */
    fun saveInterval(ctx: Context, minutes: Int) {
        sp(ctx).edit().putInt(KEY_MIN, minutes).apply()
        if (isOn(ctx)) schedule(ctx)
    }

    // ---------- 排程 ----------

    private fun tickIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx,
        ALARM_REQ,
        Intent(ctx, VocabReminderReceiver::class.java).setAction(ACTION_TICK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** 「本次开机的墙钟起点」。重启后才变，用来识别"真的重启了"。 */
    private fun bootMark(): Long =
        System.currentTimeMillis() - SystemClock.elapsedRealtime()

    /**
     * 无条件重排（把倒计时**推到"现在 + 一个间隔"**）。
     * 只在"确定闹钟没了"或"用户刚改设置"的时候调 —— 别在每次启动时调，否则永远不提醒。
     */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val ms = loadInterval(ctx) * 60_000L
        am.cancel(tickIntent(ctx)) // 先取消旧的，避免改间隔后两条并存
        runCatching {
            am.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + ms,
                ms,
                tickIntent(ctx),
            )
        }.onSuccess {
            // 记"该响时间"用**墙钟**而不是 elapsedRealtime：墙上时钟能跨重启比较，
            // 而 elapsedRealtime 一重启就归零 ⇒ 重启后那个旧值永远"在未来"，自愈就失灵了。
            sp(ctx).edit()
                .putLong(KEY_DUE, System.currentTimeMillis() + ms)
                .putLong(KEY_BOOT, bootMark())
                .apply()
        }
    }

    /** 闹钟响过一次：把"该响时间"往后推一格，让 [healIfNeeded] 知道轮播还活着。 */
    fun markFired(ctx: Context) {
        val ms = loadInterval(ctx) * 60_000L
        sp(ctx).edit().putLong(KEY_DUE, System.currentTimeMillis() + ms).apply()
    }

    /**
     * 🔴 **"确保闹钟是活着的" —— 唯一的自愈入口，别删、也别再写第二个。**
     *
     * 只在"确实需要重排"时才动手，判据有三个（任一命中就重排）：
     *  1. **重启过**（开机墙钟起点变了）⇒ 闹钟肯定没了；
     *  2. **过期没响**（该响时间早就过了）⇒ 被系统/ROM 清掉了，或装新版包清掉了；
     *  3. 从没排过（due 不存在）。
     *
     * ⚠️ **绝对不要改成"不判断就直接重排"**。踩过的坑：
     *   - App 启动时无条件重排 ⇒ 他频繁开 App 就永远等不到提醒；
     *   - 收到 BOOT_COMPLETED 就重排 ⇒ 同上，因为那个广播**每次进程启动都会被补发**（实测）。
     * 正常情况下每次 [markFired] 都会刷新 due，所以这里不会命中、也就不会顶后倒计时。
     *
     * 调用点：MainActivity.onCreate、以及接收器里的 BOOT_COMPLETED。
     */
    fun healIfNeeded(ctx: Context) {
        if (!isOn(ctx)) return
        val due = sp(ctx).getLong(KEY_DUE, 0L)
        val oldBoot = sp(ctx).getLong(KEY_BOOT, 0L)
        val rebooted = oldBoot <= 0L ||
            kotlin.math.abs(bootMark() - oldBoot) > HEAL_SLACK
        if (rebooted || due <= 0L || System.currentTimeMillis() > due + HEAL_SLACK) {
            schedule(ctx)
        }
    }

    /** 关掉开关 / 生词本清空时调用：取消闹钟 + 撤掉通知栏那条 */
    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        am?.cancel(tickIntent(ctx))
        cancelNotification(ctx)
    }

    /** 只撤通知、**不动闹钟**：他刚复习完、或今天任务已完成时用。 */
    private fun cancelNotification(ctx: Context) {
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(NOTIF_ID)
    }

    /**
     * 他刚**走完一整轮**（一路做到结算页、错题也补完了）⇒ 今天的"每日任务"完成。
     *
     * 做两件事：
     *  ① `ReviewPrefs.markRoundDone` —— 记下"上次走完一整轮的时间"，[doneRoundToday] 靠它判；
     *  ② 撤掉通知栏那条提醒。
     *
     * **不动闹钟**：明天 0 点之后 [doneRoundToday] 自然变回 false，轮播自己就活了，不需要重新排程。
     *
     * 🔴 调用点只有一处：`ReviewPage.consumeAndAdvance` 里 `finished = true` 那个分支。
     * ⚠️ **别挂到 `VocabStore.markReviewed` 上** —— 那是"答完一张卡"，
     *    挂上去就等于 0917 那个太松的老口径复活（阿亮 0919 亲口否掉的）。
     */
    fun onRoundDone(ctx: Context) {
        ReviewPrefs.markRoundDone(ctx)
        if (isOn(ctx)) cancelNotification(ctx)
    }

    // ---------- 发通知 ----------

    /**
     * 🔴 **有没有词都要发**（0917 下午阿亮改的口径）—— 这是这个函数唯一要记住的事。
     *
     * 三种情况：
     *  ① **今天已经练完一整轮** ⇒ 不发，并且把上次那条撤掉
     *     （他都练完了，通知栏还挂着提醒就是误导）。安静到明天。
     *  ② 生词本里有词 ⇒ 轮播一个（[pickFrom]）。
     *  ③ **生词本一条都没有** ⇒ **也发**，文案改成引导他去存词。
     *
     * ⚠️ 以前 ③ 是 `return false` 直接静默，他打开开关一条都收不到 ⇒ 认定功能坏了。
     *
     * 🔴🔴【0919 改了两处口径，都是阿亮报的】
     *  1. **① 的判据**从"今天复习过任意一张卡"改成"今天**走完一整轮**" —— 见 [doneRoundToday]。
     *     老口径下他只要进复习页答一张就当天闭嘴，可他又没把该复习的复习完。
     *  2. **② 的池子**从"只挑没掌握的"改成"全本" —— 见 [readPool]。
     *     老口径下词全练熟后池子永久为空 ⇒ 只弹一句中文，一个单词都没有。
     *
     * @return true = 真进通知栏了；false = 今天已完成 / 没权限 / 被系统挡了
     */
    fun showNext(ctx: Context): Boolean {
        // ① 今天的每日任务已完成（= 走完了一整轮）
        if (doneRoundToday(ctx)) {
            cancelNotification(ctx)
            return false
        }

        // ② 有词：轮播
        pickFrom(ctx, readPool(ctx))?.let { w -> return notify(ctx, w.text, w.cn, w.detail()) }

        // ③ 一条词都没有：也要提醒。
        // ⚠️ 池子现在是**全本**，所以"池子空"基本等价于"生词本 0 条"
        //    （唯一例外是所有条目的 text 都为空，那种数据产生不出来 ⇒ readPool 里那句 continue 是防御）。
        return notify(ctx, "生词本还是空的", "先去听几段，把不认识的词收进来。")
    }

    /**
     * 通知栏里要展示的一条词。
     *
     * 为什么字段这么全（0919）：阿亮抱怨"**我到现在没看见过一个完整的单词展示**" ——
     * 因为老版通知 **title = 词、body = 中文**，而"展开看全文"（`BigTextStyle`）**只作用于 body**
     * ⇒ 把通知栏拉下来看详情，看到的**还是纯中文**，英文只有折叠态那一行标题（长了就省略号）。
     * ⇒ 现在 body 里也带上词和音标（[detail]）。
     */
    private class Word(
        val text: String,
        /** 中文：词性释义优先，退回整句翻译 */
        val cn: String,
        /** 音标，只有单词条目有；空串 = 没有 */
        val ipa: String,
        /** true = 还没掌握（连对次数 < [VocabEntry.MASTERED_AT]） */
        val pending: Boolean,
        /** 上次被碰过的时间，排序用的尺子 —— 见 `VocabStore.lastTouchAt` */
        val touch: Long,
    ) {
        /**
         * 展开通知时的全文：**词 + 音标**，换行，中文。
         * 折叠态显示的仍是 `title`(=词) + `contentText`(=中文)，跟以前一样；
         * 这一行只在拉下来时出现，所以跟标题重复一次是故意的（标题会被省略号吃掉）。
         */
        fun detail(): String {
            val head = if (ipa.isEmpty()) text else "$text  /$ipa/"
            return if (cn.isEmpty()) head else "$head\n$cn"
        }
    }

    /**
     * 把 `vocab.json` 读成一串 [Word]，**全部条目一条不落**。
     *
     * 🔴🔴【0919 改口径】以前这里把"已掌握"（连对满 `MASTERED_AT` 次）的词**排除**掉。
     * 阿亮词少，全练熟之后池子永久为空 ⇒ 通知栏**永远只发一句中文引导**、一个单词都弹不出来。
     * 他的原话：「**没有我也要弹啊**……没有也要按照某个复习的顺序往外弹单词」。
     * ⇒ 池子改成**全本**：没掌握的排前面，轮完了接着上已掌握的。
     * 这跟复习页「再练一轮」（`includeMastered = true`）是同一个口径 —— 他要的"跟复习逻辑统一"。
     *
     * 🔴 **直接读 `vocab.json`**，不走 `VocabStore`：
     * 广播接收器可能在进程刚起来、VocabStore 还没异步装填完的时候被调用，
     * 那时 `entries.value` 是空的，会误报"生词本是空的"。
     * 文件读取虽然笨，但在这种"后台被叫醒"的场景下反而最可靠。
     */
    private fun readPool(ctx: Context): List<Word> {
        val f = File(ctx.filesDir, "vocab.json")
        if (!f.exists()) return emptyList()

        val pool = ArrayList<Word>()
        runCatching {
            val arr = JSONObject(f.readText()).optJSONArray("entries") ?: return@runCatching
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val text = o.optString("text").trim()
                if (text.isEmpty()) continue

                // ⚠️ 必须先 `isNull` 再 `optString`：JSON 里的 null 用 optString 读出来是
                // **字面量 "null"**，不是空串 ⇒ 不挡就会往通知里写一个 "null"。
                // （`VocabStore.readFromFile` 里同样是这么写的）
                val s = { k: String -> if (o.isNull(k)) "" else o.optString(k).trim() }

                // 中文：词性释义优先（更全），没有就用整句翻译
                val cn = listOf("definition", "translation")
                    .map(s)
                    .firstOrNull { it.isNotEmpty() }
                    ?: "（还没查到释义，点开看看）"

                // 音标：美音优先（跟复习页默认口音一致）；`phonetic` 是升级前的老字段，兜一下
                val ipa = s("usphone").ifEmpty { s("ukphone") }.ifEmpty { s("phonetic") }

                pool.add(
                    Word(
                        text = text,
                        cn = cn,
                        ipa = ipa,
                        pending = o.optInt("correctStreak", 0) < VocabEntry.MASTERED_AT,
                        // 🔴 用跟复习页**同一个函数**算"上次碰过的时间"，别在这儿另写一套
                        touch = lastTouchAt(
                            lastReviewedAt = o.optLong("lastReviewedAt", 0L),
                            addedAt = o.optLong("addedAt", 0L),
                            id = o.optLong("id", 0L),
                        ),
                    )
                )
            }
        }
        return pool
    }

    /**
     * 今天有没有**正经练完一整轮**？
     *
     * 🔴🔴【0919 换掉的判据】老版本是"读 `vocab.json` 里最大的 `lastReviewedAt` 落在今天"
     * —— 也就是**答过任意一张卡就算**。阿亮的原话把问题说得很准：
     * 「**这个复习过一次要怎么界定？就是我只是打开了一下，但我没把该复习的全复习完，
     * 这个是不是也算一次？那我想要的就是，我得正经复习完一轮，比如说我定的是 10 个，
     * 那我复习完这 10 个，这才算**。那就不通知了，如果没有，那就还通知。」
     *
     * ⇒ 判据换成 `ReviewPrefs` 里由复习页在**一轮走完那一刻**写下的时间戳
     *   （`ReviewPage.consumeAndAdvance` → [onRoundDone]）。
     * ⚠️ 这个事实**推不出来**：词条上没有"这一轮做完了没"这种信息，只能由动作本身记一笔。
     */
    private fun doneRoundToday(ctx: Context): Boolean =
        ReviewPrefs.loadRoundDoneAt(ctx) >= todayStart()

    /** 今天 0 点（本地时区）。 */
    private fun todayStart(): Long {
        val c = java.util.Calendar.getInstance()
        c.set(java.util.Calendar.HOUR_OF_DAY, 0)
        c.set(java.util.Calendar.MINUTE, 0)
        c.set(java.util.Calendar.SECOND, 0)
        c.set(java.util.Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /**
     * 取下一个词。池子空就返回 null。
     *
     * 🔴🔴【0919 改顺序】以前就是一句 `pool[idx % pool.size]` —— 按 **`vocab.json` 的原始顺序**
     * 转圈（而那个顺序是"最新入库在最前"），跟复习页**毫无关系**。阿亮的原话：
     * 「**这个展示逻辑应该跟那个复习逻辑相对统一吧**。」
     *
     * 现在两件事对齐复习页：
     *  ① **没掌握的排前面**，轮完了才轮到已掌握的（≈ 复习页"先练不会的"）；
     *  ② 各自内部按 **`lastTouchAt` 升序** = **最久没碰过的先上**
     *     （就是复习页 `pickRound` 里 `sortedByDescending { waited }` 那把尺子，
     *      改动请改 `lastTouchAt` 那一个函数，别在这儿另写）。
     *
     * ⚠️ 每轮 tick 都会重新排一次（他复习完顺序就变了），而 [KEY_IDX] 只是个游标
     *    ⇒ 排完之后游标指的可能是另一条词。无所谓，反而更活：**顺序永远跟着最新的复习情况走**。
     *    但**轮转不能省** —— 否则"最久没碰"那条会被一直重复推，直到他真去复习它。
     */
    private fun pickFrom(ctx: Context, pool: List<Word>): Word? {
        if (pool.isEmpty()) return null
        val ordered = pool.filter { it.pending }.sortedBy { it.touch } +
            pool.filterNot { it.pending }.sortedBy { it.touch }
        val idx = sp(ctx).getInt(KEY_IDX, 0)
        val pick = ordered[idx % ordered.size]
        sp(ctx).edit().putInt(KEY_IDX, (idx + 1) % ordered.size).apply()
        return pick
    }

    private fun hasNotifPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * @param body 折叠态显示的那一行
     * @param bigText 拉下通知栏后显示的全文。0919 新增 —— 默认跟 [body] 一样，
     *   但**词卡那条要单独给**：title 是英文词、body 是中文，而 bigText 才是展开后的内容，
     *   以前三处都用 body ⇒ 展开看到的是纯中文，英文只有标题那一行（长了就被省略号吃掉）。
     * @return 真进通知栏了没有。没权限时**不要**假装成功（设置页的提示要准）。
     */
    private fun notify(ctx: Context, title: String, body: String, bigText: String = body): Boolean {
        // 通知权限（Android 13+）。没授权就别白忙，也不要崩。
        if (!hasNotifPermission(ctx)) return false

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "生词提醒", NotificationManager.IMPORTANCE_LOW).apply {
                description = "隔一阵子换一个生词，用来瞄一眼复习"
                setShowBadge(false)
            }
        )

        // 点通知 = 打开 App 并落到复习页（MainActivity 读 EXTRA_GOTO_REVIEW）
        val content = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_GOTO_REVIEW, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(content)
            .setAutoCancel(true)
            // 更新同一条时不重新提醒（渠道已经是 LOW，这是双保险）
            .setOnlyAlertOnce(true)
            .build()
        nm.notify(NOTIF_ID, n)
        return true
    }
}

/**
 * 闹钟到点 / 开机 / 装完新版 的接收器。
 *
 * 🔴 **BOOT_COMPLETED 这一段不能删**：用 AlarmManager 排的闹钟**重启后会全部丢失**，
 * 不在这里重排的话，用户重启一次手机这个功能就悄悄死掉了，还很难查（他只会觉得"没提醒了"）。
 *
 * MY_PACKAGE_REPLACED 同理 —— 装完新版 APK 后闹钟可能没了，而阿亮换包很勤。
 */
class VocabReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            VocabReminder.ACTION_TICK -> {
                if (VocabReminder.isOn(context)) {
                    VocabReminder.showNext(context)
                    // 告诉自愈逻辑"轮播还活着"，这样它就不会误判成过期而重排
                    VocabReminder.markFired(context)
                }
            }
            // 开机。⚠️ 必须走 healIfNeeded 而**不是** schedule：这个广播会被系统反复补发
            // （实测连开 3 次 App 就收到 3 次），无条件重排等于把倒计时一直往后顶。
            Intent.ACTION_BOOT_COMPLETED -> {
                VocabReminder.healIfNeeded(context)
            }
            // 装完新版 APK：闹钟大概率被清了，而且这个广播**真的只发一次**，
            // 无条件重排没副作用（重来一个完整间隔也是合理的，等于"更新后重新开始计时"）。
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (VocabReminder.isOn(context)) VocabReminder.schedule(context)
            }
        }
    }
}
