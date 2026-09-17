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

/** 页面：播放页 / 选集页 / 阅读模式页 / 文件夹页 / 生词本页 */
enum class Page { PLAYER, TRACK, READING, FOLDER, VOCAB, REVIEW, VIDEO }

// ===== 视频模式 UI 状态：提升到文件级(进程内常驻),切页面/旋转不丢,回视频页自动续播 =====
private var videoUri by mutableStateOf<Uri?>(null)
/** 视频的真实文件路径：从文件管理器选视频时才有。有了它就能直接读同目录 .srt（不再靠猜 Uri） */
private var videoPath by mutableStateOf("")
private var videoName by mutableStateOf("")
private var playing by mutableStateOf(false)
// 视频独立的播放状态：之前视频/音频共用 playing，切到音频模式播放会把 playing 置 true，
// 切回视频模式时 LaunchedEffect(videoUri) 误判「正在播放」而自动续播、暂停保不住。
// 现在视频用 videoPlaying，与音频 playing 彻底分离。
private var videoPlaying by mutableStateOf(false)
// 退出软件时所处的页面（PLAYER/VIDEO/READING），冷启/媒体中心返回时据它恢复 page，而非总回音频模式。
// 普通 var（不驱动 UI），仅作为持久化的桥梁：AppRoot 的 page 是组合局部，文件级 save/load 通过它读写。
private var sessionPage: Page = Page.PLAYER
private var currentPos by mutableStateOf(0L)
private var sentences by mutableStateOf<List<Sentence>>(emptyList())
private var srtName by mutableStateOf("")
private var loopSentence by mutableStateOf(false)
private var recognizing by mutableStateOf(false)
private var asrMsg by mutableStateOf("")
private var asrProgress by mutableStateOf(0)
private var videoSpeed by mutableStateOf(1f)
    // 视频播放器声明见文件顶部（进程级，跨 Activity 重建存活）。
/** 【0903 黑屏修复】视频播放/加载错误信息：MediaPlayer onError 时设上，视频窗口显示醒目的错误卡片（替代静默黑屏）。
 *  null = 正常；非 null = 渲染错误卡片（带重试/换视频按钮）。 */
private var videoErrorMessage by mutableStateOf<String?>(null)
// 当前高亮的句子。放在文件级而不是 VideoPage 的 remember：
// 切到别的模式再切回来，VideoPage 会被销毁重建，remember 状态会归零，
// 高亮就会跳回第 0 句（而实际播放位置在第 30 句），看着像「跳句失灵」。
private var videoSentenceIndexGlobal by mutableStateOf(0)
// 视频全屏模式（需求6）：全屏时画面独占、仅全屏内叠字幕
private var videoFullscreen by mutableStateOf(false)
private var videoSelectJump by mutableStateOf(false)   // 视频选集：进文件夹页时直接钻到当前视频所在文件夹
private var readingFromVideo by mutableStateOf(false)   // 阅读模式来源：true=从视频模式进入→读视频字幕；false=读音频字幕（B 方案上下文感知）
// 阅读(视频源)复用同一个 VideoView 播放器，不另建播放器 → 零延迟/零双声/无孤儿音频。
// videoSentenceStopAt：点视频字幕进阅读时记一句尾，进度轮询到句尾自动停（对齐音频阅读「读一句停一句」）。
private var videoSentenceStopAt by mutableStateOf<Long?>(null)

// 【0915 上一句复读】视频「上一句」按音频模式「重播次数」设置，把该句复读 N 遍后再继续。
// 规则与音频 PlayerService.playSentence(repeatTimes) 完全一致：共播 N 遍 = 1 次首播 + (N-1) 次回句首重播。
// - videoRepeatStopAt：本句的句尾时间戳（= 复读目标句的 endMs）；null = 不在复读中。
// - videoRepeatStartMs：本句的句首时间戳（回跳落点，直接记下来，不在轮询里反查句子列表）。
// - videoRepeatLeft：还欠几次「回句首重播」，递减到 0 即结束。
// 三者都是文件级（不是 VideoPage 的 remember）：切模式/Activity 重建后复读状态不能丢，否则会「读到一半被打断」。
private var videoRepeatStopAt by mutableStateOf<Long?>(null)
private var videoRepeatStartMs = 0L
private var videoRepeatLeft by mutableStateOf(0)
/** 结束/取消视频「上一句复读」：清掉句尾与剩余次数（句尾自然播完、用户手动跳走、换片都走这里）。
 *  同时解除高亮钉子 —— 复读没了，高亮就该交回给轮询按播放位置跟随（对齐音频模式）。 */
private fun clearVideoSentenceRepeat() {
    videoRepeatStopAt = null
    videoRepeatStartMs = 0L
    videoRepeatLeft = 0
    videoSentencePin = -1
}

// 【0915 高亮钉住】复读期间把高亮钉在目标句上，别让轮询按播放位置来回改。
// 对齐音频模式 PlayerService 的 pendingSentenceIndex 语义：
//   点「上一句」→ 高亮钉死在那一句，复读 N 遍期间都不动；
//   N 遍播完 → 解除钉子（置 -1），高亮恢复「跟着播放位置走」。
// 不钉住的后果（阿亮报的问题）：复读每回到句首，轮询过了 seek 保护期就按 currentPos 重算，
//   高亮在「目标句 ↔ 下一句」之间来回跳；且视频画面/字幕判句比出声慢半拍（输出缓冲），
//   看着就是「高亮比音频慢一拍」。钉住后与音频模式表现一致。
// -1 = 没钉（高亮照常由轮询按位置更新）。
private var videoSentencePin by mutableStateOf(-1)

// 视频切后台续播（0904）：onPause 把音轨交给前台服务，onResume 再交还 VideoView
private var videoHandedToBg = false
// 【0910 根治·后台存活】Activity 是否真正失去前台（onPause 置 true，onResume/onNewIntent 置 false）。
// App 内切换页面(视频↔音频/文件夹)不会触发 onPause，故 false 表示「导航离开视频」→ onDispose 释放播放器；
// true 表示「切后台」→ onDispose 不释放，播放器保留、回前台 REUSE 零 seek。
private var appInBackground = false
private var videoBgResumePos = 0L
private var videoBgWasPlaying = false
// 【0904 真机修复】权威续播位：回前台优先用「音频在后台实际推进到的新位置」，而不是 onPause 交接瞬间
// 的旧位置——否则会跳回几十秒前、把后台已播过的几句重读一遍（"复读最后一句"）。
// 真机后台 MediaPlayer 确实在往前播，但后台 peek 的位置在模拟器上会回退（不可信），故用
// 「交接位 + 后台流逝时间 × 倍速」自己推算前向位置为主，peek 仅作正向交叉校验。
// 由 onResume 写入、onPrepared 消费后清零；>0 表示「本次是回前台续播」而非冷启/换片。
private var videoResumePos = 0L
private var videoBgHandoffTime = 0L   // onPause 交接时刻（毫秒），用于推算后台前向位置
// 【0905 续播不重播】切后台位置持续落盘器：前台 200ms 轮询在 onPause 后冻结，但单播放器后台仍在发声，
// 若不持续刷 SP，Activity 被系统销毁重建时续播位会滞后、SEEK_NEXT_SYNC 往前跳一大段。
// onPause 启动、onResume/onDestroy 取消；每 1.5s 读一次最新位置落盘。
private val bgPosHandler = android.os.Handler(android.os.Looper.getMainLooper())
private var bgPosRunnable: Runnable? = null

// 全屏内字幕开关（需求6）
private var videoSubOn by mutableStateOf(true)
// 当前视频所在文件夹的同级视频列表（需求2：上一曲/下一曲在同级视频间切换）
private var videoFolderVideos by mutableStateOf<List<VideoItem>>(emptyList())

// ===== 抓 B站 视频：输入框 + 导入状态（导入走前台服务，非阻塞、切后台不被掐） =====
private var biliUrl by mutableStateOf("")
private var biliStatus by mutableStateOf("")
private var biliError by mutableStateOf("")
// page 是 setContent 内的 composable 局部状态，文件级广播接收器无法直接改它；
// 这里用回调把「切到视频模式 / 复读机」转交给 setContent 作用域执行。
private var biliNavigate: ((Page) -> Unit)? = null

// 抓 B站 完成/进度回调：改用进程内事件总线 BiliImportBus（见下方 setContent 内的 LaunchedEffect 收集），
// 不再用跨组件广播——Android 14 上 RECEIVER_NOT_EXPORTED 的隐式广播投不到，会导致"提交后没反应"。
// 服务跑完通过 BiliImportBus.post(BiliImportEvent.Done/Progress) 回传，这里收集后更新状态并自动打开播放。

// 触发抓 B站：前台服务跑（切后台不被系统掐），translate=false 走原长按字幕翻译逻辑
private fun startBiliImport(context: Context, url: String) {
    val intent = Intent(context, BiliImportService::class.java)
        .putExtra(BiliImportService.EXTRA_URL, url)
        .putExtra(BiliImportService.EXTRA_TRANSLATE, false)
    context.startForegroundService(intent)
}

// 【单播放器·彻底修】视频渲染用 TextureView（持有常驻 SurfaceTexture）+ 自建 MediaPlayer。
// 【方案B 0905 · 回前台「重播当句」根因修复】原用 SurfaceView：窗口不可见时 Surface 被销毁
// (surfaceDestroyed)，回前台 surfaceCreated 新建 Surface 重绑 → MediaPlayer 从「最近关键帧」重新
// 解码显示 → 开头/结尾 GOP 长时画面闪回句首/片尾（看着像重播第一/二/最后句），而音频一直连续、
// 位置始终正确（故此前"位置连续=无缝"是误判）。
// 改 TextureView 后：onSurfaceTextureDestroyed 返回 false 保留 SurfaceTexture，Surface 不随前后台
// 销毁，MediaPlayer 的 Surface 一直有效、不需要重绑 → 不重解关键帧 → 画面真正无缝。
// ⚠️ 黑屏铁律：只能用 TextureView 在 onSurfaceTextureAvailable 里【自带的】st 建 Surface；
// 绝不可 new SurfaceTexture(0) 兜底——那才是当年「TextureView 黑屏」的真正元凶，与 TextureView 无关。
private val videoSurfaceRef = arrayOfNulls<Surface?>(1)  // 当前视频渲染 Surface（由 TextureView 自有 SurfaceTexture 建，常驻）
// 记录 MediaPlayer 当前绑定的 Surface，用于幂等绑定：重复 setSurface 会触发解码器重新出帧（重解关键帧）。
private var videoMpBoundSurface: Surface? = null
// 记录当前 Surface 所基于的 SurfaceTexture：回前台若还是同一个纹理则完全不重建、不重绑。
private var videoSurfaceTexRef: SurfaceTexture? = null

