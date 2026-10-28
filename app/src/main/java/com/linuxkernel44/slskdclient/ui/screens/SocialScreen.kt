package com.linuxkernel44.slskdclient.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linuxkernel44.slskdclient.R
import com.linuxkernel44.slskdclient.data.Conversation
import com.linuxkernel44.slskdclient.data.UserInfo
import com.linuxkernel44.slskdclient.ui.AppVm
import com.linuxkernel44.slskdclient.ui.components.Badge
import com.linuxkernel44.slskdclient.ui.components.CapsuleShape
import com.linuxkernel44.slskdclient.ui.components.ChipTabs
import com.linuxkernel44.slskdclient.ui.components.ConfirmSheet
import com.linuxkernel44.slskdclient.ui.components.Dot
import com.linuxkernel44.slskdclient.ui.components.Empty
import com.linuxkernel44.slskdclient.ui.components.ErrorBanner
import com.linuxkernel44.slskdclient.ui.components.GIcon
import com.linuxkernel44.slskdclient.ui.components.GlassButton
import com.linuxkernel44.slskdclient.ui.components.GlassCard
import com.linuxkernel44.slskdclient.ui.components.GlassField
import com.linuxkernel44.slskdclient.ui.components.GlassIconButton
import com.linuxkernel44.slskdclient.ui.components.ItemCard
import com.linuxkernel44.slskdclient.ui.components.KeyValue
import com.linuxkernel44.slskdclient.ui.components.Loading
import com.linuxkernel44.slskdclient.ui.components.MenuItem
import com.linuxkernel44.slskdclient.ui.components.MenuSheet
import com.linuxkernel44.slskdclient.ui.components.Muted
import com.linuxkernel44.slskdclient.ui.components.PromptSheet
import com.linuxkernel44.slskdclient.ui.components.Txt
import com.linuxkernel44.slskdclient.ui.components.glass
import com.linuxkernel44.slskdclient.ui.components.GlassLevel
import com.linuxkernel44.slskdclient.ui.components.pagePadding
import com.linuxkernel44.slskdclient.ui.fmtBytes
import com.linuxkernel44.slskdclient.ui.fmtSpeed
import com.linuxkernel44.slskdclient.ui.fmtTime
import com.linuxkernel44.slskdclient.ui.rememberPoll
import com.linuxkernel44.slskdclient.ui.theme.LocalColors

@Composable
fun SocialScreen(vm: AppVm) {
    when (vm.socialTab) {
        0 -> MessagesTab(vm)
        1 -> RoomsTab(vm)
        else -> UsersTab(vm)
    }
}

@Composable
private fun SocialHeader(vm: AppVm) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Txt("Social", size = 28.sp, weight = FontWeight.Bold)
        ChipTabs(listOf("Messages", "Rooms", "Users"), vm.socialTab, { vm.socialTab = it })
    }
}

// ---------------- private messages ----------------

@Composable
private fun MessagesTab(vm: AppVm) {
    val api = vm.api ?: return
    val target = vm.chatTarget
    if (target != null) {
        BackHandler { vm.chatTarget = null }
        ChatView(vm, target)
        return
    }
    val list = rememberPoll(api, intervalMs = 3000) { api.conversations().filter { it.username != ".." } }
    LazyColumn(contentPadding = pagePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SocialHeader(vm) }
        item {
            GlassButton({ vm.showSheet { PromptSheet("New conversation", "Soulseek username", confirmLabel = "Open", onConfirm = { vm.chatTarget = it }, onClose = { vm.closeSheet() }) } }, Modifier.fillMaxWidth(), height = 46.dp) {
                GIcon(R.drawable.ic_add, size = 18.dp); Txt("New conversation", weight = FontWeight.Medium)
            }
        }
        list.error?.let { item { ErrorBanner(it, list::refresh) } }
        if (list.value == null && list.error == null) item { Loading() }
        if (list.value?.isEmpty() == true) item { Empty("No conversations yet.", R.drawable.ic_chat) }
        items(list.value.orEmpty().sortedByDescending { it.hasUnAcknowledgedMessages }, key = { it.username }) { c ->
            ItemCard(onClick = { vm.chatTarget = c.username }) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    GIcon(R.drawable.ic_person, tint = LocalColors.current.accentB)
                    Txt(c.username, Modifier.weight(1f), size = 16.sp, weight = FontWeight.SemiBold, maxLines = 1)
                    if (c.hasUnAcknowledgedMessages) Badge("${c.unAcknowledgedMessageCount} new", LocalColors.current.accentB)
                }
            }
        }
    }
}

