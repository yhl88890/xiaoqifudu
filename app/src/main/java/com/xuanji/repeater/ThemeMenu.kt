package com.xuanji.repeater

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/**
 * 全 App 统一的下拉菜单外观。
 *
 * 🔴【0917 阿亮："弹窗跟主题配合一下。你把这几个弹窗光弹窗了，没跟主题挂上钩儿啊。"】
 *
 * 病根：直接 `DropdownMenu(...)` 时底色走 Material 的 `MenuDefaults.containerColor`，
 * 它取的是 `surfaceContainer` 这个 token —— 而本主题里那个值只往 primary 掺了 **14%**
 * （见 `ThemeState.kt`），掺完几乎就是块灰白板，跟满页的马卡龙粉完全不搭。
 *
 * 这里显式指定四样东西：
 * - `containerColor`：从 `background` 往 `primary` 调 **45%** ⇒ 跟卡片同一套配色，但比卡片
 *   更实一点（卡片是 30%），所以浮在卡片上或页面上都分得清。
 * - `border`：一圈主题色描边 —— "这是本 App 的弹窗"一眼可见。
 * - `shape`：14dp，跟设置卡片的 16dp 协调。
 * - `tonalElevation = 0`：⚠️ 必须归零。Material 的 `Surface` 会**再叠一层主题色调**，
 *   不归零的话上面算好的颜色会被二次染色，跟卡片对不上。
 *
 * 🔴 **以后新增下拉菜单一律用 `AppDropdownMenu`，别再直接写 `DropdownMenu`。**
 * （明暗模式共用同一个比例，暗色下算出来是深紫红，配浅色字读得清。）
 */
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        containerColor = appMenuContainer(),
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        content = content,
    )
}

/** 菜单底色：跟卡片同一套配色（`background` 往 `primary` 调 45%），明暗模式通用。 */
@Composable
fun appMenuContainer(): Color {
    val cs = MaterialTheme.colorScheme
    return lerp(cs.background, cs.primary, 0.45f)
}
