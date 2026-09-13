package com.xuanji.repeater

import android.content.Context

/**
 * 忽略列表持久化：被忽略的文件夹/文件路径以后不再加载（可在"已忽略"中取消）。
 * 所有音频加载入口（文件夹页 / 选集页）统一过滤。
 */
object IgnorePrefs {

    private const val NAME = "ignore_prefs"
    private const val KEY_PATHS = "paths"

    fun load(context: Context): Set<String> {
        val sp = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return sp.getStringSet(KEY_PATHS, emptySet())?.toSet() ?: emptySet()
    }

    fun add(context: Context, path: String) {
        val sp = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val set = (sp.getStringSet(KEY_PATHS, emptySet()) ?: emptySet()).toMutableSet()
        set.add(path)
        sp.edit().putStringSet(KEY_PATHS, set).apply()
    }

    fun remove(context: Context, path: String) {
        val sp = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val set = (sp.getStringSet(KEY_PATHS, emptySet()) ?: emptySet()).toMutableSet()
        set.remove(path)
        sp.edit().putStringSet(KEY_PATHS, set).apply()
    }
}