package com.lin0721.linmusic.desktop.ui.nowplaying

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.lin0721.linmusic.desktop.ui.palette.FallbackCoverPalette
import com.lin0721.linmusic.desktop.ui.palette.extractCoverPaletteFromUrl
import kotlin.math.PI
import kotlin.math.cos

private const val BASE_TRANSITION_MS = 800
private const val GRADIENT_STEPS = 48

// 封面主色，取色完成前保持上一首的颜色，切歌时平滑过渡
@Composable
fun rememberCoverBase(url: String?): Color {
    var base by remember { mutableStateOf(FallbackCoverPalette.base) }
    LaunchedEffect(url) {
        base = if (url.isNullOrBlank()) FallbackCoverPalette.base else extractCoverPaletteFromUrl(url).base
    }
    val animated by animateColorAsState(base, tween(BASE_TRANSITION_MS), label = "coverBase")
    return animated
}

// 平滑抗色阶断层的垂直渐变：余弦缓动采样，首尾斜率平滑；渐隐到透明时保留原色相避免发灰
fun smoothVerticalGradient(from: Color, endY: Float): Brush {
    val to = from.copy(alpha = 0f)
    val stops = Array(GRADIENT_STEPS) { i ->
        val t = i.toFloat() / (GRADIENT_STEPS - 1)
        val factor = ((1.0 - cos(t * PI)) / 2.0).toFloat()
        t to lerp(from, to, factor)
    }
    return Brush.verticalGradient(colorStops = stops, startY = 0f, endY = endY)
}
