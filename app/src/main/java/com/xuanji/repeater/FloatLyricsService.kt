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
import kotlinx.coroutines.flow.collectLatest
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
            // 等待 PlayerService 就绪（播放器常驻前台，通常立即可用）
            var svc = PlayerService.instance
            while (svc == null) {
                delay(500)
                svc = PlayerService.instance
            }
            svc.state.collectLatest { state ->
                val tv = textView ?: return@collectLatest
                val text = currentSentenceText(state)
                android.util.Log.d("FloatLyrics", "update text=[$text] idx=${state.currentSentenceIndex} lyrics=${state.lyrics.size}")
                // 最终防线：任何路径都不允许把空白文本上屏（避免悬浮窗变成无字黑块）
                tv.text = text.ifEmpty { "小七复读机" }
            }
        }
    }

    /** 当前句文本：优先歌词行，无歌词则显示曲目名；整曲播放时按位置自动跟进歌词 */
    private fun currentSentenceText(state: PlayerState): String {
        // title 可能为空串（空串不会被 ?: 兜底），统一兜底
        val title = state.currentItem?.title?.ifEmpty { null } ?: "小七复读机"
        val idx = state.currentSentenceIndex
        if (idx >= 0) {
            val sentence = state.sentences.getOrNull(idx) ?: return title
            // 句子文本由 service 端按歌词重叠归属算好；无文本显示标题
            return sentence.text.ifEmpty { title }
        }
        // 整曲播放：取当前播放位置之前最近的歌词行，实现歌词滚动跟进
        val lyric = state.lyrics.lastOrNull { it.timeMs <= state.positionMs } ?: return title
        return lyric.text
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}