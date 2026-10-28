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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linuxkernel44.slskdclient.R
import com.linuxkernel44.slskdclient.data.BrowseDirectory
import com.linuxkernel44.slskdclient.data.EnqueueFile
import com.linuxkernel44.slskdclient.data.RemoteFile
import com.linuxkernel44.slskdclient.ui.AppVm
import com.linuxkernel44.slskdclient.ui.baseName
import com.linuxkernel44.slskdclient.ui.components.Empty
import com.linuxkernel44.slskdclient.ui.components.ErrorBanner
import com.linuxkernel44.slskdclient.ui.components.GIcon
import com.linuxkernel44.slskdclient.ui.components.GlassButton
import com.linuxkernel44.slskdclient.ui.components.GlassCard
import com.linuxkernel44.slskdclient.ui.components.GlassField
import com.linuxkernel44.slskdclient.ui.components.GlassIconButton
import com.linuxkernel44.slskdclient.ui.components.GlassToggle
import com.linuxkernel44.slskdclient.ui.components.ItemCard
import com.linuxkernel44.slskdclient.ui.components.Muted
import com.linuxkernel44.slskdclient.ui.components.ProgressBar
import com.linuxkernel44.slskdclient.ui.components.Txt
import com.linuxkernel44.slskdclient.ui.components.pagePadding
import com.linuxkernel44.slskdclient.ui.rememberPoll
import com.linuxkernel44.slskdclient.ui.theme.LocalColors

private class Node(val name: String, val full: String) {
    val children = sortedMapOf<String, Node>(String.CASE_INSENSITIVE_ORDER)
    var files: List<RemoteFile> = emptyList()
    fun allFiles(): List<RemoteFile> = files + children.values.flatMap { it.allFiles() }
}

/** Builds a folder tree out of the flat directory list returned by slskd (paths use `\` or `/`). */
private fun buildTree(dirs: List<BrowseDirectory>): Pair<Node, Char> {
    val sep = dirs.firstOrNull { it.name.contains('\\') }?.let { '\\' } ?: dirs.firstOrNull { it.name.contains('/') }?.let { '/' } ?: '\\'
    val root = Node("", "")
    dirs.forEach { d ->
        var node = root; var path = ""
        d.name.split(sep).filter { it.isNotEmpty() }.forEach { part ->
            path = if (path.isEmpty()) part else path + sep + part
            node = node.children.getOrPut(part) { Node(part, path) }
        }
        node.files = d.files
    }
    return root to sep
}

@Composable
fun BrowseView(vm: AppVm, username: String, onBack: () -> Unit) {
    val api = vm.api ?: return
    val c = LocalColors.current
    val result = rememberPoll(username) { api.browseUser(username).let { it.directories + it.lockedDirectories } }
    val progress = rememberPoll(username, intervalMs = 1500, enabled = result.value == null && result.error == null) { api.browseStatus(username) }
    val tree = remember(result.value) { result.value?.let { buildTree(it) } }
    var path by remember(username) { mutableStateOf(emptyList<String>()) }
    var filter by remember(username) { mutableStateOf("") }
    val selected = remember(username) { mutableStateListOf<String>() }

    BackHandler { if (path.isNotEmpty()) path = path.dropLast(1) else onBack() }

    val node = tree?.first?.let { root -> path.fold(root) { n, p -> n.children[p] ?: n } }
    fun enqueue(files: List<RemoteFile>) {
        if (files.isEmpty()) return
        vm.run("Queued ${files.size} file${if (files.size == 1) "" else "s"}") { api ->
            files.chunked(100).forEach { chunk -> api.enqueue(username, chunk.map { EnqueueFile(it.filename, it.size) }) }
        }
    }

    LazyColumn(contentPadding = pagePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassIconButton(R.drawable.ic_chevron_left, "Back", { if (path.isNotEmpty()) path = path.dropLast(1) else onBack() })
                Column(Modifier.weight(1f)) {
                    Txt(username, size = 22.sp, weight = FontWeight.Bold, maxLines = 1)
                    Muted(if (path.isEmpty()) "Shared files" else path.joinToString(" / "), size = 12.sp, maxLines = 2)
                }
                GlassIconButton(R.drawable.ic_chat, "Message", { vm.openChat(username) })
            }
        }
        result.error?.let { item { ErrorBanner(it, result::refresh) } }
        if (tree == null && result.error == null) item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Txt("Fetching the share list…", size = 15.sp, weight = FontWeight.Medium)
                    val p = progress.value
                    ProgressBar(p?.percentComplete?.let { (it / 100).toFloat() })
                    Muted(if (p != null && p.size > 0) "${p.bytesTransferred / 1024} / ${p.size / 1024} KB" else "Large shares can take a while.", size = 12.sp)
                }
            }
        }
        if (node != null) {
            item { GlassField(filter, { filter = it }, "Filter this folder", leading = R.drawable.ic_filter) }
            val subs = node.children.values.filter { it.name.contains(filter, true) }
            val files = node.files.filter { baseName(it.filename).contains(filter, true) }
            if (subs.isEmpty() && files.isEmpty()) item { Empty("Nothing here.", R.drawable.ic_folder) }
            if (node.allFiles().isNotEmpty()) item {
                GlassButton({ enqueue(node.allFiles()) }, Modifier.fillMaxWidth(), primary = true, height = 46.dp) {
                    GIcon(R.drawable.ic_download, size = 18.dp); Txt("Download this folder (${node.allFiles().size} files)", weight = FontWeight.SemiBold)
                }
            }
            items(subs, key = { it.full }) { s ->
                ItemCard(onClick = { path = path + s.name; filter = "" }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        GIcon(R.drawable.ic_folder, tint = c.accentB)
                        Column(Modifier.weight(1f)) {
                            Txt(s.name, size = 15.sp, weight = FontWeight.SemiBold, maxLines = 2)
                            Muted("${s.allFiles().size} files", size = 12.sp)
                        }
                        GlassIconButton(R.drawable.ic_download, "Download folder", { enqueue(s.allFiles()) }, size = 36.dp)
                    }
                }
            }
            items(files, key = { it.filename }) { f ->
                val on = f.filename in selected
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassToggle(on, { if (it) selected.add(f.filename) else selected.remove(f.filename) })
                    Column(Modifier.weight(1f)) {
                        Txt(baseName(f.filename), size = 14.sp, maxLines = 2)
                        Muted(fileMeta(f), size = 11.5.sp)
                    }
                    GlassIconButton(R.drawable.ic_download, "Download", { enqueue(listOf(f)) }, size = 34.dp)
                }
            }
            if (selected.isNotEmpty()) item {
                GlassButton({
                    val all = node.allFiles().filter { it.filename in selected }
                    enqueue(all); selected.clear()
                }, Modifier.fillMaxWidth(), primary = true) { GIcon(R.drawable.ic_download, size = 18.dp); Txt("Download ${selected.size} selected", weight = FontWeight.SemiBold) }
            }
        }
    }
}
