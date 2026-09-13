package com.xuanji.repeater

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/** 播放页：模块化布局（标题固定；编辑模式由设置开关开启：拖动=移动、拖底部≡=调高，返回键退出） */
@Composable
fun PlayerPage(
    svc: PlayerService?,
    state: PlayerState,
    layoutEdit: Boolean = false,
    onExitEdit: () -> Unit = {},
) {
    val ctx = LocalContext.current
    var moduleOrder by remember { mutableStateOf(PlayerLayoutPrefs.loadOrder(ctx)) }
    var moduleHeights by remember {
        mutableStateOf(
            PlayerLayoutPrefs.defaultHeights.mapValues { (m, d) -> PlayerLayoutPrefs.loadHeight(ctx, m, d) }
        )
    }

    // 编辑模式：返回键退出
    BackHandler(enabled = layoutEdit) { onExitEdit() }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // 曲目信息（固定，不参与模块化）
        Text(
            text = state.currentItem?.title ?: "未选择音频",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 2.dp),
            textAlign = TextAlign.Center,
        )

        PlayerModuleHost(
            modules = moduleOrder,
            heights = moduleHeights,
            layoutEdit = layoutEdit,
            onMove = { from, to ->
                val list = moduleOrder.toMutableList()
                val m = list.removeAt(from)
                list.add(to, m)
                moduleOrder = list
                PlayerLayoutPrefs.saveOrder(ctx, list)
            },
            onHeightChange = { module, h ->
                moduleHeights = moduleHeights + (module to h)
                PlayerLayoutPrefs.saveHeight(ctx, module, h)
            },
        ) { module ->
            val editing = layoutEdit
            when (module) {
                PlayerModule.WAVEFORM -> WaveformModule(state, svc, editing)
                PlayerModule.TOOLBAR -> ToolbarModule(svc, state, editing)
                PlayerModule.TRANSPORT -> TransportModule(svc, state, editing)
                PlayerModule.SENTENCES -> SentencesModule(svc, state, editing)
            }
        }
    }
}

