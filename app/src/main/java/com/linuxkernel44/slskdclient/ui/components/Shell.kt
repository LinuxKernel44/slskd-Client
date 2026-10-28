package com.linuxkernel44.slskdclient.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
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

/** Christmas night: red, pine and gold light, snow-laden firs, falling snow and twinkling string lights; the glass controls refract this layer. */
private val Bulbs = listOf(Color(0xFFFF4D5E), Color(0xFFFFC94A), Color(0xFF46D98A), Color(0xFFFFF1D0))
private const val TAU = (2 * PI).toFloat()
private fun frac(v: Float) = v - floor(v)
private fun rnd(i: Int, s: Int) = frac(sin((i + 1) * 12.9898f + s * 78.233f) * 43758.5453f)
private fun hillY(h: Float, x: Float, base: Float, amp: Float, ph: Float) = h * (base - amp * (0.5f + 0.5f * sin(x * 4.2f + ph)))

private fun DrawScope.glow(color: Color, cx: Float, cy: Float, r: Float, a: Float) =
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = a), color.copy(alpha = 0f)), Offset(cx, cy), r), r, Offset(cx, cy))

private fun DrawScope.hill(base: Float, amp: Float, ph: Float, fill: Color) {
    val p = Path().apply {
        moveTo(0f, size.height)
        for (s in 0..48) { val x = s / 48f; lineTo(x * size.width, hillY(size.height, x, base, amp, ph)) }
        lineTo(size.width, size.height); close()
    }
    drawPath(p, fill)
}

