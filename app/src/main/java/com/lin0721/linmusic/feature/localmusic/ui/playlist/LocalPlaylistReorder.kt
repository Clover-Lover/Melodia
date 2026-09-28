package com.lin0721.linmusic.feature.localmusic.ui.playlist

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

// LazyColumn 拖拽排序：被拖行跟随手指平移，其中线越过相邻行时交换位置并抵消位移，视觉上始终停在手指下。
// 列表里只能放可排序的行，下标即数据下标
@Stable
class LocalReorderState internal constructor(
    private val listState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set

    fun start(key: Any) {
        draggingKey = key
        dragOffset = 0f
    }

    fun drag(deltaY: Float) {
        val key = draggingKey ?: return
        dragOffset += deltaY
        val visible = listState.layoutInfo.visibleItemsInfo
        val current = visible.firstOrNull { it.key == key } ?: return
        val center = current.offset + dragOffset + current.size / 2f
        val target = visible.firstOrNull { item ->
            item.key != key && center >= item.offset && center <= item.offset + item.size
        } ?: return
        onMove(current.index, target.index)
        dragOffset -= (target.offset - current.offset)
    }

    fun end() {
        draggingKey = null
        dragOffset = 0f
    }
}

@Composable
fun rememberLocalReorderState(listState: LazyListState, onMove: (from: Int, to: Int) -> Unit): LocalReorderState {
    val currentOnMove by rememberUpdatedState(onMove)
    return remember(listState) { LocalReorderState(listState) { from, to -> currentOnMove(from, to) } }
}
