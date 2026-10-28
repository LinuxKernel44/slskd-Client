package com.linuxkernel44.slskdclient.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linuxkernel44.slskdclient.R
import com.linuxkernel44.slskdclient.data.EnqueueFile
import com.linuxkernel44.slskdclient.data.RemoteFile
import com.linuxkernel44.slskdclient.data.SearchRequest
import com.linuxkernel44.slskdclient.data.SearchResponse
import com.linuxkernel44.slskdclient.data.SearchStatus
import com.linuxkernel44.slskdclient.ui.AppVm
import com.linuxkernel44.slskdclient.ui.baseName
import com.linuxkernel44.slskdclient.ui.components.Badge
import com.linuxkernel44.slskdclient.ui.components.ConfirmSheet
import com.linuxkernel44.slskdclient.ui.components.Empty
import com.linuxkernel44.slskdclient.ui.components.ErrorBanner
import com.linuxkernel44.slskdclient.ui.components.GIcon
import com.linuxkernel44.slskdclient.ui.components.GlassButton
import com.linuxkernel44.slskdclient.ui.components.GlassCard
import com.linuxkernel44.slskdclient.ui.components.GlassChip
import com.linuxkernel44.slskdclient.ui.components.GlassField
import com.linuxkernel44.slskdclient.ui.components.GlassIconButton
import com.linuxkernel44.slskdclient.ui.components.GlassToggle
import com.linuxkernel44.slskdclient.ui.components.ItemCard
import com.linuxkernel44.slskdclient.ui.components.Loading
import com.linuxkernel44.slskdclient.ui.components.MenuItem
import com.linuxkernel44.slskdclient.ui.components.MenuSheet
import com.linuxkernel44.slskdclient.ui.components.Muted
import com.linuxkernel44.slskdclient.ui.components.ProgressBar
import com.linuxkernel44.slskdclient.ui.components.SettingRow
import com.linuxkernel44.slskdclient.ui.components.Txt
import com.linuxkernel44.slskdclient.ui.components.pagePadding
import com.linuxkernel44.slskdclient.ui.dirName
import com.linuxkernel44.slskdclient.ui.fmtBytes
import com.linuxkernel44.slskdclient.ui.fmtDateTime
import com.linuxkernel44.slskdclient.ui.fmtDuration
import com.linuxkernel44.slskdclient.ui.fmtSpeed
import com.linuxkernel44.slskdclient.ui.rememberPoll
import com.linuxkernel44.slskdclient.ui.theme.LocalColors
import java.util.UUID

@Composable
fun SearchScreen(vm: AppVm) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val api = vm.api ?: return

    // text shared into the app or sent from another screen starts a search right away
    LaunchedEffect(vm.searchSeed) {
        val seed = vm.searchSeed ?: return@LaunchedEffect
        vm.searchSeed = null
        val id = UUID.randomUUID().toString()
        vm.run { it.createSearch(SearchRequest(id, seed)) }.join()
        openId = id
    }

    if (openId != null) {
        BackHandler { openId = null }
        SearchDetail(vm, openId!!, onBack = { openId = null }, onOpen = { openId = it })
    } else SearchList(vm, onOpen = { openId = it })
}

