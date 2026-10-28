package com.linuxkernel44.slskdclient.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linuxkernel44.slskdclient.R
import com.linuxkernel44.slskdclient.ui.theme.LocalColors

/** Scrolling page with the standard glass padding and a large title row. */
@Composable
fun Page(
    title: String, modifier: Modifier = Modifier, subtitle: String? = null, state: LazyListState = rememberLazyListState(),
    trailing: @Composable (androidx.compose.foundation.layout.RowScope.() -> Unit)? = null, bottomBar: Boolean = true, content: LazyListScope.() -> Unit,
) {
    LazyColumn(modifier.fillMaxSize(), state = state, contentPadding = pagePadding(bottomBar), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "__title") {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Txt(title, size = 28.sp, weight = FontWeight.Bold, maxLines = 1)
                    if (subtitle != null) Muted(subtitle, maxLines = 1)
                }
                if (trailing != null) trailing()
            }
        }
        content()
    }
}

@Composable
fun ChipTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
        items(labels.size) { i -> GlassChip(labels[i], i == selected, { onSelect(i) }) }
    }
}

@Composable
fun Empty(text: String, icon: Int = R.drawable.ic_info, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Column(modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GIcon(icon, size = 34.dp, tint = c.muted.copy(alpha = 0.7f))
        Muted(text, size = 14.sp)
    }
}

@Composable
fun ErrorBanner(text: String, onRetry: (() -> Unit)? = null) {
    val c = LocalColors.current
    GlassCard(Modifier.fillMaxWidth(), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GIcon(R.drawable.ic_info, tint = c.danger)
            Txt(text, Modifier.weight(1f), size = 13.sp, color = c.danger)
            if (onRetry != null) GlassIconButton(R.drawable.ic_refresh, "Retry", onRetry, size = 36.dp)
        }
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) { ProgressBar(null, Modifier.size(120.dp, 6.dp)) }
}

@Composable
fun Dot(color: Color, size: Int = 9) = Box(Modifier.size(size.dp).background(color, CircleShape))

@Composable
fun Badge(text: String, color: Color = LocalColors.current.accentA) {
    Txt(text, Modifier.background(color.copy(alpha = 0.22f), CapsuleShape).padding(horizontal = 9.dp, vertical = 3.dp), size = 11.sp, weight = FontWeight.SemiBold, color = LocalColors.current.text, maxLines = 1)
}

/** Tappable card row. */
@Composable
fun ItemCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val base = modifier.fillMaxWidth().glass(cardShape(20.dp), GlassLevel.Card)
    val clickable = when {
        onClick != null -> Modifier.pressable(onClick = onClick)
        else -> Modifier
    }
    Column(base.then(clickable).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
}

data class MenuItem(val label: String, val icon: Int, val danger: Boolean = false, val action: () -> Unit)

/** Body of a bottom sheet listing actions; closes the sheet before running the chosen one. */
@Composable
fun ColumnScope.MenuSheet(title: String, subtitle: String? = null, items: List<MenuItem>, onClose: () -> Unit) {
    val c = LocalColors.current
    Txt(title, size = 18.sp, weight = FontWeight.SemiBold, maxLines = 2)
    if (subtitle != null) Muted(subtitle, maxLines = 2)
    Spacer(Modifier.height(10.dp))
    items.forEach { item ->
        Row(
            Modifier.fillMaxWidth().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClose(); item.action() }.padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            GIcon(item.icon, tint = if (item.danger) c.danger else c.text)
            Txt(item.label, size = 16.sp, color = if (item.danger) c.danger else c.text)
        }
    }
}

@Composable
fun ColumnScope.ConfirmSheet(title: String, message: String, confirmLabel: String, danger: Boolean = false, onConfirm: () -> Unit, onClose: () -> Unit) {
    Txt(title, size = 18.sp, weight = FontWeight.SemiBold)
    Muted(message, Modifier.padding(top = 6.dp, bottom = 16.dp), size = 14.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassButton(onClose, Modifier.weight(1f)) { Txt("Cancel", weight = FontWeight.Medium) }
        GlassButton({ onClose(); onConfirm() }, Modifier.weight(1f), primary = !danger) {
            Txt(confirmLabel, weight = FontWeight.SemiBold, color = if (danger) LocalColors.current.danger else LocalColors.current.text)
        }
    }
}

@Composable
fun ColumnScope.PromptSheet(title: String, hint: String, initial: String = "", confirmLabel: String = "OK", singleLine: Boolean = true, onConfirm: (String) -> Unit, onClose: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    Txt(title, size = 18.sp, weight = FontWeight.SemiBold)
    Spacer(Modifier.height(12.dp))
    GlassField(text, { text = it }, hint, Modifier.fillMaxWidth(), singleLine = singleLine)
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassButton(onClose, Modifier.weight(1f)) { Txt("Cancel", weight = FontWeight.Medium) }
        GlassButton({ if (text.isNotBlank()) { onClose(); onConfirm(text.trim()) } }, Modifier.weight(1f), primary = true, enabled = text.isNotBlank()) {
            Txt(confirmLabel, weight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SettingRow(title: String, description: String? = null, modifier: Modifier = Modifier, trailing: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(title, size = 15.sp, weight = FontWeight.Medium)
            if (description != null) Muted(description, size = 12.sp)
        }
        trailing()
    }
}

@Composable
fun KeyValue(key: String, value: String, modifier: Modifier = Modifier, mono: Boolean = false) {
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Muted(key, Modifier.weight(0.42f), size = 13.sp)
        Txt(value, Modifier.weight(0.58f), size = 13.sp, mono = mono)
    }
}