@Composable
private fun ChatView(vm: AppVm, username: String) {
    val api = vm.api ?: return
    val c = LocalColors.current
    val poll = rememberPoll(username, intervalMs = 2000) { api.conversation(username) }
    var draft by remember { mutableStateOf("") }
    val msgs = poll.value?.messages.orEmpty()
    val listState = rememberLazyListState()
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1) }
    LaunchedEffect(username, poll.value?.unAcknowledgedMessageCount) {
        if (poll.value?.hasUnAcknowledgedMessages == true) runCatching { api.ackConversation(username) }
    }
    fun send() {
        if (draft.isBlank()) return
        val text = draft.trim(); draft = ""
        vm.run { it.sendMessage(username, text) }.invokeOnCompletion { poll.refresh() }
    }
    Column(Modifier.fillMaxSize().padding(top = statusTop() + 12.dp)) {
        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassIconButton(R.drawable.ic_chevron_left, "Back", { vm.chatTarget = null })
            Txt(username, Modifier.weight(1f), size = 22.sp, weight = FontWeight.Bold, maxLines = 1)
            GlassIconButton(R.drawable.ic_more, "Options", {
                vm.showSheet {
                    MenuSheet(username, null, listOf(
                        MenuItem("Browse shares", R.drawable.ic_folder) { vm.openUser(username) },
                        MenuItem("User info", R.drawable.ic_person) { vm.openUser(username) },
                        MenuItem("Delete conversation", R.drawable.ic_trash, danger = true) { vm.run(onDone = { vm.chatTarget = null }) { it.deleteConversation(username) } },
                    ), { vm.closeSheet() })
                }
            })
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            poll.error?.let { item { ErrorBanner(it, poll::refresh) } }
            if (poll.value == null && poll.error == null) item { Loading() }
            if (poll.value != null && msgs.isEmpty()) item { Empty("No messages yet — say hi!", R.drawable.ic_chat) }
            items(msgs.size) { i ->
                val m = msgs[i]; val mine = m.direction == "Out"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Column(
                        Modifier.fillMaxWidth(0.82f).glass(com.linuxkernel44.slskdclient.ui.components.cardShape(18.dp), GlassLevel.Card, tint = if (mine) c.accentA.copy(alpha = 0.3f) else androidx.compose.ui.graphics.Color.Unspecified).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Txt(m.message, size = 14.sp)
                        Muted(fmtTime(m.timestamp), size = 11.sp)
                    }
                }
            }
        }
        Row(Modifier.padding(horizontal = 18.dp).padding(bottom = 110.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassField(draft, { draft = it }, "Message", Modifier.weight(1f), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send))
            GlassIconButton(R.drawable.ic_send, "Send", ::send, size = 54.dp)
        }
    }
}

@Composable
private fun statusTop() = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

// ---------------- rooms ----------------

@Composable
private fun RoomsTab(vm: AppVm) {
    val api = vm.api ?: return
    val joined = rememberPoll(api, intervalMs = 5000) { api.roomsJoined() }
    val active = vm.roomTarget
    if (active != null) {
        BackHandler { vm.roomTarget = null }
        RoomView(vm, active)
        return
    }
    LazyColumn(contentPadding = pagePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SocialHeader(vm) }
        item {
            GlassButton({ vm.showSheet { JoinRoomSheet(vm) } }, Modifier.fillMaxWidth(), height = 46.dp) {
                GIcon(R.drawable.ic_add, size = 18.dp); Txt("Join a room", weight = FontWeight.Medium)
            }
        }
        joined.error?.let { item { ErrorBanner(it, joined::refresh) } }
        if (joined.value == null && joined.error == null) item { Loading() }
        if (joined.value?.isEmpty() == true) item { Empty("You haven't joined any room.", R.drawable.ic_group) }
        items(joined.value.orEmpty(), key = { it }) { name ->
            ItemCard(onClick = { vm.roomTarget = name }) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    GIcon(R.drawable.ic_group, tint = LocalColors.current.accentB)
                    Txt(name, Modifier.weight(1f), size = 16.sp, weight = FontWeight.SemiBold, maxLines = 2)
                    GlassIconButton(R.drawable.ic_logout, "Leave", {
                        vm.showSheet { ConfirmSheet("Leave room?", name, "Leave", true, { vm.run { it.leaveRoom(name) }.invokeOnCompletion { joined.refresh() } }, { vm.closeSheet() }) }
                    }, size = 36.dp)
                }
            }
        }
    }
}

