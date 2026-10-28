package com.linuxkernel44.slskdclient.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linuxkernel44.slskdclient.BuildConfig
import com.linuxkernel44.slskdclient.R
import com.linuxkernel44.slskdclient.data.AppEvent
import com.linuxkernel44.slskdclient.data.AppJson
import com.linuxkernel44.slskdclient.data.LogEntry
import com.linuxkernel44.slskdclient.data.hubEvents
import com.linuxkernel44.slskdclient.service.WatchService
import com.linuxkernel44.slskdclient.ui.AppVm
import com.linuxkernel44.slskdclient.ui.components.Badge
import com.linuxkernel44.slskdclient.ui.components.ChipTabs
import com.linuxkernel44.slskdclient.ui.components.ConfirmSheet
import com.linuxkernel44.slskdclient.ui.components.Empty
import com.linuxkernel44.slskdclient.ui.components.ErrorBanner
import com.linuxkernel44.slskdclient.ui.components.GIcon
import com.linuxkernel44.slskdclient.ui.components.GlassButton
import com.linuxkernel44.slskdclient.ui.components.GlassCard
import com.linuxkernel44.slskdclient.ui.components.GlassChip
import com.linuxkernel44.slskdclient.ui.components.GlassIconButton
import com.linuxkernel44.slskdclient.ui.components.GlassSegmented
import com.linuxkernel44.slskdclient.ui.components.GlassSlider
import com.linuxkernel44.slskdclient.ui.components.GlassToggle
import com.linuxkernel44.slskdclient.ui.components.ItemCard
import com.linuxkernel44.slskdclient.ui.components.KeyValue
import com.linuxkernel44.slskdclient.ui.components.Loading
import com.linuxkernel44.slskdclient.ui.components.Muted
import com.linuxkernel44.slskdclient.ui.components.ProgressBar
import com.linuxkernel44.slskdclient.ui.components.SectionLabel
import com.linuxkernel44.slskdclient.ui.components.SettingRow
import com.linuxkernel44.slskdclient.ui.components.Txt
import com.linuxkernel44.slskdclient.ui.components.pagePadding
import com.linuxkernel44.slskdclient.ui.copyToClipboard
import com.linuxkernel44.slskdclient.ui.fmtBytes
import com.linuxkernel44.slskdclient.ui.fmtDateTime
import com.linuxkernel44.slskdclient.ui.fmtSpeed
import com.linuxkernel44.slskdclient.ui.fmtTime
import com.linuxkernel44.slskdclient.ui.rememberPoll
import com.linuxkernel44.slskdclient.ui.theme.LocalColors
import kotlinx.coroutines.delay
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val SYSTEM_TABS = listOf("Overview", "Options", "Logs", "Events", "App")

@Composable
fun SystemScreen(vm: AppVm) {
    when (vm.systemTab) {
        0 -> OverviewTab(vm)
        1 -> OptionsTab(vm)
        2 -> LogsTab(vm)
        3 -> EventsTab(vm)
        else -> AppTab(vm)
    }
}

@Composable
private fun SystemHeader(vm: AppVm) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Txt("System", size = 28.sp, weight = FontWeight.Bold)
        ChipTabs(SYSTEM_TABS, vm.systemTab, { vm.systemTab = it })
    }
}

// ---------------- overview ----------------

