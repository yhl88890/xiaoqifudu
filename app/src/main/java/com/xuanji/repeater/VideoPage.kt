package com.xuanji.repeater

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.view.Surface
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.util.Log
import java.io.File
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// ===== 视频播放控制 + 视频页 UI（从 MainActivity 纯搬迁；可见性 private->internal，逻辑零改动） =====

// ===== 视频播放控制（文件级）：控制条 / 系统媒体中心 / 音频焦点 共用同一套状态 =====
/** 【0903】VideoView 方案：Surface 由 VideoView 自己管理，无需手动 Surface/SurfaceTexture。 */

/** 视频真正开始播放时，把控制器塞给 PlayerService（接管媒体中心/通知/音频焦点） */
internal fun attachVideoPlayback() {
    PlayerService.instance?.attachVideoController(videoMediaController)
}

/** 释放视频播放器（切出视频模式 / Activity 销毁时）。单播放器架构：只有一个播放器，无后台第二播放器。 */
internal fun releaseVideoPlayer() {
        // 【0915】播放器都没了，复读任务自然作废；不清的话切回来会带着旧句尾乱跳。
        clearVideoSentenceRepeat()
        runCatching { videoMpRef[0]?.setSurface(null) }
        runCatching { videoMpRef[0]?.release() }
        videoMpRef[0] = null
        videoMpSourceUri = null
        // 播放器释放 → 绑定/纹理/Surface 记录一并作废（此刻 TextureView 也随页面销毁了，
        // 下次进入会拿到新的 SurfaceTexture 并重新建 Surface 绑定，不会误判成"同一个纹理"）。
        videoMpBoundSurface = null
        videoSurfaceRef[0] = null
        videoSurfaceTexRef = null
        videoPrepared = false
        videoDuration = 0L
        PlayerService.instance?.detachVideoController()
    }

internal fun videoTogglePlay() {
    val mp = videoMpRef[0] ?: return
    if (videoPlaying) {
        currentPos = mp.currentPosition.toLong(); mp.pause(); videoPlaying = false
    } else {
        mp.start(); videoPlaying = true
        PlaybackSession.onVideoStarted()   // Phase3-步骤1：手动点播放
        // 【0903 用户反馈】系统媒体中心一直显示音频、控制音频：之前 attach 只发生在「打开视频自动播放
        // (onPrepared && videoPlaying)」一条路径；从文件夹选视频后手动点播放走的是这里 → 视频在响但
        // PlayerService 仍以为是音频模式。任何「视频真正开始发声」的路径都必须接管媒体中心/通知/焦点。
        attachVideoPlayback()
    }
    PlayerService.instance?.notifyVideoStateChanged()
}

/** 精确 seek（API 26+ 用 SEEK_CLOSEST 取最近关键帧） */
internal fun videoSeek(targetMs: Long, whence: String = "?" ) {
    val mp = videoMpRef[0] ?: return
    val cur = runCatching { mp.currentPosition.toLong() }.getOrDefault(-1L)
    Log.d("XJ_SEEK", "videoSeek whence=$whence target=$targetMs cur=$cur dir=${if (targetMs < cur) "BACK" else "FWD"}")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        runCatching { mp.seekTo(targetMs, MediaPlayer.SEEK_CLOSEST) }
    } else {
        runCatching { mp.seekTo(targetMs.toInt()) }
    }
}

internal fun videoSeekBy(delta: Int) {
    val mp = videoMpRef[0] ?: return
    val p = (mp.currentPosition + delta).coerceIn(0, mp.duration)
    videoSeek(p.toLong()); currentPos = p.toLong()
}

/** 跳到第 idx 句并高亮（与旧 VideoView 版逻辑一致：落点异步复核，避免跳到上一句中间） */
internal fun videoSeekToSentence(idx: Int) {
    val mp = videoMpRef[0] ?: return
    if (idx !in sentences.indices) return
    val target = sentences[idx].startMs
    val s = sentences[idx]
    val stamp = System.currentTimeMillis()
    Log.d("XJ_SEEK", "videoSeekToSentence idx=$idx target=$target")
    videoSeek(target, "sentenceJump(idx=$idx)")
    mp.start(); videoPlaying = true
    PlaybackSession.onVideoStarted()   // Phase3-步骤1：视频跳句
    attachVideoPlayback()   // 跳句也会发声：同步接管媒体中心/焦点
    currentPos = target
    videoSentenceIndexGlobal = idx
    videoLastSeekAt = stamp
    videoAsrScope.launch {
        for (tryNo in 0 until 5) {
            delay(150)
            if (videoLastSeekAt != stamp) return@launch
            val p = mp.currentPosition.toLong()
            val ok = p >= s.startMs - 300 && p <= s.endMs + 300
            if (ok) return@launch
            val nextTarget = if (p < s.startMs) {
                (s.startMs + (s.endMs - s.startMs) / 2).coerceAtMost(s.endMs - 1)
            } else s.startMs
            videoSeek(nextTarget)
            videoLastSeekAt = stamp
        }
    }
}

internal fun videoJumpSentence(step: Int) {
    // 【0915】「上一句」要先落到目标句、再交出复读任务。
    // 关键：videoSeekToSentence 里的落点异步复核协程一失败就会再叫一次 videoSeek（改 videoLastSeekAt），
    // 若在它之后立即设 videoRepeatStopAt，轮询可能先把播放头判成「已过句尾」→ 当场误触发复读跳回。
    // 但方向是反的：跳「上一句」时位置在目标句【之前】，复核只会往后微调，绝不会越过句尾；
    // 所以先 seek、后设复读标记是安全的。而跳「下一句」时位置在目标句之后（有越过句尾风险），
    // 故只在 step < 0 时挂复读，这也正是本功能要的范围 —— 只改上一句。
    if (sentences.isNotEmpty()) {
        val idx = (videoSentenceIndexGlobal + step).coerceIn(0, sentences.size - 1)
        // 任何跳句都先撤掉上一次的复读任务（用户手动跳走 = 放弃复读；也是「连续点上一句」重置计数的地方）。
        // 它同时会解除高亮钉子；真复读时由下面重新钉上，不复读（如点下一句）则由位置跟随自然接管。
        clearVideoSentenceRepeat()
        // seek 前就把当前位置挪到目标句起点：轮询每 200ms 读一次 mp.currentPosition，
        // seek 是异步的，若不预置，seek 未落地前轮询仍读到旧位置（>目标句尾）会当场触发复读跳回。
        currentPos = sentences[idx].startMs
        videoSeekToSentence(idx)
        // 【与音频模式共用同一份设置】次数直接读 RepeatPrefs —— 就是音频模式「重播次数」那一栏写的那本账，
        // 不新增任何设置项、不新增存储：音频调到 ×5，这里就是 ×5。
        if (step < 0) {
            val ctx = PlaybackSession.context
            val times = if (ctx != null) RepeatPrefs.loadTimes(ctx) else 1
            val s = sentences[idx]
            if (times > 1 && s.endMs > s.startMs) {
                videoRepeatStopAt = s.endMs
                videoRepeatStartMs = s.startMs
                videoRepeatLeft = times - 1
                // 钉住高亮：复读 N 遍期间高亮稳稳停在目标句，不再被轮询按位置来回改（对齐音频模式）
                videoSentencePin = idx
                android.util.Log.d("XJ_REPEAT", "上一句复读 idx=$idx times=$times start=${s.startMs} end=${s.endMs}")
            }
        }
    } else {
        clearVideoSentenceRepeat()
        videoSeekBy(if (step > 0) 10000 else -10000)
    }
}

