package com.linuxkernel44.slskdclient.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linuxkernel44.slskdclient.R
import com.linuxkernel44.slskdclient.data.ApiClient
import com.linuxkernel44.slskdclient.data.FsEntry
import com.linuxkernel44.slskdclient.data.Share
import com.linuxkernel44.slskdclient.player.Track
import com.linuxkernel44.slskdclient.ui.AppVm
import com.linuxkernel44.slskdclient.ui.components.Badge
import com.linuxkernel44.slskdclient.ui.components.ChipTabs
import com.linuxkernel44.slskdclient.ui.components.ConfirmSheet
import com.linuxkernel44.slskdclient.ui.components.Empty
import com.linuxkernel44.slskdclient.ui.components.ErrorBanner
import com.linuxkernel44.slskdclient.ui.components.GIcon
import com.linuxkernel44.slskdclient.ui.components.GlassButton
import com.linuxkernel44.slskdclient.ui.components.GlassCard
import com.linuxkernel44.slskdclient.ui.components.GlassIconButton
import com.linuxkernel44.slskdclient.ui.components.GlassSegmented
import com.linuxkernel44.slskdclient.ui.components.ItemCard
import com.linuxkernel44.slskdclient.ui.components.Loading
import com.linuxkernel44.slskdclient.ui.components.MenuItem
import com.linuxkernel44.slskdclient.ui.components.MenuSheet
import com.linuxkernel44.slskdclient.ui.components.Muted
import com.linuxkernel44.slskdclient.ui.components.ProgressBar
import com.linuxkernel44.slskdclient.ui.components.Txt
import com.linuxkernel44.slskdclient.ui.components.pagePadding
import com.linuxkernel44.slskdclient.ui.fmtBytes
import com.linuxkernel44.slskdclient.ui.fmtDateTime
import com.linuxkernel44.slskdclient.ui.isAudio
import com.linuxkernel44.slskdclient.ui.rememberPoll
import com.linuxkernel44.slskdclient.ui.theme.LocalColors
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(vm: AppVm) {
    when (vm.libraryTab) {
        0 -> FilesTab(vm)
        else -> SharesTab(vm)
    }
}

@Composable
private fun LibraryHeader(vm: AppVm, title: String = "Library") {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Txt(title, size = 28.sp, weight = FontWeight.Bold)
        ChipTabs(listOf("Files", "Shares"), vm.libraryTab, { vm.libraryTab = it })
    }
}

// ---------------- files on the server ----------------

private fun rel(path: List<String>, name: String) = (path + name).joinToString("/")

/** Recursively lists every file under [path] as (relative path, size). */
private suspend fun collect(api: ApiClient, root: String, path: List<String>): List<Pair<String, Long>> {
    val d = api.listFiles(root, path.joinToString("/"))
    return d.files.map { rel(path, it.name) to it.length } + d.directories.flatMap { collect(api, root, path + it.name) }
}

