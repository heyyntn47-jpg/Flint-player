// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.runtime.Composable
import com.flint.player.data.Prefs

/**
 * The top space the app keeps clear for the top bar, decided only by the "hide status bar"
 * setting and never by whether the status bar happens to be showing at this moment.
 *
 * The real status-bar inset flips between 0 and its full height whenever the system briefly
 * shows the bar: when the keyboard opens, a permission or system dialog appears, or the window
 * loses focus. Padding the top bar by that live value made the title jump each time.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun stableTopInsets(): WindowInsets =
    if (Prefs.hideStatusBar) {
        WindowInsets.displayCutout.only(WindowInsetsSides.Top)
    } else {
        WindowInsets.statusBarsIgnoringVisibility.only(WindowInsetsSides.Top)
    }

/**
 * The bottom and side space the app keeps clear for the navigation bar and a notch. Like the top
 * one, it ignores whether the bars are showing right now, and it never includes the keyboard, so
 * opening the keyboard or a system dialog does not move or resize the screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun stableBottomInsets(): WindowInsets =
    WindowInsets.navigationBarsIgnoringVisibility.only(WindowInsetsSides.Bottom)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun stableBottomAndSideInsets(): WindowInsets =
    WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