/** 幂等绑定画面：仅当目标 Surface 与「当前已绑定的」不是同一个对象时才真正 setSurface。
 *  重复绑定同一个 Surface 会让解码器重新出帧，是回前台重解关键帧的帮凶，故必须幂等。 */
private fun bindVideoSurface(sf: Surface?, whence: String) {
    val mp = videoMpRef[0] ?: return   // 播放器还没建好（CREATE 分支会创建后再绑），此处不做事
    if (videoMpBoundSurface === sf) {
        Log.d("XJ_VID", "bindVideoSurface SKIP(same surface) whence=$whence")
        return
    }
    runCatching { mp.setSurface(sf) }
    videoMpBoundSurface = sf
    Log.d("XJ_VID", "bindVideoSurface whence=$whence sf=${if (sf == null) "null" else "set"} cur=${runCatching { mp.currentPosition }.getOrDefault(-1)}")
}
// 视频总时长 / 是否就绪 / 最近一次 seek 时间戳：提升为文件级，媒体中心(系统通知)与控制逻辑共用
private var videoDuration by mutableStateOf(0L)
private var videoPrepared by mutableStateOf(false)
private var videoLastSeekAt = 0L

/** 视频「外部控制器」：塞给 PlayerService，让系统媒体中心/音频焦点统一控制视频。
 *  与 UI 控制条共用同一套文件级视频状态与播放函数。 */
private val videoMediaController = object : ExternalMediaController {
    override fun play() { if (!videoPlaying) videoTogglePlay() }
    override fun pause() { if (videoPlaying) videoTogglePlay() }
    override fun isPlaying(): Boolean = videoPlaying
    override fun position(): Long = currentPos
    override fun duration(): Long = videoDuration
    override fun seekTo(ms: Long) { videoSeek(ms); currentPos = ms }
    override fun skipNext() = videoJumpSentence(1)
    override fun skipPrev() = videoJumpSentence(-1)
    override fun title(): String = videoName.ifBlank { "视频播放" }
}


// 视频 ASR 协程作用域（文件级，顶栏与视频页共用；进程内常驻，避免局部 scope 失效后无法调用）
private val videoAsrScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

// ===== 视频现场持久化：URI / 进度 / 字幕存 SharedPreferences，进程被杀后下次启动可恢复 =====
private const val PREFS_VIDEO = "xuanji_video"
private const val PREFS_CRASH = "xuanji_crash"
private const val CRASH_WINDOW_MS = 3000L      // 兜底计数窗口
private const val CRASH_MAX_IN_WINDOW = 5      // 窗口内崩这么多次就说明救不回来，交还系统
// 进程级幂等：Activity 重建（旋转/配置变更）会重走 onCreate，兜底只能装一次，
// 否则每重建一次就多嵌套一层 Looper，装多了会出问题
private var crashGuardInstalled = false
private fun saveVideoPrefs(context: Context) {
    context.getSharedPreferences(PREFS_VIDEO, Context.MODE_PRIVATE).edit().apply {
        putString("uri", videoUri?.toString() ?: "")
        putString("path", videoPath)
        putString("name", videoName)
        putLong("pos", currentPos)
        putString("srt", srtName)
        putString("sentences", encodeSentences(sentences))
        // 退出状态：记住所处页面与播放状态，冷启/媒体中心返回时据此恢复（而非总回音频模式）。
        // sessionPage 由 AppRoot 的 LaunchedEffect(page) 同步；readingFromVideo/videoPlaying 为文件级。
        putString("mode", sessionPage.name)
        putBoolean("reading_from_video", readingFromVideo)
        putBoolean("playing", videoPlaying)
        // 视频后台交接态：真机划后台常重建 Activity（普通 var 会清零），必须落盘，重建后由 loadVideoPrefs 恢复
        putBoolean("handed_to_bg", videoHandedToBg)
        putLong("bg_resume_pos", videoBgResumePos)
        putBoolean("bg_was_playing", videoBgWasPlaying)
        putLong("resume_pos", videoResumePos)
        putLong("bg_handoff_time", videoBgHandoffTime)
        apply()
    }
}
private fun loadVideoPrefs(context: Context) {
    val sp = context.getSharedPreferences(PREFS_VIDEO, Context.MODE_PRIVATE)
    // 早读「所处页面」：不依赖视频 Uri，供启动恢复 page（即使在 PLAYER/READING 音频源，Uri 可能为空也要能恢复）。
    sessionPage = runCatching { Page.valueOf(sp.getString("mode", "PLAYER") ?: "PLAYER") }.getOrDefault(Page.PLAYER)
    // 【已移除・步骤2】原先这里按持久化的 sessionPage/reading_from_video 调 PlaybackSession.restore(VIDEO)，
    // 与「按实际 page 推导」的 setSessionSource 规则打架：sp 里 reading_from_video=true，但
    // 因视频 Uri 失效在下方提前 return，实际变量停在 false、页面是空白音频阅读 —— 于是 restore 先写
    // VIDEO、60ms 后被纠正回 AUDIO（实机日志实测）。真值只能有一个裁决者：以实际页面状态为准。
    // 视频后台交接态：放在最前恢复，避免下方提前 return 漏掉（真机重建 Activity 后靠它接回后台音轨）
    videoHandedToBg = sp.getBoolean("handed_to_bg", false)
    videoBgResumePos = sp.getLong("bg_resume_pos", 0L)
    videoBgWasPlaying = sp.getBoolean("bg_was_playing", false)
    videoResumePos = sp.getLong("resume_pos", 0L)
    videoBgHandoffTime = sp.getLong("bg_handoff_time", 0L)
    val uriStr = sp.getString("uri", "") ?: ""
    if (uriStr.isBlank()) return
    val uri = Uri.parse(uriStr)
    val path = sp.getString("path", "") ?: ""
    // 两种 Uri 要分开判断，否则从文件管理器选的视频下次启动会恢复不了：
    //   媒体库 Uri（content://media/external/video/media/<id>）：有 READ_MEDIA_VIDEO 就能读，
    //     不需要长期授权，只要文件还在磁盘上就行。
    //   老版本存的 SAF / PhotoPicker Uri：必须校验长期授权是否还握在手里。
    if (uriStr.startsWith("content://media/")) {
        if (path.isNotBlank() && !java.io.File(path).exists()) return   // 文件已被删/移走，别恢复
    } else {
        val persisted = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        if (!persisted) return
    }
    videoUri = uri
    videoPath = path
    videoName = sp.getString("name", "") ?: ""
    currentPos = sp.getLong("pos", 0L)
    srtName = sp.getString("srt", "") ?: ""
    sentences = decodeSentences(sp.getString("sentences", "") ?: "")
    // 【0915 修】冷启恢复同级视频列表：上一曲/下一曲靠 videoFolderVideos 定位当前视频的序号
    // （curFolderIdx = indexOfFirst { it.path == videoPath }），不恢复这个列表 → idx = -1 →
    // hasPrev/hasNext 全 false → 两个按钮全灰，"只能重新进视频选集再选一遍"才活。
    // 这里按当前视频所在目录，从全库扫描结果里过滤出同级视频填回去（无路径的 SAF Uri 退化为全库）。
    videoFolderVideos = runCatching {
        val all = VideoScanner.scanAll(context)
        val dir = path.substringBeforeLast('/', "")
        val sibs = if (dir.isNotBlank()) all.filter { it.path.startsWith("$dir/") } else emptyList()
        sibs.ifEmpty { all }
    }.getOrDefault(emptyList())
    // 视频现场恢复成功后，恢复阅读来源与播放状态（供启动续播/暂停）：
    // readingFromVideo 决定阅读页显示视频字幕；playing 决定 onPrepared 是 start 还是保持暂停。
    readingFromVideo = sp.getBoolean("reading_from_video", false)
    videoPlaying = sp.getBoolean("playing", false)
}
/** 视频后台交接完成：清掉 SP 里的交接态，避免下次冷启误以为还在后台交接中（导致回到开头重读）。 */
private fun clearVideoHandoffPrefs(context: Context) {
    context.getSharedPreferences(PREFS_VIDEO, Context.MODE_PRIVATE).edit().apply {
        putBoolean("handed_to_bg", false)
        putLong("bg_resume_pos", 0L)
        putBoolean("bg_was_playing", false)
        putLong("resume_pos", 0L)
        putLong("bg_handoff_time", 0L)
        apply()
    }
}

fun encodeSentences(list: List<Sentence>): String =
    list.joinToString("\n") { "${it.startMs}\t${it.endMs}\t${it.text.replace("\n", " ")}" }
fun decodeSentences(s: String): List<Sentence> =
    if (s.isBlank()) emptyList() else s.lines().mapNotNull { line ->
        val p = line.split("\t")
        if (p.size == 3) runCatching { Sentence(p[0].toLong(), p[1].toLong(), p[2]) }.getOrNull() else null
    }


/**
 * 从文件管理器里选中一个视频（媒体库扫描出来的，有真实路径）。
 *
 * 跟老的 PhotoPicker 比，好处有两个：
 *   1. Uri 是 `content://media/external/video/media/<id>`，有 READ_MEDIA_VIDEO 权限就能长期读，
 *      不用 takePersistableUriPermission，下次启动不会「权限没了打不开」。
 *   2. 有真实路径，同目录的 .srt 直接列目录就能找到 —— 之前靠 DocumentsContract 猜，
 *      对 PhotoPicker 的 Uri 根本不适用，所以同名字幕一直没生效。
 */
