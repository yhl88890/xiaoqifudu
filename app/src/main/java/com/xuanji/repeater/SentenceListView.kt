package com.xuanji.repeater

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 【结构·阶段1】句子列表骨架：播放器内嵌断句列表 与 阅读模式阅读列表 共用。
 *
 * 这两处的「列表外壳」是同一条规则，之前各写一遍（改一处忘一处就会出现
 * 「播放器里滚得对、阅读里不跟随」这种静默不一致），现在只有这一份：
 *  - LazyColumn + itemsIndexed 逐句铺开
 *  - 当前句索引一变 → 自动滚到该句（播放中高亮跟随）
 *
 * 行内容由调用方通过 [row] 决定：播放器给「点一下复读」的紧凑卡片，
 * 阅读模式给「点一句 + 长按展开翻译/生词」的学习卡片 —— 这是两页真正的差别，
 * 不强行合并（合并要开一堆参数才能保证各自像素不变，反而更难维护）。
 *
 * @param listState 外部传入可让调用方追加自己的滚动逻辑（如阅读模式进页面即定位当前句）
 * @param userScrollEnabled 播放器布局编辑时禁用滚动
 */
@Composable
fun SentenceListPane(
    sentences: List<Sentence>,
    activeIndex: Int,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    userScrollEnabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    row: @Composable (index: Int, sentence: Sentence) -> Unit,
) {
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) listState.animateScrollToItem(activeIndex)
    }
    LazyColumn(
        state = listState,
        userScrollEnabled = userScrollEnabled,
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        itemsIndexed(sentences) { index, sentence ->
            row(index, sentence)
        }
    }
}

/**
 * 【结构·阶段1】句子正文：把「这一句显示什么文字、什么样式」的规则收成一份。
 * 之前播放器列表与阅读列表各写一遍，规则其实完全一样：
 *  - 有文本 → 显示文本（当前句加大加粗）
 *  - 无文本但有歌词 → 显示占位文案（各页文案不同，由 [emptyLyricText] 传入）
 *  - 无文本也无歌词（纯波形断句）→ 「第N句 + 提示」同一行
 *
 * 不包含任何内外边距：由调用方套在自己的容器/修饰符上，保证各页原有排版逐像素不变。
 */
@Composable
fun SentenceTextBody(
    index: Int,
    text: String,
    active: Boolean,
    hasLyrics: Boolean,
    emptyLyricText: String,
    modifier: Modifier = Modifier,
) {
    if (text.isEmpty() && !hasLyrics) {
        // 纯波形断句（无歌词）：第N句 + 小字提示，同一行
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier,
        ) {
            Text(
                text = sentenceNumberLabel(index),
                style = if (active) MaterialTheme.typography.bodyLarge
                else MaterialTheme.typography.bodyMedium,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = SENTENCE_NO_LYRIC_HINT,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Text(
            text = if (text.isNotEmpty()) text else emptyLyricText,
            style = if (active) MaterialTheme.typography.bodyLarge
            else MaterialTheme.typography.bodyMedium,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (text.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            modifier = modifier,
        )
    }
}
