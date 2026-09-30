package com.lin0721.linmusic.desktop.platform

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class CloseAction { TRAY, EXIT }

// 桌面端独有设置；快捷键以 “动作=修饰键:键码” 分号拼接存储，空值表示用户已清除该键
class DesktopPreferences(private val dataStore: DataStore<Preferences>) {

    companion object {
        const val STORE_NAME = "desktop_prefs"

        private val KEY_HOTKEYS = stringPreferencesKey("hotkeys")
        private val KEY_MEDIA_KEYS_ENABLED = booleanPreferencesKey("media_keys_enabled")
        private val KEY_CLOSE_ACTION = stringPreferencesKey("close_action")
    }

    val hotkeys: Flow<Map<HotkeyAction, HotkeyCombo?>> = dataStore.data.map { prefs ->
        val saved = parseHotkeys(prefs[KEY_HOTKEYS].orEmpty())
        HotkeyAction.entries.associateWith { action ->
            if (saved.containsKey(action)) saved[action] else HotkeyCombo.defaults[action]
        }
    }.distinctUntilChanged()

    val mediaKeysEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_MEDIA_KEYS_ENABLED] ?: true }.distinctUntilChanged()

    val closeAction: Flow<CloseAction> = dataStore.data.map { prefs ->
        prefs[KEY_CLOSE_ACTION]?.let { name -> CloseAction.entries.firstOrNull { it.name == name } } ?: CloseAction.TRAY
    }.distinctUntilChanged()

    suspend fun saveHotkeys(hotkeys: Map<HotkeyAction, HotkeyCombo?>) {
        dataStore.edit { prefs ->
            prefs[KEY_HOTKEYS] = hotkeys.entries.joinToString(";") { (action, combo) -> "${action.name}=${combo?.encode().orEmpty()}" }
        }
    }

    suspend fun saveMediaKeysEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_MEDIA_KEYS_ENABLED] = enabled }
    }

    suspend fun saveCloseAction(action: CloseAction) {
        dataStore.edit { it[KEY_CLOSE_ACTION] = action.name }
    }

    // 解析失败的条目忽略，回落到默认值
    private fun parseHotkeys(raw: String): Map<HotkeyAction, HotkeyCombo?> {
        if (raw.isBlank()) return emptyMap()
        val result = mutableMapOf<HotkeyAction, HotkeyCombo?>()
        raw.split(';').forEach { entry ->
            val (name, value) = entry.split('=', limit = 2).takeIf { it.size == 2 } ?: return@forEach
            val action = HotkeyAction.entries.firstOrNull { it.name == name } ?: return@forEach
            if (value.isEmpty()) {
                result[action] = null
            } else {
                HotkeyCombo.decode(value)?.let { result[action] = it }
            }
        }
        return result
    }
}
