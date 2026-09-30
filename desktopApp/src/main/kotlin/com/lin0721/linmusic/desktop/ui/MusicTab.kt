package com.lin0721.linmusic.desktop.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.music.domain.MusicStyle
import com.lin0721.linmusic.feature.music.domain.StyleArtistItem
import com.lin0721.linmusic.feature.music.domain.StyleHead
import com.lin0721.linmusic.feature.music.domain.StylePortrait
import com.lin0721.linmusic.feature.music.domain.StylePreference
import com.lin0721.linmusic.feature.music.ui.MusicUiState
import com.lin0721.linmusic.feature.music.ui.StyleSelection

private val EdgePadding = 24.dp
private val StyleHeaderHeight = 200.dp
private val ArtistAvatarSize = 120.dp

// 服务端没给配色的曲风统一退回中性灰
private val FallbackStyleColor = Color(0xFF4A4A4A)

// 「音乐」页：胶囊以下的曲风体系，胶囊本身由 HomePage 固定在顶部
@Composable
fun MusicTab(
    uiState: MusicUiState,
    listState: LazyListState,
    nowPlayingSongId: Long?,
    onStyleSelect: (StyleSelection) -> Unit,
    onChildStyleSelect: (Long?) -> Unit,
    onPlaylistClick: (id: Long, name: String) -> Unit,
    onArtistClick: (id: Long, name: String) -> Unit,
    onPlaySongAt: (Int) -> Unit,
    onPlayFavourite: () -> Unit,
    onRetry: () -> Unit
) {
    when (uiState) {
        MusicUiState.Loading -> HomeTabLoading()
        is MusicUiState.Error -> HomeTabError(uiState.message, onRetry)
        is MusicUiState.Success -> {
            val data = uiState.data
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item(key = "style_chips") {
                    StyleChips(data.styles, data.selection, data.hasPreference, onStyleSelect)
                }

                // 偏好页展示画像与占比，具体曲风页展示头图与二级筛选
                if (data.selection is StyleSelection.Preference) {
                    data.content?.head?.portrait?.let { portrait ->
                        item(key = "portrait") {
                            PortraitCard(portrait, data.preferences.firstOrNull()?.colorHex.toStyleColor())
                        }
                    }
                    item(key = "preferences") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionTitle("偏好占比")
                            PreferenceBars(data.preferences)
                        }
                    }
                } else {
                    data.content?.head?.let { head ->
                        item(key = "style_header") { StyleHeader(head) { onPlaySongAt(0) } }
                    }
                    data.selectedStyle?.children?.takeIf { it.isNotEmpty() }?.let { children ->
                        item(key = "sub_styles") { SubStyleChips(children, data.selectedChildId, onChildStyleSelect) }
                    }
                }

                if (data.isContentLoading) {
                    item(key = "content_loading") { HomeTabLoading(Modifier.fillMaxWidth().height(160.dp)) }
                } else {
                    val content = data.content
                    content?.head?.favouriteSong?.let { track ->
                        item(key = "favourite") {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SectionTitle("你的最爱")
                                FavouriteSongCard(track, onPlayFavourite)
                            }
                        }
                    }
                    content?.playlists?.takeIf { it.isNotEmpty() }?.let { playlists ->
                        item(key = "playlists") {
                            ShelfRow("热门歌单", playlists) { playlist ->
                                CardTile(playlist.coverUrl, playlist.name, playlist.playCount.toPlayCountText()) {
                                    onPlaylistClick(playlist.id, playlist.name)
                                }
                            }
                        }
                    }
                    content?.songs?.takeIf { it.isNotEmpty() }?.let { songs ->
                        item(key = "songs") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SectionTitle("必听单曲")
                                Column(Modifier.padding(horizontal = EdgePadding - 12.dp)) {
                                    songs.forEachIndexed { index, track ->
                                        TrackRow(
                                            index = index,
                                            track = track,
                                            isCurrent = nowPlayingSongId == track.id,
                                            onPlay = { onPlaySongAt(index) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    content?.artists?.takeIf { it.isNotEmpty() }?.let { artists ->
                        item(key = "artists") { ArtistRow(artists, onArtistClick) }
                    }
                }
            }
        }
    }
}

// 一级曲风胶囊：底色取服务端配色，未选中的压低透明度，选中的加白描边
@Composable
private fun StyleChips(
    styles: List<MusicStyle>,
    selection: StyleSelection,
    showPreference: Boolean,
    onSelect: (StyleSelection) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = EdgePadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showPreference) {
            item(key = "preference") {
                StyleChip(
                    text = "我的偏好",
                    color = DesktopColors.Accent,
                    selected = selection is StyleSelection.Preference,
                    onClick = { onSelect(StyleSelection.Preference) }
                )
            }
        }
        items(styles, key = { it.id }) { style ->
            StyleChip(
                text = style.name,
                color = style.colorHex.toStyleColor(),
                selected = (selection as? StyleSelection.Style)?.id == style.id,
                onClick = { onSelect(StyleSelection.Style(style.id)) }
            )
        }
    }
}

@Composable
private fun StyleChip(text: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        },
        shape = shape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = color.copy(alpha = 0.45f),
            labelColor = Color.White.copy(alpha = 0.75f),
            selectedContainerColor = color,
            selectedLabelColor = Color.White
        ),
        border = if (selected) BorderStroke(2.dp, Color.White.copy(alpha = 0.85f)) else null
    )
}

