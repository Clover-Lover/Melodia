package com.lin0721.linmusic.core.download.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.download.DownloadTask
import com.lin0721.linmusic.core.download.DownloadTaskStatus
import com.lin0721.linmusic.core.download.SongDownloadManager
import com.lin0721.linmusic.core.model.getQualityDisplayName
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.theme.BackgroundDark
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.DownloadFailedRed
import com.lin0721.linmusic.core.ui.theme.DownloadedGreen
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.SurfaceDark
import com.lin0721.linmusic.core.ui.theme.TextGray
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val TrackGray = Color(0xFF333333)
private val PendingGray = Color(0xFF555555)
private val MutedText = Color(0xFF8A8A8A)

// 下载管理面板的分类
enum class DownloadManagerTab(val label: String) {
    ACTIVE("进行中"),
    SUCCEEDED("已完成"),
    FAILED("失败");

    fun matches(task: DownloadTask): Boolean = when (this) {
        ACTIVE -> task.isUnfinished
        SUCCEEDED -> task.status == DownloadTaskStatus.SUCCEEDED
        FAILED -> task.status == DownloadTaskStatus.FAILED
    }
}

// 下载任务计数
internal data class DownloadCounts(
    val total: Int,
    val succeeded: Int,
    val failed: Int,
    val running: Int,
    val waiting: Int,
    val paused: Int,
    val runningFraction: Float
) {
    val active: Int get() = running + waiting
    val unfinished: Int get() = active + paused
    val settled: Int get() = succeeded + failed
}

internal fun List<DownloadTask>.counts(): DownloadCounts {
    val running = filter { it.status == DownloadTaskStatus.RUNNING }
    return DownloadCounts(
        total = size,
        succeeded = count { it.status == DownloadTaskStatus.SUCCEEDED },
        failed = count { it.status == DownloadTaskStatus.FAILED },
        running = running.size,
        waiting = count { it.status == DownloadTaskStatus.WAITING },
        paused = count { it.status == DownloadTaskStatus.PAUSED },
        runningFraction = running.sumOf { it.progress.coerceIn(0, 100) } / 100f
    )
}

// 下载管理面板：查看各任务进度，取消、重试与清理
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadManagerSheet(
    onDismiss: () -> Unit,
    initialTab: DownloadManagerTab = DownloadManagerTab.ACTIVE
) {
    val manager: SongDownloadManager = koinInject()
    val tasksFlow = remember(manager) { manager.observeTasks() }
    val tasks by tasksFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    val counts = tasks.counts()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BackgroundDark,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .navigationBarsPadding()
        ) {
            Text(
                text = "下载管理",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = MelodiaSpacing.lg)
            )
            Spacer(Modifier.height(MelodiaSpacing.md))

            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "暂无下载任务", color = MutedText, fontSize = 14.sp)
                }
                return@Column
            }

            SummaryCard(
                counts = counts,
                onPauseAll = { scope.launch { manager.pause(tasks) } },
                onResumeAll = { scope.launch { manager.resume(tasks) } },
                onRetryFailed = { scope.launch { manager.retry(tasks) } },
                onClearSucceeded = {
                    scope.launch { manager.dismiss(tasks.filter { it.status == DownloadTaskStatus.SUCCEEDED }) }
                }
            )

            Row(
                modifier = Modifier.padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DownloadManagerTab.entries.forEach { entry ->
                    val count = when (entry) {
                        DownloadManagerTab.ACTIVE -> counts.unfinished
                        DownloadManagerTab.SUCCEEDED -> counts.succeeded
                        DownloadManagerTab.FAILED -> counts.failed
                    }
                    TabChip(
                        text = "${entry.label} $count",
                        selected = tab == entry,
                        accent = if (entry == DownloadManagerTab.FAILED && count > 0) DownloadFailedRed else null,
                        onClick = { tab = entry }
                    )
                }
            }

            val visible = tasks.filter(tab::matches)
            if (visible.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "没有${tab.label}的任务", color = MutedText, fontSize = 14.sp)
                }
            } else {
                val groups = visible.groupBy { it.meta.batchLabel ?: "单曲" }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = MelodiaSpacing.sm, end = MelodiaSpacing.sm, bottom = MelodiaSpacing.lg)
                ) {
                    groups.forEach { (label, groupTasks) ->
                        item(key = "header_$label") {
                            GroupHeader(
                                label = label,
                                tasks = groupTasks,
                                tab = tab,
                                onPause = { scope.launch { manager.pause(groupTasks) } },
                                onResume = { scope.launch { manager.resume(groupTasks) } },
                                onCancel = { scope.launch { manager.cancel(groupTasks) } },
                                onRetry = { scope.launch { manager.retry(groupTasks) } }
                            )
                        }
                        items(groupTasks, key = { it.meta.workId }) { task ->
                            TaskRow(
                                task = task,
                                onPause = { scope.launch { manager.pause(listOf(task)) } },
                                onResume = { scope.launch { manager.resume(listOf(task)) } },
                                onCancel = { scope.launch { manager.cancel(listOf(task)) } },
                                onRetry = { scope.launch { manager.retry(listOf(task)) } }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    counts: DownloadCounts,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onRetryFailed: () -> Unit,
    onClearSucceeded: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = MelodiaSpacing.md)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark.copy(alpha = 0.6f))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = "${counts.settled}", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                text = " / ${counts.total} 首",
                color = MutedText,
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        DownloadSegmentedProgress(counts = counts, height = 6.dp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot(DownloadedGreen, "完成 ${counts.succeeded}")
            LegendDot(Color.White, "下载中 ${counts.running}")
            LegendDot(PendingGray, if (counts.paused > 0) "等待 ${counts.waiting} · 暂停 ${counts.paused}" else "等待 ${counts.waiting}")
            LegendDot(DownloadFailedRed, "失败 ${counts.failed}")
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (counts.active == 0 && counts.paused > 0) {
                ActionPill("全部继续", enabled = true, onClick = onResumeAll, modifier = Modifier.weight(1f))
            } else {
                ActionPill("全部暂停", enabled = counts.active > 0, onClick = onPauseAll, modifier = Modifier.weight(1f))
            }
            ActionPill(
                text = if (counts.failed > 0) "重试失败 ${counts.failed}" else "重试失败",
                enabled = counts.failed > 0,
                color = DownloadFailedRed,
                onClick = onRetryFailed,
                modifier = Modifier.weight(1f)
            )
            ActionPill("清除已完成", enabled = counts.succeeded > 0, onClick = onClearSucceeded, modifier = Modifier.weight(1f))
        }
    }
}