private fun openVideoFromLibrary(context: Context, item: VideoItem, siblings: List<VideoItem> = emptyList()) {
    // 打开/切换视频：先停掉后台音频服务的旧音频（冷启动自动续播的幽灵音），避免与视频双声
    PlayerService.instance?.stopAudioForVideo()
    videoUri = VideoScanner.uriFor(item.id)
    videoPath = item.path
    videoName = fileNameWithoutExt(item.path)
    currentPos = 0L
    videoSentenceIndexGlobal = 0
    // 【续播修复】打开/切换视频必须清掉「后台交接」残留态：否则 videoResumePos/videoHandedToBg
    // 带着上次后台的位置（如 33246）泄漏进来，CREATE 续播会跳到旧位置而非从 0 开始。
    // openVideoFileByPath 已这么做，这里媒体库路径要一致。
    videoHandedToBg = false
    videoResumePos = 0L
    videoBgResumePos = 0L
    videoBgWasPlaying = false
    sentences = emptyList()
    srtName = ""
    asrMsg = ""
    videoFolderVideos = siblings
    // 打开/切换视频默认自动播放（暂停状态由返回时的续播逻辑尊重，不会在这里被误触发）
    videoPlaying = true
    PlaybackSession.onVideoStarted()   // Phase3-步骤1：记「当前播放源=视频」（只记录，不参与判定）
    // 同目录字幕：优先 <基名>.srt，没有就退而取同目录第一个 .srt
    SrtParser.findBestSrtFile(item.path)?.let { f ->
        runCatching {
            val parsed = SrtParser.parse(f.readText())
            if (parsed.isNotEmpty()) {
                sentences = parsed
                srtName = f.name
                // 同目录 .srt 也算「播过的字幕」→ 归档字幕库（视频来源）
                SubtitleLibrary.saveVideo(context, videoName, parsed, item.path)
            }
        }
    }
    // 无同目录 .srt 时，回退读取之前 ASR 生成的字幕（按视频名从字幕库取），
    // 避免「这次识别完有字幕，下次再播又没了」。
    if (sentences.isEmpty()) {
        SubtitleLibrary.loadBySource(context, SubtitleLibrary.SOURCE_VIDEO)
            .firstOrNull { it.title == videoName && it.sentences.isNotEmpty() }
            ?.let {
                sentences = it.sentences
                srtName = "生成字幕"
            }
    }
    saveVideoPrefs(context)
}

/** 打开本地文件视频（B站导入等：文件已落 Download/XuanjiBili，无 MediaStore id，直接用 file Uri 打开）
 *  与 openVideoFromLibrary 同款：设置 URI/路径 + 自动载入同目录 .srt。 */
private fun openVideoFileByPath(context: Context, file: File, siblings: List<VideoItem> = emptyList()) {
    videoUri = Uri.fromFile(file)
    videoPath = file.path
    videoName = fileNameWithoutExt(file.path)
    currentPos = 0L
    videoHandedToBg = false
    videoResumePos = 0
    videoSentenceIndexGlobal = 0
    sentences = emptyList()
    srtName = ""
    asrMsg = ""
    videoFolderVideos = siblings
    videoPlaying = true
    PlaybackSession.onVideoStarted()   // Phase3-步骤1：本地文件视频（B站导入）同样记账
    SrtParser.findBestSrtFile(file.path)?.let { f ->
        runCatching {
            val parsed = SrtParser.parse(f.readText())
            if (parsed.isNotEmpty()) {
                sentences = parsed
                srtName = f.name
                SubtitleLibrary.saveVideo(context, videoName, parsed, file.path)
            }
        }
    }
    if (sentences.isEmpty()) {
        SubtitleLibrary.loadBySource(context, SubtitleLibrary.SOURCE_VIDEO)
            .firstOrNull { it.title == videoName && it.sentences.isNotEmpty() }
            ?.let {
                sentences = it.sentences
                srtName = "生成字幕"
            }
    }
    saveVideoPrefs(context)
}

// ===== 视频「生成字幕」：抽音轨 → 云端 ASR → 填字幕行 =====
// 短视频（≤11 分钟）整段识别，快且准；长视频自动分片：逐段抽取+识别，时间戳加段偏移后拼接。
// 全程 catch(Throwable)：OOM 等是 Error 不是 Exception，只 catch Exception 会漏掉导致闪退。
private const val WHOLE_LIMIT_MS = 11 * 60 * 1000L   // ≤11 分钟走整段（≈21MB，未超 24MB 上传上限）
private const val SEGMENT_MS = 10 * 60 * 1000L       // 分片每段 10 分钟（≈19MB，留足余量）
private const val MIN_SEG_MS = 1000L                 // 末段零头短于 1 秒就别为它单独跑一次网络识别

/** 视频 ASR 总结果：识别句 + 提示 + 音轨波形真实静音点(全局 ms)，后者供长句切分当切点 */
private data class VideoAsrResult(
    val sentences: List<Sentence>,
    val note: String,
    val silencePoints: List<Long>,
)

private fun videoGenerateLyrics(context: Context) {
    if (videoUri == null) { asrMsg = "请先选择视频"; return }
    val key = AsrPrefs.loadKey(context)
    if (key.isBlank()) { asrMsg = "未配置识别 Key：去设置页填写 ASR Key 后才能自动识别"; return }
    val provider = AsrProvider.fromName(AsrPrefs.loadProvider(context))
    recognizing = true
    asrProgress = 0
    asrMsg = ""
    sentences = emptyList()   // 重新生成：先清空旧字幕，逐段识别时再边生成边显示
    videoAsrScope.launch {
        try {
            val durUs = withContext(Dispatchers.IO) { VideoAudioExtractor.probeDurationUs(context, videoUri!!) }
            val durMs = if (durUs > 0) durUs / 1000 else -1L
            // 探测不到时长时按整段走（保持原行为），能探测且超阈值才分片
            val res = if (durMs > WHOLE_LIMIT_MS) recognizeBySegments(context, provider, key, durMs)
            else recognizeWhole(context, provider, key)
            // 统一在最后合并短句：分片段边界产生的半截短句也能跨段合并
            val merged = if (res.sentences.isNotEmpty()) mergeShortAsrSentences(res.sentences) else emptyList()
            // 视频断句：用「音轨波形真实静音点」当切点（而非 ASR segment 间隙）。
            // 慢速教学音频 ASR 段少、段间间隙不够长，靠 segment 间隙切不动；波形静音点是真正停顿，
            // 长句(>6s 或 >40 字)能在最近静音点自然切开。取不到静音点再退回 gap 方案兜底。
            val finalSentences = if (merged.isNotEmpty()) {
                if (res.silencePoints.isNotEmpty())
                    splitLongSentencesByPoints(merged, res.silencePoints, emptyList<LrcLine>())
                else
                    splitLongSentences(merged, res.sentences, emptyList<LrcLine>())
            } else emptyList()
            recognizing = false
            if (finalSentences.isNotEmpty()) {
                sentences = finalSentences
                srtName = "生成字幕"
                // 识别生成的视频字幕 → 归档字幕库（视频来源）
                SubtitleLibrary.saveVideo(context, videoName, finalSentences, videoPath)
                asrMsg = buildString {
                    append("识别完成，共 ${finalSentences.size} 句")
                    if (res.note.isNotBlank()) append("（${res.note}）")
                }
                saveVideoPrefs(context)
            } else {
                asrMsg = res.note.ifBlank { "识别返回为空" }
            }
        } catch (t: Throwable) {
            recognizing = false
            asrMsg = "识别出错：${t.message ?: t.javaClass.simpleName}"
        }
    }
}

/** 进度回调跑在 IO 线程，统一 post 回主线程更新快照状态，避免跨线程写 */
private fun postAsrProgress(p: Int) {
    android.os.Handler(android.os.Looper.getMainLooper()).post { asrProgress = p.coerceIn(0, 100) }
}

/** 整段识别（短视频路径，原逻辑不变） */
private suspend fun recognizeWhole(context: Context, provider: AsrProvider, key: String): VideoAsrResult {
    val wav = java.io.File(context.cacheDir, "xuanji_video_asr.wav")
    val err = withContext(Dispatchers.IO) {
        VideoAudioExtractor.extractToWav(context, videoUri!!, wav.absolutePath) { postAsrProgress(it) }
    }
    if (err != null) { runCatching { wav.delete() }; return VideoAsrResult(emptyList(), err, emptyList()) }
        postAsrProgress(100)
        val outcome = withContext(Dispatchers.IO) { SpeechRecognizer.transcribe(wav.absolutePath, provider, key) }
        val silencePoints = withContext(Dispatchers.IO) { SentenceSplitter.silenceBoundariesMs(wav.absolutePath) }
        runCatching { wav.delete() }
        return when (outcome) {
            is AsrOutcome.Timed -> {
                val list = outcome.segments.map { Sentence(it.startMs, it.endMs, it.text.trim()) }.filter { it.text.isNotEmpty() }
                VideoAsrResult(list, "", silencePoints)
            }
            is AsrOutcome.Plain -> VideoAsrResult(emptyList(), "识别未返回时间戳，建议改用「导入字幕」选 .srt", silencePoints)
            null -> VideoAsrResult(emptyList(), "识别失败/超时（检查网络与 Key）", silencePoints)
        }
}

