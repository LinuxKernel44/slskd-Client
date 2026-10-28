package com.linuxkernel44.slskdclient.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

data class HubEvent(val target: String, val args: JsonArray)

private const val RS = "\u001e"

/**
 * Minimal SignalR (JSON protocol, WebSocket transport) client: enough to receive server-to-client events from
 * slskd's `/hub/logs`, `/hub/application` and `/hub/search`. The flow completes when the socket closes, so
 * callers retry in a loop; cancelling the collector closes the socket.
 */
fun ApiClient.hubEvents(hub: String): Flow<HubEvent> = callbackFlow {
    val base = profile.baseUrl
    val negotiate = runCatching {
        val req = Request.Builder().url("$base/hub/$hub/negotiate?negotiateVersion=1")
            .post("".toRequestBody(null)).apply { if (token.isNotEmpty()) header("Authorization", "Bearer $token") }.build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw ApiException(res.code, "negotiate failed (${res.code})")
            AppJson.parseToJsonElement(res.body.string()).jsonObject
        }
    }.getOrElse { close(it); return@callbackFlow }
    val id = (negotiate["connectionToken"] ?: negotiate["connectionId"])?.jsonPrimitive?.content ?: ""
    val wsBase = base.replaceFirst("http", "ws")
    val query = buildString { append("id=").append(ApiClient.enc(id)); if (token.isNotEmpty()) append("&access_token=").append(ApiClient.enc(token)) }
    val request = Request.Builder().url("$wsBase/hub/$hub?$query").build()
    var buffer = ""
    val socket = http.newWebSocket(request, object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send("""{"protocol":"json","version":1}$RS""")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            buffer += text
            while (true) {
                val end = buffer.indexOf(RS)
                if (end < 0) break
                val frame = buffer.substring(0, end)
                buffer = buffer.substring(end + 1)
                if (frame.isEmpty()) continue
                val obj = runCatching { AppJson.parseToJsonElement(frame).jsonObject }.getOrNull() ?: continue
                when (obj["type"]?.jsonPrimitive?.int) {
                    1 -> trySend(HubEvent(obj["target"]!!.jsonPrimitive.content, obj["arguments"]?.jsonArray ?: JsonArray(emptyList())))
                    6 -> webSocket.send("""{"type":6}$RS""")
                    7 -> { webSocket.close(1000, null); close() }
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { close() }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { close(t) }
    })
    awaitClose { socket.close(1000, null) }
}.flowOn(Dispatchers.IO)

