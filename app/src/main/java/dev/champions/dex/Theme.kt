package dev.champions.dex

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

val LocalDark = compositionLocalOf { false }
val LocalReduceMotion = compositionLocalOf { false }

/** Organic design-system tokens. Ramps are indexed by step: n(100)…n(900). */
@Immutable
class Palette(
    val bg: Color, val surface: Color, val text: Color, val accent: Color, val accent2: Color,
    private val neutral: List<Color>, private val accentRamp: List<Color>, private val accent2Ramp: List<Color>,
) {
    val divider get() = text.copy(alpha = .16f)
    fun n(step: Int) = neutral[step / 100 - 1]
    fun a(step: Int) = accentRamp[step / 100 - 1]
    fun g(step: Int) = accent2Ramp[step / 100 - 1]
}

private fun ramp(vararg hex: Long) = hex.map { Color(it) }
private val neutralLight = ramp(0xFFF9F4ED, 0xFFEEE7DB, 0xFFDCD3C4, 0xFFC0B6A5, 0xFFA19786, 0xFF82796A, 0xFF645C50, 0xFF474238, 0xFF2E2B25)
private val accentLight = ramp(0xFFFFF2EB, 0xFFFFE1D0, 0xFFFFC6A5, 0xFFF6A06B, 0xFFD67F48, 0xFFB2622D, 0xFF8C491A, 0xFF643312, 0xFF402310)
private val accent2Light = ramp(0xFFF0FAE1, 0xFFE1EECC, 0xFFCCDBB2, 0xFFAEBF92, 0xFF8FA073, 0xFF728157, 0xFF56633F, 0xFF3D472B, 0xFF272E1B)
private val LightPalette = Palette(Color(0xFFF5EAD8), Color(0xFFEBDDC5), Color(0xFF201E1D), Color(0xFFC67139), Color(0xFF7A8A5E),
    neutralLight, accentLight, accent2Light)
private val DarkPalette = Palette(Color(0xFF201E1D), Color(0xFF2E2B25), Color(0xFFF5EAD8), Color(0xFFC67139), Color(0xFF8FA073),
    ramp(0xFF2E2B25, 0xFF3A3630, 0xFF474238, 0xFF645C50, 0xFF82796A, 0xFFA19786, 0xFFC0B6A5, 0xFFDCD3C4, 0xFFF9F4ED),
    accentLight.reversed(), accent2Light.reversed())

val LocalPalette = compositionLocalOf { LightPalette }
val pal: Palette @Composable get() = LocalPalette.current

/** Caprasimo (headings) and Figtree (body) from the design system; CJK glyphs fall back to the system font. */
@OptIn(ExperimentalTextApi::class)
private fun figtree(weight: Int) = Font(R.font.figtree_variable, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
val HeadingFont: FontFamily = FontFamily(Font(R.font.caprasimo_regular, FontWeight.Normal))
val BodyFont: FontFamily = FontFamily(figtree(400), figtree(500), figtree(600), figtree(700))
val HeadingWeight = FontWeight.Normal

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = HeadingFont, fontWeight = HeadingWeight, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-.3).sp),
    headlineMedium = TextStyle(fontFamily = HeadingFont, fontWeight = HeadingWeight, fontSize = 26.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = HeadingFont, fontWeight = HeadingWeight, fontSize = 21.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = HeadingFont, fontWeight = HeadingWeight, fontSize = 20.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = HeadingFont, fontWeight = HeadingWeight, fontSize = 16.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 19.sp),
    bodyLarge = TextStyle(fontFamily = BodyFont, fontSize = 15.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = BodyFont, fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = BodyFont, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 17.sp),
    labelMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 15.sp),
    labelSmall = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 1.sp),
)