@Composable
private fun JoinRoomSheet(vm: AppVm) {
    val api = vm.api ?: return
    var filter by remember { mutableStateOf("") }
    val rooms = rememberPoll(api) { api.roomsAvailable() }
    Txt("Join a room", size = 18.sp, weight = FontWeight.SemiBold)
    GlassField(filter, { filter = it }, "Filter rooms", Modifier.fillMaxWidth().padding(vertical = 10.dp), leading = R.drawable.ic_search)
    if (rooms.value == null) Loading()
    rooms.error?.let { Txt(it, color = LocalColors.current.danger) }
    rooms.value.orEmpty().filter { it.name.contains(filter, true) }.sortedByDescending { it.userCount }.take(60).forEach { r ->
        Row(Modifier.fillMaxWidth().clickable_ { vm.closeSheet(); vm.run("Joined ${r.name}") { it.joinRoom(r.name) }.invokeOnCompletion { vm.openRoom(r.name) } }.padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (r.isPrivate) GIcon(R.drawable.ic_lock, size = 16.dp, tint = LocalColors.current.muted)
            Txt(r.name, Modifier.weight(1f), size = 15.sp, maxLines = 2)
            Muted("${r.userCount}", size = 13.sp)
        }
    }
}

private fun Modifier.clickable_(onClick: () -> Unit): Modifier = clickable(onClick = onClick)

@Composable
private fun RoomView(vm: AppVm, name: String) {
    val api = vm.api ?: return
    val c = LocalColors.current
    val messages = rememberPoll(name, intervalMs = 2000) { api.roomMessages(name) }
    var showUsers by remember { mutableStateOf(false) }
    val users = rememberPoll(name to showUsers, intervalMs = 8000, enabled = showUsers) { api.roomUsers(name) }
    var draft by remember { mutableStateOf("") }
    val msgs = messages.value.orEmpty()
    val listState = rememberLazyListState()
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty() && !showUsers) listState.animateScrollToItem(msgs.size - 1) }
    fun send() {
        if (draft.isBlank()) return
        val text = draft.trim(); draft = ""
        vm.run { it.sendRoomMessage(name, text) }.invokeOnCompletion { messages.refresh() }
    }
    Column(Modifier.fillMaxSize().padding(top = statusTop() + 12.dp)) {
        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassIconButton(R.drawable.ic_chevron_left, "Back", { vm.roomTarget = null })
            Txt(name, Modifier.weight(1f), size = 20.sp, weight = FontWeight.Bold, maxLines = 2)
            GlassIconButton(R.drawable.ic_group, "Users", { showUsers = !showUsers })
        }
        if (showUsers) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                users.error?.let { item { ErrorBanner(it, users::refresh) } }
                if (users.value == null && users.error == null) item { Loading() }
                items(users.value.orEmpty().sortedBy { it.username.lowercase() }, key = { it.username }) { u ->
                    ItemCard(onClick = {
                        vm.showSheet {
                            MenuSheet(u.username, "${u.status} · ${fmtSpeed(u.averageSpeed)} · ${u.fileCount} files", listOf(
                                MenuItem("Browse shares", R.drawable.ic_folder) { vm.openUser(u.username) },
                                MenuItem("Send message", R.drawable.ic_chat) { vm.openChat(u.username) },
                            ), { vm.closeSheet() })
                        }
                    }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Dot(if (u.status == "Online") c.ok else c.muted)
                            Txt(u.username, Modifier.weight(1f), size = 14.sp, maxLines = 1, weight = if (u.self) FontWeight.Bold else FontWeight.Normal)
                            u.countryCode?.let { Muted(it, size = 12.sp) }
                        }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                messages.error?.let { item { ErrorBanner(it, messages::refresh) } }
                if (messages.value == null && messages.error == null) item { Loading() }
                items(msgs.size) { i ->
                    val m = msgs[i]
                    Column(Modifier.fillMaxWidth().clickable_ {
                        vm.showSheet {
                            MenuSheet(m.username, m.message, listOf(
                                MenuItem("Reply in private", R.drawable.ic_chat) { vm.openChat(m.username) },
                                MenuItem("Browse shares", R.drawable.ic_folder) { vm.openUser(m.username) },
                                MenuItem("Search this text", R.drawable.ic_search) { vm.searchFor(m.message.take(80)) },
                            ), { vm.closeSheet() })
                        }
                    }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Txt(m.username, size = 13.sp, weight = FontWeight.SemiBold, color = if (m.self) c.accentB else c.accentA)
                            Muted(fmtTime(m.timestamp), size = 11.sp)
                        }
                        Txt(m.message, size = 14.sp)
                    }
                }
            }
        }
        Row(Modifier.padding(horizontal = 18.dp).padding(bottom = 110.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassField(draft, { draft = it }, "Message the room", Modifier.weight(1f), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send))
            GlassIconButton(R.drawable.ic_send, "Send", ::send, size = 54.dp)
        }
    }
}

