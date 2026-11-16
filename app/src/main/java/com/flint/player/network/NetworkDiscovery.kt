// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.network

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.flint.player.player.LibVlcProvider
import com.flint.player.util.Logger
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.MediaBrowser
import kotlin.concurrent.thread

class DiscoveredShare(val name: String, val url: String)

/**
 * Finds SMB, FTP, NFS and UPnP / DLNA servers on the local network using libVLC's own discovery,
 * the same one the VLC app uses for its "Local network" list.
 */
@Stable
class NetworkDiscovery(context: Context) {

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    var shares by mutableStateOf<List<DiscoveredShare>>(emptyList())
        private set
    var scanning by mutableStateOf(false)
        private set

    private val lock = Any()
    private var libVlc: LibVLC? = null
    private var browser: MediaBrowser? = null
    @Volatile
    private var released = false
    private var generation = 0
    private val found = ArrayList<DiscoveredShare>()
    private var timeout: Runnable? = null

    /** Starts (or restarts) a scan. It stops by itself after a short time to save battery. */
    fun scan() {
        val gen = ++generation
        scanning = true
        found.clear()
        shares = emptyList()
        cancelTimeout()
        thread(name = "flint-net-discover") {
            val vlc = runCatching {
                synchronized(lock) {
                    if (released) return@thread
                    libVlc ?: LibVlcProvider.acquire(app).also { libVlc = it }
                }
            }.getOrNull()
            if (vlc == null) {
                main.post { if (gen == generation) stop(gen) }
                return@thread
            }
            main.post { if (gen == generation && !released) start(vlc, gen) }
        }
    }

    private fun start(vlc: LibVLC, gen: Int) {
        stopBrowser()
        val listener = object : MediaBrowser.EventListener {
            override fun onMediaAdded(index: Int, media: IMedia) {
                val raw = runCatching { media.uri?.toString() }.getOrNull() ?: return
                val title = runCatching { media.getMeta(IMedia.Meta.Title) }.getOrNull()
                main.post { if (gen == generation) add(raw, title) }
            }

            override fun onMediaRemoved(index: Int, media: IMedia) {}

            override fun onBrowseEnd() {}
        }
        val created = runCatching { MediaBrowser(vlc, listener) }
            .onFailure { Logger.warn("NetworkDiscovery", "Could not create the browser", it) }
            .getOrNull()
        if (created == null) {
            stop(gen)
            return
        }
        browser = created
        runCatching { created.discoverNetworkShares() }
            .onFailure {
                Logger.warn("NetworkDiscovery", "Discovery failed", it)
                stop(gen)
            }
        val limit = Runnable { if (gen == generation) stop(gen) }
        timeout = limit
        main.postDelayed(limit, SCAN_MS)
    }

    private fun add(raw: String, title: String?) {
        val clean = NetworkAuth.strip(raw)
        if (found.any { it.url == clean }) return
        val name = title?.takeIf { it.isNotBlank() }
            ?: Uri.parse(clean).host
            ?: clean
        found.add(DiscoveredShare(name, clean))
        shares = found.sortedBy { it.name.lowercase() }
    }

    private fun stop(gen: Int) {
        if (gen != generation) return
        cancelTimeout()
        stopBrowser()
        scanning = false
    }

    private fun cancelTimeout() {
        timeout?.let { main.removeCallbacks(it) }
        timeout = null
    }

    private fun stopBrowser() {
        val old = browser ?: return
        browser = null
        thread(name = "flint-net-discover-stop") { runCatching { old.release() } }
    }

    fun release() {
        released = true
        generation++
        cancelTimeout()
        stopBrowser()
        thread(name = "flint-net-discover-release") {
            val owned = synchronized(lock) { libVlc.also { libVlc = null } }
            if (owned != null) LibVlcProvider.engineReleased()
        }
    }

    private companion object {
        const val SCAN_MS = 15_000L
    }
}
