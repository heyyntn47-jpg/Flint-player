// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.flint.player.data.MediaEntry
import com.flint.player.data.Prefs
import com.flint.player.network.NetworkAuth
import com.flint.player.stream.StreamStore
import com.flint.player.network.NetworkStore
import com.flint.player.util.Logger
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.RendererDiscoverer
import org.videolan.libvlc.RendererItem
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.Locale
import kotlin.concurrent.thread

/** The playback engine: libVLC plus our own queue, repeat, shuffle and audio focus. */
@Stable
class VlcEngine(context: Context) {

    private val app = context.applicationContext
    private val libVlc: LibVLC = LibVlcProvider.acquire(app)
    private val mediaPlayer = MediaPlayer(libVlc)
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile
    private var released = false

    // ---------- What the screen shows ----------

    var isPlaying by mutableStateOf(false)
        private set
    var currentIndex by mutableIntStateOf(0)
        private set
    var title by mutableStateOf("")
        private set
    var repeatMode by mutableIntStateOf(Repeat.OFF)
        private set
    var shuffleOn by mutableStateOf(false)
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    var videoWidth by mutableIntStateOf(0)
        private set
    var videoHeight by mutableIntStateOf(0)
        private set
    var audioList by mutableStateOf<List<TrackItem>>(emptyList())
        private set
    var audioSelected by mutableIntStateOf(-1)
        private set
    var subtitleList by mutableStateOf<List<TrackItem>>(emptyList())
        private set
    var subtitleSelected by mutableIntStateOf(-1)
        private set
    /** Subtitle delay in milliseconds. Positive = subtitles appear later, negative = earlier. */
    var subtitleDelayMs by mutableLongStateOf(0L)
        private set
    var queueTitles by mutableStateOf<List<String>>(emptyList())
        private set

    // ---------- Audio mixer state ----------
    var eqEnabled by mutableStateOf(false)
        private set
    /** Pre-amp in dB. */
    var eqPreamp by mutableFloatStateOf(0f)
        private set
    /** One gain (dB) per equalizer band. */
    var eqBands by mutableStateOf(List(EQ_BAND_COUNT) { 0f })
        private set
    /** Index of the selected preset, or -1 for custom. */
    var eqPreset by mutableIntStateOf(-1)
        private set
    /** Audio delay in milliseconds. Positive = audio plays later. */
    var audioDelayMs by mutableLongStateOf(0L)
        private set
    /** Volume boost, 100 = normal, up to 200. */
    var boostPercent by mutableIntStateOf(100)
        private set

    var hasStarted = false
        private set

    /** True once the first video has really begun playing (state, so the screen can react). */
    var started by mutableStateOf(false)
        private set
    /** True if the first video could not be started at all. */
    var startFailed by mutableStateOf(false)
        private set
    /**
     * True once the video output exists, i.e. the first picture is on its way to the screen.
     * libVLC reports "Playing" before this, so "started" alone still shows a black surface.
     */
    var firstFrame by mutableStateOf(false)
        private set

    // ---------- Casting ----------

    /** Devices found while the Cast page is open. */
    var castDevices by mutableStateOf<List<CastDevice>>(emptyList())
        private set
    /** Name of the device the video is playing on, or null when it plays on this phone. */
    var castName by mutableStateOf<String?>(null)
        private set
    var castScanning by mutableStateOf(false)
        private set

    var sleepAtEnd = false
    var onError: ((String) -> Unit)? = null
    var onNotice: ((String) -> Unit)? = null
    var onSleepFired: (() -> Unit)? = null

    val currentUri: String? get() = items.getOrNull(currentIndex)?.uri

    // ---------- Internal state ----------

    private var items: List<MediaEntry> = emptyList()
    private var order: List<Int> = emptyList()
    private val main = Handler(Looper.getMainLooper())
    private var currentPfd: ParcelFileDescriptor? = null
    private var softwareFallback = false

    private companion object {
        /** Videos that hardware decoding could not play, for this session (Automatic only). */
        val needsSoftware = HashSet<String>()
    }
    private var ended = false
    private var failureStreak = 0
    private var savedVideoTrack = -1
    private var layout: VLCVideoLayout? = null
    private var focusGranted = false
    private var resumeOnFocus = false
    private var ducked = false
    private var volumeLevel = 100