@Composable
private fun FilesTab(vm: AppVm) {
    val api = vm.api ?: return
    var root by rememberSaveable { mutableStateOf("downloads") }
    var path by rememberSaveable(root) { mutableStateOf(emptyList<String>()) }
    val dir = rememberPoll(root to path, intervalMs = 0) { api.listFiles(root, path.joinToString("/")) }
    val server = rememberPoll(api) { api.filesServerAvailable() }
    val c = LocalColors.current
    val progress by vm.saver.progress.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val canStream = root == "downloads" && server.value == true

    BackHandler(path.isNotEmpty()) { path = path.dropLast(1) }

    fun tracks(files: List<FsEntry>) = files.filter { isAudio(it.name) }.sortedBy { it.name.lowercase() }
        .map { Track(api.fileUrl(rel(path, it.name)), it.name.substringBeforeLast('.'), path.lastOrNull().orEmpty()) }

    fun save(items: List<Pair<String, Long>>) {
        if (items.isEmpty()) return
        scope.launch {
            try {
                val n = vm.saver.save(items)
                vm.say("Saved $n file${if (n == 1) "" else "s"} to Downloads/Slskd Client")
            } catch (e: Exception) { vm.say(vm.friendly(e)) }
        }
    }

    LazyColumn(contentPadding = pagePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { LibraryHeader(vm) }
        item {
            GlassSegmented(listOf("Downloads", "Incomplete"), if (root == "downloads") 0 else 1, { root = if (it == 0) "downloads" else "incomplete"; path = emptyList() }, Modifier.fillMaxWidth())
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (path.isNotEmpty()) GlassIconButton(R.drawable.ic_chevron_left, "Up", { path = path.dropLast(1) })
                Column(Modifier.weight(1f)) {
                    Txt(path.lastOrNull() ?: root.replaceFirstChar { it.uppercase() }, size = 17.sp, weight = FontWeight.SemiBold, maxLines = 2)
                    Muted(if (path.isEmpty()) "Server folder" else path.joinToString(" / "), size = 12.sp, maxLines = 2)
                }
                GlassIconButton(R.drawable.ic_refresh, "Refresh", dir::refresh)
            }
        }
        if (root == "downloads" && server.value == false) item {
            GlassCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    GIcon(R.drawable.ic_info, tint = c.muted)
                    Muted("File server not reachable at ${api.profile.filesBaseUrl}. Playing and saving need the slskd-files companion (see the repo's server/ folder).", size = 12.sp)
                }
            }
        }
        progress?.let { p ->
            item {
                GlassCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Txt("Saving ${p.index}/${p.total}: ${p.name}", size = 13.sp, maxLines = 1)
                        ProgressBar(p.fraction)
                    }
                }
            }
        }
        dir.error?.let { item { ErrorBanner(it, dir::refresh) } }
        if (dir.value == null && dir.error == null) item { Loading() }
        val d = dir.value
        if (d != null) {
            val audio = tracks(d.files)
            if (canStream && audio.isNotEmpty()) item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton({ vm.player.play(audio); vm.showPlayer = true }, Modifier.weight(1f), primary = true, height = 46.dp) { GIcon(R.drawable.ic_play, size = 18.dp); Txt("Play ${audio.size}", weight = FontWeight.SemiBold) }
                    GlassButton({ save(d.files.map { rel(path, it.name) to it.length }) }, Modifier.weight(1f), height = 46.dp) { GIcon(R.drawable.ic_save, size = 18.dp); Txt("Save to phone", weight = FontWeight.Medium) }
                }
            }
            if (d.directories.isEmpty() && d.files.isEmpty()) item { Empty("This folder is empty.", R.drawable.ic_folder) }
            items(d.directories.sortedBy { it.name.lowercase() }, key = { "d" + it.name }) { sub ->
                ItemCard(onClick = { path = path + sub.name }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        GIcon(R.drawable.ic_folder, tint = c.accentB)
                        Txt(sub.name, Modifier.weight(1f), size = 15.sp, weight = FontWeight.SemiBold, maxLines = 2)
                        GlassIconButton(R.drawable.ic_more, "Folder", {
                            vm.showSheet {
                                MenuSheet(sub.name, null, buildList {
                                    if (canStream) {
                                        add(MenuItem("Play folder", R.drawable.ic_play) {
                                            scope.launch {
                                                runCatching {
                                                    val all = collect(api, root, path + sub.name).filter { isAudio(it.first) }.sortedBy { it.first.lowercase() }
                                                    vm.player.play(all.map { Track(api.fileUrl(it.first), it.first.substringAfterLast('/').substringBeforeLast('.'), sub.name) }); vm.showPlayer = true
                                                }.onFailure { vm.say(vm.friendly(it)) }
                                            }
                                        })
                                        add(MenuItem("Save folder to phone", R.drawable.ic_save) {
                                            scope.launch { runCatching { save(collect(api, root, path + sub.name)) }.onFailure { vm.say(vm.friendly(it)) } }
                                        })
                                    }
                                    add(MenuItem("Delete folder", R.drawable.ic_trash, danger = true) {
                                        vm.showSheet { ConfirmSheet("Delete folder?", "“${sub.name}” and everything inside it will be deleted from the server.", "Delete", true, { vm.run("Deleted") { it.deleteDirectory(root, rel(path, sub.name)) }.invokeOnCompletion { dir.refresh() } }, { vm.closeSheet() }) }
                                    })
                                }, { vm.closeSheet() })
                            }
                        }, size = 36.dp)
                    }
                }
            }
            items(d.files.sortedBy { it.name.lowercase() }, key = { "f" + it.name }) { f ->
                val playable = canStream && isAudio(f.name)
                ItemCard(onClick = if (playable) ({
                    val list = tracks(d.files); val i = list.indexOfFirst { it.title == f.name.substringBeforeLast('.') }.coerceAtLeast(0)
                    vm.player.play(list, i); vm.showPlayer = true
                }) else null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        GIcon(if (isAudio(f.name)) R.drawable.ic_music else R.drawable.ic_file, tint = c.accentA)
                        Column(Modifier.weight(1f)) {
                            Txt(f.name, size = 14.sp, maxLines = 2)
                            Muted(fmtBytes(f.length) + (f.modifiedAt?.let { " · " + fmtDateTime(it) } ?: ""), size = 11.5.sp)
                        }
                        GlassIconButton(R.drawable.ic_more, "File", {
                            vm.showSheet {
                                MenuSheet(f.name, fmtBytes(f.length), buildList {
                                    if (playable) add(MenuItem("Play", R.drawable.ic_play) { vm.player.play(tracks(d.files), tracks(d.files).indexOfFirst { it.title == f.name.substringBeforeLast('.') }.coerceAtLeast(0)); vm.showPlayer = true })
                                    if (canStream) add(MenuItem("Save to phone", R.drawable.ic_save) { save(listOf(rel(path, f.name) to f.length)) })
                                    add(MenuItem("Delete", R.drawable.ic_trash, danger = true) {
                                        vm.showSheet { ConfirmSheet("Delete file?", f.name, "Delete", true, { vm.run("Deleted") { it.deleteFile(root, rel(path, f.name)) }.invokeOnCompletion { dir.refresh() } }, { vm.closeSheet() }) }
                                    })
                                }, { vm.closeSheet() })
                            }
                        }, size = 36.dp)
                    }
                }
            }
        }
    }
}