/** One tiered fir: drooping, jagged boughs, a snow cap on each tier, optional string lights and a star. */
private fun DrawScope.fir(
    cx: Float, by: Float, hh: Float, ww: Float, seed: Int, fade: Float, deco: Boolean,
    u: Float, px: Float, g1: Color, g2: Color, snow: Float,
) {
    val n = 7; val tH = hh / (n * 0.68f + 0.32f)
    for (k in n - 1 downTo 0) {
        val yk = by - hh + k * tH * 0.68f
        val wk = ww / 2f * (0.26f + 0.74f * (k + 1) / n)
        val ex = FloatArray(11); val ey = FloatArray(11)
        for (s in 0..10) {
            val t = s / 10f; val j = rnd(seed * 31 + k, s); val d = 2 * t - 1
            ex[s] = cx - wk + 2 * wk * t + (j - 0.5f) * wk * 0.04f
            ey[s] = yk + tH + tH * 0.16f * d * d - (if (s % 2 == 1) tH * 0.09f * (0.4f + j) else 0f)
        }
        val body = Path().apply { moveTo(cx, yk); for (s in 0..10) lineTo(ex[s], ey[s]); close() }
        drawPath(body, Brush.verticalGradient(listOf(g1, g2), startY = yk, endY = yk + tH), alpha = fade)
        val cap = Path().apply {
            moveTo(cx, yk)
            for (s in 0..6) {
                val t = s / 6f; val d = 2 * t - 1
                lineTo(cx - wk * 0.56f + 1.12f * wk * t, yk + tH * (0.6f + (if (s % 2 == 1) 0.1f else -0.02f) + 0.1f * d * d))
            }
            close()
        }
        drawPath(cap, Color.White.copy(alpha = snow), alpha = fade)
        if (deco) for (idx in intArrayOf(2, 5, 8)) {
            val col = Bulbs[(k + idx) % 4]
            val al = 0.55f + 0.45f * sin(TAU * u * 4 + k * 2f + idx * 1.3f)
            val bx = ex[idx]; val bY = ey[idx] - tH * 0.06f
            glow(col, bx, bY, 9 * px, 0.5f * al)
            drawCircle(col.copy(alpha = 0.6f + 0.4f * al), 2.2f * px, Offset(bx, bY))
        }
    }
    if (deco) {
        val gold = Color(0xFFFFD25A); val ay = by - hh; val r = 7 * px
        glow(gold, cx, ay, 20 * px, 0.5f)
        val star = Path().apply {
            for (i in 0 until 10) {
                val a = (-PI / 2 + i * PI / 5).toFloat(); val rr = if (i % 2 == 1) r * 0.45f else r
                val x = cx + cos(a) * rr; val y = ay + sin(a) * rr
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(star, gold)
    }
}

@Composable
fun AuroraBackground(modifier: Modifier = Modifier, flakes: Int = 110, lights: Boolean = true, animated: Boolean = true) {
    val c = LocalColors.current
    val loop by rememberInfiniteTransition(label = "noel").animateFloat(
        0f, 1f, infiniteRepeatable(tween(40_000, easing = LinearEasing)), label = "u",
    )
    val u = if (animated) loop else 0.15f
    val g1 = if (c.isDark) Color(0xFF15603F) else Color(0xFF3D9A6C)
    val g2 = if (c.isDark) Color(0xFF06261B) else Color(0xFF1D6847)
    val cap = if (c.isDark) 0.85f else 0.97f
    Box(
        modifier.fillMaxSize().background(c.bg).drawBehind {
            val w = size.width; val h = size.height; val px = w / 300f
            val a = if (c.isDark) 0.85f else 0.8f
            glow(c.blob1, w * 0.05f, h * 0.02f, w * 1.15f, a)
            glow(c.blob2, w * 1.05f, h * 0.45f, w * 1.0f, a * 0.85f)
            glow(c.blob3, -w * 0.05f, h * 0.98f, w * 1.1f, a * 0.6f)

            hill(0.93f, 0.05f, 0.6f, Color.White.copy(alpha = if (c.isDark) 0.09f else 0.55f))
            floatArrayOf(0.07f, 0.3f, 0.5f, 0.71f, 0.93f).forEachIndexed { i, x ->
                val hh = h * (0.08f + 0.05f * rnd(i, 9))
                fir(x * w, hillY(h, x, 0.93f, 0.05f, 0.6f) + 0.006f * h, hh, hh * 0.5f, i + 1, 0.55f, false, u, px, g1, g2, cap)
            }
            fir(w * 0.17f, h * 0.955f, h * 0.36f, w * 0.4f, 11, 1f, true, u, px, g1, g2, cap)
            fir(w * 0.88f, h * 0.96f, h * 0.27f, w * 0.3f, 12, 1f, true, u, px, g1, g2, cap)
            hill(0.97f, 0.04f, 2.4f, Color.White.copy(alpha = if (c.isDark) 0.15f else 0.85f))

            for (i in 0 until flakes) {
                val l = i % 3; val spd = l + 1
                val y = frac(rnd(i, 1) + u * spd) * (h + 12 * px) - 6 * px
                val x = (rnd(i, 2) + 0.03f * sin(TAU * u * spd * 2 + rnd(i, 3) * TAU)) * w
                drawCircle(
                    Color.White.copy(alpha = floatArrayOf(0.45f, 0.65f, 0.9f)[l]),
                    floatArrayOf(1.1f, 1.9f, 3f)[l] * px, Offset(x, y),
                )
            }

            if (lights) {
                val top = h * 0.05f; val sag = h * 0.028f; val nb = 15
                fun wy(x: Float) = top + sag * sin(PI.toFloat() * frac(x * 3f))
                val wire = Path().apply {
                    for (s in 0..90) { val x = s / 90f; if (s == 0) moveTo(0f, wy(0f)) else lineTo(x * w, wy(x)) }
                }
                drawPath(wire, Color.Black.copy(alpha = if (c.isDark) 0.55f else 0.35f), style = Stroke(width = px.coerceAtLeast(1f)))
                for (j in 0 until nb) {
                    val x = (j + 0.5f) / nb; val col = Bulbs[j % 4]
                    val al = 0.55f + 0.45f * sin(TAU * u * 4 + j * 1.7f)
                    glow(col, x * w, wy(x) + 5 * px, 16 * px, 0.55f * al)
                    drawCircle(col.copy(alpha = 0.6f + 0.4f * al), 3.2f * px, Offset(x * w, wy(x) + 5 * px))
                }
            }
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
