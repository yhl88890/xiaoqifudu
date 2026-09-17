package com.xuanji.repeater

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * 悬浮窗服务：跨应用显示当前句歌词文本（跟读用）。
 * - 半透明圆角卡片，可拖动
 * - 单击：播放/暂停
 * - 内容跟随播放句实时更新（无句子时显示曲目名）
 */
class FloatLyricsService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null
    private var textView: TextView? = null
    private var lp: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        addFloatView()
        observeState()
    }

    override fun onDestroy() {
        removeFloatView()
        scope.cancel()
        super.onDestroy()
    }

    private fun addFloatView() {
        val tv = TextView(this).apply {
            text = "小七复读机"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            maxLines = 3
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(0xCC222222.toInt())
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(20)
            y = dp(220)
        }
        lp = params

        // 拖动 + 单击播放/暂停
        var startX = 0
        var startY = 0
        var startRawX = 0f
        var startRawY = 0f
        var moved = false
        tv.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    startRawX = e.rawX
                    startRawY = e.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - startRawX).toInt()
                    val dy = (e.rawY - startRawY).toInt()
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) moved = true
                    if (moved) {
                        params.x = startX + dx
                        params.y = startY + dy
                        try {
                            windowManager?.updateViewLayout(v, params)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) PlayerService.instance?.togglePlayPause()
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(tv, params)
            textView = tv
        } catch (e: Exception) {
            android.util.Log.e("FloatLyrics", "addView failed", e)
        }
    }

    private fun removeFloatView() {
        try {
            textView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {
        }
        textView = null
    }

    private fun observeState() {
        scope.launch {
            combine(
                PlaybackSession.activeKind,
                PlaybackSession.floatText,
                playerStateFlow(),
            ) { kind, videoText, st -> Triple(kind, videoText, st) }
                .collectLatest { (kind, videoText, st) ->
                    val tv = textView ?: return@collectLatest
                    // 视频字幕走 MainActivity 推上来的那条窄通道；音频直接读 PlayerService 的状态
                    val text = if (kind == MediaKind.VIDEO) videoText else currentSentenceText(st)
                    // 最终防线：任何路径都不允许把空白文本上屏（避免悬浮窗变成无字黑块）
                    tv.text = text.ifEmpty { "小七复读机" }
                }
        }
    }

    /**
     * 音频侧的 PlayerState 流。
     *
     * 🔴 **不能像以前那样 `while (svc == null) delay(500)` 死等**：
     * 纯视频模式下 PlayerService 可能压根没起来（用户没播过音频），
     * 那样悬浮窗会永远卡在初始文案，看起来就是"悬浮窗坏了"。
     * 现在没就绪就先发一条空 state，等它起来再切到真流。
     */
    private fun playerStateFlow(): Flow<PlayerState> = flow {
        while (true) {
            val svc = PlayerService.instance
            if (svc != null) {
                emitAll(svc.state)
                return@flow
            }
            emit(PlayerState())
            delay(500)
        }
    }

    /**
     * 音频模式的当前句文本。兜底顺序：**断句文本 → LRC 歌词 → 曲目名**。
     *
     * 🔴【0917 阿亮报「悬浮窗只显示曲目名」】根因就在下面那个 `idx`：
     * 原来用的是 `state.currentSentenceIndex`，而**整曲播放时它恒为 -1**
     * （`PlayerService` 只在「按句复读」时才把它置成 >= 0）⇒ 一路掉到 lyrics 分支。
     * 以前他放的是 LRC 歌词，能显示；换成**自己生成的断句字幕**（没有 LRC）就只剩曲目名
     * —— 这就是"以前成功过、现在不行"的原因。
     *
     * 正解是 `activeSentenceIndex`：句子模式走 currentSentenceIndex，
     * 整曲播放按播放位置在 `sentences` 里定位
     * （那个属性本来就是为「歌词高亮 / 悬浮窗跟随」写的，见 `PlayerState`）。
     */
    private fun currentSentenceText(state: PlayerState): String {
        // title 可能为空串（空串不会被 ?: 兜底），统一兜底
        val title = state.currentItem?.title?.ifEmpty { null } ?: "小七复读机"
        val idx = state.activeSentenceIndex
        if (idx >= 0) {
            // 断句文本可能是空的（那段没识别出文字）⇒ 这时要退回 LRC，而不是直接甩曲目名
            val t = state.sentences.getOrNull(idx)?.text.orEmpty()
            if (t.isNotBlank()) return t
        }
        // 整曲播放：取当前播放位置之前最近的歌词行，实现歌词滚动跟进
        val lyric = state.lyrics.lastOrNull { it.timeMs <= state.positionMs } ?: return title
        return lyric.text
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}