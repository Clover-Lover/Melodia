package com.lin0721.linmusic.core.source

import kotlinx.serialization.Serializable

// LX 插件元信息模型
@Serializable
data class LxPluginInfo(
    val name: String = "",
    val version: String = "",
    val author: String = "",
    val description: String = "",
    val sources: List<String> = emptyList(),
    val rawScript: String = ""
)
