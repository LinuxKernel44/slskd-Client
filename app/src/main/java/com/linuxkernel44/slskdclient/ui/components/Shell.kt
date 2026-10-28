package com.linuxkernel44.slskdclient.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.linuxkernel44.slskdclient.ui.theme.LocalBackdrop
import com.linuxkernel44.slskdclient.ui.theme.LocalColors
import com.linuxkernel44.slskdclient.ui.theme.LocalGlass

/** Three soft aurora lights on the midnight canvas; the glass controls refract this layer. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Box(
        modifier.fillMaxSize().background(c.bg).drawBehind {
            val w = size.width; val h = size.height
            fun blob(color: Color, cx: Float, cy: Float, r: Float, a: Float) =
                drawCircle(Brush.radialGradient(listOf(color.copy(alpha = a), color.copy(alpha = 0f)), Offset(cx, cy), r), r, Offset(cx, cy))
            val a = if (c.isDark) 0.95f else 0.75f
            blob(c.blob1, w * 0.05f, h * 0.02f, w * 1.15f, a)
            blob(c.blob2, w * 1.05f, h * 0.45f, w * 1.0f, a * 0.85f)
            blob(c.blob3, -w * 0.05f, h * 0.98f, w * 1.1f, a * 0.9f)
        },
    )
}

/**
 * Page + overlay layout. Cards sample the aurora layer; the floating bars (overlay) sample aurora and the
 * scrolling page together, so content visibly slides under the glass.
 */
@Composable
fun GlassHost(overlay: @Composable BoxScope.() -> Unit, page: @Composable BoxScope.() -> Unit) {
    val c = LocalColors.current
    val glass = LocalGlass.current
    val bg = rememberLayerBackdrop()
    val pageLayer = rememberLayerBackdrop()
    val both = rememberCombinedBackdrop(bg, pageLayer)
    Box(Modifier.fillMaxSize().background(c.bg)) {
        if (glass.enabled) Box(Modifier.fillMaxSize().layerBackdrop(bg)) { AuroraBackground() }
        else Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c.bg, c.solid.copy(alpha = 0.6f)))))
        CompositionLocalProvider(LocalBackdrop provides bg) {
            Box(Modifier.fillMaxSize().layerBackdrop(pageLayer), content = page)
        }
        // keeps the clock and icons legible when content scrolls under the transparent status bar
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth()
            .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 18.dp)
            .background(Brush.verticalGradient(listOf(c.bg.copy(alpha = 0.72f), Color.Transparent))))
        CompositionLocalProvider(LocalBackdrop provides both) { overlay() }
    }
}

data class NavItem(val label: String, val icon: Int)

@Composable
fun GlassNavBar(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val density = LocalDensity.current
    var widthPx by remember { mutableIntStateOf(0) }
    val itemW = with(density) { if (items.isEmpty()) 0.dp else (widthPx.toDp() - 12.dp) / items.size }
    val x by animateDpAsState(itemW * selected, spring(0.72f, 380f), label = "nav")
    Box(modifier.fillMaxWidth().height(68.dp).glass(CapsuleShape, GlassLevel.Bar, tint = c.bg.copy(alpha = if (c.isDark) 0.38f else 0.25f)).onSizeChanged { widthPx = it.width }.padding(6.dp)) {
        if (itemW > 0.dp) Box(Modifier.offset(x = x).width(itemW).fillMaxSize().glass(CapsuleShape, GlassLevel.Control, tint = c.accentA.copy(.38f)))
        Row(Modifier.fillMaxSize()) {
            items.forEachIndexed { i, item ->
                Column(
                    Modifier.weight(1f).fillMaxSize().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, role = Role.Tab) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement_center,
                ) {
                    GIcon(item.icon, size = 22.dp, tint = if (i == selected) c.text else c.muted)
                    Txt(item.label, size = 11.sp, weight = FontWeight.Medium, color = if (i == selected) c.text else c.muted, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

private val Arrangement_center = androidx.compose.foundation.layout.Arrangement.Center

/** Bottom sheet over a dimmed scrim. Render it inside GlassHost's overlay so it refracts the whole page. */
@Composable
fun GlassSheet(visible: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalColors.current
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(160))) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (c.isDark) 0.55f else 0.35f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDismiss))
        }
        AnimatedVisibility(
            visible, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(0.85f, 380f)) { it } + fadeIn(), exit = slideOutVertically(tween(200)) { it } + fadeOut(),
        ) {
            Column(
                modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(bottom = 10.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .glass(cardShape(36.dp), GlassLevel.Sheet, tint = c.bg.copy(alpha = if (c.isDark) 0.62f else 0.5f))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
                    .padding(horizontal = 18.dp).padding(top = 10.dp, bottom = 20.dp)
                    .heightIn(max = 720.dp).verticalScroll(rememberScrollState()),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).width(42.dp).height(5.dp).background(c.text.copy(alpha = 0.3f), CapsuleShape))
                Box(Modifier.height(14.dp))
                content()
            }
        }
    }
}

/** Padding that keeps content clear of the status bar and the floating nav bar. */
@Composable
fun pagePadding(bottomBar: Boolean = true) = androidx.compose.foundation.layout.PaddingValues(
    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
    bottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + if (bottomBar) 104.dp else 24.dp,
    start = 18.dp, end = 18.dp,
)
