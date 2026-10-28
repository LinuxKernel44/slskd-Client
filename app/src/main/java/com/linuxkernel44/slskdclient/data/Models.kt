package com.linuxkernel44.slskdclient.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

val AppJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = true
}

@Serializable data class SessionToken(val token: String, val name: String? = null, val expires: Long = 0)

@Serializable data class VersionInfo(
    val full: String? = null, val current: String? = null, val latest: String? = null,
    val isUpdateAvailable: Boolean = false, val isCanary: Boolean = false,
)

@Serializable data class ServerState(
    val address: String? = null, val ipEndPoint: String? = null, val state: String? = null,
    val isConnected: Boolean = false, val isConnecting: Boolean = false, val isLoggedIn: Boolean = false,
    val isLoggingIn: Boolean = false, val isTransitioning: Boolean = false,
)

@Serializable data class Privileges(val isPrivileged: Boolean = false, val privilegesRemaining: Long = 0)
@Serializable data class UserStatistics(
    val averageSpeed: Long = 0, val directoryCount: Long = 0, val fileCount: Long = 0, val uploadCount: Long = 0,
)
@Serializable data class SelfUser(val username: String? = null, val privileges: Privileges = Privileges(), val statistics: UserStatistics = UserStatistics())

@Serializable data class DistributedNetwork(
    val branchLevel: Int = 0, val branchRoot: String? = null, val canAcceptChildren: Boolean = false,
    val childLimit: Int = 0, val children: List<String> = emptyList(), val hasParent: Boolean = false,
    val isBranchRoot: Boolean = false, val parent: String? = null,
)

@Serializable data class SharesState(
    val scanPending: Boolean = false, val scanning: Boolean = false, val ready: Boolean = false, val faulted: Boolean = false,
    val cancelled: Boolean = false, val scanProgress: Double = 0.0, val directories: Long = 0, val files: Long = 0,
    val excludedDirectories: Long = 0, val filtered: Long = 0,
)

@Serializable data class AppState(
    val version: VersionInfo = VersionInfo(),
    val pendingReconnect: Boolean = false,
    val pendingRestart: Boolean = false,
    val server: ServerState = ServerState(),
    val user: SelfUser = SelfUser(),
    val distributedNetwork: DistributedNetwork = DistributedNetwork(),
    val shares: SharesState = SharesState(),
)

// ---- search ----

@Serializable data class SearchRequest(
    val id: String, val searchText: String,
    val fileLimit: Int? = null, val responseLimit: Int? = null, val searchTimeout: Int? = null,
    val filterResponses: Boolean? = null, val minimumResponseFileCount: Int? = null,
    val minimumPeerUploadSpeed: Int? = null, val maximumPeerQueueLength: Int? = null,
)

@Serializable data class RemoteFile(
    val filename: String = "", val size: Long = 0, val bitRate: Int? = null, val bitDepth: Int? = null,
    val sampleRate: Int? = null, val length: Int? = null, val isVariableBitRate: Boolean? = null,
    val extension: String? = null, val isLocked: Boolean = false,
)

@Serializable data class SearchResponse(
    val username: String = "", val token: Int = 0, val fileCount: Int = 0, val lockedFileCount: Int = 0,
    val files: List<RemoteFile> = emptyList(), val lockedFiles: List<RemoteFile> = emptyList(),
    val hasFreeUploadSlot: Boolean = false, val queueLength: Int = 0, val uploadSpeed: Long = 0,
)

@Serializable data class SearchStatus(
    val id: String = "", val searchText: String = "", val state: String = "", val isComplete: Boolean = false,
    val fileCount: Int = 0, val lockedFileCount: Int = 0, val responseCount: Int = 0,
    val startedAt: String? = null, val endedAt: String? = null, val token: Int = 0,
    val responses: List<SearchResponse> = emptyList(),
)

// ---- transfers ----

