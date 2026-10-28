package com.linuxkernel44.slskdclient.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.linuxkernel44.slskdclient.data.ApiClient
import com.linuxkernel44.slskdclient.graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that polls the active slskd server and raises notifications for finished downloads and new
 * private messages while the app is closed. It is started/stopped from the settings and after boot.
 */
class WatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifier.ensureChannels(this)
        ServiceCompat.startForeground(this, 1, Notifier.foreground(this, "Watching your slskd server"), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        if (job?.isActive != true) job = scope.launch { watch() }
        return START_STICKY
    }

    private suspend fun watch() {
        val doneIds = HashSet<String>()
        val msgCounts = HashMap<String, Int>()
        var seeded = false
        var failures = 0
        while (scope.isActive) {
            val settings = graph.settings.state.value
            val api: ApiClient? = graph.ensureClient()
            if (api == null || !api.hasToken || !settings.backgroundService) {
                if (!settings.backgroundService) { stopSelf(); return }
                delay(15_000); continue
            }
            try {
                var newlyDone = 0
                var last = ""
                for (group in api.transfers("download")) for (dir in group.directories) for (f in dir.files) {
                    if (f.isSucceeded && doneIds.add(f.id) && seeded) { newlyDone++; last = f.shortName }
                }
                var messages = 0
                for (c in api.conversations()) {
                    val prev = msgCounts[c.username]
                    if (c.hasUnAcknowledgedMessages && c.unAcknowledgedMessageCount > (prev ?: 0) && seeded && settings.notifyMessages) {
                        val text = runCatching { api.conversation(c.username).messages.lastOrNull { it.direction != "Out" }?.message }.getOrNull() ?: "New message"
                        Notifier.message(this, c.username, text); messages++
                    }
                    msgCounts[c.username] = if (c.hasUnAcknowledgedMessages) c.unAcknowledgedMessageCount else 0
                }
                if (newlyDone > 0 && settings.notifyDownloads) Notifier.downloadDone(this, newlyDone, last)
                seeded = true
                failures = 0
            } catch (_: Exception) {
                failures++
            }
            delay(if (failures > 3) 60_000 else 8_000)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun sync(ctx: Context) {
            val s = ctx.graph.settings.state.value
            val has = ctx.graph.profiles.active() != null
            if (s.backgroundService && has && (s.notifyDownloads || s.notifyMessages))
                ContextCompat.startForegroundService(ctx, Intent(ctx, WatchService::class.java))
            else ctx.stopService(Intent(ctx, WatchService::class.java))
        }
    }
}
