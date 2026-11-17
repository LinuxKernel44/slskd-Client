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
        0 -> FileManagerTab(vm)
        else -> SharesTab(vm)
    }
}

@Composable
internal fun LibraryHeader(vm: AppVm, title: String = "Library") {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Txt(title, size = 28.sp, weight = FontWeight.Bold)
        ChipTabs(listOf("Files", "Shares"), vm.libraryTab, { vm.libraryTab = it })
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