/** 视频模式页：本地视频播放（框架 VideoView，0903 回退修复黑屏）。字幕导入 / ASR 自动断句已接入。
 *  控制条与音频模式统一：上一曲 / 上一句(长按快退) / 播放 / 下一句(长按快进) / 下一曲。
 *  全屏模式（需求6）：视频填满页面，仅在全屏时把字幕叠在画面上，并提供字幕开关。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun VideoPage(readingMode: Boolean = false, layoutEdit: Boolean = false) {
    val subtitleListState = rememberLazyListState()
    val context = LocalContext.current

    // 全屏：强制横屏（SENSOR_LANDSCAPE 锁定横屏，左右横屏都跟手）；退出全屏强制回竖屏。
    // 关键：Activity 已在 Manifest 声明 configChanges="orientation|screenSize"，旋转不重建 Activity，
    // 否则重建会把 page 等 remember 状态打回默认（音频模式），且 VideoView 被卸载 → 全屏瞬间闪退回竖屏+音频。
    val activity = (context as? Activity)
    DisposableEffect(videoFullscreen) {
        // 全屏=横屏；非全屏=强制竖屏（即使手机还横着也转回，避免退出全屏卡在横屏）
        activity?.requestedOrientation = if (videoFullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        // 全屏时隐藏系统状态栏：否则顶部「字幕开关 / 退出全屏」两个按钮会被状态栏压住点不到。
        // 退出全屏再恢复显示。用 InsetsController 而非 FLAG_FULLSCREEN，兼容 Android 11+。
        activity?.window?.let { w ->
            val view = w.decorView ?: return@let
            val insets = WindowCompat.getInsetsController(w, view)
            if (videoFullscreen) insets.hide(WindowInsetsCompat.Type.statusBars())
            else insets.show(WindowInsetsCompat.Type.statusBars())
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.window?.let { w ->
                val view = w.decorView ?: return@let
                WindowCompat.getInsetsController(w, view).show(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    // 本次 VideoPage 实例自己创建的 VideoView。
    // 关键：videoViewRef / videoMpRef 是文件级的，Compose 重建页面的顺序不保证「旧页 onDispose」
    // 一定早于「新页 AndroidView factory」——实测新页先把 videoViewRef[0] 换掉了，
    // 旧页 onDispose 再去 stop 就停了个寂寞，旧播放器一直在后台跑，
    // 于是「切回来还在自动播放 / 播放键显示未播放、要点一两次才同步」。
    // 单播放器：SurfaceView 由 AndroidView 直接持有并通过 videoSurfaceRef 暴露，无需 per-instance 引用。

    // 是否已完成 prepare（setDataSource 后到 onPrepared 之前为 false）。
    // 关键：切回视频页重新绑定 MediaPlayer 时，进度轮询会在 onPrepared 之前就把 currentPos
    // 写成「新播放器还没 seek 的 0」，把续播位置冲掉 → 正在播放时切走再切回会从头播（暂停却正常，
    // 因为暂停时轮询不跑）。用此标志让轮询在 prepared 之前不要写 currentPos。
    // videoPrepared / videoDuration 已提升为文件级（媒体中心也要读）。

    // 离开视频页（切到别的模式/退出）时彻底停掉并释放视频播放器与 Surface。
    // 注意：这里不改 videoPlaying——保留用户意图（暂停的回来仍暂停，播放的回来继续播）。
    // 同时把视频控制器从 PlayerService 摘掉，恢复纯音频的媒体中心/通知/焦点。
    DisposableEffect(Unit) {
        onDispose {
            val mp = videoMpRef[0]
            if (mp != null) runCatching { if (mp.duration > 0) currentPos = mp.currentPosition.toLong() }
            // 【0910 根治·后台存活】仅当「前台导航离开视频页(切音频/文件夹/生词)」才释放播放器；
            // 切后台只触发 onPause(appInBackground=true)不触发 onPause 的导航离开为 false → 释放，
            // 切后台时 appInBackground=true → 不释放，播放器保留、回前台走 REUSE 零 seek（「拉回前台就接着播」）。
            // onPause 只在 Activity 真正失去前台时调用，App 内切换页面不触发，故可区分二者。
            // 真正退出 App 由 onDestroy(isFinishing) 释放。
            val navLeave = !appInBackground
            Log.d("XJ_VID", "DISPOSE isPlaying=${videoPlaying} bg=$appInBackground release=$navLeave")
            if (navLeave) {
                releaseVideoPlayer()
            }
            saveVideoPrefs(context)
        }
    }

    // 当前句索引：播放时由进度轮询按 currentPos 所在区间更新（高亮跟随播放）；
    // 跳句/点句时由用户显式设定。用独立状态而非每次从 currentPos 现算，
    // 否则 seek 后轮询会把 currentPos 覆盖回旧句，造成「下一句重复当前句 / 点句播上一句」。
    // 最近一次手动 seek 的时间戳（文件级 videoLastSeekAt）：seek 后时间内不让轮询改 index。
    fun computeSentence(pos: Long): Int {
        if (sentences.isEmpty()) return -1
        for (i in sentences.indices) {
            if (pos >= sentences[i].startMs && pos < sentences[i].endMs) return i
        }
        return sentences.indexOfLast { pos >= it.startMs }.coerceAtLeast(0)
    }

    // 进页面时按当前进度把高亮拉回正确句子（冷启动续播 / 切回视频页都靠它）
    LaunchedEffect(Unit) {
        if (sentences.isNotEmpty() && currentPos > 0) {
            videoSentenceIndexGlobal = computeSentence(currentPos)
        }
    }

    var controlsVisible by remember { mutableStateOf(false) }
    // 字幕列表内联展开（对齐阅读模式：长按展开翻译 + 点词选词）
    var expandedVideoIndex by remember { mutableStateOf(-1) }

    // 长按快进/快退：按住期间实时变速播放，松手恢复。fastScrubbing 控制进度条显隐。
    var fastScrubbing by remember { mutableStateOf(false) }
    var rewindJob by remember { mutableStateOf<Job?>(null) }
    val FF_SPEED = 2.0f        // 快进倍速（MediaPlayer 倍速上限 2.0，超过抛异常）
    val REWIND_STEP_MS = 250L  // 快退：每次回拖的时长
    val REWIND_TICK_MS = 80L   // 快退：回拖间隔（约 3 倍速回退）

    // 同级视频列表里当前位置（上一曲/下一曲用）
    val curFolderIdx = videoFolderVideos.indexOfFirst { it.path == videoPath }
    val hasPrevVideo = curFolderIdx > 0
    val hasNextVideo = curFolderIdx in 0 until videoFolderVideos.size - 1

    val pageScope = rememberCoroutineScope()

    // 播放控制：togglePlay / seek / jumpSentence / seekToSentence 已抽到文件级（VideoPage 上方），
    // 供控制条 / 系统媒体中心 / 音频焦点共用。本页只保留 UI 专属的快进/快退与上下曲。

    // 实时快进：按住下一句时把播放速度提到 2x，画面跟着实时快放；松手由 stopFast 恢复。
    fun startFastForward() {
        val mp = videoMpRef[0] ?: return
        if (!videoPlaying) { runCatching { mp.start() }; videoPlaying = true; PlaybackSession.onVideoStarted(); attachVideoPlayback() }
        runCatching { mp.playbackParams = mp.playbackParams.setSpeed(FF_SPEED) }
        fastScrubbing = true
    }
    // 实时快退：MediaPlayer 不支持反向播放，用「按住期间持续回拖进度」模拟，约 3 倍速回退。
    fun startRewind() {
        if (videoMpRef[0] == null) return
        fastScrubbing = true
        rewindJob?.cancel()
        rewindJob = pageScope.launch {
            while (isActive) {
                videoSeekBy(-REWIND_STEP_MS.toInt())
                delay(REWIND_TICK_MS)
            }
        }
    }
    // 松手：停掉快退协程、恢复常速、隐藏进度条。
    fun stopFast() {
        rewindJob?.cancel(); rewindJob = null
        runCatching { videoMpRef[0]?.let { mp -> mp.playbackParams = mp.playbackParams.setSpeed(videoSpeed) } }
        fastScrubbing = false
    }

    fun prevVideo() {
        if (hasPrevVideo) openVideoFromLibrary(context, videoFolderVideos[curFolderIdx - 1], videoFolderVideos)
    }
    fun nextVideo() {
        if (hasNextVideo) openVideoFromLibrary(context, videoFolderVideos[curFolderIdx + 1], videoFolderVideos)
    }

    // 进度轮询：每 200ms 刷新 currentPos；开启单句循环时，到达句尾自动回到句首
    val sentencesState = rememberUpdatedState(sentences)
    val loopState = rememberUpdatedState(loopSentence)
    LaunchedEffect(videoPlaying, videoUri) {
        var lastSave = 0L
        var lastPosNotify = 0L
        while (videoPlaying) {
            delay(200)
            // 视频刚打开时 videoMpRef[0] 尚未被 onPrepared 赋值（mp 为 null），用 continue 等其就绪，
            // 不能用 break 永久退出循环（之后 mp 就绪也不会重启，导致 currentPos 永远停留在旧值）。
            val mp = videoMpRef[0] ?: continue
            val pos = runCatching { mp.currentPosition.toLong() }.getOrDefault(currentPos)
            currentPos = pos
            val now = System.currentTimeMillis()
            if (now - lastSave > 2000) { lastSave = now; saveVideoPrefs(context) }
            // 【媒体卡片进度同步】每 1s 把最新位置推给 MediaSession/通知，使下拉卡片进度条跟实际播放对齐
            if (now - lastPosNotify > 1000) { lastPosNotify = now; PlayerService.instance?.notifyVideoStateChanged() }
            // seek 后 2000ms 内不打扰 videoSentenceIndex，避免刚跳过去又被轮询拉回旧句。
            // seekTo 是异步的，连续点下一句时 mp.currentPosition 可能仍停留在旧句，2000ms
            // 足够本地视频完成 seek；之后轮询再按真实位置修正。
            // 【0915 高亮钉住】复读期间（videoSentencePin >= 0）跳过位置推断 —— 否则每回到句首，
            // 过了保护期就会被重算，高亮在目标句/下一句间来回跳（阿亮报的 bug）。
            // 【0915 高亮钉住】复读期间（videoSentencePin >= 0）高亮定死在目标句，不做位置推断 ——
            // 否则每回到句首，过了保护期就会被重算，高亮在目标句/下一句间来回跳（阿亮报的 bug）。
            // 高亮本身已由 videoSeekToSentence 落到目标句（那里有 videoSentenceIndexGlobal = idx），
            // 这里只需「按兵不动」，语义与音频模式复读期间 currentSentenceIndex 定死一致。
            if (videoSentencePin >= 0) {
                // 钉住中：不动
            } else if (now - videoLastSeekAt > 2000) {
                videoSentenceIndexGlobal = computeSentence(pos)
            }
            if (loopState.value && sentencesState.value.isNotEmpty()) {
                val list = sentencesState.value
                val idx = computeSentence(pos)
                if (idx >= 0 && pos >= list[idx].endMs) {
                    videoSeek(list[idx].startMs, "tickerLoop(idx=$idx)")
                    currentPos = list[idx].startMs
                    videoLastSeekAt = now
                }
            }
            // 【0915 上一句复读】播到目标句尾 → 回句首重播，直到把「重播次数」用完。
            // 与上面单句循环的分工：单句循环开关（跟读用）优先级更高，开关打开时不走这里，
            // 免得两套「回句首」逻辑同时抢播放头。两者同时生效时表现为单句循环（读个够）。
            val repeatStop = videoRepeatStopAt
            if (repeatStop != null && pos >= repeatStop) {
                if (videoRepeatLeft > 0 && !loopState.value) {
                    // 还欠着：回句首再播一遍（跟点句一样预置 currentPos，避免 seek 未落地时轮询读到旧位置连跳）
                    videoRepeatLeft--
                    val start = videoRepeatStartMs
                    videoSeek(start, "tickerRepeat(left=${videoRepeatLeft + 1})")
                    currentPos = start
                    videoLastSeekAt = now
                    android.util.Log.d("XJ_REPEAT", "复读回跳 start=$start 剩余=${videoRepeatLeft + 1}")
                } else {
                    // 复读完成（或遇单句循环）：撤掉复读任务 + 解除高亮钉子
                    // （clearVideoSentenceRepeat 内部已含解除钉子，对齐音频模式
                    //  handleSentenceBoundary 里 currentSentenceIndex = -1 的语义）
                    clearVideoSentenceRepeat()
                    android.util.Log.d("XJ_REPEAT", "复读结束，高亮交回位置跟随")
                }
            }
            // 阅读(视频源)：点句起播记一句尾，轮询到句尾自动暂停（对齐音频阅读「读一句停一句」体验）。
            // 用 readingFromVideo（文件级）判断：它 true 即等于「当前在阅读(视频源)模式」，VideoPage 内访问不到 setContent 的局部 page。
            val stopAt = videoSentenceStopAt
            if (readingFromVideo && stopAt != null && pos >= stopAt) {
                videoTogglePlay()   // 当前在播 → 暂停
                videoSentenceStopAt = null
            }
        }
    }

    // 装载 / 换片：自建 MediaPlayer + SurfaceView（单播放器·彻底修）。
    // 重要：续播严格尊重 videoPlaying 状态——暂停后返回/切回绝不自动开始；
    // 只有打开新视频(openVideoFromLibrary 会把 videoPlaying 置 true) 或本来就在播放才续播。
    LaunchedEffect(videoUri) {
        val uri = videoUri ?: return@LaunchedEffect
        videoPrepared = false
        // 冷启/换片才清权威续播位；若是「回前台后台接管」(videoHandedToBg=true，Activity 重建常见) 则保留，
        // 交给 onPrepared 续播分支精确接棒，否则会掉回 0/旧位重读。
        if (!videoHandedToBg) videoResumePos = 0
        // 【媒体中心卡片点回前台 修复】续播位以 SP 里持续落盘的 pos 为权威源，而非依赖全局 currentPos 变量：
        // 重建时 currentPos 这个顶层 var 可能在 loadVideoPrefs 赋值前就被 loadVideo 读到 0，导致从头播。
        // SP.pos 由播放中每 2s 的轮询器持续写入，是最可靠的续播位置。
        val spResumePos = runCatching {
            context.getSharedPreferences(PREFS_VIDEO, Context.MODE_PRIVATE).getLong("pos", 0L)
        }.getOrDefault(0L)
        // SurfaceView 可能还没就位（拿到 Surface 才能渲染），等一下；等不到也继续（surfaceCreated 会后续接上）
        var waited = 0
        while (videoSurfaceRef[0] == null && waited < 500) { delay(50); waited += 50 }
        // 【0910 回前台无重建·无 seek】同一个播放器还活着（进程没死，Activity 重建/回前台）就直接复用，
        // 只重新接画面，绝不新建、绝不 seek —— 即「把后台播放器拉出来」。
        val existingPlayer = videoMpRef[0]
        if (existingPlayer != null && videoMpSourceUri == uri) {
            Log.d("XJ_VID", "loadVideo REUSE existing player; cur=${runCatching { existingPlayer.currentPosition }.getOrDefault(-1)} playing=$videoPlaying")
            videoPrepared = true
            videoDuration = runCatching { existingPlayer.duration.toLong() }.getOrDefault(videoDuration).coerceAtLeast(0)
            // 【方案B】幂等绑定：Surface 常驻且与上次是同一个 → 不会真正 setSurface，
            // 解码器不重新出帧 → 不重解关键帧 → 回前台画面无缝（不重播当句）。
            videoSurfaceRef[0]?.let { bindVideoSurface(it, "REUSE") }
            if (videoPlaying) runCatching { existingPlayer.start() } else runCatching { existingPlayer.pause() }
            attachVideoPlayback()
            PlayerService.instance?.notifyVideoStateChanged()
            return@LaunchedEffect
        }
        // 换片/冷启：释放旧播放器，不留孤儿
        runCatching { existingPlayer?.release() }
        videoMpRef[0] = null
        val resumePos = if (videoResumePos > 0) videoResumePos else (if (currentPos > 0) currentPos else spResumePos)
        val startPlaying = videoPlaying
        Log.d("XJ_VID", "loadVideo begin: uri=$uri currentPos=$currentPos videoResumePos=$videoResumePos videoHandedToBg=$videoHandedToBg startPlaying=$startPlaying")
        val speed = videoSpeed
        val ctx = context
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA).build())
            mp.setDataSource(ctx, uri)
        } catch (e: Throwable) {
            Log.w("XJ_VID", "setDataSource failed: ${e.message}")
            videoErrorMessage = "无法打开视频：${e.message ?: "未知错误"}"
            videoPlaying = false
            runCatching { mp.release() }
            PlayerService.instance?.notifyVideoStateChanged()
            return@LaunchedEffect
        }
        videoMpRef[0] = mp
        videoMpSourceUri = uri
        // 已有 Surface 立即绑定（TextureView 已就位）；否则等 onSurfaceTextureAvailable 再绑。
        // ⚠️ 必须放在 videoMpRef[0] = mp 之后——bindVideoSurface 靠 videoMpRef[0] 取播放器，
        // 放前面会取到 null 而静默跳过绑定 → 新建播放器永远没画面（黑屏）。
        videoMpBoundSurface = null   // 新播放器，强制绑定一次
        videoSurfaceRef[0]?.let { bindVideoSurface(it, "CREATE") }
        mp.setOnPreparedListener { mpp ->
            videoDuration = mpp.duration.toLong().coerceAtLeast(0)
            videoPrepared = true
            runCatching { mpp.playbackParams = mpp.playbackParams.setSpeed(speed) }
            // 续播位置：优先权威续播位；否则用 currentPos（冷启恢复），片尾附近回开头防黑屏
            val target = if (videoResumePos > 0) videoResumePos else (if (resumePos > 0) {
                val nearEnd = resumePos >= videoDuration - 2500 || resumePos >= (videoDuration * 98 / 100)
                if (nearEnd) 0L else resumePos
            } else 0L)
            Log.d("XJ_VID", "onPrepared target=$target resumePos=$resumePos spResumePos=$spResumePos dur=$videoDuration videoResumePos=$videoResumePos nearEnd=${(resumePos >= videoDuration - 2500 || resumePos >= (videoDuration * 98 / 100))}")
            if (target > 0) {
            // 【0910】冷启/换片续播：用 SEEK_NEXT_SYNC（只往【前】/不动，绝不回退）。
            // 之前用 SEEK_CLOSEST 会被吸到「离 target 最近的关键帧」——当 target 落在某句开头附近时
            // 会回退到上一句/句首关键帧，造成「回前台重播那一句」（第一句最明显，会被吸到 0 整句重播）。
            // 后台回前台走 REUSE 分支不会到这里（不重建不 seek），此分支仅进程被杀冷启重建时触发，
            // 用 NEXT_SYNC 最多跳一个 GOP(0.5~2s) 前进，绝不重播已听内容。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching { mpp.seekTo(target, MediaPlayer.SEEK_NEXT_SYNC) }
            } else {
                runCatching { mpp.seekTo(target.toInt()) }
            }
                currentPos = target
            }
            videoResumePos = 0                    // 消费掉，避免后续冷启误用
            if (startPlaying) {
                mpp.start()
                attachVideoPlayback()
            } else {
                runCatching { mpp.pause() }
                // 即便暂停也要接管媒体中心/通知/焦点，并停掉旧音频（防幽灵双声）：
                // 进视频模式但视频处于暂停态时，若不接管，残留的音频会继续响。
                attachVideoPlayback()
            }
            PlayerService.instance?.notifyVideoStateChanged()
        }
        mp.setOnCompletionListener {
            videoPlaying = false
            PlayerService.instance?.notifyVideoStateChanged()
        }
        // 【0903 黑屏修复】MediaPlayer 错误回调：文件读不到/权限拒/解码失败时显示具体错误到视频窗口
        // （替代静默黑屏）；并给「换视频 / 重试」按钮（见 VideoStateOverlay）。
        mp.setOnErrorListener { _, what, extra ->
            val hint = when (extra) {
                MediaPlayer.MEDIA_ERROR_IO ->
                    "无法读取视频文件（权限不足或文件已被删除）"
                MediaPlayer.MEDIA_ERROR_MALFORMED ->
                    "视频格式损坏或不被支持"
                MediaPlayer.MEDIA_ERROR_UNSUPPORTED ->
                    "视频编码不支持（试试转码成标准 mp4）"
                MediaPlayer.MEDIA_ERROR_TIMED_OUT ->
                    "读取超时，请重试"
                else -> "播放失败（what=$what extra=$extra）"
            }
            Log.w("XJ_VID", "ERROR what=$what extra=$extra hint=$hint")
            videoErrorMessage = hint
            videoPlaying = false
            PlayerService.instance?.notifyVideoStateChanged()
            true  // 返回 true 表示已处理，阻止默认 onCompletion 触发
        }
        runCatching { mp.prepareAsync() }
    }

    // 字幕列表自动滚动到当前句
    LaunchedEffect(videoSentenceIndexGlobal) {
        if (videoSentenceIndexGlobal >= 0) subtitleListState.animateScrollToItem(videoSentenceIndexGlobal)
    }

    // 【1007】识别进度/结果浮层（显示在视频画面正中间的半透明胶囊，替代 TRANSPORT 模块里
    // 那两行状态文字 —— 阿亮："不占下面一行字的空间，抓取/识别时在画面上就能看到进度，
    // 抓取完了自动消掉"）。
    // 显示规则：识别中常驻（黑底白字+转圈）；完成后 asrMsg 挂一段时间自动消失 ——
    // 成功（「识别完成」开头）3 秒；其余（出错/字幕缺失/未配 Key…）红底 8 秒，给足看报错的时间。
    var asrOverlayVisible by remember { mutableStateOf(false) }
    LaunchedEffect(recognizing, asrMsg) {
        when {
            recognizing -> asrOverlayVisible = true
            asrMsg.isNotEmpty() -> {
                asrOverlayVisible = true
                delay(if (asrMsg.startsWith("识别完成")) 3000L else 8000L)
                asrOverlayVisible = false
            }
            else -> asrOverlayVisible = false
        }
    }

    // 【0915 宽度对齐】水平内边距 12→16dp，与音频页（PlayerPage 外层 padding horizontal = 16.dp）
    // 完全一致：实测音频模块统一 996px 宽（1080-42*2），视频页原来 12dp 只有 1016px，
    // 两个页面切换时模块左右边缘差 20px，看着"视频这边胖一圈"。
    // 【0915 底部不再留白】原来垂直 12dp 四边都有，底部那 12dp 叠在底栏上方形成一道大空档
    // （实测控制排卡底边 y2143、底栏 y2250，中间空 107px；音频页无垂直边距只空 62px）。
    // 阿亮："跟底栏之间距离太高了，参照音频模式" ⇒ 只留顶部 12dp，底部交还给底栏自己收。
    Column(
        modifier = Modifier.fillMaxSize().padding(
            start = if (videoFullscreen) 0.dp else 16.dp,
            end = if (videoFullscreen) 0.dp else 16.dp,
            top = if (videoFullscreen) 0.dp else 12.dp,
            bottom = 0.dp,
        )
    ) {
        Surface(
            modifier = if (videoFullscreen) Modifier.fillMaxSize()
                       else Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            shape = RoundedCornerShape(14.dp),
            color = Color.Black,
        ) {
            Box(modifier = Modifier.fillMaxSize()
                .clickable { controlsVisible = !controlsVisible }) {
                if (videoUri != null) {
                    AndroidView(
                        factory = { ctx ->
                            // 【方案B】TextureView + 常驻 SurfaceTexture：Surface 不随前后台销毁/重建，
                            // 回前台无需重绑 → 不重解关键帧 → 画面无缝（不重播当句）。
                            TextureView(ctx).also { tv ->
                                tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                                        // 回前台若还是同一个纹理 → 已有 Surface 依然有效，
                                        // 绝不重建、绝不重绑（重绑就会重解关键帧=重播当句）。
                                        if (videoSurfaceTexRef === st && videoSurfaceRef[0] != null) {
                                            Log.d("XJ_VID", "TEXTURE available REUSE(same st); skip rebind")
                                            return
                                        }
                                        // 【黑屏铁律】用 TextureView【自带】的 st 建 Surface，绝不用 new SurfaceTexture(0) 兜底
                                        videoSurfaceTexRef = st
                                        val sf = Surface(st)
                                        videoSurfaceRef[0] = sf
                                        videoMpBoundSurface = null   // 换了纹理才强制重绑
                                        bindVideoSurface(sf, "TextureAvailable")
                                    }
                                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
                                        // 尺寸变化不重建 Surface（同一个 st），不重绑，避免重解关键帧
                                    }
                                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                        // 【方案B 核心】返回 false = 保留 SurfaceTexture 不释放。
                                        // 前后台切换时 Surface 不销毁 → 回前台 MediaPlayer 无需重绑 →
                                        // 不重解关键帧 → 画面真正无缝。
                                        // 也不再 setSurface(null)/清空 videoSurfaceRef：那正是重播的触发点。
                                        Log.d("XJ_VID", "TEXTURE destroyed -> KEEP (surface 常驻、不摘画面、不重绑)")
                                        return false
                                    }
                                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // 【0903 黑屏修复】错误/空状态覆盖层：替代之前 videoUri==null 时只有一个浅白字的纯黑 Surface
                // （容易被误判为"整个黑屏"）和 MediaPlayer 错误时静默黑屏（什么反馈都没有）。
                // 视频正在正常播放时这层完全透明、不挡画面。
                if (videoErrorMessage != null || videoUri == null) {
                    VideoStateOverlay(
                        errorText = videoErrorMessage,
                        onRetry = {
                            videoErrorMessage = null
                            // 重新触发 LaunchedEffect(videoUri)：先清再设
                            val cur = videoUri
                            videoUri = null
                            videoUri = cur
                        },
                        onPickVideo = {
                            // 跳到文件夹页选视频（视频选集模式：直接钻到视频所在文件夹）
                            videoSelectJump = true
                            biliNavigate?.invoke(Page.FOLDER)   // folderInitType 由 biliNavigate 按当前模式设（见下）
                        },
                        onDismiss = { videoErrorMessage = null },
                    )
                }
                // 仅全屏时才在画面里叠字幕（需求6：非全屏太遮挡，不叠）
                // 🔴 【1003 P1 修】原来只判 `>= 0`，**没判 < sentences.size** ⇒ 重新生成字幕时
                // （MainActivity 会先 `sentences = emptyList()` 但**不重置** videoSentenceIndexGlobal）
                // 暂停在第 150 句 → 点重新生成 → 第 1 段识别完（size 只有 20）→ 进全屏
                // ⇒ sentences[150] 直接 **IndexOutOfBoundsException 崩溃**。
                // 用 `in sentences.indices` 一个条件把两头都判掉。
                if (!readingMode && videoFullscreen && videoSubOn && videoSentenceIndexGlobal in sentences.indices) {
                    Box(
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 56.dp),
                    ) {
                        Text(
                            sentences[videoSentenceIndexGlobal].text,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                // 非全屏：右上角全屏按钮，跟随「点画面浮出的底部控制条」一起显隐（controlsVisible），
                // 点一下视频窗口：全屏按钮与底部功能键同时出现 / 同时隐藏。
                if (!readingMode && !videoFullscreen && controlsVisible && videoUri != null) {
                    IconButton(
                        onClick = { videoFullscreen = true; controlsVisible = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    ) {
                        Icon(Icons.Filled.Fullscreen, contentDescription = "全屏", tint = Color.White)
                    }
                }
                // 全屏：顶部右侧 字幕开关 + 退出全屏（避开状态栏，避免被挡）
                if (!readingMode && videoFullscreen && videoUri != null) {
                    Row(
                        modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        IconButton(onClick = { videoSubOn = !videoSubOn }) {
                            Icon(
                                Icons.Filled.Subtitles,
                                contentDescription = if (videoSubOn) "隐藏字幕" else "显示字幕",
                                tint = if (videoSubOn) Color.White else Color.White.copy(alpha = 0.5f),
                            )
                        }
                        IconButton(onClick = { videoFullscreen = false }) {
                            Icon(Icons.Filled.FullscreenExit, contentDescription = "退出全屏", tint = Color.White)
                        }
                    }
                    // 全屏底部：点屏才出（不再常驻），进度条紧贴 5 键控制条上方、合并成一块
                    if (controlsVisible) {
                        Column(
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.45f))
                                    .padding(horizontal = 14.dp, vertical = 0.dp),
                            ) {
                                VideoProgressSlider(
                                    currentMs = currentPos,
                                    durationMs = videoDuration,
                                    onSeek = { targetMs ->
                                        videoSeek(targetMs)
                                        currentPos = targetMs
                                    },
                                    overlay = true,
                                )
                            }
                            VideoTransportRow(
                                modifier = Modifier.fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.5f)).padding(vertical = 2.dp),
                                onPrevTrack = { prevVideo() },
                                onPrevSentence = { videoJumpSentence(-1) },
                                onPrevSentenceHoldChange = { held -> if (held) startRewind() else stopFast() },
                                onToggle = { videoTogglePlay() },
                                onNextSentence = { videoJumpSentence(1) },
                                onNextSentenceHoldChange = { held -> if (held) startFastForward() else stopFast() },
                                onNextTrack = { nextVideo() },
                                prevTrackEnabled = hasPrevVideo,
                                nextTrackEnabled = hasNextVideo,
                                playing = videoPlaying,
                                plain = true,
                            )
                        }
                    }
                }
                // 非全屏：点画面浮出的控制条；进度条与控制条合并成一块、整体更靠底，点击屏幕即出且可拖动
                if (!readingMode && !videoFullscreen && (controlsVisible || fastScrubbing) && videoUri != null) {
                    Column(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.45f))
                                .padding(horizontal = 14.dp, vertical = 0.dp),
                        ) {
                            VideoProgressSlider(
                                currentMs = currentPos,
                                durationMs = videoDuration,
                                onSeek = { targetMs ->
                                    videoSeek(targetMs)
                                    currentPos = targetMs
                                },
                                overlay = true,
                            )
                        }
                        if (controlsVisible) {
                            VideoTransportRow(
                                modifier = Modifier.fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.5f)).padding(vertical = 2.dp),
                                onPrevTrack = { prevVideo() },
                                onPrevSentence = { videoJumpSentence(-1) },
                                onPrevSentenceHoldChange = { held -> if (held) startRewind() else stopFast() },
                                onToggle = { videoTogglePlay() },
                                onNextSentence = { videoJumpSentence(1) },
                                onNextSentenceHoldChange = { held -> if (held) startFastForward() else stopFast() },
                                onNextTrack = { nextVideo() },
                                prevTrackEnabled = hasPrevVideo,
                                nextTrackEnabled = hasNextVideo,
                                playing = videoPlaying,
                                plain = true,
                                compact = true,
                            )
                        }
                    }
                }
                // 【1007】识别进度/结果胶囊：叠在画面**正中间**（阿亮定的位置，比顶部/半中不挡字幕）
                val asrOverlayText = when {
                    recognizing -> when {
                        asrStage != null -> asrStage!!
                        asrProgress < 100 -> "抽音轨中… $asrProgress%"
                        else -> "识别中…"
                    }
                    asrOverlayVisible && asrMsg.isNotEmpty() -> asrMsg
                    else -> null
                }
                if (asrOverlayText != null) {
                    // 出错/警告 = 非成功文案且非识别中（识别中的段进度文案也别染红）
                    val asrOverlayErr = !recognizing && !asrOverlayText.startsWith("识别完成")
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (asrOverlayErr) Color(0xE6B3261E) else Color(0xE6000000),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 32.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            if (recognizing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                asrOverlayText,
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
        // ===== 非全屏时的下方内容（全屏时隐藏，画面独占） =====
        // 【0915 模块化】控制排与字幕列表都做成可拖拽换位/调高矮的模块，与音频模式同一套机制
        // （复用 PlayerPage 里抽出的 ModuleColumnHost）。偏好独立存于 video_layout，
        // 默认顺序 [SUBTITLES, TRANSPORT] ⇒ 字幕占满剩余、控制排正好贴着底栏上方。
        if (!readingMode && !videoFullscreen) {
            val ctxLayout = context
            var vModules by remember { mutableStateOf(VideoLayoutPrefs.loadOrder(ctxLayout)) }
            var vHeights by remember {
                mutableStateOf(
                    VideoLayoutPrefs.defaultHeights.mapValues { (m, d) ->
                        VideoLayoutPrefs.loadHeight(ctxLayout, m, d)
                    }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            ModuleColumnHost(
                modules = vModules,
                heights = vHeights,
                layoutEdit = layoutEdit,
                onMove = { from, to ->
                    val list = vModules.toMutableList()
                    val m = list.removeAt(from)
                    list.add(to, m)
                    vModules = list
                    VideoLayoutPrefs.saveOrder(ctxLayout, list)
                },
                onHeightChange = { module, h ->
                    vHeights = vHeights + (module to h)
                    VideoLayoutPrefs.saveHeight(ctxLayout, module, h)
                },
                idOf = { it.id },
                flexible = { it == VideoModule.SUBTITLES },
                minHeightOf = { VideoLayoutPrefs.MIN_HEIGHT_TRANSPORT },
                maxHeight = VideoLayoutPrefs.MAX_HEIGHT,
                // 【0915】控制排会临时长出状态文字（B站 抓取状态 biliStatus/biliError；
                // 识别进度/asrMsg 已于 1007 挪到视频画面正中间的胶囊，不再撑这里），
                // 高度被钉死就会溢出重叠 ⇒ 让它带 autoFit：偏好高度当下限，内容更高就撑开。
                autoFit = { it == VideoModule.TRANSPORT },
            ) { module ->
                when (module) {
                    // ---- 5 键控制排（含 B站 输入行、导入/识别状态提示） ----
                    VideoModule.TRANSPORT -> {
                        // 【0930 修】原来整块被 if (videoUri != null) 包住 ⇒ **没选视频时 B站 导入入口整个消失**，
                        // 而空状态卡只给「去选视频」。结果：手上没本地视频的人**根本没法用 B站 抓取**
                        // （提示文案写着"可以把 B站 链接粘进来后台抓"，实际压根没那个框 —— 自己打自己脸）。
                        // 改法：输入框**永远存在**，5 键只在该有的时候（有视频）才渲染。
                        // 没视频时输入框**直接是展开态**（biliInputRevealed 初值 = videoUri == null），
                        // 免去"先学会左滑才能导入"的学习成本。
                        val hasVideo = videoUri != null
                        // 主控制一排：与音频模式统一的 5 键（上一曲/上一句/播放/下一句/下一曲）
                        run {
                            // 【0903 v4 并排一体·跟手版】功能键 + B站 输入框：父容器宽 screenW，内含一个总宽
                            // screenW+inputW 的横排 Box（左=功能键，右=输入框），整体用 Animatable 横向平移：
                            //   - slideX=0：功能键填满整屏，输入框在屏外右侧（默认态）
                            //   - slideX=inputW：输入框整段在屏右，功能键右端在屏左露出（两半始终并排）
                            //   - 中间：两边各露一截，互相都是"抓手"
                            // 两个血泪坑（0903 实测）：
                            // 1) 超大容器必须 .align(Alignment.CenterStart) 强制贴左 —— 否则 Box 把比自身宽的
                            //    子元素水平居中，默认位整排左移约 inputW/2，最左的上一曲键被顶出屏外、输入框
                            //    在右边露出一半（这就是装机后"功能键少了/不对齐"的根因）。
                            // 2) 手势 draggable 实时把拖拽增量叠进 offset = 跟手；松手 settle() 先 snapTo 到
                            //    手指当前位置再动画到锚点 = 无回跳；不用 detectHorizontalDragGestures（吃点击）。
                            // 【0930】有视频 → 默认收起（功能键优先）；没视频 → 默认展开（导入是唯一能做的事）
                            var biliInputRevealed by remember { mutableStateOf(!hasVideo) }
                            var dragTotal by remember { mutableStateOf(0f) }   // 本次拖拽累计位移（左滑为负）
                            val scope = rememberCoroutineScope()
                            // 【1003 加】读剪贴板：左滑唤出输入框时自动预填，省掉"长按粘贴"。
                            // 只在本页首次组合时读一次（用户复制链接 → 左滑进来就能直接点抓取）。
                            // 不在"每次唤出"时重读：那要求跟踪唤出时机、还得区分拖拽重组，
                            // 收益只在"唤出后又去复制一次"这种极少场景，不值那份复杂度。
                            // 判据在 BiliLinkInput 里（只认含 bilibili.com / BV 号的），这里只给原始文本。
                            // ⚠️ LocalClipboardManager.current 是 @Composable ⇒ 必须在 composable 作用域取，
                            // 不能塞进 remember { }（那个 lambda 不是 composable 上下文，会编译失败）。
                            val clipboardMgr = LocalClipboardManager.current
                            var clipboardText by remember { mutableStateOf<String?>(null) }
                            LaunchedEffect(Unit) {
                                clipboardText = clipboardMgr.getText()?.text
                            }
                            // slideX 单位是 px，只有进 BoxWithConstraints 拿到 inputWPx 才知道该落在哪
                            // ⇒ 初值先用 0，进去后由下面的 LaunchedEffect 一次性校正到初始锚点。
                            val slideX = remember { Animatable(0f) }
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clipToBounds(),
                                // 强制子元素贴左：实测超大子元素会被按整屏宽水平居中（pos 变负、最左键顶出屏）
                                contentAlignment = Alignment.TopStart,
                            ) {
                                val screenW = maxWidth
                                // 【0915 翻页式】输入框宽度 = 整排宽（阿亮要求"跟功能键一样宽一样高"）：
                                // 左滑距离 = 一个屏宽，功能键整排滑出、输入框整屏滑入，像翻一页。
                                // 旧值是 minOf(280.dp, screenW - 150.dp) 的「并排滑块」，改等宽后几何更简单。
                                val inputW = screenW
                                val inputWPx = with(LocalDensity.current) { inputW.toPx() }
                                val screenWPx = with(LocalDensity.current) { screenW.toPx() }
                                // 【0930】进 Box 拿到 inputWPx 后，把 slideX 一次性校正到"当前状态该在的锚点"。
                                // 没视频时 biliInputRevealed 初值为 true ⇒ 这里直接 snapTo(inputWPx)，
                                // 输入框开局就是展开的，且**不播动画**（避免一进页面就自己滑一下）。
                                // 用 hasVideo 当 key：从"没视频"变成"有视频"（导入成功）时才重跑一次，
                                // 那时 biliInputRevealed 已被 onSubmit 置 false，会顺滑收回功能键位。
                                LaunchedEffect(hasVideo) {
                                    // 【1002 修①】删视频（true→false）后自动回到"输入框展开态"：
                                    // 原来 biliInputRevealed 只在首次组合时按 !hasVideo 算一次，
                                    // 播放中删了视频 ⇒ hasVideo 变 false 但 revealed 还是 false
                                    // ⇒ 用户被丢在"← 左滑回来"提示页，得多滑一次才回到输入框。
                                    if (!hasVideo) biliInputRevealed = true
                                    val target = if (biliInputRevealed) inputWPx else 0f
                                    dragTotal = 0f
                                    // 【1002 修③】正在向同一目标动画时**不要** snapTo 打断
                                    // （否则导入成功瞬间：onSubmit 的 settle() 250ms 收回动画
                                    // 会被这里 snapTo(0) 拦腰打断，画面瞬跳）。
                                    if (!(slideX.isRunning && slideX.targetValue == target)) {
                                        slideX.snapTo(target)
                                    }
                                }
                                // 松手/收起/提交后统一回到锚点（0 或 inputW），不产生跳变
                                fun settle() {
                                    val target = if (biliInputRevealed) inputWPx else 0f
                                    val from = (slideX.value + dragTotal).coerceIn(0f, inputWPx)
                                    dragTotal = 0f
                                    scope.launch {
                                        slideX.snapTo(from)
                                        slideX.animateTo(target, animationSpec = tween(250))
                                    }
                                }
                                fun onDragStopped(velocity: Float) {
                                    // 【0930 手感修复】阿亮反馈「滑动距离太长，老感觉滑好几次都滑不到，
                                    // 要从头按到尾才能滑过来」——根因是翻页距离 = 一个整屏宽（inputW=screenW，
                                    // 0915 定的同宽同高），再叠加「松手必须过半」的判定，手指基本得从最左拖到
                                    // 屏幕中线才认。改成两条任一满足即翻页，外观/几何完全不动：
                                    //   ① 位移过 1/4 屏（原 1/2 ⇒ 门槛砍半）
                                    //   ② 轻扫：位移不够但往该方向甩得够快（>600px/s）⇒ "啪一下"就出来
                                    // velocity>0 = 手指向右（内容右移 = 收起）；velocity<0 = 向左（唤出）。
                                    val pull = (slideX.value + dragTotal).coerceIn(0f, inputWPx)
                                    val fastLeft = velocity < -600f
                                    val fastRight = velocity > 600f
                                    biliInputRevealed = when {
                                        fastLeft -> true
                                        fastRight -> false
                                        else -> pull > inputWPx * 0.25f
                                    }
                                    settle()
                                }
                                val draggableState = rememberDraggableState { delta ->
                                    // 【0903 用户反馈】原实现是「右滑唤出」（手指右拖 pull 才涨），反直觉；
                                    // 改成左滑唤出：手指左拖（delta<0）→ dragTotal 涨 → 输入框从右侧拉出来，
                                    // 跟手方向 = 内容随手指左移，与「把右边的输入框拉过来」的手势直觉一致。
                                    dragTotal -= delta
                                }
                                // 功能键（宽 screenW）与输入框（宽 inputW）作为两个独立子元素各自 offset 平移：
                                // 功能键 x∈[0,-inputW] 左滑出屏，输入框 x=screenW→screenW-inputW 贴其右缘滑入。
                                // 关键：两者宽度都不超过父容器 → 不会触发"Box 对超宽子元素一律居中"的框架行为
                                // （0903 实测：单个 screenW+inputW 宽子元素被强制居中到 x=-279，最左键顶出屏外）。
                                // 【0930】5 键只在**有视频**时才渲染；没视频时放一个等宽的空 Box 占位。
                                // 为什么占位不能省：输入框用 matchParentSize 取 Box 尺寸、靠 screenWPx 定位，
                                // 若这一页整个不渲染，Box 就没有内容撑高度 ⇒ 输入框塌成 0 高。
                                // 占位 Box 高度给 72.dp（≈ 有视频时控制排的高度），保证没视频时输入框观感一致。
                                if (hasVideo) {
                                    VideoTransportRow(
                                        modifier = Modifier
                                            .requiredWidth(screenW)
                                            .offset { IntOffset(-(slideX.value + dragTotal).coerceIn(0f, inputWPx).roundToInt(), 0) }
                                            .draggable(
                                                state = draggableState,
                                                orientation = Orientation.Horizontal,
                                                startDragImmediately = false,
                                                onDragStopped = { v -> onDragStopped(v) },
                                            ),
                                        onPrevTrack = { prevVideo() },
                                        onPrevSentence = { videoJumpSentence(-1) },
                                        onPrevSentenceHoldChange = { held -> if (held) startRewind() else stopFast() },
                                        onToggle = { videoTogglePlay() },
                                        onNextSentence = { videoJumpSentence(1) },
                                        onNextSentenceHoldChange = { held -> if (held) startFastForward() else stopFast() },
                                        onNextTrack = { nextVideo() },
                                        prevTrackEnabled = hasPrevVideo,
                                        nextTrackEnabled = hasNextVideo,
                                        playing = videoPlaying,
                                    )
                                } else {
                                    // 空占位：暗示"这一页本来是播放键"，左滑即回到导入框。
                                    // 文案极简（按项目 UI 纪律：清爽 = 文字别啰嗦），只给一句动作提示。
                                    Box(
                                        modifier = Modifier
                                            .requiredWidth(screenW)
                                            .height(72.dp)
                                            .offset { IntOffset(-(slideX.value + dragTotal).coerceIn(0f, inputWPx).roundToInt(), 0) }
                                            .draggable(
                                                state = draggableState,
                                                orientation = Orientation.Horizontal,
                                                startDragImmediately = false,
                                                onDragStopped = { v -> onDragStopped(v) },
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "← 左滑回来粘 B站 链接",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                // 输入框：与功能键排**同宽同高**的整屏页。默认在屏外右侧（x=screenW），
                                // 左滑翻入停在 x=0。宽高都跟随功能键排（同一 Box 里两页等大 ⇒
                                // "压缩或拉伸都跟着一样"）；外观也套同款淡色圆角卡，
                                // 翻页后仍是一个模块的观感，而不是突然换成另一套白底样式。
                                BiliLinkInput(
                                    modifier = Modifier
                                        // 【0915 同宽同高】matchParentSize：宽高都取 Box 已算好的尺寸，
                                        // **不参与**父级测量 ⇒ 不会跟 VideoTransportRow 形成"谁撑谁"的循环。
                                        // 两页同宽同高，功能键排被压扁/拉高时它自动跟随。
                                        .matchParentSize()
                                        .offset { IntOffset((screenWPx - (slideX.value + dragTotal).coerceIn(0f, inputWPx)).roundToInt(), 0) }
                                        .draggable(
                                            state = draggableState,
                                            orientation = Orientation.Horizontal,
                                            startDragImmediately = false,
                                            onDragStopped = { v -> onDragStopped(v) },
                                        ),
                                    cardShape = RoundedCornerShape(20.dp),
                                    // 【1002 修②】没视频时输入框是唯一入口 ⇒ 藏掉 ×，防止把自己关到空提示页
                                    showClose = hasVideo,
                                    // 【1003 加】剪贴板里像 B站 链接就自动预填（判据在组件内）
                                    prefillFromClipboard = clipboardText,
                                    value = biliUrl,
                                    onValueChange = { biliUrl = it },
                                    onSubmit = { url ->
                                        val u = url.trim()
                                        if (u.isNotBlank()) {
                                            startBiliImport(context, u)
                                            biliInputRevealed = false
                                            biliUrl = ""
                                            biliStatus = "后台抓取中…"
                                            // 【1002 修⑤】新一次提交把上一回的错误清掉
                                            //（旧错误对新导入没有意义，不然红字永远挂着）
                                            biliError = ""
                                            settle()
                                        }
                                    },
                                    onClose = {
                                        biliInputRevealed = false
                                        settle()
                                    },
                                )
                            }

                            // 导入状态常驻显示（不随输入框收起而消失，让"后台抓取中 / 进度"始终可见）
                            // 【0930 修】原来 color = Color.White 是**硬编码白字**：这排卡片是淡色底，
                            // 白字叠上去几乎看不见（阿亮截图："下载视频"几个字太浅）。
                            // 改成主题色 onSurface ⇒ 浅底深字、深底浅字，跟主题走。
                            if (biliStatus.isNotEmpty()) {
                                Text(
                                    biliStatus,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, top = 4.dp, bottom = 2.dp),
                                )
                            }
                            if (biliError.isNotEmpty()) {
                                Text(
                                    biliError,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFF6B6B),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, top = 2.dp, bottom = 4.dp),
                                )
                            }
                        }
                        // 【1007 修】识别进度（recognizing 转圈行）和 asrMsg 两行状态文字已挪到
                        // 视频画面正中间的半透明胶囊（见上方视频 Box 内「识别进度/结果胶囊」），
                        // 不再占 TRANSPORT 模块的布局高度 —— 识别期间控制排不再被撑开。
                    }
                    // ---- 字幕列表（弹性模块：占满剩余空间，不参与调高矮） ----
                    VideoModule.SUBTITLES -> {
                        if (sentences.isNotEmpty()) {
                            // 字幕列表：点一句 -> 跳到该句开头播放；长按 -> 内联展开翻译 + 点词选词（对齐阅读模式）
                            LazyColumn(
                                state = subtitleListState,
                                modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 10.dp),
                                contentPadding = PaddingValues(vertical = 4.dp),
                            ) {
                                itemsIndexed(sentences) { index, sentence ->
                                    val active = index == videoSentenceIndexGlobal
                                    val expanded = expandedVideoIndex == index
                                    var translation by remember(sentence.text) { mutableStateOf(Translator.cached(sentence.text)) }
                                    var translating by remember(sentence.text) { mutableStateOf(false) }
                                    var failed by remember(sentence.text) { mutableStateOf(false) }
                                    var selectedWord by remember { mutableStateOf<String?>(null) }
                                    LaunchedEffect(expanded, sentence.text) {
                                        if (expanded && translation == null && !translating) {
                                            translating = true; failed = false
                                            val t = Translator.translate(sentence.text)
                                            translation = t; failed = t == null; translating = false
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = when {
                                            active -> MaterialTheme.colorScheme.primaryContainer
                                            expanded -> MaterialTheme.colorScheme.secondaryContainer
                                            else -> MaterialTheme.colorScheme.surface
                                        },
                                        tonalElevation = if (active || expanded) 0.dp else 1.dp,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                            .combinedClickable(
                                                // 【0915】手动点句 = 放弃复读：先撤复读任务+高亮钉子，再跳句。
                                                // （videoSeekToSentence 直接调、不过 videoJumpSentence，得自己清一次，
                                                //  否则复读中点了别的句子，钉子会把高亮又拉回旧句。）
                                                onClick = { clearVideoSentenceRepeat(); videoSeekToSentence(index) },
                                                onLongClick = { expandedVideoIndex = if (expanded) -1 else index },
                                            ),
                                    ) {
                                        Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp)) {
                                            if (expanded && sentence.text.isNotEmpty()) {
                                                ClickableWordsRow(text = sentence.text, onWordClick = { selectedWord = it })
                                            } else {
                                                Text(
                                                    sentence.text,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                            }
                                            if (expanded && sentence.text.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                when {
                                                    translating -> Row(verticalAlignment = Alignment.CenterVertically) {
                                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("正在翻译…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                    failed -> Text("翻译失败，请检查网络后长按重试", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                                                    else -> Text(translation ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                                }
                                                Spacer(modifier = Modifier.height(10.dp))
                                                AddSentenceButton(context = context, text = sentence.text, source = videoName)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("再长按可收起", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                    if (selectedWord != null) {
                                        WordCardDialog(word = selectedWord!!, source = videoName, onDismiss = { selectedWord = null })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
/** 抓 B站 链接输入框：左滑功能键唤出，只保留输入框本身（介绍文字与生成翻译开关已去掉，
 *  改用原长按字幕翻译逻辑）。回车即提交，由前台服务后台抓取、不阻塞当前视频。
 *
 *  【0915 翻页式改造】与功能键排**同宽同高**：外层套一层与功能键排同款的淡色圆角卡
 *  （由 cardShape 传入，两处用同一个 RoundedCornerShape(20.dp)），内容垂直居中 ——
 *  这样左滑翻过来之后仍是"同一个模块"，而不是突然变成一条白底输入行。
 *  高度/宽度都不写死，由调用方的 matchParentSize 决定，因此功能键排被压扁/拉高时它自动跟随。 */
