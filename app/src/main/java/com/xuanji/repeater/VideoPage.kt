package com.xuanji.repeater

import com.xuanji.repeater.SubtitleState.asrMsg
import com.xuanji.repeater.SubtitleState.loadReadySubtitles
import com.xuanji.repeater.SubtitleState.sentences
import com.xuanji.repeater.SubtitleState.srtName
import com.xuanji.repeater.SubtitleState.subtitlesFromAsr
import com.xuanji.repeater.SubtitleState.subtitleMode
import com.xuanji.repeater.SubtitleState.setSubtitleMode
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
import android.view.Surface
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Toast
import android.graphics.Outline
import android.media.AudioManager
import android.media.AudioAttributes
// 【v119】视频内核换 ExoPlayer：框架 MediaPlayer 的 MKV 解析器不认 AC3/EAC3（整条音轨静默跳过）⇒
//   MKV+杜比必哑。ExoPlayer 自带 MatroskaExtractor，原生支持 A_AC3/A_EAC3。
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
// 【v146】原生字幕承载视图（media3-ui）—— 裸 SurfaceView 接不住 cues，必须有这一层
import androidx.media3.ui.SubtitleView
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
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.wrapContentHeight
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

/** 视频真正开始播放时，把控制器塞给 PlayerService（接管媒体中心/通知/音频焦点） */
internal fun attachVideoPlayback() {
    PlayerService.instance?.attachVideoController(videoMediaController)
}

/** 释放视频播放器（切出视频模式 / Activity 销毁时）。单播放器架构：只有一个播放器，无后台第二播放器。 */
internal fun releaseVideoPlayer() {
        // 【0915】播放器都没了，复读任务自然作废；不清的话切回来会带着旧句尾乱跳。
        clearVideoSentenceRepeat()
        runCatching { videoMpRef[0]?.setVideoSurface(null) }
        runCatching { videoMpRef[0]?.release() }
        videoMpRef[0] = null
        videoMpSourceUri = null
        // 播放器释放 → 绑定/Surface 记录一并作废（此刻 SurfaceView 也随页面销毁了，
        // 下次进入会拿到新的 Surface 并重新绑定，不会误判成"同一个 Surface"）。
        videoMpBoundSurface = null
        videoSurfaceRef[0] = null
        videoPrepared = false
        videoDuration = 0L
        PlayerService.instance?.detachVideoController()
    }

internal fun videoTogglePlay() {
    val mp = videoMpRef[0] ?: return
    if (videoPlaying) {
        currentPos = mp.currentPosition; mp.pause(); videoPlaying = false
    } else {
        mp.play(); videoPlaying = true
        PlaybackSession.onVideoStarted()   // Phase3-步骤1：手动点播放
        // 【0903 用户反馈】系统媒体中心一直显示音频、控制音频：之前 attach 只发生在「打开视频自动播放
        // (onPrepared && videoPlaying)」一条路径；从文件夹选视频后手动点播放走的是这里 → 视频在响但
        // PlayerService 仍以为是音频模式。任何「视频真正开始发声」的路径都必须接管媒体中心/通知/焦点。
        attachVideoPlayback()
    }
    PlayerService.instance?.notifyVideoStateChanged()
}

/**
 * 【1007】整集播完后的连播：与音频模式**共用同一份 PlayMode 设置**（PlayerService state 里的
 * playMode，音频那边选的「顺序播放/列表循环/单曲循环」视频直接认）。
 * - LOOP_ONE：本集重播（PlaybackCompleted 态下 seek 后 start 位置不可靠，学 PlayerService
 *   resetToTrackStart 的三连：start 脱离该态 → pause → seekTo(0) → start 重播）
 * - SEQUENCE：有下一集就切下一集（openVideoFromLibrary 内部自动开播），列表尾停下
 * - LOOP_LIST：循环切下一集（尾部回第一集）
 * 复读中（videoSentencePin >= 0）不连播：保持「播完即停」的原状，不打断复读意图。
 * 🔴 必须主线程调（onCompletion 回调是 MediaPlayer 内部线程，调用处已 post）。
 */
internal fun videoAutoAdvanceIfEnabled() {
    if (videoSentencePin >= 0) return
    val mode = PlayerService.instance?.state?.value?.playMode ?: PlayMode.SEQUENCE
    if (mode == PlayMode.LOOP_ONE) {
        val mp = videoMpRef[0] ?: return
        // ExoPlayer：STATE_ENDED 下 seekTo(0) 自身就会回到 READY，随后的 play() 即重播。
        // （MediaPlayer 那套 start→pause→seek→start 四连是为了脱离 PlaybackCompleted 态，ExoPlayer 不需要。）
        runCatching {
            mp.seekTo(0); mp.play()
        }
        videoPlaying = true
        return
    }
    if (videoFolderVideos.isEmpty()) return
    val idx = videoFolderVideos.indexOfFirst { it.path == videoPath }
    if (idx < 0) return
    val next = when (mode) {
        PlayMode.LOOP_LIST -> (idx + 1) % videoFolderVideos.size
        else -> idx + 1
    }
    val item = videoFolderVideos.getOrNull(next) ?: return
    val ctx = PlaybackSession.context ?: return
    openVideoFromLibrary(ctx, item, videoFolderVideos)
}

/** 精确 seek（ExoPlayer 用 EXACT：字幕跳句必须落在句子开头，不能被吸到上一个关键帧） */
internal fun videoSeek(targetMs: Long, whence: String = "?" ) {
    val mp = videoMpRef[0] ?: return
    val cur = runCatching { mp.currentPosition }.getOrDefault(-1L)
    Log.d("XJ_SEEK", "videoSeek whence=$whence target=$targetMs cur=$cur dir=${if (targetMs < cur) "BACK" else "FWD"}")
    // ⚠️ SeekParameters 在 Kotlin 视图里是**只读属性**（只有 getter 可见）⇒ 必须走方法调用，
    //    写成 `mp.seekParameters = ...` 会直接编译不过（'val' cannot be reassigned）。
    runCatching { mp.setSeekParameters(SeekParameters.EXACT) }
    runCatching { mp.seekTo(targetMs) }
}

internal fun videoSeekBy(delta: Int) {
    val mp = videoMpRef[0] ?: return
    // ⚠️ ExoPlayer 在「时长还没算出来」时 duration 返回 C.TIME_UNSET（一个大负数）。
    //    直接拿它当 coerceIn 的上界会因 min>max 抛 IllegalArgumentException ⇒ 必须先归一化。
    val dur = mp.duration.let { if (it == C.TIME_UNSET || it <= 0L) Long.MAX_VALUE else it }
    val p = (mp.currentPosition + delta).coerceIn(0L, dur)
    videoSeek(p); currentPos = p
}

/**
 * 【v157 · 手势快进/快退】拖动过程中的**预览 seek**。
 *
 * 只跳到目标附近的**最近关键帧**（`CLOSEST_SYNC`）⇒ 解码量小、出画快，
 * 画面才跟得上手指。落点会差几秒，但拖动中本来就是给人"看个大概"的。
 *
 * 🔴 松手时必须由 [videoSeek] 切回 `EXACT` 精确定位 —— 那才是不许偏的地方。
 * 🔴 **别拿它做跳句**：跳句要求精确落在句子开头（见 [videoSeek] 的注释）。
 */
internal fun videoSeekPreview(targetMs: Long) {
    val mp = videoMpRef[0] ?: return
    runCatching { mp.setSeekParameters(SeekParameters.CLOSEST_SYNC) }
    runCatching { mp.seekTo(targetMs) }
}

/** 【v157】手势跟手预览的**节流间隔**（ms）。
 *  v157 首发 100ms → **v160 调成 50ms**：阿亮拿 1080P 和 4K 都实测过"不卡"、有余量，
 *  加密一倍能让拖动时闪过的画面更多。
 *  🔴 **再往下调（或去掉节流）之前，先让他试** —— 这是他手机上"卡不卡"的边界，别自己加码。 */
private const val PREVIEW_THROTTLE_MS = 50L