// ---------------- shares ----------------

@Composable
private fun SharesTab(vm: AppVm) {
    val api = vm.api ?: return
    val state = rememberPoll(api, intervalMs = 3000) { api.appState().shares }
    val shares = rememberPoll(api, intervalMs = 15000) { api.shares().local }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val c = LocalColors.current

    if (open != null) {
        BackHandler { open = null }
        val share = shares.value?.firstOrNull { it.id == open }
        ShareContents(vm, share, onBack = { open = null })
        return
    }
    LazyColumn(contentPadding = pagePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { LibraryHeader(vm) }
        val s = state.value
        if (s != null) item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Txt(when { s.scanning -> "Scanning shares…"; s.faulted -> "Share scan failed"; s.ready -> "Shares ready"; else -> "Shares not ready" }, size = 16.sp, weight = FontWeight.SemiBold)
                            Muted("${s.directories} directories · ${s.files} files", size = 12.sp)
                        }
                        if (s.scanning) GlassIconButton(R.drawable.ic_stop, "Cancel scan", { vm.run("Scan cancelled") { it.cancelShareScan() } })
                        else GlassIconButton(R.drawable.ic_refresh, "Rescan", { vm.run("Rescan started") { it.rescanShares() }.invokeOnCompletion { state.refresh() } })
                    }
                    if (s.scanning) ProgressBar(s.scanProgress.toFloat())
                }
            }
        }
        shares.error?.let { item { ErrorBanner(it, shares::refresh) } }
        if (shares.value == null && shares.error == null) item { Loading() }
        if (shares.value?.isEmpty() == true) item { Empty("No shares configured. Add `shares.directories` in Options.", R.drawable.ic_folder) }
        items(shares.value.orEmpty(), key = { it.id }) { sh ->
            ItemCard(onClick = { open = sh.id }) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    GIcon(R.drawable.ic_folder, tint = c.accentB)
                    Column(Modifier.weight(1f)) {
                        Txt(sh.alias, size = 16.sp, weight = FontWeight.SemiBold, maxLines = 1)
                        Muted(sh.localPath, size = 12.sp, maxLines = 2)
                        Muted("${sh.directories ?: 0} directories · ${sh.files ?: 0} files", size = 12.sp)
                    }
                    if (sh.isExcluded) Badge("excluded", c.danger)
                }
            }
        }
    }
}

@Composable
private fun ShareContents(vm: AppVm, share: Share?, onBack: () -> Unit) {
    val api = vm.api ?: return
    val contents = rememberPoll(share?.id) { share?.let { api.shareContents(it.id) }.orEmpty() }
    var filter by remember { mutableStateOf("") }
    LazyColumn(contentPadding = pagePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassIconButton(R.drawable.ic_chevron_left, "Back", onBack)
                Column(Modifier.weight(1f)) {
                    Txt(share?.alias ?: "Share", size = 22.sp, weight = FontWeight.Bold, maxLines = 1)
                    Muted(share?.localPath.orEmpty(), size = 12.sp, maxLines = 2)
                }
            }
        }
        item { com.linuxkernel44.slskdclient.ui.components.GlassField(filter, { filter = it }, "Filter folders", leading = R.drawable.ic_filter) }
        contents.error?.let { item { ErrorBanner(it, contents::refresh) } }
        if (contents.value == null && contents.error == null) item { Loading() }
        items(contents.value.orEmpty().filter { it.name.contains(filter, true) }.take(300), key = { it.name }) { d ->
            ItemCard {
                Txt(d.name.substringAfterLast('\\').ifBlank { d.name }, size = 14.sp, weight = FontWeight.SemiBold, maxLines = 2)
                Muted(d.name.substringBeforeLast('\\', "").takeLast(80) + " · ${d.fileCount} files", size = 11.5.sp, maxLines = 2)
            }
        }
    }
}
