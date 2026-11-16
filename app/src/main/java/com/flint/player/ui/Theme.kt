// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.flint.player.data.Prefs
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme

private val FlintShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

/** Used when haptic feedback is switched off in Settings. */
private object NoHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {}
}

/** True when the app is currently showing its dark theme. */
@Composable
fun isAppDark(): Boolean {
    val systemDark = isSystemInDarkTheme()
    return when (Prefs.themeMode) {
        1 -> false
        2 -> true
        else -> systemDark
    }
}

/*
 * Surface layers, from the back to the front (Material 3 tone steps):
 *   page          background                 the screen content sits on
 *   cards, rows   surfaceContainer           list items, settings groups
 *   bars          surfaceContainer           top bar and bottom bar, always the same color
 *   dialogs       surfaceContainerHigh       pop-ups and menus (they float over a dimmed scrim)
 *   fields, tracks surfaceContainerHighest   text fields, segmented controls, inactive tracks
 * Text uses onSurface (main) and onSurfaceVariant (secondary); accents use the primary family.
 * Every step is a different tone, so each layer stands out from the one behind it.
 */

/** Solid color of the top bar and the bottom bar. One value, so the two always match. */
@Composable
fun barColor(): Color {
    val scheme = MaterialTheme.colorScheme
    // Glass: a little more opaque than the cards, because list content scrolls under the floating bar
    return if (Prefs.glassUi) scheme.surfaceContainerHigh.copy(alpha = 0.80f) else scheme.surfaceContainer
}

/** Kept under this name for the screens that already use it. */
@Composable
fun topBarColor(): Color = barColor()

/** Bottom navigation bar: same color as the top bar. */
@Composable
fun navBarColor(): Color = barColor()

/** The page behind the lists: plain background color, no gradient. */
@Composable
fun pageColor(): Color = MaterialTheme.colorScheme.background

/**
 * Pure black (AMOLED) surfaces. Background and surface are true black; the container
 * tokens step up subtly (about #0A0A0A, #121212, #1A1A1A, #222222) with a faint tint
 * of the scheme's own primary, so cards, sheets and bars stay separated from the page.
 */
fun ColorScheme.toPureBlack(): ColorScheme {
    fun step(gray: Float): Color {
        val g = Color(gray, gray, gray)
        return lerp(g, primary, 0.04f)
    }
    return copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = step(0x0A / 255f),
        surfaceContainer = step(0x12 / 255f),
        surfaceContainerHigh = step(0x1A / 255f),
        surfaceContainerHighest = step(0x22 / 255f),
        surfaceBright = step(0x2A / 255f),
        surfaceVariant = step(0x1A / 255f),
        outlineVariant = lerp(Color(0xFF2C2C2C), primary, 0.08f)
    )
}

/*
 * Glass UI (Settings > Appearance > Interface style).
 * The same Material 3 scheme, but the surface layers are see-through and get a thin light
 * border, like the glass player controls. Every card, bar, dialog and menu reads these tokens,
 * so the whole app switches with no per-screen changes. The page itself stays opaque and gets a
 * soft color glow (see [pageBackground]) so there is something to see through to.
 */

/** Same scheme with translucent surface layers. Page ([ColorScheme.background]) stays solid. */
fun ColorScheme.toGlass(dark: Boolean): ColorScheme = copy(
    surfaceContainerLowest = surfaceContainerLowest.copy(alpha = 0.40f),
    surfaceContainerLow = surfaceContainerLow.copy(alpha = 0.50f),
    surfaceContainer = surfaceContainer.copy(alpha = 0.58f),
    surfaceContainerHigh = surfaceContainerHigh.copy(alpha = 0.74f),
    surfaceContainerHighest = surfaceContainerHighest.copy(alpha = 0.82f),
    // Doubles as the glass border color
    outlineVariant = (if (dark) Color.White else Color.Black).copy(alpha = if (dark) 0.18f else 0.12f)
)

/** Thin glass border for floating surfaces (dialogs, menus, bars). Null in the Material style. */
@Composable
fun glassBorder(): BorderStroke? =
    if (Prefs.glassUi) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null