/** 分片识别（长视频路径）：逐段抽音轨+上传识别，段内相对时间戳 + 段起始偏移 = 全局时间戳，最后拼接 */
private suspend fun recognizeBySegments(
    context: Context, provider: AsrProvider, key: String, durMs: Long,
): VideoAsrResult {
    val n = ((durMs + SEGMENT_MS - 1) / SEGMENT_MS).toInt().coerceAtLeast(1)
    val all = mutableListOf<Sentence>()
    val allSilence = mutableListOf<Long>()   // 各段音轨波形静音点（加段偏移后拼成全局）
    var tried = 0
    var okSeg = 0
    var consecFail = 0   // 连续失败段数：用来判断是「零星失败」还是「系统性失败」
    for (i in 0 until n) {
        val startMs = i * SEGMENT_MS
        val endMs = minOf(startMs + SEGMENT_MS, durMs)
        // 末段可能只剩零头（如 20min+1ms 会切出 1ms 的第 3 段），为它跑一次网络识别纯属浪费
        if (endMs - startMs < MIN_SEG_MS) continue
        tried++
        val span = endMs - startMs
        val wav = java.io.File(context.cacheDir, "xuanji_video_seg$i.wav")
        asrMsg = "第 ${i + 1}/$n 段：抽音轨中…"
        val err = withContext(Dispatchers.IO) {
            VideoAudioExtractor.extractToWav(context, videoUri!!, wav.absolutePath, startMs * 1000, endMs * 1000) { p ->
                postAsrProgress(((i * 100) + p) / n)   // 段内进度折算成全局百分比
            }
        }
        if (err != null) { runCatching { wav.delete() }; continue }
        asrMsg = "第 ${i + 1}/$n 段：识别中…"
        val outcome = withContext(Dispatchers.IO) { SpeechRecognizer.transcribe(wav.absolutePath, provider, key) }
        val segSil = if (outcome is AsrOutcome.Timed) withContext(Dispatchers.IO) { SentenceSplitter.silenceBoundariesMs(wav.absolutePath) } else emptyList()
        runCatching { wav.delete() }
        if (outcome is AsrOutcome.Timed) {
            okSeg++
            consecFail = 0
            val segSentences = outcome.segments
                .map {
                    // 相对时间戳先 clamp 到本段范围内：模型偶尔返回拖尾/幻觉时间，
                    // 不夹一下会越界到下一区，字幕跟着错位甚至跨段重叠
                    val s = it.startMs.coerceIn(0, span)
                    val e = it.endMs.coerceAtLeast(s + 1).coerceAtMost(span)
                    Sentence(s + startMs, e + startMs, it.text.trim())
                }
                .filter { it.text.isNotEmpty() && it.endMs > it.startMs }
            all.addAll(segSentences)
            allSilence.addAll(segSil.map { it + startMs })
            // 流式展示：每识别完一段立即把这一段字幕追加到列表，不用等全部生成完才显示
            if (segSentences.isNotEmpty()) sentences = sentences + segSentences
        } else {
            // 这一段没拿到时间戳。要判断是「零星失败」还是「系统性失败」：
            //   outcome == null        → HTTP 根本没成功（网络断 / Key 错 / 超时），后面每段都会同样失败
            //   outcome is Plain       → 服务通了但这家不返回时间戳，后面每段也都一样
            // 这两种情况下继续跑下去纯属白烧 CPU：每段都要先抽一遍音轨（10 分钟段的抽取要好几十秒），
            // 网络半死不活时还可能各自挂到 240s 读超时。所以满足条件就立刻中止。
            consecFail++
            val hardFail = outcome == null          // 网络/Key/超时，不是内容问题
            if ((hardFail && okSeg == 0) || consecFail >= 2) {
                return VideoAsrResult(all, if (hardFail)
                    "第 ${i + 1}/$n 段起识别失败，已中止（检查网络与 Key）"
                else
                    "识别服务未返回时间戳，已中止（建议改用「导入字幕」导入 .srt）", allSilence)
            }
            // 前面成功过、且这次只失败了一段 → 当成零星失败，跳过继续跑后面的段
        }
        postAsrProgress(((i + 1) * 100) / n)
    }
    return VideoAsrResult(
        all,
        if (okSeg > 0) "$okSeg/$tried 段成功" else "识别失败：检查网络与 Key",
        allSilence,
    )
}

// 视频播放器放在文件级（进程常驻，Activity 重建/回前台不丢），
// 配合下方 loadVideo 的「同 uri 复用」分支：回来只重新接画面，绝不重建、绝不 seek。
private val videoMpRef = arrayOfNulls<MediaPlayer?>(1)
private var videoMpSourceUri: Uri? = null

class MainActivity : ComponentActivity() {

