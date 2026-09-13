package com.xuanji.repeater

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 手绘矢量图标（避免引入 material-icons-extended 大库）。
 * 颜色会被 Icon 的 tint 覆盖，这里统一用黑色。
 */
object XuanjiIcons {

    /** 暂停：两条竖条 */
    val Pause: ImageVector by lazy {
        ImageVector.Builder(
            name = "Pause", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 5f)
                lineTo(10f, 5f)
                lineTo(10f, 19f)
                lineTo(6f, 19f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(14f, 5f)
                lineTo(18f, 5f)
                lineTo(18f, 19f)
                lineTo(14f, 19f)
                close()
            }
        }.build()
    }

    /** 上一句：双左三角（◀◀） */
    val Prev: ImageVector by lazy {
        ImageVector.Builder(
            name = "Prev", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(14f, 5f)
                lineTo(14f, 19f)
                lineTo(6f, 12f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(21f, 5f)
                lineTo(21f, 19f)
                lineTo(13f, 12f)
                close()
            }
        }.build()
    }

    /** 上一曲：竖线 + 左三角（|◀） */
    val PrevTrack: ImageVector by lazy {
        ImageVector.Builder(
            name = "PrevTrack", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 5f)
                lineTo(9f, 5f)
                lineTo(9f, 19f)
                lineTo(6f, 19f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(15f, 5f)
                lineTo(15f, 19f)
                lineTo(7f, 12f)
                close()
            }
        }.build()
    }

    /** 下一句：双右三角（▶▶） */
    val Next: ImageVector by lazy {
        ImageVector.Builder(
            name = "Next", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(10f, 5f)
                lineTo(10f, 19f)
                lineTo(18f, 12f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 5f)
                lineTo(3f, 19f)
                lineTo(11f, 12f)
                close()
            }
        }.build()
    }

    /** 下一曲：竖线 + 右三角（▶|） */
    val NextTrack: ImageVector by lazy {
        ImageVector.Builder(
            name = "NextTrack", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(7f, 5f)
                lineTo(7f, 19f)
                lineTo(15f, 12f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(16f, 5f)
                lineTo(19f, 5f)
                lineTo(19f, 19f)
                lineTo(16f, 19f)
                close()
            }
        }.build()
    }

    /** 文件夹（顶栏右上角入口） */
    val Folder: ImageVector by lazy {
        ImageVector.Builder(
            name = "Folder", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(10f, 4f)
                horizontalLineTo(4f)
                curveTo(2.9f, 4f, 2.01f, 4.9f, 2.01f, 6f)
                lineTo(2f, 18f)
                curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f)
                horizontalLineTo(20f)
                curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f)
                lineTo(22f, 8f)
                curveTo(22f, 6.9f, 21.1f, 6f, 20f, 6f)
                horizontalLineTo(12f)
                lineTo(10f, 4f)
                close()
            }
        }.build()
    }

    /** 列表循环：双箭头循环圈 */
    val Repeat: ImageVector by lazy {
        ImageVector.Builder(
            name = "Repeat", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(7f, 7f)
                lineTo(17f, 7f)
                lineTo(17f, 10f)
                lineTo(21f, 6f)
                lineTo(17f, 2f)
                lineTo(17f, 5f)
                lineTo(5f, 5f)
                lineTo(5f, 11f)
                lineTo(7f, 11f)
                lineTo(7f, 7f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(17f, 17f)
                lineTo(7f, 17f)
                lineTo(7f, 14f)
                lineTo(3f, 18f)
                lineTo(7f, 22f)
                lineTo(7f, 19f)
                lineTo(19f, 19f)
                lineTo(19f, 13f)
                lineTo(17f, 13f)
                lineTo(17f, 17f)
                close()
            }
        }.build()
    }

    /** 单曲循环：循环圈 + 数字 1 */
    val RepeatOne: ImageVector by lazy {
        ImageVector.Builder(
            name = "RepeatOne", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(7f, 7f)
                lineTo(17f, 7f)
                lineTo(17f, 10f)
                lineTo(21f, 6f)
                lineTo(17f, 2f)
                lineTo(17f, 5f)
                lineTo(5f, 5f)
                lineTo(5f, 11f)
                lineTo(7f, 11f)
                lineTo(7f, 7f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(17f, 17f)
                lineTo(7f, 17f)
                lineTo(7f, 14f)
                lineTo(3f, 18f)
                lineTo(7f, 22f)
                lineTo(7f, 19f)
                lineTo(19f, 19f)
                lineTo(19f, 13f)
                lineTo(17f, 13f)
                lineTo(17f, 17f)
                close()
            }
            // 数字 1
            path(fill = SolidColor(Color.Black)) {
                moveTo(13f, 15f)
                lineTo(13f, 9f)
                lineTo(12f, 9f)
                lineTo(10f, 10f)
                lineTo(10f, 11f)
                lineTo(11.5f, 11f)
                lineTo(11.5f, 15f)
                lineTo(13f, 15f)
                close()
            }
        }.build()
    }

    /** 书本（阅读模式入口） */
    val Book: ImageVector by lazy {
        ImageVector.Builder(
            name = "Book", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 4f)
                lineTo(20f, 4f)
                lineTo(20f, 20f)
                lineTo(4f, 20f)
                lineTo(4f, 4f)
                moveTo(12f, 4f)
                lineTo(12f, 20f)
                moveTo(7f, 9f)
                lineTo(11f, 9f)
                moveTo(7f, 13f)
                lineTo(11f, 13f)
            }
        }.build()
    }

    /** 翻译：A + 文（中英互译） */
    val Translate: ImageVector by lazy {
        ImageVector.Builder(
            name = "Translate", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 字母 A
                moveTo(2.5f, 12f)
                lineTo(5.5f, 3.5f)
                lineTo(8.5f, 12f)
                moveTo(3.6f, 9f)
                lineTo(7.4f, 9f)
                // 汉字 文（点 / 横 / 撇捺）
                moveTo(17.5f, 3.5f)
                lineTo(17.5f, 5.5f)
                moveTo(14.5f, 7f)
                lineTo(20.5f, 7f)
                moveTo(16f, 7f)
                lineTo(13.5f, 11.5f)
                moveTo(16f, 7f)
                lineTo(19.5f, 12f)
            }
        }.build()
    }

    /** 生词本：书本 + 右下角加号 */
    val Vocab: ImageVector by lazy {
        ImageVector.Builder(
            name = "Vocab", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3.5f, 3.5f)
                lineTo(20.5f, 3.5f)
                lineTo(20.5f, 20.5f)
                lineTo(3.5f, 20.5f)
                lineTo(3.5f, 3.5f)
                moveTo(12f, 3.5f)
                lineTo(12f, 20.5f)
                moveTo(6.5f, 7.5f)
                lineTo(10f, 7.5f)
                moveTo(6.5f, 11f)
                lineTo(10f, 11f)
            }
            // 加号（右下）
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(17.5f, 15.5f)
                lineTo(17.5f, 20.5f)
                moveTo(15f, 18f)
                lineTo(20f, 18f)
            }
        }.build()
    }

    /** 删除（垃圾桶） */
    val Delete: ImageVector by lazy {
        ImageVector.Builder(
            name = "Delete", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 19f)
                curveTo(6f, 20.1f, 6.9f, 21f, 8f, 21f)
                lineTo(16f, 21f)
                curveTo(17.1f, 21f, 18f, 20.1f, 18f, 19f)
                lineTo(18f, 7f)
                lineTo(6f, 7f)
                lineTo(6f, 19f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(19f, 4f)
                lineTo(15.5f, 4f)
                lineTo(14.5f, 3f)
                lineTo(9.5f, 3f)
                lineTo(8.5f, 4f)
                lineTo(5f, 4f)
                lineTo(5f, 6f)
                lineTo(19f, 6f)
                lineTo(19f, 4f)
                close()
            }
        }.build()
    }

    /** 返回（左箭头） */
    val Back: ImageVector by lazy {
        ImageVector.Builder(
            name = "Back", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.2f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(15.5f, 4.5f)
                lineTo(8f, 12f)
                lineTo(15.5f, 19.5f)
            }
        }.build()
    }

    /** 对勾（已加入生词本） */
    val Check: ImageVector by lazy {
        ImageVector.Builder(
            name = "Check", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.2f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4.5f, 12.5f)
                lineTo(9.5f, 17.5f)
                lineTo(19.5f, 6.5f)
            }
        }.build()
    }

    /** 发音：小喇叭（美音 US） */
    val SpeakerUs: ImageVector by lazy {
        ImageVector.Builder(
            name = "SpeakerUs", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 9f)
                lineTo(3f, 15f)
                lineTo(7f, 15f)
                lineTo(12f, 19f)
                lineTo(12f, 5f)
                lineTo(7f, 9f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                // 声波 1
                moveTo(15f, 9.5f)
                curveTo(16f, 11f, 16f, 13f, 15f, 14.5f)
                lineTo(16.6f, 16.1f)
                curveTo(18.2f, 14f, 18.2f, 10f, 16.6f, 7.9f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                // 声波 2
                moveTo(18.4f, 6.7f)
                curveTo(20.8f, 9.6f, 20.8f, 14.4f, 18.4f, 17.3f)
                lineTo(20f, 18.9f)
                curveTo(23f, 15.4f, 23f, 8.6f, 20f, 5.1f)
                close()
            }
        }.build()
    }

    /** 发音：小喇叭（英音 UK） */
    val SpeakerUk: ImageVector by lazy {
        ImageVector.Builder(
            name = "SpeakerUk", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 9f)
                lineTo(3f, 15f)
                lineTo(7f, 15f)
                lineTo(12f, 19f)
                lineTo(12f, 5f)
                lineTo(7f, 9f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                // 单道声波（UK 用一道区分）
                moveTo(15f, 8.5f)
                curveTo(17f, 10.5f, 17f, 13.5f, 15f, 15.5f)
                lineTo(16.4f, 16.9f)
                curveTo(19.2f, 13.8f, 19.2f, 10.2f, 16.4f, 7.1f)
                close()
            }
        }.build()
    }

    /** 音乐音符（音频模式入口） */
    val MusicNote: ImageVector by lazy {
        ImageVector.Builder(
            name = "MusicNote", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // 符头（椭圆近似，仅用基础指令）
                moveTo(11.2f, 17f)
                lineTo(9.6f, 19.08f)
                lineTo(6.4f, 19.08f)
                lineTo(4.8f, 17f)
                lineTo(6.4f, 14.92f)
                lineTo(9.6f, 14.92f)
                close()
                // 符干 + 符尾小旗
                moveTo(10.8f, 16.5f)
                lineTo(10.8f, 4f)
                lineTo(16f, 2.6f)
                lineTo(16f, 11f)
                lineTo(13f, 12f)
                lineTo(13f, 4.8f)
                close()
            }
        }.build()
    }

    /** 视频（屏幕+播放三角+镜头，视频模式入口） */
    val Video: ImageVector by lazy {
        ImageVector.Builder(
            name = "Video", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // 屏幕
                moveTo(3f, 6f)
                lineTo(16f, 6f)
                lineTo(16f, 18f)
                lineTo(3f, 18f)
                close()
                // 播放三角
                moveTo(8f, 9.5f)
                lineTo(8f, 14.5f)
                lineTo(12.5f, 12f)
                close()
                // 镜头
                moveTo(18f, 9f)
                lineTo(22f, 7f)
                lineTo(22f, 17f)
                lineTo(18f, 15f)
                close()
            }
        }.build()
    }
}