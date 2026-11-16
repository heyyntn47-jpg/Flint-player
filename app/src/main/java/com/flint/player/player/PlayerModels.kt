// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.player

import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.RendererItem

/** Small shared types and limits used by the player engine and its screens. */

object Repeat {
    const val OFF = 0
    const val ONE = 1
    const val ALL = 2
}

class TrackItem(val id: Int, val name: String)

/** A TV or speaker found on the network that video can be cast to. */
class CastDevice(val id: String, val label: String, internal val item: RendererItem)

/** Equalizer limits and layout (libVLC uses 10 bands). */
const val EQ_MAX_DB = 20f
const val MAX_AUDIO_DELAY_MS = 10_000L
val EQ_BAND_COUNT: Int = runCatching { MediaPlayer.Equalizer.getBandCount() }.getOrDefault(10)

/** Furthest the subtitle delay can be pushed either way. */
const val MAX_SUBTITLE_DELAY_MS = 30_000L