/** 模块容器：固定高度模块可拉伸；编辑模式（设置开关开启）下直接拖动移动 / 拖底部≡调高，返回键退出 */
@Composable
private fun PlayerModuleHost(
    modules: List<PlayerModule>,
    heights: Map<PlayerModule, Int>,
    layoutEdit: Boolean,
    onMove: (Int, Int) -> Unit,
    onHeightChange: (PlayerModule, Int) -> Unit,
    content: @Composable (PlayerModule) -> Unit,
) {
    val itemHeights = remember { HashMap<String, Int>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val latestModules by rememberUpdatedState(modules)
    // 模块间距 8.dp 转 px（交换补偿需要算上间距，保证视觉连续）
    val gapPx = with(LocalDensity.current) { 8.dp.toPx() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        modules.forEachIndexed { index, module ->
            // 显式 key：模块换位时 Compose 移动节点而非按位置复用，
            // 否则 pointerInput 会被销毁重建 → 拖动手势中断、draggingId 残留 → 卡死
            key(module.id) {
            val isEdit = layoutEdit
            val isDragging = draggingId == module.id
            val moduleHeight = heights[module]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (module == PlayerModule.SENTENCES) Modifier.weight(1f)
                        else Modifier.height((moduleHeight ?: 100).dp)
                    )
                    .onSizeChanged { itemHeights[module.id] = it.height }
                    .zIndex(if (isDragging || isEdit) 2f else 0f)
                    .then(if (isDragging) Modifier.offset { IntOffset(0, dragOffset.roundToInt()) } else Modifier)
                    // 编辑模式：模块任意位置直接拖动换位（无需长按）；非编辑模式不挂手势
                    .pointerInput(module, layoutEdit) {
                        if (!layoutEdit) return@pointerInput
                        detectDragGestures(
                            onDragStart = {
                                draggingId = module.id
                                dragOffset = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount.y
                                val di = latestModules.indexOfFirst { it.id == draggingId }
                                if (di >= 0) {
                                    val selfH = itemHeights[module.id] ?: 0
                                    // 下移：拖动模块中心越过下一模块中心才交换（(selfH+nextH)/2 + gap）
                                    // ——窄模块必须拖过宽模块中心，避免"卡在宽模块中间"
                                    if (dragOffset > (selfH + (itemHeights[latestModules.getOrNull(di + 1)?.id] ?: selfH)) / 2f + gapPx
                                        && di < latestModules.size - 1
                                    ) {
                                        val nextH = itemHeights[latestModules[di + 1].id] ?: selfH
                                        onMove(di, di + 1)
                                        // 补偿对方高度+间距：交换后模块视觉位置连续跟随手指；
                                        // 补偿量 (selfH-nextH)/2 恰好不超过反向阈值，永不误判反向
                                        dragOffset -= (nextH + gapPx)
                                    }
                                    // 上移：中心越过上一模块中心才交换
                                    else if (dragOffset < -((selfH + (itemHeights[latestModules.getOrNull(di - 1)?.id] ?: selfH)) / 2f + gapPx)
                                        && di > 0
                                    ) {
                                        val prevH = itemHeights[latestModules[di - 1].id] ?: selfH
                                        onMove(di, di - 1)
                                        dragOffset += (prevH + gapPx)
                                    }
                                }
                            },
                            onDragEnd = { draggingId = null },
                            onDragCancel = { draggingId = null },
                        )
                    },
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        content(module)
                    }
                }
                // 编辑模式：高度把手（句子列表为弹性模块，无把手），zIndex 高于覆盖层
                if (isEdit && module != PlayerModule.SENTENCES) {
                    HeightHandle(
                        heightDp = moduleHeight ?: 100,
                        onHeightChange = { h -> onHeightChange(module, h) },
                        // 波形条允许压得更矮，其他模块保持全局最小值
                        minHeight = when (module) {
                            PlayerModule.WAVEFORM -> PlayerLayoutPrefs.MIN_HEIGHT_WAVEFORM
                            PlayerModule.TOOLBAR -> PlayerLayoutPrefs.MIN_HEIGHT_TOOLBAR
                            else -> PlayerLayoutPrefs.MIN_HEIGHT
                        },
                        modifier = Modifier.align(Alignment.BottomCenter).zIndex(4f),
                    )
                }
                // 编辑覆盖层：高亮边框 + 提示（编辑模式由设置开关控制，返回键退出）
                if (isEdit) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .zIndex(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "拖动移动 · 拖底部≡调高 · 返回退出",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            } // key(module.id) 结束
        }
    }
}

