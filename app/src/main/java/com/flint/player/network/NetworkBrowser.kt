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

class NetEntry(val name: String, val url: String, val isDirectory: Boolean)

/**
 * Lists the folders and videos on an SMB / FTP / SFTP server using libVLC's own browser, so
 * every protocol libVLC can read works here without extra code.
 */
@Stable
class NetworkBrowser(context: Context) {

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    var entries by mutableStateOf<List<NetEntry>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    /** True when the folder came back empty or the server didn't answer in time. */
    var emptyOrUnreachable by mutableStateOf(false)
        private set

    private val lock = Any()
    private var libVlc: LibVLC? = null
    private var browser: MediaBrowser? = null
    @Volatile
    private var released = false
    private var generation = 0
    private val found = ArrayList<NetEntry>()
    private var timeout: Runnable? = null
    private var relaxed = false

    /**
     * Lists [url]. [server] supplies the login, if the server needs one. [relaxedFilter] keeps
     * every item and uses its title as the name, for UPnP / DLNA where urls have no file name.
     */
    fun browse(url: String, server: NetworkServer?, relaxedFilter: Boolean = false) {
        val gen = ++generation
        relaxed = relaxedFilter
        loading = true
        emptyOrUnreachable = false
        entries = emptyList()
        found.clear()
        cancelTimeout()
        thread(name = "flint-net-browse") {
            val vlc = runCatching {
                synchronized(lock) {
                    if (released) return@thread
                    libVlc ?: LibVlcProvider.acquire(app).also { libVlc = it }
                }
            }.getOrNull()
            if (vlc == null) {
                main.post { if (gen == generation) finish(gen) }
                return@thread
            }
            main.post { if (gen == generation && !released) start(vlc, url, server, gen) }
        }
    }

    private fun start(vlc: LibVLC, url: String, server: NetworkServer?, gen: Int) {
        stopBrowser()
        val listener = object : MediaBrowser.EventListener {
            override fun onMediaAdded(index: Int, media: IMedia) {
                // Read everything we need right now; libVLC owns the object afterwards
                val raw = runCatching { media.uri?.toString() }.getOrNull() ?: return
                val directory = runCatching { media.type == IMedia.Type.Directory }.getOrDefault(false)
                val title = runCatching { media.getMeta(IMedia.Meta.Title) }.getOrNull()
                main.post { if (gen == generation) add(raw, directory, title) }
            }

            override fun onMediaRemoved(index: Int, media: IMedia) {}

            override fun onBrowseEnd() {
                main.post { if (gen == generation) finish(gen) }
            }
        }
        val created = runCatching { MediaBrowser(vlc, listener) }
            .onFailure { Logger.warn("NetworkBrowser", "Could not create the browser", it) }
            .getOrNull()
        if (created == null) {
            finish(gen)
            return
        }
        browser = created
        val target = if (server != null) NetworkAuth.inject(url, server) else url
        runCatching { created.browse(Uri.parse(target), 0) }
            .onFailure {
                Logger.warn("NetworkBrowser", "Browsing $url failed", it)
                finish(gen)
            }
        val limit = Runnable { if (gen == generation) finish(gen) }
        timeout = limit
        main.postDelayed(limit, TIMEOUT_MS)
    }

    private fun add(raw: String, directory: Boolean, title: String?) {
        val clean = NetworkAuth.strip(raw)
        val name = if (relaxed && !title.isNullOrBlank()) title
        else Uri.decode(clean.trimEnd('/').substringAfterLast('/'))
        if (name.isEmpty() || name.startsWith(".") || name.endsWith("$")) return
        if (!relaxed && !directory && extensionOf(name) !in MEDIA_EXTENSIONS) return
        if (found.any { it.url == clean }) return
        found.add(NetEntry(name, if (directory && !clean.endsWith("/")) "$clean/" else clean, directory))
    }

    private fun finish(gen: Int) {
        if (gen != generation) return
        cancelTimeout()
        entries = found.sortedWith(
            compareBy<NetEntry>({ !it.isDirectory }, { it.name.lowercase() })
        )
        emptyOrUnreachable = found.isEmpty()
        loading = false
    }

    private fun cancelTimeout() {
        timeout?.let { main.removeCallbacks(it) }
        timeout = null
    }

    private fun stopBrowser() {
        val old = browser ?: return
        browser = null
        thread(name = "flint-net-stop") {
            runCatching { old.release() }
        }
    }

    fun release() {
        released = true
        generation++
        cancelTimeout()
        stopBrowser()
        thread(name = "flint-net-release") {
            val owned = synchronized(lock) { libVlc.also { libVlc = null } }
            if (owned != null) LibVlcProvider.engineReleased()
        }
    }

    private fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()

    private companion object {
        const val TIMEOUT_MS = 20_000L

        val MEDIA_EXTENSIONS = setOf(
            // video
            "mp4", "m4v", "mkv", "webm", "avi", "mov", "wmv", "flv", "f4v", "mpg", "mpeg", "mpe",
            "ts", "m2ts", "mts", "vob", "3gp", "3g2", "ogv", "ogm", "divx", "asf", "rm", "rmvb",
            "iso", "m3u8", "mxf",
            // audio
            "mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "wma", "mka", "ac3", "dts"
        )
    }
}
