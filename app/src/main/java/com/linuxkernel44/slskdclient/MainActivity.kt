package com.linuxkernel44.slskdclient

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.linuxkernel44.slskdclient.service.Notifier
import com.linuxkernel44.slskdclient.ui.AppVm
import com.linuxkernel44.slskdclient.ui.RootApp
import com.linuxkernel44.slskdclient.ui.Tabs

class MainActivity : ComponentActivity() {
    private val vm: AppVm by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        Notifier.ensureChannels(this)
        handle(intent)
        setContent { RootApp(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { vm.searchFor(it.trim().take(200)) }
            else -> intent.getStringExtra(Notifier.EXTRA_OPEN)?.let { target ->
                when {
                    target == "transfers" -> vm.tab = Tabs.TRANSFERS
                    target.startsWith("chat:") -> vm.openChat(target.removePrefix("chat:"))
                }
            }
        }
    }
}
