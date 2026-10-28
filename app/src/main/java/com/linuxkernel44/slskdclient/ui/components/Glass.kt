package com.linuxkernel44.slskdclient.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.linuxkernel44.slskdclient.ui.theme.LocalBackdrop
import com.linuxkernel44.slskdclient.ui.theme.LocalColors
import com.linuxkernel44.slskdclient.ui.theme.LocalGlass
import com.linuxkernel44.slskdclient.ui.theme.Sora

enum class GlassLevel(val blur: Float, val lensHeight: Float, val lensAmount: Float) {
    Card(10f, 0f, 0f),
    Control(3f, 10f, 20f),
    Bar(4f, 14f, 28f),
    Sheet(16f, 12f, 24f),
}

fun cardShape(r: Dp = 24.dp): Shape = RoundedRectangle(r)
val CapsuleShape: Shape get() = Capsule()

/**
 * The Liquid Glass surface. With glass on it samples the aurora backdrop (blur, vibrancy, lens refraction
 * on Android 13+); with glass off it is a plain solid panel, so every screen works either way.
 */
@Composable
fun Modifier.glass(shape: Shape, level: GlassLevel = GlassLevel.Card, tint: Color = Color.Unspecified): Modifier {
    val c = LocalColors.current
    val g = LocalGlass.current
    val backdrop = LocalBackdrop.current
    if (!g.enabled || backdrop == null) {
        return this.background(c.solid, shape).border(1.dp, c.border, shape)
    }
    val k = g.intensity.coerceIn(0.1f, 1f)
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur((level.blur * (0.5f + k)).dp.toPx())
            if (level.lensHeight > 0f) lens((level.lensHeight * k).dp.toPx(), (level.lensAmount * k).dp.toPx())
        },
        onDrawSurface = {
            drawRect(c.glassTint)
            if (tint.isSpecified) drawRect(tint)
        },
    )
}

@Composable
fun Txt(
    text: String, modifier: Modifier = Modifier, size: TextUnit = 14.sp, weight: FontWeight = FontWeight.Normal,
    color: Color = LocalColors.current.text, maxLines: Int = Int.MAX_VALUE, align: TextAlign = TextAlign.Start,
    lineHeight: TextUnit = TextUnit.Unspecified, letterSpacing: TextUnit = TextUnit.Unspecified, mono: Boolean = false,
) {
    BasicText(
        text, modifier,
        style = TextStyle(
            color = color, fontSize = size, fontWeight = weight, fontFamily = if (mono) androidx.compose.ui.text.font.FontFamily.Monospace else Sora,
            textAlign = align, lineHeight = lineHeight, letterSpacing = letterSpacing,
        ),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, size: TextUnit = 12.sp, maxLines: Int = Int.MAX_VALUE) =
    Txt(text, modifier, size = size, color = LocalColors.current.muted, maxLines = maxLines)

@Composable
fun GIcon(res: Int, modifier: Modifier = Modifier, size: Dp = 22.dp, tint: Color = LocalColors.current.text, description: String? = null) {
    Image(
        painterResource(res), description,
        modifier.size(size).let { if (description != null) it.semantics { contentDescription = description } else it },
        colorFilter = ColorFilter.tint(tint),
    )
}

/** Press-and-scale behaviour shared by every tappable glass control. */
@Composable
fun Modifier.pressable(enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(0.55f, 500f), label = "press")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.45f }
        .clickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = onClick)
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, radius: Dp = 24.dp, padding: Dp = 14.dp, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.glass(cardShape(radius), GlassLevel.Card).padding(padding), content = content)
}

@Composable
fun GlassButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true,
    height: Dp = 52.dp, content: @Composable RowScope.() -> Unit,
) {
    val c = LocalColors.current
    val shape = CapsuleShape
    val surface = if (primary) Modifier.glass(shape, GlassLevel.Control, tint = c.accentA.copy(alpha = 0.55f)).background(Brush.horizontalGradient(listOf(c.accentA.copy(.85f), c.accentB.copy(.85f))), shape)
    else Modifier.glass(shape, GlassLevel.Control)
    Row(
        modifier.then(surface).pressable(enabled, onClick = onClick).height(height).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically, content = content,
    )
}

@Composable
fun GlassIconButton(res: Int, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp, tint: Color = LocalColors.current.text) {
    Box(modifier.size(size).glass(CapsuleShape, GlassLevel.Control).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        GIcon(res, size = 20.dp, tint = tint, description = description)
    }
}

@Composable
fun GlassChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: Int? = null) {
    val c = LocalColors.current
    Row(
        modifier.height(38.dp).glass(CapsuleShape, GlassLevel.Control, tint = if (selected) c.accentA.copy(.45f) else Color.Unspecified)
            .pressable(onClick = onClick).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) GIcon(icon, size = 16.dp, tint = if (selected) c.text else c.muted)
        Txt(text, size = 13.sp, weight = FontWeight.Medium, color = if (selected) c.text else c.muted)
    }
}

