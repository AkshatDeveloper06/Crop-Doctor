package com.cropdoctor.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cropdoctor.app.knowledge.Bi
import com.cropdoctor.app.knowledge.Lang

object CdColors {
    val Green = Color(0xFF15803D)
    val GreenDark = Color(0xFF14532D)
    val GreenLight = Color(0xFFDCFCE7)
    val Amber = Color(0xFFB45309)
    val AmberLight = Color(0xFFFEF3C7)
    val Red = Color(0xFFB91C1C)
    val RedLight = Color(0xFFFEE2E2)
    val Ink = Color(0xFF0F172A)
    val Muted = Color(0xFF475569)
    val Surface = Color(0xFFFFFFFF)
    val Background = Color(0xFFF7FAF5)
    val Outline = Color(0xFFD7E0D2)
}

private val scheme = lightColorScheme(
    primary = CdColors.Green, onPrimary = Color.White,
    primaryContainer = CdColors.GreenLight, onPrimaryContainer = CdColors.GreenDark,
    secondary = CdColors.Amber, error = CdColors.Red,
    background = CdColors.Background, onBackground = CdColors.Ink,
    surface = CdColors.Surface, onSurface = CdColors.Ink,
    surfaceVariant = Color(0xFFEAF1E6), onSurfaceVariant = CdColors.Muted,
    outline = CdColors.Outline,
)

// Larger than default: readable in sunlight, for people who may not wear glasses outdoors.
private val type = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
)

val LocalLang = compositionLocalOf { Lang.EN }

/** Inline bilingual text: tr("Scan leaf", "पत्ती स्कैन करें"). */
@Composable
fun tr(en: String, hi: String): String = if (LocalLang.current == Lang.HI) hi else en

@Composable
fun Bi.t(): String = get(LocalLang.current)

@Composable
fun CropDoctorTheme(lang: Lang, content: @Composable () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val dark = isSystemInDarkTheme() // light only on purpose: best outdoors
    CompositionLocalProvider(LocalLang provides lang) {
        MaterialTheme(colorScheme = scheme, typography = type, content = content)
    }
}
