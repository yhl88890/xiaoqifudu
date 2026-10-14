package com.xuanji.repeater

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 选集页：仅显示当前播放文件所在文件夹的音频（忽略项已过滤），当前项高亮。
 *  listState 由 MainActivity 持有（Activity 层 rememberSaveable），页面切换后滚动位置不丢。 */
@Composable
fun TrackPage(
    context: Context,
    svc: PlayerService?,
    state: PlayerState,
    listState: LazyListState,
) {
    var items by remember { mutableStateOf<List<AudioItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var sortMode by remember { mutableStateOf(FolderSortPrefs.load(context)) }
    val currentPath = state.currentItem?.path

    // 已加载列表所属目录：同目录切歌不重载（避免点选音频时列表闪一下）
    var loadedDir by remember { mutableStateOf<String?>(null) }

    // 当前曲目变化时重新加载（切歌后选集跟随当前文件夹）
    LaunchedEffect(currentPath) {
        if (currentPath == null) {
            items = emptyList()
            loadedDir = null
            loading = false
            return@LaunchedEffect
        }
        val dir = java.io.File(currentPath).parent
        // 同一目录内点选音频：列表内容不会变，直接跳过重载，界面不闪
        if (dir == loadedDir && items.isNotEmpty()) return@LaunchedEffect
        loading = true
        items = withContext(Dispatchers.IO) {
            val all = AudioScanner.scan(context, IgnorePrefs.load(context))
            all.filter { java.io.File(it.path).parent == dir }
        }
        loadedDir = dir
        loading = false
    }

    // 展示列表：搜索过滤 + 按用户选择的排序方式（与文件夹页共享偏好）
    // 【1007 统一】音频选集补上搜索框（文件夹页/视频选集本来就有，阿亮："两边统一都有"）
    var query by remember { mutableStateOf("") }
    val displayAudios = remember(items, sortMode, query) {
        val q = query.trim()
        val filtered = if (q.isEmpty()) items
        else items.filter { fileNameWithoutExt(it.path).contains(q, ignoreCase = true) }
        sortedAudios(filtered, sortMode)
    }

    // 排序跳顶不能用 LaunchedEffect(sortMode)——页面重新组合时 effect 也会启动，
    // 会把"返回选集"变成无条件跳顶；只在用户主动点排序 chip 时手动滚回顶部。
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // 排序切换（数字/字母/时间）
        Row(
            // 【v169 🔴 统一「顶栏 → 内容」空档】top 8dp → **0dp**。
            //   口径 = 顶栏胶囊底边 → 首个可见元素（排序 chips 是**有底色的块**，取块顶）。
            //   实测原空档 67px ⇒ 去掉 22px 后 ≈ 45px ✓
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = sortMode == FolderSortMode.NUMERIC,
                onClick = {
                    sortMode = FolderSortMode.NUMERIC
                    FolderSortPrefs.save(context, sortMode)
                    scope.launch { listState.scrollToItem(0) }
                },
                label = { Text("数字") },
                modifier = Modifier.padding(end = 8.dp),
            )
            FilterChip(
                selected = sortMode == FolderSortMode.ALPHA,
                onClick = {
                    sortMode = FolderSortMode.ALPHA
                    FolderSortPrefs.save(context, sortMode)
                    scope.launch { listState.scrollToItem(0) }
                },
                label = { Text("字母") },
                modifier = Modifier.padding(end = 8.dp),
            )
            FilterChip(
                selected = sortMode == FolderSortMode.MTIME,
                onClick = {
                    sortMode = FolderSortMode.MTIME
                    FolderSortPrefs.save(context, sortMode)
                    scope.launch { listState.scrollToItem(0) }
                },
                label = { Text("时间") },
            )
        }
        // 【1007 统一】搜索框：与文件夹页同款（样式/占位文字一致，阿亮："两边统一都有"）
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索文件名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (currentPath == null) "请先在播放页播放一个音频" else "当前文件夹暂无其他音频")
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 6.dp),
            ) {
                items(displayAudios, key = { it.id }) { item ->
                    val selected = state.currentItem?.id == item.id
                    Surface(
                        onClick = {
                            // 点当前播放的同一集 → 暂停/恢复切换；点其他集 → 播放该集（仅高亮，不滚动）
                            if (selected) svc?.togglePlayPause()
                            else svc?.setPlaylist(displayAudios, displayAudios.indexOf(item))
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surface,
                        tonalElevation = if (selected) 0.dp else 1.dp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = fileNameWithoutExt(item.path),
                                style = if (selected) MaterialTheme.typography.bodyLarge
                                else MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = formatDuration(item.durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            // 当前集：播放/暂停状态图标（点同一集可暂停/恢复）
                            if (selected) {
                                Spacer(modifier = Modifier.width(10.dp))
                                Icon(
                                    imageVector = if (state.isPlaying) XuanjiIcons.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (state.isPlaying) "播放中" else "已暂停",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}