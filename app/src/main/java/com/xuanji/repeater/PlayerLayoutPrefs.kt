package com.xuanji.repeater

import android.content.Context

/** 播放页模块（可排序、可调高矮；进度条+控制条已合并为 TRANSPORT） */
enum class PlayerModule(val id: String) {
    WAVEFORM("waveform"),
    TOOLBAR("toolbar"),
    TRANSPORT("transport"),
    SENTENCES("sentences"),
}

/** 播放页模块化布局偏好：模块顺序 + 各模块高度（标题固定不参与） */
object PlayerLayoutPrefs {
    private const val NAME = "player_layout"
    private const val KEY_ORDER = "module_order"

    val defaultOrder = listOf(
        PlayerModule.WAVEFORM,
        PlayerModule.TOOLBAR,
        PlayerModule.TRANSPORT,
        PlayerModule.SENTENCES,
    )

    /** 固定高度模块的默认高度（dp）；句子列表为弹性模块占剩余空间，不在此列 */
    val defaultHeights = mapOf(
        PlayerModule.WAVEFORM to 120,
        // 【0915 收紧·阿亮标红那两条线】72 → 50：
        // 阿亮截图把「重播次数」那排的**上沿**和**下沿**各画了一条红线，说"这几个间隙变窄"。
        // 根因：这排 chip 的自然高只有 ~44dp，模块却给了 72dp，ToolbarModule 里
        // `Arrangement.Center` 就把多出来的 ~28dp **均分到 chip 行上下** ⇒ 上下各多 ~14dp 空白，
        // 正是他圈的那两条线。**这不是模块间距（gap=8dp），是模块自身的富余高度。**
        // 收到 50dp（= 内容自然高 44 + 6dp 呼吸），上下各留 3dp，视觉上就贴紧了。
        PlayerModule.TOOLBAR to 50,
        // 【0915 收紧】176 → 140：进度条与按键之间的空隙压缩后，这块不再需要那么高，
        // 省下的空间留给下方句子列表（阿亮要求"上下更窄、比例上更矮"）。
        PlayerModule.TRANSPORT to 140,
    )

    const val MIN_HEIGHT = 60
    /** 控制行最小高度（dp）：允许压得更窄（4 颗 chip 也能放下），比通用 MIN_HEIGHT 小 */
    const val MIN_HEIGHT_TOOLBAR = 44
    /** 传输模块（进度条+五键）最小高度（dp）：压太矮会让 72dp 播放键和进度条挤在一起 */
    const val MIN_HEIGHT_TRANSPORT = 110
    const val MAX_HEIGHT = 340

    /** 波形条最小高度（dp）：允许压得更矮（波形内容简单），其他模块保持 MIN_HEIGHT */
    const val MIN_HEIGHT_WAVEFORM = 40

    fun loadOrder(context: Context): List<PlayerModule> {
        val p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val raw = p.getString(KEY_ORDER, null) ?: return defaultOrder
        val result = raw.split(",").mapNotNull { id ->
            PlayerModule.values().firstOrNull { it.id == id }
        }.distinct().toMutableList()
        // 缺模块自动补齐（旧版本 progress/controls 会被过滤，TRANSPORT 自动追加）
        for (m in defaultOrder) if (m !in result) result.add(m)
        return result
    }

    fun saveOrder(context: Context, order: List<PlayerModule>) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_ORDER, order.joinToString(",") { it.id }).apply()
    }

    fun loadHeight(context: Context, module: PlayerModule, default: Int): Int {
        val p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        migrateHeightsIfNeeded(p)
        return p.getInt(heightKey(module), default)
    }

    fun saveHeight(context: Context, module: PlayerModule, heightDp: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putInt(heightKey(module), heightDp).apply()
    }

    // 【0915 新增】模块默认高度版本化。
    // 为什么需要：SharedPreferences 里存的是**绝对高度**，一旦用户装过旧版，
    // 旧默认值就被持久化下来，**新默认值永远顶不过它**（改 defaultHeights 对老装机无效）。
    // 视频那边（VideoLayoutPrefs）早有这套 height_ver，音频这边漏了 —— 这次要改
    // TOOLBAR 默认高（72→50）才补上。版本对不上就**清掉所有存的高度**、回落新默认。
    // ⚠️ 只清高度、不动顺序（用户排的模块顺序要保留）。
    private const val KEY_HEIGHT_VER = "height_ver"
    private const val HEIGHT_VER = 2

    private fun migrateHeightsIfNeeded(p: android.content.SharedPreferences) {
        if (p.getInt(KEY_HEIGHT_VER, 1) == HEIGHT_VER) return
        val e = p.edit()
        for (m in PlayerModule.values()) e.remove(heightKey(m))
        e.putInt(KEY_HEIGHT_VER, HEIGHT_VER).apply()
    }

    private fun heightKey(module: PlayerModule) = "height_" + module.id
}