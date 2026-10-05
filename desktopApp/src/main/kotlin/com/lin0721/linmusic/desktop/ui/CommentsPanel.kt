package com.lin0721.linmusic.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.comment.data.CommentSortType
import com.lin0721.linmusic.core.comment.ui.CommentsState
import com.lin0721.linmusic.desktop.ui.nowplaying.CommentRow
import com.lin0721.linmusic.desktop.ui.nowplaying.allComments
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel

// 距列表末尾还剩这么多条时开始加载下一页
private const val LOAD_MORE_THRESHOLD = 3

private val SortTypes = listOf(CommentSortType.RECOMMEND, CommentSortType.HOT, CommentSortType.LATEST)

private fun sortLabel(type: CommentSortType): String = when (type) {
    CommentSortType.RECOMMEND -> "推荐"
    CommentSortType.HOT -> "最热"
    CommentSortType.LATEST -> "最新"
}

// 右侧栏的完整评论：排序、评论流与加载更多
@Composable
fun CommentsPanel(
    playerViewModel: PlayerViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by playerViewModel.commentsState.collectAsState()
    val total = state.totalCount ?: 0

    Column(modifier.fillMaxSize().padding(top = 16.dp)) {
        OverlayPanelHeader(
            title = if (total > 0) "评论 ($total)" else "评论",
            closeDescription = "关闭评论",
            onClose = onClose,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        // 排序栏常驻顶部，不受内容区加载态影响
        TabBar(
            tabs = SortTypes,
            selected = state.sortType,
            label = ::sortLabel,
            onSelect = playerViewModel::changeCommentSort,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            small = true
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val current = state) {
                is CommentsState.Loading -> CenteredSpinner()
                is CommentsState.Error -> Column(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("加载失败: ${current.message}", color = DesktopColors.TextGray, fontSize = 14.sp, textAlign = TextAlign.Center)
                    Button(
                        onClick = playerViewModel::retryComments,
                        colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Accent),
                        modifier = Modifier.padding(top = 12.dp)
                    ) { Text("重试", color = DesktopColors.TextPrimary) }
                }
                is CommentsState.Success -> CommentList(current, playerViewModel)
            }
        }
    }
}

@Composable
private fun CommentList(state: CommentsState.Success, playerViewModel: PlayerViewModel) {
    val comments = remember(state.hotComments, state.comments) { allComments(state) }
    if (comments.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无评论", color = DesktopColors.TextGray, fontSize = 14.sp)
        }
        return
    }

    val listState = rememberLazyListState()
    val nearEnd by remember(listState, comments.size) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= comments.size - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(nearEnd, state.hasMore, state.isLoadingMore) {
        if (nearEnd && state.hasMore && !state.isLoadingMore) playerViewModel.loadMoreComments()
    }

    HoverScrollbarBox(listState) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp)
        ) {
            items(comments, key = { it.commentId }) { comment ->
                CommentRow(comment, onLike = { playerViewModel.likeComment(comment) })
            }
            if (state.isLoadingMore) {
                item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { Spinner(20) } }
            }
        }
    }
}

@Composable
private fun CenteredSpinner() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner(24) }
}

@Composable
private fun Spinner(size: Int) {
    CircularProgressIndicator(color = DesktopColors.Accent, modifier = Modifier.size(size.dp), strokeWidth = 2.dp)
}
