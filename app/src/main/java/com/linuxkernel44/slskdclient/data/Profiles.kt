package com.linuxkernel44.slskdclient.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.util.UUID

/** A saved slskd server. [password] and [token] are stored encrypted; they are only kept when [rememberMe] is on. */
@Serializable
data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val url: String = "",
    /** Base URL of the optional slskd-files companion server (audio streaming / save to phone). Blank = derived from [url]. */
    val filesUrl: String = "",
    val username: String = "",
    val rememberMe: Boolean = true,
    val allowSelfSigned: Boolean = false,
    val encPassword: String = "",
    val encToken: String = "",
) {
    val password get() = Secrets.decrypt(encPassword)
    val token get() = Secrets.decrypt(encToken)
    val displayName get() = name.ifBlank { url.removePrefix("http://").removePrefix("https://").trimEnd('/') }
    val baseUrl get() = normalizeUrl(url)
    val filesBaseUrl: String
        get() = if (filesUrl.isNotBlank()) normalizeUrl(filesUrl) else {
            val u = baseUrl
            val scheme = u.substringBefore("://")
            val host = u.substringAfter("://").substringBefore('/').substringBefore(':')
            "$scheme://$host:15501"
        }
}

fun normalizeUrl(raw: String): String {
    var u = raw.trim()
    if (u.isEmpty()) return ""
    if (!u.contains("://")) u = "http://$u"
    return u.trimEnd('/')
}

/** Profile persistence plus the id of the active profile. */
class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("profiles", Context.MODE_PRIVATE)
    private val _profiles = MutableStateFlow(load())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private fun load(): List<Profile> = runCatching {
        AppJson.decodeFromString<List<Profile>>(prefs.getString("list", "[]") ?: "[]")
    }.getOrDefault(emptyList())

    val activeId: String? get() = prefs.getString("active", null)
    fun setActive(id: String?) = prefs.edit().apply { if (id == null) remove("active") else putString("active", id) }.apply()

    fun get(id: String?): Profile? = _profiles.value.firstOrNull { it.id == id }
    fun active(): Profile? = get(activeId)

    fun save(profile: Profile) {
        val list = _profiles.value.toMutableList()
        val i = list.indexOfFirst { it.id == profile.id }
        if (i >= 0) list[i] = profile else list.add(profile)
        persist(list)
    }

    fun delete(id: String) {
        persist(_profiles.value.filterNot { it.id == id })
        if (activeId == id) setActive(null)
    }

    private fun persist(list: List<Profile>) {
        _profiles.value = list
        prefs.edit().putString("list", AppJson.encodeToString(list)).apply()
    }
}
