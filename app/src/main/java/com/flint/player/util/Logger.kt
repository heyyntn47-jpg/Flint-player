// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.util

import android.util.Log

/**
 * One place for diagnostics. Many calls in this app are wrapped in `runCatching` on purpose
 * (libVLC and system services can fail in odd ways), but failures should not vanish silently:
 * route them through here so they show up in logcat under the "Flint" tag.
 */
internal object Logger {
    private const val TAG = "Flint"

    fun warn(area: String, message: String, error: Throwable? = null) {
        Log.w(TAG, "[$area] $message", error)
    }
}