/** 跳到第 idx 句并高亮（与旧 VideoView 版逻辑一致：落点异步复核，避免跳到上一句中间） */
internal fun videoSeekToSentence(idx: Int) {
    val mp = videoMpRef[0] ?: return
    if (idx !in sentences.indices) return
    val target = sentences[idx].startMs
    val s = sentences[idx]
    val stamp = System.currentTimeMillis()
    Log.d("XJ_SEEK", "videoSeekToSentence idx=$idx target=$target")
    videoSeek(target, "sentenceJump(idx=$idx)")
    mp.play(); videoPlaying = true
    PlaybackSession.onVideoStarted()   // Phase3-步骤1：视频跳句
    attachVideoPlayback()   // 跳句也会发声：同步接管媒体中心/焦点
    currentPos = target
    videoSentenceIndexGlobal = idx
    videoLastSeekAt = stamp
    videoAsrScope.launch {
        for (tryNo in 0 until 5) {
            delay(150)
            if (videoLastSeekAt != stamp) return@launch
            val p = mp.currentPosition
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

/**
 * 【1008 · 第7步】字幕时间轴轻量自检：末句结束时间跟视频时长**差得太远**就提示一句。
 *
 * 为什么需要：网上下的字幕往往是**另一个发布版本**的（DVD 版字幕配 BluRay 版片源），
 * 片头长度、「前情提要」有没有、剪辑快慢都可能差几十秒 —— **配对成功 ≠ 时间轴对得上**。
 * 阿亮下的老友记正是这个情况（S01 的 DVDRip 字幕 + 2160p BluRay 片源，两处下的）。
 * 我们**不做**自动对齐（那是下一步、另开功能），先如实告诉他一句，
 * 免得他以为软件坏了、或者以为字幕本身是乱的。
 *
 * 判据：`|视频时长 − 末句结束| > 30 秒` **且** `差值 > 视频时长的 5%`，两条同时满足才算 ——
 * 短视频里 30 秒是极大的比例，长片里 5% 是极大的绝对值，两条一起卡住两头的误报。
 * 只在「时长已知 + 有句子」时判；读的是当前状态（换片/重新识别都会重判），
 * 走 [asrMsg] 那颗画面正中的胶囊 —— 它是视频页所有状态提示的唯一出口。
 */
internal fun checkSubtitleTimeline() {
    val total = videoDuration
    val list = sentences
    if (total <= 0L || list.isEmpty()) return
    val last = list.last().endMs
    if (last <= 0L) return
    val gap = kotlin.math.abs(total - last)
    if (gap <= 30_000L || gap * 20 <= total) return
    asrMsg = "⚠️ 字幕时间轴可能对不上：字幕到 ${formatDuration(last)}，视频 ${formatDuration(total)}"
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

/** 【v157】手势提示用的时间格式：**带小时**。
 *  ⚠️ 别复用 [formatDuration] —— 那个只出「分:秒」，遇到 2 小时的合集会显示成 "123:45"。
 *  🔴【v159】改成 `internal`：`VideoUi.kt` 的进度条两头也要用它显示时间。 */
internal fun formatSeekTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** 【v157】横向拖动时的提示文案：`+1:08   6:55 / 22:46`。
 *  🔴 **方向不在文案里**（原来开头带"→ / ←"）—— 方向改由胶囊**两侧的箭头图标**表达，
 *  文字只负责报数。所以别再从文本里认方向，认 `gestureSeekBack`。
 *  🔴【v159 阿亮】原来写的是中文「1分8秒」—— 他嫌"太突兀"，要求去掉中文。
 *  现在用**带符号的时分秒**（`+1:08` / `-1:15`）：跟右边「6:55 / 22:46」同一个格式，
 *  一行里不打架；`+`/`-` 再把增/减说清楚（箭头图标管方向，这个是管"跳了多少"）。 */
private fun formatSeekHint(targetMs: Long, durMs: Long, deltaMs: Long): String {
    val sign = if (deltaMs >= 0) "+" else "-"
    val step = formatSeekTime(kotlin.math.abs(deltaMs))
    return "$sign$step   ${formatSeekTime(targetMs)} / ${formatSeekTime(durMs)}"
}

/**
 * 【v161】画面下方那一行「当前这集叫什么」。
 *
 * - **短标题居中、长标题自动轮播** —— `basicMarquee` 的行为正好对上这两个要求：
 *   文字**放得下就不动**（于是被外层 Box 居中），**放不下才滚**。不用自己判长度、也不用两套分支。
 * - 占的就是原来 `Spacer(12.dp)` 那块空白 ⇒ 只多出几 dp，下面的模块区几乎不动。
 * - 集名为空（比如归档里那条没有文件）时**退回 12dp 空白**，不留一行空的。
 *
 * ⚠️ `videoName` 是 `fileNameWithoutExt` 出来的，**已经去掉扩展名**，不会带 ".mkv"。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoTitleBar(name: String) {
    if (name.isBlank()) {
        Spacer(modifier = Modifier.height(12.dp))
        return
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier
                // 🔴 默认 iterations = 3（滚三遍就停），那不算"轮播" ⇒ 给成一直滚
                .basicMarquee(iterations = Int.MAX_VALUE),
        )
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

/** 🔴【1011 阿亮】画面底部那条控制条（进度条 + 5 键）的**统一底板色**。
 *  以前进度条画 0.45、按钮行画 0.5 ⇒ 两块有色差、看着像两条。现在**只在外层 Column 画一次**，
 *  两个子项都不再各画各的 ⇒ 一整条。取值比原来两块（0.45 / 0.5）**略浅一点**。 */
internal val CTRL_BAND_COLOR = Color.Black.copy(alpha = 0.4f)

/** 🔴🔴【1011 阿亮】**全屏（横屏）**那条控制条的总高。
 *  ⚠️ 全屏那条用的是**大号按钮**（`plain=true` 不带 `compact` ⇒ szPlay 72dp），
 *  所以它 = padding top 4 + Slider 16 + Row(72 + 上下各 2) = **96dp**，
 *  **比竖屏那条（58dp）高 38dp** —— 两条控制条高度本来就不同，别混用同一个数。
 *  全屏字幕浮层用这个数"留位" ⇒ 字幕条底边正好接上控制条顶边，两块**紧邻成一整块、不叠加**
 *  （留少了就会被控制条压上来叠两层 ⇒ 底部更深，看着像"两块不一样高、颜色也不一样"）。 */
internal val FULLSCREEN_CTRL_BAND_H = 96.dp

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
            if (mp != null) runCatching { if (mp.duration > 0) currentPos = mp.currentPosition }
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

    // ===== 【v124】画面手势：左半屏上下滑 = 亮度，右半屏上下滑 = 音量 =====
    // 参照小米播放器**实测**（不是猜的）：左半屏上滑冒太阳图标、右半屏上滑冒喇叭图标。
    // 🔴 亮度**直接调窗口亮度**（`window.screenBrightness`）—— 阿亮拍板：
    //    不要"叠半透明黑遮罩"那种假变暗（遮罩会把画面压灰、掉对比度），
    //    窗口亮度是真的降背光，画面质量一点不变。
    // 🔴【方案 B，1009】亮度状态是**全局**的（`appBrightness`，见 MainActivity.kt 顶部注释）：
    //    任何页面调的亮度全 App 统一 ⇒ 切音频/阅读模式不会跳回系统值、退出重进也记得住。
    //    ⇒ 本页**不再**负责"离开页面还原亮度"（原先那两个 DisposableEffect 已删掉）—— 那正是 bug 的来源。
    // 系统亮度：只在"用户从没调过亮度"（appBrightness == null）时当滑块起点，免得第一次滑动跳变。
    val sysBrightness = remember {
        runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        }.getOrDefault(0.5f).coerceIn(0.05f, 1f)
    }
    // 手势提示：0=不显示 1=亮度 2=音量 3=快进/快退（3 是 v157 加的）
    var gestureHint by remember { mutableStateOf(0) }
    var gestureHintValue by remember { mutableStateOf(0f) }
    // 【v157】横向拖动时的提示文案。它是**时间型**的（"15秒 01:23 / 45:30"），
    // 塞不进 0~1 的 gestureHintValue ⇒ 单独给一个。
    var gestureSeekText by remember { mutableStateOf("") }
    // 【v157】往哪边滑（true = 往回退）。胶囊两侧的箭头图标照它翻向。
    // 🔴 别再从 gestureSeekText 里认方向 —— 文案已经不带箭头了。
    var gestureSeekBack by remember { mutableStateOf(false) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    // 提示条显示 900ms 后自动收；滑动中数值一变就重置计时 ⇒ 表现为「松手后才开始计时」。
    // 🔴【v157】key 里必须带上 gestureSeekText：横向拖动时 gestureHintValue 变化很细微，
    //    光靠它可能不重启计时 ⇒ 拖着拖着提示自己消失了（人还在拖）。
    LaunchedEffect(gestureHint, gestureHintValue, gestureSeekText) {
        if (gestureHint != 0) {
            delay(900)
            gestureHint = 0
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

    // ===== 字幕菜单开关（v153 起菜单只剩「内封字幕」+「下方字幕」）=====
    // 🔴【1012】原来这里是"手动选一份字幕"的入口（`pickSubtitleFile` launcher + `applyPickedSubtitle`），
    //   已随「选字幕…」菜单项一起**整条删除**（阿亮："我感觉基本上不用选"）——
    //   字幕要么是抓的、要么是 ASR 生成的，都在视频旁边，自动匹配（[SubtitleParser]）稳定兜得住。
    //   ⚠️ 连带 `ManualSubtitle`（私有目录副本 + 路径映射）整个 object 也删了 ——
    //     光删 UI 入口不删它，老的记忆会**永久生效且无法撤销**（写入口没了，读入口优先级却最高）。
    var subMenuOpen by remember { mutableStateOf(false) }

    // 🔴【1010】字幕显示模式：双语 / 只看英文 / 只看中文。**默认「只看英文」**（阿亮定）。
    //   只对**双语字幕**起效；单语字幕任何档位都原样显示（挑不出对面那行，硬挑就变空）。
    //   🔴🔴【1010 阿亮定】**档位是全局的**（`subtitleMode` 见 SubtitleState.kt）——
    //     阅读模式里的字幕是同一份双语数据带过去的，它**也要跟这个档**，否则"视频全显英文、
    //     阅读模式却中英都摆着"。⇒ 这里只读全局状态、切换走 `setSubtitleMode`，
    //     **不要再定义页面级副本**（副本 = 两边各说各话）。
    /** 某一句当前该显示成什么样（画面浮层 + 字幕列表都走这里，保证一致）。 */
    //   🔴🔴【1010 阿亮定】`subtitlesFromAsr` = **我们自己 ASR 生成的** ⇒ **一律不裁**。
    //     理由（阿亮原话「ASR 都不管，只要是我们生成的就都不管」）：ASR 出来的是**中英混杂**
    //     （语气词是中文、正文是英文），它**不是双语字幕**。按档位裁会把"含汉字的那一行"整行删掉，
    //     连英文正文一起丢 —— 学习要看的是英文，裁错方向了。
    //   ⇒ 只有**抓来的现成字幕**（手动选的 / 同名 .srt / 内封轨 / B站 CC）才跟档位。
    fun displayTextOf(s: Sentence): String =
        if (subtitlesFromAsr) s.text else filterByMode(s.text, subtitleMode)

    // 【v120】视频**内封**字幕轨（阿亮："往手机里一丢就自动出字幕"）。
    //   打开菜单时才去读一次轨道列表（IO，大文件要几秒），读到几条就列几条。
    //   ⚠️ 多条轨**不能同时显示**（会重叠打架）⇒ 菜单是"切换"入口，一次只用一条。
    var embeddedTracks by remember { mutableStateOf<List<EmbeddedSubtitle.Track>>(emptyList()) }
    var embeddedLoadedFor by remember { mutableStateOf("") }
    var embeddedBusy by remember { mutableStateOf(false) }

    // 🔴【1012】原 `applyPickedSubtitle()` + `pickSubtitleFile` launcher（手动选一份字幕用）
    //   **已删除** —— 随「选字幕…」菜单项一起走的，连同 `ManualSubtitle` 整个 object。
    //   ⚠️ 别再"顺手加回来"：要加先问阿亮（他的判断是"基本上不用选"）。

    // 🔴【1011 阿亮】原 `exportSubtitleNow()`（手动导出字幕到视频旁边）**已删除** ——
    //   唯一调用者是上面那个「导出字幕」菜单项，菜单项删了就成死码。
    //   ⚠️ **ASR 生成完自动落地仍然在**（`SubtitleExport.writeAsrBeside`，MainActivity 里调），
    //     所以磁盘上的字幕文件照样会生成，只是不再提供"手动再导一份"这个按钮。

    // 同级视频列表里当前位置（上一曲/下一曲用）
    val curFolderIdx = videoFolderVideos.indexOfFirst { it.path == videoPath }
    val hasPrevVideo = curFolderIdx > 0
    val hasNextVideo = curFolderIdx in 0 until videoFolderVideos.size - 1

    val pageScope = rememberCoroutineScope()

    /**
     * 【v120】切到某条**内封**字幕轨。
     * 🔴 纪律：**先重置 UI 状态、再换数据**（反了会有一帧带着旧高亮/旧展开项 = 答案泄露）。
     *   这条原来是从 `applyPickedSubtitle` 那抄的参照（那个函数 v153 已随「选字幕」一起删掉了），
     *   **规矩本身照旧有效** —— 只要还有"换一整套字幕"的操作，就按这个顺序来。
     * 扒不出（图片字幕 / 压缩轨 / 索引坏）就只弹一句提示，**不动**当前字幕。
     */
    /**
     * 🔴🔴【v147 阿亮实测】**「原生字幕轨」选择的唯一收口** —— 别在别处再写一份 override 逻辑。
     *
     * 为什么必须有这个函数（阿亮原话的问题）：
     *   「我切换简英双字跟繁英双字…下边这个点播字幕它生效了、换成繁體了，
     *     但视频窗口里头那个原生字幕它还是简体。」
     *
     * 根因 = **两条链路各走各的**：
     *   · 下面列表 / 我们自绘的字幕 ← `applyEmbeddedTrack()` 走 `EmbeddedSubtitle` 从片子里扒，
     *     扒的是**用户点名的那条轨** ⇒ 列表会变。
     *   · 画面里的原生字幕 ← **播放器自己**渲染内封轨，选哪条由 `trackSelectionParameters` 决定；
     *     原来的代码**永远取 `firstOrNull { TEXT }` + index 0** ⇒ 播放器压根不知道用户换了轨，
     *     一直停在第一条（简英）⇒ 两边对不上。
     *
     * 用法：`applyEmbeddedTrack()` 切轨时、以及开关打开时，都调它。
     * @param wantedIndex 用户点名的那条内封轨在 [embeddedTracks] 里的 `index`（= Media3 的 trackId）；
     *   null / -1 / 对不上 ⇒ 退回"第一条 TEXT"（保持 v146 的老行为，绝不因为映射失败把字幕搞没）。
     */
    fun applyNativeTrackOverride(
        b: androidx.media3.common.TrackSelectionParameters.Builder,
        p: androidx.media3.exoplayer.ExoPlayer,
        wantedIndex: Int = -1,
    ): androidx.media3.common.TrackSelectionParameters.Builder {
        val groups = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        if (groups.isEmpty()) return b
        // 命中用户指名的那条：用它的（轨道组, 组内下标）。⚠️ 取到就**立刻返回**，别再动别的轨。
        // 🔴 匹配键 = `TrackFormat.id`（**String**，ExoPlayer 从容器里读到的轨道标识）
        //    对 `EmbeddedSubtitle.Track.index`（**Int**，Media3 extractor 的 trackId）。
        //    ⚠️ 两边本来就同源（都来自同一个 MatroskaExtractor 的 track id），但类型不同
        //      ⇒ 这里**按字符串比**，同时兼容 `id` 为空时退回老行为。
        if (wantedIndex >= 0) {
            for (g in groups) {
                for (i in 0 until g.length) {
                    val f = g.getTrackFormat(i)
                    if (f.id != null && f.id == wantedIndex.toString()) {
                        Log.d("XJ_VID", "nativeSub 切到轨 id=$wantedIndex（${f.label ?: f.language ?: ""}）")
                        return b.setOverrideForType(
                            androidx.media3.common.TrackSelectionOverride(g.mediaTrackGroup, i)
                        )
                    }
                }
            }
            Log.w("XJ_VID", "nativeSub 找不到 id=$wantedIndex 的 TEXT 轨（现有 id=${
                groups.flatMap { g -> (0 until g.length).map { g.getTrackFormat(it).id } }
            }），退回第一条")
        }
        // 退回：第一条 TEXT 轨的组内第 0 条（v146 老行为）
        val g0 = groups.first()
        Log.d("XJ_VID", "nativeSub 用默认第一条 TEXT 轨（共 ${groups.size} 组）")
        return b.setOverrideForType(
            androidx.media3.common.TrackSelectionOverride(g0.mediaTrackGroup, 0)
        )
    }

    fun applyEmbeddedTrack(trackIndex: Int) {
        val path = videoPath
        val uri = videoUri
        if (path.isBlank() || uri == null || embeddedBusy) return
        embeddedBusy = true
        Toast.makeText(context, "正在从视频里读字幕…", Toast.LENGTH_SHORT).show()
        pageScope.launch {
            val r = withContext(Dispatchers.IO) {
                EmbeddedSubtitle.extractAndCache(context.applicationContext, uri, path, trackIndex)
            }
            embeddedBusy = false
            if (r == null) {
                Toast.makeText(context, "这条字幕读不出来（可能是图片字幕或压缩轨）", Toast.LENGTH_LONG).show()
                return@launch
            }
            clearVideoSentenceRepeat()
            expandedVideoIndex = -1
            videoSentenceIndexGlobal = 0
            currentPos = r.second.first().startMs
            // 【1010】内封轨 = 片方压进去的现成字幕（跟 B站CC 同性质）⇒ 要跟档
            loadReadySubtitles(r.second, r.first)
            videoSeek(r.second.first().startMs)
            SubtitleLibrary.saveVideo(context, videoName, r.second, path)
            saveVideoPrefs(context)
            // 🔴🔴【v147 阿亮实测】切内封轨必须**连画面里的原生字幕一起切** —— 否则出现
            //   「下面列表换成繁體了、视频窗口里还是简体」两条链路口径不一致。
            //   这里补上"通知播放器改选哪条 TEXT 轨"；映射不上（或没开原生字幕）时该函数内部
            //   会安全退回第一条轨，不会把字幕搞没。
            if (videoNativeSubOn) {
                runCatching {
                    val p = videoMpRef[0] ?: return@runCatching
                    val b = p.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    p.trackSelectionParameters = applyNativeTrackOverride(b, p, trackIndex).build()
                }.onFailure { Log.w("XJ_VID", "切原生字幕轨失败: ${it.message}") }
            }
            Toast.makeText(context, "已用这份字幕：${r.first}", Toast.LENGTH_LONG).show()
        }
    }

    /** 打开字幕菜单时顺手读一次内封字幕轨。同一个视频只读一次（换视频会自动重读）。 */
    fun loadEmbeddedTracks() {
        val path = videoPath
        val uri = videoUri ?: return
        if (path.isBlank() || embeddedLoadedFor == path) return
        embeddedTracks = emptyList()
        embeddedLoadedFor = path
        pageScope.launch {
            val list = withContext(Dispatchers.IO) {
                runCatching { EmbeddedSubtitle.tracks(context.applicationContext, uri) }
                    .getOrDefault(emptyList())
            }
            if (embeddedLoadedFor == path) embeddedTracks = list
        }
    }

    // 播放控制：togglePlay / seek / jumpSentence / seekToSentence 已抽到文件级（VideoPage 上方），
    // 供控制条 / 系统媒体中心 / 音频焦点共用。本页只保留 UI 专属的快进/快退与上下曲。

    // 实时快进：按住下一句时把播放速度提到 2x，画面跟着实时快放；松手由 stopFast 恢复。
    fun startFastForward() {
        val mp = videoMpRef[0] ?: return
        if (!videoPlaying) { runCatching { mp.play() }; videoPlaying = true; PlaybackSession.onVideoStarted(); attachVideoPlayback() }
        runCatching { mp.playbackParameters = PlaybackParameters(FF_SPEED) }
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
        runCatching { videoMpRef[0]?.let { mp -> mp.playbackParameters = PlaybackParameters(videoSpeed) } }
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
            val pos = runCatching { mp.currentPosition }.getOrDefault(currentPos)
            currentPos = pos
            val now = System.currentTimeMillis()
            if (now - lastSave > 2000) { lastSave = now; saveVideoPrefs(context) }
            // 【媒体卡片进度同步】每 1s 把最新位置推给 MediaSession/通知，使下拉卡片进度条跟实际播放对齐
            if (now - lastPosNotify > 1000) {
                lastPosNotify = now
                PlayerService.instance?.notifyVideoStateChanged()
            }
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

    // 装载 / 换片：自建 ExoPlayer + SurfaceView（单播放器·彻底修）。
    // 重要：续播严格尊重 videoPlaying 状态——暂停后返回/切回绝不自动开始；
    // 只有打开新视频(openVideoFromLibrary 会把 videoPlaying 置 true) 或本来就在播放才续播。
    LaunchedEffect(videoUri, videoRetryTick) {
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
            videoDuration = runCatching { existingPlayer.duration }.getOrDefault(videoDuration).coerceAtLeast(0)
            checkSubtitleTimeline()
            // 【方案B】幂等绑定：Surface 常驻且与上次是同一个 → 不会真正 setSurface，
            // 解码器不重新出帧 → 不重解关键帧 → 回前台画面无缝（不重播当句）。
            videoSurfaceRef[0]?.let { bindVideoSurface(it, "REUSE") }
            if (videoPlaying) runCatching { existingPlayer.play() } else runCatching { existingPlayer.pause() }
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
        // 【v126 修·720p长MKV黑屏】v119 换 media3 后，720p 低码率长 MKV 播不了：
        //   实测（1009）根因 = **默认 LoadControl 把整部片子往 Java 堆里灌**：
        //   播 481MB 切片时堆涨到 480MB（≈整个文件），645MB 的片子超过 512MB(largeHeap) 上限
        //   ⇒ DefaultLoadControl 无限刷"Target buffer size reached with less than 500ms"⇒ 断粮黑屏。
        //   默认 prioritizeTimeOverSizeThresholds=true：缓冲时长没到 maxBuffer(50s) 就**无视字节上限**继续装
        //   ⇒ 这类 MKV（P帧仅38字节、块极碎）永远灌不满判定 ⇒ 全文件进堆。
        //   ⇒ 修法 = 自定义 LoadControl：**字节上限硬生效**（48MB ≈ 100s@3.8Mbps）+ 常规时长参数。
        //   （另：manifest 已加 largeHeap 兜底 UI/字幕内存，见 AndroidManifest.xml。）
        // 【v129 修·stuck buffering 的根源】字节上限改成「按堆算」的自适应值。
        //   🔴 为什么必须改：v126 的 48MB 是**拍脑袋定的**（当时按「≈100s@3.8Mbps」估），毫无依据。
        //      而这条 4K DV MKV 实测 ≈4.1MB/s ⇒ 只要 12 秒的残留数据就能把 48MB 占满。
        //   🔴 死锁机制（字节码 + 真机双证）：
        //      `setPrioritizeTimeOverSizeThresholds(false)` 会把**本地播放那个字段也一起改成 false**
        //      （Builder 里是连续两个 putfield，不是只改流媒体那个），于是 shouldContinueLoading 里
        //          isLoading = (prioritize && heapOk) || !targetBufferSizeReached
        //                    = !targetBufferSizeReached            ← prioritize=false 之后
        //      而 targetBufferSizeReached = allocatedCounts×分块长 >= targetBufferBytes
        //      （DefaultLoadControl$PlayerIdFilteringAllocatorImpl 在 allocate() 时 +1、release() 时 -1）
        //      ⇒ 门一占满，加载器立刻停摆；此刻前向缓冲只剩 21ms ⇒ 播放推不动 ⇒ 旧数据 release 不掉
        //      ⇒ 门永远满 = **永久死锁**，4 秒后被 media3 硬编码判死（ERROR_CODE_FAILED_RUNTIME_CHECK）。
        //   🔴 硬字节门**必须保留**：它是 v126 修「720p 低码率长 MKV 灌满堆导致黑屏」的唯一依据。
        //      要改的只是这个数的**来源** —— 从「拍脑袋」改成「按机器堆上限算」。
        //   参考量级：media3 自己算上限时的约束是 200.5MB；本机 largeHeap 512MB ⇒ 128MB（实测峰值仅 95MB）。
        val heapMaxMb = (Runtime.getRuntime().maxMemory() / 1048576L).toInt()
        val targetMb = (heapMaxMb * 25 / 100).coerceIn(128, 256)
        Log.d("XJ_VID", "LoadControl targetBufferBytes=${targetMb}MB (heapMax=${heapMaxMb}MB)")
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15000,   // minBufferMs
                30000,   // maxBufferMs
                2500,    // bufferForPlaybackMs
                5000,    // bufferForPlaybackAfterRebufferMs
            )
            .setTargetBufferBytes(targetMb * 1024 * 1024)   // v129：按堆自适应（512MB 堆 ⇒ 128MB）
            .setPrioritizeTimeOverSizeThresholds(false) // 大小上限必须硬生效（默认 true = 无视字节上限）
            .build()
        val mp = ExoPlayer.Builder(ctx)
            .setLoadControl(loadControl)
            .build()
        try {
            // ⚠️ ExoPlayer 的 AudioAttributes 是 **androidx.media3.common.AudioAttributes**，
            //    不是 android.media 那个同名类（这里必须用全限定名，否则撞车）。
            //    第二参 handleAudioFocus = **false**：保持与 MediaPlayer 时代一致 —— 音频焦点/媒体中心
            //    统一由 PlayerService 接管（attachVideoPlayback），播放器自己别抢。
            mp.setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                false
            )
            mp.setMediaItem(MediaItem.fromUri(uri))
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
        // 【v146】换播放器 ⇒ 承载视图必须重绑到新播放器，否则字幕层还挂在**已 release 的旧实例**上，
        //   表现是"换片后原生字幕再也不出字"。跟下面 videoSurfaceRef 重绑同一个道理。
        //   ⚠️ SubtitleView **没有公开的 setPlayer**（那是 PlayerView 内部用的）⇒ 走 onCues 手动喂。
        // 已有 Surface 立即绑定（SurfaceView 已就位）；否则等 surfaceCreated 再绑。
        // ⚠️ 必须放在 videoMpRef[0] = mp 之后——bindVideoSurface 靠 videoMpRef[0] 取播放器，
        // 放前面会取到 null 而静默跳过绑定 → 新建播放器永远没画面（黑屏）。
        videoMpBoundSurface = null   // 新播放器，强制绑定一次
        videoSurfaceRef[0]?.let { bindVideoSurface(it, "CREATE") }
        // 【v119】原 `setOnPreparedListener` 的 body **原样抽成局部函数**（缩进完全不动），
        //   改由下面的 `Player.Listener.onPlaybackStateChanged(STATE_READY)` 触发。
        // ⚠️ 为什么要抽函数 + 加一次性闸门：STATE_READY 会**多次**触发（每次缓冲完成、每次 seek
        //    回落再回 READY），而 onPrepared 整个生命周期只该跑一次 —— 不守闸门就会每 seek 一次
        //    就重跑一遍续播逻辑（位置被反复拽回 target，表现成"跳句后自己又跳回来"）。
        var readyHandled = false
        // 「暂停态恢复」时用来抓住"这次 seek 落地了"：ExoPlayer 没有 setOnSeekCompleteListener，
        // 对应物是 Player.Listener.onPositionDiscontinuity(reason = SEEK)。
        var pendingPauseAfterSeek = false
        fun onVideoPrepared() {
            // 【v123 修·报错浮层粘住】片子真的就绪了 ⇒ 上一次那条报错已经不成立，必须清掉。
            // 🔴 根因（阿亮实测：「一切集就播放失败，这个报错现在还在播放页，切其他集也是一直报错」）：
            //    videoErrorMessage 原本**只有**「重试 / 知道了」两个按钮会清，而切集走的是
            //    LaunchedEffect(videoUri) 重建播放器这条**完全不相干**的路 ⇒ 新片子明明放得好好的，
            //    那层 75% 黑罩却一直挂在画面上，看着就像"画面不行/一直在报错"
            //    （实测点一下浮层，画面立刻恢复正常 ⇒ 底层播放从来没坏，纯粹是浮层粘住）。
            // ⚠️ 为什么放这里、而不是 LaunchedEffect 开头就清：只有**真到 READY**才算翻篇。
            //    放开头会在「新片子照样打不开」时把刚发生的错误一闪而过 —— 那不是修，是把错误藏起来。
            videoErrorMessage = null

            videoDuration = mp.duration.coerceAtLeast(0)
            videoPrepared = true
            checkSubtitleTimeline()
            runCatching { mp.playbackParameters = PlaybackParameters(speed) }
            // 续播位置：优先权威续播位；否则用 currentPos（冷启恢复），片尾附近回开头防黑屏
            val target = if (videoResumePos > 0) videoResumePos else (if (resumePos > 0) {
                val nearEnd = resumePos >= videoDuration - 2500 || resumePos >= (videoDuration * 98 / 100)
                if (nearEnd) 0L else resumePos
            } else 0L)
            Log.d("XJ_VID", "onPrepared target=$target resumePos=$resumePos spResumePos=$spResumePos dur=$videoDuration videoResumePos=$videoResumePos nearEnd=${(resumePos >= videoDuration - 2500 || resumePos >= (videoDuration * 98 / 100))}")
            // 【v146】STATE_READY 之后**补一次文本轨选中** —— 这是原生字幕能出字的关键一步，
            //   不是诊断代码，**别删**。理由见下面 LaunchedEffect 那段的长注释：
            //   轨道要等 READY 才出现，而那个 effect 在 `prepare()` 之前就跑过一次、
            //   那时 `currentTracks` 是空的 ⇒ override 建不起来（真机实测：单轨 mov_text
            //   一直 `[en]` 不带星号、`onCues` 一次不来）。
            runCatching {
                val groups = mp.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                if (videoNativeSubOn) {
                    val tg0 = groups.firstOrNull()
                    if (tg0 != null) {
                        mp.trackSelectionParameters = mp.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(
                                androidx.media3.common.TrackSelectionOverride(tg0.mediaTrackGroup, 0)
                            )
                            .build()
                    }
                }
            }.onFailure { Log.w("XJ_VID", "force-select TEXT track failed: ${it.message}") }
            if (target > 0) {
            // 【0910】冷启/换片续播：绝不能重播已听内容。
            // MediaPlayer 时代用 SEEK_NEXT_SYNC（只往前跳一个 GOP）来绕开"被吸到上一个关键帧"；
            // ExoPlayer 直接用 **SeekParameters.EXACT** 精确落到 target 本身 ⇒ 既不回退也不多跳，更准。
            // （后台回前台走的是上面的 REUSE 分支，压根不会到这里。）
            // ⚠️ pendingPauseAfterSeek 必须在 seek **之前**置位：seek 的完成回调是异步的，
            //    晚设会漏掉这一次（见下面 onRenderedFirstFrame）。
            pendingPauseAfterSeek = true
            runCatching { mp.setSeekParameters(SeekParameters.EXACT) }
            runCatching { mp.seekTo(target) }
                currentPos = target
            }
            videoResumePos = 0                    // 消费掉，避免后续冷启误用
            // 🔴【1008 黑屏修复】这里改用**实时** videoPlaying 判定，不用上面那个 startPlaying 快照：
            // 从快照点(L596)到这里之间隔着「等 Surface」的挂起点（最长 500ms），期间音频焦点被抢
            // /媒体中心操作都会走 videoTogglePlay() 把状态改掉，拿旧快照决策就是拿过期意图开机关。
            if (videoPlaying) {
                mp.play()
                attachVideoPlayback()
            } else {
                if (target > 0) {
                    // 🔴【1008 黑屏根因修复】「暂停态恢复 + 有续播位置」时**不能直接 pause()**。
                    // seek 是异步的，而播放器此刻刚 prepare 完、**从没 play 过** ⇒ 渲染器处于暂停态，
                    // seek 落到目标帧也不会提交到 Surface ⇒ 视频窗口纯黑、没有任何字
                    // （真机实锤：dumpsys numFramesTotal 停在 1，播放键显示「▶️」）。
                    // ⇒ 先让渲染器跑起来、把目标帧画出来，等**第一帧真的上屏**后再停住。
                    //   起播期间静音：这一下纯粹为了出画面，绝不能漏出声音。
                    runCatching { mp.volume = 0f }
                    runCatching { mp.play() }
                    // 收尾交给 listener 的 onRenderedFirstFrame；再挂一个超时兜底 ——
                    // 万一那帧迟迟不来（文件损坏等），也不能让视频一直静音着播下去。
                    pageScope.launch {
                        delay(2500)
                        if (pendingPauseAfterSeek) {
                            pendingPauseAfterSeek = false
                            Log.w("XJ_VID", "onRenderedFirstFrame 超时兜底：强制恢复音量")
                            if (!videoPlaying) runCatching { mp.pause() }
                            runCatching { mp.volume = 1f }
                            PlayerService.instance?.notifyVideoStateChanged()
                        }
                    }
                } else {
                    // 从头开始且保持暂停：没有 seek，直接停在首帧（首帧本身就有画面，不黑）。
                    runCatching { mp.pause() }
                }
                // 即便暂停也要接管媒体中心/通知/焦点，并停掉旧音频（防幽灵双声）：
                // 进视频模式但视频处于暂停态时，若不接管，残留的音频会继续响。
                attachVideoPlayback()
            }
            PlayerService.instance?.notifyVideoStateChanged()
        }
        // 【v119】ExoPlayer 用统一的 Player.Listener 承接 就绪 / 播完 / 首帧 / 出错 四类事件
        // （原来 MediaPlayer 那一堆 setOnXxxListener 的等价物）。
        mp.addListener(object : Player.Listener {
            // 🔴🔴【v146 原生字幕】**唯一把字幕喂给承载视图的地方**。
            //   为什么是这条路而不是 `subtitleView.setPlayer(...)`：
            //     `SubtitleView` **没有公开的 setPlayer**（那是 PlayerView 内部用的 @UnstableApi 东西），
            //     它的公开入口只有 `setCues(list)`。而 media3 官方文档明说：
            //     "对于实现自定义 UI 的视频应用…**`Listener.onCues` 回调可用于接收播放期间
            //      需要渲染的字幕**" ⇒ 我们正是自定义 UI（裸 SurfaceView）这条路。
            //   ⇒ 现在这条链路是完整的：
            //     播放器选轨（setTrackTypeDisabled）→ TextRenderer 解出 cues → 这里 onCues 拿到
            //     → SubtitleView.setCues() 画出来（**长相仍是 SubtitleView 默认那套**）。
            //   ⚠️ 关字幕时播放器不选文本轨 ⇒ 这里收到的是空 CueGroup ⇒ setCues(空) ⇒ 字幕消失，
            //     不需要在别处再判一次 videoSubOn（判了反而容易两边打架）。
            //   ⚠️ 直接用全限定类型写参数：CueGroup 这个类名在本文件可能与其他概念撞名，
            //     不给 IDE 留自动导错的机会。
            override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                // 🔴🔴【v148 更正】黑底的正解在 **SubtitleView 的 CaptionStyleCompat.backgroundColor**
                //   （见 AndroidView factory 里 `sv.setStyle(...)` 那段长注释 + javap 实证）。
                //   这里清 cue 的 `windowColor` **不是**解法 —— 片源/DEFAULT 的 windowColor
                //   本来就是透明，清了也没变化（v147 我在这点上判断错了，被真机抓包）。
                //   ⚠️ 保留这行只为"万一某条轨真带了 windowColor 也顺手清掉"，**别指望它去黑底**。
                runCatching {
                    val cleaned = cueGroup.cues.map { c ->
                        c.buildUpon().clearWindowColor().build()
                    }
                    videoSubtitleViewRef[0]?.setCues(cleaned)
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    // 就绪：交回上面那个一次性函数（⚠️ 必须守 readyHandled 闸门，见其定义处注释）
                    Player.STATE_READY -> if (!readyHandled) { readyHandled = true; onVideoPrepared() }
                    // 播完：原 setOnCompletionListener 的等价物
                    Player.STATE_ENDED -> {
                        videoPlaying = false
                        PlayerService.instance?.notifyVideoStateChanged()
                        // 【1007 连播】按播放模式自动续播（顺序/列表循环/单曲循环，与音频共用设置）。
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            videoAutoAdvanceIfEnabled()
                        }
                    }
                }
            }

            /** 第一帧真正画到 Surface 上 —— 「暂停态恢复」的收尾时机。
             *  它比 MediaPlayer 的 onSeekComplete 更准：要的正是「画面出来了」这个事实，
             *  而 seek 完成不等于已上屏。 */
            override fun onRenderedFirstFrame() {
                if (!pendingPauseAfterSeek) return
                pendingPauseAfterSeek = false
                // 若这几十毫秒里用户自己点了播放（videoPlaying=true），就别再把它按停。
                if (!videoPlaying) runCatching { mp.pause() }
                runCatching { mp.volume = 1f }
                PlayerService.instance?.notifyVideoStateChanged()
            }

            // 【0903 黑屏修复】错误回调：文件读不到/权限拒/解码失败时显示具体错误到视频窗口
            // （替代静默黑屏）；并给「换视频 / 重试」按钮（见 VideoStateOverlay）。
            // ExoPlayer 的 errorCode 比 MediaPlayer 的 (what, extra) 细得多，逐类映射成人话。
            override fun onPlayerError(e: PlaybackException) {
                val hint = when (e.errorCode) {
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                        "找不到视频文件（可能已被删除或移动）"
                    PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
                        "无法读取视频文件（权限不足）"
                    // 【1012 阿亮定】网络类别混进"权限不足/已删除"里 —— 那是两码事，要分开说
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                        "网络读不到这个视频"
                    PlaybackException.ERROR_CODE_IO_UNSPECIFIED ->
                        "读不到这个视频文件"
                    // 【1012 阿亮定】文件损坏 ≠ 格式不支持，分开说（原来两者混成一句"损坏或不被支持"）
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
                        "这个视频文件损坏了，播不了"
                    // 【1012 阿亮定】真·格式/编码不支持 ⇒ **直接说播不了**。
                    //   原来那句"试试转码成标准 mp4"**删掉** —— 转码是兜底手段，
                    //   不该在报错里指挥用户去干这个（阿亮反复强调：兜底最后考虑）。
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
                        "这个视频的格式不支持，播不了"
                    // 【1012 阿亮定】超时属于"软件的毛病"，**不许再说"请重试"** —— 那句是误导
                    //   （重建播放器也救不回来）。但按钮本身已修好（见 videoRetryTick），值得点一下。
                    PlaybackException.ERROR_CODE_TIMEOUT ->
                        "播放卡住了（软件的毛病，不是你的文件问题）"
                    // 内部错误码只该进日志，不给用户看 —— 他看不懂，也不会因此多做什么
                    else -> "播放失败"
                }
                Log.w("XJ_VID", "ERROR code=${e.errorCode}(${e.errorCodeName}) hint=$hint cause=${e.cause}")
                videoErrorMessage = hint
                videoPlaying = false
                PlayerService.instance?.notifyVideoStateChanged()
            }
        })
        runCatching { mp.prepare() }
    }

    // 🔴🔴【v146 1011 阿亮】「原生字幕」开关（竖屏/全屏共用 `videoNativeSubOn`）—— **唯一收口点**。
    //   上面 LaunchedEffect(videoUri) 里那次设置只在**换片/首次建播放器**时跑一次；
    //   开关在播放中途被点来点去时，靠这里即时生效（不用重建播放器）。
    //   关 = 播放器禁用 TEXT 轨（画面无原生字幕）；开 = 放开 + 显式选中，播放器原生渲染。
    //   ⚠️ 必须用 videoMpRef[0] 取**当前**播放器：换片时上面那个 effect 会先跑（新播放器已就位），
    //      这里紧接着再确认一次，两条路径结果一致。
    //   🔴 为什么变量从 `videoSubOn` 换成 `videoNativeSubOn`：`videoSubOn` 那个是"我们自绘字幕"
    //      的显隐开关（老语义，横屏那套）。原生的要有一个**只管原生**的独立开关，
    //      ⚠️ 否则那个按钮会**把两套一起开关**（v146 之前就是这毛病，阿亮实测抓到）。
    LaunchedEffect(videoNativeSubOn, videoMpRef[0]) {
        val p = videoMpRef[0] ?: return@LaunchedEffect
        runCatching {
            var b = p.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !videoNativeSubOn)
            // 🔴🔴【v146 真机实测补的】**光"不禁用"不够 —— 还得显式选中**。
            //   实测对照（同一台机、同一份代码）：
            //     · 老友记 mkv（2 条 ASS 轨）→ `READY TEXT tracks=2 [zh*选中 | zh]` ← 自己选中了
            //     · 自制 mp4（1 条 mov_text 轨）→ `READY TEXT tracks=1 [en]`      ← **没选中、onCues 一次不来**
            //   ⇒ `setTrackTypeDisabled(TEXT,false)` 只是把 TEXT 轨**放回可选集合**，
            //     至于选不选，"自动选择"那套规则在单轨/非默认轨道上并不保证命中。
            //   这里在开关打开时**显式指定文本轨**（override 那套），关掉时清空 override 并交回禁用。
            //   ⚠️ `TrackSelectionOverride` 用 `mediaItem` + `trackGroup` 构造；trackGroup 从
            //      **当前已加载的轨道**里取第一组 TEXT（`currentTracks` 在 STATE_READY 后才有值）。
            //   ⚠️ 取不到就**保持原样**（退回"只放开、不指定"），不能因为 override 建不起来把功能搞挂。
            if (videoNativeSubOn) {
                b = applyNativeTrackOverride(b, p)
            } else {
                b = b.clearOverridesOfType(C.TRACK_TYPE_TEXT)
            }
            p.trackSelectionParameters = b.build()
        }.onFailure { Log.w("XJ_VID", "subtitle track toggle failed: ${it.message}") }
    }


    // 字幕列表自动滚动到当前句
    LaunchedEffect(videoSentenceIndexGlobal) {
        if (videoSentenceIndexGlobal >= 0) subtitleListState.animateScrollToItem(videoSentenceIndexGlobal)
    }

    // 【1007】识别进度/结果浮层（显示在视频画面正中间的半透明胶囊，替代 TRANSPORT 模块里
    // 那两行状态文字 —— 阿亮："不占下面一行字的空间，抓取/识别时在画面上就能看到进度，
    // 抓取完了自动消掉"）。
    // 显示规则：识别中常驻（浅底深字+转圈，1008 由黑底白字改）；完成后 asrMsg 挂一段时间自动消失 ——
    // 成功（「识别完成」开头）3 秒；其余（出错/字幕缺失/未配 Key…）浅红底 8 秒，给足看报错的时间。
    // 🔴 这颗胶囊是**所有**视频状态提示的唯一出口：识别进度 / 识别结果 / B站 抓取状态 / 各类错误，
    //    全部从下方 asrOverlayText 的 when 分支进来、共用同一个 Surface
    //    ⇒ 改样式/配色一处即全覆盖，不存在"只改了某一种状态"的可能。
    // 🔴【1008 修②】"识别完成：N 句"是**数据快照**不是事件：asrMsg 识别完就一直留着不清空，
    //    而本页在切去音频模式时会被**卸载**（MainActivity 只在 page==VIDEO、或「从视频进阅读」时
    //    渲染本页），切回来 = 重建组合 ⇒ LaunchedEffect 首次执行，把这段陈旧消息当成"刚发生"
    //    重播 3 秒（阿亮：音频↔视频 必现；视频↔阅读 不出现，因为本页压根没卸载）。
    //    ⇒ 用 seenMsg 记"这条消息展示过没有"：**首次组合即视为已看过**，
    //    只认本次组合内**新出现**的 asrMsg。（同样是"别拿数据快照反推事件"那条纪律。）
    // ⚠️ 初值**只把"上一次识别的回执"标为已看过**（"识别完成…"/"识别出错…"），那才是要屏蔽的重播；
    //    其余消息一律当作新消息照常显示 —— 尤其 "⚠️ 字幕缺失…" 是**当前视频的状态**（不是回执），
    //    若一起屏蔽，从文件夹页打开残字视频时就看不到提醒了 = 重犯 0929 修过的那个问题。
    var seenMsg by remember { mutableStateOf(if (asrMsg.startsWith("识别")) asrMsg else "") }
    var asrOverlayVisible by remember { mutableStateOf(false) }
    LaunchedEffect(recognizing, asrMsg) {
        when {
            // 新一轮识别开始：清账，保证之后即使文本与上次完全相同也能再显示
            recognizing -> {
                asrOverlayVisible = true
                seenMsg = ""
            }
            asrMsg.isNotEmpty() && asrMsg != seenMsg -> {
                seenMsg = asrMsg
                asrOverlayVisible = true
                delay(if (asrMsg.startsWith("识别完成")) 3000L else 8000L)
                asrOverlayVisible = false
            }
            // asrMsg 被清空（打开新视频等）：连账一起清，下次同文本仍能提示
            asrMsg.isEmpty() -> {
                asrOverlayVisible = false
                seenMsg = ""
            }
            // 非空但已经看过（= 切页面回来时的那段陈旧消息）：什么都不做
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
                // 【v124】画面手势：左半屏上下滑 = 亮度、右半屏上下滑 = 音量（左右分工是实测小米播放器得来的）
                // 【v157】新增横向：左右滑 = 快进/快退（**拖动中跟手预览、松手才精确定位**）
                //
                // 🔴🔴 **必须手写方向锁定**：绝不能把 detectVerticalDragGestures 和
                //    detectHorizontalDragGestures 叠在一起 —— 两个 detector 各等各的滑移量，
                //    谁先越过 slop 谁抢到事件，实际表现就是"滑到一半突然变身"（阿亮担心的正是这个）。
                //    正解：自己收原始指针事件，**先判主方向、判定后整场锁死不再改**。
                .pointerInput(Unit) {
                    val slop = viewConfiguration.touchSlop
                    // 横向起点离左右边缘多近算"贴边" ⇒ 让位给系统返回手势。
                    // 没这道闸的话：从屏幕边缘往中间划想快进，会先被系统吃掉变成"返回"。
                    val edgeGuard = 32.dp.toPx()

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // 左右分工照搬 v124：用**按下时**的 x 判，滑动中跨半屏不会串台
                        val startX = down.position.x
                        var isBrightness = startX < size.width / 2f
                        val nearEdge = startX < edgeGuard || startX > size.width - edgeGuard

                        var acc = 0f
                        // 松手写盘。拖动过程里不写（一次一次写 SharedPreferences 白费）。
                        // 🔴 只在"确实调的是亮度"时才存；音量是**系统音量**，系统自己记着，不归我们管。
                        // 🔴 被打断也要写 —— 否则会出现"我明明调了，重开又回去了"。
                        val persistBrightness = {
                            if (isBrightness) BrightnessPrefs.save(context, appBrightness ?: sysBrightness)
                        }

                        var axis = 0            // 0=未判 1=竖向(亮度/音量) 2=横向(进度)
                        var dx = 0f             // 累计横向位移（横向用**累计**：seek 是相对起点的绝对偏移）
                        var dy = 0f
                        var seekBaseMs = 0L
                        var seekDeltaMs = 0L
                        var wasPlayingBeforeDrag = false
                        var lastPreviewAt = 0L  // 跟手预览的节流时间戳

                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            val d = ch.position - ch.previousPosition
                            dx += d.x
                            dy += d.y

                            if (!ch.pressed) {
                                // ================= 抬手 =================
                                when (axis) {
                                    2 -> {
                                        // 松手才**精确**落位：预览用的 CLOSEST_SYNC 会差几秒
                                        val mp = videoMpRef[0]
                                        val dur = mp?.duration?.let {
                                            if (it == C.TIME_UNSET || it <= 0L) 0L else it
                                        } ?: 0L
                                        if (mp != null && dur > 0L) {
                                            val target = (seekBaseMs + seekDeltaMs).coerceIn(0L, dur)
                                            Log.d("XJ_SEEK", "gestureEnd base=$seekBaseMs delta=$seekDeltaMs target=$target dur=$dur")
                                            videoSeek(target, "horizontalGesture")   // 内部会切回 EXACT
                                            if (wasPlayingBeforeDrag) {
                                                mp.play(); videoPlaying = true
                                                PlaybackSession.onVideoStarted()
                                                attachVideoPlayback()
                                                PlayerService.instance?.notifyVideoStateChanged()
                                            }
                                        }
                                        gestureHint = 0
                                    }
                                    1 -> persistBrightness()
                                }
                                break
                            }

                            if (axis == 0) {
                                // ============ 方向判定（只判一次，判完锁死）============
                                if (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop) {
                                    // 【横向门槛故意更高】竖滑是天天用的（亮度/音量），横滑偶尔用。
                                    // 要求 |dx| > |dy| * 1.5 才算横滑 ⇒ 手稍微斜一点仍走竖向，不破坏老习惯。
                                    // 贴边时一律不给横滑（那块地是系统返回手势的）。
                                    axis = if (!nearEdge && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.5f) 2 else 1
                                    Log.d("XJ_SEEK", "gestureAxis=$axis dx=$dx dy=$dy slop=$slop viewW=${size.width} nearEdge=$nearEdge")
                                    if (axis == 2) {
                                        seekBaseMs = videoMpRef[0]?.currentPosition ?: 0L
                                        wasPlayingBeforeDrag = videoPlaying
                                        // 拖动期间先停住：不然"播放"会盖掉 seek 的结果，画面来回抖
                                        if (wasPlayingBeforeDrag) runCatching { videoMpRef[0]?.pause() }
                                        gestureHint = 3
                                    } else {
                                        acc = if (isBrightness) {
                                            // 🔴 直接读全局 appBrightness —— 不能在组合期先算成局部 val：
                                            //    pointerInput(Unit) 的 lambda 只创建一次，局部 val 会被捕获成"永远的老值"。
                                            appBrightness ?: sysBrightness
                                        } else {
                                            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
                                        }
                                        gestureHint = if (isBrightness) 1 else 2
                                        gestureHintValue = acc
                                    }
                                    // ⚠️ 只有**真的开始拖了**才消费事件。没超 slop 时消费掉，
                                    //    会把下面 .clickable 的「点一下浮出控制条」判成移动、彻底失效。
                                    ch.consume()
                                }
                                continue
                            }

                            if (axis == 1) {
                                // ============ 竖向：照搬 v124 的行为 ============
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                // ⚠️ 这里用**本次增量 d.y**（不是累计 dy）—— v124 的 dragAmount 就是增量语义。
                                // 向上滑 ⇒ d.y 为负 ⇒ 数值变大。整屏高度滑一遍 = 走完整量程。
                                // 亮度留个下限：真到 0 屏幕就全黑了，什么都看不见。
                                acc = (acc - d.y / h).coerceIn(if (isBrightness) 0.01f else 0f, 1f)
                                if (isBrightness) {
                                    appBrightness = acc   // 全局状态（方案 B）⇒ 全 App 统一，切页面不跳
                                } else {
                                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                    // flags = 0 ⇒ 不弹系统那条音量条，提示统一由我们自己的浮层出
                                    audioManager.setStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        (acc * max).roundToInt().coerceIn(0, max),
                                        0,
                                    )
                                }
                                gestureHintValue = acc
                            } else {
                                // ============ 横向：快进 / 快退 ============
                                val dur = videoMpRef[0]?.duration?.let {
                                    if (it == C.TIME_UNSET || it <= 0L) 0L else it
                                } ?: 0L
                                if (dur > 0L) {
                                    // 滑满画面宽度 = 总时长的 10%，限幅 10 秒 ~ 5 分钟
                                    // ⇒ 10 分钟的短片和 2 小时的长片都顺手。
                                    // ⚠️ 按**画面自己的宽度**算，不是屏幕宽度：竖屏时视频窗口只占上面一块，
                                    //    按屏幕宽度算会灵敏过头。
                                    val span = (dur / 10).coerceIn(10_000L, 300_000L)
                                    seekDeltaMs = (dx / size.width.toFloat() * span).toLong()
                                    val target = (seekBaseMs + seekDeltaMs).coerceIn(0L, dur)
                                    gestureSeekText = formatSeekHint(target, dur, seekDeltaMs)
                                    gestureSeekBack = seekDeltaMs < 0
                                    gestureHintValue = (target.toFloat() / dur).coerceIn(0f, 1f)
                                    // 跟手预览：**节流**。不节流会让 seekTo 排队压垮解码器
                                    // （4K 尤其明显）⇒ 反而卡成幻灯片。
                                    // 【v160】100ms → **50ms**：阿亮 1080P / 4K 都实测试过"不卡"，
                                    // 有余量 ⇒ 加密一倍让画面闪过更多。⚠️ 再往下调（30ms 以下）
                                    // 或去掉节流之前，必须让他先试，别自己加码。
                                    val now = System.currentTimeMillis()
                                    if (now - lastPreviewAt >= PREVIEW_THROTTLE_MS) {
                                        lastPreviewAt = now
                                        videoSeekPreview(target)
                                    }
                                }
                            }
                            ch.consume()
                        }
                    }
                }
                .clickable { controlsVisible = !controlsVisible }) {
                if (videoUri != null) {
                    AndroidView(
                        factory = { ctx ->
                            // 【v122 · 治 HDR 发灰】SurfaceView 取代 TextureView。
                            // 官方明说：Android 13+ 的 TextureView 会把 HDR 转码成 SDR（细节丢失、色彩
                            // 剪切、出现条带）⇒ 我们之前拿到的是一份"劣质 HDR→SDR"结果，表现就是整体
                            // 暗一档、对比度塌。SurfaceView 独立成层，走硬件叠加路径、由显示端做 tone
                            // mapping，才跟系统播放器（小米视频）一个观感。
                            SurfaceView(ctx).also { sv ->
                                // 🔴🔴【v146 原生字幕】**必须设 mediaOverlay**，否则原生字幕被这层盖住看不见。
                                //   根因（v146 实测：`onCues n=1 view=true` 全正常、画面却一个字没有，
                                //   且 uiautomator dump 的画面区里**找不到 SubtitleView 节点**）：
                                //   `SurfaceView` 是**独立图层**，默认 z 序在**所有普通 View 之上**（v122 注释
                                //   里"独立成层、走硬件叠加路径"说的就是这件事）⇒ 我们在它之后声明的
                                //   SubtitleView 虽然逻辑上"在上面"，实际被这个硬件层**整个盖住**，
                                //   字幕画了但看不见。
                                //   `setZOrderMediaOverlay(true)` = 把 SurfaceView 降到"媒体层"，
                                //   与普通 View **同一层**、仍在本窗口之内（**不会**像 setZOrderOnTop 那样
                                //   盖住顶栏/控制条/字幕条 —— 那个警告见下面几行，依然成立）。
                                //   ⚠️ 这条同时解决"HDR 走 SurfaceView"与"字幕能盖上来"两个诉求：
                                //     mediaOverlay 只是改 z 序归属，**不改变 SurfaceView 的硬件解码/叠加路径**，
                                //     v122 治 HDR 发灰的结论不受影响（实测复核：画面观感无变化）。
                                sv.setZOrderMediaOverlay(true)
                                // 【圆角】SurfaceView 是独立图层，不吃父级 Compose 的圆角裁剪；系统从
                                // Android 12(API 31) 起才支持按 outline 裁 SurfaceView。12 以下退回直角
                                // 黑角（贴在本来就黑底的播放区上，可接受）。
                                if (Build.VERSION.SDK_INT >= 31) {
                                    sv.clipToOutline = true
                                    sv.outlineProvider = object : ViewOutlineProvider() {
                                        override fun getOutline(view: View, outline: Outline) {
                                            if (view.width <= 0 || view.height <= 0) return
                                            outline.setRoundRect(
                                                0, 0, view.width, view.height,
                                                14f * view.resources.displayMetrics.density,
                                            )
                                        }
                                    }
                                }
                                // 【切后台不销毁 Surface】Android 14(API 34)+ 才有这张牌：让 Surface 跟
                                // 「挂载/卸载」走，而不是跟「可见不可见」走 ⇒ 切后台不销毁 ⇒ 回前台不用
                                // 重绑 ⇒ 不闪、不卡。13 及以下只能吃默认（跟着可见性），但 ExoPlayer 走
                                // MediaCodec.setOutputSurface()（API 23+，专为「不 flush 换画面」设计，
                                // 我们 minSdk=26 恒走此路）⇒ 最坏黑一帧，不会像当年 MediaPlayer 那样
                                // release/重建解码器回关键帧，也就不会「重播当句」。
                                if (Build.VERSION.SDK_INT >= 34) {
                                    sv.setSurfaceLifecycle(SurfaceView.SURFACE_LIFECYCLE_FOLLOWS_ATTACHMENT)
                                }
                                // ⚠️ 绝不开 setZOrderOnTop(true)：画面会盖在所有 UI 之上，字幕条、控制排、
                                //    顶栏全被吃掉。默认 z-order 才是对的。
                                var cbSurface: Surface? = null
                                sv.holder.addCallback(object : SurfaceHolder.Callback {
                                    override fun surfaceCreated(h: SurfaceHolder) {
                                        val sf = h.surface ?: return
                                        // 同一个 Surface 对象（FOLLOWS_ATTACHMENT 下切后台没销毁）⇒ 绝不重绑
                                        if (videoSurfaceRef[0] === sf) {
                                            Log.d("XJ_VID", "SV surfaceCreated REUSE(same surface); skip rebind")
                                            return
                                        }
                                        cbSurface = sf
                                        videoSurfaceRef[0] = sf
                                        videoMpBoundSurface = null   // 新 Surface 对象，强制重绑一次
                                        bindVideoSurface(sf, "SV.created")
                                    }
                                    override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) {
                                        // 尺寸变化时 Surface 对象没变，不重绑（多余 setSurface 只会白折腾解码器）
                                    }
                                    override fun surfaceDestroyed(h: SurfaceHolder) {
                                        // 【关键】这里是「摘画面」而不是「让解码器重解关键帧」，两件事要分清：
                                        //  ① 必须摘：Surface 已被系统销毁，继续往里送帧会报错/卡画面。
                                        //     ExoPlayer 侧等价于 clearVideoSurface()，也是 media3 PlayerView 的做法。
                                        //  ② 摘了也不会重播当句：ExoPlayer 在 API 23+ 走
                                        //     MediaCodec.setOutputSurface()，换/摘 Surface **不 flush 解码器**，
                                        //     回前台接上新 Surface 直接续帧。当年 MediaPlayer 没这条路，只能
                                        //     release + 重建解码器 ⇒ 被迫回关键帧，那才是老 bug 的根因。
                                        Log.d("XJ_VID", "SV surfaceDestroyed -> 摘画面(不 flush)；回前台 surfaceCreated 重接")
                                        if (videoSurfaceRef[0] === cbSurface || cbSurface == null) {
                                            videoSurfaceRef[0] = null
                                        }
                                        cbSurface = null
                                        bindVideoSurface(null, "SV.destroyed")
                                    }
                                })
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    // 🔴🔴【v146 阿亮】**原生字幕承载层** —— 播放器把内封字幕渲染到这里。
                    //   为什么必须有这一层：画面是**裸 SurfaceView**（自定义 UI 路线），ExoPlayer 的
                    //   `TextRenderer` 虽然照跑、内封轨也照样被选中（v146 实测：
                    //   `READY TEXT tracks=2 [zh*选中 | zh]`），但**没有承载视图接住 cues** ⇒ 画面不出字。
                    //   `SubtitleView` 就是 media3 官方的承载物（PlayerView 内部也是用它。
                    //   ⚠️ 位置必须在 SurfaceView **之后**（叠在画面之上）、在控制条/角标**之前**
                    //     （被那些 UI 盖住，不会挡住按钮）。
                    //   ⚠️ 层级：Compose 的 AndroidView 是按声明顺序叠的 ⇒ 这层天然在画面之上。
                    //   ⚠️ 不吃点触：字幕层必须**不拦手势**，否则画面上的"点一下浮出控制条"和
                    //     亮度/音量滑动全被它吞掉。
                    AndroidView(
                        factory = { ctx ->
                            SubtitleView(ctx).also { sv ->
                                // ⚠️ 绝不能吃触摸：这层铺满整个画面，一旦可点击，画面上的
                                //   「点一下浮出控制条」和亮度/音量上下滑手势就全被它吞掉。
                                sv.isClickable = false
                                sv.isFocusable = false
                                sv.isFocusableInTouchMode = false
                                // 🔴🔴【v146 真机实测补的】光设上面三个"不可点"**不够** ——
                                //   `SubtitleView` 继承自 `View`，其 `onTouchEvent` 在**可点击时**才返回 true，
                                //   但 Compose 的 `AndroidView` 包装层仍会把它当成一个"有 View 的插槽"，
                                //   实测表现 = 铺满播放区的这一层把「点一下浮出控制条」「播放/暂停」全吞了
                                //   （真机：点画面中央播放键，media_session 一直 `PAUSED(2) position=0` 不动）。
                                //   ⇒ 显式让父级**不拦截触摸**，手势正常穿透到下面的 SurfaceView 手势层。
                                //   ⚠️ 用 `disallowInterceptTouchEvent` 不对（那是拦父级）；这里要的是"本层不接"：
                                //     置 `isEnabled=false` + 关掉 clickable，等价于 `View.VISIBLE` 但透明穿透。
                                sv.isEnabled = false
                                // 这一层纯展示、无背景 ⇒ 必须**不消耗**任何触摸事件
                                sv.setOnTouchListener { _, _ -> false }
                                // 🔴🔴【v146 重要发现】**必须保留内嵌样式**（原本我一度关掉，是错的）。
                                //   实测扒出片源字幕 = **ASS**（`ffprobe`: index=2/3 codec_name=ass,
                                //   title=简英双字/繁英双字）。ASS 的"双语"排版**全靠内嵌样式**：
                                //     中文正文 + `\N` 硬换行 + `{\fn…\fs14\1c&H4080FF&\b0}英文小字`
                                //   ⇒ 关掉内嵌样式 = 丢掉换行和字号区分，"简英双字"会挤成一行同样大小。
                                //   ⚠️ 一开始怀疑的"ASS `\pos` 绝对坐标把字画到画面外"——**本片源不成立**：
                                //     全片事件里用的是 `\N` 换行 + 默认底部对齐（`Style` 里 Alignment=2），
                                //     只有极个别标题行用 `\an8`。常规台词都是底部居中，坐标是合法的。
                                sv.setApplyEmbeddedStyles(true)
                                sv.setApplyEmbeddedFontSizes(true)
                                // 🔴🔴【v148 真机实证·去黑底的正解】黑底**不是 cue 的 windowColor**，是
                                //   `CaptionStyleCompat.backgroundColor`！
                                //   javap 反编译 `CaptionStyleCompat` 静态初始化：
                                //     `DEFAULT = new CaptionStyleCompat(-1, -16777216, 0, 0, -1, null)`
                                //     第 2 个参数 `-16777216` = 0xFF000000 = **纯不透明黑** = backgroundColor；
                                //     第 3 个参数 `0` = **透明** = windowColor。
                                //   而 `SubtitlePainter` 取色次序（javap 实证）：
                                //     `Cue.windowColorSet ? Cue.windowColor : CaptionStyleCompat.windowColor`
                                //     `backgroundColor = CaptionStyleCompat.backgroundColor`（**没有 cue 级来源**！）
                                //   ⇒ v147 清 `clearWindowColor()` **清了个无关的东西**（DEFAULT 的 windowColor
                                //     本来就是透明 0）⇒ 黑底纹丝不动，阿亮当场抓包。
                                //   ✅ 正解 = 给 SubtitleView 设一份**背景透明**的 CaptionStyleCompat，
                                //     前景/描边/字全部沿用 DEFAULT，**只把 backgroundColor 改成透明**。
                                //     ⚠️ `CaptionStyleCompat` 的构造函数参数顺序是
                                //        (foregroundColor, backgroundColor, windowColor,
                                //         edgeType, edgeColor, typeface) —— 别写错位置。
                                runCatching {
                                    sv.setStyle(
                                        androidx.media3.ui.CaptionStyleCompat(
                                            androidx.media3.ui.CaptionStyleCompat.DEFAULT.foregroundColor,
                                            android.graphics.Color.TRANSPARENT,   // ← 就是这里：背景透明
                                            androidx.media3.ui.CaptionStyleCompat.DEFAULT.windowColor,
                                            androidx.media3.ui.CaptionStyleCompat.DEFAULT.edgeType,
                                            androidx.media3.ui.CaptionStyleCompat.DEFAULT.edgeColor,
                                            androidx.media3.ui.CaptionStyleCompat.DEFAULT.typeface,
                                        )
                                    )
                                }.onFailure { Log.w("XJ_VID", "设 CaptionStyle 失败: ${it.message}") }
                                // 记进文件级 ref：换播放器时 onCues 那边要往这里喂（见 Player.Listener.onCues）。
                                // ⚠️ 这里**不调 setPlayer** —— SubtitleView 没这个公开 API，
                                //   字幕是播放器 onCues 回调送进来的（media3 官方给自定义 UI 的正道）。
                                videoSubtitleViewRef[0] = sv
                                // 样式用**播放器默认**（阿亮："就是要它默认的"）⇒ 不 setStyle，
                                // 只显式恢复默认的分数尺寸（视口高的 5.33%）与底部留白（8%），
                                // 保证与全屏/竖屏、不同机器上一致。
                                sv.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION)
                                sv.setBottomPaddingFraction(SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION)
                                // ⚠️ 字幕默认贴在画面下方（media3 行为）——竖屏画面只有约 203dp 高，
                                //    字幕会落在画面底沿，跟下方字幕列表是两块地方，不冲突。
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
                            // 🔴【1012 修·这个按钮以前是摆设】两个原因，缺一不可：
                            //   ① key 没变：原写法 `videoUri = null` 紧接 `= cur`（同一快照里改回原值）
                            //      ⇒ Compose 认为 key 没变 ⇒ `LaunchedEffect(videoUri)` **根本不重跑**。
                            //      改用 `videoRetryTick` 递增当第二把 key。
                            //   ② 光重跑还不够：`loadVideo` 里有一条「同一个播放器还活着就**直接复用**」
                            //      的快速路径 ⇒ 会把那个卡死的播放器原样拿回来。
                            //      ⇒ 必须**先把旧播放器放掉**（照 218-220 行那套：摘画面 → release → 置空），
                            //        `loadVideo` 才会走真·重建。
                            runCatching { videoMpRef[0]?.setVideoSurface(null) }
                            runCatching { videoMpRef[0]?.release() }
                            videoMpRef[0] = null
                            videoRetryTick++
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
                // 🔴🔴【v146 1011 阿亮】**这里不做互斥** —— 两个开关各管各的，同时开就让它叠。
                //   阿亮拍板原话："全屏如果两个都开重叠了，那用户自己眼睛能看到，他就自己关掉嘛，
                //   再点一下而已。" ⇒ 只判 `videoSubOn`（我们自绘那套自己的开关），
                //   **不看 `videoNativeSubOn`**：原生归上面那个「原生字幕」开关管，井水不犯河水。
                if (!readingMode && videoFullscreen && videoSubOn &&
                    videoSentenceIndexGlobal in sentences.indices) {
                    Box(
                        // 🔴🔴【1011 阿亮】字幕条是**独立模块**，跟下面那条控制条是**两套操作逻辑**
                        //   （字幕 = 字幕开关常驻；控制条 = 点画面才浮出、过会儿自己藏回去）
                        //   ⇒ 留位必须**跟着控制条走**：
                        //     控制条没出来 ⇒ 字幕**贴着屏幕底**单独显示一行；
                        //     控制条出来   ⇒ 字幕被顶上去，**字幕在上、控制条在下，上下拼接**。
                        //   ⚠️ 绝不能提前先留位（v144 就是固定留 96dp ⇒ 字幕永远吊在半空、下面一条亮视频，
                        //      被当场否掉——**别再这么搞**）。
                        //   ⚠️ 留位值必须用**全屏**控制条的真实高（96dp，大号按钮 72dp）；竖屏那条只有 58dp，
                        //      混用会被控制条压上来又叠一层（1011 实测踩过）。
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = if (controlsVisible) FULLSCREEN_CTRL_BAND_H else 0.dp),
                    ) {
                        Text(
                            // 【1010】按显示模式裁行（双语字幕只看英文/只看中文）；单语字幕原样
                            displayTextOf(sentences[videoSentenceIndexGlobal]),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            // 🔴【1011 阿亮】全屏字幕**居中**（原来没设 textAlign ⇒ 默认靠左）。
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                                .background(CTRL_BAND_COLOR)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
                // 非全屏：右上角全屏按钮，跟随「点画面浮出的底部控制条」一起显隐（controlsVisible），
                // 点一下视频窗口：全屏按钮与底部功能键同时出现 / 同时隐藏。
                if (!readingMode && !videoFullscreen && controlsVisible && videoUri != null) {
                    Row(
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 【1008】字幕菜单：手动指定一份字幕 / 把当前字幕导成 SRT。
                        // 塞在"点画面才浮出的控制层"里（跟右边全屏键同一时机出现）：不常驻、不占布局高度，
                        // 也不动字幕列表和 5 键控制排 —— 那两处阿亮调过很多轮，不碰。
                        Box {
                            IconButton(onClick = { subMenuOpen = true; loadEmbeddedTracks() }) {
                                Icon(Icons.Filled.Subtitles, contentDescription = "字幕", tint = Color.White)
                            }
                            AppDropdownMenu(expanded = subMenuOpen, onDismissRequest = { subMenuOpen = false }) {
                                // 🔴🔴【1011 阿亮】「原生字幕」**开关** —— 放菜单最上面（阿亮："就放在原来那个
                                //   字幕集成里头…点一下开点一下关，有个小按钮"）。
                                //   🔴 **名字叫「原生字幕」**（阿亮定：内部沟通统一用"原生字幕"这个词，
                                //      便于对得上话）。指**播放器原生渲染**的片源自带字幕轨。
                                //   🔴🔴 **必须做成"开关"的样子，不能做成勾选项**（阿亮 1011 实测后纠的）：
                                //      "这个原生字幕要把它做成个开关，不然它跟其他的容易混了，
                                //       它跟那个内嵌字幕…因为它不是选择项，是开关项，是不是？"
                                //      下面「字幕显示」三档 / 「内封字幕（N 条）」轨列表都是**互斥选择项**
                                //      （带 ✓ 单选）；这一项是**独立开关**（开=画面显示片源原生字幕 /
                                //      关=不显示），跟它们不是一类东西 ⇒ 用 **Switch** 画，一眼分得开。
                                //   ⚠️【v146 1011 阿亮 收口】**这个开关只管原生那套** —— 不再碰 videoSubOn。
                                //     阿亮原话："全屏模式下那个字幕开关…那个就只管我们抓取的或生成的字幕的开关，
                                //     现在是它把原生的跟我们抓取或生成的一起开关了，就这么点事儿。"
                                //     ⇒ 两个开关各管各的：**这个管原生**（片源内封轨，竖屏全屏共用）；
                                //       全屏控制条那个按钮管**我们自绘**那套。都开就会上下重叠 —— 阿亮拍板：
                                //       "两个都开重叠了，那用户自己眼睛能看到，他就自己关掉嘛，再点一下而已。"
                                //   🔴🔴【1012 改口径】**没内封轨 ⇒ 整项不显示**（v151~v153 是"灰着点不了"）。
                                //     理由：没有内封轨是**绝大多数视频的常态** ⇒ 灰着占位会让菜单
                                //     **上下两行永远是灰的**，用户打开菜单的第一反应是"字幕功能是不是坏了？"
                                //     （可字幕明明正常显示着）。这是"为了统一而制造噪音"。
                                //     阿亮 1011 原话里本就给了两个选项："要**不显示就都不显示**，要灰就都一起灰"
                                //     —— 这里选"都不显示"，比"永远灰两行"干净得多。
                                //   ⚠️ 有内封轨时**行为完全不变**（开关 + 下面的轨列表照旧）。
                                val hasEmbedded = embeddedTracks.isNotEmpty()
                                if (hasEmbedded) {
                                    DropdownMenuItem(
                                        text = { Text("原生字幕") },
                                        trailingIcon = {
                                            Switch(
                                                checked = videoNativeSubOn,
                                                onCheckedChange = { videoNativeSubOn = it },
                                            )
                                        },
                                        onClick = { videoNativeSubOn = !videoNativeSubOn },
                                    )
                                }
                                // 🔴【1010】下方字幕档位：英中双语 / 只看英文 / 只看中文（默认只看英文）。
                                //   只对**双语字幕**起效 —— 单语字幕任何档位都原样显示，不会变空。
                                //   放在菜单最上面（最常用）。
                                // 🔴【1011 阿亮改名】原叫「字幕显示」太概括 —— 用户看不出它指"画面下方那条字幕"。
                                //   改成「下方字幕」：直接说位置（阿亮原话："指的是显示窗口下面的字幕"）。
                                //   ⚠️ 只管**画面下方那条字幕**（`videoSubOn` 那套 / 片源轨），不碰别的。
                                // 🔴🔴【1011 阿亮】**ASR 生成的字幕 ⇒ 标题灰掉，三档不再展示**。
                                //   理由（MEMORY 铁律）：ASR 结果是**中英混杂**，不是"英上中下"的双语对照 ⇒
                                //   裁档会连英文整行丢掉、行序也会把"含汉字那行"挪走 ⇒ 这三档对它**没有意义**。
                                //   🔴 阿亮第二轮纠正（v152）：我原来把三档**灰着列出来**，但内封字幕那边是
                                //   "用不了就不列子项" ⇒ **两处口径又不统一了**。他原话：
                                //     "既然把内封字幕那个没的时候下边那两项都不展示了，
                                //      那下方字幕不适用的时候，那下边那几行也不用展示了，不然这逻辑又不统一了。"
                                //   ⇒ 统一口径 = **用不了 ⇒ 只留一行标题（灰），子项一律不列**。
                                //     标题留着是为了让用户知道"这里有这么个东西、现在用不了"，
                                //     子项不列是因为**列出来也没用、还占菜单高度**（v151 顶破过一次）。
                                val dimDownSub = subtitlesFromAsr
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            // ⚠️ 文案**必须短**：菜单有固定宽度，「下方字幕（识别字幕不适用）」
                                            //   实测会**折成两行**、挤掉下面一行的位置（v151 真机截图发现的）。
                                            if (dimDownSub) "下方字幕（不适用）" else "下方字幕",
                                            color = if (dimDownSub) {
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                        )
                                    },
                                    enabled = false,
                                    onClick = {},
                                )
                                if (!dimDownSub) {
                                    listOf(
                                        SubtitleMode.EN_ONLY to "只看英文",
                                        SubtitleMode.CN_ONLY to "只看中文",
                                        SubtitleMode.BILINGUAL to "英中双语",
                                    ).forEach { (m, label) ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(if (subtitleMode == m) "✓ $label" else "     $label")
                                            },
                                            onClick = { subMenuOpen = false; setSubtitleMode(context, m) },
                                        )
                                    }
                                }
                                // 【v120】视频**自带**的字幕轨（内封软字幕）。有几条列几条，当前在用的打勾。
                                // ⚠️ 多条轨不能同时叠加（会重叠打架）⇒ 这里是"切换"入口，一次只用一条。
                                // 自动挑的那条 = 简英双语 > 中上英下 > 简体中字 > 繁中 > 纯英文（见 EmbeddedSubtitle.score）。
                                // 🔴🔴【1011 阿亮】**没内封轨时不再"整块隐藏"，改成标题灰着列出来**。
                                //   阿亮原话："要要不显示就都不显示，要灰就都一起灰，为什么又有不显示又有变灰的呢？
                                //   …**要统一逻辑啊**" ⇒ 最后裁定："那就选全都选变灰，
                                //   能用的功能就不变灰，不能用的功能就变灰。"
                                //   ⚠️【v151 真机返工】**不要造"简英双字/繁英双字"占位项** ——
                                //     第一版加了这两条灰占位，菜单总高顶破 `DropdownMenu` 的可视高度，
                                //     最底下那一项**被裁掉了一半**（真机截图实测）。
                                //     而且那两条本来就是"某个片源里恰好有的轨名"，凭空列出来反而误导。
                                //   🔴【1012 改口径】**没内封轨 ⇒ 整项不显示**（和上面的「原生字幕」保持一致）。
                                //     v151~v153 是"留着灰标题、不列子项"；现在连标题也不留 ——
                                //     理由见上面「原生字幕」那段（没有内封轨是常态，别让菜单永远挂着灰行）。
                                //   ⇒ 有内封轨时行为**完全不变**（标题 + 轨道列表，当前在用的打勾）。
                                if (hasEmbedded) {
                                    DropdownMenuItem(
                                        text = { Text("内封字幕（${embeddedTracks.size} 条）") },
                                        enabled = false,
                                        onClick = {},
                                    )
                                    val activeLabel = srtName
                                        .takeIf { it.startsWith(EmbeddedSubtitle.TAG_PREFIX) }
                                        ?.removePrefix(EmbeddedSubtitle.TAG_PREFIX)
                                    embeddedTracks.forEach { t ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(if (t.label == activeLabel) "✓ ${t.label}" else "     ${t.label}")
                                            },
                                            onClick = {
                                                subMenuOpen = false
                                                applyEmbeddedTrack(t.index)
                                            },
                                        )
                                    }
                                }
                                // 🔴【1011 阿亮】「导出字幕」**已删除**（他："导出字幕留着没有什么用呢，没什么用了"）。
                                //   连同菜单里的「选字幕…」一起删掉 —— 这两项阿亮都判定为没用了。
                                //   ⚠️ **ASR 自动落地不受影响**：走的是 `SubtitleExport.writeAsrBeside`（MainActivity 里），
                                //     跟这个菜单项无关。别因为删了菜单项就以为"字幕不再落地了"。
                                //   ⚠️ 别再"顺手加回来" —— 要加先问阿亮。
                                // 🔴【1011 阿亮】「选字幕…」**已删除**（他："我感觉基本上不用选。先删了吧…
                                //   我感觉这个没用"）。原来留着是当"抓不出来/自己生成的不准"的兜底，
                                //   但实测字幕**要么是抓的、要么是 ASR 生成的**，都在视频旁边同一个文件夹，
                                //   自动匹配（`SubtitleParser.pickBest`）稳定兜得住 ⇒ 手动挑一份的场景不存在。
                                //   ⚠️ 别再"顺手加回来" —— 要加先问阿亮。
                            }
                        }
                        IconButton(onClick = { videoFullscreen = true; controlsVisible = false }) {
                            Icon(Icons.Filled.Fullscreen, contentDescription = "全屏", tint = Color.White)
                        }
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
                            // 🔴【1011 阿亮】同上：全屏这条也合成**一整条底板**（背景只画一次），颜色统一、略浅；
                            //   padding 放 background 之后 ⇒ 底板上边缘同样往上延伸 4dp（白球不贴边）。
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                .background(CTRL_BAND_COLOR)
                                .padding(top = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth()
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
                                    .padding(vertical = 2.dp),
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
                        // 🔴【1011 阿亮】进度条和按钮**合成一条底板**：原来两块各自画背景（0.45 / 0.5）⇒ 有色差、看着是两条。
                        //   现在背景只在这层 Column 上画一次，里面两个子项都不再画 ⇒ 一整条、颜色统一。
                        //   整体比原来**略浅一点**（0.45/0.5 → 0.4）。
                        // 🔴 padding 放在 background **之后** ⇒ 底板会把这段空白一起盖进去 = **底板上边缘往上延伸 4dp**，
                        //   进度条白球不再贴着底板上边（原来只剩 2dp，现在 6dp）。进度条和按钮位置都不动。
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .background(CTRL_BAND_COLOR)
                            .padding(top = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth()
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
                                // 🔴【1010 阿亮】按钮居中在「进度条轨道线」和「视频底边」的正中间。
                                //   黑条 38dp + 轨道线离黑条顶 8dp ⇒ 轨道线到视频底 46dp，中点 23dp；
                                //   图标中心本来在离底 19dp ⇒ 用 offset 把图标**整体往上挪 4dp**就正居中。
                                //   offset 只是"把图标挪个位置"，黑条高度、进度条位置一律不动。
                                modifier = Modifier.fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .offset(y = (-4).dp),
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
                // 【1008 扩】B站抓取状态也上同一颗胶囊 —— 阿亮反馈"抓 B站 时提示还在下面功能键区"。
                // 优先级：识别中 > 识别结果 > B站错误(红) > B站进度(黑)。
                // 抓取完 Done 会清 biliStatus 并自动进识别（recognizing 接管），两者先后衔接不并存。
                val asrOverlayText = when {
                    recognizing -> when {
                        asrStage != null -> asrStage!!
                        asrProgress < 100 -> "抽音轨中… $asrProgress%"
                        else -> "识别中…"
                    }
                    asrOverlayVisible && asrMsg.isNotEmpty() -> asrMsg
                    biliError.isNotEmpty() -> biliError
                    biliStatus.isNotEmpty() -> biliStatus
                    else -> null
                }
                if (asrOverlayText != null) {
                    // 染红规则：识别中的段进度不染；「识别完成」不染；B站进度文案（无错时）不染；其余红
                    val overlayBusy = recognizing || (biliError.isEmpty() && asrOverlayText == biliStatus)
                    val asrOverlayErr = when {
                        recognizing -> false
                        overlayBusy -> false
                        else -> !asrOverlayText.startsWith("识别完成")
                    }
                    // 【1008】阿亮："黑底白字太挡了 ⇒ 透明化、尽量浅、能看得到就行"。
                    // 改成**浅底半透明 + 深色字**：底 70% 白（背后的画面能透出来），字用近黑。
                    // 错误态同理：浅红底 + 红字，警示性留着但不糊画面。
                    val overlayFg = if (asrOverlayErr) Color(0xFFB3261E) else Color(0xDE000000)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (asrOverlayErr) Color(0xCCFFEBEE) else Color(0xB3FFFFFF),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 32.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            if (overlayBusy) {
                                // 进度型：不定长转圈 = "还在转、还没完"（识别 100% 僵住的老教训，同款处理）
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = overlayFg,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                asrOverlayText,
                                color = overlayFg,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                // 【v124】手势提示：屏幕中央「图标 + 进度条 + 百分比」（样式照小米播放器）。
                // 【v157】横向拖动（gestureHint == 3）走另一套：**只出图标 + 时间文案**，
                //   不画进度条 —— 文案里已经有「目标时间 / 总时长」，信息量比一根条子大。
                // 放在 Box 的**最后** = 压在最上层，别被画面/浮层/字幕条盖住。
                if (gestureHint != 0) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.Black.copy(alpha = 0.72f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (gestureHint == 3) {
                                    // 【v157】快进/快退：**胶囊两侧各一个箭头**，中间报数。
                                    // 图标复用底部控制条那两颗（FastForward / FastRewind）⇒ 风格统一；
                                    // 只调小一号 + 压暗一档，让它比正主"弱"一点、不抢中间那行字。
                                    val seekIcon =
                                        if (gestureSeekBack) Icons.Filled.FastRewind else Icons.Filled.FastForward
                                    Icon(
                                        imageVector = seekIcon,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.72f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        gestureSeekText,
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Icon(
                                        imageVector = seekIcon,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.72f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                } else {
                                    // 【v157】亮度和音量也换成**白色矢量图标**（原来是 🔆 / 🔊 彩色 emoji）。
                                    // 🔴 阿亮要求"跟底部那排功能按键一个色" ⇒ 这里是 Color.White，
                                    //    别再往这两个图标上加颜色。
                                    Icon(
                                        imageVector = if (gestureHint == 1) Icons.Filled.LightMode
                                        else Icons.Filled.VolumeUp,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    // 进度条：底槽 + 白色填充（fillMaxWidth 要 0~1，滑到底不能越界）
                                    Box(
                                        modifier = Modifier
                                            .width(110.dp)
                                            .height(6.dp)
                                            .background(Color.White.copy(alpha = 0.28f), RoundedCornerShape(3.dp))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(gestureHintValue.coerceIn(0f, 1f))
                                                .background(Color.White, RoundedCornerShape(3.dp))
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        "${(gestureHintValue * 100).roundToInt()}%",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
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
            // 【v161 阿亮】"视频页看不出这是哪一集" ⇒ 这一行放当前集名。
            // 位置就是原来那个 `Spacer(12.dp)` 的空白 —— **不新占空间**，只是把它用起来。
            // 🔴 别改去顶栏那 16dp、也别压画面（画面高是 `aspectRatio(16f/9f)` 算出来的）——
            //    三处尺寸的实测见 `2026-10-14.md`。
            VideoTitleBar(videoName)
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

                            // 【1008 修】B站抓取状态（biliStatus/biliError）已挪到视频画面正中间的
                            // 胶囊（与识别进度同一颗，见上方视频 Box）——阿亮："抓 B站 的提示
                            // 别在功能键下面，要在视频上面"。控制排不再被这两行撑高。
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
                                    // 🔴【1010】双语字幕**不做整句翻译** —— 它本来中英两行都有了，翻它没意义
                                    //   （旧行为：整条丢给翻译接口，中文被翻成英文 ⇒ 中英夹杂 + 白费一次调用）。
                                    //   双语长按 = 直接进选词；只有**纯英文**字幕才走整句翻译。
                                    // 🔴【1010 阿亮定】ASR 生成的 ⇒ **当普通字幕**：长按照旧翻译、不搞"双语不翻译"
                                    //   那一套（它只是中英混杂，不是"中英对照的双语字幕"）。
                                    val bilingual = !subtitlesFromAsr && isBilingual(sentence.text)
                                    // 当前档位下这句显示成什么样（跟画面浮层同一套规则）
                                    val shown = displayTextOf(sentence)
                                    // 🔴【1010 阿亮定】长按 = **把藏起来的那行亮出来**：
                                    //   只看英文/只看中文档下，长按展开要显示**完整双语**（对面那行补回来）；
                                    //   双语档本来就全都有，长按就是进选词。
                                    //   ⇒ 展开态统一用原文本 `sentence.text`，收起态才用 `shown`。
                                    val expandedText = sentence.text
                                    // 点词/加生词本只认英文行（阿亮：中文一律不管）
                                    // 【1010】ASR 生成的**原样给**（混杂字幕里那几个中文语气词不该把它整句改样）
                                    val wordSource = if (subtitlesFromAsr) sentence.text else englishOnly(sentence.text)
                                    var translation by remember(sentence.text, bilingual) { mutableStateOf(Translator.cached(sentence.text)) }
                                    var translating by remember(sentence.text, bilingual) { mutableStateOf(false) }
                                    var failed by remember(sentence.text, bilingual) { mutableStateOf(false) }
                                    var selectedWord by remember { mutableStateOf<String?>(null) }
                                    LaunchedEffect(expanded, sentence.text, bilingual) {
                                        // 双语 ⇒ 不调翻译（下面展开区也不显示翻译那一块）
                                        if (expanded && !bilingual && translation == null && !translating) {
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
                                            if (expanded && expandedText.isNotEmpty()) {
                                                // 【1010】展开 = 亮出**完整**那句（只看英文/只看中文档下把对面那行补回来）；
                                                //   **只认英文词**（中文行原样显示、不可点）。
                                                ClickableWordsRow(text = expandedText, onWordClick = { selectedWord = it })
                                            } else {
                                                Text(
                                                    shown,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                            }
                                            if (expanded && expandedText.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                // 🔴【1010】双语字幕：**不显示翻译那一块**（本来就有中文），
                                                //   展开就是纯选词。纯英文字幕才照旧走翻译。
                                                if (!bilingual) {
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
                                                }
                                                // 整句加生词本：双语只加**英文**部分（阿亮：只加英文不加中文）
                                                AddSentenceButton(context = context, text = wordSource, source = videoName)
                                                // 【1010 阿亮】原来下面还有一行「再长按可收起」——**删掉**（与阅读模式同步）：
                                                //   纯提醒、零信息量，却单独占一整行、灰字发闷。行为没动。
                                            }
                                        }
                                    }
                                    if (selectedWord != null) {
                                        WordCardDialog(word = selectedWord!!, source = videoName, onDismiss = { selectedWord = null })
                                    }
                                }
                            }
                        } else if (videoUri != null) {
                            // 【1008】没字幕时的空态。原来这块是**一片空白** —— 用户分不清"这视频本来就没字幕"
                            // 和"软件坏了"。给一句能看懂的引导。
                            // 🔴【1011 阿亮】原文案是「没有字幕，点这里选一份」+ 拉起选文件。
                            //   阿亮把「选字幕…」菜单项删了（"我感觉这个没用"）⇒ 这里**不能再指向它**，
                            //   否则用户按提示点进来只会更懵。改成指向**顶栏「生成字幕」**这条真实可用的路
                            //   （跑完还会自动把字幕落到视频旁边）。
                            //   ⚠️ 纯提示文字、**不带点击** —— 不新增入口，避免和顶栏按钮职责重叠。
                            // 🔴【1012 自审 B4】只指「生成字幕」还是不够：软件本来有三条路能拿到字幕
                            //   ① 同文件夹里放一个同名 .srt（**最省事，不用花时间跑 ASR**）
                            //   ② 片子自己带的内封轨（打开时自动扒，用户不用做任何事）③ 在线识别（「生成字幕」）
                            //   ① 从来没在界面上说过一个字 ⇒ 用户以为"只有跑识别这一条路"，白等几分钟。
                            //   现在补上 ①，口径**对齐阅读模式那句**（ReadingPage：可先在视频模式点「生成字幕」或导入 .srt）。
                            //   ⚠️ 得带上「再重新打开」—— 找字幕只发生在**打开视频那一刻**（findBestSubtitle），
                            //      当前页面不会自己发现新扔进来的文件；不说这句，用户放完没反应会以为没用。
                            //   ⚠️ 内封轨（②）不在这里提：那是软件自己干的活，不需要用户做任何操作，说了只是噪音。
                            Column(
                                modifier = Modifier.fillMaxWidth().weight(1f)
                                    .padding(top = 10.dp, start = 16.dp, end = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    "这个视频还没有字幕",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    // ⚠️ 这里的手动换行是**故意的、也是必需的**：让它自然折行会断在词中间，
                                    //   实测第一版写「或者找一个 .srt 放到视频旁边，再重新打开」时，
                                    //   末尾「开」字被挤到第四行、孤零零一个字（真机截图看到的）。
                                    //   ⇒ 每行都压在约 15 个全角字以内，宽度绝对够，不会再折。
                                    "点上方「生成字幕」让软件听一遍\n" +
                                        "或者放个 .srt 到视频旁边再重开",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                    textAlign = TextAlign.Center,
                                )
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
                .fillMaxWidth()
                // 【v123 修·按钮被压扁】这里必须让卡片按**自然高度**测量。
                // 🔴 根因：本层是挂在 16:9 视频框里的（宽 992px 高 558px ⇒ 约 360×203dp，
                //    v120 之前一直如此），而 Surface 原来只给了 `fillMaxWidth()` —— 高度就继承了
                //    Box 的上限 203dp。卡片内容自然高超过它时，Column 会按顺序把**后面的子项**
                //    硬压到 0 高度 ⇒ 实测按钮只剩 47px(17.1dp)、里面文字被裁没、TextButton「知道了」
                //    直接被挤出面板外。`unbounded = true` = 用无限高约束测一次得到真实高度，
                //    再在父框内居中；宁可上下被裁一点点，也绝不把按钮压成一条线。
                .wrapContentHeight(align = Alignment.CenterVertically, unbounded = true),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (errorText != null) {
                    // 错误状态
                    // ⚠️ 本分支的高度预算很紧（视频框只有 203dp），改文案/加控件前先算一遍总高：
                    //    padding 28 + 标题 24 + 6 + 错误文字2行 40 + 10 + 按钮行 40 + 2 + 知道了 36 = 186dp。
                    Text(
                        "⚠ 播放失败",
                        color = Color(0xFFFF6B6B),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        errorText,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        // 限 2 行：视频框只有 203dp 高，长错误（如「播放失败（ERROR_CODE_…）」）
                        // 撑成 3、4 行就会把下面那排按钮再次挤出去。
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    // ⚠️「知道了」**必须**另起一行：实测三个键挤一行时，前两个 Button 吃掉宽度后
                    //    它只剩 33dp ⇒ 被压成「知道 / 了」两行还贴右边框（v123 第一次实机截图）。
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4A4A4E),
                                contentColor = Color.White,
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        ) { Text("重试") }
                        Button(
                            onClick = onPickVideo,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4A4A4E),
                                contentColor = Color.White,
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        ) { Text("换视频") }
                    }
                    Spacer(Modifier.height(2.dp))
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    ) { Text("知道了", color = Color.White.copy(alpha = 0.7f)) }
                } else {
                    // 空状态（videoUri==null）
                    Text(
                        "📂 还没有选视频",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
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
        // 🔴【1010 阿亮】"进度条跟按钮中间空着一块" —— 病根是图标在按钮条里**垂直居中**：
        //   szPlay=52dp 而图标只 28dp ⇒ 图标头顶空 12dp。把触摸区压到贴着图标，
        //   空白就没了。**图标大小、圆点、功能全不动**，改的只是那圈不可见的点击区。
        val szTrack = if (compact) 28.dp else 48.dp
        val szSent = if (compact) 30.dp else 52.dp
        val szPlay = if (compact) 34.dp else 72.dp
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