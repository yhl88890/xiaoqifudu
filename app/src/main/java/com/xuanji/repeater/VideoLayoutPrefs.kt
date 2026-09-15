package com.xuanji.repeater

import android.content.Context

/**
 * 视频页（非全屏）模块：与音频模式一样可拖拽排序、可调高矮。
 *
 * - TRANSPORT：5 键控制排（上一曲/上一句/播放/下一句/下一曲）
 * - SUBTITLES：字幕列表（弹性模块，占满剩余空间，不参与调高矮）
 *
 * 默认顺序 [SUBTITLES, TRANSPORT] —— 字幕列表弹性占满、控制排落在它下方，
 * 视觉上就是「贴在底栏上方」（阿亮要求：首次安装默认在底部）。
 */
enum class VideoModule(val id: String) {
    SUBTITLES("subtitles"),
    TRANSPORT("transport"),
}

/** 视频页模块化布局偏好（与音频的 PlayerLayoutPrefs 分开存，互不干扰） */
object VideoLayoutPrefs {
    private const val NAME = "video_layout"
    private const val KEY_ORDER = "module_order"

    /** 默认顺序：字幕列表在上（弹性）、控制排在下（贴底栏）。 */
    val defaultOrder = listOf(VideoModule.SUBTITLES, VideoModule.TRANSPORT)

    /** 固定高度模块的默认高度（dp）；字幕列表为弹性模块，不在此列。
     *  【0915 收紧】110 → 88：控制排实际内容就是 88dp（72dp 播放键 + 上下各 8dp 内边距），
     *  模块按 110dp 排版时下面白白多出 22dp 空档 —— 阿亮说的"跟底栏之间距离太高"就是它。
     *  88dp = 内容自然高，卡片底边贴住模块底边，模块底边即紧邻底栏。 */
    val defaultHeights = mapOf(
        VideoModule.TRANSPORT to 88,
    )

    /** 控制排最小高度（dp）：里面最大的是 72dp 播放键，压到 76 已到极限（再矮会切边） */
    const val MIN_HEIGHT_TRANSPORT = 76
    const val MAX_HEIGHT = 340

    /** 【0915】偏好键版本：改动默认高度后，老装机里存的旧值会顶掉新默认。
     *  版本号对不上就清掉存的高度、回落新默认（顺序保留，不打扰用户排好的模块顺序）。 */
    private const val KEY_HEIGHT_VER = "height_ver"
    private const val HEIGHT_VER = 2

    private fun migrateHeightsIfNeeded(p: android.content.SharedPreferences) {
        if (p.getInt(KEY_HEIGHT_VER, 0) >= HEIGHT_VER) return
        val e = p.edit()
        for (m in VideoModule.values()) e.remove(heightKey(m))
        e.putInt(KEY_HEIGHT_VER, HEIGHT_VER).apply()
    }

    fun loadOrder(context: Context): List<VideoModule> {
        val p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val raw = p.getString(KEY_ORDER, null) ?: return defaultOrder
        val result = raw.split(",").mapNotNull { id ->
            VideoModule.values().firstOrNull { it.id == id }
        }.distinct().toMutableList()
        // 缺模块自动补齐（版本升级时新增的模块落到末尾）
        for (m in defaultOrder) if (m !in result) result.add(m)
        return result
    }

    fun saveOrder(context: Context, order: List<VideoModule>) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_ORDER, order.joinToString(",") { it.id }).apply()
    }

    fun loadHeight(context: Context, module: VideoModule, default: Int): Int {
        val p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        migrateHeightsIfNeeded(p)
        return p.getInt(heightKey(module), default)
    }

    fun saveHeight(context: Context, module: VideoModule, heightDp: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putInt(heightKey(module), heightDp).apply()
    }

    private fun heightKey(module: VideoModule) = "height_" + module.id
}
