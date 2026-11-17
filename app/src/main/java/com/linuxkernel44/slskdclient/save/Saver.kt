package com.linuxkernel44.slskdclient.save

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.linuxkernel44.slskdclient.data.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** One file to copy from the companion server: [url] is a /fm/file or /fm/zip URL, [subDir] a folder under Downloads/Slskd Client. */
data class SaveItem(val url: String, val name: String, val subDir: String = "", val size: Long = 0)

data class SaveProgress(val name: String, val index: Int, val total: Int, val fraction: Float)

/** Copies files from the slskd-files companion server onto the phone (Downloads/Slskd Client on Android 10+). */
class Saver(private val context: Context, private val graph: Graph) {
    private val _progress = MutableStateFlow<SaveProgress?>(null)
    val progress: StateFlow<SaveProgress?> = _progress.asStateFlow()

    /** Returns the number of files saved. */
    suspend fun save(items: List<SaveItem>): Int = withContext(Dispatchers.IO) {
        val api = graph.client.value ?: throw IOException("Not connected")
        var saved = 0
        try {
            items.forEachIndexed { i, item ->
                _progress.value = SaveProgress(item.name, i + 1, items.size, 0f)
                api.callUrl(item.url, client = api.xferHttp).use { res ->
                    val total = res.body.contentLength().takeIf { it > 0 } ?: res.header("X-Zip-Estimate")?.toLongOrNull()?.takeIf { it > 0 } ?: item.size.coerceAtLeast(1)
                    openOutput(item.name, item.subDir).use { out ->
                        res.body.byteStream().use { input ->
                            val buf = ByteArray(128 * 1024); var got = 0L
                            while (true) {
                                val n = input.read(buf); if (n < 0) break
                                out.write(buf, 0, n); got += n
                                _progress.value = SaveProgress(item.name, i + 1, items.size, (got.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                saved++
            }
        } finally { _progress.value = null }
        saved
    }

    /** Downloads one file into the app cache (for sharing or opening with another app). */
    suspend fun toCache(url: String, name: String): File = withContext(Dispatchers.IO) {
        val api = graph.client.value ?: throw IOException("Not connected")
        val dir = File(context.cacheDir, "shared").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, name.replace('/', '_'))
        _progress.value = SaveProgress(name, 1, 1, 0f)
        try {
            api.callUrl(url, client = api.xferHttp).use { res ->
                val total = res.body.contentLength().coerceAtLeast(1)
                out.outputStream().use { sink ->
                    res.body.byteStream().use { input ->
                        val buf = ByteArray(128 * 1024); var got = 0L
                        while (true) {
                            val n = input.read(buf); if (n < 0) break
                            sink.write(buf, 0, n); got += n
                            _progress.value = SaveProgress(name, 1, 1, (got.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        } finally { _progress.value = null }
        out
    }

    private fun openOutput(name: String, subDir: String): java.io.OutputStream {
        val relative = listOf("Slskd Client", subDir).filter { it.isNotBlank() }.joinToString("/")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + relative)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("Cannot create $name")
            return context.contentResolver.openOutputStream(uri) ?: throw IOException("Cannot open $name")
        }
        // Android 8/9: app-specific external storage needs no permission.
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), relative).apply { mkdirs() }
        return File(dir, name).outputStream()
    }
}
