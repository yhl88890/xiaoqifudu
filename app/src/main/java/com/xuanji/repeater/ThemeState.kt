package com.xuanji.repeater

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** 马卡龙主题色预设 */
data class MacaronColor(val name: String, val light: Color, val dark: Color)

val MACARON_COLORS = listOf(
    MacaronColor("草莓粉", Color(0xFFF48FB1), Color(0xFFAD5D7F)),
    MacaronColor("薄荷绿", Color(0xFF80CBC4), Color(0xFF4E8D87)),
    MacaronColor("薰衣草紫", Color(0xFF9575CD), Color(0xFF5E4B8B)),
    MacaronColor("奶油杏", Color(0xFFFFB74D), Color(0xFFB27A2B)),
    MacaronColor("天空蓝", Color(0xFF4DD0E1), Color(0xFF2C8B97)),
    MacaronColor("抹茶绿", Color(0xFFAED581), Color(0xFF6F9A3D)),
)

/** 根据主题序号与明暗模式生成完整 ColorScheme：背景/卡片/高亮/控件全部跟随马卡龙主色 */
fun xuanjiColorScheme(index: Int, dark: Boolean): ColorScheme {
    val c = MACARON_COLORS[index.coerceIn(0, MACARON_COLORS.size - 1)]
    val primary = if (dark) c.dark else c.light
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return if (dark) {
        base.copy(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = c.dark.copy(alpha = 0.34f),
            onPrimaryContainer = c.light,
            secondary = primary,
            secondaryContainer = c.dark.copy(alpha = 0.30f),
            onSecondaryContainer = c.light,
            tertiary = primary,
            // 🔴【0917 阿亮】原来这里**只覆盖了 `tertiary`，漏了 `tertiaryContainer` / `onTertiaryContainer`**
            // ⇒ 这俩落到 `darkColorScheme()` 的默认值（淡紫容器 + 浅紫字），跟马卡龙主题不搭。
            //   生词本「句」角标、阅读模式视频标记都在用它。这里补齐，跟 primaryContainer 取同一个值。
            tertiaryContainer = c.dark.copy(alpha = 0.34f),
            onTertiaryContainer = c.light,
            background = blend(Color(0xFF121212), c.dark, 0.08f),
            surface = blend(Color(0xFF121212), c.dark, 0.08f),
            surfaceContainerLowest = blend(Color(0xFF0D0D0D), c.dark, 0.05f),
            surfaceContainerLow = blend(Color(0xFF161616), c.dark, 0.09f),
            surfaceContainer = blend(Color(0xFF1B1B1B), c.dark, 0.10f),
            surfaceContainerHigh = blend(Color(0xFF202020), c.dark, 0.11f),
            surfaceContainerHighest = blend(Color(0xFF262626), c.dark, 0.12f),
            surfaceVariant = blend(Color(0xFF1D1D1D), c.dark, 0.12f),
            onSurfaceVariant = Color(0xFFC9C9C9),
        )
    } else {
        base.copy(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = c.light.copy(alpha = 0.30f),
            onPrimaryContainer = c.dark,
            secondary = primary,
            secondaryContainer = c.light.copy(alpha = 0.24f),
            onSecondaryContainer = c.dark,
            tertiary = primary,
            // 🔴【0917 阿亮】同上面暗色分支：原来漏覆盖 `tertiaryContainer`/`onTertiaryContainer`，
            // 走到 `lightColorScheme()` 默认的淡紫（#EADDFF / #21005D）⇒ 生词本「句」角标是紫的，
            // 跟粉色主题格格不入（他原话："颜色跟主题风格也太不统一了"）。补齐成跟「词」同一套色。
            tertiaryContainer = c.light.copy(alpha = 0.30f),
            onTertiaryContainer = c.dark,
            background = blend(Color.White, c.light, 0.10f),
            surface = blend(Color.White, c.light, 0.10f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = blend(Color(0xFFF7F7F7), c.light, 0.12f),
            surfaceContainer = blend(Color(0xFFF2F2F2), c.light, 0.14f),
            surfaceContainerHigh = blend(Color(0xFFECECEC), c.light, 0.16f),
            surfaceContainerHighest = blend(Color(0xFFE6E6E6), c.light, 0.18f),
            surfaceVariant = blend(Color(0xFFF0F0F0), c.light, 0.18f),
            onSurfaceVariant = Color(0xFF555555),
        )
    }
}

/** 颜色混合：ratio=0 纯 base，ratio=1 纯 overlay */
private fun blend(base: Color, overlay: Color, ratio: Float): Color {
    val r = base.red * (1f - ratio) + overlay.red * ratio
    val g = base.green * (1f - ratio) + overlay.green * ratio
    val b = base.blue * (1f - ratio) + overlay.blue * ratio
    return Color(r, g, b, 1f)
}

/** 悬浮窗开关 / 字号偏好持久化 */
object FloatPrefs {
    private const val NAME = "float_prefs"
    private const val KEY_ON = "float_on"
    private const val KEY_SIZE = "float_size_idx"

    /** 【1014】悬浮窗字号三档（sp），索引即档位。
     *  🔴 **`SIZE_DEFAULT` 必须指向 15sp** —— 那是加这个功能之前源码里写死的值，
     *    默认档一旦变了，等于悄悄把所有老用户的悬浮窗字号改掉。
     *  🔴 单位是 **sp**（`FloatLyricsService` 直接喂给 `TextView.setTextSize`），
     *    所以它会**跟随系统字体缩放**，且**不受 App 那个 1.5 倍上限约束**
     *    （悬浮窗是独立 Window，不走 Compose 那棵树）。 */
    val SIZE_SP = listOf(13f, 15f, 18f)
    const val SIZE_DEFAULT = 1

    fun load(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY_ON, false)

    fun save(context: Context, on: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ON, on).apply()
    }

    /** 字号档位。越界（历史脏数据 / 以后增删档位）一律回落到默认档，别抛也别硬取。 */
    fun loadSizeIndex(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getInt(KEY_SIZE, SIZE_DEFAULT)
            .coerceIn(0, SIZE_SP.size - 1)

    fun saveSizeIndex(context: Context, idx: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_SIZE, idx.coerceIn(0, SIZE_SP.size - 1)).apply()
    }

    /** 悬浮窗当前字号（sp）。 */
    fun sizeSp(context: Context): Float = SIZE_SP[loadSizeIndex(context)]
}
object ThemePrefs {
    private const val NAME = "theme_prefs"
    private const val KEY_INDEX = "theme_index"
    private const val KEY_DARK = "dark_mode"

    fun loadIndex(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt(KEY_INDEX, 0)

    fun saveIndex(context: Context, index: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_INDEX, index).apply()
    }

    fun loadDark(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY_DARK, false)

    fun saveDark(context: Context, dark: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DARK, dark).apply()
    }
}
