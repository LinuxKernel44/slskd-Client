package com.linuxkernel44.slskdclient.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class ApiException(val code: Int, message: String) : IOException(message)

/** Builds the OkHttp client for a profile; trusts every certificate only when the profile explicitly allows it. */
fun buildHttpClient(allowSelfSigned: Boolean, readTimeoutSec: Long = 60): OkHttpClient {
    val b = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
    if (allowSelfSigned) {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trustAll), SecureRandom()) }
        b.sslSocketFactory(ctx.socketFactory, trustAll).hostnameVerifier { _, _ -> true }
    }
    return b.build()
}

private val JSON_MEDIA = "application/json".toMediaType()

/**
 * Authenticated slskd REST client for one profile. A 401 triggers one silent re-login when the password was saved
 * ("remember me"), otherwise [onAuthLost] fires so the UI can ask for the password again.
 */
class ApiClient(
    val profile: Profile,
    private val store: ProfileStore,
    var onAuthLost: () -> Unit = {},
) {
    val http: OkHttpClient = buildHttpClient(profile.allowSelfSigned)
    /** Browsing a user's whole share list can take minutes; this client waits for it. */
    private val longHttp: OkHttpClient by lazy { http.newBuilder().readTimeout(5, TimeUnit.MINUTES).build() }
    private val base = profile.baseUrl
    private val api = "$base/api/v0"

    @Volatile var token: String = profile.token
    @Volatile var securityEnabled: Boolean = true

    val hasToken get() = token.isNotEmpty() || !securityEnabled

    private fun Request.Builder.auth(): Request.Builder = apply { if (token.isNotEmpty()) header("Authorization", "Bearer $token") }

    /** Raw call; [path] is relative to /api/v0 (leading slash). */
    suspend fun call(method: String, path: String, body: String? = null, json: Boolean = true, retry: Boolean = true, long: Boolean = false): Response = withContext(Dispatchers.IO) {
        val rb = when {
            body != null -> body.toRequestBody(if (json) JSON_MEDIA else "text/plain".toMediaType())
            method == "POST" || method == "PUT" -> "".toRequestBody(null)
            else -> null
        }
        val res = (if (long) longHttp else http).newCall(Request.Builder().url(api + path).auth().method(method, rb).build()).execute()
        if (res.code == 401 && retry && !path.startsWith("/session")) {
            res.close()
            if (relogin()) return@withContext call(method, path, body, json, false, long)
            onAuthLost()
            throw ApiException(401, "Session expired")
        }
        if (!res.isSuccessful) {
            val text = runCatching { res.body.string() }.getOrDefault("")
            res.close()
            throw ApiException(res.code, errorText(res.code, text))
        }
        res
    }

    private fun errorText(code: Int, body: String): String {
        val msg = runCatching { AppJson.parseToJsonElement(body).jsonObject.let { it["detail"] ?: it["title"] ?: it["message"] } }.getOrNull()
            ?.jsonPrimitive?.content
        return msg?.takeIf { it.isNotBlank() } ?: body.take(200).ifBlank { "HTTP $code" }
    }

    private suspend fun relogin(): Boolean {
        val p = store.get(profile.id) ?: profile
        val pw = p.password
        if (!p.rememberMe || pw.isEmpty() || p.username.isEmpty()) return false
        return runCatching { login(p.username, pw, true); true }.getOrDefault(false)
    }

    suspend fun text(method: String, path: String, body: String? = null, json: Boolean = true): String =
        withContext(Dispatchers.IO) { call(method, path, body, json).use { it.body.string() } }

    suspend inline fun <reified T> get(path: String): T { val t = text("GET", path); return withContext(Dispatchers.Default) { AppJson.decodeFromString<T>(t) } }
    suspend inline fun <reified T> post(path: String, body: String? = null): T { val t = text("POST", path, body); return withContext(Dispatchers.Default) { AppJson.decodeFromString<T>(t) } }

    // ---- session ----

    suspend fun isSecurityEnabled(): Boolean = withContext(Dispatchers.IO) {
        val res = http.newCall(Request.Builder().url("$api/session/enabled").build()).execute()
        res.use {
            if (!it.isSuccessful) throw ApiException(it.code, "HTTP ${it.code}")
            it.body.string().trim().equals("true", true)
        }.also { securityEnabled = it }
    }

    /** Logs in and stores the token (and the password when [remember] is set). */
    suspend fun login(username: String, password: String, remember: Boolean) {
        val payload = AppJson.encodeToString(mapOf("username" to username, "password" to password))
        val res = withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url("$api/session").post(payload.toRequestBody(JSON_MEDIA)).build()).execute()
        }
        val txt = res.use {
            if (it.code == 401) throw ApiException(401, "Wrong username or password")
            if (!it.isSuccessful) throw ApiException(it.code, "Login failed (HTTP ${it.code})")
            it.body.string()
        }
        val session = AppJson.decodeFromString<SessionToken>(txt)
        token = session.token
        val current = store.get(profile.id) ?: profile
        store.save(
            current.copy(
                username = username,
                rememberMe = remember,
                encPassword = if (remember) Secrets.encrypt(password) else "",
                encToken = if (remember) Secrets.encrypt(session.token) else "",
            ),
        )
    }

    suspend fun checkSession(): Boolean = runCatching { call("GET", "/session").discard(); true }.getOrElse { if (it is ApiException && it.code == 401) false else throw it }

    fun logout(keepUsername: Boolean = true) {
        token = ""
        val current = store.get(profile.id) ?: return
        store.save(current.copy(encToken = "", encPassword = "", username = if (keepUsername) current.username else ""))
    }

    // ---- application / server ----

    suspend fun appState(): AppState = get("/application")
    suspend fun latestVersion(force: Boolean = false): VersionInfo = get("/application/version/latest?forceCheck=$force")
    suspend fun restartApp() { call("PUT", "/application").discard() }
    suspend fun shutdownApp() { call("DELETE", "/application").discard() }
    suspend fun serverState(): ServerState = get("/server")
    suspend fun connectServer() { call("PUT", "/server").discard() }
    suspend fun disconnectServer(message: String = "client disconnected from Slskd Client") {
        call("DELETE", "/server", AppJson.encodeToString(message)).discard()
    }

    // ---- searches ----

    suspend fun searches(): List<SearchStatus> = get("/searches")
    suspend fun createSearch(request: SearchRequest) { call("POST", "/searches", AppJson.encodeToString(request)).discard() }
    suspend fun searchStatus(id: String, withResponses: Boolean = false): SearchStatus = get("/searches/$id?includeResponses=$withResponses")
    suspend fun searchResponses(id: String): List<SearchResponse> = get("/searches/$id/responses")
    suspend fun stopSearch(id: String) { call("PUT", "/searches/$id").discard() }
    suspend fun deleteSearch(id: String) { call("DELETE", "/searches/$id").discard() }

    // ---- transfers ----

    suspend fun transfers(direction: String): List<UserTransfers> = get("/transfers/${direction}s")
    suspend fun enqueue(username: String, files: List<EnqueueFile>) {
        call("POST", "/transfers/downloads/${enc(username)}", AppJson.encodeToString(files)).discard()
    }
    suspend fun cancelTransfer(direction: String, username: String, id: String, remove: Boolean) {
        call("DELETE", "/transfers/${direction}s/${enc(username)}/${enc(id)}?remove=$remove").discard()
    }
    suspend fun clearCompleted(direction: String) { call("DELETE", "/transfers/${direction}s/all/completed").discard() }
    suspend fun queuePosition(username: String, id: String): Int? =
        text("GET", "/transfers/downloads/${enc(username)}/${enc(id)}/position").trim().toIntOrNull()

    // ---- users ----

    suspend fun userInfo(username: String): UserInfo = get("/users/${enc(username)}/info")
    suspend fun userStatus(username: String): UserStatus = get("/users/${enc(username)}/status")
    suspend fun userEndpoint(username: String): String = text("GET", "/users/${enc(username)}/endpoint").trim('"')
    suspend fun browseUser(username: String): BrowseResponse =
        withContext(Dispatchers.IO) { call("GET", "/users/${enc(username)}/browse", long = true).use { AppJson.decodeFromString<BrowseResponse>(it.body.string()) } }
    suspend fun browseStatus(username: String): BrowseStatus = get("/users/${enc(username)}/browse/status")
    suspend fun userDirectory(username: String, directory: String): List<BrowseDirectory> =
        text("POST", "/users/${enc(username)}/directory", AppJson.encodeToString(DirectoryRequest(directory))).let { AppJson.decodeFromString(it) }

    // ---- chat / rooms ----

    suspend fun conversations(): List<Conversation> = get("/conversations")
    suspend fun conversation(username: String): Conversation = get("/conversations/${enc(username)}?includeMessages=true")
    suspend fun ackConversation(username: String) { call("PUT", "/conversations/${enc(username)}").discard() }
    suspend fun sendMessage(username: String, message: String) { call("POST", "/conversations/${enc(username)}", AppJson.encodeToString(message)).discard() }
    suspend fun deleteConversation(username: String) { call("DELETE", "/conversations/${enc(username)}").discard() }

    suspend fun roomsAvailable(): List<RoomInfo> = get("/rooms/available")
    suspend fun roomsJoined(): List<String> = get("/rooms/joined")
    suspend fun roomMessages(room: String): List<RoomMessage> = get("/rooms/joined/${enc(room)}/messages")
    suspend fun roomUsers(room: String): List<RoomUser> = get("/rooms/joined/${enc(room)}/users")
    suspend fun joinRoom(room: String) { call("POST", "/rooms/joined", AppJson.encodeToString(room)).discard() }
    suspend fun leaveRoom(room: String) { call("DELETE", "/rooms/joined/${enc(room)}").discard() }
    suspend fun sendRoomMessage(room: String, message: String) { call("POST", "/rooms/joined/${enc(room)}/messages", AppJson.encodeToString(message)).discard() }

    // ---- files / shares ----

    suspend fun listFiles(root: String, subdirectory: String = ""): FsDirectory =
        get("/files/$root/directories/${b64(subdirectory)}")
    suspend fun deleteFile(root: String, path: String) { call("DELETE", "/files/$root/files/${b64(path)}").discard() }
    suspend fun deleteDirectory(root: String, path: String) { call("DELETE", "/files/$root/directories/${b64(path)}").discard() }

    suspend fun shares(): SharesResponse = get("/shares")
    suspend fun shareContents(id: String): List<ShareDirectory> = get("/shares/${enc(id)}/contents")
    suspend fun rescanShares() { call("PUT", "/shares").discard() }
    suspend fun cancelShareScan() { call("DELETE", "/shares").discard() }

    // ---- options / logs / events ----

    suspend fun optionsYaml(): String = text("GET", "/options/yaml").let { runCatching { AppJson.decodeFromString<String>(it) }.getOrDefault(it) }
    suspend fun optionsYamlLocation(): String = text("GET", "/options/yaml/location").trim('"')
    suspend fun optionsDebug(): String = text("GET", "/options/debug")
    /** Returns null when valid, otherwise the validation message. */
    suspend fun validateYaml(yaml: String): String? = try {
        text("POST", "/options/yaml/validate", AppJson.encodeToString(yaml)).trim('"').takeIf { it.isNotBlank() && it != "null" }
    } catch (e: ApiException) { if (e.code == 400) e.message else throw e }
    suspend fun updateYaml(yaml: String) { call("PUT", "/options/yaml", AppJson.encodeToString(yaml)).discard() }

    suspend fun logFiles(): List<FsEntry> = get("/logs/files")
    suspend fun logFile(name: String): String = text("GET", "/logs/files/${enc(name)}")

    suspend fun events(offset: Int, limit: Int): Pair<List<AppEvent>, Int> {
        return withContext(Dispatchers.IO) {
            call("GET", "/events?offset=$offset&limit=$limit").use {
                val total = it.header("X-Total-Count")?.toIntOrNull() ?: 0
                AppJson.decodeFromString<List<AppEvent>>(it.body.string()) to total
            }
        }
    }

    // ---- companion file server ----

    /** True when the slskd-files companion answers on the profile's files URL. */
    suspend fun filesServerAvailable(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(profile.filesBaseUrl + "/health").build()).execute().use { it.isSuccessful && it.body.string().contains("slskd-files") }
        }.getOrDefault(false)
    }

    fun fileUrl(relativePath: String): String = profile.filesBaseUrl + "/files/downloads/" + b64Url(relativePath)

    companion object {
        fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        fun b64(s: String): String = android.util.Base64.encodeToString(s.toByteArray(), android.util.Base64.NO_WRAP).let { enc(it) }
        fun b64Url(s: String): String = b64(s)
    }
}

fun JsonElement.asArrayOrNull(): JsonArray? = this as? JsonArray

private suspend fun Response.discard() = withContext(Dispatchers.IO) { close() }