    /** Compose 响应式持有服务引用（绑定完成后触发重组） */
    private val serviceState = mutableStateOf<PlayerService?>(null)
    private var service: PlayerService?
        get() = serviceState.value
        set(value) {
            serviceState.value = value
        }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as PlayerService.LocalBinder).getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    // 开屏动画只在本进程第一次启动 MainActivity 时播；之后 Activity 被系统销毁重建（点媒体卡片回前台常发生）
    // 不再重播，避免"像重新打开一遍"。随进程存活，进程被杀才重置。
    private companion object {
        var appSplashShownThisProcess = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        appInBackground = false
        Log.d("XJ_VID", "onNewIntent (媒体卡片回前台): cur=${videoMpRef[0]?.currentPosition?.toLong()} mp=${videoMpRef[0] != null}")
        // 直接把画面接回现有播放器（不重建、不重跑 loadVideo）
        // 【方案B】幂等绑定：Surface 常驻没变就不会真正 setSurface → 不重解关键帧 → 不重播当句
        if (videoUri != null && videoSurfaceRef[0] != null) {
            bindVideoSurface(videoSurfaceRef[0], "onNewIntent")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashGuard()   // 越早装越好：之后所有主线程异常都能被兜住，而不是直接闪退
        logLastCrash(this)    // 上次要是闪退过，把堆栈打进日志
        // 【0915】把应用级 Context 挂到会话对象上：文件级顶层函数（如 videoJumpSentence）拿不到
        // LocalContext，但「上一句复读」要读音频模式的 RepeatPrefs 重播次数。只存 Application Context，不泄漏。
        PlaybackSession.attachContext(this)
        startForegroundService(Intent(this, PlayerService::class.java))
        bindService(Intent(this, PlayerService::class.java), connection, Context.BIND_AUTO_CREATE)
        // 恢复悬浮窗：上次开启过则自动拉起（应用在前台，可安全 startService）
        if (FloatPrefs.load(this) && Settings.canDrawOverlays(this)) {
            startService(Intent(this, FloatLyricsService::class.java))
        }
        setContent {
            val context = LocalContext.current
            // 收集抓 B站 进度/完成事件（进程内 SharedFlow，规避 Android 14 隐式广播投递限制）
            LaunchedEffect(Unit) {
                BiliImportBus.events.collect { event ->
                    when (event) {
                        is BiliImportEvent.Progress -> {
                            biliStatus = event.text
                        }
                        is BiliImportEvent.Done -> {
                            if (!event.ok) {
                                biliError = event.error ?: "抓取失败"
                                biliStatus = ""
                                return@collect
                            }
                            val path = event.path ?: return@collect
                            val file = File(path)
                            if (event.videoMode) {
                                openVideoFileByPath(this@MainActivity, file)
                                biliNavigate?.invoke(Page.VIDEO)
                            } else {
                                val item = AudioItem(id = System.currentTimeMillis(), path = path, title = event.title ?: "B站视频")
                                PlayerService.instance?.setPlaylist(listOf(item), 0)
                                biliNavigate?.invoke(Page.PLAYER)
                            }
                        }
                    }
                }
            }
            var themeIndex by remember { mutableStateOf(ThemePrefs.loadIndex(context)) }
            var darkMode by remember { mutableStateOf(ThemePrefs.loadDark(context)) }
            var splashDone by remember { mutableStateOf(appSplashShownThisProcess) }
            val colorScheme = remember(themeIndex, darkMode) { xuanjiColorScheme(themeIndex, darkMode) }
            MaterialTheme(colorScheme = colorScheme) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppRoot(
                            themeIndex = themeIndex,
                            darkMode = darkMode,
                            onThemeIndexChange = {
                                themeIndex = it
                                ThemePrefs.saveIndex(context, it)
                            },
                            onDarkModeChange = {
                                darkMode = it
                                ThemePrefs.saveDark(context, it)
                            },
                        )
                    }
                    // 启动动画：照片逐渐放大 → 淡出，露出播放页
                    if (!splashDone) {
                        SplashOverlay(onDone = { appSplashShownThisProcess = true; splashDone = true })
                    }
                }
            }
        }
    }

    // 切后台/进程被杀前落盘视频现场，下次启动可恢复进度（不要求重新选视频）
    override fun onPause() {
        super.onPause()
        appInBackground = true
        if (videoUri != null) {
            videoMpRef[0]?.let { mp -> runCatching { currentPos = mp.currentPosition.toLong() } }
            saveVideoPrefs(this)
            // 【续播修复】进后台即标记「已交后台」并落盘权威续播位。
            // 此前 videoHandedToBg 全代码从未被置 true，导致 LaunchedEffect 里
            // `if (!videoHandedToBg) videoResumePos = 0` 永远清空续播位，
            // Activity 被销毁重建时只能靠 currentPos/SP 兜底，位置算错→跳句/重播上一句。
            videoHandedToBg = true
            videoBgResumePos = currentPos
            videoResumePos = currentPos
            // 【方案B】不再 setSurface(null) 摘画面：
            // ① TextureView 是普通 View，随窗口一起隐藏，不像 SurfaceView 那样独立成层透到锁屏，无需摘；
            // ② 摘画面会让回前台必须重新 setSurface → 解码器重解关键帧 → 画面闪回=重播当句（本 bug 根因）。
            // 保持 Surface 常驻，声音继续播放。
            Log.d("XJ_VID", "onPause cur=${videoMpRef[0]?.currentPosition} KEEP surface(TextureView 常驻); audio keeps playing")
            // 【0905 续播不重播】后台持续刷续播位：每 1.5s 读一次最新位置落盘，
            // 避免 Activity 被销毁重建时续播位滞后、SEEK_NEXT_SYNC 往前跳一大段。
            bgPosRunnable = object : Runnable {
                override fun run() {
                    videoMpRef[0]?.let { mp ->
                        runCatching { currentPos = mp.currentPosition.toLong() }
                        runCatching { saveVideoPrefs(this@MainActivity) }
                    }
                    bgPosHandler.postDelayed(this, 1500)
                }
            }
            bgPosHandler.postDelayed(bgPosRunnable!!, 1500)
        }
        // 切后台不再交接给第二个播放器（那正是每次进/出后台卡顿的根因）。保持 videoPlaying 原样即可。
    }

    // 回前台：【方案B】TextureView 的 SurfaceTexture 常驻，切后台不摘画面、不销毁 Surface，音频一直在播。
    // 画面接回：Surface 一直有效，通常什么都不用做；只有纹理真的换了才由 onSurfaceTextureAvailable 重绑。
    // 全程只操作同一个 MediaPlayer：无双播放器交接、无 seek，也【不会重解关键帧】
    // （此前 SurfaceView 每次回前台都要重绑 → 重解关键帧 → 画面闪回句首，看着像重播当句）。
    override fun onResume() {
        super.onResume()
        appInBackground = false
        videoHandedToBg = false
        // 【0905 续播不重播】回前台取消后台持续落盘器（避免与前台轮询重复写 SP）
        bgPosRunnable?.let { bgPosHandler.removeCallbacks(it) }; bgPosRunnable = null
        if (videoUri != null && videoSurfaceRef[0] != null) {
            bindVideoSurface(videoSurfaceRef[0], "onResume")   // 幂等：同一 Surface 不重绑 → 不重解关键帧
            Log.d("XJ_VID", "onResume re-bind surface; mp=${videoMpRef[0] != null}")
        }
    }

    override fun onDestroy() {
        bgPosRunnable?.let { bgPosHandler.removeCallbacks(it) }; bgPosRunnable = null
        // 【0910】仅真正退出(isFinishing)才释放视频播放器；后台/重建(onDestroy 但非 finishing)
        // 保留它，这样回前台能直接复用、只重接画面（不重建不 seek）。进程被杀时播放器随进程消亡，无泄漏。
        if (isFinishing) releaseVideoPlayer()
        unbindService(connection)
        super.onDestroy()
    }

    // ===== 崩溃兜底 =====
    // 实测结论（2026-08-30，用探针点崩过三次，别再想当然）：
    //   能兜住：普通 post 到主线程的消息里抛的异常 —— 内层 loop 退出被 catch 后能续上。
    //   兜不住：点击/触摸回调里抛的异常。这类异常走的是 nativePollOnce → InputEventReceiver
    //           的 JNI 上行调用，异常不经过 Java 调用栈的正常展开，直接冒到系统的
    //           默认 UncaughtExceptionHandler，进程照杀（实测 pid 每次都换，界面回桌面）。
    // 所以别对外说"装了它就再也不闪退"，那是假的。它真正的价值是下面这一半：
    // 无论哪种崩法，堆栈都一定会落盘（用 commit 同步写），下次启动能捞出来定位。
    private fun installCrashGuard() {
        if (crashGuardInstalled) return
        crashGuardInstalled = true
        val app = applicationContext
        // 子线程的未捕获异常救不回来（进程必死），但要留下现场，方便事后排查
        val prevHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { saveLastCrash(app, e) }
            prevHandler?.uncaughtException(t, e)
        }
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            var crashes = 0
            var windowStart = System.currentTimeMillis()
            while (true) {
                try {
                    android.os.Looper.loop()
                    break       // loop() 正常返回说明是 quit，别再续命
                } catch (t: Throwable) {
                    runCatching { saveLastCrash(app, t) }
                    val now = System.currentTimeMillis()
                    if (now - windowStart > CRASH_WINDOW_MS) { windowStart = now; crashes = 0 }
                    crashes++
                    android.util.Log.e("CrashGuard", "主线程异常被兜住（第 $crashes 次）", t)
                    // 短时间反复崩说明救不回来，交还系统，免得无限续命把界面刷屏卡死
                    if (crashes > CRASH_MAX_IN_WINDOW) {
                        android.util.Log.e("CrashGuard", "异常过于频繁，放弃兜底")
                        throw t
                    }
                }
            }
        }
    }

    /** 把异常现场存到 SP：万一真闪退了，这段信息能直接拿来定位，不用再靠猜 */
    private fun saveLastCrash(context: Context, t: Throwable) {
        try {
            val sb = StringBuilder()
            sb.append(t.javaClass.name).append(": ").append(t.message ?: "").append("\n")
            t.stackTrace.take(15).forEach { sb.append("    at ").append(it).append("\n") }
            // 必须 commit 不能 apply：进程马上就要被杀，异步落盘来不及写，现场就丢了
            context.getSharedPreferences(PREFS_CRASH, Context.MODE_PRIVATE).edit()
                .putString("last", sb.toString())
                .putLong("time", System.currentTimeMillis())
                .commit()
        } catch (_: Throwable) { }
    }

    /** 启动时把上次闪退的堆栈打进日志：真出了事，插上 USB 一条 logcat 就能捞到现场，不用复现 */
    private fun logLastCrash(context: Context) {
        runCatching {
            val sp = context.getSharedPreferences(PREFS_CRASH, Context.MODE_PRIVATE)
            val last = sp.getString("last", null) ?: return
            val time = sp.getLong("time", 0L)
            android.util.Log.e("XJ_LASTCRASH", "上次崩溃时间=${java.util.Date(time)}\n$last")
        }
    }

    /** 底栏模式栏：音频模式 / 阅读模式 / 视频模式，等宽 */
    @Composable
    private fun ModeBar(
        current: Page,
        onSelect: (Page) -> Unit,
        enabled: Boolean = true,
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(64.dp)) {
            ModeButton(
                text = "音频模式",
                icon = XuanjiIcons.MusicNote,
                selected = current == Page.PLAYER,
                onClick = { onSelect(Page.PLAYER) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            ModeButton(
                text = "阅读模式",
                icon = XuanjiIcons.Book,
                selected = current == Page.READING || current == Page.VOCAB || current == Page.REVIEW,
                onClick = { onSelect(Page.READING) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            ModeButton(
                text = "视频模式",
                icon = XuanjiIcons.Video,
                selected = current == Page.VIDEO,
                onClick = { onSelect(Page.VIDEO) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
        }
    }

    @Composable
    private fun ModeButton(
        text: String,
        icon: ImageVector,
        selected: Boolean,
        onClick: () -> Unit,
        enabled: Boolean = true,
        modifier: Modifier = Modifier,
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(22.dp),
            color = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
            modifier = modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                .alpha(if (enabled) 1f else 0.45f),
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    icon,
                    contentDescription = text,
                    modifier = Modifier.padding(end = 4.dp),
                    tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    @Composable
    private fun AppRoot(
        themeIndex: Int,
        darkMode: Boolean,
        onThemeIndexChange: (Int) -> Unit,
        onDarkModeChange: (Boolean) -> Unit,
    ) {
        val context = LocalContext.current
        val svc = service
        val state by (svc?.state?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(PlayerState()) })
        var page by remember { mutableStateOf(Page.PLAYER) }
        // 阅读模式顶栏「阅读选集」→ 让 ReadingPage 打开/关闭「阅读选集」视图
        // （= 原来的「全部」合并播放历史）。音频进阅读、视频进阅读都能用（0914）。
        val readingLibraryReq = remember { mutableStateOf(false) }
        // 进文件夹页时先停在音频还是视频那一栏（从视频模式进来就先看视频）
        var folderInitType by remember { mutableStateOf(MediaType.AUDIO) }
        // 把 page 的写权限暴露给文件级广播接收器：前台服务抓完 B站 自动导航到视频/复读机。
        // 视频模式进文件夹→文件夹默认看视频栏、顶栏显示「视频选集」（0904 修 Bug C）
        biliNavigate = { p ->
            if (p == Page.FOLDER && page == Page.VIDEO) folderInitType = MediaType.VIDEO
            page = p
        }
        // 进文件夹页前记住来源模式：退出文件夹时回到进入时的模式（需求3）
        var folderReturnPage by remember { mutableStateOf(Page.PLAYER) }
        var layoutEdit by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        var floatOn by remember { mutableStateOf(FloatPrefs.load(context)) }
        var resumeOn by remember { mutableStateOf(PlaybackPrefs.loadResume(context)) }
        // 选集页滚动位置：提升到 Activity 层，页面切换（组合销毁/重建）后仍保留。
        // rememberSaveable 在这里有效：Activity 重建/旋转时也恢复。
        val trackListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

        // 服务绑定完成后：恢复上次播放现场（服务内部判断已有播放内容时跳过）
        LaunchedEffect(svc) {
            svc?.restorePlayback()
        }

        // 音频 + 视频一起申请：视频也要在文件管理器里列出来，缺了 READ_MEDIA_VIDEO 会扫不到
        val permissions = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val missingPerms = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

        // 权限请求必须等组合完成后再发起（launcher 需要先完成初始化），
        // 否则在组合函数体内直接 launch() 会抛 "Launcher has not been initialized"
        LaunchedEffect(Unit) {
            if (missingPerms.isNotEmpty()) permLauncher.launch(missingPerms.toTypedArray())
        }

        // 启动即恢复上次现场：视频 Uri/进度/字幕 + 退出时所处的页面与播放状态，
        // 让冷启/媒体中心返回能回到上次模式（视频/阅读/音频），并尊重暂停/播放状态（而非总回音频）。
        LaunchedEffect(Unit) {
            loadVideoPrefs(context)
            when (sessionPage) {
                Page.VIDEO -> if (videoUri != null) {
                    page = Page.VIDEO
                    // 视频模式恢复：确保不串音频（restorePlayback 可能因 lastMode 误续播音频）
                    PlayerService.instance?.stopAudioForVideo()
                }
                Page.READING -> { page = Page.READING }   // readingFromVideo 已由 loadVideoPrefs 设好
                else -> { /* PLAYER 默认，无需改 page */ }
            }
        }

        // 退出状态实时落盘：页面或播放状态一变就存，进程被杀(划掉后台)后冷启也能恢复上次模式/暂停播放。
        LaunchedEffect(page) {
            sessionPage = page; saveVideoPrefs(context)
            // 【0913】离开阅读页时清掉「阅读选集」请求：残留的请求会让下次进阅读直接停在选集视图。
            if (page != Page.READING) readingLibraryReq.value = false
        }
        LaunchedEffect(videoPlaying) { saveVideoPrefs(context) }

        // Phase3-步骤2：把「当前会话是不是视频源」的推导收敛进唯一真值 PlaybackSession。
        // 这段推导以前在页面里被抄了 6 遍、写法还不一样；顶栏「生成字幕」按钮那处漏了
        // 「从视频进阅读/生词本」，于是按钮错判成音频（显示禁用、或点下去给音频生成字幕）。
        // 统一规则：视频模式，或「从视频来源进入的阅读/生词本」→ 视频；其余 → 音频。
        LaunchedEffect(page, readingFromVideo) {
            PlaybackSession.setSessionSource(
                page == Page.VIDEO ||
                    ((page == Page.READING || page == Page.VOCAB || page == Page.REVIEW) && readingFromVideo)
            )
        }

        // 音频模式字幕落库：句子就绪（断句/ASR/缓存任一路径）→ 归档字幕库，供阅读模式「全部」汇总复习
        LaunchedEffect(state.sentences, state.currentItem) {
            val item = state.currentItem
            if (item != null && state.sentences.isNotEmpty()) {
                SubtitleLibrary.saveAudio(context, item.title, state.sentences, item.path)
            }
        }

        // 全局返回逻辑：非播放页左滑/返回键先回到播放页（生词本页先回阅读模式）
        // 设置面板打开时必须让位给它，否则两处 BackHandler 同时监听，第一次返回会被这里吃掉
        // （表现就是「设置页要按两次返回才关」）
        BackHandler(enabled = page != Page.PLAYER && !showSettings) {
            when {
                // 视频全屏时：返回先退出全屏、留在视频模式，不要跳回音频播放页
                page == Page.VIDEO && videoFullscreen -> videoFullscreen = false
                // 复习页：返回回生词本（它是从生词本进去的）
                page == Page.REVIEW -> page = Page.VOCAB
                page == Page.VOCAB -> page = Page.READING
                page == Page.FOLDER -> page = folderReturnPage
                // 阅读(视频源)：返回回到视频模式——保留同一个 VideoView 播放器不重建、不双声
                page == Page.READING && readingFromVideo -> { page = Page.VIDEO; readingFromVideo = false; videoSentenceStopAt = null }
                else -> page = Page.PLAYER
            }
        }

        // 离开播放页（切页）自动退出编辑模式，返回播放页时保持正常状态
        LaunchedEffect(page) {
            if (page != Page.PLAYER) layoutEdit = false
            // 离开视频模式时退出全屏，避免卡在全屏状态
            if (page != Page.VIDEO) videoFullscreen = false
            // 模式互斥：进视频模式时把音频播放服务暂停，避免音频模式在后台接着放、
            // 听起来像「视频切回来自动播放、播放键却显示没播」的串音错觉。
            // （反向：离开视频模式时 VideoPage 的 onDispose 会 stopPlayback 停掉视频，无需这里再处理）
            if (page == Page.VIDEO) PlayerService.instance?.pause()
            // 🔴【0917 阿亮报】音频正在播的时候进复习，音频居然接着放，跟卡片自己的发音串在一起，
            // 也盖住他"心里先想意思"的注意力 ⇒ 进复习页把音频暂停。
            // 用 pause 不用 stop：位置留着，回播放页按一下就能接着听。
            // ⚠️ 只停复习页，**别顺手把阅读模式/生词本也停了** —— 阅读模式本来就是"边听边看"，
            //    之前"阅读无缝接管音频"是刻意做的（见 0904 那几轮），停了就是回退。
            if (page == Page.REVIEW) PlayerService.instance?.pause()
        }

        // Phase3-步骤2：订阅「当前播放源」唯一真值。
        // 注意 StateFlow 必须 collect 才能触发重组，直接读 PlaybackSession.current 是不会重组的。
        val activeKind by PlaybackSession.activeKind.collectAsStateWithLifecycle()

        Surface(modifier = Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
                // 顶栏：左设置、中「正在学」选集下拉、右生成歌词/文件夹
                if (!(page == Page.VIDEO && videoFullscreen)) Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().height(52.dp).padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                // 选集入口：音频模式=音频选集（跳选集页）；
                // 视频模式=选择视频 → 跳文件夹页（文件管理器样式，跟音频那边一个路子），
                // 进去默认停在「视频」那一栏，不会跟音频混在一起看花眼
                // 【0903 用户反馈】文件夹页里顶栏按钮死写「音频选集」很怪。改成按「当前选集的来源语境」
                // 显示：视频来源=视频选集 / 音频来源=音频选集；且点它=回到来源模式（folderReturnPage），
                // 避免在文件夹里点一下反而跳去了别的选集。
                // Phase3-步骤3：除文件夹页有它自己的「类型记忆」外，一律读「当前播放源」唯一真值。
                // 原先这里把 page==VIDEO / readingFromVideo 又抄了一遍 —— 与 PlaybackSession 同源不同写，
                // 抄漏一处就是顶栏按钮那个 bug。收敛后规则只在一处定义。
                val selectionIsVideo = when {
                    page == Page.FOLDER -> folderInitType == MediaType.VIDEO
                    else -> activeKind == MediaKind.VIDEO
                }
                val isReadingSel = page == Page.READING || page == Page.VOCAB || page == Page.REVIEW
                Surface(
                    onClick = {
                        if (page == Page.READING || page == Page.VOCAB || page == Page.REVIEW) {
                            // 阅读模式：顶栏按钮 =「阅读选集」→ 开关合并播放历史（= 原「全部」），不导航。
                            // 【0914】视频进阅读也能用了（三个来源 tab 已去掉，选集视图成了统一入口），
                            // 所以不再有「阅读(视频源)下按了没人消费」的残留问题。
                            readingLibraryReq.value = true
                        } else if (page == Page.FOLDER || page == Page.TRACK) {
                            // 已在文件夹/选集列表里：点选集按钮保持选集，不再跳回播放页（0904 修 bounce）
                        } else if (selectionIsVideo) {
                            folderReturnPage = page
                            videoSelectJump = true
                            folderInitType = MediaType.VIDEO
                            page = Page.FOLDER
                        } else {
                            folderReturnPage = page
                            folderInitType = MediaType.AUDIO
                            page = Page.TRACK
                        }
                    },
                    enabled = when {
                        page == Page.FOLDER || page == Page.TRACK -> true
                        page == Page.READING || page == Page.VOCAB -> true
                        selectionIsVideo -> true
                        else -> state.currentItem != null
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = if (selectionIsVideo || isReadingSel || state.currentItem != null || page == Page.FOLDER || page == Page.TRACK) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (isReadingSel) Icons.AutoMirrored.Filled.List else if (selectionIsVideo) Icons.Filled.VideoFile else XuanjiIcons.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selectionIsVideo || isReadingSel || state.currentItem != null || page == Page.FOLDER || page == Page.TRACK) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isReadingSel) "阅读选集" else if (selectionIsVideo) "视频选集" else "音频选集",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selectionIsVideo || isReadingSel || state.currentItem != null || page == Page.FOLDER || page == Page.TRACK) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                // Phase3-步骤2：改用「当前播放源」唯一真值判定。
                // 原先写死 page == Page.VIDEO，漏了「从视频进阅读/生词本」→ 按钮错判成音频。
                val isVideoMode = activeKind == MediaKind.VIDEO
                val genActive = if (isVideoMode) recognizing else state.recognizing
                val genDisabled = if (isVideoMode) (videoUri == null) else (state.currentItem == null)
                Surface(
                    onClick = {
                        if (isVideoMode) { if (!recognizing) videoGenerateLyrics(context) }
                        else { if (!state.recognizing) PlayerService.instance?.generateLyrics() }
                    },
                    enabled = !genActive && !genDisabled,
                    shape = RoundedCornerShape(16.dp),
                    color = if (genActive || genDisabled) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (genActive) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                        }
                        Text(
                            text = when {
                                !genActive -> "生成字幕"
                                isVideoMode -> "识别中 $asrProgress%"
                                state.recognizeProgress.isNotEmpty() -> "识别中 ${state.recognizeProgress}"
                                else -> "识别中…"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (genActive || genDisabled) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                IconButton(onClick = {
                    videoSelectJump = false
                    folderReturnPage = page
                    // 文件夹 icon 从视频模式/阅读(视频来源)点入时默认看视频，其余看音频
                    // Phase3-步骤3：读唯一真值（原先这里把 page==VIDEO && readingFromVideo 整段重抄了一遍）
                    folderInitType = if (activeKind == MediaKind.VIDEO) MediaType.VIDEO else MediaType.AUDIO
                    page = Page.FOLDER
                }) {
                    Icon(XuanjiIcons.Folder, contentDescription = "文件夹")
                }
                }

                // 内容区（占据剩余空间）
                androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                    // ===== 常驻视频层（阅读无缝衔接核心）=====
                    // 视频模式 / 阅读(视频源) 时把 VideoView 常驻挂载：切视频↔阅读不卸载 VideoView，
                    // 底层 MediaPlayer 永不重建 → 零延迟、零双声、无孤儿音频（彻底消灭 0903 那堆打地鼠补丁）。
                    // 阅读(视频源)时 VideoPage 进入 readingMode：只留背后挂载的 VideoView（音频继续走），
                    // 由 ReadingPage 不透明覆盖层盖住画面、只露字幕。
                    if (page == Page.VIDEO || (page == Page.READING && readingFromVideo)) {
                        VideoPage(readingMode = page == Page.READING && readingFromVideo, layoutEdit = layoutEdit)
                    }
                    when (page) {
                        Page.PLAYER -> PlayerPage(
                            svc = svc,
                            state = state,
                            layoutEdit = layoutEdit,
                            onExitEdit = { layoutEdit = false },
                        )
                        Page.TRACK -> TrackPage(
                            context = context,
                            svc = svc,
                            state = state,
                            listState = trackListState,
                        )
                        Page.READING -> ReadingPage(
                            svc = svc,
                            state = state,
                            onOpenVocab = { page = Page.VOCAB },
                            // 【结构·阶段1】视频源收成一个对象（null = 音频阅读）。
                            // 以前是 5 个参数分开传，容易「标志说在放视频、字幕却没给」。
                            videoSource = if (readingFromVideo) ReadingSource(
                                title = videoName.ifBlank { "视频字幕" },
                                sentences = sentences,
                                activeIndex = videoSentenceIndexGlobal,
                                onPlaySentence = { idx, ms ->
                                    // 阅读(视频源)：直接复用同一个 VideoView 播放器跳句，不切回视频页、不新建播放器
                                    // → 物理上不可能双声、不可能孤儿音频，且零延迟（沿用音频模式切阅读无延迟的思路）。
                                    // 【0915】手动点句 = 放弃复读：撤掉可能残留的高亮钉子和复读任务，
                                    // 否则钉住分支会把高亮又拉回旧句、看起来像「点不动」。
                                    clearVideoSentenceRepeat()
                                    videoSentenceIndexGlobal = idx
                                    currentPos = ms
                                    // 记一句尾，轮询到句尾自动停（对齐音频阅读「读一句停一句」体验）
                                    videoSentenceStopAt = sentences.getOrNull(idx)?.endMs
                                    videoSeek(ms)
                                    if (!videoPlaying) videoTogglePlay()
                                    PlayerService.instance?.notifyVideoStateChanged()
                                },
                            ) else null,
                            // 【0903 用户反馈】「全部」字幕库点播放：按归档路径重新打开原媒体。
                            // 视频→开视频文件进视频模式（VideoPage 装载/字幕点句照常）；
                            // 音频→让 PlayerService 播放该文件（音频模式断句/字幕流程接管）。
                            onPlayLibraryEntry = { entry ->
                                val p = entry.path
                                if (entry.source == SubtitleLibrary.SOURCE_VIDEO) {
                                    // 阅读模式内联播放视频：装好视频 + 标为视频来源 + 停在阅读页，
                                    // 复用常驻 VideoView（不切视频页），ReadingPage 作字幕覆盖层，边看边读。
                                    PlayerService.instance?.stopAudioForVideo()
                                    val file = if (!p.isNullOrBlank()) {
                                        java.io.File(p)
                                    } else {
                                        // 老版本归档的视频没存路径：按标题在媒体库里找同名视频兜底
                                        VideoScanner.scanAll(context)
                                            .firstOrNull { fileNameWithoutExt(it.path) == entry.title }
                                            ?.let { java.io.File(it.path) }
                                    }
                                    if (file == null || !file.exists()) {
                                        asrMsg = "找不到原视频文件：${entry.title}"
                                    } else {
                                        openVideoFileByPath(context, file)
                                        if (sentences.isEmpty()) sentences = entry.sentences
                                        readingFromVideo = true
                                        page = Page.READING
                                    }
                                } else {
                                    // 音频：必须有源文件路径才能播放
                                    if (p.isNullOrBlank()) {
                                        asrMsg = "该字幕没记录原文件位置，无法直接播放"
                                    } else {
                                        val item = AudioItem(id = System.currentTimeMillis(), path = p, title = entry.title)
                                        PlayerService.instance?.setPlaylist(listOf(item), 0)
                                        page = Page.PLAYER
                                    }
                                }
                            },
                            // 0914：阅读模式顶栏「阅读选集」→ ReadingPage 打开/关闭选集视图（音频/视频源都可）
                            libraryRequest = readingLibraryReq,
                        )
                        Page.VOCAB -> VocabPage(
                            onBack = { page = Page.READING },
                            onReview = { page = Page.REVIEW },
                        )
                        Page.REVIEW -> ReviewPage(onBack = { page = Page.VOCAB })
                        Page.FOLDER -> FolderPage(
                            context = context,
                            svc = svc,
                            state = state,
                            onBackToPlayer = { page = folderReturnPage },
                            initialType = folderInitType,
                            currentVideoPath = videoPath,
                            jumpToCurrentVideoFolder = videoSelectJump,
                            simplifiedHeader = videoSelectJump,
                            onOpenVideo = { item, siblings ->
                                // 选中视频 → 自动跳视频模式开播（音频那边同理，选音频回播放页）
                                // siblings = 同级视频列表，供上一曲/下一曲在文件夹内切换
                                openVideoFromLibrary(context, item, siblings)
                                page = Page.VIDEO
                            },
                        )
                        Page.VIDEO -> { /* 视频层由上方常驻 if 渲染，避免切阅读卸载 VideoView 导致播放器重建 */ }
                    }
                }
                // 底栏三模式：音频 / 阅读 / 视频（等宽，固定底部）
                            if (!(page == Page.VIDEO && videoFullscreen)) ModeBar(
                current = page,
                enabled = !layoutEdit,
                    onSelect = { target ->
                        // 进入阅读模式时记录来源：仅当「当前就在视频模式且目标是阅读」才标记读视频字幕；
                        // 其余情况（进视频/音频/阅读来源非视频）一律 false，避免状态泄漏导致后续误判。
                        val readingVideoSource = (target == Page.READING && page == Page.VIDEO)
                        readingFromVideo = readingVideoSource
                        // 离开阅读模式：清掉「读到句尾自动停」标记，避免回到视频模式后到句尾莫名暂停
                        if (target != Page.READING) videoSentenceStopAt = null
                        // 【0913】只有「进视频」或「进阅读(视频源)」才停音频——那里才可能跟视频双声/出幽灵音。
                        // 从音频进阅读必须保持播放：阅读模式是音频的镜像视图，停了就不是「边听边读」了。
                        if (target == Page.VIDEO || readingVideoSource) PlayerService.instance?.stopAudioForVideo()
                        // 切回音频模式：视频模式冷启时 restorePlayback 没预备音频播放器，
                        // 这里按保存位置干净加载（已存在则跳过），彻底修「视频退出→重开→切音频后失控」
                        if (target == Page.PLAYER) PlayerService.instance?.loadAudioFromRestoreIfNeeded()
                        page = target
                    },
            )
            }
        }

        if (showSettings) {
            // 【0916】设置从底部弹窗（ModalBottomSheet）改成**独立全屏页面**。
            // 原来半屏装不下 8 项 + 主题色行 + 两条长说明，且 BottomSheet 的返回是两段式的
            //（第一次 back 先滚回顶部、第二次才关），曾专门写 BackHandler 绕开，还踩过
            // "卡在半收起状态"的坑。独立页面空间够、返回天然正确，283 行也顺便挪出本文件。
            SettingsPage(
                themeIndex = themeIndex,
                darkMode = darkMode,
                floatOn = floatOn,
                resumeOn = resumeOn,
                layoutEdit = layoutEdit,
                onThemeIndexChange = onThemeIndexChange,
                onDarkModeChange = onDarkModeChange,
                onFloatChange = {
                    toggleFloat(context, it) { ok ->
                        floatOn = ok
                        FloatPrefs.save(context, ok)
                    }
                },
                onResumeChange = {
                    resumeOn = it
                    PlaybackPrefs.saveResume(context, it)
                },
                onLayoutEditChange = {
                    layoutEdit = it
                    if (it) showSettings = false // 开启编辑模式自动收起设置面板，直接进入编辑
                },
                onBack = { showSettings = false },
            )
        }

    }

    /** 悬浮窗开关：无权限时跳授权页；开启/关闭 FloatLyricsService */
    private fun toggleFloat(context: Context, on: Boolean, onResult: (Boolean) -> Unit) {
        if (on) {
            if (Settings.canDrawOverlays(context)) {
                context.startService(Intent(context, FloatLyricsService::class.java))
                onResult(true)
            } else {
                onResult(false)
                try {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                } catch (_: Exception) {
                }
            }
        } else {
            context.stopService(Intent(context, FloatLyricsService::class.java))
            onResult(false)
        }
    }

}

/** 启动动画：高清照片（640px）淡入 → 放大到屏幕宽度 → 定格 → 淡出，露出播放页 */
@Composable
private fun SplashOverlay(onDone: () -> Unit) {
    val alpha = remember { Animatable(0f) } // 初始透明，先淡入衔接系统启动窗
    val startSize = 96.dp
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    // 关键：Image 本身按屏幕宽度渲染，再用 graphicsLayer 从 96dp 缩放到全宽，
    // 避免"先压到 96dp 低分辨率再放大"导致的最后一秒变花。
    val initialScale = (startSize.value / screenWidth.value).coerceAtLeast(0.01f)
    val scale = remember { Animatable(initialScale) }
    LaunchedEffect(Unit) {
        // B: 快速淡入（120ms），避免首帧硬切
        alpha.animateTo(1f, animationSpec = tween(120, easing = LinearEasing))
        // A: 照片从 96dp 放大到与屏幕同宽
        scale.animateTo(1f, animationSpec = tween(480, easing = FastOutSlowInEasing))
        // C: 定格 100ms，视觉上有"到位"停顿感
        delay(100)
        // 淡出：透明度降到最低，透出播放页
        alpha.animateTo(0f, animationSpec = tween(300, easing = LinearEasing))
        onDone()
    }
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFFABB1A8)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_splash_photo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(screenWidth)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                },
        )
    }
}

