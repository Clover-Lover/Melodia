package com.lin0721.linmusic.feature.settings.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.source.SourcePreferences
import com.lin0721.linmusic.core.source.UnmApiClient
import com.lin0721.linmusic.core.source.UnmModule
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.MelodiaSwitch
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.PillRadius
import kotlinx.coroutines.launch

@Composable
fun AudioSourcesSettingsView(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val searchAggregationEnabled by viewModel.searchAggregationEnabled.collectAsStateWithLifecycle()
    val fallbackEnabled by viewModel.fallbackEnabled.collectAsStateWithLifecycle()
    val unmServerUrl by viewModel.unmServerUrl.collectAsStateWithLifecycle()
    val unmRemoteFallbackEnabled by viewModel.unmRemoteFallbackEnabled.collectAsStateWithLifecycle()
    val unmAutoMatch by viewModel.unmAutoMatch.collectAsStateWithLifecycle()
    val unmEnabledModules by viewModel.unmEnabledModules.collectAsStateWithLifecycle()
    val unmModuleOrder by viewModel.unmModuleOrder.collectAsStateWithLifecycle()

    val lxPluginEnabled by viewModel.lxPluginEnabled.collectAsStateWithLifecycle()
    val lxPluginName by viewModel.lxPluginName.collectAsStateWithLifecycle()
    val lxPluginVersion by viewModel.lxPluginVersion.collectAsStateWithLifecycle()
    val lxPluginAuthor by viewModel.lxPluginAuthor.collectAsStateWithLifecycle()
    val lxPluginDesc by viewModel.lxPluginDesc.collectAsStateWithLifecycle()
    val lxPluginSources by viewModel.lxPluginSources.collectAsStateWithLifecycle()

    var showServerUrlDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    // 系统文件选取器 (选取 .js 文件)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().readText()
                    }
                    if (!content.isNullOrBlank()) {
                        val result = viewModel.importLxScript(content)
                        if (result.isSuccess) {
                            ToastManager.showToast("插件导入成功: ${result.getOrNull()?.name}")
                        } else {
                            ToastManager.showToast("插件解析失败，请检查脚本格式")
                        }
                    }
                } catch (e: Exception) {
                    ToastManager.showToast("读取文件失败: ${e.message}")
                }
            }
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
        contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomOverlayInset.current + 16.dp)
    ) {
        // 分组 1：换源开关
        item {
            SettingsGroupCard(SettingsSubMenu.AUDIO_SOURCES.sectionTitles[0]) {
                SettingsSwitchRow(
                    title = "无版权/VIP 自动换源",
                    subtitle = "官方网易云音源不可用或仅为试听时，优先通过本地直连音源获取完整播放直链",
                    checked = fallbackEnabled,
                    onCheckedChange = { viewModel.updateFallbackEnabled(it) }
                )
                SettingsSwitchRow(
                    title = "多平台聚合搜索",
                    subtitle = "在搜索页面提供多平台切换标签，可检索酷狗、酷我、QQ 音乐等外部曲库",
                    checked = searchAggregationEnabled,
                    onCheckedChange = { viewModel.updateSearchAggregationEnabled(it) }
                )
            }
        }

        // 分组 2：本地直连音源
        item {
            SettingsGroupCard(SettingsSubMenu.AUDIO_SOURCES.sectionTitles[1]) {
                Text(
                    text = "优先在本地设备直接向音源发起请求，以下都是第三方维护的音源，如果有条件可以给他们一点赞助：",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = MelodiaSpacing.sm)
                )

                unmModuleOrder.forEachIndexed { index, moduleKey ->
                    val module = UnmModule.fromKey(moduleKey)
                    val displayName = module?.displayName ?: moduleKey
                    val description = module?.description ?: "第三方音源模块"
                    val isEnabled = moduleKey in unmEnabledModules

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = if (isEnabled) 0.15f else 0.05f),
                                modifier = Modifier.size(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(MelodiaSpacing.sm))
                            Column {
                                Text(
                                    text = displayName,
                                    color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = description,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isEnabled) 1f else 0.4f),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // 开关与上下移动操作按钮
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MelodiaSwitch(
                                checked = isEnabled,
                                onCheckedChange = { viewModel.toggleUnmModule(moduleKey) }
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            MelodiaIconButton(
                                onClick = { viewModel.moveUnmModuleUp(moduleKey) },
                                enabled = index > 0
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "上移",
                                    tint = if (index > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            MelodiaIconButton(
                                onClick = { viewModel.moveUnmModuleDown(moduleKey) },
                                enabled = index < unmModuleOrder.size - 1
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "下移",
                                    tint = if (index < unmModuleOrder.size - 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    if (index < unmModuleOrder.size - 1) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    }
                }
            }
        }

        // 分组 3：远程兜底服务
        item {
            SettingsGroupCard(SettingsSubMenu.AUDIO_SOURCES.sectionTitles[2]) {
                SettingsSwitchRow(
                    title = "启用远程兜底服务",
                    subtitle = "当本地所有已启用的音源均未解析成功时，向远程 UNM 服务器请求兜底",
                    checked = unmRemoteFallbackEnabled,
                    onCheckedChange = { viewModel.updateUnmRemoteFallbackEnabled(it) }
                )

                if (unmRemoteFallbackEnabled) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    SettingsRow(
                        title = "兜底服务接口 (Base URL)",
                        subtitle = unmServerUrl.ifBlank { "未配置（点击设置服务器地址）" },
                        onClick = { showServerUrlDialog = true }
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    SettingsSwitchRow(
                        title = "服务端自动选择模式",
                        subtitle = "由服务端自动轮询最优源，关闭后按本地模块顺序依次向远程请求",
                        checked = unmAutoMatch,
                        onCheckedChange = { viewModel.updateUnmAutoMatch(it) }
                    )
                }
            }
        }

        // 分组 4：社区扩展源 (LX Music 脚本)
        item {
            SettingsGroupCard("社区扩展源 (LX Music 脚本)") {
                val hasPlugin = lxPluginName.isNotBlank()

                SettingsSwitchRow(
                    title = "启用社区源插件",
                    subtitle = if (hasPlugin) "UNM 服务未命中时，调用已加载的社区自定义源脚本兜底解析" else "尚未导入脚本，请先导入 .js 脚本",
                    checked = lxPluginEnabled && hasPlugin,
                    onCheckedChange = {
                        if (hasPlugin) {
                            viewModel.updateLxPluginEnabled(it)
                        } else {
                            ToastManager.showToast("请先导入源脚本")
                        }
                    }
                )

                if (hasPlugin) {
                    Spacer(modifier = Modifier.height(MelodiaSpacing.xs))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$lxPluginName (v$lxPluginVersion)",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                TextButton(
                                    onClick = { viewModel.removeLxPlugin() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("卸载", fontSize = 12.sp)
                                }
                            }

                            if (lxPluginAuthor.isNotBlank()) {
                                Text(
                                    text = "作者: $lxPluginAuthor",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (lxPluginDesc.isNotBlank()) {
                                Text(
                                    text = lxPluginDesc,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            if (lxPluginSources.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("支持平台:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    lxPluginSources.forEach { sourceKey ->
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = formatSourceBadge(sourceKey),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(MelodiaSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)
                ) {
                    OutlinedButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(PillRadius),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("选取文件 (.js)", fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = { showImportDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(PillRadius),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("粘贴脚本导入", fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // 服务器地址配置弹窗
    if (showServerUrlDialog) {
        ServerUrlEditDialog(
            currentUrl = unmServerUrl,
            onDismiss = { showServerUrlDialog = false },
            onSave = { newUrl ->
                viewModel.updateUnmServerUrl(newUrl)
                showServerUrlDialog = false
            },
            onReset = {
                viewModel.resetUnmServerUrl()
                showServerUrlDialog = false
            }
        )
    }

    // 粘贴脚本导入弹窗
    if (showImportDialog) {
        LxScriptImportDialog(
            onDismiss = { showImportDialog = false },
            onImport = { script ->
                coroutineScope.launch {
                    val result = viewModel.importLxScript(script)
                    if (result.isSuccess) {
                        ToastManager.showToast("插件导入成功: ${result.getOrNull()?.name}")
                    } else {
                        ToastManager.showToast("插件初始化失败，请检查脚本语法")
                    }
                    showImportDialog = false
                }
            }
        )
    }
}

// 格式化平台支持角标
private fun formatSourceBadge(key: String): String = when (key) {
    "kw" -> "酷我"
    "kg" -> "酷狗"
    "tx" -> "QQ"
    "mg" -> "咪咕"
    "wy" -> "网易云"
    else -> key.uppercase()
}

// 服务器地址配置弹窗
@Composable
private fun ServerUrlEditDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onReset: () -> Unit
) {
    var textValue by remember { mutableStateOf(currentUrl) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "配置换源服务器接口", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "填写 UNM Utils 服务的基础访问 URL（末尾无需带斜杠）：",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = MelodiaSpacing.sm)
                )
                OutlinedTextField(
                    value = textValue,
                    onValueChange = {
                        textValue = it
                        testResult = null
                    },
                    placeholder = { Text("例如 https://your-unm-server.com", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                isTesting = true
                                testResult = UnmApiClient().testConnection(textValue.trim())
                                isTesting = false
                            }
                        },
                        enabled = !isTesting && textValue.isNotBlank()
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("检测中...", fontSize = 12.sp)
                        } else {
                            Text("测试连接", fontSize = 12.sp)
                        }
                    }

                    testResult?.let { success ->
                        Text(
                            text = if (success) "✓ 服务正常可用" else "✕ 连接失败，请检查地址",
                            color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(textValue.trim()) }
            ) {
                Text("保存", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    textValue = ""
                    testResult = null
                    onReset()
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("清空")
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )
}

// 粘贴脚本文本导入弹窗
@Composable
private fun LxScriptImportDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var textValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "导入 LX 插件脚本", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "粘贴完整的落雪音乐自定义音源 JavaScript 脚本内容：",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = MelodiaSpacing.sm)
                )
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    placeholder = { Text("粘贴脚本文本 (含 globalThis.lx)...", fontSize = 13.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    maxLines = 10
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onImport(textValue.trim()) },
                enabled = textValue.isNotBlank()
            ) {
                Text("解析并导入", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
