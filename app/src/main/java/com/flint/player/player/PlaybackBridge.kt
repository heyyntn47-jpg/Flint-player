// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.player

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.flint.player.util.Logger

/** Connects the player screen to the background service. */
object PlaybackBridge {
    @Volatile var engine: VlcEngine? = null
    @Volatile var service: PlaybackService? = null

    fun attach(context: Context, engine: VlcEngine) {
        this.engine = engine
        val app = context.applicationContext
        // Starting a foreground service can be refused by the system; playback still works without it
        runCatching { ContextCompat.startForegroundService(app, Intent(app, PlaybackService::class.java)) }
            .onFailure { Logger.warn("PlaybackBridge", "Foreground service was refused", it) }
        refresh()
    }

    /** With [only] set, nothing happens unless that engine is the current one. */
    fun detach(context: Context, only: VlcEngine? = null) {
        if (only != null && engine !== only) return
        engine = null
        val app = context.applicationContext
        runCatching { app.stopService(Intent(app, PlaybackService::class.java)) }
    }

    fun refresh() {
        service?.update()
    }
}
