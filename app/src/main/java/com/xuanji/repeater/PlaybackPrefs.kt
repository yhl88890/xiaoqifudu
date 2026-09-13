package com.xuanji.repeater

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 一次已保存的播放现场 */
data class SavedPlayback(
    val paths: List<String>,
    val index: Int,
    val positionMs: Long,
    val playMode: PlayMode,
)

/** 播放记忆：后台彻底退出时保存播放现场（列表/索引/进度/模式），重启可恢复 */
object PlaybackPrefs {

    private const val PREFS = "playback_prefs"
    private const val KEY_JSON = "saved_json"
    private const val KEY_RESUME = "resume_on_start"

    fun save(context: Context, paths: List<String>, index: Int, positionMs: Long, playMode: PlayMode) {
        if (paths.isEmpty()) return
        try {
            val arr = JSONArray()
            paths.forEach { arr.put(it) }
            val json = JSONObject()
                .put("paths", arr)
                .put("index", index)
                .put("position", positionMs)
                .put("mode", playMode.name)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_JSON, json.toString())
                .apply()
        } catch (_: Exception) {
        }
    }

    fun load(context: Context): SavedPlayback? {
        return try {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_JSON, null) ?: return null
            val json = JSONObject(raw)
            val arr = json.getJSONArray("paths")
            val paths = mutableListOf<String>()
            for (i in 0 until arr.length()) paths.add(arr.getString(i))
            val mode = try {
                PlayMode.valueOf(json.optString("mode", PlayMode.SEQUENCE.name))
            } catch (_: Exception) {
                PlayMode.SEQUENCE
            }
            SavedPlayback(
                paths = paths,
                index = json.optInt("index", 0),
                positionMs = json.optLong("position", 0L),
                playMode = mode,
            )
        } catch (_: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_JSON)
            .apply()
    }

    /** 重启后是否自动继续播放（设置项） */
    fun loadResume(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_RESUME, true)

    fun saveResume(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RESUME, on)
            .apply()
    }
}
