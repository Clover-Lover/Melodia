package com.lin0721.linmusic.desktop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.theme.DesktopDimens
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import kotlin.math.roundToInt

private const val TOOLTIP_DELAY_MS = 400

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerBar(
    controller: PlaybackController,
    playerViewModel: PlayerViewModel,
    volume: Int?,
    onVolumeChange: (Int) -> Unit,
    nowPlayingOpen: Boolean,
    onToggleNowPlaying: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nowPlaying by controller.nowPlaying.collectAsState()
    val isPlaying by controller.playWhenReady.collectAsState()
    val playMode by controller.playMode.collectAsState()
    val position by controller.currentPosition.collectAsState()
    val duration by controller.duration.collectAsState()
    val hasTrack = nowPlaying != null

    Row(
        modifier.fillMaxWidth().height(DesktopDimens.PlayerBarHeight).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(0.3f), verticalAlignment = Alignment.CenterVertically) {
            val track = nowPlaying
            if (track != null) {
                TooltipArea(
                    tooltip = { TooltipLabel(if (nowPlayingOpen) "隐藏“正在播放”" else "显示“正在播放”") },
                    delayMillis = TOOLTIP_DELAY_MS
                ) {
                    Box(Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onToggleNowPlaying)) {
                        Cover(track.artworkUri, 56.dp)
                    }
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(track.title, color = DesktopColors.TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    NowPlayingArtists(track, playerViewModel, 12.sp)
                }
            }
        }
        Column(Modifier.weight(0.4f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ControlIcon(
                    Icons.Rounded.Shuffle,
                    "随机播放",
                    enabled = hasTrack,
                    active = playMode == PlayMode.SHUFFLE,
                    onClick = controller::toggleShuffle
                )
                ControlIcon(Icons.Rounded.SkipPrevious, "上一首", enabled = hasTrack, onClick = controller::skipToPrevious)
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(if (hasTrack) DesktopColors.TextPrimary else DesktopColors.SurfaceLight),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = controller::togglePlayPause, enabled = hasTrack) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (isPlaying) "暂停" else "播放",
                            tint = DesktopColors.Pane
                        )
                    }
                }
                ControlIcon(Icons.Rounded.SkipNext, "下一首", enabled = hasTrack, onClick = controller::playNext)
                ControlIcon(
                    if (playMode == PlayMode.SINGLE_LOOP) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    "循环模式",
                    enabled = hasTrack,
                    active = playMode == PlayMode.SINGLE_LOOP,
                    onClick = controller::toggleRepeat
                )
            }
            ProgressRow(position, duration, enabled = hasTrack && duration > 0, onSeek = controller::seekTo)
        }
        Row(Modifier.weight(0.3f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            // 占位播放器没有音量能力时不显示
            if (volume != null) VolumeControl(volume, onVolumeChange)
        }
    }
}

@Composable
private fun VolumeControl(volume: Int, onVolumeChange: (Int) -> Unit) {
    // 静音前的音量，再点一次恢复
    var lastAudible by remember { mutableStateOf(if (volume > 0) volume else 100) }
    IconButton(onClick = {
        if (volume > 0) {
            lastAudible = volume
            onVolumeChange(0)
        } else {
            onVolumeChange(lastAudible)
        }
    }) {
        Icon(
            when {
                volume == 0 -> Icons.AutoMirrored.Rounded.VolumeOff
                volume < 50 -> Icons.AutoMirrored.Rounded.VolumeDown
                else -> Icons.AutoMirrored.Rounded.VolumeUp
            },
            "音量",
            tint = DesktopColors.TextGray
        )
    }
    Slider(
        value = volume / 100f,
        onValueChange = { onVolumeChange((it * 100).roundToInt()) },
        colors = SliderDefaults.colors(
            thumbColor = DesktopColors.TextPrimary,
            activeTrackColor = DesktopColors.TextPrimary,
            inactiveTrackColor = DesktopColors.SurfaceLight
        ),
        modifier = Modifier.width(120.dp).height(20.dp)
    )
}

@Composable
private fun ProgressRow(position: Long, duration: Long, enabled: Boolean, onSeek: (Long) -> Unit) {
    // 拖动期间以本地值为准，松手才提交，避免进度回推造成跳动
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TimeLabel(if (dragValue != null) (dragValue!! * duration).toLong() else position)
        Slider(
            value = dragValue ?: fraction,
            onValueChange = { dragValue = it },
            onValueChangeFinished = {
                dragValue?.let { onSeek((it * duration).toLong()) }
                dragValue = null
            },
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = DesktopColors.TextPrimary,
                activeTrackColor = DesktopColors.TextPrimary,
                inactiveTrackColor = DesktopColors.SurfaceLight
            ),
            modifier = Modifier.weight(1f).height(20.dp).padding(horizontal = 8.dp)
        )
        TimeLabel(duration)
    }
}

@Composable
private fun TimeLabel(ms: Long) {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    Text(
        "%d:%02d".format(totalSeconds / 60, totalSeconds % 60),
        color = DesktopColors.TextGray,
        fontSize = 11.sp,
        modifier = Modifier.width(40.dp)
    )
}

@Composable
private fun ControlIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    active: Boolean = false,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp).fillMaxHeight()) {
        Icon(
            icon,
            description,
            tint = when {
                !enabled -> DesktopColors.SurfaceLight
                active -> DesktopColors.Accent
                else -> DesktopColors.TextGray
            }
        )
    }
}