internal fun BiliLinkInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    cardShape: Shape = RoundedCornerShape(12.dp),
    // 【1002 修】没视频时输入框是唯一入口，× 点了会把自己关到"只剩一句提示"的空页
    // ⇒ 没视频时隐藏 ×（调用方传 hasVideo）。
    showClose: Boolean = true,
    // 【1003 加】自动预填剪贴板里的 B站 链接。
    // 为什么需要：以前**唯一**的提交途径是键盘回车（KeyboardActions onDone），
    // 而输入行里唯一的圆按钮是「收起 ×」—— 键盘一弹起来 × 还被顶出可视区，
    // 提交动作零视觉提示（1003 阿亮指出"按钮不够明显"，我实测也先点错成收起）。
    // 从 B站 复制链接过来 → 唤出输入框 → 直接点抓取。
    prefillFromClipboard: String? = null,
) {
    // 只认"看着像 B站 视频"的内容。判据必须严：认错了会把你复制的别的文字
    // （快递地址、聊天内容）糊进输入框，还得手动清掉再改——那比"不自动填"更糟。
    // ⇒ bilibili.com / b23.tv / BV[0-9A-Za-z]{8,} 三者命中其一才填。
    // ⚠️ 注意：系统粘贴板（长按输入框弹出的那排历史）**照旧由系统管**，
    // 1003 阿亮实测：点一下弹系统粘贴板、点条目才填进去，这个交互没问题、不用改。

    LaunchedEffect(prefillFromClipboard) {
        val raw = prefillFromClipboard?.trim().orEmpty()
        if (raw.isEmpty() || value.isNotBlank()) return@LaunchedEffect
        val looksLikeBili = raw.contains("bilibili.com", ignoreCase = true) ||
            raw.contains("b23.tv", ignoreCase = true) ||
            Regex("""(?i)\bBV[0-9A-Za-z]{8,}""").containsMatchIn(raw)
        if (looksLikeBili) onValueChange(raw)
    }
    val canSubmit = value.isNotBlank()

    Surface(
        shape = cardShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 【0915 美化】原来是 OutlinedTextField（自带描边框）套在粉色卡片里，
                // 形成"框里框"、且固定高度会裁掉文字下半截。改成自绘输入槽：
                // 一个比卡片深一档的同色系圆角浅槽 + 左侧链接图标 + 无边框 BasicTextField，
                // 视觉上就是"卡片里的一个输入位"，而不是一个独立控件叠上来。
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                            RoundedCornerShape(14.dp),
                        )
                        .padding(horizontal = 12.dp),
                ) {
                    Icon(
                        Icons.Filled.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                "粘贴 B站 链接",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        BasicTextField(
                            value = value,
                            onValueChange = onValueChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onSubmit(value) }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                // 关闭钮：与功能键同样的圆形浅色底，整排风格一致
                // 【1003 加】显式「抓取」键：以前只能靠键盘回车，键盘一弹起连 × 都看不见，
                // 提交动作没有任何视觉提示（实测先点错成收起）。现在两条路并存：
                // 键盘回车照旧，这个键负责"看得见的那个"。
                // 空内容时置灰（alpha 0.3 + onSurfaceVariant 淡色）⇒ 不会误触发空抓取。
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp),
                ) {
                    IconButton(
                        onClick = { if (canSubmit) onSubmit(value) },
                        enabled = canSubmit,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            // 【1003 换图标】原来用 ArrowUpward（↑），阿亮一看就说"像上传"——
                            // 确实，↑ 在 UI 通用语义里是"向上/上传"，不是"提交"。
                            // 换 KeyboardReturn（↵ 回车键符号）：跟键盘上真正触发提交的是同一个键，
                            // 看到它就知道"按回车也能干这事"，两条提交路在视觉上对齐了。
                            Icons.Filled.KeyboardReturn, contentDescription = "抓取",
                            tint = if (canSubmit) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f)
                            },
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                // 【1002 修】showClose=false（没视频）时不渲染 —— 输入槽 weight(1f) 自动占满整行。
                if (showClose) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.size(44.dp),
                    ) {
                        IconButton(onClick = onClose, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                Icons.Filled.Close, contentDescription = "收起",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 【0903 黑屏修复】视频窗口错误/空状态覆盖层：
 *  - errorText 非空：显示错误卡片（红色边框 + 具体原因 + 重试/换视频/收起按钮），用户立即知道为什么黑屏。
 *  - errorText 为空且 videoUri==null：显示空状态引导卡（图标 + 提示 + 「去选视频」大按钮），不再让用户对着纯黑 Surface 猜。
 *  视频正常播放时这层完全透明（两个分支都不渲染），不挡画面。 */
@Composable
internal fun VideoStateOverlay(
    errorText: String?,
    onRetry: () -> Unit,
    onPickVideo: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF2A2A2E),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (errorText != null) {
                    // 错误状态
                    Text(
                        "⚠ 播放失败",
                        color = Color(0xFFFF6B6B),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        errorText,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4A4A4E),
                                contentColor = Color.White,
                            ),
                        ) { Text("重试") }
                        Button(
                            onClick = onPickVideo,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4A4A4E),
                                contentColor = Color.White,
                            ),
                        ) { Text("换视频") }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onDismiss) {
                        Text("知道了", color = Color.White.copy(alpha = 0.7f))
                    }
                } else {
                    // 空状态（videoUri==null）
                    Text(
                        "📂 还没有选视频",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "点顶部「选择视频」挑一个，\n或者把 B站 链接粘进来后台抓",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onPickVideo,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) { Text("去选视频") }
                }
            }
        }
    }
}

