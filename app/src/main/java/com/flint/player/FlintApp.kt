// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player

import android.app.Application
import com.flint.player.data.FlintData
import com.flint.player.data.Prefs
import com.flint.player.network.NetworkStore
import com.flint.player.stream.StreamStore
import com.flint.player.player.LibVlcProvider

class FlintApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FlintData.init(this)
        Prefs.init(this)
        NetworkStore.init(this)
        StreamStore.init(this)
        // Start building libVLC right away, in the background, while the first screen loads.
        // By the time someone picks a video it is ready, even straight after install.
        LibVlcProvider.warmUp(this)
    }
}