@Composable
private fun OverviewTab(vm: AppVm) {
    val api = vm.api ?: return
    val c = LocalColors.current
    val state = rememberPoll(api, intervalMs = 3000) { api.appState() }
    val s = state.value
    LazyColumn(contentPadding = pagePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SystemHeader(vm) }
        state.error?.let { item { ErrorBanner(it, state::refresh) } }
        if (s == null && state.error == null) item { Loading() }
        if (s != null) {
            if (s.pendingRestart || s.pendingReconnect) item {
                GlassCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        GIcon(R.drawable.ic_info, tint = c.accentB)
                        Txt(if (s.pendingRestart) "A restart is needed to apply configuration changes." else "A reconnect is needed to apply configuration changes.", size = 13.sp)
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GIcon(R.drawable.ic_server, tint = if (s.server.isConnected && s.server.isLoggedIn) c.ok else c.danger)
                            Column(Modifier.weight(1f)) {
                                Txt("Soulseek server", size = 16.sp, weight = FontWeight.SemiBold)
                                Muted(s.server.state ?: "Unknown", size = 12.sp)
                            }
                            if (s.server.isConnected) GlassButton({
                                vm.showSheet { ConfirmSheet("Disconnect?", "slskd will leave the Soulseek network until you reconnect.", "Disconnect", true, { vm.run("Disconnected") { it.disconnectServer() }.invokeOnCompletion { state.refresh() } }, { vm.closeSheet() }) }
                            }, height = 40.dp) { Txt("Disconnect", size = 13.sp, weight = FontWeight.Medium) }
                            else GlassButton({ vm.run("Connecting…") { it.connectServer() }.invokeOnCompletion { state.refresh() } }, primary = true, height = 40.dp, enabled = !s.server.isConnecting) { Txt("Connect", size = 13.sp, weight = FontWeight.SemiBold) }
                        }
                        KeyValue("Address", s.server.address.orEmpty()); KeyValue("Endpoint", s.server.ipEndPoint.orEmpty(), mono = true)
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column {
                        Txt("Account", size = 16.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
                        KeyValue("Soulseek user", s.user.username.orEmpty())
                        KeyValue("Privileges", if (s.user.privileges.isPrivileged) "${s.user.privileges.privilegesRemaining}s remaining" else "none")
                        KeyValue("Average speed", fmtSpeed(s.user.statistics.averageSpeed))
                        KeyValue("Shared", "${s.user.statistics.fileCount} files · ${s.user.statistics.directoryCount} dirs")
                        KeyValue("Uploads served", s.user.statistics.uploadCount.toString())
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column {
                        Txt("slskd", size = 16.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
                        KeyValue("Version", s.version.current.orEmpty())
                        KeyValue("Latest", s.version.latest.orEmpty() + if (s.version.isUpdateAvailable) "  (update available)" else "")
                        KeyValue("Shares", "${s.shares.directories} dirs · ${s.shares.files} files" + if (s.shares.scanning) " · scanning" else "")
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column {
                        Txt("Distributed network", size = 16.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
                        KeyValue("Parent", s.distributedNetwork.parent ?: "none")
                        KeyValue("Branch", "level ${s.distributedNetwork.branchLevel} · root ${s.distributedNetwork.branchRoot ?: "–"}")
                        KeyValue("Children", "${s.distributedNetwork.children.size} / ${s.distributedNetwork.childLimit}")
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton({
                        vm.showSheet { ConfirmSheet("Restart slskd?", "The application restarts; the connection drops for a few seconds.", "Restart", true, { vm.run("Restarting…") { it.restartApp() } }, { vm.closeSheet() }) }
                    }, Modifier.weight(1f)) { GIcon(R.drawable.ic_refresh, size = 18.dp); Txt("Restart", weight = FontWeight.Medium) }
                    GlassButton({
                        vm.showSheet { ConfirmSheet("Shut down slskd?", "The service stops and cannot be started from this app.", "Shut down", true, { vm.run("Shutting down…") { it.shutdownApp() } }, { vm.closeSheet() }) }
                    }, Modifier.weight(1f)) { GIcon(R.drawable.ic_power, size = 18.dp, tint = c.danger); Txt("Shut down", weight = FontWeight.Medium, color = c.danger) }
                }
            }
        }
    }
}

// ---------------- options (YAML) ----------------

@Composable
private fun OptionsTab(vm: AppVm) {
    val api = vm.api ?: return
    var editing by rememberSaveable { mutableStateOf(false) }
    val yaml = rememberPoll(api) { api.optionsYaml() }
    val location = rememberPoll(api) { runCatching { api.optionsYamlLocation() }.getOrDefault("") }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val debug = rememberPoll(mode, enabled = mode == 1) { api.optionsDebug() }
    val ctx = LocalContext.current

    if (editing && yaml.value != null) {
        BackHandler { editing = false }
        YamlEditor(vm, yaml.value!!, onClose = { editing = false; yaml.refresh() })
        return
    }
    LazyColumn(contentPadding = pagePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SystemHeader(vm) }
        item { GlassSegmented(listOf("YAML file", "Effective (debug)"), mode, { mode = it }, Modifier.fillMaxWidth()) }
        location.value?.takeIf { it.isNotBlank() }?.let { loc -> item { Muted("File: $loc", size = 12.sp) } }
        val text = if (mode == 0) yaml.value else debug.value
        val err = if (mode == 0) yaml.error else debug.error
        err?.let { item { ErrorBanner(it, { yaml.refresh(); debug.refresh() }) } }
        if (text == null && err == null) item { Loading() }
        if (text != null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (mode == 0) GlassButton({ editing = true }, Modifier.weight(1f), primary = true, height = 46.dp) { GIcon(R.drawable.ic_code, size = 18.dp); Txt("Edit YAML", weight = FontWeight.SemiBold) }
                    GlassButton({ ctx.copyToClipboard("slskd options", text); vm.say("Copied") }, Modifier.weight(1f), height = 46.dp) { GIcon(R.drawable.ic_copy, size = 18.dp); Txt("Copy", weight = FontWeight.Medium) }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                    Txt(text.ifBlank { "(empty)" }, Modifier.horizontalScroll(rememberScrollState()), size = 12.sp, mono = true)
                }
            }
        }
    }
}

