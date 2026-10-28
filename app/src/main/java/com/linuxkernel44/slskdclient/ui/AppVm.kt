package com.linuxkernel44.slskdclient.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.ColumnScope
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.linuxkernel44.slskdclient.data.ApiClient
import com.linuxkernel44.slskdclient.data.ApiException
import com.linuxkernel44.slskdclient.data.Profile
import com.linuxkernel44.slskdclient.graph
import com.linuxkernel44.slskdclient.player.PlayerController
import com.linuxkernel44.slskdclient.save.Saver
import com.linuxkernel44.slskdclient.service.WatchService
import com.linuxkernel44.slskdclient.update.AppUpdate
import com.linuxkernel44.slskdclient.update.Updater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface AuthState {
    data object Starting : AuthState
    data object NoProfile : AuthState
    data class NeedsLogin(val profile: Profile, val message: String? = null) : AuthState
    data object Ready : AuthState
}

object Tabs { const val SEARCH = 0; const val TRANSFERS = 1; const val SOCIAL = 2; const val LIBRARY = 3; const val SYSTEM = 4 }

class AppVm(app: Application) : AndroidViewModel(app) {
    val graph = app.graph
    val player = PlayerController(app)
    val saver = Saver(app, graph)
    private val updater = Updater(app)

    var auth by mutableStateOf<AuthState>(AuthState.Starting); private set
    val api: ApiClient? get() = graph.client.value

    // ---- navigation shared across screens ----
    var tab by mutableIntStateOf(Tabs.SEARCH)
    var socialTab by mutableIntStateOf(0)          // 0 messages, 1 rooms, 2 users
    var libraryTab by mutableIntStateOf(0)         // 0 files, 1 shares
    var systemTab by mutableIntStateOf(0)
    var chatTarget by mutableStateOf<String?>(null)
    var userTarget by mutableStateOf<String?>(null)
    var roomTarget by mutableStateOf<String?>(null)
    var searchSeed by mutableStateOf<String?>(null)
    var showProfiles by mutableStateOf(false)
    var showPlayer by mutableStateOf(false)

    fun openChat(username: String) { chatTarget = username; socialTab = 0; tab = Tabs.SOCIAL }
    fun openUser(username: String) { userTarget = username; socialTab = 2; tab = Tabs.SOCIAL }
    fun openRoom(name: String) { roomTarget = name; socialTab = 1; tab = Tabs.SOCIAL }
    fun searchFor(text: String) { searchSeed = text; tab = Tabs.SEARCH }

    // ---- transient UI ----
    var toast by mutableStateOf<String?>(null); private set
    private var toastJob: Job? = null
    fun say(message: String) {
        toast = message
        toastJob?.cancel()
        toastJob = viewModelScope.launch { delay(3200); toast = null }
    }

    var sheet by mutableStateOf<(@Composable ColumnScope.() -> Unit)?>(null); private set
    fun showSheet(content: @Composable ColumnScope.() -> Unit) { sheet = content }
    fun closeSheet() { sheet = null }

    /** Runs [block] off the main thread, reporting failures as a toast. Returns the job. */
    fun run(success: String? = null, onDone: () -> Unit = {}, block: suspend (ApiClient) -> Unit): Job = viewModelScope.launch {
        val client = api ?: return@launch
        try {
            block(client)
            success?.let { say(it) }
            onDone()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            say(friendly(e))
        }
    }

    fun friendly(e: Throwable): String = when (e) {
        is ApiException -> if (e.code == 401) "Session expired — sign in again" else e.message ?: "HTTP ${e.code}"
        is java.net.UnknownHostException -> "Host not found — check the server address"
        is java.net.ConnectException, is java.net.SocketTimeoutException -> "Cannot reach the server (VPN on?)"
        is javax.net.ssl.SSLException -> "TLS error: ${e.message ?: "certificate rejected"}"
        else -> e.message ?: e.javaClass.simpleName
    }

    // ---- auth ----
    init {
        viewModelScope.launch { graph.authLost.collect { n -> if (n > 0) api?.profile?.let { auth = AuthState.NeedsLogin(graph.profiles.get(it.id) ?: it, "Session expired — please sign in again") } } }
        start()
    }

    fun start() {
        val p = graph.profiles.active()
        if (p == null) { auth = if (graph.profiles.profiles.value.isEmpty()) AuthState.NoProfile else AuthState.NoProfile; return }
        signIn(p)
    }

    /** Connects to [profile]; reuses the remembered token or password when there is one, otherwise asks for credentials. */
    fun signIn(profile: Profile, username: String? = null, password: String? = null, remember: Boolean = profile.rememberMe) {
        auth = AuthState.Starting
        viewModelScope.launch {
            val client = graph.connect(profile)
            try {
                val secured = client.isSecurityEnabled()
                if (!secured) { onReady(); return@launch }
                if (username != null && password != null) {
                    client.login(username, password, remember); onReady(); return@launch
                }
                val stored = graph.profiles.get(profile.id) ?: profile
                if (client.token.isNotEmpty() && client.checkSession()) { onReady(); return@launch }
                val pw = stored.password
                if (stored.rememberMe && pw.isNotEmpty() && stored.username.isNotEmpty()) {
                    client.login(stored.username, pw, true); onReady(); return@launch
                }
                auth = AuthState.NeedsLogin(stored)
            } catch (e: Exception) {
                val stored = graph.profiles.get(profile.id) ?: profile
                auth = AuthState.NeedsLogin(stored, friendly(e))
            }
        }
    }

    private fun onReady() {
        auth = AuthState.Ready
        WatchService.sync(getApplication())
        if (graph.settings.state.value.checkUpdates) checkForUpdate(silent = true)
    }

    fun signOut() {
        graph.disconnect()
        WatchService.sync(getApplication())
        auth = graph.profiles.active()?.let { AuthState.NeedsLogin(graph.profiles.get(it.id) ?: it) } ?: AuthState.NoProfile
    }

    fun switchProfile(profile: Profile) { showProfiles = false; graph.release(); signIn(profile) }

    // ---- updates ----
    var update by mutableStateOf<AppUpdate?>(null); private set
    var updateProgress by mutableStateOf<Float?>(null); private set
    var updateChecking by mutableStateOf(false); private set

    fun checkForUpdate(silent: Boolean) {
        viewModelScope.launch {
            updateChecking = true
            try {
                update = updater.check()
                if (!silent) say(if (update == null) "You are up to date" else "Version ${update!!.version} is available")
            } catch (e: Exception) { if (!silent) say("Update check failed: ${friendly(e)}") }
            updateChecking = false
        }
    }

    fun installUpdate() {
        val u = update ?: return
        viewModelScope.launch {
            try {
                updateProgress = 0f
                val apk = updater.download(u) { updateProgress = it }
                updateProgress = null
                getApplication<Application>().startActivity(updater.installIntent(apk))
            } catch (e: Exception) { updateProgress = null; say("Update failed: ${friendly(e)}") }
        }
    }

    override fun onCleared() { player.stop() }
}
