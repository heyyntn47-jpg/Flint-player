// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.player

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecList
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.flint.player.data.Prefs
import com.flint.player.util.Logger
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer
import java.io.File
import kotlin.concurrent.thread
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns the single shared libVLC instance. Creating it is the slow part of starting a video, so
 * it is built early on a background thread and handed to every [VlcEngine] that asks for it.
 */
object LibVlcProvider {
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var instance: LibVLC? = null
    private var builtSignature = ""
    private var engines = 0
    private var building = false
    private var lowEnd: Boolean? = null
    private var lowRam = false

    // Completes once libVLC exists (or failed to). Heavy background work waits for it, so on the
    // very first launch nothing competes with libVLC while it sets itself up.
    private val readyGate = CompletableDeferred<Unit>()

    /** Waits until libVLC is ready, but never longer than [timeoutMs]. */
    suspend fun awaitReady(timeoutMs: Long) {
        if (readyGate.isCompleted) return
        withTimeoutOrNull(timeoutMs) { readyGate.await() }
    }

    private fun signature() = "${Prefs.subtitleSize}-${Prefs.subtitleStyle}-${Prefs.subtitleColor}"

    /** Older or weaker phones get lighter decoding settings and a bigger buffer. */
    private fun detectLowEnd(context: Context): Boolean {
        lowEnd?.let { return it }
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        lowRam = am?.isLowRamDevice == true
        val cores = Runtime.getRuntime().availableProcessors()
        val memoryClass = am?.memoryClass ?: 256
        val result = lowRam || cores <= 2 || memoryClass <= 96 ||
                (cores <= 4 && Build.VERSION.SDK_INT <= 25)
        lowEnd = result
        return result
    }

    private fun baseOptions(low: Boolean): ArrayList<String> = arrayListOf(
        "--audio-time-stretch",
        "--network-caching=${if (low) 3000 else 2000}",
        // Local files: a little extra buffer smooths over slow storage
        "--file-caching=${if (low) 1500 else 1000}",
        // Things the player never shows, so libVLC doesn't spend time on them
        "--no-stats",
        "--no-osd",
        "--no-video-title-show"
    )

    // Nothing here lowers picture quality any more. These used to be added for every video on weak
    // phones: skipping the loop filter on all non-key frames (blocky picture), fast non-spec
    // shortcuts (artifacts) and 16-bit color (banding). They hit software decoding hardest, even
    // when the user had chosen it on purpose. A light version now applies per video, see
    // [softwareDecodeOptions].
    private fun tunedOptions(low: Boolean): ArrayList<String> = baseOptions(low)

    /** True on older or weaker phones (few cores, little memory, old Android). */
    fun isLowEnd(context: Context): Boolean = detectLowEnd(context.applicationContext)

    /**
     * Per-video options for software decoding. Full quality everywhere; weak phones only skip the
     * loop filter on non-reference frames, which is hard to see but saves a good amount of work.
     */
    fun softwareDecodeOptions(context: Context): List<String> =
        if (isLowEnd(context)) listOf(":avcodec-skiploopfilter=1") else emptyList()

    // Subtitle look comes from Settings. libVLC reads it once, when the instance is created.
    private fun styledOptions(low: Boolean, tuned: Boolean): ArrayList<String> {
        val options = if (tuned) tunedOptions(low) else baseOptions(low)
        val relativeSize = listOf(20, 16, 13, 10).getOrElse(Prefs.subtitleSize) { 16 }
        options.add("--freetype-rel-fontsize=$relativeSize")
        options.add("--freetype-color=${if (Prefs.subtitleColor == 1) 16776960 else 16777215}")
        when (Prefs.subtitleStyle) {
            1 -> {
                options.add("--freetype-outline-thickness=0")
                options.add("--freetype-shadow-opacity=255")
            }
            2 -> {
                options.add("--freetype-outline-thickness=0")
                options.add("--freetype-background-opacity=190")
                options.add("--freetype-background-color=0")
            }
            else -> options.add("--freetype-outline-thickness=4")
        }
        return options
    }

    private fun create(context: Context): LibVLC {
        val app = context.applicationContext
        val low = detectLowEnd(app)
        // libVLC refuses to start if it doesn't know an option, so step down one level at a
        // time. Playback always works, and the subtitle style is kept as long as possible.
        val attempts: List<() -> ArrayList<String>> = listOf(
            { styledOptions(low, tuned = true) },
            { styledOptions(low, tuned = false) },
            { baseOptions(low) },
            { arrayListOf("--audio-time-stretch") }
        )
        for ((level, build) in attempts.withIndex()) {
            val created = runCatching { LibVLC(app, build()) }
                .onFailure { Logger.warn("LibVlcProvider", "libVLC refused option set #$level", it) }
                .getOrNull()
            if (created != null) return created
        }
        return LibVLC(app, arrayListOf())
    }

    private fun ensureInstance(context: Context): LibVLC {
        val current = instance
        if (current != null && builtSignature != signature() && engines == 0) {
            runCatching { current.release() }
            instance = null
        }
        return instance ?: create(context).also {
            instance = it
            builtSignature = signature()
            readyGate.complete(Unit)
        }
    }

    /** Called by every new player. Counts it, so the instance isn't replaced while in use. */
    fun acquire(context: Context): LibVLC = synchronized(lock) {
        val ready = ensureInstance(context)
        engines++
        ready
    }

    fun engineReleased() {
        synchronized(lock) { if (engines > 0) engines-- }
    }

    /**
     * Builds the instance on a background thread so the UI never waits for it. Also asks Android
     * for its codec list once, which makes the first hardware decoder start noticeably faster.
     */
    fun warmUp(context: Context) {
        synchronized(lock) {
            if (instance != null || building) return
            building = true
        }
        val app = context.applicationContext
        // Straight after install libVLC has to set itself up from scratch, which takes longest.
        // Give that first run a normal-priority thread; later runs can stay out of the way.
        val marker = File(app.filesDir, "vlc_warm_done")
        val firstRun = !marker.exists()
        thread(name = "vlc-warmup") {
            runCatching {
                Process.setThreadPriority(
                    if (firstRun) Process.THREAD_PRIORITY_DEFAULT
                    else Process.THREAD_PRIORITY_LESS_FAVORABLE
                )
            }
            val built = runCatching { synchronized(lock) { ensureInstance(app) } }.getOrNull()
            if (built != null) {
                runCatching { MediaPlayer.Equalizer.getBandCount() }
                runCatching { marker.createNewFile() }
            }
            readyGate.complete(Unit)
            synchronized(lock) { building = false }
            runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos }
            // The first hardware decoder of each kind takes a noticeable moment to open.
            // Opening and closing one now means the first real video doesn't pay for it.
            for (type in listOf("video/avc", "video/hevc", "audio/mp4a-latm")) {
                runCatching { MediaCodec.createDecoderByType(type).release() }
            }
        }
    }

    fun scheduleWarmUp(context: Context) {
        main.postDelayed({ warmUp(context) }, 800)
    }

    /** Throws the instance away so the next one picks up changed settings. */
    fun reset(context: Context) {
        val app = context.applicationContext
        thread(name = "vlc-reset") {
            synchronized(lock) {
                if (engines == 0) {
                    instance?.let { runCatching { it.release() } }
                    instance = null
                }
            }
            warmUp(app)
        }
    }
}
