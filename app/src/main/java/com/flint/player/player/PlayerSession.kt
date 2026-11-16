// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.player

/**
 * True while the video player is on screen. The player owns the system bars then, so the
 * activity must not re-show or re-hide them (that is what made the layout jump when the
 * keyboard or a system dialog appeared).
 */
object PlayerSession {
    @Volatile
    var active = false
}
