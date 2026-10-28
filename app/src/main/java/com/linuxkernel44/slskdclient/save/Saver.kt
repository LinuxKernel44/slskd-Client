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
import okhttp3.Request
import java.io.File
import java.io.IOException

data class SaveProgress(val name: String, val index: Int, val total: Int, val fraction: Float)

/** Copies files from the slskd-files companion server onto the phone (Downloads/Slskd Client on Android 10+). */
class Saver(private val context: Context, private val graph: Graph) {
    private val _progress = MutableStateFlow<SaveProgress?>(null)
    val progress: StateFlow<SaveProgress?> = _progress.asStateFlow()

    /** [files] are (path relative to the server's downloads dir, size). Returns the number of files saved. */
    suspend fun save(files: List<Pair<String, Long>>): Int = withContext(Dispatchers.IO) {
        val api = graph.client.value ?: throw IOException("Not connected")
        var saved = 0
        try {
            files.forEachIndexed { i, (path, size) ->
                val name = path.substringAfterLast('/')
                val subDir = path.substringBeforeLast('/', "")
                _progress.value = SaveProgress(name, i + 1, files.size, 0f)
                val req = Request.Builder().url(api.fileUrl(path)).apply {
                    if (api.token.isNotEmpty()) header("Authorization", "Bearer ${api.token}")
                }.build()
                api.http.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) throw IOException("${res.code} while downloading $name")
                    val total = res.body.contentLength().takeIf { it > 0 } ?: size.coerceAtLeast(1)
                    openOutput(name, subDir).use { out ->
                        res.body.byteStream().use { input ->
                            val buf = ByteArray(128 * 1024); var got = 0L
                            while (true) {
                                val n = input.read(buf); if (n < 0) break
                                out.write(buf, 0, n); got += n
                                _progress.value = SaveProgress(name, i + 1, files.size, (got.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                saved++
            }
        } finally { _progress.value = null }
        saved
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