@Composable
internal fun VideoTransportRow(
    modifier: Modifier = Modifier,
    onPrevTrack: () -> Unit,
    onPrevSentence: () -> Unit,
    onPrevSentenceHoldChange: (Boolean) -> Unit,
    onToggle: () -> Unit,
    onNextSentence: () -> Unit,
    onNextSentenceHoldChange: (Boolean) -> Unit,
    onNextTrack: () -> Unit,
    prevTrackEnabled: Boolean,
    nextTrackEnabled: Boolean,
    playing: Boolean,
    // plain=true：窗口内/全屏/弹出用的「半透明灰白简单键」（无圆形底色，越简单越好）；
    // plain=false：窗口下方主控制条用的圆形 primaryContainer 风格（保持不变）。
    plain: Boolean = false,
    // compact=true：整体再缩一档，用于「点视频窗口浮出的那条控制条」，少挡画面。
    compact: Boolean = false,
) {
    if (plain) {
        // —— 旧版窗口内简单风：黑半透明条上直接放白色 IconButton，无圆形底色 ——
        val tintOf: (Boolean) -> androidx.compose.ui.graphics.Color = { en ->
            if (en) Color.White else Color.White.copy(alpha = 0.4f)
        }
        // compact：点视频窗口浮出的那条半透明控制条，整体再缩一档（约 -15%）少挡画面
        val szTrack = if (compact) 34.dp else 48.dp
        val szSent = if (compact) 38.dp else 52.dp
        val szPlay = if (compact) 52.dp else 72.dp
        val icTrack = if (compact) 21.dp else 28.dp
        val icSent = if (compact) 24.dp else 32.dp
        val icPlay = if (compact) 28.dp else 38.dp
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevTrack, enabled = prevTrackEnabled, modifier = Modifier.size(szTrack)) {
                Icon(XuanjiIcons.PrevTrack, contentDescription = "上一曲", tint = tintOf(prevTrackEnabled), modifier = Modifier.size(icTrack))
            }
            Box(
                modifier = Modifier.size(szSent)
                    .pressHold(onTap = onPrevSentence, onHoldChange = onPrevSentenceHoldChange),
                contentAlignment = Alignment.Center,
            ) {
                Icon(XuanjiIcons.Prev, contentDescription = "上一句（长按快退）", tint = Color.White, modifier = Modifier.size(icSent))
            }
            IconButton(onClick = onToggle, modifier = Modifier.size(szPlay)) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "暂停" else "播放",
                    tint = Color.White,
                    modifier = Modifier.size(icPlay),
                )
            }
            Box(
                modifier = Modifier.size(szSent)
                    .pressHold(onTap = onNextSentence, onHoldChange = onNextSentenceHoldChange),
                contentAlignment = Alignment.Center,
            ) {
                Icon(XuanjiIcons.Next, contentDescription = "下一句（长按快进）", tint = Color.White, modifier = Modifier.size(icSent))
            }
            IconButton(onClick = onNextTrack, enabled = nextTrackEnabled, modifier = Modifier.size(szTrack)) {
                Icon(XuanjiIcons.NextTrack, contentDescription = "下一曲", tint = tintOf(nextTrackEnabled), modifier = Modifier.size(icTrack))
            }
        }
    } else {
        // —— 窗口下方主控制条：圆形 primaryContainer 风格（与音频模式统一，保持不变） ——
        // 【0915 统一模块外观】整排外面套一层淡色圆角卡，参数与音频 TransportModule 完全对齐
        // （RoundedCornerShape(20.dp) + primaryContainer 35%）：阿亮反馈"只有 5 个按钮、
        // 后面就是白底，看着不像个模块"。取色跟随主题，蓝色主题下自然是淡蓝。
        // modifier（含调用方传的 requiredWidth/offset）给最外层 Box —— 滑动/布局行为不变。
        // 【0915 宽度修正】底框**不再套水平 padding**：整块直接铺满调用方给的宽度。
        // 上一版这里写了 padding(horizontal = 20.dp)，实测把底框压成 910px，而音频模块是
        // 996px，两者差 86px；且滑动时底框比按钮排窄、左滑会提前露出边缘。现在宽度由
        // 调用方（ModuleColumnHost / requiredWidth）决定，与音频模块逐像素对齐。
        Box(modifier = modifier) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                // ⚠️ 这里**不能**用 fillMaxSize()：外层 Box 由 ModuleColumnHost 给了
                // heightIn(min = 偏好高度)，只约束下限、上限仍是父级全高，fillMaxSize 会把卡片
                // 直接撑到整页高（实测控制排吃掉大半个屏、字幕被挤没）。
                // 卡片就按内容自然高即可，尾部余量靠 api 的默认高度收紧（见 VideoLayoutPrefs）。
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp),
                    ) {
                        IconButton(onClick = onPrevTrack, enabled = prevTrackEnabled, modifier = Modifier.fillMaxSize()) {
                            Icon(XuanjiIcons.PrevTrack, contentDescription = "上一曲", modifier = Modifier.size(28.dp))
                        }
                    }
                    Box(
                        modifier = Modifier.size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            .pressHold(onTap = onPrevSentence, onHoldChange = onPrevSentenceHoldChange),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(XuanjiIcons.Prev, contentDescription = "上一句（长按快退）", modifier = Modifier.size(32.dp))
                    }
                    FilledIconButton(onClick = onToggle, modifier = Modifier.size(72.dp)) {
                        Icon(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playing) "暂停" else "播放",
                            modifier = Modifier.size(38.dp),
                        )
                    }
                    Box(
                        modifier = Modifier.size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            .pressHold(onTap = onNextSentence, onHoldChange = onNextSentenceHoldChange),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(XuanjiIcons.Next, contentDescription = "下一句（长按快进）", modifier = Modifier.size(32.dp))
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp),
                    ) {
                        IconButton(onClick = onNextTrack, enabled = nextTrackEnabled, modifier = Modifier.fillMaxSize()) {
                            Icon(XuanjiIcons.NextTrack, contentDescription = "下一曲", modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    }
}