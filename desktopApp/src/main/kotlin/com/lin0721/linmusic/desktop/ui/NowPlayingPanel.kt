package com.lin0721.linmusic.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors

@Composable
fun NowPlayingPanel(controller: PlaybackController, modifier: Modifier = Modifier) {
    val nowPlaying by controller.nowPlaying.collectAsState()
    val track = nowPlaying
    if (track == null) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("还没有正在播放的歌曲", color = DesktopColors.TextGray, fontSize = 14.sp)
        }
        return
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(
            track.title,
            color = DesktopColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        BoxWithConstraints(Modifier.padding(vertical = 16.dp)) {
            Cover(track.artworkUri, maxWidth, shape = RoundedCornerShape(8.dp))
        }
        Text(
            track.title,
            color = DesktopColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(track.artist, color = DesktopColors.TextGray, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
