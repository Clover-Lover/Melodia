package com.lin0721.linmusic.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.library.ui.LibraryFilter
import com.lin0721.linmusic.feature.library.ui.LibraryItem
import com.lin0721.linmusic.feature.library.ui.LibraryItemType
import com.lin0721.linmusic.feature.library.ui.LibraryUiState
import com.lin0721.linmusic.feature.library.ui.LibraryViewModel

private val SidebarFilters = listOf(
    LibraryFilter.PLAYLIST to "歌单",
    LibraryFilter.ALBUM to "专辑",
    LibraryFilter.ARTIST to "艺人"
)

@Composable
fun LibrarySidebar(
    viewModel: LibraryViewModel,
    isLoggedIn: Boolean,
    onLoginClick: () -> Unit,
    onPlaylistClick: (LibraryItem) -> Unit,
    onAlbumClick: (LibraryItem) -> Unit,
    onArtistClick: (LibraryItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()

    Column(modifier.fillMaxSize().padding(vertical = 16.dp)) {
        Text(
            "音乐库",
            color = DesktopColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.padding(top = 12.dp))
        if (!isLoggedIn) {
            LoginPrompt(onLoginClick)
            return@Column
        }
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SidebarFilters.forEach { (filter, label) ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { viewModel.toggleFilter(filter) },
                    label = { Text(label, fontSize = 13.sp) },
                    shape = RoundedCornerShape(16.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = DesktopColors.Surface,
                        labelColor = DesktopColors.TextPrimary,
                        selectedContainerColor = DesktopColors.TextPrimary,
                        selectedLabelColor = DesktopColors.Pane
                    ),
                    border = null
                )
            }
        }
        Spacer(Modifier.padding(top = 8.dp))
        when (val state = uiState) {
            LibraryUiState.Loading -> CenteredBox { CircularProgressIndicator(color = DesktopColors.Accent) }
            is LibraryUiState.Error -> CenteredBox {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.message, color = DesktopColors.TextGray, fontSize = 13.sp)
                    TextButton(onClick = { viewModel.loadLibraryData() }) {
                        Text("重试", color = DesktopColors.TextPrimary)
                    }
                }
            }
            is LibraryUiState.Success -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.filteredItems, key = { it.id }) { item ->
                    LibraryRow(item) {
                        when (item.type) {
                            LibraryItemType.PLAYLIST -> onPlaylistClick(item)
                            LibraryItemType.ALBUM -> onAlbumClick(item)
                            LibraryItemType.ARTIST -> onArtistClick(item)
                            // 桌面端暂不支持 MV 播放
                            LibraryItemType.MV -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(item: LibraryItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(
            item.coverUrl,
            48.dp,
            shape = if (item.type == LibraryItemType.ARTIST) CircleShape else RoundedCornerShape(4.dp)
        )
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                item.title,
                color = DesktopColors.TextPrimary,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                item.subtitle,
                color = DesktopColors.TextGray,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LoginPrompt(onLoginClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(8.dp))
            .background(DesktopColors.Surface).padding(16.dp)
    ) {
        Text("登录后查看你的歌单", color = DesktopColors.TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.padding(top = 4.dp))
        Text("收藏的歌单、专辑和艺人会显示在这里", color = DesktopColors.TextGray, fontSize = 13.sp)
        Spacer(Modifier.padding(top = 12.dp))
        Box(
            Modifier.clip(RoundedCornerShape(20.dp)).background(DesktopColors.TextPrimary)
                .clickable(onClick = onLoginClick).padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("登录", color = DesktopColors.Pane, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
        content()
    }
}
