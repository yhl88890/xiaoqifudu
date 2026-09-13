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
    private val _events = MutableSharedFlow<BiliImportEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<BiliImportEvent> = _events.asSharedFlow()

    fun post(event: BiliImportEvent) {
        _events.tryEmit(event)
    }
}
