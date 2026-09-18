package com.xuanji.repeater

/**
 * 对外的媒体控制接口：音频走 PlayerService 实现，视频模式时把控制权
 * 转发到视频；切走视频模式时 detach，恢复成纯音频控制。
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