    private var castItem: RendererItem? = null
    private val discoverers = ArrayList<RendererDiscoverer>()
    private var multicastLock: WifiManager.MulticastLock? = null
    @Volatile
    private var scanGeneration = 0

    private val listener = MediaPlayer.EventListener { event ->
        val type = event.type
        // libVLC calls this on its own thread, and starting or stopping playback from inside
        // the callback can deadlock it. Every event is handled on the main thread instead.
        val vouts = if (type == MediaPlayer.Event.Vout) event.voutCount else 0
        if (Looper.myLooper() == Looper.getMainLooper()) handleEvent(type, vouts)
        else main.post { handleEvent(type, vouts) }
    }

    private fun handleEvent(type: Int, voutCount: Int = 0) {
        if (released) return
        when (type) {
            MediaPlayer.Event.Playing -> {
                isPlaying = true
                ended = false
                hasStarted = true
                started = true
                failureStreak = 0
                if (speed != 1f) runCatching { mediaPlayer.setRate(speed) }
                if (subtitleDelayMs != 0L) applySubtitleDelay()
                if (audioDelayMs != 0L) applyAudioDelay()
                if (eqEnabled) applyEqualizer()
                refreshTracks()
                refreshVideoSize()
                PlaybackBridge.refresh()
            }
            MediaPlayer.Event.Paused -> {
                isPlaying = false
                PlaybackBridge.refresh()
            }
            MediaPlayer.Event.Stopped -> isPlaying = false
            MediaPlayer.Event.EndReached -> handleEnded()
            MediaPlayer.Event.EncounteredError -> handleError()
            MediaPlayer.Event.Vout -> {
                if (voutCount > 0) firstFrame = true
                refreshVideoSize()
            }
            MediaPlayer.Event.ESAdded,
            MediaPlayer.Event.ESDeleted,
            MediaPlayer.Event.ESSelected,
            MediaPlayer.Event.MediaChanged -> {
                refreshTracks()
                refreshVideoSize()
            }
        }
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocus = false
                // Focus is gone for good: forget it, so the next play() asks for it again
                focusGranted = false
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (isPlaying) {
                    resumeOnFocus = true
                    pause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                ducked = true
                applyVolume()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                ducked = false
                applyVolume()
                if (resumeOnFocus) {
                    resumeOnFocus = false
                    play()
                }
            }
        }
    }

    // Headphones unplugged
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (Prefs.pauseOnUnplug) pause()
        }
    }

    init {
        mediaPlayer.setEventListener(listener)
        runCatching { mediaPlayer.setVolume(100) }
        ContextCompat.registerReceiver(
            app,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }


    // ---------- Casting ----------

    private val rendererListener = RendererDiscoverer.EventListener { event ->
        val type = event.type
        val item = event.item
        main.post { handleRenderer(type, item) }
    }

    private fun handleRenderer(type: Int, item: RendererItem?) {
        if (released || item == null) return
        val id = item.name ?: return
        when (type) {
            RendererDiscoverer.Event.ItemAdded -> {
                if (castDevices.none { it.id == id }) {
                    val label = item.displayName?.takeIf { it.isNotBlank() } ?: id
                    castDevices = castDevices + CastDevice(id, label, item)
                }
            }
            RendererDiscoverer.Event.ItemDeleted -> {
                // Keep the device we are playing on, even if it stops answering for a moment
                if (castItem?.name != id) castDevices = castDevices.filterNot { it.id == id }
            }
        }
    }

    /** Starts looking for TVs and speakers. Call when the Cast page opens. */
    fun startCastScan() {
        if (released || castScanning) return
        castScanning = true
        val generation = ++scanGeneration
        // Many phones ignore multicast (how devices announce themselves) unless asked to listen
        runCatching {
            val wifi = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("video-player-cast")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        thread(name = "vlc-cast-scan") {
            val found = runCatching { RendererDiscoverer.list(libVlc) }.getOrNull().orEmpty()
            for (description in found) {
                val discoverer = runCatching { RendererDiscoverer(libVlc, description.name) }
                    .getOrNull() ?: continue
                discoverer.setEventListener(rendererListener)
                val started = runCatching { discoverer.start() }.getOrDefault(false)
                val keep = started && generation == scanGeneration && !released
                if (keep) {
                    synchronized(discoverers) { discoverers.add(discoverer) }
                } else {
                    runCatching { discoverer.setEventListener(null) }
                    runCatching { discoverer.stop() }
                    runCatching { discoverer.release() }
                }
            }
        }
    }

    /** Stops looking. Call when the Cast page closes, so Wi-Fi isn't kept busy. */
    fun stopCastScan() {
        scanGeneration++
        castScanning = false
        runCatching { multicastLock?.takeIf { it.isHeld }?.release() }
        multicastLock = null
        val running = synchronized(discoverers) { discoverers.toList().also { discoverers.clear() } }
        if (running.isEmpty()) return
        thread(name = "vlc-cast-stop") {
            for (discoverer in running) {
                runCatching { discoverer.setEventListener(null) }
                runCatching { discoverer.stop() }
                runCatching { discoverer.release() }
            }
        }
    }

    /** Plays on [device], or back on this phone when it is null. Keeps the same spot. */
    fun castTo(device: CastDevice?) {
        if (released) return
        val resumeAt = if (items.isEmpty() || ended) 0L else position()
        castItem = device?.item
        castName = device?.label
        runCatching { mediaPlayer.stop() }
        runCatching { mediaPlayer.setRenderer(castItem) }
        onNotice?.invoke(if (device != null) "Casting to ${device.label}" else "Playing on this phone")
        if (items.isNotEmpty()) open(currentIndex, resumeAt)
    }

    // ---------- Loading ----------

    fun load(entries: List<MediaEntry>, startIndex: Int, startPositionMs: Long) {
        if (entries.isEmpty()) return
        items = entries
        queueTitles = entries.map { it.title }
        val start = startIndex.coerceIn(0, entries.lastIndex)
        order = if (shuffleOn) shuffledOrder(start) else entries.indices.toList()
        open(start, startPositionMs)
    }

    private fun open(index: Int, startMs: Long, software: Boolean = false) {
        if (released) return
        val entry = items.getOrNull(index) ?: return

        currentIndex = index
        title = entry.title
        ended = false
        hasStarted = false
        firstFrame = false
        softwareFallback = software
        videoWidth = 0
        videoHeight = 0
        audioList = emptyList()
        subtitleList = emptyList()
        subtitleDelayMs = 0L
        audioDelayMs = 0L

        // The previous file stays open until the new media has replaced it. Closing it first
        // can pull the file out from under the decoder and cause read errors.
        val previousPfd = currentPfd
        currentPfd = null

        val uri = Uri.parse(entry.uri)
        val media: Media? = try {
            if (uri.scheme == "content") {
                val pfd = app.contentResolver.openFileDescriptor(uri, "r")
                if (pfd == null) {
                    null
                } else {
                    currentPfd = pfd
                    Media(libVlc, pfd.fileDescriptor)
                }
            } else {
                // smb:// ftp:// sftp:// get their saved login here; the stored url stays clean
                Media(libVlc, Uri.parse(NetworkAuth.playable(entry.uri, NetworkStore.servers)))
            }
        } catch (e: Exception) {
            Logger.warn("VlcEngine", "Could not open $uri", e)
            null
        }

        if (media == null) {
            closeQuietly(previousPfd)
            giveUp()
            return
        }

        // Decoding acceleration: 0 automatic (hardware, software when it can't play),
        // 1 disable (software only), 2 full (hardware only)
        val automatic = Prefs.decoderMode == 0
        val useSoftware = software || Prefs.decoderMode == 1 ||
                (automatic && entry.uri in needsSoftware)
        softwareFallback = useSoftware
        when {
            useSoftware -> {
                media.setHWDecoderEnabled(false, false)
                LibVlcProvider.softwareDecodeOptions(app).forEach { media.addOption(it) }
            }
            Prefs.decoderMode == 2 -> media.setHWDecoderEnabled(true, true)
            else -> media.setHWDecoderEnabled(true, false)
        }
        if (uri.scheme != "content") {
            NetworkAuth.options(entry.uri, NetworkStore.servers).forEach { media.addOption(it) }
            // Caching, user agent, referrer and so on chosen in the Stream menu
            StreamStore.mediaOptions(entry.uri).forEach { media.addOption(it) }
        }
        if (startMs > 3_000L) {
            media.addOption(":start-time=" + String.format(Locale.US, "%.3f", startMs / 1000.0))
        }

        mediaPlayer.setMedia(media)
        media.release()
        closeQuietly(previousPfd)
        requestFocus()
        mediaPlayer.play()
        PlaybackBridge.refresh()
    }

    /** Opens the current video again at the same spot (after changing the decoder). */
    fun reopenCurrent() {
        val pos = if (ended) 0L else position()
        open(currentIndex, pos)
    }

    private fun closeQuietly(pfd: ParcelFileDescriptor?) {
        runCatching { pfd?.close() }
    }

    // ---------- Transport ----------

    fun play() {
        if (released) return
        if (ended) {
            open(currentIndex, 0L)
            return
        }
        requestFocus()
        mediaPlayer.play()
    }

    fun pause() {
        if (released) return
        mediaPlayer.pause()
    }

    fun togglePlay() {
        if (isPlaying) pause() else play()
    }

    fun seekTo(ms: Long) {
        if (released) return
        if (ended) {
            open(currentIndex, ms)
            return
        }
        val length = duration()
        val target = if (length > 0L) ms.coerceIn(0L, length) else ms.coerceAtLeast(0L)
        mediaPlayer.setTime(target)
    }

    fun position(): Long {
        if (released) return 0L
        if (ended) return duration()
        return runCatching { mediaPlayer.time }.getOrDefault(0L).coerceAtLeast(0L)
    }

    fun duration(): Long {
        if (released) return 0L
        return runCatching { mediaPlayer.length }.getOrDefault(0L).coerceAtLeast(0L)
    }

    @JvmName("applySpeed")
    fun setSpeed(value: Float) {
        speed = value
        runCatching { mediaPlayer.setRate(value) }
    }

    /** 0 to 100. Used by the sleep timer's fade-out. */
    fun setVolumePercent(value: Int) {
        volumeLevel = value.coerceIn(0, 100)
        applyVolume()
    }

    private fun applyVolume() {
        val boosted = volumeLevel * boostPercent / 100
        val level = if (ducked) boosted * 30 / 100 else boosted
        runCatching { mediaPlayer.setVolume(level.coerceIn(0, 200)) }
    }

    // ---------- Queue ----------

    private fun orderPosition(): Int = order.indexOf(currentIndex).coerceAtLeast(0)

    private fun shuffledOrder(first: Int): List<Int> =
        listOf(first) + items.indices.filter { it != first }.shuffled()

    fun hasNext(): Boolean =
        items.size > 1 && (orderPosition() + 1 < order.size || repeatMode == Repeat.ALL)

    fun hasPrevious(): Boolean =
        items.size > 1 && (orderPosition() > 0 || repeatMode == Repeat.ALL)

    fun next() {
        val pos = orderPosition()
        when {
            pos + 1 < order.size -> open(order[pos + 1], 0L)
            repeatMode == Repeat.ALL && order.isNotEmpty() -> open(order[0], 0L)
        }
    }

    fun previous() {
        if (position() > 3_000L || items.size <= 1) {
            seekTo(0L)
            return
        }
        val pos = orderPosition()
        when {
            pos > 0 -> open(order[pos - 1], 0L)
            repeatMode == Repeat.ALL && order.isNotEmpty() -> open(order.last(), 0L)
            else -> seekTo(0L)
        }
    }

    fun jumpTo(index: Int) {
        if (index in items.indices) open(index, 0L)
    }

    fun setRepeat(mode: Int) {
        repeatMode = mode
    }

    fun setShuffle(on: Boolean) {
        shuffleOn = on
        order = if (on) shuffledOrder(currentIndex) else items.indices.toList()
    }

    private fun handleEnded() {
        ended = true
        isPlaying = false
        when {
            sleepAtEnd -> {
                sleepAtEnd = false
                onSleepFired?.invoke()
            }
            repeatMode == Repeat.ONE -> open(currentIndex, 0L)
            hasNext() -> next()
            else -> PlaybackBridge.refresh()
        }
    }

    private fun handleError() {
        isPlaying = false
        // Hardware decoders fail on some files (10-bit, odd profiles, weak chips). Before giving
        // up, try the same video again from the same spot with the software decoder. Automatic
        // remembers it, so this video goes straight to software the next time it is opened.
        if (!softwareFallback && Prefs.decoderMode == 0 && items.isNotEmpty()) {
            val resumeAt = if (ended) 0L else position()
            currentUri?.let { needsSoftware.add(it) }
            onNotice?.invoke("Hardware decoder failed, trying software")
            open(currentIndex, resumeAt, software = true)
            return
        }
        giveUp()
    }

    private fun giveUp() {
        isPlaying = false
        startFailed = true
        failureStreak++
        onError?.invoke("Can't play this video")
        // In a playlist, skip a broken item (but never loop forever)
        if (failureStreak <= items.size && hasNext()) next()
    }

    // ---------- Tracks and subtitles ----------

    private fun refreshTracks() {
        audioList = runCatching {
            mediaPlayer.audioTracks
                ?.filter { it.id >= 0 }
                ?.map { TrackItem(it.id, it.name ?: "Track ${it.id}") }
        }.getOrNull().orEmpty()
        audioSelected = runCatching { mediaPlayer.audioTrack }.getOrDefault(-1)

        subtitleList = runCatching {
            mediaPlayer.spuTracks
                ?.filter { it.id >= 0 }
                ?.map { TrackItem(it.id, it.name ?: "Subtitle ${it.id}") }
        }.getOrNull().orEmpty()
        subtitleSelected = runCatching { mediaPlayer.spuTrack }.getOrDefault(-1)
    }

    private fun refreshVideoSize() {
        val track = runCatching { mediaPlayer.currentVideoTrack }.getOrNull() ?: return
        if (track.width > 0 && track.height > 0) {
            videoWidth = track.width
            videoHeight = track.height
        }
    }

    fun selectAudio(id: Int) {
        runCatching { mediaPlayer.setAudioTrack(id) }
        refreshTracks()
    }

    /** Pass -1 to turn subtitles off. */
    fun selectSubtitle(id: Int) {
        runCatching { mediaPlayer.setSpuTrack(id) }
        refreshTracks()
    }

    /** Shifts subtitle timing. Positive values show subtitles later, negative values earlier. */
    fun setSubtitleDelay(ms: Long) {
        subtitleDelayMs = ms.coerceIn(-MAX_SUBTITLE_DELAY_MS, MAX_SUBTITLE_DELAY_MS)
        applySubtitleDelay()
    }

    private fun applySubtitleDelay() {
        // libVLC takes microseconds
        runCatching { mediaPlayer.setSpuDelay(subtitleDelayMs * 1000L) }
    }

    fun addSubtitle(uri: Uri): Boolean =
        runCatching { mediaPlayer.addSlave(IMedia.Slave.Type.Subtitle, uri, true) }
            .getOrDefault(false)

    // ---------- Audio mixer ----------

    val eqPresetNames: List<String> by lazy {
        runCatching {
            (0 until MediaPlayer.Equalizer.getPresetCount()).map { MediaPlayer.Equalizer.getPresetName(it) }
        }.getOrDefault(emptyList())
    }

    val eqBandLabels: List<String> by lazy {
        (0 until EQ_BAND_COUNT).map { i ->
            val hz = runCatching { MediaPlayer.Equalizer.getBandFrequency(i) }.getOrDefault(0f)
            if (hz >= 1000f) {
                val k = hz / 1000f
                (if (k >= 10f || k == k.toInt().toFloat()) k.toInt().toString()
                else String.format(Locale.US, "%.1f", k)) + "k"
            } else hz.toInt().toString()
        }
    }

    @JvmName("applyEqEnabled")
    fun setEqEnabled(on: Boolean) {
        eqEnabled = on
        applyEqualizer()
    }

    fun setEqBand(index: Int, db: Float) {
        if (index !in 0 until EQ_BAND_COUNT) return
        val value = db.coerceIn(-EQ_MAX_DB, EQ_MAX_DB)
        eqBands = eqBands.toMutableList().also { it[index] = value }
        eqPreset = -1
        eqEnabled = true
        applyEqualizer()
    }

    @JvmName("applyEqPreamp")
    fun setEqPreamp(db: Float) {
        eqPreamp = db.coerceIn(-EQ_MAX_DB, EQ_MAX_DB)
        eqPreset = -1
        eqEnabled = true
        applyEqualizer()
    }

    fun applyEqPreset(index: Int) {
        val eq = runCatching { MediaPlayer.Equalizer.createFromPreset(index) }.getOrNull() ?: return
        eqPreamp = runCatching { eq.preAmp }.getOrDefault(0f)
        eqBands = List(EQ_BAND_COUNT) { i -> runCatching { eq.getAmp(i) }.getOrDefault(0f) }
        eqPreset = index
        eqEnabled = true
        applyEqualizer()
    }

    fun resetEq() {
        eqPreamp = 0f
        eqBands = List(EQ_BAND_COUNT) { 0f }
        eqPreset = -1
        applyEqualizer()
    }

    private fun applyEqualizer() {
        if (released) return
        runCatching {
            if (!eqEnabled) {
                mediaPlayer.setEqualizer(null)
            } else {
                // libVLC copies the settings, and this libVLC version has no release() on
                // Equalizer (its native memory is freed when the object is garbage collected)
                val eq = MediaPlayer.Equalizer.create()
                eq.preAmp = eqPreamp
                eqBands.forEachIndexed { i, db -> eq.setAmp(i, db) }
                mediaPlayer.setEqualizer(eq)
            }
        }
    }

    /** Shifts audio timing. Positive values play audio later, negative earlier. */
    fun setAudioDelay(ms: Long) {
        audioDelayMs = ms.coerceIn(-MAX_AUDIO_DELAY_MS, MAX_AUDIO_DELAY_MS)
        applyAudioDelay()
    }

    private fun applyAudioDelay() {
        // libVLC takes microseconds
        runCatching { mediaPlayer.setAudioDelay(audioDelayMs * 1000L) }
    }

    /** 100 = normal, up to 200. */
    fun setBoost(percent: Int) {
        boostPercent = percent.coerceIn(100, 200)
        applyVolume()
    }

    // ---------- Video surface ----------

    fun attach(videoLayout: VLCVideoLayout) {
        layout = videoLayout
        if (released) return
        runCatching {
            if (!mediaPlayer.vlcVout.areViewsAttached()) {
                mediaPlayer.attachViews(videoLayout, null, true, false)
            }
        }
    }

    fun setAspect(mode: AspectMode) {
        runCatching {
            mediaPlayer.setVideoScale(
                when (mode) {
                    AspectMode.FIT -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
                    AspectMode.STRETCH -> MediaPlayer.ScaleType.SURFACE_FILL
                    AspectMode.CROP -> MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
                }
            )
        }
    }

    /** App left the screen but audio keeps playing: stop drawing video. */
    fun enterBackground() {
        if (released) return
        runCatching {
            savedVideoTrack = mediaPlayer.videoTrack
            mediaPlayer.setVideoTrack(-1)
        }
        runCatching { mediaPlayer.detachViews() }
    }

    fun returnToForeground() {
        val view = layout ?: return
        if (released) return
        runCatching {
            if (!mediaPlayer.vlcVout.areViewsAttached()) {
                mediaPlayer.attachViews(view, null, true, false)
            }
            if (savedVideoTrack >= 0) mediaPlayer.setVideoTrack(savedVideoTrack)
            savedVideoTrack = -1
        }
    }

    // ---------- Audio focus ----------

    @Suppress("DEPRECATION")
    private fun requestFocus() {
        if (focusGranted) return
        focusGranted = audioManager.requestAudioFocus(
            focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN
        ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    @Suppress("DEPRECATION")
    private fun abandonFocus() {
        if (focusGranted) audioManager.abandonAudioFocus(focusListener)
        focusGranted = false
    }

    // ---------- Cleanup ----------

    fun release() {
        if (released) return
        released = true
        runCatching { app.unregisterReceiver(noisyReceiver) }
        stopCastScan()
        castItem = null
        abandonFocus()
        runCatching { mediaPlayer.setEventListener(null) }
        runCatching { mediaPlayer.detachViews() }
        val player = mediaPlayer
        val pfd = currentPfd
        currentPfd = null
        // stop() and release() can take a while on slow devices, so keep them off the main thread
        thread(name = "vlc-release") {
            runCatching { player.stop() }
            runCatching { player.release() }
            closeQuietly(pfd)
            LibVlcProvider.engineReleased()
        }
    }
}
