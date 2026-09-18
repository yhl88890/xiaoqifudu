package com.xuanji.repeater

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** 启动动画：高清照片（640px）淡入 → 放大到屏幕宽度 → 定格 → 淡出，露出播放页（从 MainActivity 纯搬迁） */
@Composable
internal fun SplashOverlay(onDone: () -> Unit) {
    val alpha = remember { Animatable(0f) } // 初始透明，先淡入衔接系统启动窗
    val startSize = 96.dp
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    // 关键：Image 本身按屏幕宽度渲染，再用 graphicsLayer 从 96dp 缩放到全宽，
    // 避免"先压到 96dp 低分辨率再放大"导致的最后一秒变花。
    val initialScale = (startSize.value / screenWidth.value).coerceAtLeast(0.01f)
    val scale = remember { Animatable(initialScale) }
    LaunchedEffect(Unit) {
        // B: 快速淡入（120ms），避免首帧硬切
        alpha.animateTo(1f, animationSpec = tween(120, easing = LinearEasing))
        // A: 照片从 96dp 放大到与屏幕同宽
        scale.animateTo(1f, animationSpec = tween(480, easing = FastOutSlowInEasing))
        // C: 定格 100ms，视觉上有"到位"停顿感
        delay(100)
        // 淡出：透明度降到最低，透出播放页
        alpha.animateTo(0f, animationSpec = tween(300, easing = LinearEasing))
        onDone()
    }
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFFABB1A8)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_splash_photo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(screenWidth)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                },
        )
    }
}
