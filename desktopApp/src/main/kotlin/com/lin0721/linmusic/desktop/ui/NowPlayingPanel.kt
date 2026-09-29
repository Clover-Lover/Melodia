package com.lin0721.linmusic.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel

private val LyricCardHeight = 280.dp

@Composable
fun NowPlayingPanel(
    controller: PlaybackController,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val nowPlaying by controller.nowPlaying.collectAsState()
    val track = nowPlaying
    if (track == null) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("还没有正在播放的歌曲", color = DesktopColors.TextGray, fontSize = 14.sp)
        }
        return
    }
    val detailState by playerViewModel.songDetailState.collectAsState()
    val currentLyricIndex by playerViewModel.currentLyricIndex.collectAsState()

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Cover(track.artworkUri, maxWidth, shape = RoundedCornerShape(8.dp))
        }
        Text(
            track.title,
            color = DesktopColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(track.artist, color = DesktopColors.TextGray, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        LyricPreviewCard(
            lines = detailState.lyrics,
            isLoading = detailState.isLyricsLoading,
            currentIndex = currentLyricIndex,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
private fun LyricPreviewCard(
    lines: List<LyricLine>,
    isLoading: Boolean,
    currentIndex: Int,
    modifier: Modifier
) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(DesktopColors.Surface).padding(16.dp)
    ) {
        Text("歌词预览", color = DesktopColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Box(Modifier.fillMaxWidth().height(LyricCardHeight).padding(top = 8.dp), contentAlignment = Alignment.Center) {
            when {
                isLoading -> CircularProgressIndicator(color = DesktopColors.Accent)
                lines.isEmpty() -> Text("暂无歌词", color = DesktopColors.TextGray, fontSize = 14.sp)
                else -> LyricList(lines, currentIndex)
            }
        }
    }
}

@Composable
private fun LyricList(lines: List<LyricLine>, currentIndex: Int) {
    val listState = rememberLazyListState()
    // 当前行滚到卡片约三分之一处，上方保留已唱过的一两行作语境
    val offsetPx = with(LocalDensity.current) { (LyricCardHeight / 3).roundToPx() }
    LaunchedEffect(currentIndex, lines) {
        if (currentIndex in lines.indices) listState.animateScrollToItem(currentIndex, -offsetPx)
    }
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
                    color = if (active) DesktopColors.TextPrimary else DesktopColors.TextGray,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    fontSize = if (active) 18.sp else 16.sp
                )
                line.translation?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = DesktopColors.TextGray, fontSize = 13.sp)
                }
            }
        }
    }
}
