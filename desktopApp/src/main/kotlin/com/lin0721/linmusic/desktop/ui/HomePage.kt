package com.lin0721.linmusic.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.home.domain.HomeCard
import com.lin0721.linmusic.feature.home.domain.HomeShelf
import com.lin0721.linmusic.feature.home.ui.HomeFeedData
import com.lin0721.linmusic.feature.home.ui.HomeUiState
import com.lin0721.linmusic.feature.home.ui.HomeViewModel

internal val CardWidth = 168.dp

// 距离底部还剩几项时提前拉下一页货架
private const val LOAD_MORE_THRESHOLD = 2

@Composable
fun HomePage(
    viewModel: HomeViewModel,
    onPlaylistClick: (id: Long, title: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    when (val state = uiState) {
        HomeUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = DesktopColors.Accent)
        }
        is HomeUiState.Error -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message, color = DesktopColors.TextGray)
                TextButton(onClick = { viewModel.refreshHomeData() }) {
                    Text("重试", color = DesktopColors.TextPrimary)
                }
            }
        }
        is HomeUiState.Success -> HomeFeed(state.data, viewModel, onPlaylistClick, modifier)
    }
}

@Composable
private fun HomeFeed(
    data: HomeFeedData,
    viewModel: HomeViewModel,
    onPlaylistClick: (id: Long, title: String) -> Unit,
    modifier: Modifier
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember(data) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            data.hasMore && !data.isLoadingMore &&
                lastVisible >= listState.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMoreShelves()
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        if (data.recentPlaylists.isNotEmpty()) {
            item(key = "recent") {
                RecentGrid(data) { id, title -> onPlaylistClick(id, title) }
            }
        }
        if (data.dailySongs.isNotEmpty()) {
            item(key = "daily") {
                DailyBanner(data.dailySongs.size) { viewModel.playDailySong() }
            }
        }
        // 服务端货架已含推荐歌单，仅在货架缺失时兜底展示
        if (data.shelves.isEmpty() && data.recommendPlaylists.isNotEmpty()) {
            item(key = "recommend") {
                ShelfRow("推荐歌单", data.recommendPlaylists) { playlist ->
                    CardTile(playlist.picUrl, playlist.name, "") { onPlaylistClick(playlist.id, playlist.name) }
                }
            }
        }
        items(data.shelves, key = { "shelf_${it.blockCode}_${it.title}" }) { shelf ->
            ServerShelf(shelf, viewModel, onPlaylistClick)
        }
        if (data.isLoadingMore) {
            item(key = "loading_more") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DesktopColors.Accent, modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}

@Composable
private fun RecentGrid(data: HomeFeedData, onClick: (Long, String) -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("最近播放", horizontalPadding = 0)
        data.recentPlaylists.take(8).chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { playlist ->
                    Row(
                        Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(4.dp))
                            .background(DesktopColors.Surface)
                            .clickable { onClick(playlist.id, playlist.name) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Cover(playlist.coverUrl, 56.dp, shape = RoundedCornerShape(0.dp))
                        Text(
                            playlist.name,
                            color = DesktopColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
                // 末行不足四项时补位，保持列宽一致
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DailyBanner(songCount: Int, onPlay: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 24.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(DesktopColors.Accent).clickable(onClick = onPlay).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("每日推荐", color = DesktopColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("$songCount 首专属好歌", color = DesktopColors.TextPrimary.copy(alpha = 0.8f), fontSize = 14.sp)
        }
        Text("播放", color = DesktopColors.TextPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ServerShelf(
    shelf: HomeShelf,
    viewModel: HomeViewModel,
    onPlaylistClick: (Long, String) -> Unit
) {
    val songs = shelf.cards.filterIsInstance<HomeCard.Song>()
    val voices = shelf.cards.filterIsInstance<HomeCard.Voice>()
    ShelfRow(shelf.title, shelf.cards) { card ->
        CardTile(card.coverUrl, card.title, card.caption) {
            when (card) {
                is HomeCard.Playlist -> onPlaylistClick(card.id, card.title)
                is HomeCard.Song -> viewModel.playShelfSong(shelf.title, songs, card)
                is HomeCard.Voice -> viewModel.playShelfVoice(shelf.title, voices, card)
                // 专辑页在后续阶段接入
                is HomeCard.Album -> Unit
            }
        }
    }
}

@Composable
private fun <T> ShelfRow(title: String, items: List<T>, itemContent: @Composable (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items) { itemContent(it) }
        }
    }
}

@Composable
internal fun CardTile(coverUrl: String, title: String, caption: String, onClick: () -> Unit) {
    Column(
        Modifier.width(CardWidth).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick)
    ) {
        Cover(coverUrl, CardWidth, shape = RoundedCornerShape(6.dp))
        Text(
            title,
            color = DesktopColors.TextPrimary,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (caption.isNotBlank()) {
            Text(caption, color = DesktopColors.TextGray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SectionTitle(text: String, horizontalPadding: Int = 24) {
    Text(
        text,
        color = DesktopColors.TextPrimary,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = horizontalPadding.dp)
    )
}
