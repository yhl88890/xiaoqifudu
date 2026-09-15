package com.xuanji.repeater

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.util.Log
import android.os.Binder
import android.os.Build
import android.os.IBinder
import java.io.File
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 播放内核：
 * - MediaPlayer 本地播放（无第三方依赖，体积小）
 * - MediaSession + 前台服务通知（锁屏/通知栏控制）
 * - 三种播放模式：顺序 / 列表循环 / 单曲循环
 * - 句子粒度播放（复读）：按 Sentence 区间播放，到 endMs 停止或自动下一句
 */
/**
 * 视频播放的「外部控制器」：视频的 MediaPlayer 实际在 MainActivity 里（VideoView 内部），
 * 但系统媒体中心/音频焦点统一由本 PlayerService 的 MediaSession + 前台通知承载。
 * 视频开始播放时 MainActivity 把这套控制器塞进来，本服务的 MediaSession 回调/音频焦点
 * 就转发到视频；切走视频模式时 detach，恢复成纯音频控制。
 */
interface ExternalMediaController {
    fun play()
    fun pause()
    fun isPlaying(): Boolean
    fun position(): Long
    fun duration(): Long
    fun seekTo(ms: Long)
    fun skipNext()
    fun skipPrev()
    fun title(): String
}

class PlayerService : Service() {

    companion object {
        const val CHANNEL_ID = "playback"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY_PAUSE = "com.xuanji.repeater.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.xuanji.repeater.action.NEXT"
        const val ACTION_PREV = "com.xuanji.repeater.action.PREV"
        const val ACTION_STOP = "com.xuanji.repeater.action.STOP"

        /** 当前服务实例（单进程内供悬浮窗等组件读取状态） */
        @Volatile
        var instance: PlayerService? = null

        /** 距句尾小于该值时进入高精度轮询（10ms），避免复读跳回时带过下一句开头 */
        const val HIGH_PRECISION_WINDOW = 300L
        /** 句尾触发补偿量：MediaPlayer.currentPosition 是解码写入位置，领先耳朵实际听到的
         *  位置一个输出缓冲（AudioTrack 缓冲 + AudioFlinger mixer，通常 50~200ms，低端机更高）。
         *  截断点取 endMs+150，pause→seek 会 flush 缓冲，耳朵恰好停在句尾——不砍尾、不带过。 */
        const val SEEK_AHEAD_MS = 150L
        /** 短句合并阈值：句长低于此值（ms）与相邻句合并，避免复读太碎 */
        const val SHORT_SENTENCE_MS = 2500L
        /** 长句切分阈值：句长超过此值（ms）或字符数超过 LONG_SENTENCE_CHARS，在逗号处切分 */
        const val LONG_SENTENCE_MS = 6000L
        const val LONG_SENTENCE_CHARS = 40
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private var mediaSession: MediaSession? = null
    private var positionTicker: Job? = null
    private var splitJob: Job? = null
    private var pendingSentenceStart: Long = -1L
    private var pendingSentenceEnd: Long = -1L
    private var pendingSentenceIndex: Int = -1
    /** 剩余复读次数（上一句复读3遍用）：句尾时跳回句首，为 0 后自然继续播放 */
    private var pendingRepeatLeft: Int = 0
    /** 句尾 seek 进行中标志：防止 seek 完成前 ticker 重复触发边界处理（会多扣复读次数/重复 seek） */
    private var inBoundarySeek = false
    /** 前台通知是否已挂载（停止后重新播放需重新挂载） */
    private var foregroundActive = false
    /** MediaPlayer 是否已完成 prepare（prepareAsync 是异步的）。
     *  未就绪时 playSentence 的 seek/pause 会错位或抛异常，故未就绪先记下待播句子，
     *  等 onPrepared 触发后再真正 seek+播放，避免「重启后点歌词播错句/状态不同步」。 */
    private var playerPrepared = false
    private var pendingPlaySentenceIndex = -1
    private var pendingPlaySentenceStopAtEnd = false
    private var pendingPlaySentenceTimes = 1
    /** 待播句归属的音频路径：只有切到「另一首歌」时才该丢弃待播句。
     *  过去 loadAndPlay 无条件清空 pending，导致「恢复现场时点歌词 → 待播句被清 → 改播整曲（上次关闭的位置）」。 */
    private var pendingPlaySentencePath: String? = null
    /** 待播句看门狗：句子迟迟不到（识别失败/缓存异常）时清掉待播句，
     *  防止 pending 永久挂起把播放/暂停等控制逻辑一直挡住。 */
    private var pendingPlayWatchdog: Runnable? = null
    private val mainHandler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }

    // ---------------- 音频焦点 ----------------
    // 来电 / 打开别的音视频 App / 微信语音 等抢占音频输出时，系统会收回焦点，
    // 我们据此自动暂停播放；对方释放后如本来在播则自动续播（仅限"被抢占"暂停，用户主动暂停不续播）。
    private var audioManager: AudioManager? = null
    private lateinit var afChangeListener: AudioManager.OnAudioFocusChangeListener
    private var focusRequest: AudioFocusRequest? = null
    /** 是否因「失去焦点」而暂停（与用户主动暂停区分：前者在重新获得焦点时自动续播） */
    private var pausedByFocusLoss = false

    // ---------------- 视频外部控制器 ----------------
    // 视频播放时由 MainActivity 注入；为 null 表示当前由音频模式掌控 MediaSession/通知/焦点。
    private var videoController: ExternalMediaController? = null
    private var videoActive = false



