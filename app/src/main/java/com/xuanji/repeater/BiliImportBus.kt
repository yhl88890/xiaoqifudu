package com.xuanji.repeater

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
/**
 * 抓 B站 进度/完成事件总线：前台服务 [BiliImportService] 跑完长流程后，
 * 通过进程内 SharedFlow 把结果回传给 MainActivity（替代跨组件广播）。
 *
 * 为什么不用广播：在 Android 14（targetSdk 35）上，用 [android.content.Context.RECEIVER_NOT_EXPORTED]
 * 注册的上下文接收器收不到本 App 内 sendBroadcast 发出的隐式广播，导致"提交后没反应/进度看不见"。
 * 同进程内改用 SharedFlow 最稳妥，也无广播权限/跨进程投递的坑。
 */
sealed interface BiliImportEvent {
    data class Progress(val text: String) : BiliImportEvent
    data class Done(
        val ok: Boolean,
        val path: String?,
        val videoMode: Boolean,
        val title: String?,
        val note: String?,
        val error: String?
    ) : BiliImportEvent
}

object BiliImportBus {
    /**
     * 🔴 【1003 P1 修】进度事件：replay=0（只要有人在听就实时给，不需要补发）。
     */
    private val _progress = MutableSharedFlow<BiliImportEvent>(extraBufferCapacity = 16)

    /**
     * 🔴 【1003 P1 修】**完成事件必须 replay=1**。
     *
     * 原来两条流合在一起、replay 都是 0 ⇒ `tryEmit` 在**零订阅者**时直接丢弃。
     * 而唯一订阅者在 MainActivity 的 setContent 里、生命周期绑 Activity
     * ⇒ 抓取中 Activity 被销毁（内存压力 / 用户开了"不保留活动"）时，
     * 前台服务照样把活干完，但 `post(Done)` 时订阅者为 0 ⇒ **结果被吞**：
     * 用户回来看到"后台抓取中…"永远停住，视频不打开也不报错（文件其实已经在手机里）。
     *
     * 这与"用前台服务就是为了 Activity 没了也能把活干完"的设计前提直接冲突。
     * replay=1 ⇒ 完成事件会**存住**，下次进 App 一定收得到（消费后调 [consumeLatestDone] 清掉，
     * 避免下次进来又播一遍旧结果）。
     */
    private val _done = MutableSharedFlow<BiliImportEvent>(replay = 1, extraBufferCapacity = 4)

    /**
     * ⚠️ **刻意不 merge 成一条流**，收集端请分别订阅 [progress] 与 [done]。
     *
     * 原因：merge() 得到的是冷 Flow，用 `merge(...).shareIn(...)` 再包一层 **replay 只能是 0**，
     * 一旦下游（Activity）没订阅就照样丢事件 —— 那等于白修。
     * 只有让 Activity **直接订阅 [done] 这个 replay=1 的 SharedFlow**，replay 才真正管用。
     */
    val progress: SharedFlow<BiliImportEvent> = _progress.asSharedFlow()
    val done: SharedFlow<BiliImportEvent> = _done.asSharedFlow()

    fun post(event: BiliImportEvent) {
        if (event is BiliImportEvent.Done) _done.tryEmit(event) else _progress.tryEmit(event)
    }

    /** 收集端处理完 Done 后调用：清掉 replay 缓存，保证"下一次导入"不会被旧结果干扰 */
    fun consumeLatestDone() {
        _done.resetReplayCache()
    }
}
