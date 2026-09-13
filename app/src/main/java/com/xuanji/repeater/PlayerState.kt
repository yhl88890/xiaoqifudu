package com.xuanji.repeater

/** 一句话的时间区间（毫秒）+ 归属歌词文本（service 端按歌词行重叠归属算好，可能为空） */
data class Sentence(
    val startMs: Long,
    val endMs: Long,
    val text: String = "",
)

/** 播放器全局状态（UI 通过 StateFlow 观察） */
data class PlayerState(
    val isPlaying: Boolean = false,
    val playMode: PlayMode = PlayMode.SEQUENCE,
    val playlist: List<AudioItem> = emptyList(),
    val currentIndex: Int = -1,
    val currentItem: AudioItem? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val sentences: List<Sentence> = emptyList(),
    /** 当前播放句子索引，-1 表示未按句子播放 */
    val currentSentenceIndex: Int = -1,
    /** 波形帧数据（50ms/帧，归一化 0..1，用于绘制） */
    val waveform: FloatArray = FloatArray(0),
    /** 当前音频的 LRC 歌词（按时间升序，用于阅读模式文本） */
    val lyrics: List<LrcLine> = emptyList(),
    /** 是否正在断句分析 */
    val splitting: Boolean = false,
    /** 是否正在在线识别生成歌词 */
    val recognizing: Boolean = false,
    /** 逐段识别进度（如 "12/40"），空串表示无进度信息 */
    val recognizeProgress: String = "",
    /** 断句进度 0..1，-1 表示无任务 */
    val splitProgress: Float = -1f,
    /** 播放/复读速度倍率（默认 1.0x） */
    val speed: Float = 1f,
    /** 当前音频的原始静音边界段（未做最短句长合并，供在线识别文本对齐用） */
    val rawSegments: List<Sentence> = emptyList(),
) {
    val isSentenceMode: Boolean get() = sentences.isNotEmpty() && currentSentenceIndex >= 0

    /**
     * 当前生效句子索引：
     * - 句子模式（点击句子复读）→ currentSentenceIndex
     * - 整曲播放 → 按播放位置定位所在句子（供歌词高亮/悬浮窗跟随）
     * 定位带 250ms 容差：播放起点会在句首前静音区提前最多 250ms（防 seek 落点偏晚），
     * 容差保证这段时间仍归入本句，高亮不会先跳上一句再跳回来。
     */
    val activeSentenceIndex: Int
        get() {
            if (currentSentenceIndex >= 0) return currentSentenceIndex
            if (sentences.isEmpty()) return -1
            val pos = positionMs + 250
            var idx = -1
            for (i in sentences.indices) {
                if (sentences[i].startMs <= pos) idx = i else break
            }
            return idx
        }
}