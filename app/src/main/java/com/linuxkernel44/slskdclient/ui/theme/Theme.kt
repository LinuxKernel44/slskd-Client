package com.linuxkernel44.slskdclient.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.kyant.backdrop.Backdrop
import com.linuxkernel44.slskdclient.R

/** Aurora Glass palette (design A). Dark is the default; light is a milky glass over pale aurora. */
data class SlskdColors(
    val isDark: Boolean,
    val bg: Color,
    val text: Color,
    val muted: Color,
    val accentA: Color,
    val accentB: Color,
    val onAccent: Color,
    val glassTint: Color,
    val border: Color,
    val solid: Color,
    val field: Color,
    val danger: Color,
    val ok: Color,
    val blob1: Color,
    val blob2: Color,
    val blob3: Color,
)

val DarkColors = SlskdColors(
    isDark = true, bg = Color(0xFF060914), text = Color(0xFFEEF1FF), muted = Color(0xFFB4BAD6),
    accentA = Color(0xFF9FB0FF), accentB = Color(0xFF5EE7D6), onAccent = Color(0xFF04101C),
    glassTint = Color(0x14FFFFFF), border = Color(0x2BFFFFFF), solid = Color(0xFF151A30), field = Color(0x59050819),
    danger = Color(0xFFFF7A8A), ok = Color(0xFF5EF0B0),
    blob1 = Color(0xFF5E6EFF), blob2 = Color(0xFF22D3C5), blob3 = Color(0xFFB856FF),
)

val LightColors = SlskdColors(
    isDark = false, bg = Color(0xFFEFF1FB), text = Color(0xFF12142B), muted = Color(0xFF4F5475),
    accentA = Color(0xFF4B5CFF), accentB = Color(0xFF0FB5A6), onAccent = Color(0xFFFFFFFF),
    glassTint = Color(0x73FFFFFF), border = Color(0xB3FFFFFF), solid = Color(0xFFFFFFFF), field = Color(0x40FFFFFF),
    danger = Color(0xFFC62840), ok = Color(0xFF0E8A5F),
    blob1 = Color(0xFF9AA7FF), blob2 = Color(0xFF6FE3D6), blob3 = Color(0xFFD7A2FF),
)

data class GlassConfig(val enabled: Boolean = true, val intensity: Float = 0.7f)

val LocalColors = staticCompositionLocalOf { DarkColors }
val LocalGlass = staticCompositionLocalOf { GlassConfig() }
/** Backdrop used by cards and controls (the aurora only). */
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

val Sora = FontFamily(
    listOf(400, 500, 600, 700).map {
        Font(R.font.sora, weight = FontWeight(it), variationSettings = FontVariation.Settings(FontVariation.weight(it)))
    }
)

@Composable
fun SlskdTheme(theme: String, glass: GlassConfig, content: @Composable () -> Unit) {
    val dark = when (theme) { "light" -> false; "system" -> isSystemInDarkTheme(); else -> true }
    val view = LocalView.current
    SideEffect {
        (view.context as? android.app.Activity)?.window?.let { w ->
            WindowCompat.getInsetsController(w, view).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark }
        }
    }
    CompositionLocalProvider(LocalColors provides if (dark) DarkColors else LightColors, LocalGlass provides glass, content = content)
}
