package com.linuxkernel44.slskdclient.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linuxkernel44.slskdclient.R
import com.linuxkernel44.slskdclient.data.EnqueueFile
import com.linuxkernel44.slskdclient.data.Transfer
import com.linuxkernel44.slskdclient.data.UserTransfers
import com.linuxkernel44.slskdclient.ui.AppVm
import com.linuxkernel44.slskdclient.ui.baseName
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
import com.linuxkernel44.slskdclient.ui.dirName
import com.linuxkernel44.slskdclient.ui.fmtBytes
import com.linuxkernel44.slskdclient.ui.fmtSpeed
import com.linuxkernel44.slskdclient.ui.rememberPoll
import com.linuxkernel44.slskdclient.ui.theme.LocalColors

private val FILTERS = listOf("All", "Active", "Queued", "Done", "Failed")

private fun matches(t: Transfer, filter: Int) = when (filter) {
    1 -> t.isActive
    2 -> t.isQueued
    3 -> t.isSucceeded
    4 -> t.isRetryable
    else -> true
}

@Composable
fun TransfersScreen(vm: AppVm) {
    val api = vm.api ?: return
    var direction by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val dir = if (direction == 0) "download" else "upload"
    val poll = rememberPoll(dir, intervalMs = 1500) { api.transfers(dir) }
    val groups = poll.value.orEmpty()
    val all = groups.flatMap { g -> g.directories.flatMap { it.files } }
    val speed = all.filter { it.isActive }.sumOf { it.averageSpeed }

    LazyColumn(contentPadding = pagePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Txt("Transfers", size = 28.sp, weight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
            GlassSegmented(listOf("Downloads", "Uploads"), direction, { direction = it; filter = 0 }, Modifier.fillMaxWidth())
        }
        item {
            GlassCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Stat("Active", all.count { it.isActive }.toString())
                        Stat("Queued", all.count { it.isQueued }.toString())
                        Stat("Done", all.count { it.isSucceeded }.toString())
                        Stat("Failed", all.count { it.isRetryable }.toString())
                        Stat("Speed", fmtSpeed(speed))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton({
                            vm.showSheet { ConfirmSheet("Clear completed?", "Removes every finished ${dir} from the list (files on disk stay).", "Clear", false, { vm.run("Cleared") { it.clearCompleted(dir) }; poll.refresh() }, { vm.closeSheet() }) }
                        }, Modifier.weight(1f), height = 42.dp, enabled = all.any { it.isCompleted }) { GIcon(R.drawable.ic_trash, size = 18.dp); Txt("Clear done", size = 13.sp, weight = FontWeight.Medium) }
                        if (direction == 0) GlassButton({
                            val failed = groups.flatMap { g -> g.directories.flatMap { d -> d.files.filter { it.isRetryable }.map { g.username to it } } }
                            retry(vm, failed); poll.refresh()
                        }, Modifier.weight(1f), height = 42.dp, enabled = all.any { it.isRetryable }) { GIcon(R.drawable.ic_refresh, size = 18.dp); Txt("Retry failed", size = 13.sp, weight = FontWeight.Medium) }
                    }
                }
            }
        }
        item { ChipTabs(FILTERS, filter, { filter = it }) }
        poll.error?.let { item { ErrorBanner(it, poll::refresh) } }
        if (poll.value == null && poll.error == null) item { Loading() }
        val visible = groups.filter { g -> g.directories.any { d -> d.files.any { matches(it, filter) } } }
        if (poll.value != null && visible.isEmpty()) item { Empty(if (all.isEmpty()) "No ${dir}s yet." else "Nothing in this filter.", R.drawable.ic_swap) }
        items(visible, key = { it.username }) { g -> UserGroup(vm, dir, g, filter, onChanged = poll::refresh) }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column { Txt(value, size = 15.sp, weight = FontWeight.SemiBold, maxLines = 1); Muted(label, size = 11.sp) }
}

private fun retry(vm: AppVm, items: List<Pair<String, Transfer>>) {
    items.groupBy({ it.first }, { it.second }).forEach { (user, files) ->
        vm.run("Retrying ${files.size} from $user") { it.enqueue(user, files.map { f -> EnqueueFile(f.filename, f.size) }) }
    }
}

