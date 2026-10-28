package com.linuxkernel44.slskdclient.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.linuxkernel44.slskdclient.BuildConfig
import com.linuxkernel44.slskdclient.data.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

@Serializable data class GhAsset(val name: String = "", val browser_download_url: String = "", val size: Long = 0)
@Serializable data class GhRelease(val tag_name: String = "", val name: String? = null, val body: String? = null, val assets: List<GhAsset> = emptyList())

data class AppUpdate(val version: String, val notes: String, val apkUrl: String, val size: Long)

/** Checks the public GitHub repo for a newer release and installs its APK through the system installer. */
class Updater(private val context: Context) {
    private val http = OkHttpClient()

    suspend fun check(): AppUpdate? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("https://api.github.com/repos/LinuxKernel44/slskd-Client/releases/latest")
            .header("Accept", "application/vnd.github+json").build()
        val release = http.newCall(req).execute().use {
            if (!it.isSuccessful) throw IOException("GitHub answered HTTP ${it.code}")
            AppJson.decodeFromString<GhRelease>(it.body.string())
        }
        val version = release.tag_name.removePrefix("v")
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk") } ?: return@withContext null
        if (compareVersions(version, BuildConfig.VERSION_NAME.substringBefore('-')) <= 0) null
        else AppUpdate(version, release.body.orEmpty(), apk.browser_download_url, apk.size)
    }

    suspend fun download(update: AppUpdate, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "slskd-client-${update.version}.apk")
        http.newCall(Request.Builder().url(update.apkUrl).build()).execute().use { res ->
            if (!res.isSuccessful) throw IOException("Download failed (HTTP ${res.code})")
            val total = res.body.contentLength().takeIf { it > 0 } ?: update.size.coerceAtLeast(1)
            var got = 0L
            res.body.byteStream().use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(128 * 1024)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        o.write(buf, 0, n); got += n; onProgress((got.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        out
    }

    /** Intent for the system installer, or the settings screen that grants "install unknown apps". */
    fun installIntent(apk: File): Intent {
        if (!context.packageManager.canRequestPackageInstalls())
            return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    companion object {
        fun compareVersions(a: String, b: String): Int {
            val x = a.split('.').map { it.toIntOrNull() ?: 0 }
            val y = b.split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(x.size, y.size)) {
                val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
                if (c != 0) return c
            }
            return 0
        }
    }
}