    /** 标记上次活跃的媒体类型，避免重开时音频服务误续播（视频模式下不应自动播音频） */
    private fun markLastMode(mode: String) {
        runCatching { getSharedPreferences("xuanji_mode", Context.MODE_PRIVATE).edit().putString("last", mode).apply() }
    }
    private fun lastMode(): String =
        runCatching { getSharedPreferences("xuanji_mode", Context.MODE_PRIVATE).getString("last", "") ?: "" }.getOrElse { "" }

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): PlayerService = this@PlayerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannel()
        setupAudioFocus()
        setupMediaSession()
        startForeground(NOTIFICATION_ID, buildNotification())
        foregroundActive = true
    }

    override fun onDestroy() {
        savePlayback()
        abandonAudioFocus()
        instance = null
        positionTicker?.cancel()
        splitJob?.cancel()
        player?.release()
        player = null
        mediaSession?.release()
        mediaSession = null
        videoController = null
        videoActive = false
        serviceScope.cancel()
        super.onDestroy()
    }

    /** 用户从最近任务划掉应用（后台彻底退出）时保存播放现场。
     *  单播放器架构：视频 MediaPlayer 现由 MainActivity 持有（切后台只 setSurface(null) 保声音），
     *  本服务不再另持视频播放器；videoActive 仅在视频接管媒体中心时为 true。 */
    override fun onTaskRemoved(rootIntent: Intent?) {
        savePlayback()
        super.onTaskRemoved(rootIntent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_NEXT -> next()
            ACTION_PREV -> prev()
            ACTION_STOP -> {
                pause()
                stopForeground(STOP_FOREGROUND_REMOVE)
                foregroundActive = false
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    // ---------------- 对外控制 API ----------------

    fun setPlaylist(items: List<AudioItem>, startIndex: Int, autoPlay: Boolean = true) {
        if (items.isEmpty()) return
        _state.value = _state.value.copy(
            playlist = items,
            currentIndex = startIndex.coerceIn(0, items.size - 1),
            currentItem = items[startIndex.coerceIn(0, items.size - 1)],
            sentences = emptyList(),
            currentSentenceIndex = -1,
            waveform = FloatArray(0),
            lyrics = emptyList(),
            splitting = false,
            splitProgress = -1f,
        )
        loadAndPlay(_state.value.currentItem!!, autoPlay)
    }

    fun setSentences(sentences: List<Sentence>) {
        _state.value = _state.value.copy(sentences = sentences, currentSentenceIndex = -1)
        playPendingSentenceIfReady()
    }

    fun togglePlayPause() {
        val s = _state.value
        if (s.currentItem == null) return
        val p = player
        when {
            // 播放器还没建：直接按当前进度加载并播放
            p == null -> loadAndPlay(s.currentItem, autoPlay = true, initialPositionMs = s.positionMs)
            // 正在准备中（prepareAsync 尚未完成、player 处于 Preparing 状态）：
            // 此时直接 p.start()/p.pause() 会抛 IllegalStateException 被吞，导致按钮「点了没反应」。
            // 若当前还没在播，则重建为 autoPlay=true，确保准备完成后真正开始；
            // 若已在播（准备中即将自动播），什么都不做，等 onPrepared 处理。
            !playerPrepared -> {
                // 过去这里遇到待播句（pending）会直接 return —— 一旦待播句迟迟不就绪，
                // 播放/暂停键就永久失灵（只能靠切歌救回），正是「界面冻住、只有上下曲管用」的一环。
                // 现在 loadAndPlay 会保留同一首歌的待播句：onPrepared 时若待播句已就绪优先播该句，
                // 否则先按当前进度播、句子到位后再由补播机制跳到用户点的那句。
                if (!s.isPlaying) {
                    loadAndPlay(s.currentItem, autoPlay = true, initialPositionMs = s.positionMs)
                }
            }
            // 以真实 player 状态为准，防止 state 与实际错位导致按钮失灵。
            p.isPlaying -> pause()
            else -> play()
        }
    }

    fun play() {
        markLastMode("audio")   // 音频开始播放：记 lastMode，避免下次重开误判为视频而吞掉音频续播
        // MediaPlayer 的 start/pause/seekTo 必须在主线程调用，否则跨线程会破坏其内部状态，
        // 表现为「点歌词后界面冻住、播放/暂停/上下句全失灵、只有切歌能救」。
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post { play() }
            return
        }
        // 开始/恢复播放前抢回音频焦点（来电等抢占后也能重新拿到），否则系统可能继续把声音路由给别人
        requestAudioFocus()
        val p = player ?: return
        // 不用 !playerPrepared 守门：准备中调 start() 会抛异常（被下方 try/catch 吞掉，不崩），
        // 但若 playerPrepared 因故卡 false 会让所有控制永久失效（只能切歌救回）。
        val s = _state.value
        // 停止后重新播放：重新挂载前台通知（否则 Android 8+ 会限制后台播放）
        if (!foregroundActive) {
            startForeground(NOTIFICATION_ID, buildNotification())
            foregroundActive = true
        }
        if (!p.isPlaying) {
            try {
                p.start()
                applySpeed(p, s.speed)
            } catch (_: Exception) {
                // 极少数仍未准备好的情况：不抛异常、不更新 isPlaying，等下次再点
                return
            }
        }
        _state.value = s.copy(isPlaying = true)
        PlaybackSession.onAudioStarted()   // Phase3-步骤1：记「当前播放源=音频」（只记录，不参与判定）
        startTicker()
        updateMediaSession()
        updateNotification()
    }

    fun pause() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post { pause() }
            return
        }
        // 用户主动暂停：释放音频焦点，让别的音视频 App 能正常出声
        abandonAudioFocus()
        val p = player ?: return
        if (p.isPlaying) {
            try {
                p.pause()
            } catch (_: Exception) {
                // 忽略：状态仍按已暂停处理
            }
        }
        _state.value = _state.value.copy(isPlaying = false)
        stopTicker()
        savePlayback()
        updateMediaSession()
        updateNotification()
    }

    fun seekTo(ms: Long) {
        val p = player ?: return
        // 用户手动拖进度条 = 明确放弃之前点的待播句，否则句子到位后会被补播抢走位置
        clearPendingPlaySentence()
        p.seekTo(ms.coerceAtLeast(0L).toInt())
        pendingSentenceStart = -1L
        pendingSentenceEnd = -1L
        pendingSentenceIndex = -1
        pendingRepeatLeft = 0
        // 拖进度条退出句子模式：歌词高亮按新位置定位
        _state.value = _state.value.copy(positionMs = ms.coerceAtLeast(0L), currentSentenceIndex = -1)
        savePlayback()
    }

    fun setPlayMode(mode: PlayMode) {
        _state.value = _state.value.copy(playMode = mode)
        updateMediaSession()
        updateNotification()
    }

    /** 设置播放速度（立即生效并持久化） */
    fun setSpeed(speed: Float) {
        val s = _state.value
        if (s.speed == speed) return
        _state.value = s.copy(speed = speed)
        // 仅播放中立即变速；暂停时只存偏好，下次 play() 自动应用
        // （对未播放的 MediaPlayer 设 PlaybackParams 部分系统会隐式触发播放且不走 ticker）
        val p = player
        if (p != null && p.isPlaying) applySpeed(p, speed)
        RepeatPrefs.saveSpeed(this, speed)
        updateMediaSession()
        updateNotification()
    }

    /** 设置重播（句子复读）专用速度：保存偏好；复读播放中立即应用 */
    fun setRepeatSpeed(speed: Float) {
        RepeatPrefs.saveRepeatSpeed(this, speed)
        if (pendingSentenceIndex >= 0) {
            val p = player
            if (p != null && p.isPlaying) applySpeed(p, speed)
        }
    }

    /** 应用播放速度（MediaPlayer PlaybackParams，API23+；minSdk 26 可用） */
    private fun applySpeed(mp: MediaPlayer, speed: Float) {
        try {
            mp.playbackParams = PlaybackParams().setSpeed(speed.coerceIn(0.25f, 3f))
        } catch (_: Exception) {
            // 个别机型/格式不支持变速，忽略
        }
    }

    /** 按句子播放：从第 index 句起点开始播放。
     *  默认播完自然继续（不停顿）；repeatTimes>1 时该句循环复读 N 遍后继续；
     *  stopAtEnd=true 保留旧行为：播到句尾自动暂停（单句复读）。
     *
     *  定位采用「暂停 → seek → 完成后再播」：MediaPlayer.seekTo 是异步操作，
     *  直接 seek+start 会在 seek 完成前继续输出旧位置的缓冲音频（串音/吞句头）。
     *  等待 onSeekComplete 保证声音从句子起点精确开始。
     *  播放起点用波形安全提前（paddedStart）：部分机型 mp3 seek 落点偏晚，
     *  提前量落在句前静音里，落点偏晚也不丢句头。 */
    fun playSentence(index: Int, stopAtEnd: Boolean = false, repeatTimes: Int = 1) {
        // MediaPlayer 的 start/pause/seekTo 必须在主线程调用；onPrepared / 内部协程补播时可能在其他线程进入，
        // 这里统一抛回主线程，杜绝跨线程破坏播放器状态。
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                playSentence(index, stopAtEnd, repeatTimes)
            }
            return
        }
        val s = _state.value
        val sentence = s.sentences.getOrNull(index)
        // 句子或播放器尚未就绪（刚重启恢复现场、断句/识别未完成、prepare 还没完成）：
        // 先记下待播句，等「播放器已 prepare 且 句子已到达」后由 playPendingSentenceIfReady 补播，
        // 避免恢复现场时点歌词被吞掉 → 表现为「播错句（播整曲）/ 状态不同步 / 切歌再切回才正常」。
        if (sentence == null || !playerPrepared) {
            setPendingPlaySentence(index, stopAtEnd, repeatTimes, s.currentItem?.path)
            // 播放器还没建 → 仅加载并 prepare（不整首自动播），待句子到位后由 pending 机制精确播该句。
            if (!playerPrepared && player == null) {
                val item = s.currentItem ?: return
                loadAndPlay(item, autoPlay = false, initialPositionMs = sentence?.startMs ?: s.positionMs)
            }
            return
        }
        val p = player ?: run {
            // 播放器未就绪（如刚重启恢复现场）：先按该句起点重建并播放，避免点击空转
            val item = s.currentItem ?: return
            loadAndPlay(item, autoPlay = true, initialPositionMs = sentence.startMs)
            return
        }
        val needTrack = stopAtEnd || repeatTimes > 1
        val playStart = paddedStart(sentence.startMs, s.waveform, s.durationMs)
        pendingSentenceStart = playStart
        pendingSentenceEnd = if (needTrack) sentence.endMs else -1L
        pendingSentenceIndex = if (needTrack) index else -1
        pendingRepeatLeft = if (needTrack) (repeatTimes - 1).coerceAtLeast(0) else 0
        _state.value = s.copy(
            isPlaying = true,
            positionMs = playStart,
            currentSentenceIndex = if (needTrack) index else -1,
        )
        PlaybackSession.onAudioStarted()   // Phase3-步骤1：点句复读也是音频发声，同样记账
        // 停止旧 ticker：seek 完成前暂停中播放头不动，防止旧 ticker 在暂停窗口误触边界处理
        stopTicker()
        // MediaPlayer 偶发进入非法状态（长音频 seek/prepare 竞态、系统回收解码器等）：
        // 此时 pause/seekTo/start 全部抛 IllegalStateException。旧代码把异常静默吞掉，
        // 结果「声音还在按旧位置播、进度条冻在句首、之后所有按键全失灵」，只能靠切歌重建救回。
        // 现在一旦发现卡死就地重建播放器并从该句起点播放，实现自愈。
        var wedged = false
        // 关键：只在真正播放中才 pause。「重启后未自动播放」时播放器停在 Prepared（从未 start），
        // 此时调 pause() 会触发 onError(-38 INVALID_OPERATION)，旧逻辑在 onError 里全局杀状态
        // （playerPrepared=false + stopTicker），正是「点歌词后声音从旧位置播、时间冻住」的真凶。
        val wasPlaying = runCatching { p.isPlaying }.getOrDefault(false)
        if (wasPlaying) {
            try {
                p.pause()
            } catch (_: Exception) {
                wedged = true
            }
        }
        if (wedged) {
            val item = s.currentItem
            if (item != null) loadAndPlay(item, autoPlay = true, initialPositionMs = playStart)
            return
        }
        seekAccurate(p, playStart.toInt()) {
            var startOk = true
            if (!p.isPlaying) {
                try {
                    p.start()
                    applySpeed(p, RepeatPrefs.loadRepeatSpeed(this))
                } catch (e: Exception) {
                    startOk = false
                }
            }
            if (!startOk) {
                // 同上：不再静默返回（那会让 startTicker 永不执行 → 界面彻底冻住）
                val item = _state.value.currentItem
                if (item != null) loadAndPlay(item, autoPlay = true, initialPositionMs = playStart)
                return@seekAccurate
            }
            startTicker()
            updateMediaSession()
            updateNotification()
        }
    }

    /** 清空待播句并撤掉看门狗（切歌、补播完成、看门狗超时都走这里，避免状态残留把控制键挡住）。 */
    private fun clearPendingPlaySentence() {
        pendingPlaySentenceIndex = -1
        pendingPlaySentencePath = null
        pendingPlayWatchdog?.let { mainHandler.removeCallbacks(it) }
        pendingPlayWatchdog = null
    }

    /** 缓存待播句 + 启动看门狗：句子 12s 还没到（识别失败/缓存损坏）就放弃待播，
     *  绝不让 pending 无限期挂起（否则会一直影响播放/暂停等控制分支）。 */
    private fun setPendingPlaySentence(index: Int, stopAtEnd: Boolean, repeatTimes: Int, path: String?) {
        pendingPlaySentenceIndex = index
        pendingPlaySentenceStopAtEnd = stopAtEnd
        pendingPlaySentenceTimes = repeatTimes
        pendingPlaySentencePath = path
        pendingPlayWatchdog?.let { mainHandler.removeCallbacks(it) }
        val wd = Runnable {
            if (pendingPlaySentenceIndex == index) {
                clearPendingPlaySentence()
            }
        }
        pendingPlayWatchdog = wd
        mainHandler.postDelayed(wd, 12_000)
    }

    /** 待播句子就绪后补播：仅在「播放器已 prepare 且 句子已到达」时真正播放，
     *  否则保持挂起，由 onPrepared / cacheReady / pushAndCache 在条件齐备时再次触发。
     *  彻底解决「重启后先点歌词」因 prepare 或断句未完成而被吞掉、导致播错句/状态不同步。 */
    private fun playPendingSentenceIfReady() {
        val idx = pendingPlaySentenceIndex
        if (idx < 0) return
        if (!playerPrepared) return
        val s = _state.value
        if (s.sentences.getOrNull(idx) == null) return
        val stopAtEnd = pendingPlaySentenceStopAtEnd
        val times = pendingPlaySentenceTimes
        clearPendingPlaySentence()
        playSentence(idx, stopAtEnd, times)
    }

    /** 精确 seek：MediaPlayer 对 mp3 的 seek 落点可能有数百 ms 误差
     *  （暂停状态/双声道尤其明显，实测 ±300ms+，且误差非线性——反向补偿会发散）。
     *  策略：
     *  - 落点偏晚（会缺句头）→ 按偏差提前补偿重 seek（最多 2 次，方向可靠）；
     *  - 落点偏早 → 不补偿：paddedStart 已把目标提前 250ms 到句前静音区，
     *    落点偏早 ≤250ms 仍落在静音区（多静音无害）；偏早补偿非线性反而更差。
     *  - 完成回调偶发丢失（MediaPlayer 已知问题）→ 600ms 超时兜底恢复，
     *    避免播放器永远停在暂停、ticker/高亮全部停摆。
     *  连续调用时 listener 取最后一次；若覆盖了边界 pauseSeek 的 seek，
     *  其回调不会执行，这里必须重置 inBoundarySeek，否则句尾边界处理被挡住。 */
    private fun seekAccurate(mp: MediaPlayer, targetMs: Int, onDone: () -> Unit) {
        var attempts = 0
        var done = false
        val timeout = android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!done) {
                done = true
                mp.setOnSeekCompleteListener(null)
                inBoundarySeek = false
                onDone()
            }
        }, 600)
        fun doSeek(t: Int) {
            mp.setOnSeekCompleteListener {
                if (done) return@setOnSeekCompleteListener
                val actual = try {
                    mp.currentPosition
                } catch (_: Exception) {
                    -1
                }
                val drift = actual - t
                if (attempts < 2 && actual >= 0 && drift > 100) {
                    attempts++
                    doSeek((t - drift).coerceAtLeast(0))
                } else {
                    done = true
                    mp.setOnSeekCompleteListener(null)
                    inBoundarySeek = false
                    // onSeekComplete 在 MediaPlayer 内部线程触发，必须把后续播放器操作抛回主线程
                    android.os.Handler(android.os.Looper.getMainLooper()).post { onDone() }
                }
            }
            try {
                mp.seekTo(t)
            } catch (_: Exception) {
                // 极少数未就绪/已释放：丢弃本次 seek，600ms 超时仍会触发 onDone 兜底
                if (!done) {
                    done = true
                    mp.setOnSeekCompleteListener(null)
                    inBoundarySeek = false
                    onDone()
                }
            }
        }
        doSeek(targetMs)
    }

    /** 波形实际帧长（ms）：双声道 mp3 帧数翻倍（约 25ms/帧），单声道约 50ms/帧。
     *  用总时长/帧数校准，避免双声道换算错误。 */
    private fun frameMsOf(totalMs: Long, waveform: FloatArray): Double {
        if (waveform.isEmpty() || totalMs <= 0) return 50.0
        return (totalMs.toDouble() / waveform.size).coerceIn(20.0, 60.0)
    }

    /** 把句子时间戳修正到波形真实语音边界（防 ASR 起点偏晚/终点偏早造成的丢头少尾）。
     *  起点：startMs 在语音中（ASR 偏晚）→ 往前找第一个静音区，右边界 = 本句真实起点。
     *        不穿透短停顿（穿透会误抓到上一句内部），短停顿场景由播放时 paddedStart 兜底。
     *  终点：endMs 在语音中（偏早）或其后 150ms 内有语音（ASR 终点切在句内停顿）→
     *        往后找"持续 2 帧静音"作为真实句尾（尾音递减可延伸）；
     *        能量突增（≥0.10 且比前一帧强 1.5 倍 = 下一句开头）即停止延伸并回退到本句语音结束。
     *  起点在静音中（偏早/正常）不动；全部修正限 500ms 窗口。 */
    private fun refineSentenceBoundaries(
        sentences: List<Sentence>,
        waveform: FloatArray,
        totalMs: Long,
    ): List<Sentence> {
        if (waveform.isEmpty() || totalMs <= 0) return sentences
        val frameMs = frameMsOf(totalMs, waveform)
        val maxFrames = (500 / frameMs).toInt().coerceAtLeast(8)
        val ENERGY = 0.03f
        fun isSilence(i: Int) = i !in waveform.indices || waveform[i] < ENERGY
        return sentences.map { s ->
            val startFrame = (s.startMs / frameMs).toInt().coerceIn(0, waveform.size - 1)
            val endFrame = (s.endMs / frameMs).toInt().coerceIn(0, waveform.size - 1)
            var ns = s.startMs
            var ne = s.endMs

            // 起点修正
            if (!isSilence(startFrame)) {
                var i = startFrame
                while (i > 0 && startFrame - i <= maxFrames) {
                    if (isSilence(i)) {
                        val cand = ((i + 1) * frameMs).toLong()
                        if (cand < s.startMs && s.startMs - cand <= 500) ns = cand
                        break
                    }
                    i--
                }
            }

            // 终点修正
            val lookAhead = (150 / frameMs).toInt().coerceAtLeast(2)
            val speechAfter =
                (endFrame + 1..(endFrame + lookAhead).coerceAtMost(waveform.size - 1)).any { !isSilence(it) }
            if (!isSilence(endFrame) || speechAfter) {
                var i = endFrame
                var found = false
                var bumped = false
                while (i < waveform.size && i - endFrame <= maxFrames) {
                    if (isSilence(i)) {
                        if (i + 1 < waveform.size && isSilence(i + 1)) {
                            val cand = (i * frameMs).toLong()
                            if (cand > s.endMs && cand - s.endMs <= 500) { ne = cand; found = true }
                        }
                        break
                    } else if (i > endFrame && waveform[i] > 0.10f && waveform[i] > waveform[i - 1] * 1.5f) {
                        bumped = true
                        break
                    }
                    i++
                }
                // 找不到持续静音或能量突增（连读/进入下一句）→ 回退到本句语音结束
                if (!found || bumped) {
                    var j = endFrame
                    var back2 = 0
                    while (j > 0 && !(isSilence(j) && !isSilence(j - 1)) && back2 < maxFrames) { j--; back2++ }
                    if (j > 0 && isSilence(j) && !isSilence(j - 1)) {
                        val cand = (j * frameMs).toLong()
                        if (cand < s.endMs && s.endMs - cand <= 500) { ne = cand }
                    }
                }
            }

            if (ns >= ne - 100) ns = s.startMs
            if (ne <= ns + 100) ne = s.endMs
            Sentence(ns, ne, s.text)
        }
    }

    /** 播放起点安全提前：部分机型 MediaPlayer 对 mp3 的 seek 落点会偏晚（几十到几百 ms），
     *  直接 seek 到句首会缺头。仅当提前区间在波形里全是静音（<0.03）时才扩展，最多 250ms，
     *  避免带出上一句尾音；遇到语音立即停止。 */
    private fun paddedStart(startMs: Long, waveform: FloatArray, totalMs: Long): Long {
        if (waveform.isEmpty() || totalMs <= 0 || startMs <= 0) return startMs
        val frameMs = frameMsOf(totalMs, waveform)
        val startFrame = (startMs / frameMs).toInt()
        val maxFrames = (250 / frameMs).toInt().coerceAtLeast(2)
        var padFrames = 0
        for (i in startFrame - 1 downTo (startFrame - maxFrames).coerceAtLeast(0)) {
            if (waveform[i] < 0.03f) padFrames++ else break
        }
        return startMs - (padFrames * frameMs).toLong()
    }

    /** 上一句：定位到上一句，按设置的重播次数复读后自然继续播放 */
    fun prevSentence() {
        val s = _state.value
        if (s.sentences.isEmpty()) {
            prev()
            return
        }
        val idx = if (s.currentSentenceIndex >= 0) {
            if (s.currentSentenceIndex > 0) s.currentSentenceIndex - 1 else 0
        } else {
            // 整曲播放：当前所在句的前一句；已在第一句则回第一句
            val cur = s.sentences.indexOfLast { it.startMs <= s.positionMs }
            if (cur > 0) cur - 1 else 0
        }
        playSentence(idx, repeatTimes = RepeatPrefs.loadTimes(this))
    }

    fun nextSentence() {
        val s = _state.value
        if (s.sentences.isEmpty()) {
            next()
            return
        }
        val idx = if (s.currentSentenceIndex >= 0) {
            if (s.currentSentenceIndex < s.sentences.size - 1) s.currentSentenceIndex + 1 else 0
        } else {
            // 整曲播放：当前所在句的下一句；末尾则循环回第一句（定位后继续播放）
            val cur = s.sentences.indexOfLast { it.startMs <= s.positionMs }
            if (cur >= 0 && cur < s.sentences.size - 1) cur + 1 else 0
        }
        playSentence(idx, stopAtEnd = s.currentSentenceIndex >= 0)
    }

    // ---------------- 播放器内部 ----------------

    private fun loadAndPlay(item: AudioItem, autoPlay: Boolean, initialPositionMs: Long = 0L) {
        player?.release()
        val p = MediaPlayer()
        player = p
        try {
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            p.setDataSource(item.path)
            p.setOnPreparedListener { mp ->
                playerPrepared = true
                val sp = RepeatPrefs.loadSpeed(this)
                _state.value = _state.value.copy(
                    durationMs = mp.duration.toLong(),
                    positionMs = if (initialPositionMs > 0) initialPositionMs else 0L,
                    speed = sp,
                )
                if (initialPositionMs > 0) mp.seekTo(initialPositionMs.toInt())
                // 优先补播用户待播句：若恢复现场/断句未完成期间用户点过某句，且句子已就绪，
                // 则精确播该句（不再先整首自动播），彻底解决「点某句却播整曲」。
                val pendingReady = pendingPlaySentenceIndex >= 0 &&
                    _state.value.sentences.getOrNull(pendingPlaySentenceIndex) != null
                if (pendingReady) {
                    val pi = pendingPlaySentenceIndex
                    val ps = pendingPlaySentenceStopAtEnd
                    val pt = pendingPlaySentenceTimes
                    clearPendingPlaySentence()
                    playSentence(pi, ps, pt)
                } else if (autoPlay) {
                    mp.start()
                    applySpeed(mp, sp)
                    _state.value = _state.value.copy(isPlaying = true)
                    PlaybackSession.onAudioStarted()   // Phase3-步骤1：自动续播同样记账
                }
                // 无论是否自动续播，prepare 完成后都要启动 ticker：
                // 恢复现场/视频模式后切回音频时 autoPlay=false，不启动 ticker 会导致
                // 进度/断句高亮/UI 状态机冻结，点击进度/句子看起来像"失灵"。
                startTicker()
                updateMediaSession()
                updateNotification()
            }
            p.setOnCompletionListener { onTrackCompleted() }
            p.setOnErrorListener { _, what, extra ->
                if (what == -38) {
                    // -38 = INVALID_OPERATION：操作时序竞态（如 Prepared 态误调 pause/seek）。
                    // 播放器本身没有死（start/seek 通常仍可用），绝不能在这里全局清状态，
                    // 否则会把刚启动的 ticker 杀掉 → 界面时间冻住、控制状态错乱。
                    true
                } else {
                    playerPrepared = false
                    _state.value = _state.value.copy(isPlaying = false)
                    stopTicker()
                    true
                }
            }
            playerPrepared = false
            // 只有切到「另一首歌」才丢弃待播句。
            // 过去这里无条件 `pendingPlaySentenceIndex = -1`，会把用户刚点的那句抹掉，
            // 结果 onPrepared 时 pending 已是 -1 → 按 autoPlay 播整曲（=上次关闭的位置），
            // 正是「重启后点歌词却从上次位置播、且控制键失灵」的根因。
            val keepPending = pendingPlaySentenceIndex >= 0 && pendingPlaySentencePath == item.path
            if (!keepPending) {
                clearPendingPlaySentence()
            }
            p.prepareAsync()
        } catch (e: Exception) {
            e.printStackTrace()
            _state.value = _state.value.copy(isPlaying = false)
        }
        startSplitting(item)
    }

    /** 断句 + 歌词装配：
     *  - 有缓存且含识别文本 → 直接复用（已在线识别过的音频不再联网/花钱）
     *  - 有 LRC → 波形+歌词融合（原逻辑不变）
     *  - 无 LRC 且有 Key → 在线识别自动生成英文歌词（recognizing 状态；失败回退静音断句）
     *  - 无 LRC 无 Key → 静音断句（无文本，与现状一致） */
    private fun startSplitting(item: AudioItem) {
        splitJob?.cancel()
        splitJob = serviceScope.launch {
            val lyrics = withContext(Dispatchers.IO) {
                LrcParser.loadFor(this@PlayerService, item.path) ?: emptyList()
            }
            if (_state.value.currentItem?.path == item.path) {
                _state.value = _state.value.copy(lyrics = lyrics)
            }
            val cached = withContext(Dispatchers.IO) { SentenceCache.load(this@PlayerService, item) }
            if (cached != null && cached.sentences.isNotEmpty()) {
                // 缓存自愈：旧版本「生成字幕」曾把空波形覆盖进缓存（恢复现场时不跑断句，
                // 内存 waveform 还是空值就落盘），导致这份缓存只有句子、没有波形，
                // 界面永远停在「N 句 · 波形未缓存」且不会自己好（命中缓存即 return，不重跑分析）。
                // 所以这里发现波形为空就补跑一次本地分析，并回写缓存修好它。
                var cachedWaveform = cached.waveform
                var cachedTotal = cached.totalMs
                if (cachedWaveform.isEmpty()) {
                    val analyzed = withContext(Dispatchers.IO) { SentenceSplitter.split(item.path) { } }
                    if (analyzed.waveform.isNotEmpty()) {
                        cachedWaveform = analyzed.waveform
                        cachedTotal = analyzed.totalMs
                        withContext(Dispatchers.IO) {
                            SentenceCache.save(
                                this@PlayerService, item,
                                CachedSplit(cached.sentences, cachedWaveform, cachedTotal),
                            )
                        }
                    }
                }
                // 旧缓存 totalMs 可能有双声道 2 倍 bug：用末句终点校准真实总时长（否则波形帧长推断全错）
                val lastEnd = cached.sentences.lastOrNull()?.endMs ?: 0L
                val realTotal = if (lastEnd > 0 && cachedTotal > lastEnd + 30_000) lastEnd + 2_000 else cachedTotal
                // 修正句子边界到波形真实语音边界（防丢头少尾）
                val refined = refineSentenceBoundaries(cached.sentences, cachedWaveform, realTotal)
                val finalSentences = when {
                    lyrics.isNotEmpty() -> {
                        val merged = buildWaveLyricSentences(refined, lyrics, realTotal)
                        splitLongSentences(merged, refined, lyrics)
                    }
                    // 已对齐文本的缓存原样使用（不再做短句合并——会把对齐好的短句又粘回长句）
                    else -> refined
                }
                if (_state.value.currentItem?.path == item.path) {
                    _state.value = _state.value.copy(
                        sentences = finalSentences, waveform = cachedWaveform,
                        rawSegments = emptyList(), // 原始边界未随缓存存，清空防止上一首串台；重新识别时会现场重跑断句
                        splitting = false, splitProgress = 1f, recognizing = false,
                    )
                    // 缓存恢复句子后，若恢复现场时用户已点过某句，现在条件齐备，补播该句
                // （无缓存路径走 pushAndCache 同样补播；此处漏调会导致「点哪句都播上次位置」）
                playPendingSentenceIfReady()
                }
                return@launch
            }

            // 无缓存：本地静音分析（波形 + 静音边界，始终需要；也作为识别无时间戳时的对齐基准）
            _state.value = _state.value.copy(splitting = true, splitProgress = 0f)
            val result = withContext(Dispatchers.IO) {
                SentenceSplitter.split(item.path) { p ->
                    // 仅当仍是当前曲目时更新进度，避免切歌后进度串台
                    if (_state.value.currentItem?.path == item.path) {
                        _state.value = _state.value.copy(splitProgress = p)
                    }
                }
            }
            if (result.sentences.isEmpty()) {
                _state.value = _state.value.copy(splitting = false, splitProgress = -1f)
                return@launch
            }
            val baseSentences = result.sentences
            val rawSegments = result.rawSegments
            if (_state.value.currentItem?.path == item.path) {
                _state.value = _state.value.copy(rawSegments = rawSegments)
            }
            val waveform = result.waveform
            val totalMs = result.totalMs

            // 1) 有 LRC → 波形+歌词融合
            if (lyrics.isNotEmpty()) {
                val merged = buildWaveLyricSentences(baseSentences, lyrics, totalMs)
                val finalS = splitLongSentences(merged, baseSentences, lyrics)
                pushAndCache(item, finalS, waveform, totalMs, rawSegments)
                return@launch
            }

            // 2) 无 LRC 且已配置 Key → 在线识别生成歌词
            // 主路径：整曲识别（星辰返回精确句子时间戳，长音频一次搞定，快且稳）
            val key = AsrPrefs.loadKey(this@PlayerService)
            if (key.isNotBlank()) {
                _state.value = _state.value.copy(splitting = false, recognizing = true, splitProgress = -1f, recognizeProgress = "")
                val provider = AsrProvider.fromName(AsrPrefs.loadProvider(this@PlayerService))
                val segsForAsr = rawSegments.ifEmpty { baseSentences }
                val outcome = withContext(Dispatchers.IO) {
                    SpeechRecognizer.transcribe(item.path, provider, key)
                }
                if (outcome != null) {
                    val asrSentences = when (outcome) {
                        is AsrOutcome.Timed ->
                            mergeShortSentences(outcome.segments.map { Sentence(it.startMs, it.endMs, it.text) })
                        is AsrOutcome.Plain ->
                            alignTextToSilence(outcome.text, segsForAsr)
                    }
                    pushAndCache(item, asrSentences, waveform, totalMs, rawSegments)
                    return@launch
                }
                // 整曲失败 → 逐段识别兜底（每段真实停顿边界）
                val segResult = withContext(Dispatchers.IO) {
                    recognizeBySegments(item, provider, key, segsForAsr, waveform, totalMs)
                }
                if (segResult != null) {
                    pushAndCache(item, segResult, waveform, totalMs, rawSegments)
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PlayerService, "在线识别失败，已用静音断句", Toast.LENGTH_LONG).show()
                }
            }

            // 3) 无 LRC 无 Key/失败 → 静音断句。与有歌词/识别路径一致，先按 2.5s 合并短句，避免纯波形又碎又短。
            pushAndCache(item, mergeShortSentences(baseSentences), waveform, totalMs, rawSegments)
        }
    }

    /** 推送句子到 UI 并落盘缓存（含文本，下次打开免联网）。
     *  落盘前把句子边界修正到波形真实语音边界（防丢头少尾）。 */
    private suspend fun pushAndCache(
        item: AudioItem, sentences: List<Sentence>,
        waveform: FloatArray, totalMs: Long,
        rawSegs: List<Sentence> = emptyList(),
    ) {
        val refined = refineSentenceBoundaries(sentences, waveform, totalMs)
        if (_state.value.currentItem?.path == item.path) {
            _state.value = _state.value.copy(
                sentences = refined, waveform = waveform, rawSegments = rawSegs,
                recognizing = false, splitting = false, splitProgress = 1f,
            )
            // 句子刚到达：若恢复现场时用户已点过某句，现在条件齐备，补播该句
            playPendingSentenceIfReady()
        }
        withContext(Dispatchers.IO) {
            SentenceCache.save(this@PlayerService, item, CachedSplit(refined, waveform, totalMs))
        }
    }

    /** 手动触发在线识别（按钮调用）：即使已缓存静音断句也重新识别并覆盖缓存 */
    fun generateLyrics() {
        val item = _state.value.currentItem ?: return
        if (_state.value.recognizing) return
        val key = AsrPrefs.loadKey(this)
        if (key.isBlank()) {
            Toast.makeText(this, "请先在设置中填写 API Key", Toast.LENGTH_LONG).show()
            return
        }
        val value = _state.value
        _state.value = _state.value.copy(recognizing = true)
        serviceScope.launch {
            _state.value = _state.value.copy(recognizeProgress = "")
            val provider = AsrProvider.fromName(AsrPrefs.loadProvider(this@PlayerService))
            // 波形/原始段兜底：恢复现场（restorePlayback→loadAndPlay）不会跑断句，
            // 此时 waveform 仍是初始空值、rawSegments 也是空的。若直接拿空波形落盘，
            // 会把缓存里原有的波形覆盖成空 —— 表现为「生成字幕后字幕有了、波形却消失，
            // 只剩 N 句 · 波形未缓存」。所以缺任一项就现场补跑一次本地分析，一次拿齐。
            var waveform = value.waveform
            var realTotalMs = value.durationMs
            var segs = _state.value.rawSegments
            if (waveform.isEmpty() || segs.isEmpty()) {
                val analyzed = withContext(Dispatchers.IO) { SentenceSplitter.split(item.path) { } }
                if (waveform.isEmpty() && analyzed.waveform.isNotEmpty()) {
                    waveform = analyzed.waveform
                    realTotalMs = analyzed.totalMs
                }
                if (segs.isEmpty() && analyzed.rawSegments.isNotEmpty()) {
                    segs = analyzed.rawSegments
                    if (_state.value.currentItem?.path == item.path) {
                        _state.value = _state.value.copy(rawSegments = segs)
                    }
                }
            }
            var asrSentences: List<Sentence>? = null
            val outcome = withContext(Dispatchers.IO) { SpeechRecognizer.transcribe(item.path, provider, key) }
            if (outcome != null) {
                asrSentences = when (outcome) {
                    is AsrOutcome.Timed ->
                        mergeShortSentences(outcome.segments.map { Sentence(it.startMs, it.endMs, it.text) })
                    is AsrOutcome.Plain -> alignTextToSilence(outcome.text, segs)
                }
            }
            // 整曲失败 → 逐段识别兜底
            if (asrSentences == null) {
                asrSentences = withContext(Dispatchers.IO) {
                    recognizeBySegments(item, provider, key, segs, waveform, realTotalMs)
                }
            }
            if (asrSentences == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PlayerService, "在线识别失败，请检查网络或 Key", Toast.LENGTH_LONG).show()
                    if (_state.value.currentItem?.path == item.path) {
                        _state.value = _state.value.copy(recognizing = false, recognizeProgress = "")
                    }
                }
                return@launch
            }
            // 修正句子边界到波形真实语音边界（防丢头少尾）
            val refined = refineSentenceBoundaries(asrSentences, waveform, realTotalMs)
            if (_state.value.currentItem?.path == item.path) {
                _state.value = _state.value.copy(
                    sentences = refined, recognizing = false, recognizeProgress = "",
                    // 补跑过分析时把波形/时长也回灌 UI，否则界面仍停在「波形未缓存」
                    waveform = waveform, durationMs = if (realTotalMs > 0) realTotalMs else _state.value.durationMs,
                )
            }
            withContext(Dispatchers.IO) {
                SentenceCache.save(this@PlayerService, item, CachedSplit(refined, waveform, realTotalMs))
            }
        }
    }

    /** SenseVoice 等 ASR 会在文本里夹 emoji 标签（😊 等），先剥离再分句 */
    /** 逐段识别主流程：把每个静音段单独裁出上传识别，段文本天然对准该段时间。
     *  全部失败返回 null（调用方回退整曲识别+DP 对齐）；部分失败的空段并入上一句。 */
    private suspend fun recognizeBySegments(
        item: AudioItem,
        provider: AsrProvider,
        key: String,
        segs: List<Sentence>,
        waveform: FloatArray,
        totalMs: Long,
    ): List<Sentence>? {
        if (segs.isEmpty()) return null
        val isWav = item.path.lowercase().endsWith(".wav")
        val out = mutableListOf<Sentence>()
        var anyText = false
        for ((i, seg) in segs.withIndex()) {
            if (_state.value.currentItem?.path != item.path) return null // 已切歌/停止
            _state.value = _state.value.copy(recognizeProgress = "${i + 1}/${segs.size}")
            val sliceFile = File(cacheDir, "asr_slice_${item.id}_$i.${if (isWav) "wav" else "m4a"}")
            val ok = try {
                AudioSlice.extract(item.path, seg.startMs, seg.endMs, sliceFile.absolutePath)
            } catch (_: Exception) {
                false
            }
            val outcome = if (ok) SpeechRecognizer.transcribe(sliceFile.absolutePath, provider, key) else null
            sliceFile.delete()
            when (outcome) {
                // 词级/段落时间戳（星辰/Groq/OpenAI）：句子时间精确，直接采用（相对时间 + 段起点）。
                // 星辰会把一句话拆成短语级 segment，用 mergeShortSentences 合并成完整句子
                is AsrOutcome.Timed -> {
                    val timedSegs = outcome.segments.map {
                        Sentence(seg.startMs + it.startMs, seg.startMs + it.endMs, it.text)
                    }
                    out.addAll(mergeShortSentences(timedSegs))
                    anyText = true
                }
                // 纯文本（硅基流动）：段内按标点拆子句 + 短停顿吸附/字符比例分配
                is AsrOutcome.Plain -> {
                    out.addAll(splitSegmentWithin(seg, outcome.text, waveform, totalMs))
                    anyText = true
                }
                null -> out.add(seg.copy(text = ""))
            }
        }
        if (!anyText) return null
        // 空文本段（停顿/静音误检）并入上一句，避免空白可点句
        val merged = mutableListOf<Sentence>()
        for (s in out) {
            if (s.text.isEmpty() && merged.isNotEmpty()) {
                merged[merged.lastIndex] = merged.last().copy(endMs = s.endMs)
            } else {
                merged.add(s)
            }
        }
        return merged
    }
    /** 段内二次断句：段文本按句末标点拆子句（缩写保护），
     *  子句时间 = 段内短停顿（>=150ms）吸附 + 字符比例插值。
     *  段文本无标点且超长时按逗号/空格粗拆，避免整段一句。 */
    private fun splitSegmentWithin(seg: Sentence, text: String, waveform: FloatArray, totalMs: Long): List<Sentence> {
        val protected = asrAbbrevRegex.replace(text) { m -> m.value.replace('.', ASR_ABBREV_PLACEHOLDER[0]) }
        var subs = asrEmojiRegex.replace(protected, " ")
            .split(Regex("(?<=[.!?！？。])(?=\\s*[A-Z]|\\s+[a-z]|\\s*$)"))
            .map { it.trim().replace(ASR_ABBREV_PLACEHOLDER, ".") }
            .filter { it.isNotEmpty() }
        // 无标点且超长：按逗号切，再不行按 6 个词一刀粗拆（阈值放低，宁可碎一点也别整段一句）
        val tooLong = text.length > 45 || text.split(Regex("\\s+")).count { it.isNotEmpty() } > 9
        if (subs.size <= 1 && tooLong) {
            subs = text.split(Regex("[,;，；]")).flatMap { part ->
                val p = part.trim()
                if (p.length <= 25) listOf(p)
                else p.split(Regex("\\s+")).filter { it.isNotEmpty() }.chunked(6).map { it.joinToString(" ") }
            }.filter { it.isNotEmpty() }
        }
        if (subs.size <= 1) return listOf(seg.copy(text = text))

        // 段内短停顿（>=150ms）作为子边界吸附点（波形帧长按实际推断，双声道音频 25ms/帧）
        val frameMs = frameMsOf(totalMs, waveform)
        val startFrame = (seg.startMs / frameMs).toInt().coerceAtLeast(0)
        val endFrame = (seg.endMs / frameMs).toInt().coerceAtMost(waveform.size)
        val pauses = mutableListOf<Long>()
        if (endFrame > startFrame) {
            val segPeak = (startFrame until endFrame).maxOfOrNull { waveform[it] } ?: 1f
            val thr = (segPeak * 0.1f).coerceAtLeast(0.01f)
            var i = startFrame
            while (i < endFrame) {
                if (waveform[i] < thr) {
                    var j = i
                    while (j < endFrame && waveform[j] < thr) j++
                    if ((j - i) * frameMs >= 150) pauses.add((((i + j) / 2) * frameMs).toLong())
                    i = j
                } else i++
            }
        }

        val total = subs.sumOf { it.count { ch -> ch.isLetterOrDigit() } }.coerceAtLeast(1)
        val segMs = (seg.endMs - seg.startMs).coerceAtLeast(1L)
        val out = mutableListOf<Sentence>()
        var cursor = seg.startMs
        subs.forEachIndexed { idx, st ->
            val ratio = st.count { ch -> ch.isLetterOrDigit() }.toDouble() / total
            var end = if (idx == subs.size - 1) seg.endMs
            else (cursor + (segMs * ratio).toLong()).coerceAtLeast(cursor + 400L)
            // 吸附到段内最近的短停顿（1s 内），让子句边界落在真实停歇处
            if (idx < subs.size - 1) {
                val snap = pauses.minByOrNull { kotlin.math.abs(it - end) }
                if (snap != null && kotlin.math.abs(snap - end) < 1000) {
                    end = snap.coerceIn(cursor + 300L, seg.endMs - 200L)
                }
            }
            out.add(Sentence(cursor, end, st))
            cursor = end
        }
        return out
    }
    private val asrEmojiRegex = Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u2600-\\u27BF\\u2190-\\u21FF\\uFE0F]")

    /** 常见英文缩写：拆句前把点换成占位符，避免 "Mr. Taylor" 被拆成两句 */
    private val asrAbbrevRegex = Regex("\\b(Mr|Mrs|Ms|Dr|St|Prof|Jr|Sr|vs|etc|eg|ie|No|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\.", RegexOption.IGNORE_CASE)
    private val ASR_ABBREV_PLACEHOLDER = "\uE000"
    /** 句并入邻组的代价惩罚（ms）：抑制"整篇并成几句" */
    private val ASR_MERGE_PENALTY_MS = 1500.0
    /** 段并入邻组的代价惩罚（ms）：抑制大量停顿段并入导致组超长 */
    private val ASR_SKIP_PENALTY_MS = 600.0
    /** 对齐组时长上限：超过则组内句子按估时比例拆开（防超长句） */
    private val ASR_MAX_GROUP_MS = 6000L
    /** 对齐组句数上限：超过则拆开 */
    private val ASR_MAX_GROUP_SENTENCES = 3
    /** 无时间戳 ASR 文本 ↔ 静音段 动态规划全局最优对齐：
     *  1) 剥离 emoji、按句末标点拆句（标点后只要不是小写字母就拆——ASR 文本常无空格，
     *     如 "everyone.Welcome"，句号后也可能直接跟 emoji 或下一句）；
     *  2) 每句估时 ≈ 总语音时长 × 字符占比；
     *  3) 与各静音段真实时长做单调序列比对（类似基因比对）：
     *     句配段代价 |估时-实际时长|、句并入邻组代价=其估时、段并入邻组代价=其时长，
     *     求全局最小总代价。相比逐句累计贪心，能纠正语速快慢造成的 ±1 段错位，
     *     避免"点的句子"和"实际播的句子"错开。 */
    private fun alignTextToSilence(text: String, segs: List<Sentence>): List<Sentence> {
        // 缩写保护：Mr. 等先换成占位符，避免被句末标点规则拆开
        val protected = asrAbbrevRegex.replace(text) { m -> m.value.replace('.', ASR_ABBREV_PLACEHOLDER[0]) }
        val sentences = asrEmojiRegex.replace(protected, " ")
            .split(Regex("(?<=[.!?！？。])(?=\\s*[A-Z]|\\s+[a-z]|\\s*$)"))
            .map { it.trim().replace(ASR_ABBREV_PLACEHOLDER, ".") }
            .filter { it.isNotEmpty() }
        if (sentences.isEmpty() || segs.isEmpty()) return segs

        // 估时按"字母数字字符数"（标点/空格不影响语音时长），比纯 length 更贴近真实语速
        fun speechChars(s: String): Int = s.count { it.isLetterOrDigit() }
        val totalChars = sentences.sumOf { speechChars(it) }.toDouble().coerceAtLeast(1.0)
        val spokenMs = segs.sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }.toDouble().coerceAtLeast(1.0)
        val est = DoubleArray(sentences.size) { spokenMs * speechChars(sentences[it]) / totalChars }
        val dur = DoubleArray(segs.size) { (segs[it].endMs - segs[it].startMs).coerceAtLeast(0L).toDouble() }

        val n = sentences.size
        val m = segs.size
        val inf = Double.POSITIVE_INFINITY
        // dp[i][j] = 前 i 句与前 j 段对齐的最小总代价；back 记录步进方向：
        // 1=句i-1与段j-1配对开新组  2=句i-1并入当前组  3=段j-1并入当前组
        val dp = Array(n + 1) { DoubleArray(m + 1) { inf } }
        val back = Array(n + 1) { ByteArray(m + 1) }
        dp[0][0] = 0.0
        for (i in 0..n) {
            for (j in 0..m) {
                val c = dp[i][j]
                if (c == inf) continue
                val start = i == 0 && j == 0 // 首句必须与首段配对开组（前奏静音归入首句）
                if (i < n && j < m) {
                    val v = c + kotlin.math.abs(est[i] - dur[j])
                    if (v < dp[i + 1][j + 1]) { dp[i + 1][j + 1] = v; back[i + 1][j + 1] = 1 }
                }
                if (i < n && !start) {
                    // 句并入组：加惩罚，抑制"整篇并成几句"
                    val v = c + est[i] + ASR_MERGE_PENALTY_MS
                    if (v < dp[i + 1][j]) { dp[i + 1][j] = v; back[i + 1][j] = 2 }
                }
                if (j < m && !start) {
                    // 段并入组：加小惩罚，避免大量停顿段并入
                    val v = c + dur[j] + ASR_SKIP_PENALTY_MS
                    if (v < dp[i][j + 1]) { dp[i][j + 1] = v; back[i][j + 1] = 3 }
                }
            }
        }

        // 回溯分组：每组 = 连续若干句 + 连续若干段；步进逆行时，
        // 遇到的 2/3 步属于"下一个（逆行方向）对角步"所开的组
        val groups = ArrayList<IntArray>() // [句起,句止,段起,段止]（闭区间）
        var i = n
        var j = m
        var ptHi = -1 // 尚未归属的句最大下标
        var psHi = -1 // 尚未归属的段最大下标
        while (i > 0 || j > 0) {
            when (back[i][j].toInt()) {
                1 -> {
                    groups.add(intArrayOf(i - 1, maxOf(i - 1, ptHi), j - 1, maxOf(j - 1, psHi)))
                    ptHi = -1; psHi = -1; i--; j--
                }
                2 -> { ptHi = maxOf(ptHi, i - 1); i-- }
                3 -> { psHi = maxOf(psHi, j - 1); j-- }
                else -> { if (i > 0) i-- else j-- } // 理论不可达，防御
            }
        }
        groups.reverse()

        val out = mutableListOf<Sentence>()
        for (g in groups) {
            val txts = (g[0]..g[1]).mapNotNull { sentences.getOrNull(it) }.filter { it.isNotEmpty() }
            val start = segs[g[2]].startMs
            val end = segs[g[3]].endMs
            if (txts.isEmpty()) {
                // 无文本段（纯停顿尾巴）：并入上一句结尾，避免空白可点句
                if (out.isNotEmpty()) out[out.lastIndex] = out.last().copy(endMs = end)
                continue
            }
            val groupMs = end - start
            val tooMany = txts.size > ASR_MAX_GROUP_SENTENCES
            val tooLong = groupMs > ASR_MAX_GROUP_MS
            if (tooMany || tooLong) {
                // 长组拆分：每句独立成句，组时长按句的估时比例分配，贴合各自语音时长
                val total = txts.sumOf { it.count { ch -> ch.isLetterOrDigit() } }.coerceAtLeast(1)
                var cursor = start
                txts.forEachIndexed { idx, st ->
                    val ratio = st.count { ch -> ch.isLetterOrDigit() }.toDouble() / total
                    val stEnd = if (idx == txts.size - 1) end
                    else (cursor + (groupMs * ratio).toLong()).coerceAtLeast(cursor + 400L)
                    out.add(Sentence(cursor, stEnd, st))
                    cursor = stEnd
                }
            } else {
                out.add(Sentence(start, end, txts.joinToString(" ")))
            }
        }
        return out
    }

    /** 按标点断句（LRC 文本 + 时间戳），再按长度规则调整：
     *  1) 连续 LRC 行累积，遇到句末标点（.?!）才闭合为一句 → 长句不被 LRC 行切碎
     *  2) 一行内含多个句末标点 → 按字符比例在行时间区间内插值切分
     *  3) 短句（<SHORT_SENTENCE_MS）与相邻句合并（二合一，避免复读太碎）
     *  4) 长句（>LONG_SENTENCE_MS 或超长字符）在逗号处切分，切点优先吸附最近静音边界
     *  5) 传入静音断句边界时，终点进入下一句（LRC 打点偏晚）则拉回句间停顿处 */
    private fun buildPunctSentences(
        lyrics: List<LrcLine>,
        totalMs: Long,
        silenceBounds: List<Long> = emptyList(),
    ): List<Sentence> {
        // 句末标点（含后随闭合引号/括号）；逗号、分号不算句末
        val endPunct = Regex("""[.!?！？。][”"'』」）)]*""")
        // 自然句：时间区间 + 文本（供合并/切分）
        data class Natural(val start: Long, val end: Long, val text: String)

        val naturals = mutableListOf<Natural>()
        var segStartMs = -1L
        var buf = StringBuilder()
        for ((i, line) in lyrics.withIndex()) {
            if (segStartMs < 0) segStartMs = line.timeMs
            val prevLen = buf.length
            if (prevLen > 0) buf.append(' ')
            buf.append(line.text)
            // 从本行新增文本处开始找标点；行首时 offset=0
            var offset = if (prevLen > 0) prevLen + 1 else 0
            val nextMs = if (i < lyrics.size - 1) lyrics[i + 1].timeMs else totalMs
            val lineLen = (nextMs - line.timeMs).coerceAtLeast(1L)
            var lineCursor = line.timeMs
            while (true) {
                val m = endPunct.find(buf, offset) ?: break
                val endIdx = m.range.last + 1
                val consumedInLine = (endIdx - offset).coerceAtLeast(0)
                val ratio = consumedInLine.toDouble() / line.text.length.coerceAtLeast(1)
                var endMs = (lineCursor + (lineLen * ratio).toLong()).coerceAtLeast(segStartMs + 1)
                // 静音修正：终点进入下一句时拉回句间停顿
                if (silenceBounds.isNotEmpty()) {
                    silenceBounds.filter { it in segStartMs..endMs }
                        .maxOrNull()?.let { endMs = it.coerceAtLeast(segStartMs + 1) }
                }
                val sentText = buf.substring(offset, endIdx).trim()
                naturals.add(Natural(segStartMs, endMs, sentText))
                segStartMs = endMs
                lineCursor = endMs
                buf = StringBuilder(buf.substring(endIdx).trimStart())
                // 剩余部分属于本行，从头找下一个标点
                offset = 0
            }
        }
        // 尾部未闭合文本（最后一行无句末标点）：终点到音频结尾
        if (buf.isNotEmpty() && segStartMs >= 0) {
            naturals.add(Natural(segStartMs, totalMs.coerceAtLeast(segStartMs + 1), buf.toString().trim()))
        }

        // ---- 短句合并：< SHORT_SENTENCE_MS 时与下一句合并（末句与上一句合并），循环直到没有短句 ----
        var changed = true
        while (changed) {
            changed = false
            var k = 0
            while (k < naturals.size) {
                val n = naturals[k]
                if (n.end - n.start < SHORT_SENTENCE_MS && naturals.size > 1) {
                    if (k < naturals.size - 1) {
                        val next = naturals.removeAt(k + 1)
                        naturals[k] = n.copy(end = next.end, text = n.text + " " + next.text)
                    } else {
                        val prev = naturals.removeAt(k - 1)
                        naturals[k - 1] = prev.copy(end = n.end, text = prev.text + " " + n.text)
                        k--
                    }
                    changed = true
                }
                k++
            }
        }

        // ---- 长句切分：> LONG_SENTENCE_MS 或 > LONG_SENTENCE_CHARS 时在逗号/从句连接词处切 ----
        // 连接词需前后带空白，避免误匹配词内子串（如 understand 里的 and）
        val cutPoint = Regex("""[，,]|\s+(and|but|or|so|because|that|when|if|which|who|where|while|although|since|until|after|before)\s+""")
        val final = mutableListOf<Sentence>()
        for (n in naturals) {
            var segStart = n.start
            var segText = n.text
            var segEnd = n.end
            while (segEnd - segStart > LONG_SENTENCE_MS || segText.length > LONG_SENTENCE_CHARS) {
                val m = cutPoint.find(segText) ?: break
                val cutPos = m.range.first
                val ratio = (cutPos + 1).toDouble() / segText.length.coerceAtLeast(1)
                var cutMs = segStart + ((segEnd - segStart) * ratio).toLong()
                // 切点吸附最近静音边界（±800ms），时间更准
                if (silenceBounds.isNotEmpty()) {
                    silenceBounds.filter { it in (cutMs - 800)..(cutMs + 800) }
                        .minByOrNull { kotlin.math.abs(it - cutMs) }
                        ?.let { cutMs = it }
                }
                if (cutMs <= segStart || cutMs >= segEnd) break
                final.add(Sentence(segStart, cutMs))
                segStart = cutMs
                segText = segText.substring(cutPos + 1).trimStart()
            }
            final.add(Sentence(segStart, segEnd))
        }
        return final
    }

