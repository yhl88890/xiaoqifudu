package com.xuanji.repeater

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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

    // 没掌握的条数：复习入口上直接显示，一眼知道还有多少要练
    val pending = entries.count { !it.mastered }

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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    VocabEntryRow(entry = entry, onDelete = { VocabStore.remove(context, entry.id) })
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
                    Text(
                        text = entry.text,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
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
                    // 掌握进度角标：连对 N 次里的 N。0 不显示（没复习过就不打扰）
                    if (entry.correctStreak > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (entry.mastered) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                // 掌握了给对勾，没掌握给进度（2/3）
                                text = if (entry.mastered) "✓ 已掌握"
                                else "${entry.correctStreak}/${VocabEntry.MASTERED_AT}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (entry.mastered) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
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
