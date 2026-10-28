package com.linuxkernel44.slskdclient.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.linuxkernel44.slskdclient.MainActivity
import com.linuxkernel44.slskdclient.R

object Notifier {
    const val CH_SERVICE = "service"
    const val CH_DOWNLOADS = "downloads"
    const val CH_MESSAGES = "messages"
    const val EXTRA_OPEN = "open"            // "transfers" | "chat:<username>"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_SERVICE, "Background connection", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) })
        nm.createNotificationChannel(NotificationChannel(CH_DOWNLOADS, "Downloads", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_MESSAGES, "Private messages", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun open(ctx: Context, target: String, code: Int): PendingIntent =
        PendingIntent.getActivity(
            ctx, code, Intent(ctx, MainActivity::class.java).putExtra(EXTRA_OPEN, target).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun foreground(ctx: Context, text: String) = NotificationCompat.Builder(ctx, CH_SERVICE)
        .setSmallIcon(R.drawable.ic_notification).setContentTitle("Slskd Client").setContentText(text)
        .setOngoing(true).setSilent(true).setPriority(NotificationCompat.PRIORITY_MIN).setContentIntent(open(ctx, "transfers", 1)).build()

    private fun canPost(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun downloadDone(ctx: Context, count: Int, lastName: String) {
        if (!canPost(ctx)) return
        val title = if (count == 1) "Download complete" else "$count downloads complete"
        val n = NotificationCompat.Builder(ctx, CH_DOWNLOADS).setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title).setContentText(lastName).setAutoCancel(true).setContentIntent(open(ctx, "transfers", 2)).build()
        ctx.getSystemService(NotificationManager::class.java).notify(2001, n)
    }

    fun message(ctx: Context, username: String, text: String) {
        if (!canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_MESSAGES).setSmallIcon(R.drawable.ic_chat)
            .setContentTitle(username).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true).setContentIntent(open(ctx, "chat:$username", username.hashCode())).build()
        ctx.getSystemService(NotificationManager::class.java).notify(3000 + (username.hashCode() and 0xfff), n)
    }
}