// 分段进度条：绿=完成，红=失败，白=下载中，灰底=等待
@Composable
internal fun DownloadSegmentedProgress(counts: DownloadCounts, height: Dp, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(TrackGray),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val remaining = counts.total - counts.settled - counts.runningFraction
        if (counts.succeeded > 0) Box(Modifier.weight(counts.succeeded.toFloat()).fillMaxHeight().background(DownloadedGreen))
        if (counts.failed > 0) Box(Modifier.weight(counts.failed.toFloat()).fillMaxHeight().background(DownloadFailedRed))
        if (counts.runningFraction > 0f) Box(Modifier.weight(counts.runningFraction).fillMaxHeight().background(Color.White))
        if (remaining > 0f) Spacer(Modifier.weight(remaining))
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(text = text, color = TextGray, fontSize = 12.sp)
    }
}

@Composable
private fun ActionPill(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) color else MutedText.copy(alpha = 0.6f),
            fontSize = 13.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun TabChip(text: String, selected: Boolean, accent: Color?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = when {
                selected -> BackgroundDark
                accent != null -> accent
                else -> TextGray
            },
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun GroupHeader(
    label: String,
    tasks: List<DownloadTask>,
    tab: DownloadManagerTab,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = MelodiaSpacing.sm, end = MelodiaSpacing.xs, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(text = "${tasks.size} 首", color = MutedText, fontSize = 12.sp)
        }
        when {
            tab == DownloadManagerTab.ACTIVE && tasks.size > 1 -> {
                if (tasks.any { it.isActive }) {
                    TextAction("暂停", Color.White, onPause)
                } else {
                    TextAction("继续", Color.White, onResume)
                }
                TextAction("取消", MutedText, onCancel)
            }
            tab == DownloadManagerTab.FAILED && tasks.size > 1 -> TextAction("全部重试", DownloadFailedRed, onRetry)
        }
    }
}

@Composable
private fun TextAction(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text = text,
        color = color,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    )
}

@Composable
private fun TaskRow(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    val meta = task.meta
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.sm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val cover = meta.coverUrl?.let { url ->
            if (url.startsWith("http://") || url.startsWith("https://")) "$url?param=120y120" else url
        }
        SubcomposeAsyncImage(
            model = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            loading = { CoverPlaceholder() },
            error = { CoverPlaceholder() },
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = meta.songName,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .border(1.dp, MutedText, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp)
                ) {
                    Text(text = getQualityDisplayName(meta.level), color = TextGray, fontSize = 10.sp, maxLines = 1)
                }
            }
            Spacer(Modifier.height(2.dp))
            val secondary = when {
                task.status == DownloadTaskStatus.FAILED -> task.failureReason ?: "下载失败"
                task.skipped -> "${meta.artistName} · 已存在，未重复下载"
                else -> meta.artistName
            }
            Text(
                text = secondary,
                color = if (task.status == DownloadTaskStatus.FAILED) DownloadFailedRed else MutedText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (task.status == DownloadTaskStatus.RUNNING) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { task.progress.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = Color.White,
                    trackColor = TrackGray,
                    drawStopIndicator = {}
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        when (task.status) {
            DownloadTaskStatus.RUNNING, DownloadTaskStatus.WAITING, DownloadTaskStatus.PAUSED -> {
                Text(
                    text = when (task.status) {
                        DownloadTaskStatus.RUNNING -> "${task.progress}%"
                        DownloadTaskStatus.PAUSED -> "已暂停"
                        else -> "等待中"
                    },
                    color = if (task.status == DownloadTaskStatus.RUNNING) Color.White else MutedText,
                    fontSize = 12.sp
                )
                if (task.status == DownloadTaskStatus.PAUSED) {
                    IconButton(onClick = onResume) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "继续下载", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                } else {
                    IconButton(onClick = onPause) {
                        Icon(Icons.Rounded.Pause, contentDescription = "暂停下载", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "取消下载", tint = MutedText, modifier = Modifier.size(18.dp))
                }
            }
            DownloadTaskStatus.FAILED -> {
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "重试", color = Color.White, fontSize = 12.sp)
                }
            }
            DownloadTaskStatus.SUCCEEDED -> {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "已完成",
                    tint = DownloadedGreen,
                    modifier = Modifier.padding(end = 8.dp).size(20.dp)
                )
            }
        }
    }
}
