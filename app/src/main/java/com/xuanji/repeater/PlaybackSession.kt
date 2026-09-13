package com.xuanji.repeater

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 播放源类型：当前「在用」的播放通道 */
enum class MediaKind { AUDIO, VIDEO }

/**
 * 播放会话真值 —— Phase 3「统一状态层」的第一块砖。
 *
 * 【为什么需要】
 * 音频走 PlayerService（一个前台 Service），视频走 MainActivity 里的 MediaPlayer，两套引擎各自
 * 维护自己的状态。「当前到底在用哪一路」这条信息现在散落在 page / readingFromVideo / videoPlaying /
 * playing 好几处，没有一个统一答案 —— 每次要判断都得重新拼一遍，是 bug 的温床。
 *
 * 【本对象职责】
 * 只记录一件事：最后开始播放的是哪一路。这是唯一真值。
 *
 * 【步骤 1 纪律 · 行为零变化】
 * 本真值目前【不参与任何】播放/暂停/存储决策，只被写入并打 XJ_KIND 日志。
 * 等验证它记得准了，后续步骤才让它逐步接管判定。所以本次改动不改变任何现有行为。
 */
object PlaybackSession {

    private val _activeKind = MutableStateFlow(MediaKind.AUDIO)

    /** 当前活动播放源（冷启默认音频，与启动页一致） */
    val activeKind: StateFlow<MediaKind> = _activeKind.asStateFlow()

    /** 当前活动播放源（同步读取，供非协程场景/判定用） */
    val current: MediaKind get() = _activeKind.value

    /** 音频开始播放时调用（PlayerService 内三处「开始发声」路径） */
    fun onAudioStarted() = mark(MediaKind.AUDIO, "audio")

    /** 视频开始播放 / 被打开时调用（MainActivity 内视频发声路径） */
    fun onVideoStarted() = mark(MediaKind.VIDEO, "video")

    /** 冷启恢复：上次退出时停在视频模式，真值也要跟过去，否则开局就记错 */
    fun restore(kind: MediaKind) = mark(kind, "restore")

    private fun mark(kind: MediaKind, whence: String) {
        if (_activeKind.value == kind) return
        _activeKind.value = kind
        Log.d("XJ_KIND", "activeKind -> $kind (from=$whence)")
    }
}