private fun scheme(p: Palette, dark: Boolean) = if (dark) darkColorScheme(
    primary = p.accent, onPrimary = p.bg, primaryContainer = p.a(200), onPrimaryContainer = p.a(900),
    secondary = p.accent2, onSecondary = p.bg, secondaryContainer = p.g(200), onSecondaryContainer = p.g(900),
    background = p.bg, onBackground = p.text, surface = p.bg, onSurface = p.text,
    surfaceVariant = p.surface, onSurfaceVariant = p.n(700), outline = p.n(500), outlineVariant = p.divider,
    surfaceContainerLowest = p.bg, surfaceContainerLow = p.bg, surfaceContainer = p.surface,
    surfaceContainerHigh = p.surface, surfaceContainerHighest = p.n(300), error = p.a(700),
) else lightColorScheme(
    primary = p.accent, onPrimary = p.bg, primaryContainer = p.a(200), onPrimaryContainer = p.a(900),
    secondary = p.accent2, onSecondary = p.bg, secondaryContainer = p.g(200), onSecondaryContainer = p.g(900),
    background = p.bg, onBackground = p.text, surface = p.bg, onSurface = p.text,
    surfaceVariant = p.surface, onSurfaceVariant = p.n(700), outline = p.n(500), outlineVariant = p.divider,
    surfaceContainerLowest = p.bg, surfaceContainerLow = p.bg, surfaceContainer = p.surface,
    surfaceContainerHigh = p.surface, surfaceContainerHighest = p.n(300), error = p.a(700),
)

@Composable
fun DexTheme(appearance: Appearance, reduceMotion: Boolean, content: @Composable () -> Unit) {
    val dark = when (appearance) { Appearance.SYSTEM -> isSystemInDarkTheme(); Appearance.LIGHT -> false; Appearance.DARK -> true }
    val palette = if (dark) DarkPalette else LightPalette
    val colors = remember(dark) { scheme(palette, dark) }
    CompositionLocalProvider(LocalDark provides dark, LocalReduceMotion provides reduceMotion, LocalPalette provides palette) {
        MaterialTheme(colorScheme = colors, typography = AppTypography) { CompositionLocalProvider(LocalContentColor provides palette.text, content = content) }
    }
}

// —— Type colours: one hue/chroma per type, toned in OKLCH like the design. ——

/** Hue and chroma per type, indexed like [TypeNames]. */
private val TypeHue = listOf(80 to .03, 28 to .12, 262 to .08, 320 to .10, 70 to .09, 80 to .07, 115 to .11, 295 to .10, 220 to .03,
    42 to .13, 245 to .10, 135 to .11, 95 to .13, 355 to .12, 205 to .08, 280 to .13, 45 to .04, 345 to .08)

private fun srgb(x: Double) = (if (x <= .0031308) 12.92 * x else 1.055 * x.pow(1 / 2.4) - .055).coerceIn(0.0, 1.0).toFloat()

private fun oklch(l: Double, c: Double, hDeg: Double): Color {
    val h = Math.toRadians(hDeg); val a = c * cos(h); val b = c * sin(h)
    val l1 = (l + .3963377774 * a + .2158037573 * b).pow(3)
    val m1 = (l - .1055613458 * a - .0638541728 * b).pow(3)
    val s1 = (l - .0894841775 * a - 1.2914855480 * b).pow(3)
    return Color(srgb(4.0767416621 * l1 - 3.3077115913 * m1 + .2309699292 * s1),
        srgb(-1.2684380046 * l1 + 2.6097574011 * m1 - .3413193965 * s1),
        srgb(-.0041960863 * l1 - .7034186147 * m1 + 1.7076147010 * s1))
}

@Immutable data class TypeTone(val bg: Color, val fg: Color, val solid: Color, val blob: Color)

private val toneLight = TypeHue.map { (h, c) -> TypeTone(oklch(.9, c * .5, h.toDouble()), oklch(.4, c, h.toDouble()), oklch(.68, c, h.toDouble()), oklch(.87, c * .55, h.toDouble())) }
private val toneDark = TypeHue.map { (h, c) -> TypeTone(oklch(.36, c * .6, h.toDouble()), oklch(.88, c * .75, h.toDouble()), oklch(.64, c, h.toDouble()), oklch(.33, c * .5, h.toDouble())) }

@Composable fun typeTone(type: Int): TypeTone = (if (LocalDark.current) toneDark else toneLight)[type]