/**
 * 视频 ASR 短句合并：TeleSpeechASR 的 segment 粒度偶发偏碎（一两词一句），
 * 把时长 < 1200ms 的短句与相邻句合并，字幕行更整、点句复读听感更自然。
 * 逻辑对齐 PlayerService.mergeShortSentences（优先并下一个，无则并上一个）。
 */
private fun mergeShortAsrSentences(list: List<Sentence>): List<Sentence> {
    val shortMs = 1200L
    // 相邻两句间隔超过 5 秒就别硬并：分片识别时若中间某段失败被跳过，
    // 前后两句会隔着整个失败段（10 分钟），并起来就是一句点下去要播十分钟的巨无霸
    val gapLimitMs = 5000L
    val out = list.toMutableList()
    var changed = true
    while (changed) {
        changed = false
        var k = 0
        while (k < out.size) {
            val n = out[k]
            if (n.endMs - n.startMs < shortMs && out.size > 1 && n.text.isNotEmpty()) {
                val next = out.getOrNull(k + 1)
                if (next != null && next.text.isNotEmpty() && next.startMs - n.endMs <= gapLimitMs) {
                    // 注意必须写 ${n.text}：写成 "$n.text" 会被 Kotlin 解析成 ${n} 的 toString 再拼上字面量 ".text"，
                    // 结果字幕里出现 "Sentence(startMs=..., text=xxx).text" 这种鬼文本
                    out[k] = Sentence(n.startMs, next.endMs, "${n.text} ${next.text}".trim())
                    out.removeAt(k + 1)
                    changed = true
                    continue
                }
                val prev = out.getOrNull(k - 1)
                if (prev != null && prev.text.isNotEmpty() && n.startMs - prev.endMs <= gapLimitMs) {
                    out[k - 1] = Sentence(prev.startMs, n.endMs, "${prev.text} ${n.text}".trim())
                    out.removeAt(k)
                    changed = true
                    k--
                    continue
                }
            }
            k++
        }
    }
    return out
}

