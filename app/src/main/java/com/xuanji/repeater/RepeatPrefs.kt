package com.xuanji.repeater

import android.content.Context

/**
 * 复读设置偏好：
 * - 上一句重播次数（默认 3 遍）
 * - 播放速度（整曲播放，默认 1.0x）
 * - 重播速度（句子复读专用，默认 1.0x）
 */
object RepeatPrefs {
    private const val NAME = "repeat_prefs"
    private const val KEY_TIMES = "repeat_times"
    private const val KEY_SPEED = "play_speed"
    private const val KEY_REPEAT_SPEED = "repeat_speed"
    /**
     * v43/v44 旧键：播放速度曾存在这个键下，读取时自动迁移。
     *
     * 🔴 【1003 P0 修】**这里曾经直接写成 "repeat_speed"（和 [KEY_REPEAT_SPEED] 一模一样的字符串）**：
     * 于是「用户没手动改过全局速度（play_speed 不存在＝默认状态）+ 改过重播速度」时，
     * loadSpeed() 会把重播速度当旧版播放速度读走、**再 remove 掉重播速度那个键**。
     * 而 loadSpeed() 在 PlayerService 每次 prepare 都会调 ⇒ 必然触发
     * ⇒ 后果：全局速度被静默改成重播速度的值，用户的重播速度设置丢失。
     *
     * 两处修正：
     * ① 旧键名换成**绝不可能与正式键相撞**的名字；
     * ② 迁移前先确认重播速度键**不存在**，存在就放弃迁移（宁可速度回落默认，也不吞用户设置）。
     */
    private const val LEGACY_KEY_SPEED = "play_speed_legacy_v43"

    private const val DEFAULT_TIMES = 3
    private const val DEFAULT_SPEED = 1.0f

    private fun prefs(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun loadTimes(context: Context): Int =
        prefs(context).getInt(KEY_TIMES, DEFAULT_TIMES)

    fun saveTimes(context: Context, times: Int) {
        prefs(context).edit().putInt(KEY_TIMES, times).apply()
    }

    /** 播放速度：优先读新键，旧键值自动迁移
     *  🔴 【1003】迁移前必须确认**重播速度键不存在**，否则会吞掉用户的重播速度设置（见 LEGACY_KEY_SPEED 注释）。
     *  宁可这里回落默认值，也不能删别人的键。
     */
    fun loadSpeed(context: Context): Float {
        val p = prefs(context)
        val v = p.getFloat(KEY_SPEED, Float.NaN)
        if (!v.isNaN()) return v
        // 重播速度已被用户设置过 ⇒ 绝不动它，直接用默认播放速度
        if (p.contains(KEY_REPEAT_SPEED)) return DEFAULT_SPEED
        val legacy = p.getFloat(LEGACY_KEY_SPEED, Float.NaN)
        if (legacy.isNaN()) return DEFAULT_SPEED
        p.edit().putFloat(KEY_SPEED, legacy).remove(LEGACY_KEY_SPEED).apply()
        return legacy
    }

    fun saveSpeed(context: Context, speed: Float) {
        prefs(context).edit().putFloat(KEY_SPEED, speed).apply()
    }

    fun loadRepeatSpeed(context: Context): Float =
        prefs(context).getFloat(KEY_REPEAT_SPEED, DEFAULT_SPEED)

    fun saveRepeatSpeed(context: Context, speed: Float) {
        prefs(context).edit().putFloat(KEY_REPEAT_SPEED, speed).apply()
    }
}