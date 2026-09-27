package com.lin0721.linmusic.core.download.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import com.lin0721.linmusic.core.download.DownloadWorkSnapshot
import com.lin0721.linmusic.core.download.SongDownloadManager
import com.lin0721.linmusic.core.ui.components.MiniStatusBanner
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import java.util.UUID

private const val SUMMARY_DISPLAY_MS = 2500L

private data class DownloadBannerContent(
    val icon: ImageVector,
    val title: String,
    val subtitle: String?,
    val progress: Float?,
    val trailingText: String?,
    val isSummary: Boolean
)

// 下载进度横幅，按一轮下载聚合进度，结束后短暂展示汇总
@Composable
fun DownloadProgressBanner(modifier: Modifier = Modifier) {
    val songDownloadManager: SongDownloadManager = koinInject()
    val worksFlow = remember(songDownloadManager) { songDownloadManager.observeUserDownloads() }
    val works by worksFlow
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // 只收录进行中的任务，排除历史已完成任务
    var sessionIds by remember { mutableStateOf(emptySet<UUID>()) }
    val activeIds = works.filterNot { it.state.isFinished }.map { it.workId }.toSet()
    LaunchedEffect(activeIds) {
        if (activeIds.isNotEmpty()) {
            sessionIds = sessionIds + activeIds
        } else if (sessionIds.isNotEmpty()) {
            delay(SUMMARY_DISPLAY_MS)
            sessionIds = emptySet()
        }
    }

    val sessionWorks = works.filter {
        (it.workId in sessionIds || it.workId in activeIds) && it.state != WorkInfo.State.CANCELLED
    }
    val content = sessionWorks.takeIf { it.isNotEmpty() }?.let(::buildBannerContent)

    var lastContent by remember { mutableStateOf<DownloadBannerContent?>(null) }
    if (content != null) lastContent = content

    val animatedProgress by animateFloatAsState(
        targetValue = lastContent?.progress ?: 0f,
        animationSpec = tween(300),
        label = "downloadProgress"
    )

    AnimatedVisibility(
        visible = content != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        val shown = lastContent ?: return@AnimatedVisibility
        MiniStatusBanner(
            icon = shown.icon,
            title = shown.title,
            subtitle = shown.subtitle,
            progress = shown.progress?.let { animatedProgress },
            trailingText = shown.trailingText,
            onClose = if (shown.isSummary) ({ sessionIds = emptySet() }) else null
        )
    }
}

private fun buildBannerContent(works: List<DownloadWorkSnapshot>): DownloadBannerContent {
    val total = works.size
    val succeeded = works.count { it.state == WorkInfo.State.SUCCEEDED }
    val failed = works.count { it.state == WorkInfo.State.FAILED }
    val settled = succeeded + failed
    val running = works.firstOrNull { it.state == WorkInfo.State.RUNNING }

    if (settled >= total) {
        val summary = buildString {
            append("成功 $succeeded 首")
            if (failed > 0) append("，失败 $failed 首")
        }
        return DownloadBannerContent(
            icon = if (failed == 0) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            title = if (failed == 0) "下载完成" else "下载结束",
            subtitle = if (total == 1 && failed == 0) "已保存到 Music/Melodia" else summary,
            progress = null,
            trailingText = null,
            isSummary = true
        )
    }

    val runningName = running?.songName?.ifBlank { null }
    val runningFraction = (running?.progress ?: 0).coerceIn(0, 100) / 100f
    val overall = ((settled + runningFraction) / total).coerceIn(0f, 1f)

    if (total == 1) {
        return DownloadBannerContent(
            icon = Icons.Rounded.Download,
            title = if (running == null) "等待下载…" else "正在下载 ${runningName ?: "歌曲"}",
            subtitle = null,
            progress = overall,
            trailingText = "${(overall * 100).toInt()}%",
            isSummary = false
        )
    }

    val subtitle = buildString {
        append("第 ${(settled + 1).coerceAtMost(total)}/$total 首")
        if (failed > 0) append(" · 失败 $failed")
    }
    return DownloadBannerContent(
        icon = Icons.Rounded.Download,
        title = if (runningName != null) "正在下载 $runningName" else "准备下载 $total 首歌曲",
        subtitle = subtitle,
        progress = overall,
        trailingText = "$settled/$total",
        isSummary = false
    )
}