@Composable
private fun SearchList(vm: AppVm, onOpen: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var limits by remember { mutableStateOf(SearchRequest("", "")) }
    val api = vm.api ?: return
    val list = rememberPoll(api, intervalMs = 2500) { api.searches().sortedByDescending { it.startedAt } }
    val c = LocalColors.current

    fun start() {
        if (text.isBlank()) return
        val id = UUID.randomUUID().toString()
        val req = limits.copy(id = id, searchText = text.trim())
        text = ""
        vm.run(onDone = { onOpen(id) }) { it.createSearch(req) }
    }

    LazyColumn(contentPadding = pagePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Txt("Search", size = 28.sp, weight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassField(text, { text = it }, "Search the Soulseek network", Modifier.weight(1f), leading = R.drawable.ic_search,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search))
                GlassIconButton(R.drawable.ic_filter, "Search options", { vm.showSheet { SearchOptionsSheet(limits, { limits = it }) { vm.closeSheet() } } }, size = 54.dp)
                GlassIconButton(R.drawable.ic_search, "Search", ::start, size = 54.dp)
            }
        }
        list.error?.let { item { ErrorBanner(it, list::refresh) } }
        val searches = list.value
        if (searches == null && list.error == null) item { Loading() }
        if (searches != null && searches.isEmpty()) item { Empty("No searches yet. Type something above.", R.drawable.ic_search) }
        items(searches.orEmpty(), key = { it.id }) { s ->
            ItemCard(onClick = { onOpen(s.id) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Txt(s.searchText.ifBlank { "(empty)" }, size = 16.sp, weight = FontWeight.SemiBold, maxLines = 2)
                        Muted("${s.responseCount} responses · ${s.fileCount} files · ${fmtDateTime(s.startedAt)}", size = 12.sp)
                    }
                    Badge(stateLabel(s), if (s.isComplete) c.muted else c.accentB)
                    GlassIconButton(R.drawable.ic_more, "Options", {
                        vm.showSheet {
                            MenuSheet(s.searchText, stateLabel(s), buildList {
                                add(MenuItem("Open", R.drawable.ic_eye) { onOpen(s.id) })
                                add(MenuItem("Repeat", R.drawable.ic_refresh) {
                                    val id = UUID.randomUUID().toString()
                                    vm.run(onDone = { onOpen(id) }) { it.createSearch(SearchRequest(id, s.searchText)) }
                                })
                                if (!s.isComplete) add(MenuItem("Stop", R.drawable.ic_stop) { vm.run { it.stopSearch(s.id) } })
                                add(MenuItem("Delete", R.drawable.ic_trash, danger = true) { vm.run { it.deleteSearch(s.id) } })
                            }, { vm.closeSheet() })
                        }
                    }, size = 38.dp)
                }
                if (!s.isComplete) ProgressBar(null, Modifier.padding(top = 6.dp))
            }
        }
    }
}

private fun stateLabel(s: SearchStatus) = when {
    !s.isComplete -> "Searching"
    s.state.contains("Cancelled") -> "Stopped"
    s.state.contains("Errored") && s.responseCount == 0 -> "Failed"
    s.state.contains("TimedOut") -> "Done"
    s.state.contains("LimitReached") -> "Limit"
    else -> "Done"
}

