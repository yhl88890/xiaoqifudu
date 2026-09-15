package com.xuanji.repeater

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
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
    // 【0915 修复】改为 loadAllHeights()：内部先同步迁移一次再统一读。
    // 原来写 `defaultHeights.mapValues { loadHeight(...) }` 是**逐模块读**，
    // 第一个模块触发迁移、apply() 异步落盘，后面模块可能仍读到旧值
    // ⇒ 老装机上出现"波形条被拉长、重播速度那栏被压扁"这种局部错乱。
    var moduleHeights by remember { mutableStateOf(PlayerLayoutPrefs.loadAllHeights(ctx)) }

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
            // 【0915 收紧】阿亮标红："顶栏 ↔ 标题"这段太空（实测 98px≈37dp）。
            // 其中 20dp 是这里的 top padding，其余是 titleLarge 自带的行框上白。
            // top 20→6dp；底部 bottom 保持 2dp（标题与波形条本来就贴得近，别动）。
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
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
    ModuleColumnHost(
        modules = modules,
        heights = heights,
        layoutEdit = layoutEdit,
        onMove = onMove,
        onHeightChange = onHeightChange,
        idOf = { it.id },
        flexible = { it == PlayerModule.SENTENCES },
        // 波形条允许压得更矮，控制行可用更矮的 44dp，其他模块保持全局最小值
        minHeightOf = {
            when (it) {
                PlayerModule.WAVEFORM -> PlayerLayoutPrefs.MIN_HEIGHT_WAVEFORM
                PlayerModule.TOOLBAR -> PlayerLayoutPrefs.MIN_HEIGHT_TOOLBAR
                PlayerModule.TRANSPORT -> PlayerLayoutPrefs.MIN_HEIGHT_TRANSPORT
                else -> PlayerLayoutPrefs.MIN_HEIGHT
            }
        },
        maxHeight = PlayerLayoutPrefs.MAX_HEIGHT,
        // 【0915】控制行 chip 本体高 = 52*scale，模块却给了 50dp ⇒ 富余高度被 Arrangement.Center
        // 均分到上下。编辑框按这个富余量内缩，阿亮说的"上下白边宽"就没了。
        editInsetOf = { m ->
            if (m == PlayerModule.TOOLBAR) {
                val h = (heights[m] ?: PlayerLayoutPrefs.defaultHeights[m] ?: 50).toFloat()
                val chipH = 52f * (h / 72f).coerceIn(0.55f, 1.8f)
                (((h - chipH) / 2f).coerceAtLeast(0f)).dp
            } else 0.dp
        },
        content = content,
    )
}

/**
 * 通用模块宿主：与音频播放页共用的「拖拽换位 + 拖底边调高」机制。
 * 视频页也走这套（模块类型不同、偏好分开存），保证两处手感完全一致。
 *
 * @param idOf         取模块唯一字符串 id（既作 Compose key 也作高度表键）
 * @param flexible     是否弹性模块（占满剩余空间、无高度把手、不参与调高）
 * @param minHeightOf  各模块的最小高度（dp）
 * @param autoFit      内容撑不下时是否允许模块自动长高（默认 false = 高度被偏好钉死）
 *
 * ⚠️ **autoFit 的由来（0915 实测踩坑）**：视频控制排里除了 5 个按钮，还会临时出现状态文字
 *    （「未配置识别 Key…」/「后台抓取中…」/识别进度/asrMsg）。如果高度被偏好**钉死**，
 *    内容一超高就向上溢出、与按钮/字幕列表重叠（实测状态文字压在按钮圆钮上）。
 *    ⇒ 这类"内容高度会变"的模块必须开 autoFit：**用户拖出来的高度当"下限"**，
 *    内容真需要更高时让它长高；内容矮时仍按用户设定（保持可拖拽调高的手感）。
 */
