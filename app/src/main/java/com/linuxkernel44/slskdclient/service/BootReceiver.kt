package com.linuxkernel44.slskdclient.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the notification watcher after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching { WatchService.sync(context) }
    }
}
