package com.xuanji.repeater

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Calendar

/**
 * 学习统计页（全屏 Dialog，从生词本「学习统计」入口弹出）。
 *
 * v1 只用现有 [VocabStore]（vocab.json）现算，不引入任何新存储：
 * 累计生词 / 已掌握 / 本周新增 / 近 8 周新增曲线 / 掌握度分布。
 * 音频收听、视频观看时长（需新存储）见 TODO，本版不含。
 *
 * 颜色全部走 MaterialTheme.colorScheme，跟随深浅主题，不写死任何色值。
 */
@Composable
fun StatsPage(
    onBack: () -> Unit,
    // 【v89】滚动状态由外部（VocabPage ← MainActivity）持有：重开不再跳顶
    scrollState: ScrollState = rememberScrollState(),
) {
    val context = LocalContext.current
    VocabStore.ensureLoaded(context)
    val entries by VocabStore.entries.collectAsStateWithLifecycle()

    val total = entries.size
    val mastered = entries.count { it.mastered }
    val masteredPct = if (total > 0) mastered * 100 / total else 0
    val unmastered = entries.count { !it.mastered && it.correctStreak == 0 }
    val learning = entries.count { !it.mastered && it.correctStreak > 0 }
    val weekStart = weekStartMillis(System.currentTimeMillis())
    val thisWeek = entries.count { it.addedAt >= weekStart }
    val weekBars = remember(entries) { computeWeekBars(entries, weekStart) }
    val maxBar = (weekBars.maxOrNull() ?: 0).coerceAtLeast(1)
    val weekLabels = listOf("7周", "6周", "5周", "4周", "3周", "2周", "上周", "本周")

    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp).padding(start = 4.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(XuanjiIcons.Back, contentDescription = "返回")
                    }
                    Text(
                        text = "学习统计",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).padding(16.dp),
                ) {
                    // 概览卡片
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatCard(label = "累计生词", value = "$total", modifier = Modifier.weight(1f))
                        StatCard(
                            label = "已掌握",
                            value = "$mastered",
                            sub = "$masteredPct%",
                            modifier = Modifier.weight(1f),
                        )
                        StatCard(label = "本周新增", value = "$thisWeek", modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    SectionTitle("近 8 周新增")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        weekBars.forEachIndexed { i, v ->
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = "$v",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.55f)
                                        .height((v.toFloat() / maxBar * 80f).dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                                        ),
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = weekLabels[i],
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    SectionTitle("掌握度分布")
                    Spacer(modifier = Modifier.height(8.dp))
                    DistributionRow(label = "未掌握", count = unmastered, total = total, color = MaterialTheme.colorScheme.error)
                    DistributionRow(label = "学习中", count = learning, total = total, color = MaterialTheme.colorScheme.tertiary)
                    DistributionRow(label = "已掌握", count = mastered, total = total, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, sub: String? = null, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (sub != null) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun DistributionRow(label: String, count: Int, total: Int, color: Color) {
    val pct = if (total > 0) count * 100 / total else 0
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(5.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(pct / 100f)
                    .height(10.dp)
                    .background(color, RoundedCornerShape(5.dp)),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$count · $pct%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
            textAlign = TextAlign.End,
        )
    }
}

/** 本周一 00:00 的毫秒时间戳（用于「本周新增」与按周统计） */
private fun weekStartMillis(now: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val dow = cal.get(Calendar.DAY_OF_WEEK) // 1=周日 .. 7=周六
    val back = if (dow == Calendar.MONDAY) 0 else (dow - Calendar.MONDAY + 7) % 7
    cal.add(Calendar.DAY_OF_MONTH, -back)
    return cal.timeInMillis
}

/** 把条目按加入时间分到「本周、上周 … 7 周前」共 8 个桶，返回长度 8（索引 7=本周） */
private fun computeWeekBars(entries: List<VocabEntry>, weekStart: Long): List<Int> {
    val weekMs = 7L * 24 * 60 * 60 * 1000
    val bars = IntArray(8)
    for (e in entries) {
        if (e.addedAt <= 0L) continue
        val weeksAgo = ((weekStart - e.addedAt) / weekMs).toInt()
        if (weeksAgo in 0..7) bars[7 - weeksAgo]++
    }
    return bars.toList()
}