@Composable
fun <T> ModuleColumnHost(
    modules: List<T>,
    heights: Map<T, Int>,
    layoutEdit: Boolean,
    onMove: (Int, Int) -> Unit,
    onHeightChange: (T, Int) -> Unit,
    idOf: (T) -> String,
    flexible: (T) -> Boolean,
    minHeightOf: (T) -> Int,
    maxHeight: Int = PlayerLayoutPrefs.MAX_HEIGHT,
    gap: Dp = 8.dp,
    autoFit: (T) -> Boolean = { false },
    editHint: String = "拖动移动 · 拖底部≡调高 · 返回退出",
    /**
     * 编辑态高亮框相对模块内缩多少（dp，四边同值）。
     * 【0915 阿亮反馈】控制行模块高 50dp，chip 本体只有 ~36dp，`Arrangement.Center`
     * 把富余高度均分到上下 ⇒ 高亮框比 chip 上下各多 ~7dp 粉边，阿亮说"框比按钮宽/上下白边宽"。
     * 传入这个内缩量，框就贴在 chip 本体上。默认 0 = 撑满模块（波形条/句子列表等本来就没有富余）。
     */
    editInsetOf: (T) -> Dp = { 0.dp },
    content: @Composable (T) -> Unit,
) {
    val autoFitModule = autoFit
    val itemHeights = remember { HashMap<String, Int>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val latestModules by rememberUpdatedState(modules)
    // 模块间距转 px（交换补偿需要算上间距，保证视觉连续）
    val gapPx = with(LocalDensity.current) { gap.toPx() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        modules.forEachIndexed { index, module ->
            // 显式 key：模块换位时 Compose 移动节点而非按位置复用，
            // 否则 pointerInput 会被销毁重建 → 拖动手势中断、draggingId 残留 → 卡死
            key(idOf(module)) {
            val mid = idOf(module)
            val isEdit = layoutEdit
            val isDragging = draggingId == mid
            val moduleHeight = heights[module]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        when {
                            flexible(module) -> Modifier.weight(1f)
                            // autoFit：偏好高度当"下限"，内容更高就自动撑开（防状态文字溢出重叠）
                            autoFitModule(module) -> Modifier.heightIn(min = (moduleHeight ?: 100).dp)
                            else -> Modifier.height((moduleHeight ?: 100).dp)
                        }
                    )
                    .onSizeChanged { itemHeights[mid] = it.height }
                    .zIndex(if (isDragging || isEdit) 2f else 0f)
                    .then(if (isDragging) Modifier.offset { IntOffset(0, dragOffset.roundToInt()) } else Modifier)
                    // 编辑模式：模块任意位置直接拖动换位（无需长按）；非编辑模式不挂手势
                    .pointerInput(mid, layoutEdit) {
                        if (!layoutEdit) return@pointerInput
                        detectDragGestures(
                            onDragStart = {
                                draggingId = mid
                                dragOffset = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount.y
                                val di = latestModules.indexOfFirst { idOf(it) == draggingId }
                                if (di >= 0) {
                                    val selfH = itemHeights[mid] ?: 0
                                    // 下移：拖动模块中心越过下一模块中心才交换（(selfH+nextH)/2 + gap）
                                    // ——窄模块必须拖过宽模块中心，避免"卡在宽模块中间"
                                    if (dragOffset > (selfH + (itemHeights[latestModules.getOrNull(di + 1)?.let { idOf(it) }] ?: selfH)) / 2f + gapPx
                                        && di < latestModules.size - 1
                                    ) {
                                        val nextH = itemHeights[idOf(latestModules[di + 1])] ?: selfH
                                        onMove(di, di + 1)
                                        // 补偿对方高度+间距：交换后模块视觉位置连续跟随手指；
                                        // 补偿量 (selfH-nextH)/2 恰好不超过反向阈值，永不误判反向
                                        dragOffset -= (nextH + gapPx)
                                    }
                                    // 上移：中心越过上一模块中心才交换
                                    else if (dragOffset < -((selfH + (itemHeights[latestModules.getOrNull(di - 1)?.let { idOf(it) }] ?: selfH)) / 2f + gapPx)
                                        && di > 0
                                    ) {
                                        val prevH = itemHeights[idOf(latestModules[di - 1])] ?: selfH
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
                // ⚠️ 这里必须是 Column 而不是 Box：模块内容经常是**多个平级 composable**
                // （视频控制排 = 按钮行 + B站状态 + 识别进度 + asrMsg）。
                // Box 会把平级子元素**叠在同一位置**（实测：红色 asrMsg 直接压在按钮圆钮上）；
                // 而它们在改造前是页面外层 Column 的直接子级、本来就是**纵向依次排列**的。
                // 用 Column 才能保持"上下依次排列"的原行为，配合外层 heightIn 让内容撑高模块。
                Column(modifier = Modifier.fillMaxWidth()) {
                    content(module)
                }
                // 编辑模式：高度把手（弹性模块无把手），zIndex 高于覆盖层
                if (isEdit && !flexible(module)) {
                    HeightHandle(
                        heightDp = moduleHeight ?: 100,
                        onHeightChange = { h -> onHeightChange(module, h) },
                        minHeight = minHeightOf(module),
                        maxHeight = maxHeight,
                        modifier = Modifier.align(Alignment.BottomCenter).zIndex(4f),
                    )
                }
                // 编辑覆盖层：高亮边框 + 提示（编辑模式由设置开关控制，返回键退出）
                // 【0915】按 editInsetOf 内缩，让框贴住内容本体（控制行 chip 上下不再多出粉边）。
                if (isEdit) {
                    val inset = editInsetOf(module)
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(inset)
                            .zIndex(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            editHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            } // key(id) 结束
        }
    }
}

/** 高度调节把手：编辑模式下拖底边上下改变模块高度（minHeight 按模块类型可放宽，波形条允许更矮） */
@Composable
private fun HeightHandle(
    heightDp: Int,
    onHeightChange: (Int) -> Unit,
    minHeight: Int = PlayerLayoutPrefs.MIN_HEIGHT,
    maxHeight: Int = PlayerLayoutPrefs.MAX_HEIGHT,
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
                                    .coerceIn(minHeight, maxHeight)
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
                // 【0915】原来这里 padding(horizontal = 2.dp)，会让编辑高亮框比 chip **左右各宽 2dp**
                // （实测：框 x 42→1037，chip x 48→1031）。阿亮说"框比按钮宽"包含这一条，故去掉。
                modifier = Modifier.fillMaxWidth(),
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
    /** 【0915】轨道垂直位置：false=居中（原行为）；true=贴着顶部 thumb 半径处。
     *  阿亮第二次要求"进度条和功能键中间那块空白再近一点"：轨道居中时盒子下半部
     *  那一半高度全是纯空白，贴上后这段空白直接消失，时间文字能再上提。 */
    trackAtTop: Boolean = false,
    /** 【0915 三次收紧】thumb 半径随模块缩放。原来固定 9dp，模块压缩到 scale<1 时
     *  盒子(18*scale dp)比 thumb 直径还矮，thumb 溢出、盒子却按矮的高度排版，
     *  中间白白多出一段。现在 thumb 与盒子同步缩，盒子 = 2*thumbR 精确贴合。 */
    thumbRadius: Dp = 9.dp,
    /** 【0915 四次收紧】轨道中心距盒子顶的距离（独立于 thumbRadius）。
     *  默认 null = 跟随 thumbRadius（原行为，轨道贴顶画在 thumb 圆心高度）。
     *  指定数值时轨道钉在该位置不动 —— 用于"缩 thumb 但轨道位置纹丝不动"的场景。 */
    trackTopOffset: Dp? = null,
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
            val thumbR = thumbRadius.toPx()
            // trackAtTop：轨道放在距顶 thumbR 处，盒子余下的高度全部让给下方内容，
            // 让"轨道→时间→按钮"三段贴在一起（上方呼吸由外层 Column 的 top padding 负责）。
            // trackTopOffset 优先：调用方要"缩 thumb 但轨道钉住不动"时给死值。
            val centerY = when {
                trackTopOffset != null -> trackTopOffset.toPx()
                trackAtTop -> thumbR
                else -> size.height / 2f
            }
            val trackH = 4.dp.toPx()
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

/** 【0915 三次收紧】自绘圆形功能键：替代 Material3 IconButton/FilledIconButton。
 *  IconButton 自带 48dp 最小触摸目标与内部 padding，会把所在 Row 撑高 —— 可见圆
 *  只有 48*scale dp 时，上方多出来的那层空白就是它撑的。这里圆的尺寸即 Row 高度，
 *  进度条到按钮之间不再有夹层空白。
 *
 *  ⚠️【0915 五次定稿·颜色对齐视频模式】图标色必须**手写注入**，不能靠容器自动色：
 *  视频模式那几个键是 `Surface(color = primaryContainer.copy(alpha = 0.5f))` 里套
 *  IconButton —— Surface 的 contentColorFor 只认**完全相等**的颜色，带 alpha 的副本
 *  匹配不上 primaryContainer ⇒ 回落到 LocalContentColor = onSurface，也就是**黑色**。
 *  实测视频模式辅助键图标最深像素 = (29,27,32)，就是 onSurface。（这是"歪打正着"，
 *  但阿亮看着顺眼，就是它。）
 *  而我上一版用 contentColorFor(primaryContainer) = onPrimaryContainer（深紫 173,93,127），
 *  所以"颜色还是不对"。现在照视频模式走：启用 = onSurface（黑）；禁用 = 同色降透明度，
 *  看起来是"浅黑"（阿亮原话："不能按的时候它是浅。浅黑色吧"）。 */
@Composable
private fun CircleIconBtn(
    size: Dp,
    bg: Color,
    iconTint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    // 触摸热区 = 圆的尺寸本身（48/52/72dp，均 ≥ Android 建议的 48dp 最小触摸目标，够手指用）。
    // 不能用 minimumInteractiveComponentSize：它作用于**布局**尺寸，会把 Row 撑高，
    // 正是上一版要消掉的夹层空白。
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (enabled) bg else bg.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // 禁用态：颜色整体降到 40% 透明 —— 落在淡粉圆底上就是"浅黑"，与视频模式观感一致。
        val tint = if (enabled) iconTint else iconTint.copy(alpha = 0.4f)
        CompositionLocalProvider(LocalContentColor provides tint) { content() }
    }
}

/** 传输模块：进度条 + 时间 + 五键控制条（合并），整体卡片化，内容随高度自适应缩放 */
@Composable
private fun TransportModule(svc: PlayerService?, state: PlayerState, editing: Boolean) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // 进度条/时间/按钮统一按 scale 缩放：压缩模块高度时整块协调缩小
        // 【0915 收紧】基准从 176dp 下调到 140dp：同一实际高度下内容更饱满，
        // 不再"缩在卡片中间、上下各空一大截"（阿亮反馈中间空隙太大）。
        val scale = (maxHeight / 140.dp).coerceIn(0.6f, 1.8f)
        // 卡片背景（与波形条同款）：primaryContainer 半透明圆角卡
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                // 【0915 二次收紧】阿亮再提"进度条和功能键中间还有块空白"，这次直接量了像素：
                // 轨道→时间文字那段空约 50px、时间→按钮圆顶还有约 15px。根因是 SeekBar 盒子
                // 24dp 里轨道居中（上下各空 ~10dp），加上时间行自带的行高富余。
                // 改法：盒子 24→18dp + 轨道贴顶（trackAtTop）把盒内下半格空白消掉；
                // 时间行 lineHeight 压到等于字号；顶部留 4dp 呼吸（否则轨道贴死在卡片上边缘）。
                // 【0915 五次收紧】底部 padding 2dp→0：阿亮"进度条到按键那 57px 再小一点"，
                // 盒子(SeekBar)已收到 14dp(=thumb 直径，无富余)，这里再把整块下方那 2dp 抹掉。
                // 顶部保留 4dp 呼吸，否则轨道贴死在卡片上边缘、显脏。
                modifier = Modifier.fillMaxSize()
                    .padding(top = (4 * scale).dp, bottom = 0.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                SeekBar(
                    positionMs = state.positionMs,
                    durationMs = state.durationMs,
                    onSeek = { svc?.seekTo(it) },
                    enabled = !editing,
                    modifier = Modifier.fillMaxWidth(),
                    // 【0915 五次收紧·阿亮第三次提】他明确只要"进度条到按键那 57px 再小"，
                    // 且**时间位置绝对不许动**（"时间就放在那里，很好"）。逐像素拆过那 57px：
                    //   轨道线底(1023) → 时间文字顶(1048) = 25px
                    //   时间文字本体 1048..1068               = 21px ← 不能压（阿亮红线）
                    //   文字底(1068) → 播放键圆顶(1077)        =  9px ← 已到极限
                    // 那 25px 的构成：轨道线半宽(5px) + thumb 下半圆(19px) + 文字行框上留白(6px)
                    // —— 主体是 thumb 下半圆。盒子高 = 2*thumbR，thumb 越小盒子越矮，整块越紧凑；
                    //   但 thumb 太小会抓不住，7dp(≈18px 直径) 是手感下限。
                    // 本轮：盒子 14dp == thumb 直径(2*7dp)，thumb 恰好占满、无裁切、无富余；
                    //   轨道钉在 7dp（= thumb 圆心），与上一版同高 ⇒ 视觉上轨道不动，
                    //   只有盒子底与时间行整体上提 ~5px。再把 Column 底部 padding 2→0，
                    //   时间行与按键之间那 9px 也一并吃掉一点。
                    barHeight = (14 * scale).dp,
                    trackAtTop = true,
                    thumbRadius = (7 * scale).dp,
                    trackTopOffset = (7 * scale).dp,
                )
                // 【0915 七次·修裁字】上一版栽了：我把时间盒钉成 11dp，但字号是 10sp，
                // 10sp 的字实测行高要 ~13-14px，盒子比字还矮 ⇒ **文字下半截被裁掉**
                // （阿亮截图："0:14 只能显示一半"）。
                // ⚠️ 教训：includeFontPadding=false 只是去掉字体**自带的白**，不是让字形变矮；
                //   盒子高度必须 ≥ 字号 ×1.25，否则必裁。
                // 🔴 【0915 二次修复·真根因】之前盒子高度写的是 `(14*scale).dp`，字号写 `(11*scale).sp`。
                //   问题：**sp 会跟着系统字体缩放走，dp 不会**。阿亮的手机系统字体放大过（常见 1.1~1.3），
                //   于是 11sp 实际渲染成 ~14sp 高，而盒子仍只有 14dp ⇒ 文字底部被裁掉一半
                //   （他原话"时间只能显示一半，底部看不见"）。我这边模拟器字体缩放=1.0，所以看不到。
                //   修法：盒子高度**从字号的真实像素高度推导**，不再用固定的 dp 常量 ⇒ 任何字体缩放都不裁。
                val timeFontSize = (11 * scale).sp
                val timeFontPx = with(LocalDensity.current) { timeFontSize.toPx() }
                // 盒高 = 字形高 × 1.32（留呼吸），并保证不小于原来的 14dp，视觉大小不变。
                val timeBoxH = with(LocalDensity.current) {
                    maxOf(timeFontPx * 1.32f, 14.dp.toPx() * scale).toDp()
                }
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .height(timeBoxH),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatDuration(state.positionMs),
                        style = MaterialTheme.typography.labelSmall.copy(
                            // 去掉字体自带的上白（ascent/descent padding）——让文字更贴盒顶，
                            // 但盒子本身按真实字号算足了高度，**任何系统字体缩放都不裁字**。
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                        ),
                        fontSize = timeFontSize,
                        // lineHeight = 字号 ×1.2：行盒略高于字形，避免上下被切。
                        lineHeight = (11 * scale * 1.2f).sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        formatDuration(state.durationMs),
                        style = MaterialTheme.typography.labelSmall.copy(
                            // 去掉 Material 文字默认的字体上下白（ascent/descent padding）。
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                        ),
                        fontSize = timeFontSize,
                        lineHeight = (11 * scale * 1.2f).sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 【0915 三次收紧】全部改用自绘圆 + clickable，替掉 IconButton / FilledIconButton。
                    // 根因：Material3 的 IconButton 自带 48dp 最小触摸目标 + 内部 padding，
                    // 会把所在 Row 撑高；可见圆只有 48*scale(~38dp)，上面那层"空"就是它撑出来的。
                    // 现在圆的尺寸 == Row 的高度，进度条到按钮之间不再有夹层空白。
                    CircleIconBtn(
                        size = (48 * scale).dp,
                        bg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        // 【0915 五次定稿·对齐视频模式】图标色 = onSurface（实测 (29,27,32)，即黑色）。
                        // 视频模式那几个键底色是 primaryContainer.copy(alpha=0.5f)，Surface 的
                        // contentColorFor 只认完全相等色 ⇒ 匹配不上、回落 onSurface，凑巧就是黑色；
                        // 阿亮看着顺眼，就照它做。禁用态在 CircleIconBtn 里降透明度成"浅黑"。
                        iconTint = MaterialTheme.colorScheme.onSurface,
                        enabled = !editing,
                        onClick = { svc?.prev() },
                    ) { Icon(XuanjiIcons.PrevTrack, contentDescription = "上一曲", modifier = Modifier.size((28 * scale).dp)) }
                    Spacer(modifier = Modifier.width((12 * scale).dp))
                    CircleIconBtn(
                        size = (52 * scale).dp,
                        bg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        iconTint = MaterialTheme.colorScheme.onSurface,
                        enabled = !editing,
                        onClick = { svc?.prevSentence() },
                    ) { Icon(XuanjiIcons.Prev, contentDescription = "上一句", modifier = Modifier.size((32 * scale).dp)) }
                    Spacer(modifier = Modifier.width((20 * scale).dp))
                    // 播放键：实心主色，视觉焦点（图标白 = onPrimary，与旧版 FilledIconButton 一致）
                    CircleIconBtn(
                        size = (72 * scale).dp,
                        bg = MaterialTheme.colorScheme.primary,
                        iconTint = MaterialTheme.colorScheme.onPrimary,
                        enabled = !editing,
                        onClick = { svc?.togglePlayPause() },
                    ) {
                        Icon(
                            if (state.isPlaying) XuanjiIcons.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) "暂停" else "播放",
                            modifier = Modifier.size((38 * scale).dp),
                        )
                    }
                    Spacer(modifier = Modifier.width((20 * scale).dp))
                    CircleIconBtn(
                        size = (52 * scale).dp,
                        bg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        iconTint = MaterialTheme.colorScheme.onSurface,
                        enabled = !editing,
                        onClick = { svc?.nextSentence() },
                    ) { Icon(XuanjiIcons.Next, contentDescription = "下一句", modifier = Modifier.size((32 * scale).dp)) }
                    Spacer(modifier = Modifier.width((12 * scale).dp))
                    CircleIconBtn(
                        size = (48 * scale).dp,
                        bg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        iconTint = MaterialTheme.colorScheme.onSurface,
                        enabled = !editing,
                        onClick = { svc?.next() },
                    ) { Icon(XuanjiIcons.NextTrack, contentDescription = "下一曲", modifier = Modifier.size((28 * scale).dp)) }
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