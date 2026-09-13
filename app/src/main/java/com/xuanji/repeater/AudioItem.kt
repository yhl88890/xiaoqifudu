package com.xuanji.repeater

/** 一条音频（用于选集列表） */
data class AudioItem(
    val id: Long,
    val path: String,
    val title: String,
    val durationMs: Long = 0L,
    /** 同目录是否存在同名 .lrc 歌词 */
    val hasLrc: Boolean = false,
)

/** 一个视频（用于文件夹页视频类型列表） */
data class VideoItem(
    val id: Long,
    val path: String,
    val title: String,
    val durationMs: Long = 0L,
    /** 同目录是否存在同名 .srt 字幕 */
    val hasSrt: Boolean = false,
)

/** 文件夹内展示哪一类：音频 / 视频（用户切换用，解决「这个文件夹里到底是不是全是音频」的困惑） */
enum class MediaType { AUDIO, VIDEO }

/**
 * 一个媒体文件夹（按父目录分组，供文件夹页展示）。
 * 音频与视频放在同一个文件夹里也能一起列出来，靠 [audios] / [videos] 分开计；
 * 新增 videos 字段时给了默认值，老调用点（删除/重命名等）不受影响。
 */
data class FolderItem(
    val name: String,
    val path: String,
    val audios: List<AudioItem>,
    val totalMs: Long,
    val videos: List<VideoItem> = emptyList(),
)

/** 文件名（去扩展名）：如 085&086－Paris in the Spring.mp3 → 085&086－Paris in the Spring */
internal fun fileNameWithoutExt(path: String): String {
    val name = path.substringAfterLast('/')
    return name.substringBeforeLast('.', name)
}