/** Segmented control: a glass lens slides under the selected option. */
@Composable
fun GlassSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val density = LocalDensity.current
    var widthPx by remember { mutableIntStateOf(0) }
    val segW = with(density) { if (options.isEmpty()) 0.dp else (widthPx.toDp() - 8.dp) / options.size }
    val x by animateDpAsState(segW * selected, spring(0.7f, 420f), label = "seg")
    Box(modifier.height(48.dp).background(c.field, CapsuleShape).border(1.dp, c.border, CapsuleShape).onSizeChanged { widthPx = it.width }.padding(4.dp)) {
        if (segW > 0.dp) Box(Modifier.offset(x = x).width(segW).fillMaxSize().glass(CapsuleShape, GlassLevel.Control, tint = c.accentA.copy(.35f)))
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                Box(Modifier.weight(1f).fillMaxSize().clip(CapsuleShape).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, role = Role.Tab) { onSelect(i) }, contentAlignment = Alignment.Center) {
                    Txt(label, size = 14.sp, weight = FontWeight.Medium, color = if (i == selected) c.text else c.muted)
                }
            }
        }
    }
}

@Composable
fun GlassToggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val x by animateDpAsState(if (checked) 22.dp else 0.dp, spring(0.65f, 500f), label = "knob")
    Box(
        modifier.size(54.dp, 32.dp).clip(CapsuleShape)
            .background(if (checked) Brush.horizontalGradient(listOf(c.accentA, c.accentB)) else SolidColor(c.text.copy(alpha = 0.18f)))
            .semantics { role = Role.Switch }
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onChange(!checked) }
            .padding(3.dp),
    ) {
        Box(Modifier.offset(x = x).size(26.dp).glass(CapsuleShape, GlassLevel.Control, tint = Color.White.copy(.55f)))
    }
}

@Composable
fun GlassSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    var w by remember { mutableIntStateOf(1) }
    Box(
        modifier.height(36.dp).onSizeChanged { w = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> onChange((change.position.x / w).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) { detectTapGestures { onChange((it.x / w).coerceIn(0f, 1f)) } },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CapsuleShape).background(c.text.copy(alpha = 0.16f)))
        Box(Modifier.fillMaxWidth(value).height(6.dp).clip(CapsuleShape).background(Brush.horizontalGradient(listOf(c.accentA, c.accentB))))
        val knobX = with(LocalDensity.current) { (w * value).toDp() } - 15.dp
        Box(Modifier.offset(x = knobX.coerceAtLeast(0.dp)).size(30.dp, 22.dp).glass(CapsuleShape, GlassLevel.Control, tint = Color.White.copy(.55f)))
    }
}

@Composable
fun GlassField(
    value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, leading: Int? = null,
    password: Boolean = false, singleLine: Boolean = true, trailing: @Composable (RowScope.() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val c = LocalColors.current
    Row(
        modifier.height(54.dp).background(c.field, cardShape(18.dp)).border(1.dp, c.border, cardShape(18.dp)).padding(start = 14.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) GIcon(leading, tint = c.muted)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Txt(placeholder, size = 15.sp, color = c.muted.copy(alpha = 0.8f), maxLines = 1)
            BasicTextField(
                value, onChange, singleLine = singleLine, keyboardOptions = keyboardOptions,
                textStyle = TextStyle(color = c.text, fontSize = 15.sp, fontFamily = Sora),
                cursorBrush = SolidColor(c.accentB), visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        trailing?.invoke(this)
    }
}

@Composable
fun ProgressBar(fraction: Float?, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Box(modifier.fillMaxWidth().height(6.dp).clip(CapsuleShape).background(c.text.copy(alpha = 0.14f))) {
        val f = fraction ?: 0.35f
        Box(Modifier.fillMaxWidth(f.coerceIn(0.02f, 1f)).fillMaxSize().clip(CapsuleShape).background(Brush.horizontalGradient(listOf(c.accentA, c.accentB))))
    }
}

/** Deterministic gradient poster derived from a title, used until (or instead of) a real thumbnail. */
@Composable
fun GradientThumb(seed: String, modifier: Modifier = Modifier, icon: Int? = null) {
    val palette = listOf(
        0xFFFF8A6B to 0xFFFF3D77, 0xFF6EE7FF to 0xFF4D6BFF, 0xFFFFD36B to 0xFFFF8A3D, 0xFFB78BFF to 0xFF6B5BFF, 0xFF6BFFB8 to 0xFF2DA8FF,
    )
    val (a, b) = palette[(seed.hashCode() and 0x7fffffff) % palette.size]
    Box(modifier.background(Brush.linearGradient(listOf(Color(a), Color(b)))), contentAlignment = Alignment.Center) {
        if (icon != null) GIcon(icon, size = 22.dp, tint = Color.White.copy(alpha = 0.9f))
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Txt(text.uppercase(), modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp), size = 11.5.sp, weight = FontWeight.SemiBold,
        color = LocalColors.current.muted, letterSpacing = 1.sp)
}

@Composable
fun Row2(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) =
    Row(modifier.fillMaxWidth().widthIn(min = 0.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = content)

