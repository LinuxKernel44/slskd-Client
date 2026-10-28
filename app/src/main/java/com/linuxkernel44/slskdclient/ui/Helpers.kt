package com.linuxkernel44.slskdclient.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Result holder for [rememberPoll]: last good value, last error, and a manual refresh hook. */
@Stable
class Poll<T> {
    var value by mutableStateOf<T?>(null)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set
    var loading by mutableStateOf(true)
        internal set
    internal var tick by mutableIntStateOf(0)
    fun refresh() { tick++ }
}

/**
 * Loads [load] immediately and then every [intervalMs] (0 = once) while composed and [enabled]; reloads when [key]
 * changes or [Poll.refresh] is called. Errors keep the previous value so lists do not flash empty on a bad poll.
 */
@Composable
fun <T> rememberPoll(key: Any?, intervalMs: Long = 0, enabled: Boolean = true, load: suspend () -> T): Poll<T> {
    val poll = remember(key) { Poll<T>() }
    val currentLoad by rememberUpdatedState(load)
    LaunchedEffect(key, enabled, poll.tick) {
        if (!enabled) return@LaunchedEffect
        while (true) {
            try {
                poll.value = currentLoad(); poll.error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                poll.error = e.message ?: e.javaClass.simpleName
            }
            poll.loading = false
            if (intervalMs <= 0) break
            delay(intervalMs)
        }
    }
    return poll
}

fun Context.copyToClipboard(label: String, text: String) {
    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
}

fun Context.clipboardText(): String? =
    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()

fun fmtSpeed(bytesPerSec: Number?): String = if (bytesPerSec == null || bytesPerSec.toDouble() <= 0) "–" else fmtBytes(bytesPerSec.toLong()) + "/s"

/** "2026-10-28T17:49:50.123Z" → "17:49:50" (local time when it parses). */
fun fmtTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching {
        val instant = if (iso.endsWith("Z") || iso.contains('+')) java.time.OffsetDateTime.parse(iso).toInstant() else java.time.LocalDateTime.parse(iso).toInstant(java.time.ZoneOffset.UTC)
        java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
    }.getOrDefault(iso.takeLast(8))
}

fun fmtDateTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching {
        val instant = if (iso.endsWith("Z") || iso.contains('+')) java.time.OffsetDateTime.parse(iso).toInstant() else java.time.LocalDateTime.parse(iso).toInstant(java.time.ZoneOffset.UTC)
        java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm"))
    }.getOrDefault(iso)
}

/** Last path segment of a Soulseek (backslash) or POSIX path. */
fun baseName(path: String) = path.substringAfterLast('\\').substringAfterLast('/')
fun dirName(path: String): String {
    val i = maxOf(path.lastIndexOf('\\'), path.lastIndexOf('/'))
    return if (i < 0) "" else path.substring(0, i)
}

val AUDIO_EXT = setOf("mp3", "flac", "ogg", "opus", "m4a", "aac", "wav", "wma", "ape", "aiff", "alac")
fun isAudio(name: String) = name.substringAfterLast('.', "").lowercase() in AUDIO_EXT
