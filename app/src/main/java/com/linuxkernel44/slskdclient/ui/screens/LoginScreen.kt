package com.linuxkernel44.slskdclient.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linuxkernel44.slskdclient.R
import com.linuxkernel44.slskdclient.data.ApiClient
import com.linuxkernel44.slskdclient.data.Profile
import com.linuxkernel44.slskdclient.data.normalizeUrl
import com.linuxkernel44.slskdclient.ui.AppVm
import com.linuxkernel44.slskdclient.ui.AuthState
import com.linuxkernel44.slskdclient.ui.components.Badge
import com.linuxkernel44.slskdclient.ui.components.ConfirmSheet
import com.linuxkernel44.slskdclient.ui.components.GIcon
import com.linuxkernel44.slskdclient.ui.components.GlassButton
import com.linuxkernel44.slskdclient.ui.components.GlassCard
import com.linuxkernel44.slskdclient.ui.components.GlassField
import com.linuxkernel44.slskdclient.ui.components.GlassIconButton
import com.linuxkernel44.slskdclient.ui.components.GlassToggle
import com.linuxkernel44.slskdclient.ui.components.ItemCard
import com.linuxkernel44.slskdclient.ui.components.MenuItem
import com.linuxkernel44.slskdclient.ui.components.MenuSheet
import com.linuxkernel44.slskdclient.ui.components.Muted
import com.linuxkernel44.slskdclient.ui.components.SettingRow
import com.linuxkernel44.slskdclient.ui.components.Txt
import com.linuxkernel44.slskdclient.ui.components.pagePadding
import com.linuxkernel44.slskdclient.ui.theme.LocalColors
import kotlinx.coroutines.launch

/** Server profiles list, profile editor and the credentials prompt, shown whenever the app is not signed in. */
@Composable
fun LoginScreen(vm: AppVm, auth: AuthState) {
    val profiles by vm.graph.profiles.profiles.collectAsState()
    var editing by remember { mutableStateOf<Profile?>(null) }
    val needsLogin = auth as? AuthState.NeedsLogin
    val listMode = vm.showProfiles || (needsLogin == null && profiles.isNotEmpty())

    when {
        editing != null -> ProfileEditor(vm, editing!!, onClose = { editing = null })
        listMode -> ProfileList(vm, profiles, onEdit = { editing = it })
        needsLogin != null -> CredentialsForm(vm, needsLogin, onEdit = { editing = it }, onProfiles = { vm.showProfiles = true })
        else -> ProfileEditor(vm, Profile(), onClose = null)
    }
}

