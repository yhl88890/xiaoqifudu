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
    /** v43/v44 旧键（播放速度曾存在此键下），读取时自动迁移 */
    private const val LEGACY_KEY_SPEED = "repeat_speed"

    private const val DEFAULT_TIMES = 3
    private const val DEFAULT_SPEED = 1.0f

    private fun prefs(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun loadTimes(context: Context): Int =
        prefs(context).getInt(KEY_TIMES, DEFAULT_TIMES)

    fun saveTimes(context: Context, times: Int) {
        prefs(context).edit().putInt(KEY_TIMES, times).apply()
    }

    /** 播放速度：优先读新键，旧键值自动迁移 */
    fun loadSpeed(context: Context): Float {
        val p = prefs(context)
        val v = p.getFloat(KEY_SPEED, Float.NaN)
        if (!v.isNaN()) return v
        val legacy = p.getFloat(LEGACY_KEY_SPEED, DEFAULT_SPEED)
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