/** 波形-歌词融合断句：
     *  句子起点 = 歌词行时间戳（每句必有歌词文本，中间无空白）；
     *  终点 = 下一个波形静音点（贴合语音停顿，歌词对上波纹）；
     *  歌词密集时（下一行歌词早于静音点）按下一行歌词边界收尾；
     *  前奏/尾奏各合并为一个句子（纯音乐段留空一行，不再大片空白）。
     *  无 LRC 时退化为纯波形断句 + 短句合并。 */
    private fun buildWaveLyricSentences(
        silenceSentences: List<Sentence>,
        lyrics: List<LrcLine>,
        totalMs: Long,
    ): List<Sentence> {
        if (lyrics.isEmpty()) return mergeShortSentences(silenceSentences)
        // 波形断句边界（静音点，去重排序）
        val silences = silenceSentences.flatMap { listOf(it.startMs, it.endMs) }
            .filter { it > 0 && it < totalMs }
            .distinct()
            .sorted()
        val result = mutableListOf<Sentence>()
        for (i in lyrics.indices) {
            val start = lyrics[i].timeMs
            val nextLyric = if (i + 1 < lyrics.size) lyrics[i + 1].timeMs else totalMs
            // 行末停顿：本行终点前 ≤1.2s 的最近静音点（真实语音结束，贴合波纹）。
            // 行内逗号等短停顿离行末远，不会被误当行末 → 整行文本完整覆盖，不截断
            val rowEnd = silences.filter { it > start && it < nextLyric && nextLyric - it <= 1200 }.maxOrNull()
            val end = rowEnd ?: nextLyric // 无行末停顿（连唱）则按下一行歌词边界收尾
            if (end > start) result.add(Sentence(start, end, lyrics[i].text))
        }
        // 前奏：0 → 第一句起点（合并为一个句子，纯音乐段）。
        // 终点钳制到波形语音起点之前（LRC 打点晚于实际语音时，避免前奏句包进歌词开头）
        val firstStart = result.firstOrNull()?.startMs ?: return result
        val firstWaveStart = silenceSentences.firstOrNull()?.startMs ?: firstStart
        val introEnd = minOf(firstStart, firstWaveStart)
        if (introEnd > 0) result.add(0, Sentence(0L, introEnd))
        // 尾奏：最后句终点 → 音频末尾（合并为一个句子）。
        // 起点钳制到波形语音终点之后（避免尾奏句包进歌词结尾）
        val lastEnd = result.lastOrNull()?.endMs ?: return result
        val lastWaveEnd = silenceSentences.lastOrNull()?.endMs ?: lastEnd
        val outroStart = maxOf(lastEnd, lastWaveEnd)
        if (outroStart < totalMs - 100) result.add(Sentence(outroStart, totalMs))
        // 短句合并（保留拼接文本）；空文本句（前奏/尾奏）不参与合并
        return mergeShortSentences(result, protectEmpty = true)
    }

    /** 短句合并：< SHORT_SENTENCE_MS 的句子与相邻句合并
     * （末句与上一句合并），循环至无短句。避免复读太碎。
     *  protectEmpty=true 时，空文本句（前奏/尾奏占位）不参与合并，
     *  歌词短句也不与空文本句合并（保持纯音乐段独立、边界干净）。 */
    private fun mergeShortSentences(list: List<Sentence>, protectEmpty: Boolean = false): List<Sentence> {
        val merged = list.toMutableList()
        var changed = true
        while (changed) {
            changed = false
            var k = 0
            while (k < merged.size) {
                val n = merged[k]
                if (n.endMs - n.startMs < SHORT_SENTENCE_MS && merged.size > 1
                    && (!protectEmpty || n.text.isNotEmpty())
                ) {
                    // 合并目标：优先下一个（protectEmpty 时需非空）
                    val next = merged.getOrNull(k + 1)
                    if (next != null && (!protectEmpty || next.text.isNotEmpty())) {
                        merged.removeAt(k + 1)
                        merged[k] = Sentence(n.startMs, next.endMs, mergeText(n.text, next.text))
                        changed = true
                        continue // 合并后可能仍短，原地继续检查
                    }
                    // 下一个不可用（没有或为空文本）：找上一个可合并的
                    var p = k - 1
                    if (protectEmpty) {
                        while (p >= 0 && merged[p].text.isEmpty()) p--
                    }
                    if (p >= 0) {
                        val prev = merged[p]
                        merged[p] = Sentence(prev.startMs, n.endMs, mergeText(prev.text, n.text))
                        merged.removeAt(k)
                        changed = true
                        k = p // 从合并处继续检查
                        continue
                    }
                }
                k++
            }
        }
        return merged
    }

    private fun mergeText(a: String, b: String): String = when {
        a.isEmpty() -> b
        b.isEmpty() -> a
        else -> "$a $b"
    }

    /** 长句切分：> LONG_SENTENCE_MS 或 > LONG_SENTENCE_CHARS 的句子，
     *  在句内最近的波形静音点处切分（切点=真实语音停顿，听感自然）；
     *  合并句（含多行歌词）优先按歌词行边界切分：时间切点=行边界附近静音点，
     *  文本按行完整归属，避免"下一句句首词串到上一句句尾"；
     *  句内无切点或切出的段太短（<短句阈值）时不硬切，保持整句。 */
    // 以下三个长句切分函数已抽到 LongSentenceSplit.kt（音频/视频共用同一套算法），
    // 这里仅做转发，调用点（buildLyrics 等）保持不变。
    private fun splitLongSentences(
        sentences: List<Sentence>,
        silenceSource: List<Sentence>,
        lyrics: List<LrcLine>,
    ) = com.xuanji.repeater.splitLongSentences(sentences, silenceSource, lyrics)

    private fun splitOneLong(s: Sentence, silences: List<Long>, lyrics: List<LrcLine>) =
        com.xuanji.repeater.splitOneLong(s, silences, lyrics)

    private fun cutTextAtWord(text: String, ratio: Double) = com.xuanji.repeater.cutTextAtWord(text, ratio)

    /** 把句子起点吸附到波形上最近的"静音→语音"跳变（±800ms 内），修正插值误差。
     *  波形帧长按实际推断（双声道音频 25ms/帧）；当前未被调用（吸附曾导致跳错句），保留备用。 */
    private fun snapToSpeechStart(startMs: Long, waveform: FloatArray, totalMs: Long): Long {
        if (waveform.isEmpty()) return startMs
        val frameMs = frameMsOf(totalMs, waveform)
        val center = (startMs / frameMs).toInt()
        val lo = (center - 16).coerceAtLeast(0)
        val hi = (center + 16).coerceAtMost(waveform.size - 1)
        // 向句尾方向找第一个能量跳变（上一帧安静、当前帧有语音）
        for (i in center..hi) {
            val prev = if (i - 1 >= lo) waveform[i - 1] else 0f
            if (waveform[i] > 0.15f && prev < 0.08f) return (i * frameMs).toLong()
        }
        // 向句首方向找
        for (i in center - 1 downTo lo) {
            val prev = if (i - 1 >= lo) waveform[i - 1] else 0f
            if (waveform[i] > 0.15f && prev < 0.08f) return (i * frameMs).toLong()
        }
        return startMs
    }

    private fun onTrackCompleted() {
        // setOnCompletionListener 在 MediaPlayer 内部线程回调，必须回主线程操作播放器
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post { onTrackCompleted() }
            return
        }
        val s = _state.value
        // 复读中到达音频末尾（最后一句复读）：跳回句首继续复读
        if (pendingSentenceIndex >= 0) {
            val sentence = s.sentences.getOrNull(pendingSentenceIndex)
            if (sentence != null && pendingRepeatLeft > 0) {
                pendingRepeatLeft--
                player?.seekTo(sentence.startMs.toInt())
                _state.value = s.copy(positionMs = sentence.startMs)
                return
            }
            // 复读完成：清除状态，恢复正常播放速度，落到下方播放模式逻辑自然续播
            pendingSentenceEnd = -1L
            pendingSentenceIndex = -1
            // 退出句子模式：歌词高亮恢复按播放位置跟随
            _state.value = _state.value.copy(currentSentenceIndex = -1)
            player?.let { applySpeed(it, _state.value.speed) }
        }
        when (s.playMode) {
            PlayMode.LOOP_ONE -> {
                player?.seekTo(0)
                player?.start()
                _state.value = s.copy(positionMs = 0)
            }
            PlayMode.SEQUENCE -> {
                if (s.currentIndex < s.playlist.size - 1) {
                    playNextIndex(s.currentIndex + 1)
                } else {
                    _state.value = s.copy(isPlaying = false, positionMs = 0)
                    player?.seekTo(0)
                    stopTicker()
                    abandonAudioFocus()
                    updateMediaSession()
                    updateNotification()
                }
            }
            PlayMode.LOOP_LIST -> {
                val next = (s.currentIndex + 1) % s.playlist.size
                playNextIndex(next)
            }
        }
    }

    private fun playNextIndex(index: Int) {
        val items = _state.value.playlist
        val item = items.getOrNull(index) ?: return
        _state.value = _state.value.copy(
            currentIndex = index,
            currentItem = item,
            sentences = emptyList(),
            currentSentenceIndex = -1,
            waveform = FloatArray(0),
            lyrics = emptyList(),
            splitting = false,
            splitProgress = -1f,
        )
        loadAndPlay(item, autoPlay = true)
    }

    fun next() {
        val s = _state.value
        if (s.playlist.isEmpty()) return
        val next = (s.currentIndex + 1) % s.playlist.size
        playNextIndex(next)
    }

    fun prev() {
        val s = _state.value
        if (s.playlist.isEmpty()) return
        val prevIdx = if (s.currentIndex > 0) s.currentIndex - 1 else s.playlist.size - 1
        playNextIndex(prevIdx)
    }

    // ---------------- 播放记忆 ----------------

    /** 保存当前播放现场（列表/索引/进度/模式） */
    fun savePlayback() {
        val s = _state.value
        if (s.playlist.isEmpty() || s.currentItem == null) return
        PlaybackPrefs.save(
            this,
            s.playlist.map { it.path },
            s.currentIndex.coerceAtLeast(0),
            s.positionMs,
            s.playMode,
        )
    }

    /** 重启后恢复播放现场；已有播放内容时忽略（避免 Activity 重建重复恢复）。
     *  按设置决定自动继续播放或仅恢复列表并暂停。 */
    fun restorePlayback() {
        val saved = PlaybackPrefs.load(this) ?: return
        if (_state.value.playlist.isNotEmpty()) return
        val all = AudioScanner.scan(this, IgnorePrefs.load(this))
        val items = saved.paths.mapNotNull { path -> all.firstOrNull { it.path == path } }
        if (items.isEmpty()) {
            PlaybackPrefs.clear(this)
            return
        }
        val idx = saved.index.coerceIn(0, items.size - 1)
        _state.value = _state.value.copy(
            playlist = items,
            currentIndex = idx,
            currentItem = items[idx],
            playMode = saved.playMode,
            sentences = emptyList(),
            currentSentenceIndex = -1,
            waveform = FloatArray(0),
            lyrics = emptyList(),
            splitting = false,
            splitProgress = -1f,
        )
        // 上次活跃的是视频：不自动续播音频（否则重开会出现「幽灵音频」与视频双声）
        val resume = PlaybackPrefs.loadResume(this) && lastMode() != "video"
        if (lastMode() == "video") {
            // 视频模式冷启：只恢复播放列表与现场元数据，不预备音频播放器。
            // 否则后台预备的音频播放器停在 PREPARED 态（autoPlay=false 从未 start()），
            // 视频接管时 attachVideoController 的 player?.pause() 会对 PREPARED 态播放器
            // 抛 -38(INVALID_OPERATION) 错误，使音频播放器进入异常态；切回音频时
            // playerPrepared 错乱 / togglePlayPause 误判而 reload，表现为进度冻住、点句子/进度失灵。
            // 音频播放器改由用户切到「音频模式」时 loadAudioFromRestoreIfNeeded() 干净加载。
            return
        }
        loadAndPlay(items[idx], autoPlay = resume, initialPositionMs = saved.positionMs)
    }

    /** 视频模式冷启后切回音频模式：按保存位置干净地加载音频播放器。
     *  restorePlayback 在视频模式不预备音频（避免 -38），故此处补加载；
     *  若音频播放器已存在则直接跳过（避免重复加载/释放）。 */
    fun loadAudioFromRestoreIfNeeded() {
        if (player != null) return
        val saved = PlaybackPrefs.load(this) ?: return
        val item = _state.value.currentItem ?: return
        val auto = PlaybackPrefs.loadResume(this)
        loadAndPlay(item, autoPlay = auto, initialPositionMs = saved.positionMs)
    }

    // ---------------- 进度刷新 ----------------

    private fun startTicker() {
        positionTicker?.cancel()
        positionTicker = serviceScope.launch {
            var tickCount = 0
            while (true) {
                delay(250)
                val p = player ?: continue
                val pos = try {
                    p.currentPosition.toLong()
                } catch (e: Exception) {
                    continue // MediaPlayer 状态异常（未 prepared/已释放）：跳过本轮，ticker 不崩
                }
                _state.value = _state.value.copy(positionMs = pos)
                // 节流保存播放现场（每 2 秒），进程被杀兜底
                if (++tickCount % 8 == 0) savePlayback()
                if (pendingSentenceEnd <= 0) continue
                if (pos >= pendingSentenceEnd - HIGH_PRECISION_WINDOW) {
                    // 接近句尾：高精度轮询。播放头越过句尾 + SEEK_AHEAD_MS（输出缓冲补偿）后
                    // 才触发边界处理——currentPosition 领先耳朵一个输出缓冲，此时耳朵恰好停在句尾
                    while (true) {
                        delay(10)
                        val p2 = player ?: break
                        val pos2 = try {
                            p2.currentPosition.toLong()
                        } catch (_: Exception) {
                            break
                        }
                        if (pos2 >= pendingSentenceEnd + SEEK_AHEAD_MS) {
                            _state.value = _state.value.copy(positionMs = pos2)
                            handleSentenceBoundary()
                            break
                        }
                    }
                }
            }
        }
    }

    /** 暂停并精确 seek，seek 完成后按 needResume 决定是否续播（杜绝 seek 窗口期播放越界内容） */
    private fun pauseSeek(mp: MediaPlayer, targetMs: Int, needResume: Boolean) {
        if (inBoundarySeek) return
        inBoundarySeek = true
        mp.pause()
        seekAccurate(mp, targetMs) {
            if (needResume && !mp.isPlaying) mp.start()
            inBoundarySeek = false
        }
    }

    /** 句子边界：复读未完成则跳回句首重播；复读完成（或单遍定位）从终点精确续播 */
    private fun handleSentenceBoundary() {
        val p = player ?: return
        if (inBoundarySeek) return
        if (pendingRepeatLeft > 0) {
            // 继续复读：暂停→seek 句首→seek 完成再播，杜绝带出下一句开头
            pendingRepeatLeft--
            pauseSeek(p, pendingSentenceStart.toInt(), needResume = true)
            _state.value = _state.value.copy(positionMs = pendingSentenceStart)
        } else {
            // 复读完成 / 单遍定位：退出句子模式
            pendingSentenceEnd = -1L
            pendingSentenceIndex = -1
            // 退出句子模式：歌词高亮恢复按播放位置跟随
            _state.value = _state.value.copy(currentSentenceIndex = -1)
            // 复读结束：恢复正常播放速度
            applySpeed(p, _state.value.speed)
            // 不再 seek 回终点：触发点已越过句尾（播放头 = endMs + 输出缓冲补偿），
            // 回退会让耳朵重听一遍句尾内容；让播放头自然继续即可
        }
    }

    private fun stopTicker() {
        positionTicker?.cancel()
        positionTicker = null
    }

    // ---------------- 音频焦点 + 视频外部控制 ----------------

    private fun setupAudioFocus() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        afChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                // 来电 / 别的音视频 App / 微信语音 等：永久或瞬时抢占 → 自动暂停
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                // 【0903 用户反馈】微信语音消息常以 MAY_DUCK 方式请求焦点；之前这里「保持播放不处理」，
                // 导致语音期间本 App 还在响（或系统只压一点音量="小声播"）。改为与瞬时抢占一样：
                // 暂停本 App，等语音放完（GAIN 回调）再自动续播，符合「微信语音不打架」的预期。
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    if (isPlayingNow()) {
                        pausedByFocusLoss = true
                        suspendPlayback()
                    }
                }
                // 重新获得焦点：若是因为被抢占才暂停的，自动续播（用户主动暂停不会续播）
                AudioManager.AUDIOFOCUS_GAIN -> {
                    if (pausedByFocusLoss) {
                        pausedByFocusLoss = false
                        resumePlayback()
                    }
                }
            }
        }
    }

    /** 当前真正在播的是哪个：视频模式下看视频控制器，否则看音频状态 */
    private fun isPlayingNow(): Boolean =
        if (videoActive && videoController != null) videoController!!.isPlaying() else _state.value.isPlaying

    /** 失去焦点时暂停：视频模式转发给视频控制器，音频模式直接暂停播放器（不释放焦点，便于续播） */
    private fun suspendPlayback() {
        if (videoActive && videoController != null) {
            videoController!!.pause()
        } else {
            val p = player
            if (p != null && p.isPlaying) {
                try { p.pause() } catch (_: Exception) {}
            }
            _state.value = _state.value.copy(isPlaying = false)
            stopTicker()
            updateMediaSession()
            updateNotification()
        }
    }

    /** 重新获得焦点时续播（仅当之前是被抢占暂停的） */
    private fun resumePlayback() {
        if (videoActive && videoController != null) videoController!!.play() else play()
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(afChangeListener)
                .build()
            am.requestAudioFocus(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(afChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(afChangeListener)
        }
    }

    /** 视频开始播放：注入控制器，统一由本服务接管媒体中心/通知/音频焦点 */
    fun attachVideoController(controller: ExternalMediaController) {
        videoController = controller
        videoActive = true
        // 视频接管时停掉音频服务自身播放器，避免「视频在播 + 旧音频还在播」双声
        runCatching { if (player?.isPlaying == true) player?.pause() }
        // 清掉音频状态里残留的「播放中」：否则视频退出后台后媒体中心仍读 _state.isPlaying=true，
        // 焦点重获时 resumePlayback 会误把音频播放器重新拉起来（"媒体中心点回去自动播"的幽灵音频）。
        _state.value = _state.value.copy(isPlaying = false)
        pausedByFocusLoss = false
        markLastMode("video")
        requestAudioFocus()
        updateMediaSession()
        updateNotification()
    }

    /** 退出视频模式：交还控制权，恢复纯音频的媒体中心/通知/焦点 */
    fun detachVideoController() {
        videoController = null
        videoActive = false
        pausedByFocusLoss = false
        abandonAudioFocus()
        updateMediaSession()
        updateNotification()
    }

    /** 进视频/阅读模式或打开视频时调用：停掉音频模式自身播放器，
     *  避免冷启动自动续播的「幽灵音频」与视频/阅读双声。 */
    fun stopAudioForVideo() {
        runCatching { player?.pause() }
        pausedByFocusLoss = false
        updateMediaSession()
        updateNotification()
    }


    /** 媒体中心当前委托的是不是这个控制器（供 MainActivity.onDestroy 判断是否要摘掉已释放的阅读播放器） */
    fun isControllerActive(c: ExternalMediaController?): Boolean = videoActive && videoController === c

    /** 视频播放状态变化（播放/暂停/seek/跳句）后刷新媒体中心；仅视频激活时有效 */
    fun notifyVideoStateChanged() {
        if (videoActive) {
            updateMediaSession()
            updateNotification()
        }
    }

    // ---------------- MediaSession / 通知 ----------------

    private fun setupMediaSession() {
        val session = MediaSession(this, "XuanJiPlayer")
        mediaSession = session
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() =
                if (videoActive && videoController != null) videoController!!.play() else play()
            override fun onPause() =
                if (videoActive && videoController != null) videoController!!.pause() else pause()
            override fun onSkipToNext() =
                if (videoActive && videoController != null) videoController!!.skipNext() else nextSentence()
            override fun onSkipToPrevious() =
                if (videoActive && videoController != null) videoController!!.skipPrev() else prevSentence()
            override fun onStop() {
                if (videoActive && videoController != null) {
                    // 视频模式：仅暂停，控制权由 MainActivity 在切走时 detach
                    videoController!!.pause()
                } else {
                    pause()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    foregroundActive = false
                    stopSelf()
                }
            }
        })
        session.isActive = true
    }

    private fun updateMediaSession() {
        val session = mediaSession ?: return
        // 视频模式：状态/元数据取自外部控制器；音频模式：取自内部播放状态
        val isPlaying: Boolean
        val positionMs: Long
        val durationMs: Long
        val title: String
        if (videoActive && videoController != null) {
            isPlaying = videoController!!.isPlaying()
            positionMs = videoController!!.position()
            durationMs = videoController!!.duration()
            title = videoController!!.title()
        } else {
            val s = _state.value
            isPlaying = s.isPlaying
            positionMs = s.positionMs
            durationMs = s.durationMs
            title = s.currentItem?.title ?: "小七复读机"
        }
        val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val pb = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP
            )
            .setState(state, positionMs, 1f)
            .build()
        session.setPlaybackState(pb)
        session.setMetadata(
            android.media.MediaMetadata.Builder()
                .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(android.media.MediaMetadata.METADATA_KEY_ALBUM, "句子复读")
                .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, durationMs)
                // 媒体中心/锁屏封面 = 旧版应用图标
                .putBitmap(
                    android.media.MediaMetadata.METADATA_KEY_ART,
                    BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
                )
                .build()
        )
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val s = _state.value
        val session = mediaSession
        // 视频模式下通知的标题/播放态取自外部控制器
        val isPlaying = if (videoActive && videoController != null) videoController!!.isPlaying() else s.isPlaying
        val title = if (videoActive && videoController != null) videoController!!.title()
                    else (s.currentItem?.title ?: "小七复读机")
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIntent = PendingIntent.getService(
            this, 1,
            Intent(this, PlayerService::class.java).setAction(ACTION_PLAY_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextIntent = PendingIntent.getService(
            this, 2,
            Intent(this, PlayerService::class.java).setAction(ACTION_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val prevIntent = PendingIntent.getService(
            this, 3,
            Intent(this, PlayerService::class.java).setAction(ACTION_PREV),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 4,
            Intent(this, PlayerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
        // 标准媒体通知：关联 MediaSession → 系统媒体中心（下拉控制面板）可显示小窗
        if (session != null) {
            builder.setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_media_play)
            // 媒体卡片左上角大图标 = 旧版应用图标（替代系统三角）
            .setLargeIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
            .setContentTitle(title)
            .setContentText(if (isPlaying) "播放中" else "已暂停")
            .setContentIntent(contentIntent)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "上一句", prevIntent)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "暂停" else "播放",
                playPauseIntent
            )
            .addAction(android.R.drawable.ic_media_next, "下一句", nextIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopIntent)
            .setPriority(Notification.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "播放控制", NotificationManager.IMPORTANCE_LOW
            )
            channel.setShowBadge(false)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}