@Composable
private fun YamlEditor(vm: AppVm, original: String, onClose: () -> Unit) {
    val api = vm.api ?: return
    var text by remember { mutableStateOf(original) }
    var problem by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = pagePadding(false), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassIconButton(R.drawable.ic_chevron_left, "Back", onClose)
                Txt("Edit options", Modifier.weight(1f), size = 22.sp, weight = FontWeight.Bold)
            }
        }
        item {
            Muted("Saving restarts parts of slskd. A syntax check runs first; some changes need a restart afterwards.", size = 12.sp)
        }
        item {
            androidx.compose.foundation.text.BasicTextField(
                text, { text = it; problem = null },
                modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp).background(LocalColors.current.field, com.linuxkernel44.slskdclient.ui.components.cardShape(14.dp)).padding(12.dp),
                textStyle = androidx.compose.ui.text.TextStyle(color = LocalColors.current.text, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(LocalColors.current.accentB),
            )
        }
        problem?.let { item { ErrorBanner(it) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton({
                    busy = true
                    vm.run { a -> val r = a.validateYaml(text); problem = r; if (r == null) vm.say("YAML is valid") }.invokeOnCompletion { busy = false }
                }, Modifier.weight(1f), enabled = !busy) { Txt("Validate", weight = FontWeight.Medium) }
                GlassButton({
                    busy = true
                    vm.run { a ->
                        val r = a.validateYaml(text)
                        if (r != null) { problem = r } else { a.updateYaml(text); vm.say("Options saved"); onClose() }
                    }.invokeOnCompletion { busy = false }
                }, Modifier.weight(1f), primary = true, enabled = !busy && text != original) { Txt("Save", weight = FontWeight.SemiBold) }
            }
        }
    }
}

// ---------------- logs ----------------

private val LEVELS = listOf("All", "Info", "Warn", "Error")

@Composable
private fun LogsTab(vm: AppVm) {
    val api = vm.api ?: return
    var mode by rememberSaveable { mutableIntStateOf(0) }       // 0 live, 1 files
    var level by rememberSaveable { mutableIntStateOf(0) }
    val live = remember { mutableStateListOf<LogEntry>() }
    var status by remember { mutableStateOf("Connecting…") }
    var openFile by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(api, mode) {
        if (mode != 0) return@LaunchedEffect
        while (true) {
            try {
                status = "Connecting…"
                api.hubEvents("logs").collect { ev ->
                    status = "Live"
                    when (ev.target.lowercase()) {
                        "buffer" -> { live.clear(); live.addAll(ev.args.firstOrNull()?.let { runCatching { AppJson.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(LogEntry.serializer()), it) }.getOrNull() }.orEmpty().takeLast(300)) }
                        "log" -> ev.args.firstOrNull()?.let { runCatching { AppJson.decodeFromJsonElement(LogEntry.serializer(), it) }.getOrNull() }?.let { live.add(it); if (live.size > 500) live.removeAt(0) }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { status = "Disconnected: ${e.message ?: e.javaClass.simpleName}" }
            delay(3000)
        }
    }

    if (openFile != null) {
        BackHandler { openFile = null }
        LogFileView(vm, openFile!!, onBack = { openFile = null })
        return
    }
    val listState = rememberLazyListState()
    val shown = live.filter { levelOk(it.level, level) }
    LaunchedEffect(shown.size, mode) { if (mode == 0 && shown.isNotEmpty()) listState.scrollToItem(shown.size - 1) }

    if (mode == 0) {
        LazyColumn(state = listState, contentPadding = pagePadding(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { SystemHeader(vm) }
            item { GlassSegmented(listOf("Live", "Files"), mode, { mode = it }, Modifier.fillMaxWidth()) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LEVELS.forEachIndexed { i, l -> GlassChip(l, level == i, { level = i }) }
                Muted(status, size = 12.sp, modifier = Modifier.padding(start = 6.dp))
            } }
            if (shown.isEmpty()) item { Empty("Waiting for log lines…", R.drawable.ic_list) }
            items(shown.size) { i -> LogLine(shown[i]) }
        }
    } else {
        val files = rememberPoll(api) { api.logFiles().sortedByDescending { it.name } }
        LazyColumn(contentPadding = pagePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SystemHeader(vm) }
            item { GlassSegmented(listOf("Live", "Files"), mode, { mode = it }, Modifier.fillMaxWidth()) }
            files.error?.let { item { ErrorBanner(it, files::refresh) } }
            if (files.value == null && files.error == null) item { Loading() }
            items(files.value.orEmpty(), key = { it.name }) { f ->
                ItemCard(onClick = { openFile = f.name }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        GIcon(R.drawable.ic_file, tint = LocalColors.current.accentA)
                        Column(Modifier.weight(1f)) { Txt(f.name, size = 14.sp, maxLines = 1); Muted("${fmtBytes(f.length)} · ${fmtDateTime(f.modifiedAt)}", size = 12.sp) }
                    }
                }
            }
        }
    }
}

