package com.linuxkernel44.slskdclient.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.BufferedSink
import okio.Source

@Serializable data class FmRoot(val id: String, val name: String, val free: Long = 0, val total: Long = 0)
@Serializable data class FmRoots(val version: Int = 1, val roots: List<FmRoot> = emptyList(), val trashDays: Int = 30, val navidrome: Boolean = false)
@Serializable data class FmEntry(val name: String, val dir: Boolean = false, val size: Long = 0, val mtime: Long = 0, val link: Boolean = false, val path: String? = null)
@Serializable data class FmList(val entries: List<FmEntry> = emptyList(), val truncated: Boolean = false)
@Serializable data class FmAudio(
    val codec: String? = null, val duration: Double? = null, val bitrate: Long? = null, val sampleRate: Int? = null, val channels: Int? = null,
    val bitDepth: Int? = null, val title: String? = null, val artist: String? = null, val album: String? = null, val albumArtist: String? = null,
    val date: String? = null, val track: String? = null, val disc: String? = null, val genre: String? = null,
)
@Serializable data class FmInfo(
    val dir: Boolean = false, val size: Long = 0, val mtime: Long = 0, val mime: String? = null,
    val files: Long = 0, val dirs: Long = 0, val truncated: Boolean = false, val audio: FmAudio? = null,
)
@Serializable data class FmTrashItem(val id: String, val root: String, val path: String, val name: String, val dir: Boolean = false, val size: Long = 0, val deleted: Long = 0)
@Serializable data class FmTrash(val items: List<FmTrashItem> = emptyList(), val days: Int = 30)
@Serializable data class FmSharedSize(val bytes: Long = 0, val files: Long = 0)
@Serializable data class FmError(val path: String = "", val error: String = "")
@Serializable data class FmResult(val done: Int = 0, val skipped: Int = 0, val errors: List<FmError> = emptyList())

/** A file or folder on the server: [root] id plus the path relative to it ("" is the root itself). */
data class FmRef(val root: String, val path: String) {
    val name get() = path.substringAfterLast('/')
}

/** What a conflicting name does on copy, move, upload and restore. */
enum class Conflict(val wire: String) { Rename("rename"), Replace("replace"), Skip("skip") }

/** Cut or copy waiting for a destination. */
data class FmClip(val move: Boolean, val items: List<FmRef>)

/** Browsing position and clipboard of the file manager; lives in the view model so it survives tab switches. */
class FmState {
    var root by mutableStateOf<String?>(null)
    var path by mutableStateOf(emptyList<String>())
    var clip by mutableStateOf<FmClip?>(null)
    var showTrash by mutableStateOf(false)
    /** 0 name, 1 date, 2 size, 3 type */
    var sort by mutableStateOf(0)
    var descending by mutableStateOf(false)

    fun open(root: String?, path: List<String> = emptyList()) { this.root = root; this.path = path }
    val rel get() = path.joinToString("/")
}

/** Client for the slskd-files companion's /fm API (browse, transfer, trash, upload, rescan). */
class FileManager(private val api: ApiClient) {
    private val base get() = api.profile.filesBaseUrl + "/fm/"
    private val jsonType = "application/json".toMediaType()

    private fun query(vararg pairs: Pair<String, String>) = pairs.joinToString("&") { "${it.first}=${ApiClient.enc(it.second)}" }

    private suspend fun <T> get(route: String, strategy: KSerializer<T>, vararg pairs: Pair<String, String>): T {
        val text = withContext(Dispatchers.IO) { api.callUrl(base + route + "?" + query(*pairs)).use { it.body.string() } }
        return withContext(Dispatchers.Default) { AppJson.decodeFromString(strategy, text) }
    }

    private suspend fun <T> post(route: String, body: String, strategy: KSerializer<T>): T {
        val text = withContext(Dispatchers.IO) { api.callUrl(base + route, "POST", body.toRequestBody(jsonType)).use { it.body.string() } }
        return withContext(Dispatchers.Default) { AppJson.decodeFromString(strategy, text) }
    }

