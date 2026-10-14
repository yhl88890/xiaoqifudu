package com.xuanji.repeater

import android.content.Context

/**
 * 【v170】Pad 模式开关。
 *
 * 阿亮 1015 定的方案：
 * - **平板上横屏 → 自动双栏**（左视频 / 右字幕）；竖屏 = 手机版放大，不双栏。
 * - **手机上给一个开关**（就是本类存的这个值），打开后横屏也走双栏。
 *
 * 🔴 它是**全 App 级**的偏好，不是视频页私有的 —— 它还决定**要不要放开屏幕方向锁**：
 *    - 关 = 维持竖屏锁（`AndroidManifest` 里那句 `screenOrientation="portrait"` 的现状，零变化）
 *    - 开 = 允许旋转（`MainActivity` 里 `requestedOrientation` 那段）
 *   ⇒ 所以不能塞进 `VideoLayoutPrefs`（那个只管视频页模块顺序 / 高度，跟方向无关）。
 *
 * ⚠️ 手机会不会**真的**转过来，还取决于系统「自动旋转」开没开 —— 这一点已写进软件介绍，
 *   免得他以为开关坏了。
 */
object PadModePrefs {
    private const val NAME = "pad_mode"
    private const val KEY_ON = "on"

    /** 默认关。手机用户绝大多数不需要；平板那边由 `isTablet` 自动放行，不看这个值。 */
    const val DEFAULT = false

    fun load(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY_ON, DEFAULT)

    fun save(context: Context, on: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ON, on).apply()
    }
}