private fun levelOk(l: String, min: Int): Boolean {
    val n = when (l.lowercase()) { "debug", "verbose", "dbg", "vrb" -> 0; "information", "info", "inf" -> 1; "warning", "warn", "wrn" -> 2; "error", "err", "fatal", "ftl" -> 3; else -> 1 }
    return when (min) { 0 -> true; 1 -> n >= 1; 2 -> n >= 2; else -> n >= 3 }
}

@Composable
private fun LogLine(e: LogEntry) {
    val c = LocalColors.current
    val color = when (e.level.lowercase().take(3)) { "err", "ftl" -> c.danger; "war", "wrn" -> androidx.compose.ui.graphics.Color(0xFFFFC857); "dbg", "deb", "ver", "vrb" -> c.muted; else -> c.text }
    Column(Modifier.fillMaxWidth()) {
        Txt("${fmtTime(e.timestamp)}  ${e.level.take(3).uppercase()}${e.context?.let { "  " + it.substringAfterLast('.') } ?: ""}", size = 10.5.sp, color = c.muted, mono = true, maxLines = 1)
        Txt(e.message, size = 12.sp, color = color, mono = true)
    }
}

@Composable
private fun LogFileView(vm: AppVm, name: String, onBack: () -> Unit) {
    val api = vm.api ?: return
    val ctx = LocalContext.current
    val text = rememberPoll(name) { api.logFile(name).lines().takeLast(1500) }
    LazyColumn(contentPadding = pagePadding(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassIconButton(R.drawable.ic_chevron_left, "Back", onBack)
                Txt(name, Modifier.weight(1f), size = 17.sp, weight = FontWeight.Bold, maxLines = 1)
                GlassIconButton(R.drawable.ic_copy, "Copy", { ctx.copyToClipboard(name, text.value.orEmpty().joinToString("\n")); vm.say("Copied last ${text.value?.size ?: 0} lines") })
            }
        }
        text.error?.let { item { ErrorBanner(it, text::refresh) } }
        if (text.value == null && text.error == null) item { Loading() }
        item { Muted("Showing the last ${text.value?.size ?: 0} lines.", size = 12.sp) }
        items(text.value.orEmpty().size) { i -> Txt(text.value!![i], size = 11.sp, mono = true) }
    }
}

// ---------------- events ----------------

@Composable
private fun EventsTab(vm: AppVm) {
    val api = vm.api ?: return
    var limit by rememberSaveable { mutableIntStateOf(40) }
    val events = rememberPoll(limit, intervalMs = 10000) { api.events(0, limit) }
    val list = events.value?.first.orEmpty()
    val total = events.value?.second ?: 0
    LazyColumn(contentPadding = pagePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SystemHeader(vm) }
        events.error?.let { item { ErrorBanner(it, events::refresh) } }
        if (events.value == null && events.error == null) item { Loading() }
        if (events.value != null && list.isEmpty()) item { Empty("No events recorded.", R.drawable.ic_bolt) }
        items(list, key = { it.id }) { e -> EventCard(e) }
        if (total > list.size) item { GlassButton({ limit += 40 }, Modifier.fillMaxWidth()) { Txt("Load more (${total - list.size} older)", weight = FontWeight.Medium) } }
    }
}