@Composable
private fun SearchOptionsSheet(current: SearchRequest, onChange: (SearchRequest) -> Unit, onClose: () -> Unit) {
    var response by remember { mutableStateOf(current.responseLimit?.toString().orEmpty()) }
    var file by remember { mutableStateOf(current.fileLimit?.toString().orEmpty()) }
    var timeout by remember { mutableStateOf(current.searchTimeout?.let { (it / 1000).toString() }.orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Txt("Search options", size = 18.sp, weight = FontWeight.SemiBold)
        Muted("Leave empty to use the server defaults.", size = 13.sp)
        GlassField(response, { response = it.filter(Char::isDigit) }, "Response limit", keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
        GlassField(file, { file = it.filter(Char::isDigit) }, "File limit", keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
        GlassField(timeout, { timeout = it.filter(Char::isDigit) }, "Timeout (seconds)", keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton({ onChange(SearchRequest("", "")); onClose() }, Modifier.weight(1f)) { Txt("Reset", weight = FontWeight.Medium) }
            GlassButton({
                onChange(SearchRequest("", "", fileLimit = file.toIntOrNull(), responseLimit = response.toIntOrNull(), searchTimeout = timeout.toIntOrNull()?.times(1000)))
                onClose()
            }, Modifier.weight(1f), primary = true) { Txt("Apply", weight = FontWeight.SemiBold) }
        }
    }
}

// ---------------- detail ----------------

@Composable
private fun SearchDetail(vm: AppVm, id: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val api = vm.api ?: return
    val status = rememberPoll(id, intervalMs = 1500) { api.searchStatus(id) }
    val complete = status.value?.isComplete == true
    var finalFetched by remember(id) { mutableStateOf(false) }
    val responses = rememberPoll(id to complete, intervalMs = if (complete) 0 else 3000) {
        api.searchResponses(id).also { if (complete) finalFetched = true }
    }

    var filterText by rememberSaveable(id) { mutableStateOf("") }
    var sortByQueue by rememberSaveable(id) { mutableStateOf(false) }
    var freeSlotsOnly by rememberSaveable(id) { mutableStateOf(false) }
    var hideLocked by rememberSaveable(id) { mutableStateOf(true) }
    var shown by remember(id) { mutableIntStateOf(15) }
    val hidden = remember(id) { mutableStateListOf<String>() }
    val selected = remember(id) { mutableStateListOf<String>() }     // "username\u0000filename"

    val filtered by remember(responses.value, filterText, sortByQueue, freeSlotsOnly, hideLocked, hidden.size) {
        derivedStateOf {
            val f = parseFilters(filterText)
            responses.value.orEmpty().filter { it.username !in hidden }.map { filterResponse(it, f, hideLocked) }
                .filter { it.fileCount + it.lockedFileCount > 0 }.filter { !(freeSlotsOnly && !it.hasFreeUploadSlot) }
                .sortedWith(if (sortByQueue) compareBy { it.queueLength } else compareByDescending { it.uploadSpeed })
        }
    }

    fun enqueue(user: String, files: List<RemoteFile>) {
        if (files.isEmpty()) return
        vm.run("Queued ${files.size} file${if (files.size == 1) "" else "s"} from $user") { it.enqueue(user, files.map { f -> EnqueueFile(f.filename, f.size) }) }
    }

    LazyColumn(contentPadding = pagePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            val s = status.value
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassIconButton(R.drawable.ic_chevron_left, "Back", onBack)
                Column(Modifier.weight(1f)) {
                    Txt(s?.searchText ?: "…", size = 22.sp, weight = FontWeight.Bold, maxLines = 2)
                    Muted(if (s == null) "" else "${stateLabel(s)} · ${s.responseCount} responses · ${s.fileCount} files" + if (s.lockedFileCount > 0) " (+${s.lockedFileCount} locked)" else "", size = 12.sp)
                }
                if (s != null && !s.isComplete) GlassIconButton(R.drawable.ic_stop, "Stop", { vm.run { it.stopSearch(id) } })
                GlassIconButton(R.drawable.ic_more, "More", {
                    vm.showSheet {
                        MenuSheet(s?.searchText.orEmpty(), null, listOf(
                            MenuItem("Repeat search", R.drawable.ic_refresh) {
                                val nid = UUID.randomUUID().toString()
                                vm.run(onDone = { onOpen(nid) }) { it.createSearch(SearchRequest(nid, s?.searchText.orEmpty())) }
                            },
                            MenuItem("Delete search", R.drawable.ic_trash, danger = true) { vm.run(onDone = onBack) { it.deleteSearch(id) } },
                        ), { vm.closeSheet() })
                    }
                })
            }
            if (s != null && !s.isComplete) ProgressBar(null, Modifier.padding(top = 10.dp))
        }
        status.error?.let { item { ErrorBanner(it, status::refresh) } }
        item {
            GlassCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassField(filterText, { filterText = it }, "Filter: minbr:320 islossless -live …", leading = R.drawable.ic_filter)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassChip("Fastest", !sortByQueue, { sortByQueue = false })
                        GlassChip("Shortest queue", sortByQueue, { sortByQueue = true })
                        GlassChip("Free slot", freeSlotsOnly, { freeSlotsOnly = !freeSlotsOnly })
                        GlassChip("Hide locked", hideLocked, { hideLocked = !hideLocked })
                    }
                    Muted("${filtered.size} of ${responses.value?.size ?: 0} results shown" + if (hidden.isNotEmpty()) " · ${hidden.size} hidden" else "", size = 12.sp)
                }
            }
        }
        if (responses.value == null && responses.error == null) item { Loading() }
        responses.error?.let { item { ErrorBanner(it, responses::refresh) } }
        if (responses.value != null && filtered.isEmpty()) item { Empty(if (complete) "No results match" else "Waiting for results…", R.drawable.ic_search) }
        items(filtered.take(shown), key = { it.username + it.token }) { r ->
            ResponseCard(vm, r, selected, hideLocked, onDownload = { enqueue(r.username, it) }, onHide = { hidden.add(r.username) })
        }
        if (filtered.size > shown) item { GlassButton({ shown += 15 }, Modifier.fillMaxWidth()) { Txt("Show more (${filtered.size - shown} left)", weight = FontWeight.Medium) } }
    }
}

private fun key(user: String, file: String) = user + "\u0000" + file

