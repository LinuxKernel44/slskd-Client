package com.linuxkernel44.slskdclient.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linuxkernel44.slskdclient.R
import com.linuxkernel44.slskdclient.player.PlayerUi
import com.linuxkernel44.slskdclient.ui.AppVm
import com.linuxkernel44.slskdclient.ui.components.GIcon
import com.linuxkernel44.slskdclient.ui.components.GlassCard
import com.linuxkernel44.slskdclient.ui.components.GlassIconButton
import com.linuxkernel44.slskdclient.ui.components.GlassLevel
import com.linuxkernel44.slskdclient.ui.components.GlassSlider
import com.linuxkernel44.slskdclient.ui.components.GradientThumb
import com.linuxkernel44.slskdclient.ui.components.Muted
import com.linuxkernel44.slskdclient.ui.components.Txt
import com.linuxkernel44.slskdclient.ui.components.cardShape
import com.linuxkernel44.slskdclient.ui.components.glass
import com.linuxkernel44.slskdclient.ui.components.pressable
import com.linuxkernel44.slskdclient.ui.fmtDuration
import com.linuxkernel44.slskdclient.ui.theme.LocalColors
import androidx.compose.ui.draw.clip

@Composable
fun MiniPlayer(vm: AppVm, ui: PlayerUi) {
    val c = LocalColors.current
    Row(
        Modifier.fillMaxWidth().glass(cardShape(28.dp), GlassLevel.Bar, tint = c.bg.copy(alpha = if (c.isDark) 0.38f else 0.25f))
            .pressable { vm.showPlayer = true }.padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        GradientThumb(ui.title, Modifier.size(42.dp).clip(cardShape(12.dp)), R.drawable.ic_music)
        Column(Modifier.weight(1f)) {
            Txt(ui.title.ifBlank { "Playing" }, size = 14.sp, weight = FontWeight.SemiBold, maxLines = 1)
            Muted(ui.error ?: ui.subtitle.ifBlank { fmtDuration(ui.positionMs / 1000.0) }, size = 12.sp, maxLines = 1)
        }
        GlassIconButton(if (ui.playing) R.drawable.ic_pause else R.drawable.ic_play, "Play/pause", { vm.player.toggle() }, size = 40.dp)
        GlassIconButton(R.drawable.ic_close, "Stop", { vm.player.stop() }, size = 40.dp)
    }
}

@Composable
fun PlayerSheet(vm: AppVm, ui: PlayerUi) {
    val c = LocalColors.current
    AnimatedVisibility(vm.showPlayer && ui.hasMedia, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
        Box(Modifier.fillMaxSize().padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp)
            .padding(horizontal = 10.dp)
            .glass(cardShape(36.dp), GlassLevel.Sheet, tint = c.bg.copy(alpha = if (c.isDark) 0.7f else 0.55f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(R.drawable.ic_chevron_down, "Close", { vm.showPlayer = false })
                    Txt("Now playing", Modifier.weight(1f), size = 16.sp, weight = FontWeight.SemiBold, align = androidx.compose.ui.text.style.TextAlign.Center)
                    GlassIconButton(R.drawable.ic_close, "Stop", { vm.player.stop(); vm.showPlayer = false })
                }
                GradientThumb(ui.title, Modifier.fillMaxWidth().padding(horizontal = 40.dp).aspectRatio(1f).clip(cardShape(32.dp)), R.drawable.ic_wave)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Txt(ui.title, size = 22.sp, weight = FontWeight.Bold, maxLines = 2)
                    Muted(ui.subtitle, size = 14.sp, maxLines = 1)
                    ui.error?.let { Txt(it, size = 12.sp, color = c.danger) }
                }
                GlassSlider(if (ui.durationMs > 0) (ui.positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f) else 0f,
                    { f -> if (ui.durationMs > 0) vm.player.seekTo((f * ui.durationMs).toLong()) }, Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Muted(fmtDuration(ui.positionMs / 1000.0).ifBlank { "0:00" }, size = 12.sp); Muted(fmtDuration(ui.durationMs / 1000.0).ifBlank { "–:––" }, size = 12.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(R.drawable.ic_prev, "Previous", { vm.player.previous() }, size = 52.dp)
                    GlassIconButton(if (ui.playing) R.drawable.ic_pause else R.drawable.ic_play, "Play/pause", { vm.player.toggle() }, size = 68.dp)
                    GlassIconButton(R.drawable.ic_next, "Next", { vm.player.next() }, size = 52.dp)
                }
                Muted("Queue · ${ui.count} track${if (ui.count == 1) "" else "s"}", size = 12.sp)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    itemsIndexed(ui.titles) { i, title ->
                        Row(Modifier.fillMaxWidth().clickable { vm.player.skipTo(i) }.padding(vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            GIcon(if (i == ui.index) R.drawable.ic_play else R.drawable.ic_music, size = 16.dp, tint = if (i == ui.index) c.accentB else c.muted)
                            Txt(title, size = 14.sp, weight = if (i == ui.index) FontWeight.SemiBold else FontWeight.Normal, color = if (i == ui.index) c.text else c.muted, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

