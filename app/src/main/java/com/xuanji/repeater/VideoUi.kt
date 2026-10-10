package com.xuanji.repeater

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

// ===== 视频模式的独立 UI 组件（从 MainActivity 纯搬迁，逻辑零改动） =====

/**
 * 🔴🔴【1010 阿亮 v139】进度条那条**灰色透明底板**的总高。
 *
 * 为什么要有这个常量：底板的 `background` 原本挂在「高度由 Slider 决定」的 Box 上，
 * 于是调 Slider 高度会连带把底板总高、底板上半空隙一起改掉 ⇒ 出现
 * "只想把轨道↔按键那段压近，结果上面的灰区也被压矮了"。
 *
 * 现在两者**解耦**（两段空隙各由一个数决定，互不牵连）：
 *   - 上半空隙 = `PROGRESS_BAND_H` − `PROGRESS_SLIDER_H`
 *   - 下半空隙 = `PROGRESS_SLIDER_H` ÷ 2（轨道/圆点在 Slider 内居中）
 * ⚠️ 算空隙别把下半当成"灰区高 − 上半"：`PROGRESS_BAND_H` 已包含**整个 Slider**，下半只是它的一半。
 *
 * 🔴🔴【1010 阿亮，唯一需求】**只要求"轨道↔按钮距离变小"，别的都不要动。**
 *   1010 原话（重复三遍强调）："上面那个从来就没要求过，不用管、没人让你管。"
 *   ⇒ 改这个区域时**只许动下半**；上半保持 6dp，别再自作主张去"恢复"或"优化"。
 *
 * ✅ 当前值 = 20dp 灰区 / 14dp Slider ⇒ 上半 **6dp（不动）**、下半 **7dp**（比 8dp 再小 1dp）。
 *   14dp 时 thumb（直径 12dp）上下各留 1dp 余量。真机看着"圆点戳边"就退回 16/22dp。
 *
 * 各版对照（1dp = 2.75px @ 1080px 宽）：
 *   v136 灰区 28dp：上半 14 / 下半 14dp
 *   v137 灰区 18dp：上半  9 / 下半  9dp
 *   v138 灰区 16dp：上半  8 / 下半  8dp
 *   v139 灰区 20dp：上半  6 / 下半  7dp  ← 上半与 22dp 版相同，下半再小 1dp
 */
internal val PROGRESS_BAND_H = 20.dp

/** 进度条自身的触摸区高度（也是轨道居中的容器高）。下限：thumb 直径 12dp 上下各留 1dp ⇒ 14dp。 */
internal val PROGRESS_SLIDER_H = 14.dp

/** 视频进度条：直接拖动 seek，支持 overlay 白配色与主界面主题配色。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoProgressSlider(
    currentMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    overlay: Boolean = false,
) {
    val totalMs = durationMs.coerceAtLeast(1L)
    val fraction = (currentMs.coerceIn(0L, totalMs).toFloat() / totalMs).coerceIn(0f, 1f)
    val trackH = 4.dp
    val thumbR = 6.dp
    val inert = Color.White.copy(alpha = 0.4f)
    // 🔴【1010 阿亮】"进度条和下面几个功能键之间空得太多" —— 根因就是这个 Box 的高度：
    //   轨道/小圆点画在**正中**（上面那条"不再偏上"的规矩要保住，所以**不能**把轨道往下挪），
    //   于是 Box 有多高、上下就各空多少 ⇒ 轨道到 Box 底 = Box 高的一半。
    //   28dp(44dp 间距) → 18dp(39dp) → 16dp(38dp)（1010 要求"再缩一点点"）。
    // ⚠️ 这条高度就是**触摸区**高度，别为了好看再压：thumb 直径 12dp，低于 12dp 圆点会顶出边。
    //   真要更大的触摸区而不改外观，用 `minimumInteractiveComponentSize()`（1010 未批准，暂不加）。
    // 🔴 v139 起「触摸区高度」与「灰底板总高」已解耦，见文件顶部 PROGRESS_BAND_H 注释。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PROGRESS_SLIDER_H)
            .padding(horizontal = 8.dp)
            // 点击轨道 → 跳转
            .pointerInput(totalMs) {
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width * totalMs).toLong().coerceIn(0L, totalMs))
                }
            }
            // 拖动 thumb → 连续跳转
            .pointerInput(totalMs) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onSeek((offset.x / size.width * totalMs).toLong().coerceIn(0L, totalMs))
                    },
                    onDrag = { change, _ ->
                        onSeek((change.position.x / size.width * totalMs).toLong().coerceIn(0L, totalMs))
                        change.consume()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val w = size.width
            val tx = fraction * w
            // 轨道与 thumb 都严格画在 centerY → 小圆点处于进度条正中（不再偏上）
            drawLine(color = inert, start = Offset(0f, centerY), end = Offset(w, centerY), strokeWidth = trackH.toPx(), cap = StrokeCap.Round)
            if (tx > 0f) {
                drawLine(color = Color.White, start = Offset(0f, centerY), end = Offset(tx, centerY), strokeWidth = trackH.toPx(), cap = StrokeCap.Round)
            }
            drawCircle(color = Color.White, radius = thumbR.toPx(), center = Offset(tx, centerY))
        }
    }
}

/** 视频控制条：与音频模式统一的 5 键（上一曲 / 上一句 / 播放 / 下一句 / 下一曲）。
 *  上一句/下一句 点按=上一句/下一句，长按=快退/快进 10 秒。 */
@OptIn(ExperimentalFoundationApi::class)
/** 点按=单击(tap)跳一句；按住=按住的整个期间持续触发 hold(true)，松手触发 hold(false)。
 *  【0903 bug 修复】原来用 detectTapGestures(onPress)：按下立即 hold(true)，一旦手指移动
 *  （左滑唤出 B站 输入框正好压在下一句键上）onPress 协程被手势取消，hold(false) 永不执行 →
 *  2x 快进永远停不下来（暂停/再播仍是快进，只有切曲才恢复）。改成自实现手势：
 *  - 手指移动超过 touch slop → 判定为「拖动」（交给父级 draggable），不触发 hold/tap；
 *  - 按住不动超过 HOLD_TRIGGER_MS 才进入 hold，松手/被抢占都保证发 hold(false)。 */
internal const val HOLD_TRIGGER_MS = 250L   // 按下多久才算「长按快进/快退」（避开拖动误触）
internal fun Modifier.pressHold(
    onTap: () -> Unit,
    onHoldChange: (Boolean) -> Unit,
): Modifier = this.pointerInput(Unit) {
    val slopPx = viewConfiguration.touchSlop.toDouble()
    awaitEachGesture {
        val down = awaitFirstDown()
        val id = down.id
        val startX = down.position.x
        val startY = down.position.y
        val startMs = System.currentTimeMillis()
        var holding = false
        var aborted = false   // 手指移动超 slop 或被父手势消费 → 判定拖动
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == id } ?: break
            if (!change.pressed) break                 // 松手
            if (change.isConsumed) { aborted = true; break }  // 被 draggable 抢走
            val dx = change.position.x - startX
            val dy = change.position.y - startY
            if (dx * dx + dy * dy > slopPx * slopPx) { aborted = true; break }  // 拖动
            if (!holding && System.currentTimeMillis() - startMs >= HOLD_TRIGGER_MS) {
                holding = true
                onHoldChange(true)
            }
        }
        // 无论怎么结束（松手/被抢占/手势取消），都保证恢复 —— 修复快进停不下来的根因
        if (holding) onHoldChange(false)
        else if (!aborted) onTap()
    }
}