@Serializable data class Transfer(
    val id: String = "", val username: String = "", val direction: String = "", val filename: String = "",
    val size: Long = 0, val state: String = "", val requestedAt: String? = null, val enqueuedAt: String? = null,
    val startedAt: String? = null, val endedAt: String? = null, val bytesTransferred: Long = 0,
    val averageSpeed: Double = 0.0, val placeInQueue: Int? = null, val exception: String? = null,
    val attempts: Int = 0, val bytesRemaining: Long = 0, val percentComplete: Double = 0.0,
    val remainingTime: String? = null, val elapsedTime: String? = null,
) {
    val isCompleted get() = state.startsWith("Completed")
    val isSucceeded get() = state == "Completed, Succeeded"
    val isActive get() = state.contains("InProgress") || state.contains("Initializing")
    val isQueued get() = state.startsWith("Queued") || state.startsWith("Requested")
    val isRetryable get() = isCompleted && !isSucceeded
    val isCancellable get() = !isCompleted
    val shortName get() = filename.substringAfterLast('\\').substringAfterLast('/')
}

@Serializable data class DirectoryTransfers(val directory: String = "", val fileCount: Int = 0, val files: List<Transfer> = emptyList())
@Serializable data class UserTransfers(val username: String = "", val directories: List<DirectoryTransfers> = emptyList())

@Serializable data class EnqueueFile(val filename: String, val size: Long)
@Serializable data class EnqueueRequest(val username: String, val files: List<EnqueueFile>)

// ---- users / browse ----

@Serializable data class UserInfo(
    val description: String? = null, val hasPicture: Boolean = false, val picture: String? = null,
    val uploadSlots: Int = 0, val queueLength: Int = 0, val hasFreeUploadSlot: Boolean = false,
)
@Serializable data class UserStatus(val isPrivileged: Boolean = false, val presence: String = "")
@Serializable data class BrowseDirectory(val name: String = "", val fileCount: Int = 0, val files: List<RemoteFile> = emptyList())
@Serializable data class BrowseResponse(
    val directories: List<BrowseDirectory> = emptyList(), val lockedDirectories: List<BrowseDirectory> = emptyList(),
)
@Serializable data class BrowseStatus(val state: String = "", val percentComplete: Double = 0.0, val bytesTransferred: Long = 0, val size: Long = 0)
@Serializable data class DirectoryRequest(val directory: String)

// ---- chat / rooms ----

@Serializable data class PrivateMessage(
    val timestamp: String = "", val username: String = "", val message: String = "",
    val direction: String = "", val acknowledged: Boolean = false,
)
@Serializable data class Conversation(
    val username: String = "", val isActive: Boolean = false, val hasUnAcknowledgedMessages: Boolean = false,
    val unAcknowledgedMessageCount: Int = 0, val messages: List<PrivateMessage> = emptyList(),
)
@Serializable data class RoomInfo(
    val name: String = "", val userCount: Int = 0, val isPrivate: Boolean = false,
    val isOwned: Boolean = false, val isModerated: Boolean = false,
)
@Serializable data class RoomMessage(
    val timestamp: String = "", val username: String = "", val message: String = "",
    val roomName: String? = null, val self: Boolean = false,
)
@Serializable data class RoomUser(
    val username: String = "", val status: String = "", val countryCode: String? = null, val self: Boolean = false,
    val averageSpeed: Long = 0, val fileCount: Long = 0, val directoryCount: Long = 0,
)

// ---- files / shares / logs / events ----

@Serializable data class FsEntry(
    val name: String = "", val fullName: String = "", val length: Long = 0, val attributes: String = "",
    val createdAt: String? = null, val modifiedAt: String? = null,
)
@Serializable data class FsDirectory(
    val name: String = "", val fullName: String = "",
    val files: List<FsEntry> = emptyList(), val directories: List<FsEntry> = emptyList(),
)

@Serializable data class Share(
    val id: String = "", val alias: String = "", val isExcluded: Boolean = false,
    val localPath: String = "", val raw: String = "", val remotePath: String = "",
    val directories: Int? = null, val files: Int? = null,
)
@Serializable data class SharesResponse(val local: List<Share> = emptyList())
@Serializable data class ShareDirectory(val name: String = "", val fileCount: Int = 0, val files: List<RemoteFile> = emptyList())

@Serializable data class AppEvent(val timestamp: String = "", val type: String = "", val data: String = "", val id: String = "")

@Serializable data class LogEntry(
    val timestamp: String = "", val context: String? = null, val level: String = "", val message: String = "",
)

@Serializable data class YamlValidation(val raw: JsonElement? = null)
