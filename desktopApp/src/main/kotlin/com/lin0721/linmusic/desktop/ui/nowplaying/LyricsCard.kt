package com.lin0721.linmusic.desktop.ui.nowplaying

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.desktop.ui.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.palette.darken
import com.lin0721.linmusic.desktop.ui.palette.lighten
import com.lin0721.linmusic.desktop.ui.palette.saturateIfChromatic

private val LyricCardHeight = 280.dp
private val MeshBlurRadius = 32.dp

// 歌词预览卡：背景随封面主色着色并缓慢游走（移植自移动端），当前行高亮，未唱到的行取主色提亮后的浅色
@Composable
fun LyricsCard(
    lines: List<LyricLine>,
    currentIndex: Int,
    base: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "lyricMesh")
    val darkCenterX by transition.animateFloat(
        initialValue = 1.20f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(15000, easing = LinearEasing), RepeatMode.Reverse),
        label = "meshX"
    )
    val darkCenterY by transition.animateFloat(
        initialValue = 1.20f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(13000, easing = LinearEasing), RepeatMode.Reverse),
        label = "meshY"
    )
    val darkRadiusScale by transition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.50f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Reverse),
        label = "meshRadius"
    )
    val vividBase = remember(base) { base.saturateIfChromatic(0.6f) }
    val fillColor = remember(vividBase) { vividBase.darken(0.35f) }
    val darkBlob = remember(vividBase) { vividBase.darken(0.15f) }
    // 与全屏歌词同配方：文字单独再提一档饱和度与明度，不然混完白会发灰
    val inactiveColor = remember(base) { lerp(base.saturateIfChromatic(0.8f).lighten(1.0f), Color.White, 0.5f) }

    InfoCard(
        title = "歌词预览",
        modifier = modifier,
        backdrop = {
            Box(
                Modifier.matchParentSize().blur(MeshBlurRadius).drawBehind {
                    drawSingleHueMesh(
                        fill = fillColor,
                        darkBlob = darkBlob,
                        darkCenter = Offset(size.width * darkCenterX, size.height * darkCenterY),
                        darkRadius = size.minDimension * darkRadiusScale
                    )
                }
            )
        }
    ) {
        Box(Modifier.fillMaxWidth().height(LyricCardHeight).padding(top = 8.dp)) {
            LyricList(lines, currentIndex, inactiveColor)
        }
    }
}

// 单一色相的光斑：深色底 + 一枚渐隐光斑；fill 必须是明显压暗过的变体，不能直接传未处理的 base
private fun DrawScope.drawSingleHueMesh(fill: Color, darkBlob: Color, darkCenter: Offset, darkRadius: Float) {
    drawRect(color = fill)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(darkBlob.copy(alpha = 0.6f), Color.Transparent),
            center = darkCenter,
            radius = darkRadius
        ),
        center = darkCenter,
        radius = darkRadius
    )
}

@Composable
private fun LyricList(lines: List<LyricLine>, currentIndex: Int, inactiveColor: Color) {
    val listState = rememberLazyListState()
    // 当前行滚到卡片约三分之一处，上方保留已唱过的一两行作语境
    val offsetPx = with(LocalDensity.current) { (LyricCardHeight / 3).roundToPx() }
    LaunchedEffect(currentIndex, lines) {
        if (currentIndex in lines.indices) listState.animateScrollToItem(currentIndex, -offsetPx)
    }
    HoverScrollbarBox(listState) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // 底部留白让末尾几行也能滚到定位处
            contentPadding = PaddingValues(bottom = LyricCardHeight)
        ) {
            itemsIndexed(lines) { index, line ->
                val active = index == currentIndex
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(
                        line.text,
                        color = if (active) Color.White else inactiveColor,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        fontSize = if (active) 18.sp else 16.sp
                    )
                    line.translation?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = (if (active) Color.White else inactiveColor).copy(alpha = 0.75f), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