// ===== 视频播放控制（文件级）：控制条 / 系统媒体中心 / 音频焦点 共用同一套状态 =====
/** 【0903】VideoView 方案：Surface 由 VideoView 自己管理，无需手动 Surface/SurfaceTexture。 */

/** 视频真正开始播放时，把控制器塞给 PlayerService（接管媒体中心/通知/音频焦点） */
private fun attachVideoPlayback() {
    PlayerService.instance?.attachVideoController(videoMediaController)
}

/** 释放视频播放器（切出视频模式 / Activity 销毁时）。单播放器架构：只有一个播放器，无后台第二播放器。 */
    private fun releaseVideoPlayer() {
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

private fun videoTogglePlay() {
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
private fun videoSeek(targetMs: Long, whence: String = "?" ) {
    val mp = videoMpRef[0] ?: return
    val cur = runCatching { mp.currentPosition.toLong() }.getOrDefault(-1L)
    Log.d("XJ_SEEK", "videoSeek whence=$whence target=$targetMs cur=$cur dir=${if (targetMs < cur) "BACK" else "FWD"}")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        runCatching { mp.seekTo(targetMs, MediaPlayer.SEEK_CLOSEST) }
    } else {
        runCatching { mp.seekTo(targetMs.toInt()) }
    }
}

private fun videoSeekBy(delta: Int) {
    val mp = videoMpRef[0] ?: return
    val p = (mp.currentPosition + delta).coerceIn(0, mp.duration)
    videoSeek(p.toLong()); currentPos = p.toLong()
}

/** 跳到第 idx 句并高亮（与旧 VideoView 版逻辑一致：落点异步复核，避免跳到上一句中间） */
private fun videoSeekToSentence(idx: Int) {
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

private fun videoJumpSentence(step: Int) {
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
private fun VideoPage(readingMode: Boolean = false, layoutEdit: Boolean = false) {
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
                if (!readingMode && videoFullscreen && videoSubOn && sentences.isNotEmpty() && videoSentenceIndexGlobal >= 0) {
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
                // 【0915】控制排会临时长出状态文字（未配置Key提示/抓取中/识别进度/asrMsg），
                // 高度被钉死就会溢出重叠 ⇒ 让它带 autoFit：偏好高度当下限，内容更高就撑开。
                autoFit = { it == VideoModule.TRANSPORT },
            ) { module ->
                when (module) {
                    // ---- 5 键控制排（含 B站 输入行、导入/识别状态提示） ----
                    VideoModule.TRANSPORT -> {
                        // 主控制一排：与音频模式统一的 5 键（上一曲/上一句/播放/下一句/下一曲）
                        if (videoUri != null) {
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
                            var biliInputRevealed by remember { mutableStateOf(false) }
                            var dragTotal by remember { mutableStateOf(0f) }   // 本次拖拽累计位移（左滑为负）
                            val scope = rememberCoroutineScope()
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
                                fun onDragStopped() {
                                    // 松手：净位移过半 → 停在"输入框打开"，否则收回功能键
                                    val pull = (slideX.value + dragTotal).coerceIn(0f, inputWPx)
                                    biliInputRevealed = pull > inputWPx * 0.5f
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
                                VideoTransportRow(
                                    modifier = Modifier
                                        .requiredWidth(screenW)
                                        .offset { IntOffset(-(slideX.value + dragTotal).coerceIn(0f, inputWPx).roundToInt(), 0) }
                                        .draggable(
                                            state = draggableState,
                                            orientation = Orientation.Horizontal,
                                            startDragImmediately = false,
                                            onDragStopped = { onDragStopped() },
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
                                            onDragStopped = { onDragStopped() },
                                        ),
                                    cardShape = RoundedCornerShape(20.dp),
                                    value = biliUrl,
                                    status = biliStatus,
                                    onValueChange = { biliUrl = it },
                                    onSubmit = { url ->
                                        val u = url.trim()
                                        if (u.isNotBlank()) {
                                            startBiliImport(context, u)
                                            biliInputRevealed = false
                                            biliUrl = ""
                                            biliStatus = "后台抓取中…"
                                            settle()
                                        }
                                    },
                                    onClose = {
                                        biliInputRevealed = false
                                        settle()
                                    },
                                )
                            }

                            // 导入状态常驻显示
                            // 导入状态常驻显示（不随输入框收起而消失，让"后台抓取中 / 进度"始终可见）
                            if (biliStatus.isNotEmpty()) {
                                Text(
                                    biliStatus,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
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
                        if (recognizing) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                                CircularProgressIndicator(
                                    progress = if (asrProgress < 100) asrProgress / 100f else 1f,
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (asrProgress < 100) "抽音轨中… $asrProgress%" else "云端识别中…",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        if (asrMsg.isNotEmpty()) {
                            Text(
                                asrMsg,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
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

/** 视频进度条：直接拖动 seek，支持 overlay 白配色与主界面主题配色。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoProgressSlider(
    currentMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    overlay: Boolean = false,
) {
    val totalMs = durationMs.coerceAtLeast(1L)
    val fraction = (currentMs.coerceIn(0L, totalMs).toFloat() / totalMs).coerceIn(0f, 1f)
    val trackH = 4.dp
    val thumbR = 6.dp
    val inert = Color.White.copy(alpha = 0.4f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(horizontal = 8.dp)
            // 点击轨道 → 跳转
            .pointerInput(totalMs) {
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width * totalMs).toLong().coerceIn(0L, totalMs))
                }
            }
            // 拖动 thumb → 连续跳转
            .pointerInput(totalMs) {
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
            val w = size.width
            val tx = fraction * w
            // 轨道与 thumb 都严格画在 centerY → 小圆点处于进度条正中（不再偏上）
            drawLine(color = inert, start = Offset(0f, centerY), end = Offset(w, centerY), strokeWidth = trackH.toPx(), cap = StrokeCap.Round)
            if (tx > 0f) {
                drawLine(color = Color.White, start = Offset(0f, centerY), end = Offset(tx, centerY), strokeWidth = trackH.toPx(), cap = StrokeCap.Round)
            }
            drawCircle(color = Color.White, radius = thumbR.toPx(), center = Offset(tx, centerY))
        }
    }
}

/** 视频控制条：与音频模式统一的 5 键（上一曲 / 上一句 / 播放 / 下一句 / 下一曲）。
 *  上一句/下一句 点按=上一句/下一句，长按=快退/快进 10 秒。 */
@OptIn(ExperimentalFoundationApi::class)
/** 点按=单击(tap)跳一句；按住=按住的整个期间持续触发 hold(true)，松手触发 hold(false)。
 *  【0903 bug 修复】原来用 detectTapGestures(onPress)：按下立即 hold(true)，一旦手指移动
 *  （左滑唤出 B站 输入框正好压在下一句键上）onPress 协程被手势取消，hold(false) 永不执行 →
 *  2x 快进永远停不下来（暂停/再播仍是快进，只有切曲才恢复）。改成自实现手势：
 *  - 手指移动超过 touch slop → 判定为「拖动」（交给父级 draggable），不触发 hold/tap；
 *  - 按住不动超过 HOLD_TRIGGER_MS 才进入 hold，松手/被抢占都保证发 hold(false)。 */
private const val HOLD_TRIGGER_MS = 250L   // 按下多久才算「长按快进/快退」（避开拖动误触）
private fun Modifier.pressHold(
    onTap: () -> Unit,
    onHoldChange: (Boolean) -> Unit,
): Modifier = this.pointerInput(Unit) {
    val slopPx = viewConfiguration.touchSlop.toDouble()
    awaitEachGesture {
        val down = awaitFirstDown()
        val id = down.id
        val startX = down.position.x
        val startY = down.position.y
        val startMs = System.currentTimeMillis()
        var holding = false
        var aborted = false   // 手指移动超 slop 或被父手势消费 → 判定拖动
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == id } ?: break
            if (!change.pressed) break                 // 松手
            if (change.isConsumed) { aborted = true; break }  // 被 draggable 抢走
            val dx = change.position.x - startX
            val dy = change.position.y - startY
            if (dx * dx + dy * dy > slopPx * slopPx) { aborted = true; break }  // 拖动
            if (!holding && System.currentTimeMillis() - startMs >= HOLD_TRIGGER_MS) {
                holding = true
                onHoldChange(true)
            }
        }
        // 无论怎么结束（松手/被抢占/手势取消），都保证恢复 —— 修复快进停不下来的根因
        if (holding) onHoldChange(false)
        else if (!aborted) onTap()
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
private fun BiliLinkInput(
    value: String,
    status: String,
    onValueChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    cardShape: Shape = RoundedCornerShape(12.dp),
) {
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
            if (status.isNotEmpty()) {
                Text(
                    status, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                )
            }
        }
    }
}

/** 【0903 黑屏修复】视频窗口错误/空状态覆盖层：
 *  - errorText 非空：显示错误卡片（红色边框 + 具体原因 + 重试/换视频/收起按钮），用户立即知道为什么黑屏。
 *  - errorText 为空且 videoUri==null：显示空状态引导卡（图标 + 提示 + 「去选视频」大按钮），不再让用户对着纯黑 Surface 猜。
 *  视频正常播放时这层完全透明（两个分支都不渲染），不挡画面。 */
@Composable
private fun VideoStateOverlay(
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
private fun VideoTransportRow(
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
