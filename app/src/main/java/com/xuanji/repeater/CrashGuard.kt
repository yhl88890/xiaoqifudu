package com.xuanji.repeater

import android.content.Context

// ===== 崩溃现场落盘：从 MainActivity 抽出的自包含工具（只依赖 Context 参数，不碰类状态） =====
internal const val PREFS_CRASH = "xuanji_crash"

/** 把异常现场存到 SP：万一真闪退了，这段信息能直接拿来定位，不用再靠猜 */
internal fun saveLastCrash(context: Context, t: Throwable) {
    try {
        val sb = StringBuilder()
        sb.append(t.javaClass.name).append(": ").append(t.message ?: "").append("\n")
        t.stackTrace.take(15).forEach { sb.append("    at ").append(it).append("\n") }
        // 必须 commit 不能 apply：进程马上就要被杀，异步落盘来不及写，现场就丢了
        context.getSharedPreferences(PREFS_CRASH, Context.MODE_PRIVATE).edit()
            .putString("last", sb.toString())
            .putLong("time", System.currentTimeMillis())
            .commit()
    } catch (_: Throwable) { }
}

/** 启动时把上次闪退的堆栈打进日志：真出了事，插上 USB 一条 logcat 就能捞到现场，不用复现 */
internal fun logLastCrash(context: Context) {
    runCatching {
        val sp = context.getSharedPreferences(PREFS_CRASH, Context.MODE_PRIVATE)
        val last = sp.getString("last", null) ?: return
        val time = sp.getLong("time", 0L)
        android.util.Log.e("XJ_LASTCRASH", "上次崩溃时间=${java.util.Date(time)}\n$last")
    }
}
