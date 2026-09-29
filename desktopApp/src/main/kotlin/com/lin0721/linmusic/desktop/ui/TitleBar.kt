package com.lin0721.linmusic.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowScope
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.desktop.ui.navigation.BackStack
import com.lin0721.linmusic.desktop.ui.navigation.DesktopRoute
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.theme.DesktopDimens

private val CloseHover = Color(0xFFE81123)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WindowScope.TitleBar(
    backStack: BackStack,
    isMaximized: Boolean,
    userProfile: UserProfile?,
    onAvatarClick: () -> Unit,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit
) {
    Box(Modifier.fillMaxWidth().height(DesktopDimens.TitleBarHeight)) {
        // 整条标题栏作拖动区，按钮叠在上层自行消费点击；
        // 双击取 AWT 自带的 clickCount 判断，不消费事件，避免手势检测吞掉紧随其后的拖动
        WindowDraggableArea(
            Modifier.fillMaxSize().onPointerEvent(PointerEventType.Press) { event ->
                if (event.awtEventOrNull?.clickCount == 2) onToggleMaximize()
            }
        ) {
            Box(Modifier.fillMaxSize())
        }
        Row(
            Modifier.align(Alignment.CenterStart).padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavArrow(Icons.AutoMirrored.Rounded.ArrowBackIos, "后退", backStack.canGoBack) { backStack.back() }
            NavArrow(Icons.AutoMirrored.Rounded.ArrowForwardIos, "前进", backStack.canGoForward) { backStack.forward() }
        }
        Row(
            Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(DesktopColors.Surface)
                    .clickable { backStack.navigate(DesktopRoute.Home) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Home, "首页", tint = DesktopColors.TextPrimary)
            }
            // 搜索页在 4b 接入，此处先保留入口外观
            Row(
                Modifier.width(420.dp).height(44.dp).clip(RoundedCornerShape(22.dp))
                    .background(DesktopColors.Surface).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, null, tint = DesktopColors.TextGray)
                Spacer(Modifier.width(10.dp))
                Text("想播放什么？", color = DesktopColors.TextGray, fontSize = 14.sp)
            }
        }
        Row(Modifier.align(Alignment.CenterEnd).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.padding(end = 12.dp).size(32.dp).clip(CircleShape).background(DesktopColors.Surface)
                    .clickable(onClick = onAvatarClick),
                contentAlignment = Alignment.Center
            ) {
                if (userProfile != null) {
                    Cover(userProfile.avatarUrl, 32.dp, shape = CircleShape)
                } else {
                    Icon(Icons.Rounded.Person, "登录", tint = DesktopColors.TextGray, modifier = Modifier.size(20.dp))
                }
            }
            WindowButton(Icons.Rounded.Remove, "最小化", onClick = onMinimize)
            WindowButton(
                if (isMaximized) Icons.Rounded.FilterNone else Icons.Rounded.CropSquare,
                if (isMaximized) "还原" else "最大化",
                onClick = onToggleMaximize
            )
            WindowButton(Icons.Rounded.Close, "关闭", hoverColor = CloseHover, onClick = onClose)
        }
    }
}

@Composable
private fun NavArrow(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(
            icon,
            description,
            tint = if (enabled) DesktopColors.TextPrimary else DesktopColors.SurfaceLight,
            modifier = Modifier.size(16.dp)
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WindowButton(
    icon: ImageVector,
    description: String,
    hoverColor: Color = DesktopColors.Surface,
    onClick: () -> Unit
) {
    var hovered by remember { mutableStateOf(false) }
    Box(
        Modifier.width(46.dp).fillMaxHeight()
            .background(if (hovered) hoverColor else Color.Transparent)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = DesktopColors.TextPrimary, modifier = Modifier.size(16.dp))
    }
}
