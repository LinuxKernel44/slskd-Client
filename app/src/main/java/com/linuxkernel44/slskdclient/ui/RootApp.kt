package com.linuxkernel44.slskdclient.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linuxkernel44.slskdclient.R
import com.linuxkernel44.slskdclient.ui.components.GIcon
import com.linuxkernel44.slskdclient.ui.components.GlassCard
import com.linuxkernel44.slskdclient.ui.components.GlassHost
import com.linuxkernel44.slskdclient.ui.components.GlassNavBar
import com.linuxkernel44.slskdclient.ui.components.GlassSheet
import com.linuxkernel44.slskdclient.ui.components.NavItem
import com.linuxkernel44.slskdclient.ui.components.Txt
import com.linuxkernel44.slskdclient.ui.screens.LibraryScreen
import com.linuxkernel44.slskdclient.ui.screens.LoginScreen
import com.linuxkernel44.slskdclient.ui.screens.MiniPlayer
import com.linuxkernel44.slskdclient.ui.screens.PlayerSheet
import com.linuxkernel44.slskdclient.ui.screens.SearchScreen
import com.linuxkernel44.slskdclient.ui.screens.SocialScreen
import com.linuxkernel44.slskdclient.ui.screens.SystemScreen
import com.linuxkernel44.slskdclient.ui.screens.TransfersScreen
import com.linuxkernel44.slskdclient.ui.theme.GlassConfig
import com.linuxkernel44.slskdclient.ui.theme.LocalColors
import com.linuxkernel44.slskdclient.ui.theme.SlskdTheme

private val NAV = listOf(
    NavItem("Search", R.drawable.ic_search),
    NavItem("Transfers", R.drawable.ic_swap),
    NavItem("Social", R.drawable.ic_chat),
    NavItem("Library", R.drawable.ic_folder),
    NavItem("System", R.drawable.ic_settings),
)

@Composable
fun RootApp(vm: AppVm) {
    val settings by vm.graph.settings.state.collectAsState()
    SlskdTheme(settings.theme, GlassConfig(settings.glass, settings.glassIntensity)) {
        val ready = vm.auth is AuthState.Ready && !vm.showProfiles
        val player by vm.player.ui.collectAsState()
        BackHandler(vm.sheet != null) { vm.closeSheet() }
        BackHandler(vm.showPlayer && vm.sheet == null) { vm.showPlayer = false }
        BackHandler(ready && vm.tab != Tabs.SEARCH && vm.sheet == null && !vm.showPlayer) { vm.tab = Tabs.SEARCH }
        GlassHost(
            overlay = {
                if (ready) {
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 14.dp)
                        .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (player.hasMedia) MiniPlayer(vm, player)
                        GlassNavBar(NAV, vm.tab, { vm.tab = it })
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    GlassSheet(vm.sheet != null, { vm.closeSheet() }) { vm.sheet?.invoke(this) }
                }
                Box(Modifier.fillMaxSize()) { PlayerSheet(vm, player) }
                Box(Modifier.align(Alignment.TopCenter).padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp).padding(horizontal = 16.dp)) {
                    AnimatedVisibility(vm.toast != null, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }) {
                        GlassCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                GIcon(R.drawable.ic_info, size = 18.dp, tint = LocalColors.current.accentB)
                                Txt(vm.toast.orEmpty(), size = 14.sp, weight = FontWeight.Medium)
                            }
                        }
                    }
                }
            },
            page = {
                when (val a = vm.auth) {
                    AuthState.Starting -> Box(Modifier.fillMaxSize(), Alignment.Center) { Txt("Connecting…", size = 16.sp, color = LocalColors.current.muted) }
                    else -> if (a is AuthState.Ready && !vm.showProfiles) {
                        when (vm.tab) {
                            Tabs.SEARCH -> SearchScreen(vm)
                            Tabs.TRANSFERS -> TransfersScreen(vm)
                            Tabs.SOCIAL -> SocialScreen(vm)
                            Tabs.LIBRARY -> LibraryScreen(vm)
                            else -> SystemScreen(vm)
                        }
                    } else LoginScreen(vm, a)
                }
            },
        )
    }
}