// 二级曲风子筛选：服务端不给二级配色，一律用描边胶囊
@Composable
private fun SubStyleChips(children: List<MusicStyle>, selectedChildId: Long?, onSelect: (Long?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = EdgePadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "all") { OutlineChip("全部", selectedChildId == null) { onSelect(null) } }
        items(children, key = { it.id }) { child ->
            OutlineChip(child.name, selectedChildId == child.id) { onSelect(child.id) }
        }
    }
}

// 选中为白底、未选中为描边的胶囊，音乐二级曲风与播客分类共用
@Composable
internal fun OutlineChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, fontSize = 13.sp) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = DesktopColors.TextGray,
            selectedContainerColor = DesktopColors.TextPrimary,
            selectedLabelColor = DesktopColors.Pane
        ),
        border = if (selected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
    )
}

// 曲风画像卡：文案是服务端模板替换后的成稿，客户端不再拼接
@Composable
private fun PortraitCard(portrait: StylePortrait, accent: Color) {
    Column(
        Modifier.padding(horizontal = EdgePadding).fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.35f))))
            .padding(20.dp)
    ) {
        Text("你的曲风画像", color = Color.White.copy(alpha = 0.82f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(
            portrait.content,
            color = Color.White,
            fontSize = 16.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 10.dp)
        )
        if (portrait.dataTip.isNotBlank()) {
            Text(
                portrait.dataTip,
                color = Color.White.copy(alpha = 0.58f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun PreferenceBars(preferences: List<StylePreference>) {
    Column(Modifier.padding(horizontal = EdgePadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        preferences.forEach { pref ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pref.name,
                    color = DesktopColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(80.dp)
                )
                Box(
                    Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.09f))
                ) {
                    Box(
                        Modifier.fillMaxWidth(pref.ratio.coerceIn(0, 100) / 100f).height(8.dp)
                            .clip(RoundedCornerShape(4.dp)).background(pref.colorHex.toStyleColor())
                    )
                }
                Text(
                    "${pref.ratio}%",
                    color = DesktopColors.TextGray,
                    fontSize = 13.sp,
                    modifier = Modifier.width(48.dp).padding(start = 12.dp)
                )
            }
        }
    }
}

// 曲风头图：封面打底 + 英文名水印 + 简介 + 整段起播
@Composable
private fun StyleHeader(head: StyleHead, onPlay: () -> Unit) {
    val accent = head.colorHex.toStyleColor()
    Box(
        Modifier.padding(horizontal = EdgePadding).fillMaxWidth().height(StyleHeaderHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.4f))))
    ) {
        sizedCoverUrl(head.coverUrl, 800)?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.42f,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (head.enName.isNotBlank()) {
            Text(
                head.enName,
                color = Color.White.copy(alpha = 0.17f),
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.TopEnd).padding(20.dp)
            )
        }
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp).fillMaxWidth(0.8f)) {
            Text(
                head.name,
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // 简介优先；热门曲风的数量统计都是封顶值没有区分度，拿到真实数字才退回展示
            val subtitle = head.desc.takeIf { it.isNotBlank() } ?: head.realStatsOrEmpty()
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        FilledIconButton(
            onClick = onPlay,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).size(48.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.White,
                contentColor = DesktopColors.Accent
            )
        ) {
            Icon(Icons.Rounded.PlayArrow, "播放", modifier = Modifier.size(26.dp))
        }
    }
}

// 带 + 的是服务端封顶值（999999+ / 1000+），各曲风都一样，展示了等于没说
private fun StyleHead.realStatsOrEmpty(): String = listOfNotNull(
    songNum.takeIf { it.isNotBlank() && !it.endsWith("+") }?.let { "$it 首歌" },
    artistNum.takeIf { it.isNotBlank() && !it.endsWith("+") }?.let { "$it 位歌手" }
).joinToString(" · ")

// 「你在此曲风最爱」，未登录时上游不会传入；双击起播
@Composable
private fun FavouriteSongCard(track: Track, onPlay: () -> Unit) {
    Row(
        Modifier.padding(horizontal = EdgePadding).fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.07f)).onDoubleClick(onPlay).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(track.al.picUrl, 56.dp)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text("你在此曲风最爱", color = DesktopColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(
                track.name,
                color = DesktopColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                track.ar.joinToString("/") { it.name },
                color = DesktopColors.TextGray,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.Rounded.Favorite, null, tint = DesktopColors.Accent, modifier = Modifier.size(20.dp))
    }
}

// 代表歌手：圆形头像，点击进歌手页
@Composable
private fun ArtistRow(artists: List<StyleArtistItem>, onClick: (id: Long, name: String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("代表歌手")
        LazyRow(
            contentPadding = PaddingValues(horizontal = EdgePadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(artists, key = { it.id }) { artist ->
                Column(
                    Modifier.width(ArtistAvatarSize).clip(RoundedCornerShape(6.dp)).clickable { onClick(artist.id, artist.name) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Cover(artist.picUrl, ArtistAvatarSize, shape = CircleShape)
                    Text(
                        artist.name,
                        color = DesktopColors.TextPrimary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    if (artist.musicSize > 0) {
                        Text("${artist.musicSize} 首", color = DesktopColors.TextGray, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

// 六位 hex 转 Color，脏值退回中性灰
private fun String?.toStyleColor(): Color {
    val hex = this?.trim()?.removePrefix("#")?.takeIf { it.length == 6 } ?: return FallbackStyleColor
    val rgb = hex.toIntOrNull(16) ?: return FallbackStyleColor
    return Color(0xFF000000.toInt() or rgb)
}

private fun Long.toPlayCountText(): String = when {
    this >= 100_000_000 -> "${this / 100_000_000} 亿次播放"
    this >= 10_000 -> "${this / 10_000} 万次播放"
    this <= 0 -> ""
    else -> "$this 次播放"
}
