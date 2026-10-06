package com.xuanji.repeater

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 睡眠定时器（1007 新增）：倒计时到点**暂停播放**（音频/视频各停各的），不杀 App。
 *
 * - 入口：顶栏小闹钟按钮（音频/视频模式顶栏共用一个按钮），选项 15/30/45/60/90 分钟 + 取消；
 * - 剩余时间由 [endsAt] 暴露给顶栏按钮直接显示（阿亮："倒计时显示在按钮上，小一点，不是重要信息"）；
 * - 不持久化：App 进程被杀定时随进程消失（睡前场景可接受，重设也只要一下）；
 * - 到点动作只做「暂停」，不碰播放逻辑本身：音频调 PlayerService.pause()，视频调 videoTogglePlay()。
 */
object SleepTimer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 定时结束时刻（epoch ms）；0 = 未启用 */
    private val _endsAt = MutableStateFlow(0L)
    val endsAt = _endsAt.asStateFlow()

    /** 每秒心跳（epoch ms）：endsAt 不变也要驱动顶栏按钮上的剩余时间每秒刷新 */
    private val _tick = MutableStateFlow(0L)
    val tick = _tick.asStateFlow()

    private var job: Job? = null

    fun active(): Boolean = _endsAt.value > System.currentTimeMillis()

    /** 剩余毫秒（未启用 = 0） */
    fun remainingMs(): Long = (_endsAt.value - System.currentTimeMillis()).coerceAtLeast(0)

    /** 启动/重设倒计时（分钟） */
    fun start(minutes: Long) {
        cancel()
        if (minutes <= 0) return
        _endsAt.value = System.currentTimeMillis() + minutes * 60_000
        job = scope.launch {
            while (System.currentTimeMillis() < _endsAt.value) {
                delay(1000)
                _tick.value = System.currentTimeMillis()
            }
            _endsAt.value = 0
            _tick.value = 0
            // 到点：两边播放器都尝试暂停（正在播的那个才会真停，另一个调用是安全的空操作）
            if (videoPlaying) videoTogglePlay()          // 视频手搓引擎：在播才暂停
            runCatching { PlayerService.instance?.pause() }  // 音频引擎：pause 本身幂等
        }
    }

    /** 取消定时 */
    fun cancel() {
        job?.cancel()
        job = null
        _endsAt.value = 0
        _tick.value = 0
    }
}