// ---------------- users ----------------

@Composable
private fun UsersTab(vm: AppVm) {
    val api = vm.api ?: return
    var query by rememberSaveable { mutableStateOf(vm.userTarget.orEmpty()) }
    var current by rememberSaveable { mutableStateOf(vm.userTarget) }
    var browsing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(vm.userTarget) { vm.userTarget?.let { query = it; current = it; browsing = false; vm.userTarget = null } }

    if (current != null && browsing) {
        BackHandler { browsing = false }
        BrowseView(vm, current!!, onBack = { browsing = false })
        return
    }
    val info = rememberPoll(current, enabled = current != null) {
        val u = current!!
        Triple(runCatching { api.userInfo(u) }.getOrNull(), runCatching { api.userStatus(u) }.getOrNull(), runCatching { api.userEndpoint(u) }.getOrNull())
    }
    LazyColumn(contentPadding = pagePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SocialHeader(vm) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassField(query, { query = it }, "Look up a user", Modifier.weight(1f), leading = R.drawable.ic_person, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search))
                GlassIconButton(R.drawable.ic_search, "Look up", { if (query.isNotBlank()) { current = query.trim(); info.refresh() } }, size = 54.dp)
            }
        }
        if (current == null) item { Empty("Enter a Soulseek username to see their info, browse their files or message them.", R.drawable.ic_person) }
        else {
            val (ui, status, endpoint) = info.value ?: Triple<UserInfo?, com.linuxkernel44.slskdclient.data.UserStatus?, String?>(null, null, null)
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            val bmp = remember(ui?.picture) { ui?.picture?.let { runCatching { val b = android.util.Base64.decode(it, android.util.Base64.DEFAULT); android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size) }.getOrNull() } }
                            if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.size(64.dp).clip(CapsuleShape))
                            Column(Modifier.weight(1f)) {
                                Txt(current!!, size = 22.sp, weight = FontWeight.Bold, maxLines = 1)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Dot(if (status?.presence == "Online") LocalColors.current.ok else LocalColors.current.muted)
                                    Muted(status?.presence ?: if (info.loading) "Loading…" else "Unknown", size = 13.sp)
                                    if (status?.isPrivileged == true) Badge("privileged")
                                }
                            }
                        }
                        if (info.loading) Loading()
                        if (ui != null) {
                            Txt(ui.description?.takeIf { it.isNotBlank() } ?: "No user info.", size = 14.sp)
                            KeyValue("Upload slots", ui.uploadSlots.toString()); KeyValue("Queue length", ui.queueLength.toString())
                            KeyValue("Free slot", if (ui.hasFreeUploadSlot) "yes" else "no")
                        } else if (!info.loading) Muted("User is offline or did not answer.", size = 13.sp)
                        endpoint?.takeIf { it.isNotBlank() && !it.startsWith("{") }?.let { KeyValue("Endpoint", it, mono = true) }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton({ browsing = true }, Modifier.weight(1f), primary = true) { GIcon(R.drawable.ic_folder, size = 18.dp); Txt("Browse", weight = FontWeight.SemiBold) }
                    GlassButton({ vm.openChat(current!!) }, Modifier.weight(1f)) { GIcon(R.drawable.ic_chat, size = 18.dp); Txt("Message", weight = FontWeight.Medium) }
                }
            }
        }
    }
}
