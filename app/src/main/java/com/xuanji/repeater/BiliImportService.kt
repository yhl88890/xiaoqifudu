package com.xuanji.repeater

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 抓 B站 视频的前台服务：把「下载 + 转音频 + 云端识别 + 合成字幕」这一长流程放到前台服务里跑，
 * 这样切到后台（看其他视频）时系统不会因后台限制把转换/识别掐掉（需求 #4）。
 * 完成后发广播 [ACTION_DONE]，由 MainActivity 自动打开并播放（需求 #5）。
 */
class BiliImportService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL) ?: ""
        val translate = intent?.getBooleanExtra(EXTRA_TRANSLATE, false) ?: false
        if (url.isBlank()) { stopSelf(); return START_NOT_STICKY }

        startForeground(NOTIF_ID, buildNotification("正在抓取 B站 视频…"))
        scope.launch {
            try {
                val result = BiliImport.import(this@BiliImportService, url, translate) { p ->
                    updateNotification("正在抓取 B站 视频…\n$p")
                    // 进度回灌进 App（通知栏之外，App 内常驻状态文字也实时更新，
                    // 避免用户觉得"提交后没反应 / 进度看不见"）。
                    BiliImportBus.post(BiliImportEvent.Progress(p))
                }
                BiliImportBus.post(
                    BiliImportEvent.Done(
                        ok = true,
                        path = result.mediaFile.absolutePath,
                        videoMode = result.videoMode,
                        title = result.title,
                        note = result.note,
                        error = null
                    )
                )
                updateNotification("抓取完成：${result.title}")
            } catch (e: Throwable) {
                BiliImportBus.post(
                    BiliImportEvent.Done(
                        ok = false,
                        path = null,
                        videoMode = true,
                        title = null,
                        note = null,
                        error = e.message ?: "抓取失败"
                    )
                )
                updateNotification("抓取失败：${e.message}")
            } finally {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(CHANNEL_ID, "B站抓取", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("小七复读机 · 抓 B站")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    companion object {
        const val CHANNEL_ID = "bili_import"
        const val NOTIF_ID = 9001
        const val ACTION_DONE = "com.xuanji.repeater.BILI_IMPORT_DONE"
        const val ACTION_PROGRESS = "com.xuanji.repeater.BILI_IMPORT_PROGRESS"
        const val EXTRA_URL = "url"
        const val EXTRA_TRANSLATE = "translate"
        const val EXTRA_OK = "ok"
        const val EXTRA_PATH = "path"
        const val EXTRA_VIDEO_MODE = "videoMode"
        const val EXTRA_TITLE = "title"
        const val EXTRA_NOTE = "note"
        const val EXTRA_ERROR = "error"
        const val EXTRA_PROGRESS = "progress"
    }
}