    private fun refs(items: List<FmRef>): JsonArray = buildJsonArray {
        items.forEach { add(buildJsonObject { put("root", it.root); put("path", it.path) }) }
    }

    suspend fun roots(): FmRoots = get("roots", FmRoots.serializer())
    suspend fun list(root: String, path: String): List<FmEntry> = get("list", FmList.serializer(), "root" to root, "path" to path).entries
    suspend fun search(root: String, path: String, text: String): FmList = get("search", FmList.serializer(), "root" to root, "path" to path, "q" to text)
    suspend fun info(ref: FmRef): FmInfo = get("info", FmInfo.serializer(), "root" to ref.root, "path" to ref.path)
    suspend fun sharedSize(): FmSharedSize = get("shared-size", FmSharedSize.serializer())
    suspend fun trash(): FmTrash = get("trash", FmTrash.serializer())

    /** Streaming / download URL of one file; the app adds the bearer token itself. */
    fun fileUrl(ref: FmRef): String = base + "file?" + query("root" to ref.root, "path" to ref.path)
    fun zipUrl(items: List<FmRef>): String = base + "zip?" + items.joinToString("&") { query("root" to it.root, "path" to it.path) }

    suspend fun mkdir(parent: FmRef, name: String) {
        post("mkdir", buildJsonObject { put("root", parent.root); put("path", parent.path); put("name", name) }.toString(), JsonElement.serializer())
    }

    suspend fun rename(ref: FmRef, name: String) {
        post("rename", buildJsonObject { put("root", ref.root); put("path", ref.path); put("name", name) }.toString(), JsonElement.serializer())
    }

    suspend fun transfer(move: Boolean, items: List<FmRef>, dest: FmRef, conflict: Conflict = Conflict.Rename): FmResult =
        post("transfer", buildJsonObject {
            put("mode", if (move) "move" else "copy")
            put("items", refs(items))
            put("dest", buildJsonObject { put("root", dest.root); put("path", dest.path) })
            put("onConflict", conflict.wire)
        }.toString(), FmResult.serializer())

    suspend fun delete(items: List<FmRef>): FmResult = post("delete", buildJsonObject { put("items", refs(items)) }.toString(), FmResult.serializer())

    suspend fun restore(ids: List<String>): FmResult =
        post("trash/restore", buildJsonObject { put("ids", JsonArray(ids.map { JsonPrimitive(it) })) }.toString(), FmResult.serializer())

    suspend fun purge(ids: List<String>? = null) {
        post("trash/purge", buildJsonObject { if (ids == null) put("all", true) else put("ids", JsonArray(ids.map { JsonPrimitive(it) })) }.toString(), JsonElement.serializer())
    }

    suspend fun rescan(slskd: Boolean, navidrome: Boolean) {
        post("rescan", buildJsonObject { put("slskd", slskd); put("navidrome", navidrome) }.toString(), JsonElement.serializer())
    }

    /** Streams [source] to [dest]/[name]; [onProgress] gets the bytes sent so far. Returns the name the server stored. */
    suspend fun upload(dest: FmRef, name: String, size: Long, conflict: Conflict, source: () -> Source, onProgress: (Long) -> Unit): String {
        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = size
            override fun writeTo(sink: BufferedSink) {
                val buffer = Buffer(); var sent = 0L
                source().use { input ->
                    while (true) {
                        val n = input.read(buffer, 64 * 1024L)
                        if (n < 0) break
                        sink.write(buffer, n); sent += n; onProgress(sent)
                    }
                }
            }
        }
        val url = base + "upload?" + query("root" to dest.root, "path" to dest.path, "name" to name, "onConflict" to conflict.wire)
        val text = withContext(Dispatchers.IO) { api.callUrl(url, "PUT", body, api.xferHttp).use { it.body.string() } }
        return AppJson.parseToJsonElement(text).let { (it as JsonObject)["name"]?.let { n -> (n as JsonPrimitive).content } } ?: name
    }
}
