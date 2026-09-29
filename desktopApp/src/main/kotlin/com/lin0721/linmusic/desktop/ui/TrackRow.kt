package com.lin0721.linmusic.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors

// 双击播放，与桌面音乐客户端习惯一致
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TrackRow(
    index: Int,
    track: Track,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    var hovered by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
            .background(if (hovered) DesktopColors.PaneHover else Color.Transparent)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .onPointerEvent(PointerEventType.Press) { event ->
                if (event.awtEventOrNull?.clickCount == 2) onPlay()
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${index + 1}",
            color = if (isCurrent) DesktopColors.Accent else DesktopColors.TextGray,
            fontSize = 14.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(32.dp)
        )
        Cover(track.al.picUrl, 40.dp, modifier = Modifier.padding(start = 16.dp))
        Column(Modifier.weight(0.45f).padding(start = 12.dp)) {
            Text(
                track.name,
                color = if (isCurrent) DesktopColors.Accent else DesktopColors.TextPrimary,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                track.ar.joinToString(" / ") { it.name },
                color = DesktopColors.TextGray,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            track.al.name,
            color = DesktopColors.TextGray,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.35f).padding(horizontal = 12.dp)
        )
        Text(
            formatDuration(track.dt),
            color = DesktopColors.TextGray,
            fontSize = 13.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(56.dp)
        )
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
