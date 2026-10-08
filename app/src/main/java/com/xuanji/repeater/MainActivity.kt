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
import android.view.Surface
import android.media.AudioAttributes
import androidx.media3.exoplayer.ExoPlayer
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
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// ===== 视频模式 UI 状态：提升到文件级(进程内常驻),切页面/旋转不丢,回视频页自动续播 =====
internal var videoUri by mutableStateOf<Uri?>(null)
/** 【1003 加保险】上次启动**没能**把视频现场恢复出来（文件被删/ 授权丢失 / Uri 无法识别）。
 *  true 时 saveVideoPrefs 一律跳过 ⇒ **保住 SP 里原有的 uri/path/srt/sentences 不被空值覆盖**。
 *  为什么需要：App 启动时 page 还是默认 PLAYER，LaunchedEffect(page) 会无条件存一次盘，
 *  若此刻 videoUri 是 null 就会把现场写空、再也回不来（1003 实测丢了mean girls 现场）。
 *  恢复成功 / 用户主动选了新视频（openVideo*）时置回 false。 */
internal var videoRestoreFailed = false
/** 视频的真实文件路径：从文件管理器选视频时才有。有了它就能直接读同目录 .srt（不再靠猜 Uri） */
internal var videoPath by mutableStateOf("")
internal var videoName by mutableStateOf("")
internal var playing by mutableStateOf(false)
// 视频独立的播放状态：之前视频/音频共用 playing，切到音频模式播放会把 playing 置 true，
// 切回视频模式时 LaunchedEffect(videoUri) 误判「正在播放」而自动续播、暂停保不住。
// 现在视频用 videoPlaying，与音频 playing 彻底分离。
internal var videoPlaying by mutableStateOf(false)
// 退出软件时所处的页面（PLAYER/VIDEO/READING），冷启/媒体中心返回时据它恢复 page，而非总回音频模式。
// 普通 var（不驱动 UI），仅作为持久化的桥梁：AppRoot 的 page 是组合局部，文件级 save/load 通过它读写。
private var sessionPage: Page = Page.PLAYER
internal var currentPos by mutableStateOf(0L)
internal var sentences by mutableStateOf<List<Sentence>>(emptyList())
// 当前字幕的来源名（同名字幕文件名 / 手动选的那份的原名 / "生成字幕"）。
// 【1008】原为 private：视频页「选字幕」选完要立刻把它换成新来源名，故放开成 internal（只读用它，别在别处乱写）。
internal var srtName by mutableStateOf("")
internal var loopSentence by mutableStateOf(false)
internal var recognizing by mutableStateOf(false)
internal var asrMsg by mutableStateOf("")
internal var asrProgress by mutableStateOf(0)
// 【1003 加】当前正在做**哪一步**（抽音轨 / 上传识别 / 合并…）。
// 为什么需要：asrProgress 只在抽音轨时上报，transcribe（上传 + 云端识别，几分钟）
// 期间一个数都不报 ⇒ 100% 会**僵在那不动**，阿亮反馈"让人以为没成功/卡死了"，
// 新用户尤其会以为 App 挂了。
// 这里给"卡住的那段"一个会动的表达：数字换成"步骤名 + 已用秒数"，让用户知道还活着。
// null = 不在识别流程里。
internal var asrStage by mutableStateOf<String?>(null)
internal var videoSpeed by mutableStateOf(1f)
    // 视频播放器声明见文件顶部（进程级，跨 Activity 重建存活）。
/** 【0903 黑屏修复】视频播放/加载错误信息：MediaPlayer onError 时设上，视频窗口显示醒目的错误卡片（替代静默黑屏）。
 *  null = 正常；非 null = 渲染错误卡片（带重试/换视频按钮）。 */
internal var videoErrorMessage by mutableStateOf<String?>(null)
// 当前高亮的句子。放在文件级而不是 VideoPage 的 remember：
// 切到别的模式再切回来，VideoPage 会被销毁重建，remember 状态会归零，
// 高亮就会跳回第 0 句（而实际播放位置在第 30 句），看着像「跳句失灵」。
internal var videoSentenceIndexGlobal by mutableStateOf(0)
// 视频全屏模式（需求6）：全屏时画面独占、仅全屏内叠字幕
internal var videoFullscreen by mutableStateOf(false)
internal var videoSelectJump by mutableStateOf(false)   // 视频选集：进文件夹页时直接钻到当前视频所在文件夹
internal var readingFromVideo by mutableStateOf(false)   // 阅读模式来源：true=从视频模式进入→读视频字幕；false=读音频字幕（B 方案上下文感知）
// 阅读(视频源)复用同一个 VideoView 播放器，不另建播放器 → 零延迟/零双声/无孤儿音频。
// videoSentenceStopAt：点视频字幕进阅读时记一句尾，进度轮询到句尾自动停（对齐音频阅读「读一句停一句」）。
internal var videoSentenceStopAt by mutableStateOf<Long?>(null)

// 【0915 上一句复读】视频「上一句」按音频模式「重播次数」设置，把该句复读 N 遍后再继续。
// 规则与音频 PlayerService.playSentence(repeatTimes) 完全一致：共播 N 遍 = 1 次首播 + (N-1) 次回句首重播。
// - videoRepeatStopAt：本句的句尾时间戳（= 复读目标句的 endMs）；null = 不在复读中。
// - videoRepeatStartMs：本句的句首时间戳（回跳落点，直接记下来，不在轮询里反查句子列表）。
// - videoRepeatLeft：还欠几次「回句首重播」，递减到 0 即结束。
// 三者都是文件级（不是 VideoPage 的 remember）：切模式/Activity 重建后复读状态不能丢，否则会「读到一半被打断」。
internal var videoRepeatStopAt by mutableStateOf<Long?>(null)
internal var videoRepeatStartMs = 0L
internal var videoRepeatLeft by mutableStateOf(0)
/** 结束/取消视频「上一句复读」：清掉句尾与剩余次数（句尾自然播完、用户手动跳走、换片都走这里）。
 *  同时解除高亮钉子 —— 复读没了，高亮就该交回给轮询按播放位置跟随（对齐音频模式）。 */