@Composable
private fun Hero(title: String, subtitle: String) {
    Column(Modifier.padding(top = 18.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        GIcon(R.drawable.ic_download, size = 38.dp, tint = LocalColors.current.accentB)
        Txt(title, size = 30.sp, weight = FontWeight.Bold)
        Muted(subtitle, size = 14.sp)
    }
}

@Composable
private fun ProfileList(vm: AppVm, profiles: List<Profile>, onEdit: (Profile) -> Unit) {
    val activeId = vm.graph.profiles.activeId
    LazyColumn(contentPadding = pagePadding(false), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Hero("Servers", "Choose the slskd instance to control.") }
        items(profiles.size) { i ->
            val p = profiles[i]
            ItemCard(onClick = { vm.switchProfile(p) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    GIcon(R.drawable.ic_server, tint = LocalColors.current.accentB)
                    Column(Modifier.weight(1f)) {
                        Txt(p.displayName, size = 16.sp, weight = FontWeight.SemiBold, maxLines = 1)
                        Muted(p.baseUrl + if (p.username.isNotBlank()) "  ·  ${p.username}" else "", maxLines = 1)
                    }
                    if (p.id == activeId) Badge("active")
                    GlassIconButton(R.drawable.ic_more, "Options", {
                        vm.showSheet {
                            MenuSheet(p.displayName, p.baseUrl, listOf(
                                MenuItem("Open", R.drawable.ic_play) { vm.switchProfile(p) },
                                MenuItem("Edit", R.drawable.ic_settings) { onEdit(p) },
                                MenuItem("Delete", R.drawable.ic_trash, danger = true) {
                                    vm.showSheet { ConfirmSheet("Delete server?", "Removes ${p.displayName} and its saved login from this phone.", "Delete", true, { vm.graph.profiles.delete(p.id) }, { vm.closeSheet() }) }
                                },
                            ), { vm.closeSheet() })
                        }
                    }, size = 38.dp)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton({ onEdit(Profile()) }, Modifier.weight(1f), primary = true) { GIcon(R.drawable.ic_add, size = 20.dp); Txt("Add server", weight = FontWeight.SemiBold) }
                if (vm.showProfiles && vm.auth is AuthState.Ready) GlassButton({ vm.showProfiles = false }, Modifier.weight(1f)) { Txt("Back", weight = FontWeight.Medium) }
            }
        }
    }
}

@Composable
private fun ProfileEditor(vm: AppVm, initial: Profile, onClose: (() -> Unit)?) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial.name) }
    var url by remember { mutableStateOf(initial.url) }
    var filesUrl by remember { mutableStateOf(initial.filesUrl) }
    var username by remember { mutableStateOf(initial.username) }
    var password by remember { mutableStateOf(initial.password) }
    var remember by remember { mutableStateOf(initial.rememberMe) }
    var selfSigned by remember { mutableStateOf(initial.allowSelfSigned) }
    var advanced by remember { mutableStateOf(initial.filesUrl.isNotBlank() || initial.allowSelfSigned) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var resultOk by remember { mutableStateOf(false) }

    fun draft() = initial.copy(name = name.trim(), url = normalizeUrl(url), filesUrl = filesUrl.trim(), username = username.trim(), rememberMe = remember, allowSelfSigned = selfSigned)

    LazyColumn(contentPadding = pagePadding(false), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Hero(if (initial.url.isBlank()) "Add a server" else "Edit server", "Your slskd address, e.g. http://10.0.0.8:15500") }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassField(name, { name = it }, "Name (optional)", leading = R.drawable.ic_tag)
                    GlassField(url, { url = it }, "Server URL", leading = R.drawable.ic_link, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    GlassField(username, { username = it }, "Username", leading = R.drawable.ic_person)
                    GlassField(password, { password = it }, "Password", leading = R.drawable.ic_lock, password = true)
                    SettingRow("Remember me", "Keep the login (encrypted on this phone)") { GlassToggle(remember, { remember = it }) }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Txt("Advanced", Modifier.weight(1f), size = 15.sp, weight = FontWeight.Medium)
                        GlassToggle(advanced, { advanced = it })
                    }
                    if (advanced) {
                        GlassField(filesUrl, { filesUrl = it }, "File server URL (default: same host, port 15501)", leading = R.drawable.ic_file, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                        SettingRow("Accept self-signed certificates", "Only enable for your own HTTPS server") { GlassToggle(selfSigned, { selfSigned = it }) }
                    }
                }
            }
        }
        if (result != null) item {
            GlassCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    GIcon(if (resultOk) R.drawable.ic_check else R.drawable.ic_info, tint = if (resultOk) LocalColors.current.ok else LocalColors.current.danger)
                    Txt(result.orEmpty(), size = 13.sp)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton({
                    testing = true; result = null
                    scope.launch {
                        try {
                            val d = draft()
                            val api = ApiClient(d, vm.graph.profiles)
                            val secured = api.isSecurityEnabled()
                            val files = api.filesServerAvailable()
                            resultOk = true
                            result = "slskd reachable · login ${if (secured) "required" else "not required"} · file server ${if (files) "found" else "not found (streaming/saving unavailable)"}"
                        } catch (e: Exception) { resultOk = false; result = vm.friendly(e) }
                        testing = false
                    }
                }, Modifier.weight(1f), enabled = url.isNotBlank() && !testing) { Txt(if (testing) "Testing…" else "Test", weight = FontWeight.Medium) }
                GlassButton({
                    val d = draft()
                    vm.graph.profiles.save(d)
                    if (username.isNotBlank() && password.isNotEmpty()) vm.signIn(d, username.trim(), password, remember) else vm.signIn(d)
                    onClose?.invoke()
                }, Modifier.weight(1f), primary = true, enabled = url.isNotBlank()) { Txt("Save & sign in", weight = FontWeight.SemiBold) }
            }
        }
        if (onClose != null) item { GlassButton(onClose, Modifier.fillMaxWidth()) { Txt("Cancel", weight = FontWeight.Medium) } }
    }
}

@Composable
private fun CredentialsForm(vm: AppVm, state: AuthState.NeedsLogin, onEdit: (Profile) -> Unit, onProfiles: () -> Unit) {
    val p = state.profile
    var username by remember(p.id) { mutableStateOf(p.username) }
    var password by remember(p.id) { mutableStateOf("") }
    var remember by remember(p.id) { mutableStateOf(p.rememberMe) }
    LazyColumn(contentPadding = pagePadding(false), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Hero("Sign in", p.displayName) }
        if (state.message != null) item {
            GlassCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    GIcon(R.drawable.ic_info, tint = LocalColors.current.danger)
                    Txt(state.message, size = 13.sp, color = LocalColors.current.danger)
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassField(username, { username = it }, "Username", leading = R.drawable.ic_person)
                    GlassField(password, { password = it }, "Password", leading = R.drawable.ic_lock, password = true)
                    SettingRow("Remember me", "Keep the login (encrypted on this phone)") { GlassToggle(remember, { remember = it }) }
                }
            }
        }
        item {
            GlassButton({ vm.signIn(p, username.trim(), password, remember) }, Modifier.fillMaxWidth(), primary = true, enabled = username.isNotBlank() && password.isNotEmpty()) {
                Txt("Sign in", weight = FontWeight.SemiBold)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton({ onEdit(p) }, Modifier.weight(1f)) { Txt("Edit server", weight = FontWeight.Medium) }
                GlassButton(onProfiles, Modifier.weight(1f)) { Txt("Servers", weight = FontWeight.Medium) }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}
