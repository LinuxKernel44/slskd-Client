package com.linuxkernel44.slskdclient.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-wide singletons: stores plus the API client of the active profile. */
class Graph(val context: Context) {
    val profiles = ProfileStore(context)
    val settings = SettingsStore(context)

    private val _client = MutableStateFlow<ApiClient?>(null)
    val client: StateFlow<ApiClient?> = _client.asStateFlow()

    /** Bumped whenever the server rejects our credentials and no saved password could renew the session. */
    private val _authLost = MutableStateFlow(0)
    val authLost: StateFlow<Int> = _authLost.asStateFlow()

    fun connect(profile: Profile): ApiClient {
        profiles.setActive(profile.id)
        return ApiClient(profile, profiles, onAuthLost = { _authLost.value += 1 }).also { _client.value = it }
    }

    /** Rebuilds the client for the active profile (used by background components after a process restart). */
    fun ensureClient(): ApiClient? {
        _client.value?.let { return it }
        val p = profiles.active() ?: return null
        return connect(p)
    }

    fun disconnect() {
        _client.value?.logout()
        _client.value = null
    }

    /** Drops the in-memory client without touching the saved profile (e.g. when switching profile). */
    fun release() { _client.value = null }
}