@Composable
private fun ResponseCard(
    vm: AppVm, r: SearchResponse, selected: MutableList<String>, hideLocked: Boolean,
    onDownload: (List<RemoteFile>) -> Unit, onHide: () -> Unit,
) {
    val c = LocalColors.current
    var expanded by rememberSaveable(r.username + r.token) { mutableStateOf(false) }
    val all = r.files + if (hideLocked) emptyList() else r.lockedFiles
    val groups = remember(r) { r.files.groupBy { dirName(it.filename) } }
    val mySelected = all.filter { key(r.username, it.filename) in selected }

    ItemCard(onClick = { expanded = !expanded }) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt(r.username, size = 16.sp, weight = FontWeight.SemiBold, maxLines = 1)
                Muted("${fmtSpeed(r.uploadSpeed)} · queue ${r.queueLength} · ${r.fileCount} file${if (r.fileCount == 1) "" else "s"}" + if (r.lockedFileCount > 0) " · ${r.lockedFileCount} locked" else "", size = 12.sp)
            }
            if (r.hasFreeUploadSlot) Badge("free slot", c.ok)
            GlassIconButton(R.drawable.ic_more, "User", {
                vm.showSheet {
                    MenuSheet(r.username, "${fmtSpeed(r.uploadSpeed)} · queue ${r.queueLength}", listOf(
                        MenuItem("Download all ${r.files.size} files", R.drawable.ic_download) { onDownload(r.files) },
                        MenuItem("Browse shares", R.drawable.ic_folder) { vm.openUser(r.username) },
                        MenuItem("Send message", R.drawable.ic_chat) { vm.openChat(r.username) },
                        MenuItem("Hide this user", R.drawable.ic_block) { onHide() },
                    ), { vm.closeSheet() })
                }
            }, size = 36.dp)
        }
        // collapsed: first folder name and first few files
        val firstDir = groups.keys.firstOrNull().orEmpty()
        if (!expanded) {
            Muted(baseName(firstDir).ifBlank { "/" } + if (groups.size > 1) "  +${groups.size - 1} more folders" else "", size = 12.sp, maxLines = 1)
            r.files.take(3).forEach { f -> Txt(baseName(f.filename), size = 13.sp, maxLines = 1) }
            if (r.files.size > 3) Muted("+${r.files.size - 3} more…", size = 12.sp)
        } else {
            groups.forEach { (dir, files) ->
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GIcon(R.drawable.ic_folder, size = 16.dp, tint = c.accentB)
                    Txt(baseName(dir).ifBlank { "/" }, Modifier.weight(1f), size = 13.sp, weight = FontWeight.SemiBold, maxLines = 2)
                    GlassIconButton(R.drawable.ic_download, "Download folder", { onDownload(files) }, size = 32.dp)
                }
                files.forEach { f -> FileLine(r.username, f, selected, locked = false) }
            }
            if (!hideLocked) r.lockedFiles.forEach { f -> FileLine(r.username, f, selected, locked = true) }
            if (mySelected.isNotEmpty()) {
                GlassButton({ onDownload(mySelected.filter { !it.isLocked }); mySelected.forEach { selected.remove(key(r.username, it.filename)) } }, Modifier.fillMaxWidth().padding(top = 8.dp), primary = true, height = 46.dp) {
                    GIcon(R.drawable.ic_download, size = 18.dp); Txt("Download ${mySelected.size} selected", weight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun FileLine(user: String, f: RemoteFile, selected: MutableList<String>, locked: Boolean) {
    val c = LocalColors.current
    val k = key(user, f.filename)
    val on = k in selected
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).let { m -> if (locked) m else m },
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (locked) GIcon(R.drawable.ic_lock, size = 20.dp, tint = c.muted)
        else GlassToggle(on, { if (it) selected.add(k) else selected.remove(k) })
        Column(Modifier.weight(1f)) {
            Txt(baseName(f.filename), size = 13.sp, maxLines = 2, color = if (locked) c.muted else c.text)
            Muted(fileMeta(f), size = 11.sp)
        }
    }
}

fun fileMeta(f: RemoteFile): String = buildList {
    add(fmtBytes(f.size))
    f.bitRate?.takeIf { it > 0 }?.let { add("$it kbps" + if (f.isVariableBitRate == true) " VBR" else "") }
    if (f.sampleRate != null && f.bitDepth != null) add("${f.bitDepth}-bit/${f.sampleRate / 1000.0} kHz")
    f.length?.takeIf { it > 0 }?.let { add(fmtDuration(it)) }
}.joinToString(" · ")
