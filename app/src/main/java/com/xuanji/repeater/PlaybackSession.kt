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

    /**
     * 应用 Application Context（进程级，只存 Application Context，绝不存 Activity，避免泄漏）。
     *
     * 【为什么放这里】文件级/非 @Composable 的顶层函数（如 videoJumpSentence）拿不到 LocalContext，
     * 但又需要读偏好设置（如 RepeatPrefs 的重播次数）。MainActivity 在 onCreate 里调 [attachContext]
     * 挂上即可。放在「播放会话」这个对象里而不是新建一个单例，是因为这里本就是会话级共享状态的落脚点。
     */
    private var appContext: android.content.Context? = null

    /** 挂载应用级 Context（MainActivity.onCreate 调用，幂等） */
    fun attachContext(ctx: android.content.Context) {
        appContext = ctx.applicationContext
    }

    /** 供文件级函数读偏好用；未挂载时返回 null（调用方需自行兜底） */
    val context: android.content.Context? get() = appContext

    private val _activeKind = MutableStateFlow(MediaKind.AUDIO)

    /** 当前活动播放源（冷启默认音频，与启动页一致） */
    val activeKind: StateFlow<MediaKind> = _activeKind.asStateFlow()

    /** 当前活动播放源（同步读取，供非协程场景/判定用） */
    val current: MediaKind get() = _activeKind.value

    /** 音频开始播放时调用（PlayerService 内三处「开始发声」路径） */
    fun onAudioStarted() = mark(MediaKind.AUDIO, "audio")

    /** 视频开始播放 / 被打开时调用（MainActivity 内视频发声路径） */
    fun onVideoStarted() = mark(MediaKind.VIDEO, "video")

    // 【已删除 restore(kind)】原打算用持久化的「上次页面」在冷启早期先恢复真值，但实测它与
    // setSessionSource 的推导打架：持久化值未必被实际采纳（视频 Uri 失效时页面会回落到音频阅读），
    // 结果先写 VIDEO 再被纠正为 AUDIO，白闪一次。真值只留一个裁决者：setSessionSource。

    /**
     * 会话源同步（步骤2）：页面/来源一变就写。
     *
     * 【为什么需要】「当前是不是视频源」这段推导原先在 MainActivity 里被抄了 6 遍，
     * 写法还各不相同 —— `page == VIDEO`、`page == VIDEO || READING && readingFromVideo`…
     * 顶栏「生成字幕」按钮那处只写了 `page == VIDEO`，漏掉「从视频进阅读 / 生词本」，
     * 结果按钮错判成音频：显示禁用、或点下去给音频生成字幕。抄多遍必漏，这就是收敛的理由。
     *
     * 统一的推导规则（与选集/常驻视频层保持一致）：
     *   视频模式，或「从视频来源进入的阅读/生词本」→ VIDEO；其余 → AUDIO。
     *
     * 边界：文件夹页刻意不进推导 —— 它只是「选择器」还不是会话（真值会在进文件夹时短暂回落 AUDIO，
     * 选中视频后立刻由 onVideoStarted 纠正为 VIDEO）。这样做保证本次改动对文件夹页行为零影响。
     */
    fun setSessionSource(isVideo: Boolean) =
        mark(if (isVideo) MediaKind.VIDEO else MediaKind.AUDIO, "session")

    private fun mark(kind: MediaKind, whence: String) {
        if (_activeKind.value == kind) return
        _activeKind.value = kind
        Log.d("XJ_KIND", "activeKind -> $kind (from=$whence)")
    }

    private val _floatText = MutableStateFlow("")

    /**
     * 视频模式下的「当前句字幕文本」，专供悬浮窗读。
     *
     * 【为什么音频不用它】音频的悬浮窗可以直接读 `PlayerService.state`（同一个进程）。
     * 而**视频的字幕只活在 MainActivity 的变量里** —— 视频是自建 ExoPlayer、画面绑在 View 上，
     * 搬不进 Service ⇒ 悬浮窗隔着进程拿不到。这里开一条**只放文本**的窄通道：
     * MainActivity 把当前句推上来，悬浮窗读走。
     *
     * ⚠️ 只放「文本」这一个字段。别顺手把视频的播放状态整包搬进来 ——
     * 那会变成第二套状态真值，跟 `activeKind` 打架（0916 那个 `restore()` 就是这么被删掉的）。
     */
    val floatText: StateFlow<String> = _floatText.asStateFlow()

    /** MainActivity 在视频当前句变化时调用（同一句重复调用无副作用） */
    fun setFloatText(text: String) {
        if (_floatText.value == text) return
        _floatText.value = text
    }
}