@Composable
private fun UserGroup(vm: AppVm, dir: String, g: UserTransfers, filter: Int, onChanged: () -> Unit) {
    val c = LocalColors.current
    var open by rememberSaveable(dir + g.username) { mutableStateOf(true) }
    val files = g.directories.flatMap { it.files }
    ItemCard(onClick = { open = !open }) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            GIcon(if (open) R.drawable.ic_chevron_down else R.drawable.ic_chevron_right, size = 18.dp, tint = c.muted)
            Column(Modifier.weight(1f)) {
                Txt(g.username, size = 16.sp, weight = FontWeight.SemiBold, maxLines = 1)
                Muted("${files.size} file${if (files.size == 1) "" else "s"} · ${files.count { it.isSucceeded }} done · ${fmtBytes(files.sumOf { it.size })}", size = 12.sp)
            }
            GlassIconButton(R.drawable.ic_more, "User", {
                vm.showSheet {
                    MenuSheet(g.username, null, buildList {
                        add(MenuItem("Browse shares", R.drawable.ic_folder) { vm.openUser(g.username) })
                        add(MenuItem("Send message", R.drawable.ic_chat) { vm.openChat(g.username) })
                        if (files.any { it.isCancellable }) add(MenuItem("Cancel all", R.drawable.ic_stop) {
                            vm.run("Cancelled") { api -> files.filter { it.isCancellable }.forEach { api.cancelTransfer(dir, g.username, it.id, false) } }; onChanged()
                        })
                        if (files.any { it.isCompleted }) add(MenuItem("Remove finished", R.drawable.ic_trash) {
                            vm.run("Removed") { api -> files.filter { it.isCompleted }.forEach { api.cancelTransfer(dir, g.username, it.id, true) } }; onChanged()
                        })
                        if (dir == "download" && files.any { it.isRetryable }) add(MenuItem("Retry failed", R.drawable.ic_refresh) { retry(vm, files.filter { it.isRetryable }.map { g.username to it }); onChanged() })
                    }, { vm.closeSheet() })
                }
            }, size = 36.dp)
        }
        if (open) g.directories.forEach { d ->
            val shown = d.files.filter { matches(it, filter) }
            if (shown.isNotEmpty()) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GIcon(R.drawable.ic_folder, size = 15.dp, tint = c.accentB)
                    Txt(baseName(d.directory).ifBlank { "/" }, size = 12.5.sp, weight = FontWeight.SemiBold, color = c.muted, maxLines = 1)
                }
                shown.forEach { TransferRow(vm, dir, g.username, it, onChanged) }
            }
        }
    }
}

@Composable
private fun TransferRow(vm: AppVm, dir: String, user: String, t: Transfer, onChanged: () -> Unit) {
    val c = LocalColors.current
    val color: Color = when {
        t.isSucceeded -> c.ok
        t.isRetryable -> c.danger
        t.isActive -> c.accentB
        else -> c.muted
    }
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).let { m -> m },
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt(t.shortName, size = 13.5.sp, maxLines = 2)
                Muted(statusLine(t), size = 11.5.sp, maxLines = 2)
            }
            Badge(shortState(t), color)
            GlassIconButton(R.drawable.ic_more, "File", {
                vm.showSheet {
                    MenuSheet(t.shortName, t.state, buildList {
                        if (dir == "download" && t.isQueued) add(MenuItem("Queue position", R.drawable.ic_list) {
                            vm.run { api -> val p = api.queuePosition(user, t.id); vm.say(if (p == null) "Position unknown" else "Position $p in the remote queue") }
                        })
                        if (t.isCancellable) add(MenuItem("Cancel", R.drawable.ic_stop) { vm.run { it.cancelTransfer(dir, user, t.id, false) }; onChanged() })
                        if (dir == "download" && t.isRetryable) add(MenuItem("Retry", R.drawable.ic_refresh) { retry(vm, listOf(user to t)); onChanged() })
                        if (t.isCompleted) add(MenuItem("Remove from list", R.drawable.ic_trash) { vm.run { it.cancelTransfer(dir, user, t.id, true) }; onChanged() })
                        add(MenuItem("Browse $user", R.drawable.ic_folder) { vm.openUser(user) })
                    }, { vm.closeSheet() })
                }
            }, size = 32.dp)
        }
        if (t.isActive) ProgressBar((t.percentComplete / 100.0).toFloat())
    }
}

private fun shortState(t: Transfer) = when {
    t.isSucceeded -> "Done"
    t.state.contains("Cancelled") -> "Cancelled"
    t.state.contains("TimedOut") -> "Timed out"
    t.state.contains("Rejected") -> "Rejected"
    t.state.contains("Errored") -> "Error"
    t.state.contains("Remotely") -> "Queued·remote"
    t.state.contains("Locally") -> "Queued·local"
    t.isActive -> "%.0f%%".format(t.percentComplete)
    else -> t.state.substringBefore(',')
}

private fun statusLine(t: Transfer): String = buildList {
    add(if (t.isActive) "${fmtBytes(t.bytesTransferred)} / ${fmtBytes(t.size)}" else fmtBytes(t.size))
    if (t.isActive) { add(fmtSpeed(t.averageSpeed)); t.remainingTime?.takeIf { it.isNotBlank() }?.let { add("ETA ${it.substringBefore('.')}") } }
    if (t.isRetryable && !t.exception.isNullOrBlank()) add(t.exception.take(90))
    t.placeInQueue?.let { add("queue #$it") }
}.joinToString(" · ")
