package com.lin0721.linmusic.desktop.ui

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LookaheadScope

// 与移动端平板首页一致的重排动画：卡片先按新排版算出目标位置与尺寸，再从旧位置平滑过去
const val LAYOUT_REFLOW_MS = 300

// 首页内容区的 LookaheadScope；右侧栏开合导致列数整体切换时，格子由此做位置与尺寸过渡
val LocalHomeLookaheadScope = staticCompositionLocalOf<LookaheadScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
private val HomeReflowBoundsTransform = BoundsTransform { _, _ ->
    tween(LAYOUT_REFLOW_MS, easing = FastOutSlowInEasing)
}

// 只对排版变化本身做动画；列表滚动这类父级整体移动不参与
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.homeReflowBounds(): Modifier {
    val scope = LocalHomeLookaheadScope.current ?: return this
    return this.animateBounds(lookaheadScope = scope, boundsTransform = HomeReflowBoundsTransform)
}
