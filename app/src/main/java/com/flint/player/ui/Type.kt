// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.flint.player.R

/** Inter: all UI text. Tuned for screens, with a tall x-height that stays readable at small sizes. */
val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

/** Inter Display: the same design drawn for large sizes, with tighter spacing. Used for headlines. */
val InterDisplayFamily = FontFamily(
    Font(R.font.interdisplay_medium, FontWeight.Normal),
    Font(R.font.interdisplay_medium, FontWeight.Medium),
    Font(R.font.interdisplay_semibold, FontWeight.SemiBold),
    Font(R.font.interdisplay_semibold, FontWeight.Bold)
)

/** Poppins: geometric, only used for the Flint wordmark so it stands apart from the UI text. */
val WordmarkFamily = FontFamily(
    Font(R.font.poppins_medium, FontWeight.Medium)
)

/** The Flint wordmark in the home top bar. */
val WordmarkStyle = TextStyle(
    fontFamily = WordmarkFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 28.sp,
    lineHeight = 34.sp,
    letterSpacing = (-0.6).sp
)

/**
 * Material 3 type scale, set in Inter.
 * Display and headline styles use Inter Display with slightly negative tracking, which keeps big
 * text compact. Small text gets a little positive tracking so it stays open and legible.
 */
val FlintTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = InterDisplayFamily, fontWeight = FontWeight.Medium,
        fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-1.2).sp
    ),
    displayMedium = TextStyle(
        fontFamily = InterDisplayFamily, fontWeight = FontWeight.Medium,
        fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = (-0.9).sp
    ),
    displaySmall = TextStyle(
        fontFamily = InterDisplayFamily, fontWeight = FontWeight.Medium,
        fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-0.6).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = InterDisplayFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = InterDisplayFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = InterDisplayFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = InterDisplayFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = (-0.1).sp
    ),
    titleSmall = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    bodySmall = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp
    ),
    labelLarge = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp
    )
)

/**
 * Same-width digits, so a ticking time or a duration doesn't jitter as the numbers change.
 * Merge it into any style that shows time: `style = base.merge(TabularNumbers)`.
 */
val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")
