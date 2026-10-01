package com.lin0721.linmusic.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.newworks.domain.NewWorksMv
import com.lin0721.linmusic.feature.newworks.domain.NewWorksRelease
import com.lin0721.linmusic.feature.newworks.domain.caption
import com.lin0721.linmusic.feature.newworks.ui.NewWorksUiState
import java.time.ZoneId
import java.util.Locale

// 曲目数超过这个阈值时角标改用强调色，提示这条发布内联了大量曲目
private const val BULKY_RELEASE_TRACK_THRESHOLD = 50

// 距离末尾还剩几项时提前拉下一页
private const val LOAD_MORE_THRESHOLD = 2
private val EdgePadding = 24.dp
private val HeroHeight = 200.dp
private val RailCardWidth = 168.dp
private val RailCardHeight = 94.dp

// 音乐页「最新」：新 MV（桌面端暂无 MV 播放，仅展示）+ 新发布网格。
// 专辑点击进专辑页，单曲双击播放
@Composable
fun NewWorksTab(
    uiState: NewWorksUiState,
    gridState: LazyGridState,
    onAlbumClick: (id: Long, title: String) -> Unit,
    onSongPlay: (NewWorksRelease) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit
) {
    when (uiState) {
        NewWorksUiState.Loading -> HomeTabLoading()
        is NewWorksUiState.Error -> HomeTabError(uiState.message, onRetry)
        is NewWorksUiState.Success -> {
            if (uiState.mvs.isEmpty() && uiState.releases.isEmpty()) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("还没有关注歌手的新作", color = DesktopColors.TextPrimary, fontWeight = FontWeight.Bold)
                    Text(
                        "多关注几位歌手，新歌新 MV 会出现在这里",
                        color = DesktopColors.TextGray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                return
            }
            NewWorksGrid(uiState, gridState, onAlbumClick, onSongPlay, onLoadMore)
        }
    }
}

@Composable
private fun NewWorksGrid(
    state: NewWorksUiState.Success,
    gridState: LazyGridState,
    onAlbumClick: (Long, String) -> Unit,
    onSongPlay: (NewWorksRelease) -> Unit,
    onLoadMore: () -> Unit
) {
    val shouldLoadMore by remember(state.releases.size, state.hasMore, state.isLoadingMore) {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            // 网格前面有 MV 区与标题占位，用总项数比较而不是 release 下标
            state.hasMore && !state.isLoadingMore &&
                lastVisible >= gridState.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(CardWidth),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = EdgePadding, end = EdgePadding, top = 4.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (state.mvs.isNotEmpty()) {
            item(key = "mv_section", span = { GridItemSpan(maxLineSpan) }) { MvSection(state.mvs) }
        }
        state.releaseGroups.forEachIndexed { index, group ->
            item(key = "group_${group.period.name}", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    group.period.title,
                    color = DesktopColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = if (index > 0) 16.dp else 0.dp)
                )
            }
            items(group.releases, key = { "${it.isAlbum}_${it.id}" }) { release ->
                ReleaseCard(release, onAlbumClick, onSongPlay)
            }
        }
        if (state.isLoadingMore) {
            item(key = "loading_more", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DesktopColors.Accent)
                }
            }
        }
    }
}

// MV 聚焦卡 + 横向卷轴：第一条做成大封面，其余平铺横向滚动
@Composable
private fun MvSection(mvs: List<NewWorksMv>) {
    Column(Modifier.padding(bottom = 8.dp)) {
        HeroMvCard(mvs.first())
        val rest = mvs.drop(1)
        if (rest.isNotEmpty()) {
            Text(
                "更多新 MV",
                color = DesktopColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(rest, key = { it.id }) { RailMvCard(it) }
            }
        }
    }
}

@Composable
private fun HeroMvCard(mv: NewWorksMv) {
    Box(Modifier.fillMaxWidth().height(HeroHeight).clip(RoundedCornerShape(8.dp)).background(DesktopColors.CoverPlaceholder)) {
        AsyncImage(
            model = mv.coverUrl.withCoverParam("800y450"),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
        )
        Text(
            "最新 MV",
            color = DesktopColors.Accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        )
        if (mv.durationMs > 0) {
            DurationBadge(mv.durationMs, Modifier.align(Alignment.TopEnd).padding(16.dp))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Text(
                mv.name,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                mv.artistName,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RailMvCard(mv: NewWorksMv) {
    Column(Modifier.width(RailCardWidth)) {
        Box(Modifier.width(RailCardWidth).height(RailCardHeight).clip(RoundedCornerShape(6.dp)).background(DesktopColors.CoverPlaceholder)) {
            AsyncImage(
                model = mv.coverUrl.withCoverParam("400y225"),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (mv.durationMs > 0) {
                DurationBadge(mv.durationMs, Modifier.align(Alignment.BottomEnd).padding(6.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(mv.name, color = DesktopColors.TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(mv.artistName, color = DesktopColors.TextGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DurationBadge(durationMs: Long, modifier: Modifier) {
    Text(
        formatMvDuration(durationMs),
        color = Color.White,
        fontSize = 11.sp,
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun ReleaseCard(
    release: NewWorksRelease,
    onAlbumClick: (Long, String) -> Unit,
    onSongPlay: (NewWorksRelease) -> Unit
) {
    val isBulky = release.trackCount > BULKY_RELEASE_TRACK_THRESHOLD
    val interaction = if (release.isAlbum) {
        Modifier.clickable { onAlbumClick(release.id, release.title) }
    } else {
        Modifier.onDoubleClick { onSongPlay(release) }
    }
    Column(Modifier.clip(RoundedCornerShape(6.dp)).then(interaction)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(DesktopColors.CoverPlaceholder)) {
            AsyncImage(
                model = sizedCoverUrl(release.coverUrl, 400),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(
            release.title,
            color = DesktopColors.TextPrimary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(release.artistName, color = DesktopColors.TextGray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            release.caption(System.currentTimeMillis(), ZoneId.systemDefault()),
            color = if (isBulky) DesktopColors.Accent else DesktopColors.TextGray,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(4.dp))
                .then(if (isBulky) Modifier.background(DesktopColors.Accent.copy(alpha = 0.12f)) else Modifier)
                .padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

// 封面自带查询串时不再追加尺寸参数
private fun String.withCoverParam(param: String): String =
    if (contains('?')) this else "$this?param=$param"

private fun formatMvDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
