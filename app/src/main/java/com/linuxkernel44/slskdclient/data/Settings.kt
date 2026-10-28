package com.linuxkernel44.slskdclient.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val theme: String = "dark",            // dark | light | system
    val glass: Boolean = true,
    val glassIntensity: Float = 0.7f,
    val notifyDownloads: Boolean = true,
    val notifyMessages: Boolean = true,
    val backgroundService: Boolean = true,
    val checkUpdates: Boolean = true,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    private fun read() = AppSettings(
        theme = prefs.getString("theme", "dark") ?: "dark",
        glass = prefs.getBoolean("glass", true),
        glassIntensity = prefs.getFloat("glassIntensity", 0.7f),
        notifyDownloads = prefs.getBoolean("notifyDownloads", true),
        notifyMessages = prefs.getBoolean("notifyMessages", true),
        backgroundService = prefs.getBoolean("backgroundService", true),
        checkUpdates = prefs.getBoolean("checkUpdates", true),
    )

    fun update(block: (AppSettings) -> AppSettings) {
        val s = block(_state.value)
        _state.value = s
        prefs.edit().putString("theme", s.theme).putBoolean("glass", s.glass).putFloat("glassIntensity", s.glassIntensity)
            .putBoolean("notifyDownloads", s.notifyDownloads).putBoolean("notifyMessages", s.notifyMessages)
            .putBoolean("backgroundService", s.backgroundService).putBoolean("checkUpdates", s.checkUpdates).apply()
    }
}