/** 高度调节把手：编辑模式下拖底边上下改变模块高度（minHeight 按模块类型可放宽，波形条允许更矮） */
@Composable
private fun HeightHandle(
    heightDp: Int,
    onHeightChange: (Int) -> Unit,
    minHeight: Int = PlayerLayoutPrefs.MIN_HEIGHT,
    modifier: Modifier = Modifier,
) {
    val currentHeight by rememberUpdatedState(heightDp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
            // 手动事件循环：按下即消费一切，与模块长按检测器隔离（按下停顿再拖也能正常调高）
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var lastY = down.position.y
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                        val dy = change.position.y - lastY
                        if (dy != 0f) {
                            onHeightChange(
                                (currentHeight + (dy / density).roundToInt())
                                    .coerceIn(minHeight, PlayerLayoutPrefs.MAX_HEIGHT)
                            )
                            lastY = change.position.y
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "≡",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 波形模块：Canvas 自适应高度 */
@Composable
private fun WaveformModule(state: PlayerState, svc: PlayerService?, editing: Boolean) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxSize(),
    ) {
        WaveformView(
            state = state,
            onSeek = { ms -> svc?.seekTo(ms) },
            seekEnabled = !editing,
            // 水平内边距 10dp：与进度条 SeekBar 轨道同几何（轨道区域两端各内缩 10dp），两者进度精确对齐
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
        )
    }
}

/** 工具条模块：三选项卡，内容随高度自适应缩放 */
@Composable
private fun ToolbarModule(svc: PlayerService?, state: PlayerState, editing: Boolean) {
    val ctx = LocalContext.current
    var showRepeatMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showRepeatSpeedMenu by remember { mutableStateOf(false) }
    var showPlayModeMenu by remember { mutableStateOf(false) }
    var repeatTimes by remember { mutableStateOf(RepeatPrefs.loadTimes(ctx)) }
    var repeatSpeed by remember { mutableStateOf(RepeatPrefs.loadRepeatSpeed(ctx)) }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val scale = (maxHeight / 72.dp).coerceIn(0.55f, 1.8f)
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy((8 * scale).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ToolChipCard(
                        label = "重播次数",
                        value = "×$repeatTimes",
                        onClick = { showRepeatMenu = true },
                        scale = scale,
                        enabled = !editing,
                    )
                    DropdownMenu(
                        expanded = showRepeatMenu,
                        onDismissRequest = { showRepeatMenu = false },
                    ) {
                        listOf(1, 3, 5, 7).forEach { t ->
                            DropdownMenuItem(
                                text = { Text(if (t == repeatTimes) "重播 $t 次 ✓" else "重播 $t 次") },
                                onClick = {
                                    repeatTimes = t
                                    RepeatPrefs.saveTimes(ctx, t)
                                    showRepeatMenu = false
                                },
                            )
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    ToolChipCard(
                        label = "全局速度",
                        value = "×${state.speed}",
                        onClick = { showSpeedMenu = true },
                        scale = scale,
                        enabled = !editing,
                    )
                    DropdownMenu(
                        expanded = showSpeedMenu,
                        onDismissRequest = { showSpeedMenu = false },
                    ) {
                        listOf(0.75f, 1.0f, 1.25f, 1.5f).forEach { sp ->
                            DropdownMenuItem(
                                text = { Text(if (state.speed == sp) "${sp}x ✓" else "${sp}x") },
                                onClick = {
                                    svc?.setSpeed(sp)
                                    showSpeedMenu = false
                                },
                            )
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    ToolChipCard(
                        label = "重播速度",
                        value = "×${repeatSpeed}",
                        onClick = { showRepeatSpeedMenu = true },
                        scale = scale,
                        enabled = !editing,
                    )
                    DropdownMenu(
                        expanded = showRepeatSpeedMenu,
                        onDismissRequest = { showRepeatSpeedMenu = false },
                    ) {
                        listOf(0.75f, 1.0f, 1.25f, 1.5f).forEach { sp ->
                            DropdownMenuItem(
                                text = { Text(if (repeatSpeed == sp) "${sp}x ✓" else "${sp}x") },
                                onClick = {
                                    repeatSpeed = sp
                                    RepeatPrefs.saveRepeatSpeed(ctx, sp)
                                    svc?.setRepeatSpeed(sp)
                                    showRepeatSpeedMenu = false
                                },
                            )
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    ToolChipCard(
                        label = when (state.playMode) {
                            PlayMode.SEQUENCE -> "顺序播放"
                            PlayMode.LOOP_LIST -> "列表循环"
                            PlayMode.LOOP_ONE -> "单曲循环"
                        },
                        icon = when (state.playMode) {
                            PlayMode.SEQUENCE -> Icons.Filled.PlayArrow
                            PlayMode.LOOP_LIST -> XuanjiIcons.Repeat
                            PlayMode.LOOP_ONE -> XuanjiIcons.RepeatOne
                        },
                        iconAtBottom = true,
                        value = "",
                        onClick = { showPlayModeMenu = true },
                        scale = scale,
                        enabled = !editing,
                    )
                    DropdownMenu(
                        expanded = showPlayModeMenu,
                        onDismissRequest = { showPlayModeMenu = false },
                    ) {
                        listOf(
                            PlayMode.SEQUENCE to "顺序播放",
                            PlayMode.LOOP_LIST to "列表循环",
                            PlayMode.LOOP_ONE to "单曲循环",
                        ).forEach { (mode, name) ->
                            DropdownMenuItem(
                                text = { Text(if (state.playMode == mode) "$name ✓" else name) },
                                onClick = {
                                    svc?.setPlayMode(mode)
                                    showPlayModeMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 自定义进度条：轨道/thumb 与波形条同几何（水平 padding 10dp + 全宽绘制），两者进度精确对齐；支持点击/拖动 seek */
@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    barHeight: Dp = 32.dp,
) {
    val totalMs = durationMs.coerceAtLeast(1L)
    val fraction = positionMs.coerceIn(0L, totalMs).toFloat() / totalMs
    val primary = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = modifier
            .height(barHeight)
            // 与波形条相同的水平内边距：进度几何完全一致（竖线 x = thumb x）
            .padding(horizontal = 10.dp)
            // 点击轨道 → 跳转（编辑模式下禁用）
            .pointerInput(totalMs, enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width * totalMs).toLong().coerceIn(0L, totalMs))
                }
            }
            // 拖动 thumb → 连续跳转（编辑模式下禁用）
            .pointerInput(totalMs, enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        onSeek((offset.x / size.width * totalMs).toLong().coerceIn(0L, totalMs))
                    },
                    onDrag = { change, _ ->
                        onSeek((change.position.x / size.width * totalMs).toLong().coerceIn(0L, totalMs))
                        change.consume()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val trackH = 4.dp.toPx()
            val thumbR = 9.dp.toPx()
            val thumbX = fraction * size.width
            // 未播放轨道（圆头）
            drawLine(
                color = trackColor,
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = trackH,
                cap = StrokeCap.Round,
            )
            // 已播放轨道
            if (thumbX > 0f) {
                drawLine(
                    color = primary,
                    start = Offset(0f, centerY),
                    end = Offset(thumbX, centerY),
                    strokeWidth = trackH,
                    cap = StrokeCap.Round,
                )
            }
            // thumb 光晕外圈（半透明，提升层次感）
            drawCircle(
                color = primary.copy(alpha = 0.25f),
                radius = thumbR + 4.dp.toPx(),
                center = Offset(thumbX, centerY),
            )
            // thumb 内芯
            drawCircle(color = primary, radius = thumbR, center = Offset(thumbX, centerY))
            // thumb 高光点（左上方小亮点，增强立体感）
            drawCircle(
                color = onPrimary.copy(alpha = 0.35f),
                radius = 3.dp.toPx(),
                center = Offset(thumbX - 2.5.dp.toPx(), centerY - 2.5.dp.toPx()),
            )
        }
    }
}

/** 传输模块：进度条 + 时间 + 五键控制条（合并），整体卡片化，内容随高度自适应缩放 */
@Composable
private fun TransportModule(svc: PlayerService?, state: PlayerState, editing: Boolean) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // 进度条/时间/按钮统一按 scale 缩放：压缩模块高度时整块协调缩小
        val scale = (maxHeight / 176.dp).coerceIn(0.6f, 1.8f)
        // 卡片背景（与波形条同款）：primaryContainer 半透明圆角卡
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(vertical = (6 * scale).dp),
                verticalArrangement = Arrangement.Center,
            ) {
                SeekBar(
                    positionMs = state.positionMs,
                    durationMs = state.durationMs,
                    onSeek = { svc?.seekTo(it) },
                    enabled = !editing,
                    modifier = Modifier.fillMaxWidth(),
                    barHeight = (32 * scale).dp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = (2 * scale).dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        formatDuration(state.positionMs),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = (11 * scale).sp,
                        lineHeight = (13 * scale).sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        formatDuration(state.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = (11 * scale).sp,
                        lineHeight = (13 * scale).sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 辅助键：圆形浅色底（与工具条卡片同色系），与实心播放键形成主次层级
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.size((48 * scale).dp),
                    ) {
                        IconButton(
                            onClick = { svc?.prev() },
                            enabled = !editing,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(XuanjiIcons.PrevTrack, contentDescription = "上一曲", modifier = Modifier.size((28 * scale).dp))
                        }
                    }
                    Spacer(modifier = Modifier.width((12 * scale).dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.size((52 * scale).dp),
                    ) {
                        IconButton(
                            onClick = { svc?.prevSentence() },
                            enabled = !editing,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(XuanjiIcons.Prev, contentDescription = "上一句", modifier = Modifier.size((32 * scale).dp))
                        }
                    }
                    Spacer(modifier = Modifier.width((20 * scale).dp))
                    // 播放键：实心主色，视觉焦点
                    FilledIconButton(
                        onClick = { svc?.togglePlayPause() },
                        enabled = !editing,
                        modifier = Modifier.size((72 * scale).dp),
                    ) {
                        Icon(
                            if (state.isPlaying) XuanjiIcons.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) "暂停" else "播放",
                            modifier = Modifier.size((38 * scale).dp),
                        )
                    }
                    Spacer(modifier = Modifier.width((20 * scale).dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.size((52 * scale).dp),
                    ) {
                        IconButton(
                            onClick = { svc?.nextSentence() },
                            enabled = !editing,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(XuanjiIcons.Next, contentDescription = "下一句", modifier = Modifier.size((32 * scale).dp))
                        }
                    }
                    Spacer(modifier = Modifier.width((12 * scale).dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.size((48 * scale).dp),
                    ) {
                        IconButton(
                            onClick = { svc?.next() },
                            enabled = !editing,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(XuanjiIcons.NextTrack, contentDescription = "下一曲", modifier = Modifier.size((28 * scale).dp))
                        }
                    }
                }
            }
        }
    }
}

/** 句子列表模块：断句/歌词卡片，播放时高亮当前句（弹性模块占剩余空间）。
 *  生成歌词入口已移到顶栏（设置与文件夹之间）。 */
@Composable
private fun SentencesModule(svc: PlayerService?, state: PlayerState, editing: Boolean) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (state.sentences.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        state.recognizing -> "正在识别生成字幕…"
                        state.splitting -> "正在断句分析…"
                        else -> "暂无断句\n（播放后自动分析）"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            // 【结构·阶段1】列表骨架与句子正文走共用实现（见 SentenceListView.kt）：
            // 原来这里手写了一遍 LazyColumn / 滚动跟随 / 文本渲染，阅读模式又抄了一遍。
            val hasLyrics = state.lyrics.isNotEmpty()
            SentenceListPane(
                sentences = state.sentences,
                activeIndex = state.activeSentenceIndex,
                modifier = Modifier.fillMaxSize().weight(1f),
                userScrollEnabled = !editing,
                contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
            ) { index, sentence ->
                val active = state.activeSentenceIndex == index
                Surface(
                    onClick = { svc?.playSentence(index) },
                    enabled = !editing,
                    shape = RoundedCornerShape(14.dp),
                    color = if (active) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
                    tonalElevation = if (active) 0.dp else 1.dp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                ) {
                    SentenceTextBody(
                        index = index,
                        text = sentence.text,
                        active = active,
                        hasLyrics = hasLyrics,
                        emptyLyricText = "（无文本，可在顶栏生成字幕）",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}



/** 工具条选项卡：两行式圆角卡片（功能名 + 数值），内容随 scale 缩放 */
@Composable
private fun ToolChipCard(
    label: String,
    value: String,
    onClick: () -> Unit,
    scale: Float = 1f,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    iconAtBottom: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().height((52 * scale).dp),
        ) {
            if (icon != null && !iconAtBottom) {
                // 图标 + 文字 同行（默认）；文字在上、数值在下
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = (2 * scale).dp),
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size((15 * scale).dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width((4 * scale).dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = (11 * scale).sp,
                        lineHeight = (12 * scale).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = (16 * scale).sp,
                    lineHeight = (18 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else if (icon != null && iconAtBottom) {
                // 播放模式：文字在上、图标在下（与其他三颗数值位置统一，尺寸一致）
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = (11 * scale).sp,
                    lineHeight = (12 * scale).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height((2 * scale).dp))
                Box(modifier = Modifier.height((20 * scale).dp), contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size((20 * scale).dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = (11 * scale).sp,
                    lineHeight = (12 * scale).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height((2 * scale).dp))
                Box(modifier = Modifier.height((20 * scale).dp), contentAlignment = Alignment.Center) {
                    Text(
                        value,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = (12 * scale).sp,
                        lineHeight = (14 * scale).sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** 波形视图：柱状波形 + 句子边界竖线 + 当前句高亮 + 播放进度竖线；断句进行中显示进度 */
@Composable
private fun WaveformView(
    state: PlayerState,
    modifier: Modifier = Modifier,
    onSeek: (Long) -> Unit = {},
    seekEnabled: Boolean = true,
) {
    val waveform = state.waveform
    val sentences = state.sentences
    val totalMs = state.durationMs.coerceAtLeast(1L)
    val barColor = MaterialTheme.colorScheme.primary
    val activeColor = MaterialTheme.colorScheme.tertiary
    val boundaryColor = MaterialTheme.colorScheme.outlineVariant
    val progressColor = MaterialTheme.colorScheme.error

    Box(
        modifier = modifier
            // 点击波形卡 → 按 x 比例跳转进度（编辑模式下禁用）
            .pointerInput(totalMs, seekEnabled) {
                if (!seekEnabled) return@pointerInput
                detectTapGestures { offset ->
                    val t = (offset.x / size.width * totalMs).toLong().coerceIn(0L, totalMs)
                    onSeek(t)
                }
            }
            // 拖动波形卡 → 连续跳转进度（编辑模式下禁用）
            .pointerInput(totalMs, seekEnabled) {
                if (!seekEnabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        val t = (offset.x / size.width * totalMs).toLong().coerceIn(0L, totalMs)
                        onSeek(t)
                    },
                    onDrag = { change, _ ->
                        val t = (change.position.x / size.width * totalMs).toLong().coerceIn(0L, totalMs)
                        onSeek(t)
                        change.consume()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.splitting -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "正在分析断句 ${(state.splitProgress.coerceIn(0f, 1f) * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = state.splitProgress.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp),
                )
            }
            waveform.isNotEmpty() -> Canvas(modifier = Modifier.fillMaxSize()) {
                val n = waveform.size
                if (n == 0) return@Canvas
                val barW = size.width / n
                val stroke = barW * 0.7f
                val centerY = size.height / 2f
                val curIdx = state.activeSentenceIndex
                val activeStart = if (curIdx >= 0) sentences.getOrNull(curIdx)?.let {
                    (it.startMs * n / totalMs).toInt()
                } ?: -1 else -1
                val activeEnd = if (curIdx >= 0) sentences.getOrNull(curIdx)?.let {
                    (it.endMs * n / totalMs).toInt()
                } ?: -1 else -1
                // 上下对称柱状波形（以中线为轴）
                for (i in 0 until n) {
                    val h = (waveform[i] * size.height * 0.92f).coerceAtLeast(2f)
                    val color = if (i in activeStart..activeEnd) activeColor else barColor
                    drawRect(
                        color = color,
                        topLeft = Offset(i * barW + (barW - stroke) / 2f, centerY - h / 2f),
                        size = Size(stroke, h),
                    )
                }
                // 中间轴横线
                drawLine(
                    color = boundaryColor,
                    start = Offset(0f, centerY),
                    end = Offset(size.width, centerY),
                    strokeWidth = 1f,
                )
                // 句子边界竖线
                for (s in sentences) {
                    val x = s.startMs * size.width / totalMs
                    drawLine(
                        color = boundaryColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 2f,
                    )
                }
                // 播放进度竖线（细）+ 顶部圆点，点击/拖动可跳转
                val px = state.positionMs.coerceIn(0L, totalMs) * size.width / totalMs
                drawLine(
                    color = progressColor,
                    start = Offset(px, 10.dp.toPx()),
                    end = Offset(px, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
                drawCircle(
                    color = progressColor,
                    radius = 4.dp.toPx(),
                    center = Offset(px, 4.dp.toPx()),
                )
            }
            sentences.isNotEmpty() -> Text(
                "${sentences.size} 句 · 波形未缓存",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Text(
                "波形区域",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}