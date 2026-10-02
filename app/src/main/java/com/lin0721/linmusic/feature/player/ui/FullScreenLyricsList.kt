package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.lyricLineKey
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.PillRadius

private val FullScreenLyricsTopSafetyPadding = 32.dp
private const val FullScreenLyricsAnchorFraction = 0.25f

// 全屏歌词列表区：加载态/空态、当前行自动定位到视口 1/4 处、目标行推导与拖动定位覆盖层
@Composable
fun ColumnScope.FullScreenLyricsList(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    // 同时需要高亮的行（对唱/背景和声的重叠区间）；留空时退回只高亮 currentIndex
    activeIndices: Set<Int> = emptySet(),
    isLoading: Boolean,
    isUserScrolling: Boolean,
    highlightColor: Color,
    currentPositionProvider: () -> Long,
    lazyListState: LazyListState,
    viewportHeightPx: Float,
    onViewportHeightChange: (Float) -> Unit,
    gestureModifier: Modifier,
    fontSize: Int = 22,
    alignment: String = "left",
    secondaryMode: String = "translation",
    lineSpacing: Int = 24,
    secondarySpacing: Int = 6,
    advancedKaraokeEffect: Boolean = true,
    isPlaying: Boolean = true,
    // 以下为宽屏播放器用：关掉基准线与播放胶囊、关掉列表自带拖动（由外层按命中规则接管）、上报每行文字范围
    showSeekGuide: Boolean = true,
    userScrollEnabled: Boolean = true,
    onLineTextBounds: ((index: Int, bounds: Rect) -> Unit)? = null,
    onSeek: (Long) -> Unit,
    onLyricClick: (LyricLine) -> Unit
) {
    val density = LocalDensity.current
    val topSafetyPaddingPx = with(density) { FullScreenLyricsTopSafetyPadding.toPx() }
    // 当前行锚定在视口 1/4 处（屏幕上半部分），顶部留出安全边距避免贴到状态栏
    val targetLinePx = remember(viewportHeightPx, topSafetyPaddingPx) {
        topSafetyPaddingPx + ((viewportHeightPx - topSafetyPaddingPx).coerceAtLeast(0f) * FullScreenLyricsAnchorFraction)
    }

    LaunchedEffect(currentIndex, isUserScrolling, viewportHeightPx, fontSize, lineSpacing, secondarySpacing) {
        if (!isUserScrolling && currentIndex in lyrics.indices && viewportHeightPx > 0f) {
            // 估算值以默认间距（行距 24dp、副文本距 6dp）为基准，按用户设置的差值修正
            val itemStridePx = with(density) { (66 + lineSpacing - 24).coerceAtLeast(1).dp.toPx() }
            val linesAboveTarget = (targetLinePx / itemStridePx).toInt()

            if (currentIndex < linesAboveTarget) {
                // 还滚不到基准线（首句附近没有足够内容可用），把首句钉在顶部保持不动。
                // 偏移必须是 0：内容顶部内边距是安全边距，item 落点为 offset = -scrollOffset，
                // 传任何非 0 值都会把首句往上多推一段，表现为前几行播放时列表乱跳。
                lazyListState.animateScrollToItem(index = 0, scrollOffset = 0)
                return@LaunchedEffect
            }

            val current = lyrics[currentIndex]
            val hasSecondary = current.translation != null ||
                current.roma != null ||
                current.backgroundLine != null
            val itemHeightPx = with(density) {
                (if (hasSecondary) 96 + secondarySpacing - 6 else 54).coerceAtLeast(1).dp.toPx()
            }
            // 让当前行中心落在基准线上
            val targetOffsetPx = (targetLinePx - itemHeightPx / 2f).toInt()
            lazyListState.animateScrollToItem(
                index = currentIndex,
                scrollOffset = -targetOffsetPx
            )
        }
    }

    val centerLineIndex by remember(targetLinePx) {
        derivedStateOf {
            val layoutInfo = lazyListState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf -1
            var minDistance = Float.MAX_VALUE
            var closestIndex = -1
            for (item in visibleItems) {
                val itemCenter = item.offset + item.size / 2f
                val distance = kotlin.math.abs(itemCenter - targetLinePx)
                if (distance < minDistance) {
                    minDistance = distance
                    closestIndex = item.index
                }
            }
            closestIndex
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp).align(Alignment.Center)
            )
        } else if (lyrics.isEmpty()) {
            Text(
                text = "暂无歌词",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 18.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            val targetLineOffsetDp = with(density) { targetLinePx.toDp() }
            CenterTargetLine(
                visible = isUserScrolling && showSeekGuide,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .align(Alignment.TopCenter)
                    .offset(y = targetLineOffsetDp)
            )

            LazyColumn(
                state = lazyListState,
                userScrollEnabled = userScrollEnabled,
                modifier = Modifier
                    .fillMaxSize()
                    .then(gestureModifier)
                    .onSizeChanged { onViewportHeightChange(it.height.toFloat()) },
                verticalArrangement = Arrangement.spacedBy(lineSpacing.coerceAtLeast(0).dp),
                contentPadding = PaddingValues(
                    top = FullScreenLyricsTopSafetyPadding,
                    // 底部留出锚点以下的可视区域，才能让最后一行滚到锚点
                    bottom = with(density) { (viewportHeightPx - targetLinePx).coerceAtLeast(0f).toDp() }
                ),
                horizontalAlignment = Alignment.Start
            ) {
                itemsIndexed(items = lyrics, key = ::lyricLineKey) { index, line ->
                    val isCurrent = if (activeIndices.isEmpty()) {
                        index == currentIndex
                    } else {
                        index in activeIndices
                    }
                    val isCenterTarget = index == centerLineIndex && isUserScrolling && showSeekGuide
                    val distance = kotlin.math.abs(index - currentIndex).coerceAtMost(5)

                    FullScreenLyricsRow(
                        index = index,
                        line = line,
                        isCurrent = isCurrent,
                        isCenterTarget = isCenterTarget,
                        distance = distance,
                        highlightColor = highlightColor,
                        currentPositionProvider = currentPositionProvider,
                        fontSize = fontSize,
                        alignment = alignment,
                        secondaryMode = secondaryMode,
                        secondarySpacing = secondarySpacing,
                        advancedKaraokeEffect = advancedKaraokeEffect,
                        isPlaying = isPlaying,
                        onTextBoundsInRoot = onLineTextBounds?.let { report -> { bounds -> report(index, bounds) } },
                        onClick = { onLyricClick(line) }
                    )
                }
            }

            PlayCapsule(
                visible = isUserScrolling && showSeekGuide && centerLineIndex in lyrics.indices,
                targetLine = lyrics.getOrNull(centerLineIndex),
                onSeek = onSeek,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    // 胶囊中心对齐到 1/4 基准线（胶囊约 36dp 高，上移一半）
                    .offset(y = targetLineOffsetDp - 18.dp)
                    .padding(end = MelodiaSpacing.md)
            )
        }
    }
}

// 用户滚动时出现的 1/4 处虚线基准，标示"松手即跳转"的目标位置
@Composable
private fun CenterTargetLine(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200)),
        modifier = modifier
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawLine(
                color = Color.White.copy(alpha = 0.2f),
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f),
                strokeWidth = 1f
            )
        }
    }
}

// 1/4 虚线右侧的跳转胶囊，显示目标行时间并点击定位播放
@Composable
private fun PlayCapsule(
    visible: Boolean,
    targetLine: LyricLine?,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200)),
        modifier = modifier
    ) {
        if (targetLine != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .pressable(MelodiaPress.Pill) { onSeek(targetLine.timeMs) }
                    .clip(RoundedCornerShape(PillRadius))
                    .background(Color.White.copy(alpha = 0.2f))
                    .padding(horizontal = 14.dp, vertical = MelodiaSpacing.sm)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "跳转到此处播放",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(MelodiaSpacing.xs))
                Text(
                    text = formatTime(targetLine.timeMs),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
