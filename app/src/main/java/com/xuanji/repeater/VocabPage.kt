package com.xuanji.repeater

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 生词本页：展示收藏的单词与句子（含翻译、来源、时间）。
 * 删除交互（【1005 阿亮定】）：**长按生词进入多选**，可全选/多选后批量删除；
 * 行内垃圾桶和右上角"清空"已删 —— 全选+删除等价于清空，界面更清爽。
 * 右上角改为「复习」入口（原来在概览行中间）。
 * 数据由 VocabStore（JSON 持久化 + StateFlow）驱动。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VocabPage(
    onBack: () -> Unit,
    onReview: () -> Unit = {},
    // 【v89 阿亮："滑到哪里，返回来还在哪里"】生词列表滚动状态 + 统计页滚动状态由外部持有：
    // 生词本 ⇄ 复习 / 统计 来回切不再跳顶。
    listState: LazyListState = remember { LazyListState() },
    statsScrollState: ScrollState = remember { ScrollState(0) },
) {
    val context = LocalContext.current
    VocabStore.ensureLoaded(context)
    val entries by VocabStore.entries.collectAsStateWithLifecycle()
    var showStats by remember { mutableStateOf(false) }
    // ---- 多选删除（【1005 阿亮定】长按进多选，全选/多选后批量删）----
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // 【v92 阿亮定】多选模式下系统返回（三键返回键 / 全面屏侧滑）= 退出多选回生词本，
    // **不是**退出生词本页 —— 不拦的话按一下返回直接出页面了，选择全白选。
    // 顶栏「<」和行上左滑走同一个逻辑，三条路干同一件事。
    BackHandler(enabled = selecting) {
        selecting = false
        selectedIds = emptySet()
    }

    // ---- 筛选 / 搜索（0916：生词本超过 200 条后，翻列表找不到词）----
    // ⚠️ 刻意不持久化：每次进来都从「全部」开始。
    // 要是记住上次筛选，你进去只看到 3 条，第一反应是"我词怎么没了"。
    var filter by remember { mutableStateOf(VocabFilter.ALL) }
    var keyword by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }

    // ---- 排序（0917：按加入时间 / 按开头字母）----
    // 跟筛选相反，**这里要记住**：排序不会让词"消失"，记住反而更顺手。理由见 VocabPrefs.kt
    var sort by remember { mutableStateOf(VocabPrefs.loadSort(context)) }
    var sortOpen by remember { mutableStateOf(false) }

    val shown = remember(entries, filter, keyword, sort) {
        // ⚠️ 顺序：先筛、再排。反过来的话筛完再排也没错，但先把集合缩小更省（2000 条时看得出来）
        sort.apply(
            entries.filter { e ->
                filter.match(e) && (keyword.isBlank() || e.matches(keyword))
            }
        )
    }

    // 没掌握的条数：复习入口上直接显示，一眼知道还有多少要练
    val pending = entries.count { !it.mastered }

    Column(modifier = Modifier.fillMaxSize()) {
        // 头部：返回 + 清空（复习入口已挪到下方概览行，见 0918）
        //
        // 🔴【0917 阿亮】这里原来在返回箭头右边跟着一行「生词本（3）」当标题。
        // 问题：**这串字在「阅读模式」顶栏右侧是"入口按钮"，到了这一页又成了"标题"**，
        // 而箭头紧贴标题 ⇒ 连起来读就是「返回生词本」，人已经在这一页了，读着莫名其妙
        // （复习页同理：「← 复习」读成「返回复习」）。
        // 他的原话："这个逻辑不太对吧"，然后拍板"只留返回的小三角儿"。
        // ⇒ 所以标题去掉了，**别再顺手加回来**。
        // 条数信息没丢：下方筛选档（全部/未掌握/已掌握）本来就在写。
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) {
                // ---- 多选模式顶栏：返回(退出多选) + 已选 N + 全选 + 删除 ----
                IconButton(onClick = {
                    selecting = false
                    selectedIds = emptySet()
                }) {
                    Icon(XuanjiIcons.Back, contentDescription = "退出多选")
                }
                Text(
                    text = "已选 ${selectedIds.size}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 4.dp),
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    // 全选 ⇄ 取消全选（针对当前筛选显示的条目）
                    selectedIds = if (selectedIds.size == shown.size) emptySet() else shown.map { it.id }.toSet()
                }) {
                    Text(
                        text = if (selectedIds.size == shown.size && shown.isNotEmpty()) "取消全选" else "全选",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                TextButton(
                    onClick = { if (selectedIds.isNotEmpty()) showDeleteConfirm = true },
                    enabled = selectedIds.isNotEmpty(),
                ) {
                    Text(
                        text = "删除",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // ---- 常态顶栏：返回 + 右上角复习入口（【1005】从概览行挪上来，替掉原"清空"）----
                IconButton(onClick = onBack) {
                    Icon(XuanjiIcons.Back, contentDescription = "返回")
                }
                Spacer(modifier = Modifier.weight(1f))
                FilledTonalButton(
                    onClick = onReview,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Icon(
                        XuanjiIcons.Repeat,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (pending > 0) "复习 $pending" else "复习",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "生词本还是空的",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            // ---- 概览 + 搜索 + 筛选档（0916）----
            // 【1005】复习入口挪到顶栏右上角（原"清空"位置）⇒ 这行只剩 学习统计 + 排序 + 搜索
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { showStats = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                ) {
                    Text(
                        text = "学习统计",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))

                // ---- 排序入口（0917）----
                // 为什么不做成一行 chips：上面筛选那行已经横滑了，再叠一行排序，
                // 页顶就成了「概览 / 搜索框 / 筛选 / 排序」四层，太重。
                // 排序是设置、不是高频切换 ⇒ 压成一个文字按钮，点一下弹三项。
                // ⚠️ 这三项**要持久化**（跟上面的筛选故意相反）：见 VocabPrefs.kt 的说明。
                Box {
                    TextButton(
                        onClick = { sortOpen = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    ) {
                        Text(
                            text = sort.label,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = if (sortOpen) "▴" else "▾",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    AppDropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                        VocabSort.values().forEach { s ->
                            val sel = s == sort
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = s.label,
                                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                },
                                leadingIcon = {
                                    // 不选中时留个等宽空位，免得三行文字左右错开
                                    Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                                        if (sel) {
                                            Icon(
                                                XuanjiIcons.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    sort = s
                                    VocabPrefs.saveSort(context, s)
                                    sortOpen = false
                                },
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) keyword = ""   // 收起搜索框就把关键词一起清掉，免得"藏着一个筛选条件"
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        if (searchOpen) XuanjiIcons.Close else XuanjiIcons.Search,
                        contentDescription = if (searchOpen) "关闭搜索" else "搜索",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            if (searchOpen) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    singleLine = true,
                    placeholder = { Text("搜单词 / 翻译 / 释义 / 来源") },
                    trailingIcon = {
                        if (keyword.isNotEmpty()) {
                            IconButton(onClick = { keyword = "" }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    XuanjiIcons.Close,
                                    contentDescription = "清空关键词",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }

            // 筛选档：横向可滑，档数以后加也不会挤（别用 Row 硬塞，窄屏会截断）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VocabFilter.entries.forEach { f ->
                    val selected = f == filter
                    val count = remember(entries, f) { entries.count { f.match(it) } }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = if (selected) 0.dp else 1.dp,
                        modifier = Modifier.clickable { filter = f },
                    ) {
                        Text(
                            text = "${f.label} $count",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            if (shown.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (keyword.isNotBlank()) "没有匹配「$keyword」的条目" else "这一档是空的",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    // key 用 id 而不是下标：删一条后下标会整体前移，Compose 会认错节点、行内状态错位
                    items(shown, key = { it.id }) { entry ->
                        // 【1005 阿亮定】长按进多选；多选中点击 = 切换选中。
                        // 行内垃圾桶已删（删除统一走多选顶栏的「删除」）
                        VocabEntryRow(
                            entry = entry,
                            selected = entry.id in selectedIds,
                            selecting = selecting,
                            onClick = {
                                if (selecting) {
                                    selectedIds = if (entry.id in selectedIds) selectedIds - entry.id
                                    else selectedIds + entry.id
                                }
                            },
                            onLongClick = {
                                // 长按 = 进多选并选中这一条（再长按其他条继续加）
                                selecting = true
                                selectedIds = selectedIds + entry.id
                            },
                            // 【v92 阿亮定】多选中行上向左滑过阈值 = 退出多选回生词本（顶栏「<」/系统返回同逻辑）
                            onSwipeLeft = {
                                selecting = false
                                selectedIds = emptySet()
                            },
                        )
                    }
                }
            }
        }
    }

    // 【1005 阿亮定】批量删除确认：样式对齐原"清空生词本"，确认键红色警示。
    // 原「清空」入口已删 —— 全选 + 删除等价于清空（多一次确认反而更稳）。
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除生词") },
            text = { Text("删除所选 ${selectedIds.size} 条生词？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    VocabStore.removeMany(context, selectedIds)
                    selectedIds = emptySet()
                    selecting = false
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showStats) {
        StatsPage(onBack = { showStats = false }, scrollState = statsScrollState)
    }
}

/** 单条生词卡片：文本 + 翻译 + 来源/时间。长按进多选，多选中点击切换选中（【1005 阿亮定】）；
 *  多选中向左滑过阈值 = 退出多选（【v92 阿亮定】）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VocabEntryRow(
    entry: VocabEntry,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeLeft: () -> Unit = {},
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .pointerInput(selecting) {
                    // 【v92 阿亮定】多选模式下，行上向左拖累计超 200px ≈ 屏宽 1/5 → 退出多选。
                    // 与点击/长按不冲突（tap 与 drag 手势各自判定：动过 touch slop 的 tap 自动取消）；
                    // 与列表垂直滚动不冲突（LazyColumn 只消费垂直拖动，水平方向的落到这里）。
                    // selecting=false 时 pointerInput key 变化重启 block，直接结束 = 不挂监听。
                    if (selecting) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onDragEnd = { if (totalDrag < -200f) onSwipeLeft() },
                        ) { _, dragAmount ->
                            totalDrag += dragAmount
                        }
                    }
                }
                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 多选模式：左侧选中圈（选中 = 实心✓）
            if (selecting) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(22.dp),
                ) {
                    if (selected) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "✓",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 🔴🔴 `weight(1f, fill = false)` 不能删（0917 阿亮报"句子没有角标也没有星"的根因）：
                    //    原来这个 Text 没有任何宽度约束，**长句会把整行占满**，
                    //    后面的「词/句」角标和星级被整个挤到屏幕外 —— 他以为"句子不支持这两个东西"。
                    //    实测：94 字符的长句，Text 宽 x=[69,911]，角标直接不出现在无障碍树里；
                    //    同时「Time flies.」这种短句一切正常。所以这**不是"句子功能缺失"**，
                    //    是纯布局溢出 —— **别为此去把单词和句子拆成两个列表**，治不了。
                    //    ⚠️ `fill = false` 也是必需的：填 true 的话短词后面的角标会被推到屏幕最右边。
                    Text(
                        text = entry.text,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // 词/句 类型角标
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (entry.isSentence) MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = if (entry.isSentence) "句" else "词",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (entry.isSentence) MaterialTheme.colorScheme.onTertiaryContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                    // 熟练度星级（照欧路的做法）：5 颗星，亮的表示练到第几档
                    // 0 星不显示（没复习过就不打扰）
                    if (entry.correctStreak > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        StarRating(stars = entry.stars)
                    }
                }
                // 单词：美音/英音 各一行（喇叭在左 + 标签 + 音标），音标不折行
                if (!entry.isSentence) {
                    Column(modifier = Modifier.padding(top = 3.dp)) {
                        // 美音行
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { Pronounce.speak(entry.text, "us") },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    XuanjiIcons.SpeakerUs,
                                    contentDescription = "美音发音",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Text(
                                text = "美",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(16.dp),
                            )
                            Text(
                                text = entry.usphone?.let { "/$it/" } ?: "/—/",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // 英音行
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { Pronounce.speak(entry.text, "uk") },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    XuanjiIcons.SpeakerUk,
                                    contentDescription = "英音发音",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Text(
                                text = "英",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.width(16.dp),
                            )
                            Text(
                                text = entry.ukphone?.let { "/$it/" } ?: "/—/",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                val detail = entry.definition ?: entry.translation
                detail?.let { d ->
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = d,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${entry.source} · ${formatAddedTime(entry.addedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 【1005】行内垃圾桶已删：删除统一走「长按 → 多选 → 顶栏删除」
        }
    }
}

private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

private fun formatAddedTime(ms: Long): String = try {
    timeFormat.format(Date(ms))
} catch (_: Exception) {
    ""
}

/**
 * 熟练度星级：五颗星，亮的表示练到第几档（照欧路的做法）。
 *
 * 为什么自绘而不是用 IconButton/Icon：
 * 这里要的是"一排小星星"这种装饰性元素，不是按钮 —— 不能有触摸目标、不能撑高行。
 * 用 `Box.size()+clip(CircleShape)` 那套自绘思路的简化版：直接画实心/描边五角星路径。
 *
 * 金色是刻意写死的（不用主题色）：星级是"成绩"的语义，跟粉色主题放一起容易被当成装饰，
 * 阿亮照着欧路来，金色才像"熟练度"。
 */
@Composable
fun StarRating(stars: Int, size: Int = 11) {
    val filled = stars.coerceIn(0, VocabEntry.MAX_STARS)
    if (filled == 0) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(VocabEntry.MAX_STARS) { i ->
            Icon(
                imageVector = if (i < filled) XuanjiIcons.StarFilled else XuanjiIcons.StarOutline,
                contentDescription = null,
                tint = if (i < filled) StarGold else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(size.dp),
            )
        }
    }
}

/** 熟练度星星的金色 */
private val StarGold = androidx.compose.ui.graphics.Color(0xFFFFB300)