internal fun clearVideoSentenceRepeat() {
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
internal var videoSentencePin by mutableStateOf(-1)

// 视频切后台续播（0904）：onPause 把音轨交给前台服务，onResume 再交还 VideoView
internal var videoHandedToBg = false
// 【0910 根治·后台存活】Activity 是否真正失去前台（onPause 置 true，onResume/onNewIntent 置 false）。
// App 内切换页面(视频↔音频/文件夹)不会触发 onPause，故 false 表示「导航离开视频」→ onDispose 释放播放器；
// true 表示「切后台」→ onDispose 不释放，播放器保留、回前台 REUSE 零 seek。
internal var appInBackground = false
private var videoBgResumePos = 0L
private var videoBgWasPlaying = false
// 【0904 真机修复】权威续播位：回前台优先用「音频在后台实际推进到的新位置」，而不是 onPause 交接瞬间
// 的旧位置——否则会跳回几十秒前、把后台已播过的几句重读一遍（"复读最后一句"）。
// 真机后台 MediaPlayer 确实在往前播，但后台 peek 的位置在模拟器上会回退（不可信），故用
// 「交接位 + 后台流逝时间 × 倍速」自己推算前向位置为主，peek 仅作正向交叉校验。
// 由 onResume 写入、onPrepared 消费后清零；>0 表示「本次是回前台续播」而非冷启/换片。
internal var videoResumePos = 0L
private var videoBgHandoffTime = 0L   // onPause 交接时刻（毫秒），用于推算后台前向位置
// 【0905 续播不重播】切后台位置持续落盘器：前台 200ms 轮询在 onPause 后冻结，但单播放器后台仍在发声，
// 若不持续刷 SP，Activity 被系统销毁重建时续播位会滞后、SEEK_NEXT_SYNC 往前跳一大段。
// onPause 启动、onResume/onDestroy 取消；每 1.5s 读一次最新位置落盘。
private val bgPosHandler = android.os.Handler(android.os.Looper.getMainLooper())
private var bgPosRunnable: Runnable? = null

// 全屏内字幕开关（需求6）
internal var videoSubOn by mutableStateOf(true)
// 当前视频所在文件夹的同级视频列表（需求2：上一曲/下一曲在同级视频间切换）
internal var videoFolderVideos by mutableStateOf<List<VideoItem>>(emptyList())

// ===== 抓 B站 视频：输入框 + 导入状态（导入走前台服务，非阻塞、切后台不被掐） =====
internal var biliUrl by mutableStateOf("")
internal var biliStatus by mutableStateOf("")
internal var biliError by mutableStateOf("")
// page 是 setContent 内的 composable 局部状态，文件级广播接收器无法直接改它；
// 这里用回调把「切到视频模式 / 复读机」转交给 setContent 作用域执行。
internal var biliNavigate: ((Page) -> Unit)? = null

// 0917 生词提醒：点通知**冷启**进来时，onCreate 阶段 AppRoot 还没组合、biliNavigate 还是 null，
// 所以先把请求挂起，等 AppRoot 里那个「恢复上次现场」的 LaunchedEffect 消费掉（见那里）。
// ⚠️ 必须由那个 effect 消费：否则「恢复上次页面」（会跳回视频/阅读）会把复习页覆盖掉。
private var pendingGotoReview = false

// 抓 B站 完成/进度回调：改用进程内事件总线 BiliImportBus（见下方 setContent 内的 LaunchedEffect 收集），
// 不再用跨组件广播——Android 14 上 RECEIVER_NOT_EXPORTED 的隐式广播投不到，会导致"提交后没反应"。
// 服务跑完通过 BiliImportBus.post(BiliImportEvent.Done/Progress) 回传，这里收集后更新状态并自动打开播放。

// 触发抓 B站：前台服务跑（切后台不被系统掐），translate=false 走原长按字幕翻译逻辑
internal fun startBiliImport(context: Context, url: String) {
    val intent = Intent(context, BiliImportService::class.java)
        .putExtra(BiliImportService.EXTRA_URL, url)
        .putExtra(BiliImportService.EXTRA_TRANSLATE, false)
    context.startForegroundService(intent)
}

// 【单播放器·彻底修】视频渲染用 SurfaceView + 自建 ExoPlayer。
// 【v122 · 治 HDR 发灰】为什么从 TextureView 换回 SurfaceView：官方明说 Android 13+ 的 TextureView
// 会把 HDR 转码成 SDR（细节丢失、色彩剪切、条带）⇒ 画面整体暗一档、对比度塌，跟系统播放器一比就发灰。
// SurfaceView 独立成层、走硬件叠加，由显示端 tone mapping，观感才对。
// 【为什么当初换成过 TextureView（0905）】那时用 SurfaceView 踩到「切后台→回前台画面闪回句首」，
// 看着像重播当句：窗口不可见 → surfaceDestroyed → 回前台新建 Surface 重绑 → **MediaPlayer** 没能力
// 不 flush 换 Surface，只能 release + 重建解码器 → 被迫从最近关键帧重解。
// 【v122 为什么敢换回来】那两条前提都变了：
//   ① 内核已换 ExoPlayer（v119），走 MediaCodec.setOutputSurface()（API 23+，专为「不 flush 换画面」
//      设计，我们 minSdk=26 恒走此路）⇒ 换/摘 Surface 不再强行回关键帧；
//   ② Android 14(API 34)+ 新增 SurfaceView.setSurfaceLifecycle(
//      SURFACE_LIFECYCLE_FOLLOWS_ATTACHMENT) ⇒ Surface 跟「挂载」而非「可见性」走，切后台压根不销毁。
// ⚠️ 保留的老铁律：Surface 必须来自 SurfaceView 自己的 holder，绝不自造兜底 Surface（那才是当年
//    「黑屏」的真正元凶）；也绝不开 setZOrderOnTop(true)（画面会盖住字幕/控制排/顶栏）。
internal val videoSurfaceRef = arrayOfNulls<Surface?>(1)  // 当前视频渲染 Surface（由 SurfaceView 的 holder 提供）
// 记录播放器当前绑定的 Surface，用于幂等绑定：重复 setSurface 会白折腾解码器。
internal var videoMpBoundSurface: Surface? = null

/** 幂等绑定画面：仅当目标 Surface 与「当前已绑定的」不是同一个对象时才真正 setSurface。
 *  重复绑定同一个 Surface 会在解码器侧白折腾一趟，故必须幂等。
 *  （传 null = 摘画面，等价 ExoPlayer 的 clearVideoSurface()；API 23+ 走 setOutputSurface，**不 flush**，
 *  所以回前台重接不会回关键帧，别因为怕重播而当鸵鸟不摘。） */
internal fun bindVideoSurface(sf: Surface?, whence: String) {
    val mp = videoMpRef[0] ?: return   // 播放器还没建好（CREATE 分支会创建后再绑），此处不做事
    if (videoMpBoundSurface === sf) {
        Log.d("XJ_VID", "bindVideoSurface SKIP(same surface) whence=$whence")
        return
    }
    runCatching { mp.setVideoSurface(sf) }
    videoMpBoundSurface = sf
    Log.d("XJ_VID", "bindVideoSurface whence=$whence sf=${if (sf == null) "null" else "set"} cur=${runCatching { mp.currentPosition }.getOrDefault(-1)}")
}
// 视频总时长 / 是否就绪 / 最近一次 seek 时间戳：提升为文件级，媒体中心(系统通知)与控制逻辑共用
internal var videoDuration by mutableStateOf(0L)
internal var videoPrepared by mutableStateOf(false)
internal var videoLastSeekAt = 0L

/** 视频「外部控制器」：塞给 PlayerService，让系统媒体中心/音频焦点统一控制视频。
 *  与 UI 控制条共用同一套文件级视频状态与播放函数。 */
internal val videoMediaController = object : ExternalMediaController {
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
internal val videoAsrScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

// ===== 视频现场持久化：URI / 进度 / 字幕存 SharedPreferences，进程被杀后下次启动可恢复 =====
internal const val PREFS_VIDEO = "xuanji_video"
private const val CRASH_WINDOW_MS = 3000L      // 兜底计数窗口
private const val CRASH_MAX_IN_WINDOW = 5      // 窗口内崩这么多次就说明救不回来，交还系统
/**
 * 把「当前页」归一化成**主模式**，再写进 [saveVideoPrefs] 的 `mode`。
 *
 * 🔴【1008 阿亮报的 bug：每次打开 App 都进音频模式】根因就在这一行判定上。
 *
 * 文件夹页 [Page.FOLDER] / 音频选集页 [Page.TRACK] / 生词本 [Page.VOCAB] / 复习 [Page.REVIEW]
 * 都是**子页面**，不属于三种主模式（音频播放 / 视频 / 阅读）。原先把 `page` 原样存进 `mode`：
 * 在「视频选集」那一页退出，SP 里就写着 `mode=FOLDER`；下次启动的恢复逻辑只认 `VIDEO` / `READING`，
 * 其余一律落 `else`（什么都不做）⇒ `page` 停在初值 [Page.PLAYER] = **音频模式**。
 *
 * 更坑的是它是**自我固化**的：这一轮启动的 `LaunchedEffect(page)` 又立刻把 `mode` 改写成 `PLAYER`
 * ⇒ 一次踩中，之后每次启动都是音频模式，再也回不来（真机 100% 复现：进视频选集 → 杀进程 → 重开）。
 *
 * ⇒ 子页面必须回到「它是从哪个模式点开的」：
 * - [Page.FOLDER] / [Page.TRACK]：进页时已经把来源模式记在 `folderReturnPage` 里（两处入口都写了）；
 * - [Page.VOCAB] / [Page.REVIEW]：都是阅读模式的下级页面（返回键链路 REVIEW→VOCAB→READING）⇒ 回阅读。
 */
private fun mainModeOf(page: Page, folderReturnPage: Page): Page = when (page) {
    Page.FOLDER, Page.TRACK -> when (folderReturnPage) {
        // 来源本身就是子页面（理论上不会发生，来源模式初值就是 PLAYER）⇒ 退回音频
        Page.FOLDER, Page.TRACK -> Page.PLAYER
        else -> folderReturnPage
    }
    Page.VOCAB, Page.REVIEW -> Page.READING
    else -> page
}

// 进程级幂等：Activity 重建（旋转/配置变更）会重走 onCreate，兜底只能装一次，
// 否则每重建一次就多嵌套一层 Looper，装多了会出问题
private var crashGuardInstalled = false
internal fun saveVideoPrefs(context: Context) {
    // 🔴【1003 加保险】恢复失败时**直接不写**：此刻 videoUri 是 null，而启动时
    // LaunchedEffect(page) 会无条件调一次本函数 ⇒ 写下去就是 uri/path/srt/sentences 全空
    // ⇒ 现场永久清空。宁可这次不存（SP 里旧值还在，下次启动/文件回来还能救），也不能写空。
    if (videoRestoreFailed) return
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
internal fun loadVideoPrefs(context: Context) {
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
    // 三种 Uri 要分开判断，否则有一类会**永远恢复不了**（1003 实测踩中）：
    //   媒体库 Uri（content://media/external/video/media/<id>）：有 READ_MEDIA_VIDEO 就能读，
    //     不需要长期授权，只要文件还在磁盘上就行。
    //   file://（B站 导入等本地文件，openVideoFileByPath 用 Uri.fromFile 存的）：
    //     🔴 绝不能走下面那条 SAF 分支！persistedUriPermissions 只装 SAF/PhotoPicker 的授权，
    //     file:// 永远不在里面 ⇒ persisted 恒 false ⇒ 每次冷启都提前 return、视频现场全丢，
    //     表现就是"每次更新完都要重新选视频"（更新必冷启，冷启必走这条路）。只判文件还在不在。
    //   SAF / PhotoPicker Uri：必须校验长期授权是否还握在手里。
    //
    // 🔴【1003 加保险】下面每处提前 return 都标记 videoRestoreFailed=true。
    // 为什么：App 启动时 page 还是默认 PLAYER，1258 行的 LaunchedEffect(page) 会**无条件**
    // 跑一次 saveVideoPrefs，而它在 loadVideoPrefs 之后 ⇒ 一旦这里return，videoUri 是 null
    // ⇒ 这次就把**空值写回 SP** ⇒ 现场永久清空、再也回不来（1003 实测：mean girls 现场就是这样没的）。
    // ⇒ 失败时**只记不改**：保住 uri/path/srt/sentences 原样，下次启动/文件回来还能救。
    if (uriStr.startsWith("content://media/")) {
        if (path.isNotBlank() && !java.io.File(path).exists()) { videoRestoreFailed = true; return }
    } else if (uriStr.startsWith("file://")) {
        if (path.isNotBlank() && !java.io.File(path).exists()) { videoRestoreFailed = true; return }
    } else {
        val persisted = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        if (!persisted) { videoRestoreFailed = true; return }
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


/**
 * 从文件管理器里选中一个视频（媒体库扫描出来的，有真实路径）。
 *
 * 跟老的 PhotoPicker 比，好处有两个：
 *   1. Uri 是 `content://media/external/video/media/<id>`，有 READ_MEDIA_VIDEO 权限就能长期读，
 *      不用 takePersistableUriPermission，下次启动不会「权限没了打不开」。
 *   2. 有真实路径，同目录的 .srt 直接列目录就能找到 —— 之前靠 DocumentsContract 猜，
 *      对 PhotoPicker 的 Uri 根本不适用，所以同名字幕一直没生效。
 */
internal fun openVideoFromLibrary(context: Context, item: VideoItem, siblings: List<VideoItem> = emptyList()) {
    // 【1003 加保险】用户主动选了新视频 ⇒ 上一轮"恢复失败"作废，恢复正常存盘
    videoRestoreFailed = false
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
    // 字幕来源优先级：① 手动选过的（用户显式指定，最高）② 同名/近名的字幕文件（下面那段）③ 以前识别的结果
    // 🔴【1008】原来调 SrtParser.findBestSrtFile —— 它只认 .srt，而且找不到同名就"取同目录第一个"，
    //   一个目录里放多个视频时会把**别的视频的字幕**加载进来（张冠李戴且界面看不出来）。
    //   现在统一走 [SubtitleParser] 四级匹配（SRT/ASS/SSA + 去版本尾巴 + 编码自适应），
    //   四级都不中一律**不自动加载**（返回 null，交给右上角「选字幕」）。
    val manualSub = ManualSubtitle.saved(context, item.path)
    val subFile = manualSub ?: SubtitleParser.findBestSubtitle(item.path)
    if (subFile != null) runCatching {
        val parsed = SubtitleParser.parseFile(subFile)
        if (parsed.isNotEmpty()) {
            sentences = parsed
            srtName = if (manualSub != null) {
                ManualSubtitle.displayName(context, item.path).ifBlank { subFile.name }
            } else {
                subFile.name
            }
            // 同目录 / 手动选的字幕也算「播过的字幕」→ 归档字幕库（视频来源）
            SubtitleLibrary.saveVideo(context, videoName, parsed, item.path)
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
                // 🔴【0929】这份字幕是残缺的（上次识别中途断了/有段被跳过）⇒ 打开时就明说，
                //   否则界面只显示"生成字幕"，用户会一直以为字幕是全的，用到一半才发现少了。
                //   提示带动作：重新点「生成字幕」会整段重跑并覆盖，就能补全。
                if (it.incomplete) asrMsg = "⚠️ 字幕缺失：上次识别未跑完，点「生成字幕」可重新识别"
            }
    }
    // 【v120】前三级都没有 ⇒ 最后试「视频**内封**的字幕轨」（阿亮：往手机里一丢就自动出字幕）。
    //   🔴 优先级**故意放最低**：同目录 .srt / 以前识别过的结果，一个字都不许被它改掉。
    //   🔴 扒不到就**静默退回** —— 不提示、不占位，界面跟以前完全一样（别为加功能搞坏存量场景）。
    //   ⚠️ 扒一条轨要顺着文件扫一遍（大 MKV 几秒），所以走后台；回来时可能已经换视频了 ⇒ 用路径对上号再写回。
    if (sentences.isEmpty()) {
        val vp = item.path
        val vUri = videoUri
        if (vUri != null) EmbeddedSubtitle.autoLoad(context, vp, vUri) { name, list ->
            if (vp != videoPath) return@autoLoad   // 已经切走了，别把 A 的字幕糊到 B 上
            if (list.isEmpty()) return@autoLoad
            // 走到这里说明本来一句字幕都没有（没有句子列表 ⇒ 用户不可能已经钉过句子/启过复读），
            // 所以这次重置是安全的、也是必需的（否则下标/钉子带着上一个视频的残留）。
            clearVideoSentenceRepeat()
            videoSentenceIndexGlobal = 0
            sentences = list
            srtName = name
            asrMsg = ""
            SubtitleLibrary.saveVideo(context, videoName, list, vp)
            saveVideoPrefs(context)
        }
    }
    saveVideoPrefs(context)
}

/** 打开本地文件视频（B站导入等：文件已落 Download/XuanjiBili，无 MediaStore id，直接用 file Uri 打开）
 *  与 openVideoFromLibrary 同款：设置 URI/路径 + 自动载入同目录 .srt。 */
private fun openVideoFileByPath(context: Context, file: File, siblings: List<VideoItem> = emptyList()) {
    // 【1003 加保险】同上：用户主动选了新视频 ⇒ 恢复失败标志作废，恢复正常存盘
    videoRestoreFailed = false
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
    // 字幕来源优先级：同 openVideoFromLibrary（见那里的【1008】说明）—— 手动选的 > 同目录文件 > 识别结果
    val manualSub = ManualSubtitle.saved(context, file.path)
    val subFile = manualSub ?: SubtitleParser.findBestSubtitle(file.path)
    if (subFile != null) runCatching {
        val parsed = SubtitleParser.parseFile(subFile)
        if (parsed.isNotEmpty()) {
            sentences = parsed
            srtName = if (manualSub != null) {
                ManualSubtitle.displayName(context, file.path).ifBlank { subFile.name }
            } else {
                subFile.name
            }
            SubtitleLibrary.saveVideo(context, videoName, parsed, file.path)
        }
    }
    if (sentences.isEmpty()) {
        SubtitleLibrary.loadBySource(context, SubtitleLibrary.SOURCE_VIDEO)
            .firstOrNull { it.title == videoName && it.sentences.isNotEmpty() }
            ?.let {
                sentences = it.sentences
                srtName = "生成字幕"
                // 残缺标记同样要提示（与 openVideoFromLibrary 一致）
                if (it.incomplete) asrMsg = "⚠️ 字幕缺失：上次识别未跑完，点「生成字幕」可重新识别"
            }
    }
    // 【v120】内封字幕轨：与 openVideoFromLibrary 完全同款（优先级最低 + 扒不到静默退回）。
    //   B站 导入的视频也走这条路 —— 下载工具把字幕封进 MKV 的情况，以后就不用另外配一份 .srt 了。
    if (sentences.isEmpty()) {
        val vp = file.path
        EmbeddedSubtitle.autoLoad(context, vp, Uri.fromFile(file)) { name, list ->
            if (vp != videoPath) return@autoLoad
            if (list.isEmpty()) return@autoLoad
            clearVideoSentenceRepeat()
            videoSentenceIndexGlobal = 0
            sentences = list
            srtName = name
            asrMsg = ""
            SubtitleLibrary.saveVideo(context, videoName, list, vp)
            saveVideoPrefs(context)
        }
    }
    saveVideoPrefs(context)
}

// ===== 视频「生成字幕」：抽音轨 → 云端 ASR → 填字幕行 =====
// 短视频整段识别，快且准；长视频自动分片：逐段抽取+识别，时间戳加段偏移后拼接。
// 全程 catch(Throwable)：OOM 等是 Error 不是 Exception，只 catch Exception 会漏掉导致闪退。
//
// 🔴🔴【0929 改写】这两个常量原来是写死的（11 分钟 / 10 分钟），换算口径是「16kHz 单声道 ≈32KB/s
//   ⇒ 10 分钟 ≈19MB，卡在 24MB 上传上限内」。可那个 24MB 是照 OpenAI/Groq 的 Whisper 抄的，
//   我们实际在跑的硅基流动上限宽得多（45MB）⇒ **白白把段切小了一半**，长视频多花一倍时间。
//   ⇒ 现在改成**由服务商的实际上传上限反推**：段长 = 上限 ÷ 码率，留 10% 余量。
//   ⚠️ 别再把段长写成常量：上限是跟着 provider 走的，写死就会在换成 Groq 时切出超限的段。
private const val MIN_SEG_MS = 1000L                 // 末段零头短于 1 秒就别为它单独跑一次网络识别

/**
 * 按服务商算「一段最长多少毫秒」。
 *
 * 口径：抽出的 WAV 恒定 16kHz / 16bit / 单声道 ⇒ 32_000 B/s。
 * 留 10% 余量是给 WAV 头 + seek 落到最近同步帧时可能多解出的一点数据（见 [VideoAudioExtractor] 的注释：
 * 音频帧极小，起点误差 <100ms），余量太少会出现「切出来的段自己超了上限」。
 */
private fun segmentMsFor(provider: AsrProvider): Long {
    val usableBytes = (SpeechRecognizer.maxUploadBytes(provider) * 9) / 10   // 留 10% 余量
    val ms = usableBytes / 32L                                               // 32KB/s ⇒ 每毫秒 32 字节
    return ms.coerceAtLeast(60_000L)                                         // 兜底：至少 1 分钟
}

/** 整段识别的时长阈值 = 一段的长度（短视频只要能塞进一段，就整段走，少一次拼接） */
private fun wholeLimitMsFor(provider: AsrProvider): Long = segmentMsFor(provider)

/** 视频 ASR 总结果：识别句 + 提示 + 音轨波形静音谷(全局ms, 静音时长ms)，后者供长句切分/边界吸附 */
private data class VideoAsrResult(
    val sentences: List<Sentence>,
    val note: String,
    val silenceValleys: List<Pair<Long, Long>>,
    /**
     * 这份结果是不是**残缺**的（【0929 新增】）。
     *
     * 起因：分片识别中途失败时，[recognizeBySegments] 会把**已成功的那些段**返回（不是空），
     * 而调用方原来只判 `isNotEmpty()` 就存库 ⇒ 半份字幕被当成完整的存下来。
     * 当次还能在 asrMsg 看到"3/5 段成功"，**下次打开视频那句提示早没了**，
     * 只剩"生成字幕"四个字 ⇒ 用户会一直以为字幕是全的。
     *
     * ⇒ 现在把这个事实**随结果一起传上去**，由调用方决定怎么提示、存不存。
     */
    val incomplete: Boolean = false,
)

private fun videoGenerateLyrics(context: Context) {
    if (videoUri == null) { asrMsg = "请先选择视频"; return }
    val key = AsrPrefs.loadKey(context)
    if (key.isBlank()) { asrMsg = "未配置识别 Key：去设置页填写 ASR Key 后才能自动识别"; return }
    val provider = AsrProvider.fromName(AsrPrefs.loadProvider(context))
    recognizing = true
    asrProgress = 0
    asrMsg = ""
    // 【1003】新一次识别开始 ⇒ 清掉上一轮残留的阶段名，否则 UI 会一直显示"上传识别中"
    asrStage = null
    // 重新生成：先清空旧字幕，逐段识别时再边生成边显示
    // 🔴 【1003 P1】原来只清了 sentences、**没重置 videoSentenceIndexGlobal**
    // ⇒ 暂停在第 150 句时点重新生成，索引还是 150 而列表已空/很短，
    //    进全屏时 sentences[150] 会越界崩溃（VideoPage 那侧也补了 `in indices` 双保险）。
    // 对照：下面 openVideoFromLibrary 的两处清空都是**成对**重置的（:455/:463、:508/:509），只有这里漏了。
    sentences = emptyList()
    videoSentenceIndexGlobal = 0
    clearVideoSentenceRepeat()
    videoAsrScope.launch {
        try {
            val durUs = withContext(Dispatchers.IO) { VideoAudioExtractor.probeDurationUs(context, videoUri!!) }
            val durMs = if (durUs > 0) durUs / 1000 else -1L
            // 探测不到时长时按整段走（保持原行为），能探测且超阈值才分片
            val wholeLimit = wholeLimitMsFor(provider)
            val res = if (durMs > wholeLimit) recognizeBySegments(context, provider, key, durMs)
            else recognizeWhole(context, provider, key)
            // 统一在最后合并短句：分片段边界产生的半截短句也能跨段合并
            val merged = if (res.sentences.isNotEmpty()) mergeShortAsrSentences(res.sentences) else emptyList()
            // 【v96】边界吸附：TeleSpeech 偶发把文本记进下一个 segment 而时间边界没跟着走
            //   （实测：'and as always' 发音在上一句区间、文本在下句、下句 startMs 又跳到 'thank you'）。
            //   把相邻两句的公共边界吸附到最近的句间停顿(≥400ms 长静音)，文本与声音对齐。
            //   吸附目标只用长停顿（换气口不碰）；窗口/重叠保护见 snapBoundariesToSilence。
            val allPoints = res.silenceValleys.map { it.first }
            val gapCenters = res.silenceValleys.filter { it.second >= SENTENCE_GAP_MS }.map { it.first }
            val snapped = if (merged.isNotEmpty() && gapCenters.isNotEmpty())
                snapBoundariesToSilence(merged, gapCenters) else merged
            // 视频断句：用「音轨波形真实静音点」当切点（而非 ASR segment 间隙）。
            // 慢速教学音频 ASR 段少、段间间隙不够长，靠 segment 间隙切不动；波形静音点是真正停顿，
            // 长句(>6s 或 >40 字)能在最近静音点自然切开。取不到静音点再退回 gap 方案兜底。
            val finalSentences = if (snapped.isNotEmpty()) {
                if (allPoints.isNotEmpty())
                    splitLongSentencesByPoints(snapped, allPoints, emptyList<LrcLine>())
                else
                    splitLongSentences(snapped, res.sentences, emptyList<LrcLine>())
            } else emptyList()
            recognizing = false
            asrStage = null   // 【1003】真识别结束，清掉阶段名
            if (finalSentences.isNotEmpty()) {
                sentences = finalSentences
                srtName = "生成字幕"
                // 识别生成的视频字幕 → 归档字幕库（视频来源）
                // 🔴 残缺标记随字幕一起存：下次打开视频靠它提示「字幕缺失」（见 openVideoFromLibrary 的回填）
                SubtitleLibrary.saveVideo(
                    context, videoName, finalSentences, videoPath, incomplete = res.incomplete,
                )
                // 🔴【0929】残缺的字幕必须**明说**：这次的字幕照常显示（用户马上能用），
                //   但提示里要点出"字幕缺失"，别让他以为识别干净了。
                //   原来只报"识别完成，共 N 句（3/5 段成功）"——后半句用户不会当回事，
                //   而且下次打开视频连这句都没了。
                asrMsg = buildString {
                    append("识别完成，共 ${finalSentences.size} 句")
                    if (res.note.isNotBlank()) append("（${res.note}）")
                    if (res.incomplete) append(" ⚠️ 字幕缺失")
                }
                saveVideoPrefs(context)
            } else {
                asrMsg = res.note.ifBlank { "识别返回为空" }
            }
        } catch (t: Throwable) {
            recognizing = false
            asrStage = null   // 【1003】真识别结束，清掉阶段名
            asrMsg = "识别出错：${t.message ?: t.javaClass.simpleName}"
        }
    }
}

/** 进度回调跑在 IO 线程，统一 post 回主线程更新快照状态，避免跨线程写 */
private fun postAsrProgress(p: Int) {
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        asrProgress = p.coerceIn(0, 100)
        // 有进度在动 ⇒ 就是抽音轨阶段。100% 之后再收到进度只可能是收尾，别抢"上传"的话头
        if (asrProgress < 100) asrStage = null
    }
}

/** 设置当前阶段文案（抽音轨有百分比，所以进度一旦 <100 就清掉阶段名，让百分比露出来） */
private fun postAsrStage(s: String?) {
    android.os.Handler(android.os.Looper.getMainLooper()).post { asrStage = s }
}

/** 整段识别（短视频路径）。抽出上限跟着服务商走（见 [segmentMsFor]） */
private suspend fun recognizeWhole(context: Context, provider: AsrProvider, key: String): VideoAsrResult {
    val wav = java.io.File(context.cacheDir, "xuanji_video_asr.wav")
    postAsrStage(null)
    val err = withContext(Dispatchers.IO) {
        VideoAudioExtractor.extractToWav(
            context, videoUri!!, wav.absolutePath,
            // ⚠️ onProgress 必须显式命名：写成尾部 lambda 会被当成 maxBytes 之后的位置参数
            onProgress = { postAsrProgress(it) },
            maxBytes = SpeechRecognizer.maxUploadBytes(provider),
        )
    }
    if (err != null) { runCatching { wav.delete() }; return VideoAsrResult(emptyList(), err, emptyList()) }
    postAsrProgress(100)

    // 静音谷（整条 wav 算一次，全局时间轴）：长句切分、块边界挑选共用
    // 【v95】门槛 350→180ms：快语速素材句间停顿 150~300ms，350 全漏。
    // 【v96】带静音长度：句间停顿(≥400ms)供边界吸附/挑块边界，换气口不碰。
    val silenceValleys = withContext(Dispatchers.IO) { SentenceSplitter.silenceValleysWithLen(wav.absolutePath, minSilenceMs = 180L) }

    // 【v97】超过 3 分钟就沿句间停顿切块、逐块串行识别：
    //   根治模型长音频「文本/时间错位」（and as always 串句案）——模型看哪块识别哪块，文本无法跨块串；
    //   块内时间戳独立，不累积漂移。串行调用（同一时刻只有一个请求，无限流风险）；
    //   每块识别完立即流式追加到 sentences（同 recognizeBySegments 的策略）。
    val wavMs = (wav.length() - 44) / 32
    val gapCenters = silenceValleys.filter { it.second >= SENTENCE_GAP_MS }.map { it.first }
    val blocks = if (wavMs > BLOCK_TARGET_MS) {
        val cuts = chooseBlockCuts(gapCenters, wavMs, BLOCK_TARGET_MS)
        withContext(Dispatchers.IO) { WavConverter.splitWavAtPoints(wav, cuts, java.io.File(context.cacheDir, "xuanji_vblk")) }
    } else listOf(Pair(wav, 0L))

    if (blocks.size == 1) {
        // 原整段路径（短视频）：模型对短音频时间戳可信
        postAsrStage("上传并识别中")
        val outcome = withContext(Dispatchers.IO) { SpeechRecognizer.transcribe(wav.absolutePath, provider, key) }
        postAsrStage(null)
        runCatching { wav.delete() }
        return when (outcome) {
            is AsrOutcome.Timed -> {
                val list = outcome.segments.map { Sentence(it.startMs, it.endMs, it.text.trim()) }.filter { it.text.isNotEmpty() }
                VideoAsrResult(list, "", silenceValleys)
            }
            is AsrOutcome.Plain -> VideoAsrResult(emptyList(), "识别未返回时间戳，建议改用「导入字幕」选 .srt", silenceValleys)
            null -> VideoAsrResult(emptyList(), "识别失败/超时（检查网络与 Key）", silenceValleys)
        }
    }

    // 多块路径（长音频）
    val all = mutableListOf<Sentence>()
    var note = ""
    var consecFail = 0
    var incomplete = false
    for ((i, b) in blocks.withIndex()) {
        postAsrStage("第 ${i + 1}/${blocks.size} 块上传识别中")
        val outcome = withContext(Dispatchers.IO) { SpeechRecognizer.transcribe(b.first.absolutePath, provider, key) }
        runCatching { b.first.delete() }
        if (outcome !is AsrOutcome.Timed) {
            consecFail++
            note = "第 ${i + 1} 块识别失败"
            // 连续两块失败就中止：网络/Key 问题会块块失败，继续烧请求没意义（对齐 recognizeBySegments）
            if (consecFail >= 2) { incomplete = true; break }
            continue
        }
        consecFail = 0
        // 🔴【v97.1 修】不能写 "(blocks[i+1].second - b.second).takeIf { i+1 < size }"——
        //   Kotlin 先求值 receiver 再判条件，最后一块时 blocks[i+1] 直接越界 → 异常被外层
        //   catch 吃掉 → 只剩流式追加的原始 segment（未切分）＝「一大段一大段」。
        val blockSpan = if (i + 1 < blocks.size) blocks[i + 1].second - b.second else wavMs - b.second
        val segSentences = outcome.segments.map {
            // 相对时间戳 clamp 到本块内：防模型幻觉时间越界到下一块
            val s = it.startMs.coerceIn(0, blockSpan)
            val e = it.endMs.coerceAtLeast(s + 1).coerceAtMost(blockSpan)
            Sentence(b.second + s, b.second + e, it.text.trim())
        }.filter { it.text.isNotEmpty() && it.endMs > it.startMs }
        all.addAll(segSentences)
        // 流式展示：每块识别完立即追加到字幕列表，不用等全部识别完
        if (segSentences.isNotEmpty()) sentences = sentences + segSentences
    }
    postAsrStage(null)
    runCatching { wav.delete() }
    if (all.isEmpty()) return VideoAsrResult(emptyList(), note.ifBlank { "识别失败/超时（检查网络与 Key）" }, silenceValleys)
    return VideoAsrResult(all, note, silenceValleys, incomplete = incomplete)
}

/** 【v97】块目标时长 3 分钟：块内模型时间戳可信、块数少（10 分钟视频 = 4 块，串行无限流压力） */
private const val BLOCK_TARGET_MS = 180_000L

/**
 * 【v97】挑块切点：目标块长 [target]，切点优先落在附近最近的句间停顿（不砍词）；
 * ±60s 内没有放宽到 ±90s，再没有就在目标位置硬切（保底；词被劈开只影响该词识别，概率很低）。
 */
private fun chooseBlockCuts(gapCenters: List<Long>, totalMs: Long, target: Long): List<Long> {
    val cuts = mutableListOf<Long>()
    var pos = 0L
    while (totalMs - pos > target * 5 / 4) {   // 尾巴不足一块半就不切了，留给最后一块
        val want = pos + target
        val c = gapCenters.filter { it > pos + target / 2 && it < want + 60_000 }
            .minByOrNull { kotlin.math.abs(it - want) }
            ?: gapCenters.filter { it > pos + 30_000 && it < want + 90_000 }
                .minByOrNull { kotlin.math.abs(it - want) }
        val cut = (c ?: want).coerceIn(pos + 30_000, totalMs - 30_000)
        if (cut <= pos) break
        cuts.add(cut)
        pos = cut
    }
    return cuts
}

/** 分片识别（长视频路径）：逐段抽音轨+上传识别，段内相对时间戳 + 段起始偏移 = 全局时间戳，最后拼接 */
private suspend fun recognizeBySegments(
    context: Context, provider: AsrProvider, key: String, durMs: Long,
): VideoAsrResult {
    // 🔴 段长按服务商的上传上限反推（不是常量）：硅基流动 45MB ⇒ ≈21 分钟/段，Whisper 家 24MB ⇒ ≈11 分钟/段
    val segmentMs = segmentMsFor(provider)
    val maxBytes = SpeechRecognizer.maxUploadBytes(provider)
    val n = ((durMs + segmentMs - 1) / segmentMs).toInt().coerceAtLeast(1)
    val all = mutableListOf<Sentence>()
    val allSilence = mutableListOf<Pair<Long, Long>>()   // 各段音轨波形静音谷（加段偏移后拼成全局）
    var tried = 0
    var okSeg = 0
    var skipped = false   // 有没有段被跳过（零星失败）：有 ⇒ 字幕中间缺了内容
    var consecFail = 0   // 连续失败段数：用来判断是「零星失败」还是「系统性失败」
    for (i in 0 until n) {
        val startMs = i * segmentMs
        val endMs = minOf(startMs + segmentMs, durMs)
        // 末段可能只剩零头（如 20min+1ms 会切出 1ms 的第 3 段），为它跑一次网络识别纯属浪费
        if (endMs - startMs < MIN_SEG_MS) continue
        tried++
        val span = endMs - startMs
        val wav = java.io.File(context.cacheDir, "xuanji_video_seg$i.wav")
        asrMsg = "第 ${i + 1}/$n 段：抽音轨中…"
        postAsrStage(null)
        val err = withContext(Dispatchers.IO) {
            VideoAudioExtractor.extractToWav(
                context, videoUri!!, wav.absolutePath, startMs * 1000, endMs * 1000,
                onProgress = { p -> postAsrProgress(((i * 100) + p) / n) },  // 段内进度折算成全局百分比
                maxBytes = maxBytes,
            )
        }
        if (err != null) { runCatching { wav.delete() }; continue }
        asrMsg = "第 ${i + 1}/$n 段：识别中…"
        // 【1003】本段抽完、进入上传+云端识别时，把该段终点百分比钉住并给阶段名
        // ⇒ 顶栏不再显示一个"永远不动"的数，而是"第 i/n 段 · 上传识别中"。
        postAsrProgress(((i + 1) * 100) / n)
        postAsrStage("第 ${i + 1}/$n 段上传识别中")
        val outcome = withContext(Dispatchers.IO) { SpeechRecognizer.transcribe(wav.absolutePath, provider, key) }
        // 【v95】同 718 行：门槛 350→180ms，快语速素材的长句才切得动
        val segSil = if (outcome is AsrOutcome.Timed) withContext(Dispatchers.IO) { SentenceSplitter.silenceValleysWithLen(wav.absolutePath, minSilenceMs = 180L) } else emptyList()
        postAsrStage(null)
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
            allSilence.addAll(segSil.map { Pair(it.first + startMs, it.second) })
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
                // 🔴 返回的是**已成功的部分** ⇒ 必须标 incomplete，否则半份字幕会被当成完整的存库
                return VideoAsrResult(all, if (hardFail)
                    "第 ${i + 1}/$n 段起识别失败，已中止（检查网络与 Key）"
                else
                    "识别服务未返回时间戳，已中止（建议改用「导入字幕」导入 .srt）", allSilence,
                    incomplete = all.isNotEmpty())
            }
            // 前面成功过、且这次只失败了一段 → 当成零星失败，跳过继续跑后面的段
            skipped = true   // 但这段的字幕确实缺了，最后要标记残缺
        }
        // 本段收尾：阶段名已在上面清掉，这里只把全局百分比推到位
        postAsrProgress(((i + 1) * 100) / n)
    }
    return VideoAsrResult(
        all,
        if (okSeg > 0) "$okSeg/$tried 段成功" else "识别失败：检查网络与 Key",
        allSilence,
        // 有段被跳过（零星失败）⇒ 字幕中间是缺的；全段成功才是完整的
        incomplete = all.isNotEmpty() && skipped,
    )
}

// 视频播放器放在文件级（进程常驻，Activity 重建/回前台不丢），
// 配合下方 loadVideo 的「同 uri 复用」分支：回来只重新接画面，绝不重建、绝不 seek。
// 【v119】内核由框架 MediaPlayer 换成 **ExoPlayer**（MKV 里的 AC3/EAC3 才认得出）。
//   ⚠️ 变量名沿用 `videoMpRef`（全工程 50+ 处调用点），但类型已是 ExoPlayer，别再按 MediaPlayer 读。
internal val videoMpRef = arrayOfNulls<ExoPlayer?>(1)
internal var videoMpSourceUri: Uri? = null

/**
 * 字号缩放的「天花板」——超过它就一律按 [MAX_FONT_SCALE] 渲染（当前 1.5）。
 *
 * 【v48 · 2026-09-19 阿亮拍板】起因：全 App **没有任何自适应机制**
 * （全仓 grep `fontScale` / `AutoSize` / `TextAutoSize` 零命中），字号一大就只能靠当初怎么写。
 * 实测（模拟器 5 档，证据 `截图/2026-09-19-字号自适应-*.png`）：
 *   · `maxLines = 1` 的文字 ⇒ 1.6 起被截成「…」；
 *   · **固定 dp 高**的盒子 ⇒ 2.0 时底栏「音频模式」折两行、**第二行被屏幕裁掉**；
 *   · **固定 dp 宽**的字位 ⇒ 2.0 时生词本「美/英」被压扁、和后面音标粘在一起。
 * ⇒ 方案只做「锁天花板」这一条（不做逐个改盒子）。代价照实说：开无障碍超大字的用户，
 *   在我们 App 里享受不到那么大的字 —— 但界面不会坏。
 *
 * 🔴 用 `coerceAtMost` 而**不是** `coerceIn`：只有"放大"会撑坏布局，"缩小"不会；
 *    设下限等于替用户改设置，别做。
 * 🔴 天花板取 **1.5**（0919 阿亮定的口径）：
 *    他两台手机，一台 1.3、**另一台 145%**。取 1.5 = "比他手上最大的那台再高一点点"，
 *    意思是「**1.5 倍以内一律保证显示全**，再大的（无障碍超大字体）就不管了」。
 *    ⚠️ v48 曾取 1.3 ⇒ 他那台 145% 的机器字体被压小，"比原来小好多"，他当场否掉。
 *    **教训：天花板不能低于用户自己正在用的档位**，否则等于替用户把字改小。
 *    ⚠️ 改这个数 ⇒ 必须重跑 1.0 / 1.3 / 1.5 / 2.0 截图验收，且**模拟器要调窄到真机宽度**
 *    （见下面"验证坑"）。
 *
 * 🔴🔴 **验证坑（v48 栽过，必读）**：模拟器默认 1080x2400 @440dpi ≈ **392dp 逻辑宽**，
 *   而真机常见 **360dp**（`wm density 480`）。差这 33dp，设置页的说明文字就是"放得下 / 被截成「…」"的区别。
 *   ⇒ **验收窄屏文案必须先 `adb shell wm density 480` 把模拟器调窄**，否则会得出"1.3 没问题"的假结论。
 *   验完记得 `wm density reset`。
 *
 * ⚠️ **只覆盖 Compose 画的界面**。`FloatLyricsService` 的悬浮窗是另一个 Window，
 *    不一定走这棵组合树 ⇒ 它仍按系统 fontScale 渲染，**这里管不到**。
 */
private const val MAX_FONT_SCALE = 1.5f

/**
 * 全 App 的主题包装（替代直接用 `MaterialTheme`）。
 * 除了套主题，唯一额外的事就是**把 `LocalDensity` 的 `fontScale` 钳到 [MAX_FONT_SCALE]** ——
 * `sp` 的 px 换算走的就是它，所以钳在这里 = 全 App 的 `sp` 字号一起被限高。
 * `dp` 不受影响（它只看 `density`，不看 `fontScale`）⇒ 间距、图标大小照旧。
 */
@Composable
private fun XuanjiTheme(colorScheme: ColorScheme, content: @Composable () -> Unit) {
    val system = LocalDensity.current
    val appDensity = remember(system.density, system.fontScale) {
        if (system.fontScale <= MAX_FONT_SCALE) system
        else Density(system.density, MAX_FONT_SCALE)
    }
    CompositionLocalProvider(LocalDensity provides appDensity) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}

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
        // 0917 生词提醒：点通知时 App 还活着（singleTask）= 走这条，直接跳复习页。
        // 冷启那条走 onCreate 的 pendingGotoReview（那时 biliNavigate 还没赋值）。
        if (intent.getBooleanExtra(VocabReminder.EXTRA_GOTO_REVIEW, false)) {
            biliNavigate?.invoke(Page.REVIEW)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashGuard()   // 越早装越好：之后所有主线程异常都能被兜住，而不是直接闪退
        logLastCrash(this)    // 上次要是闪退过，把堆栈打进日志
        // 0917 生词提醒：点通知冷启进来 → 挂个请求，等 AppRoot 组合时消费（那时才能改 page）
        if (intent?.getBooleanExtra(VocabReminder.EXTRA_GOTO_REVIEW, false) == true) {
            pendingGotoReview = true
        }
        // 【0915】把应用级 Context 挂到会话对象上：文件级顶层函数（如 videoJumpSentence）拿不到
        // LocalContext，但「上一句复读」要读音频模式的 RepeatPrefs 重播次数。只存 Application Context，不泄漏。
        PlaybackSession.attachContext(this)
        // 0917 生词提醒自愈：开关开着但闹钟被清掉了 ⇒ 重排。
        // 🔴【0922】这一句现在是**真的救命**：MIUI 把"从最近任务划掉卡片"做成了 force-stop
        //   （`stopped=true`）⇒ 系统闹钟被清空。而 AlarmManager **没有公开 API** 能问"我的闹钟还在不在"，
        //   所以只能靠"每次启动都按同一个到期时刻幂等重排"来兜底。
        //   幂等是关键：App 开一百次也不会把倒计时往后顶。细节见 VocabReminder.healIfNeeded。
        VocabReminder.healIfNeeded(this)
        startForegroundService(Intent(this, PlayerService::class.java))
        bindService(Intent(this, PlayerService::class.java), connection, Context.BIND_AUTO_CREATE)
        // 恢复悬浮窗：上次开启过则自动拉起（应用在前台，可安全 startService）
        if (FloatPrefs.load(this) && Settings.canDrawOverlays(this)) {
            startService(Intent(this, FloatLyricsService::class.java))
        }
        setContent {
            val context = LocalContext.current
            // 收集抓 B站 进度/完成事件（进程内 SharedFlow，规避 Android 14 隐式广播投递限制）
            // 🔴 【1003 P1】**刻意拆成两个协程分别订阅** progress / done（不能 merge 成一条，
            // 理由见 BiliImportBus 注释）—— done 那条 replay=1，抓取中 Activity 被销毁也不丢结果。
            LaunchedEffect(Unit) {
                launch {
                    BiliImportBus.progress.collect { event ->
                        if (event is BiliImportEvent.Progress) biliStatus = event.text
                    }
                }
                launch {
                    BiliImportBus.done.collect { event ->
                        if (event !is BiliImportEvent.Done) return@collect
                        // 🔴 【1003 P1】Done 现在走 replay=1 的通道（见 BiliImportBus 注释）：
                        // 抓取中 Activity 被销毁也不会丢结果。**处理完必须清 replay 缓存**，
                        // 否则下次进 App 会又收到上一次的老结果、莫名自动打开一个视频。
                        BiliImportBus.consumeLatestDone()
                        // 【0930 修】原来「成功」这条路只顾着开视频、**没有清 biliStatus**
                        // ⇒ "下载视频…"永远挂在控制排下面（阿亮截图实锤：视频都能播完了，那行字还在，
                        // 白占一行高度）。成功/失败都必须收掉状态文字。
                        biliStatus = ""
                        if (!event.ok) {
                            biliError = event.error ?: "抓取失败"
                            return@collect
                        }
                        // 【1002 修⑤】成功 = 上一次的失败已经翻篇 ⇒ 旧红字一并清掉，
                        // 不然"抓取失败"会一直挂着直到下次导入。
                        biliError = ""
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
            var themeIndex by remember { mutableStateOf(ThemePrefs.loadIndex(context)) }
            var darkMode by remember { mutableStateOf(ThemePrefs.loadDark(context)) }
            var splashDone by remember { mutableStateOf(appSplashShownThisProcess) }
            val colorScheme = remember(themeIndex, darkMode) { xuanjiColorScheme(themeIndex, darkMode) }
            // 【v48】原来这里直接是 `MaterialTheme(...)`。换成 `XuanjiTheme` 只为多一层
            // 「字号天花板」（见文件上方 MAX_FONT_SCALE 注释）；主题行为一字未改。
            XuanjiTheme(colorScheme = colorScheme) {
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
            // 切后台【不主动摘画面】（v122 起对 SurfaceView 亦然）：
            // ① API 34+ 的 SURFACE_LIFECYCLE_FOLLOWS_ATTACHMENT 让 Surface 跟挂载走，切后台系统根本
            //    不销毁它，什么都不会发生；
            // ② 万一系统真销毁了（API 13 及以下 / 系统回收），则由 surfaceDestroyed 回调去摘 —— 那是
            //    "Surface 已死不得不摘"，跟这里"没事先摘掉"是两回事。主动摘只会平白多一次重接。
            // ③ 保持表面常驻，声音继续播放。
            Log.d("XJ_VID", "onPause cur=${videoMpRef[0]?.currentPosition} KEEP surface; audio keeps playing")
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

    // 回前台：v122 起用 SurfaceView。
    // ① API 34+：FOLLOWS_ATTACHMENT ⇒ 切后台 Surface 没销毁，本方法里的 bindVideoSurface 会被幂等
    //    判定 SKIP 掉，什么都不做，画面本来就还在 ⇒ 不闪不卡；
    // ② API 13 及以下：Surface 被销毁重建过，surfaceCreated 已经接过新 Surface，这里同样 SKIP；
    // ③ 无论哪条路都不 seek、不重建播放器、不 flush 解码器 ⇒ 不会「重播当句」。
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
        // 进文件夹页前记住来源模式：退出文件夹时回到进入时的模式（需求3）
        var folderReturnPage by remember { mutableStateOf(Page.PLAYER) }
        var layoutEdit by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        // 把 page 的写权限暴露给文件级广播接收器：前台服务抓完 B站 自动导航到视频/复读机。
        // 视频模式进文件夹→文件夹默认看视频栏、顶栏显示「视频选集」（0904 修 Bug C）
        // ⚠️ 这个赋值必须留在 showSettings 声明**之后** —— 下面要顺手关它。
        biliNavigate = { p ->
            if (p == Page.FOLDER && page == Page.VIDEO) folderInitType = MediaType.VIDEO
            page = p
            // 0917 生词提醒：点通知跳复习时顺手把设置页关掉。
            // 不关的话设置页会盖在复习页上 —— 用户看见的就是「点了通知没反应」
            // （模拟器实测过：复习页确实跳过去了，只是被设置页挡着）。
            if (p == Page.REVIEW) showSettings = false
        }
        var floatOn by remember { mutableStateOf(FloatPrefs.load(context)) }
        var resumeOn by remember { mutableStateOf(PlaybackPrefs.loadResume(context)) }
        // 选集页滚动位置：提升到 Activity 层，页面切换（组合销毁/重建）后仍保留。
        // rememberSaveable 在这里有效：Activity 重建/旋转时也恢复。
        val trackListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
        // 【v89 阿亮："滑到哪里，返回来还在哪里"】各列表滚动位置 / 选集导航层级同样提到 Activity 层。
        // 病根：when(page) 切页 = 页面组合销毁，页面内部 remember 的滚动状态跟着没了，回来就从顶部开始。
        // 药方与 trackListState 同款。🔴 带自动跟随当前句的列表（视频字幕列表、视频源阅读正文）**不动**，
        // 仍由页面内部管理 —— 跟随优先，位置记忆反而添乱（阿亮 1005 定）。
        val folderOverviewListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
        val folderFileListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
        val readingListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
        val vocabListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
        val settingsScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
        val statsScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
        // 选集「在哪个文件夹里 / 搜索词」：普通 remember 就够 —— 只需要活过 when 分支切换；
        // 用 mutableStateOf 包一层传给 FolderPage，页面销毁重建后导航层级不丢。
        // （不保 mediaType tab：FolderPage 首组合的 LaunchedEffect(initialType) 会按入口重置，保持原行为。）
        val folderCurrent = remember { mutableStateOf<FolderItem?>(null) }
        val folderQueryState = remember { mutableStateOf("") }

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
        // 【0918 修·阿亮"新版本前两次启动记不住播放进度"】
        // 上面 LaunchedEffect(svc) 里的 restorePlayback() 跑在权限申请**之前**，
        // 那一刻媒体权限还没拿到，AudioScanner 扫不出任何音频 ⇒ 恢复必然失败。
        // 旧代码这里回调是空的，等于白白浪费掉这一轮；拿到权限后补一次，让它有机会真正恢复。
        // restorePlayback 自带「已有播放内容就跳过」的守卫，用户已经手动开始播放时不会被打断。
        val permLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { granted ->
            if (granted.values.any { it }) PlayerService.instance?.restorePlayback()
        }

        // 权限请求必须等组合完成后再发起（launcher 需要先完成初始化），
        // 否则在组合函数体内直接 launch() 会抛 "Launcher has not been initialized"
        LaunchedEffect(Unit) {
            if (missingPerms.isNotEmpty()) permLauncher.launch(missingPerms.toTypedArray())
        }

        // 启动即恢复上次现场：视频 Uri/进度/字幕 + 退出时所处的页面与播放状态，
        // 让冷启/媒体中心返回能回到上次模式（视频/阅读/音频），并尊重暂停/播放状态（而非总回音频）。
        LaunchedEffect(Unit) {
            loadVideoPrefs(context)
            // 0917 生词提醒：点通知冷启进来的，**优先**落复习页 ——
            // 必须 return 掉，否则下面按 sessionPage 恢复（可能跳回视频/阅读）会把复习页盖掉。
            if (pendingGotoReview) {
                pendingGotoReview = false
                page = Page.REVIEW
                return@LaunchedEffect
            }
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
        // 🔴 存的是 mainModeOf(page)：**子页面不能原样存**，否则下次启动认不出来（见 mainModeOf 的说明）。
        LaunchedEffect(page) {
            sessionPage = mainModeOf(page, folderReturnPage)
            saveVideoPrefs(context)
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

        // 视频当前句 → 悬浮窗（0917 阿亮要求"视频下的字幕也能用悬浮窗播"）
        //
        // 【为什么必须有这段】视频的字幕只活在 MainActivity 的变量里（自建 ExoPlayer + SurfaceView，
        // 绑在 TextureView->SurfaceView 的 View 上、搬不进 Service），而悬浮窗是个独立 Service ⇒ 它够不着。
        // 这里把「当前句文本」推给 PlaybackSession.floatText，悬浮窗去读那一条。
        //
        // 【为什么用 snapshotFlow 而不是在写入点各补一行】videoSentenceIndexGlobal 有 6 个写入点
        // （加载重置 / 点句 / seek / 播放轮询…），抄漏一处就会出现"某条路径切句后悬浮窗不跟"。
        // 监听一次覆盖全部。**带上 activeKind 是必要的**：切换模式时当前句没变、但得重推一次，
        // 否则从音频切到视频，悬浮窗还念着上一路的文本。
        LaunchedEffect(Unit) {
            combine(
                PlaybackSession.activeKind,
                snapshotFlow { videoSentenceIndexGlobal to sentences },
            ) { kind, (idx, list) -> kind to list.getOrNull(idx)?.text.orEmpty() }
                .collect { (kind, text) ->
                    // 只在视频是真值来源时推：音频模式别拿视频的残留文本把悬浮窗带偏
                    if (kind == MediaKind.VIDEO) PlaybackSession.setFloatText(text)
                }
        }

        Surface(modifier = Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
                // 顶栏：左设置、中「正在学」选集下拉、右生成歌词/文件夹
                //
                // 🔴【0917 阿亮】生词本 / 复习页**不要顶栏** —— 那三个按钮（阅读选集 / 生成字幕 / 文件夹）
                // 对这两页都没意义，留着纯占地方、还容易误点。
                // 他原话："进到生词本之后还显示顶栏，我感觉好像没必要了吧…这几个都感觉就没用了，
                // 就给它弄掉，底栏可以保留。"
                // ⚠️ 这里**不是**"遮挡"也不是另一套界面 —— 就是同一个 Column 里的第一行，
                //    隐藏后内容会直接顶到状态栏底下 ⇒ 下面 Box 那处必须补 statusBarsPadding，
                //    不然返回箭头会被状态栏压住（视频全屏那条分支**不加**，全屏就是要铺满）。
                val hideTopBar = (page == Page.VIDEO && videoFullscreen) ||
                    page == Page.VOCAB || page == Page.REVIEW
                if (!hideTopBar) Box(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().height(52.dp)
                ) {
                    // 【1007 顶栏三层布局】阿亮：定时按钮钉死屏幕正中间（对准摄像头位置），
                    // 其他按钮以它为中心分两组贴两边 —— 左组「设置+选集」贴左、右组「生成字幕+文件夹」贴右。
                    // 以前是单 Row + 两个 weight Spacer：闹钟只在「选集右缘↔生成字幕左缘」之间居中，
                    // 右边「识别中 87%」文字一变宽就把它挤歪。Box 叠加后三组互不推挤。
                    // ---- 左组：设置 + 选集（贴左） ----
                    Row(
                        modifier = Modifier.align(Alignment.CenterStart),
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
                    // 【1007 顶栏】阿亮：选集往左挪、贴着设置键（原来有 6dp 外距嫌远）。
                    // 去掉 start 外距，只在右侧留 6dp 与中间定时按钮的呼吸空隙。
                    modifier = Modifier.padding(end = 6.dp),
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
                }
                    // ---- 中间：定时按钮（绝对居中，正对屏幕中心/摄像头） ----
                    // 【1007 睡眠定时】点一下弹 AppDropdownMenu 选 15/30/45/60/90 分钟；倒计时中按钮
                    // 直接显示剩余时间（阿亮："显示在按钮上，小一点，不是重要信息不会老看"）。到点暂停。
                    val sleepEndsAt by SleepTimer.endsAt.collectAsStateWithLifecycle()
                    val sleepTick by SleepTimer.tick.collectAsStateWithLifecycle()
                    var sleepMenu by remember { mutableStateOf(false) }
                    val sleepRemainMs = (sleepEndsAt - System.currentTimeMillis()).coerceAtLeast(0L)
                    val sleepRemainText = if (sleepEndsAt > 0L && sleepRemainMs > 0L) {
                        // sleepTick 只为驱动每秒重组，显示值由实时时间算出
                        @Suppress("UNUSED_EXPRESSION") sleepTick
                        String.format("%d:%02d", sleepRemainMs / 60_000, sleepRemainMs / 1000 % 60)
                    } else null
                    Box(modifier = Modifier.align(Alignment.Center)) {
                    Surface(
                        onClick = { sleepMenu = true },
                        shape = RoundedCornerShape(16.dp),
                        color = if (sleepRemainText != null) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Timer,
                                contentDescription = "定时停止",
                                modifier = Modifier.size(15.dp),
                                tint = if (sleepRemainText != null) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (sleepRemainText != null) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = sleepRemainText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    AppDropdownMenu(
                        expanded = sleepMenu,
                        onDismissRequest = { sleepMenu = false },
                    ) {
                        listOf(15L, 30L, 45L, 60L, 90L).forEach { m ->
                            DropdownMenuItem(
                                text = { Text("$m 分钟后暂停") },
                                onClick = {
                                    SleepTimer.start(m)
                                    sleepMenu = false
                                },
                            )
                        }
                        // 【1007 修】取消定时常显：原来只在倒计时中才显示，阿亮反馈"选项里没有取消定时"
                        DropdownMenuItem(
                            text = { Text(if (sleepEndsAt > 0L) "取消定时" else "取消定时（未在倒计时）") },
                            onClick = {
                                SleepTimer.cancel()
                                sleepMenu = false
                            },
                        )
                    }
                    }
                    // ---- 右组：生成字幕/重新生成 + 文件夹（贴右） ----
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                // Phase3-步骤2：改用「当前播放源」唯一真值判定。
                // 原先写死 page == Page.VIDEO，漏了「从视频进阅读/生词本」→ 按钮错判成音频。
                val isVideoMode = activeKind == MediaKind.VIDEO
                val genActive = if (isVideoMode) recognizing else state.recognizing
                val genDisabled = if (isVideoMode) (videoUri == null) else (state.currentItem == null)
                // 【1005 阿亮】以前生成过字幕的，按钮变成「重新生成」，一眼知道能重跑
                val hasSubs = if (isVideoMode) sentences.isNotEmpty() else state.lyrics.isNotEmpty()
                Surface(
                    onClick = {
                        if (isVideoMode) { if (!recognizing) videoGenerateLyrics(context) }
                        else { if (!state.recognizing) PlayerService.instance?.generateLyrics() }
                    },
                    enabled = !genActive && !genDisabled,
                    shape = RoundedCornerShape(16.dp),
                    color = if (genActive || genDisabled) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.primaryContainer,
                    // 【1007 顶栏对称】选集按钮有 padding(horizontal=6.dp)，生成字幕/重新生成原来没有
                    // ⇒ 左边「设置→选集」间隙 18dp、右边「生成字幕→文件夹」只有 12dp，看着歪。
                    // 补上同样的 6dp，两边间隙一致（18dp）。
                    modifier = Modifier.padding(horizontal = 6.dp),
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
                            !genActive -> if (hasSubs) "重新生成" else "生成字幕"
                                // 【1010 阿亮】视频模式：识别进度**只在画面正中那颗胶囊里**露
                                // （带 % 和阶段名，见 VideoPage 的 asrOverlayText），顶栏按钮只留「识别中」。
                                // 原因：asrStage 文案形如"第 3/5 块上传识别中"，一长就把按钮顶宽 ——
                                // 顶栏是 Box 三层叠加布局（见上方 1007 注释），右组变宽只会往左长，
                                // 直接压到中间钉死的定时按钮上。
                                // ⚠️ asrProgress / asrStage 都是**视频识别链路专用**；音频走 state.recognizeProgress，
                                //    下面那条不动，音频页照旧显示自己的进度。
                                isVideoMode -> "识别中"
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
                }

                // 内容区（占据剩余空间）
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .weight(1f)
                        // 生词本 / 复习页把顶栏藏了（见上面 hideTopBar），内容得自己避开状态栏，
                        // 否则顶部的返回箭头会钻到状态栏下面。视频全屏不在此列 —— 全屏本来就要铺满。
                        .then(
                            if (page == Page.VOCAB || page == Page.REVIEW) Modifier.statusBarsPadding()
                            else Modifier
                        )
                ) {
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
                            // 【v89】正文句子列表滚动状态由 Activity 层持有（音频源阅读切页不再跳顶）；
                            // 视频源阅读的「定位当前句」LaunchedEffect 保持原样，跟随行为不变。
                            listState = readingListState,
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
                            listState = vocabListState,
                            statsScrollState = statsScrollState,
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
                            // 【v89】滚动位置 + 导航层级由 Activity 层持有：进出选集不再跳顶
                            folderOverviewListState = folderOverviewListState,
                            folderFileListState = folderFileListState,
                            folderCurrent = folderCurrent,
                            folderQueryState = folderQueryState,
                            onOpenVideo = { item, siblings ->
                                // 选中视频 → 自动跳视频模式开播（音频那边同理，选音频回播放页）
                                // siblings = 同级视频列表，供上一曲/下一曲在文件夹内切换
                                openVideoFromLibrary(context, item, siblings)
                                page = Page.VIDEO
                            },
                            // 【1007 统一】选中音频 → 自动跳音频模式开播（与上面选视频对称）。
                            // 原来点音频走「setPlaylist + 原路返回」：视频模式进文件夹选音频时，
                            // 页面被切回视频页、视频画面没变 = "选了没反应/跳回原来的视频"。
                            // 音频真正开始发声后 activeKind 自动置 AUDIO，顶栏/底栏会跟着切过来。
                            onOpenAudio = { item, siblings ->
                                // 视频正在响先暂停（视频是自建 ExoPlayer，不会自己停）→ 防双声
                                if (videoPlaying) videoTogglePlay()
                                svc?.setPlaylist(siblings, siblings.indexOf(item))
                                videoSelectJump = false
                                folderReturnPage = Page.PLAYER
                                page = Page.PLAYER
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
                // 【v89】设置页滚动位置 Activity 层持有：关掉重开不再跳顶
                scrollState = settingsScrollState,
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
