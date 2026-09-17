package com.xuanji.repeater

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * 生词本页：展示收藏的单词与句子（含翻译、来源、时间），支持删除与清空。
 * 数据由 VocabStore（JSON 持久化 + StateFlow）驱动。
 */
@Composable
fun VocabPage(onBack: () -> Unit, onReview: () -> Unit = {}) {
    val context = LocalContext.current
    VocabStore.ensureLoaded(context)
    val entries by VocabStore.entries.collectAsStateWithLifecycle()
    var showClearConfirm by remember { mutableStateOf(false) }

    // ---- 筛选 / 搜索（0916：生词本超过 200 条后，翻列表找不到词）----
    // ⚠️ 刻意不持久化：每次进来都从「全部」开始。
    // 要是记住上次筛选，你进去只看到 3 条，第一反应是"我词怎么没了"。
    var filter by remember { mutableStateOf(VocabFilter.ALL) }
    var keyword by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }

    val shown = remember(entries, filter, keyword) {
        entries.filter { e ->
            filter.match(e) && (keyword.isBlank() || e.matches(keyword))
        }
    }

    // 没掌握的条数：复习入口上直接显示，一眼知道还有多少要练
    val pending = entries.count { !it.mastered }
    val masteredCount = entries.size - pending

    Column(modifier = Modifier.fillMaxSize()) {
        // 头部：返回 + 标题 + 复习 + 清空
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(XuanjiIcons.Back, contentDescription = "返回")
            }
            Text(
                text = "生词本（${entries.size}）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.weight(1f))
            // 复习入口：只要生词本里有东西就给（哪怕全掌握了，进去也能再练）
            if (entries.isNotEmpty()) {
                TextButton(onClick = onReview) {
                    Icon(
                        XuanjiIcons.Repeat,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (pending > 0) "复习 $pending" else "复习")
                }
            }
            TextButton(
                onClick = { showClearConfirm = true },
                enabled = entries.isNotEmpty(),
            ) {
                Icon(
                    XuanjiIcons.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("清空")
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "共 ${entries.size} 条 · 已掌握 $masteredCount 条",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
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
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    // key 用 id 而不是下标：删一条后下标会整体前移，Compose 会认错节点、行内状态错位
                    items(shown, key = { it.id }) { entry ->
                        VocabEntryRow(entry = entry, onDelete = { VocabStore.remove(context, entry.id) })
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空生词本") },
            text = { Text("将删除全部 ${entries.size} 条生词，此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    VocabStore.clear(context)
                }) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }
}

/** 单条生词卡片：文本 + 翻译 + 来源/时间 + 删除 */
@Composable
private fun VocabEntryRow(entry: VocabEntry, onDelete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
            IconButton(onClick = onDelete) {
                Icon(
                    XuanjiIcons.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