@Composable
private fun EventCard(e: AppEvent) {
    var open by remember { mutableStateOf(false) }
    val summary = remember(e.data) {
        runCatching {
            val o = AppJson.parseToJsonElement(e.data).jsonObject
            val t = o["transfer"]?.jsonObject
            when {
                t != null -> "${t["username"]?.jsonPrimitive?.content}: ${t["filename"]?.jsonPrimitive?.content?.substringAfterLast('\\')}"
                else -> o.entries.firstOrNull { it.key != "version" }?.let { "${it.key}: ${it.value}" }.orEmpty()
            }
        }.getOrDefault(e.data.take(120))
    }
    ItemCard(onClick = { open = !open }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Badge(e.type)
            Muted(fmtDateTime(e.timestamp), size = 11.5.sp, modifier = Modifier.weight(1f))
        }
        Txt(summary, size = 13.sp, maxLines = if (open) Int.MAX_VALUE else 2)
        if (open) Txt(e.data, size = 11.sp, mono = true, color = LocalColors.current.muted)
    }
}

// ---------------- app settings ----------------

@Composable
private fun AppTab(vm: AppVm) {
    val ctx = LocalContext.current
    val settings by vm.graph.settings.state.collectAsState()
    val api = vm.api
    LazyColumn(contentPadding = pagePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SystemHeader(vm) }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column {
                    Txt("Server", size = 16.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
                    KeyValue("Profile", api?.profile?.displayName.orEmpty())
                    KeyValue("URL", api?.profile?.baseUrl.orEmpty(), mono = true)
                    KeyValue("File server", api?.profile?.filesBaseUrl.orEmpty(), mono = true)
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton({ vm.showProfiles = true }, Modifier.weight(1f), height = 44.dp) { GIcon(R.drawable.ic_server, size = 18.dp); Txt("Servers", weight = FontWeight.Medium) }
                        GlassButton({ vm.signOut() }, Modifier.weight(1f), height = 44.dp) { GIcon(R.drawable.ic_logout, size = 18.dp); Txt("Sign out", weight = FontWeight.Medium) }
                    }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column {
                    Txt("Appearance", size = 16.sp, weight = FontWeight.SemiBold)
                    GlassSegmented(listOf("Dark", "Light", "System"), listOf("dark", "light", "system").indexOf(settings.theme).coerceAtLeast(0),
                        { vm.graph.settings.update { s -> s.copy(theme = listOf("dark", "light", "system")[it]) } }, Modifier.fillMaxWidth().padding(top = 10.dp))
                    SettingRow("Liquid glass effect", "Blur and refraction (best on Android 13+)") { GlassToggle(settings.glass, { v -> vm.graph.settings.update { it.copy(glass = v) } }) }
                    if (settings.glass) {
                        Muted("Glass intensity", size = 12.sp)
                        GlassSlider(settings.glassIntensity, { v -> vm.graph.settings.update { it.copy(glassIntensity = v.coerceIn(0.1f, 1f)) } }, Modifier.fillMaxWidth())
                    }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column {
                    Txt("Notifications", size = 16.sp, weight = FontWeight.SemiBold)
                    SettingRow("Background connection", "Keeps watching your server with the app closed (shows a quiet notification)") {
                        GlassToggle(settings.backgroundService, { v -> vm.graph.settings.update { it.copy(backgroundService = v) }; WatchService.sync(ctx) })
                    }
                    SettingRow("Finished downloads") { GlassToggle(settings.notifyDownloads, { v -> vm.graph.settings.update { it.copy(notifyDownloads = v) }; WatchService.sync(ctx) }) }
                    SettingRow("Private messages") { GlassToggle(settings.notifyMessages, { v -> vm.graph.settings.update { it.copy(notifyMessages = v) }; WatchService.sync(ctx) }) }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Txt("About", size = 16.sp, weight = FontWeight.SemiBold)
                    KeyValue("Version", BuildConfig.VERSION_NAME)
                    KeyValue("Source", "github.com/LinuxKernel44/slskd-Client")
                    SettingRow("Check for updates on start") { GlassToggle(settings.checkUpdates, { v -> vm.graph.settings.update { it.copy(checkUpdates = v) } }) }
                    val u = vm.update
                    if (u != null) {
                        Txt("Version ${u.version} is available", weight = FontWeight.SemiBold, color = LocalColors.current.accentB)
                        if (u.notes.isNotBlank()) Muted(u.notes.take(400), size = 12.sp)
                        vm.updateProgress?.let { ProgressBar(it) }
                        GlassButton({ vm.installUpdate() }, Modifier.fillMaxWidth(), primary = true, enabled = vm.updateProgress == null) { GIcon(R.drawable.ic_download, size = 18.dp); Txt("Download & install", weight = FontWeight.SemiBold) }
                    } else GlassButton({ vm.checkForUpdate(false) }, Modifier.fillMaxWidth(), enabled = !vm.updateChecking, height = 46.dp) { Txt(if (vm.updateChecking) "Checking…" else "Check for updates", weight = FontWeight.Medium) }
                }
            }
        }
    }
}