/** Same border as a modifier, for cards drawn with `.clip(shape).background(...)`. No-op in Material. */
@Composable
fun Modifier.glassStroke(shape: Shape): Modifier =
    if (Prefs.glassUi) this.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape) else this

/**
 * The page behind every screen. Material: the plain background color. Glass: the same color with
 * soft glows of the theme colors in the corners, which the translucent cards and bars show through.
 */
@Composable
fun Modifier.pageBackground(): Modifier {
    val scheme = MaterialTheme.colorScheme
    val base = scheme.background
    if (!Prefs.glassUi) return this.background(base)

    val amoled = base == Color.Black
    val strength = when {
        amoled -> 0.16f
        isAppDark() -> 0.30f
        else -> 0.24f
    }
    val first = scheme.primary
    val second = scheme.tertiary
    val third = scheme.secondary
    return this
        .background(base)
        .drawBehind {
            val reach = maxOf(size.width, size.height)
            drawRect(
                Brush.radialGradient(
                    colors = listOf(first.copy(alpha = strength), Color.Transparent),
                    center = Offset(size.width * 0.08f, size.height * 0.04f),
                    radius = reach * 0.75f
                )
            )
            drawRect(
                Brush.radialGradient(
                    colors = listOf(second.copy(alpha = strength * 0.9f), Color.Transparent),
                    center = Offset(size.width * 0.95f, size.height * 0.55f),
                    radius = reach * 0.70f
                )
            )
            drawRect(
                Brush.radialGradient(
                    colors = listOf(third.copy(alpha = strength * 0.8f), Color.Transparent),
                    center = Offset(size.width * 0.20f, size.height * 1.02f),
                    radius = reach * 0.65f
                )
            )
        }
}

/**
 * Glass only: paints the bar color behind the top corners of the rounded page "pill". In the
 * Material style the whole Scaffold is bar colored, so the corners already match the bar. In glass
 * the Scaffold is transparent, so without this the cut-out corners show the bare page and the bar
 * looks cut off from the pill. Put it BEFORE `.clip(...)` so it is drawn outside the clip.
 */
@Composable
fun Modifier.barCornerBackdrop(radius: Dp = 36.dp): Modifier {
    if (!Prefs.glassUi) return this
    val color = barColor()
    return this.drawBehind {
        drawRect(color, size = Size(size.width, radius.toPx()))
    }
}

/** The seed-based Material 3 scheme (HCT, TonalSpot). Used by the theme and by the picker preview. */
@Composable
fun seedColorScheme(seed: Color, dark: Boolean, amoled: Boolean): ColorScheme {
    val base = rememberDynamicColorScheme(
        seedColor = seed,
        isDark = dark,
        isAmoled = amoled,
        style = PaletteStyle.TonalSpot
    )
    // Dark keeps the tonal (seed tinted) surfaces, like the wallpaper colors do
    return if (dark && amoled) base.toPureBlack() else base
}

@Composable
fun FlintTheme(content: @Composable () -> Unit) {
    val darkTheme = isAppDark()
    val context = LocalContext.current

    val useWallpaper = Build.VERSION.SDK_INT >= 31 && Prefs.dynamicColor
    val pureBlack = darkTheme && Prefs.pureBlack

    // Always composed (not only in one branch), and rebuilt whenever the saved color changes
    val customScheme = seedColorScheme(Color(Prefs.customColor), darkTheme, pureBlack)

    val scheme = if (useWallpaper) {
        val wallpaper = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        if (pureBlack) remember(wallpaper) { wallpaper.toPureBlack() } else wallpaper
    } else {
        customScheme
    }

    // Interface style: glass makes the surface layers see-through, Material keeps them solid
    val finalScheme = if (Prefs.glassUi) remember(scheme, darkTheme) { scheme.toGlass(darkTheme) } else scheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge to edge with see-through bars; the icons follow the theme
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= 29) {
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            }
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    val baseHaptics = LocalHapticFeedback.current
    val haptics = if (Prefs.hapticsEnabled) baseHaptics else NoHaptics

    CompositionLocalProvider(LocalHapticFeedback provides haptics) {
        MaterialTheme(
            colorScheme = finalScheme,
            shapes = FlintShapes,
            typography = FlintTypography,
            content = content
        )
    }
}
