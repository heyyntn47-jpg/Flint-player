// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.player

import android.Manifest
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.flint.player.ui.EngineLoading
import com.flint.player.data.FlintData
import com.flint.player.data.MediaEntry
import com.flint.player.data.Prefs
import com.flint.player.data.resolveTitle
import com.flint.player.data.resumeFor
import com.flint.player.library.formatDuration
import com.flint.player.ui.ExpressiveIconButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.math.abs
import kotlin.math.roundToInt

private fun readBrightness(activity: ComponentActivity): Float {
    val set = activity.window.attributes.screenBrightness
    if (set >= 0f) return set
    return runCatching {
        Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    }.getOrDefault(0.5f)
}

private fun writeBrightness(activity: ComponentActivity, value: Float) {
    val attrs = activity.window.attributes
    attrs.screenBrightness = value
    activity.window.attributes = attrs
}

/** Reads the video's real shape from the file, quickly, so the screen can turn before playback. */
private fun videoIsLandscape(context: Context, uri: Uri): Boolean? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        var width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull() ?: return null
        var height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull() ?: return null
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull() ?: 0
        if (rotation == 90 || rotation == 270) {
            val swap = width
            width = height
            height = swap
        }
        width >= height
    } catch (e: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

@Composable
fun FlintPlayer(
    uri: Uri?,
    playlistId: String?,
    startIndex: Int,
    activity: ComponentActivity
) {
    val context = LocalContext.current

    // Full screen from the very first moment, so the loading page looks like the player
    DisposableEffect(Unit) {
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller.show(WindowInsetsCompat.Type.navigationBars())
            if (!Prefs.hideStatusBar) controller.show(WindowInsetsCompat.Type.statusBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    // Turn the screen to match the video right away, instead of showing the loading page
    // in portrait and flipping later
    var orientationSettled by remember { mutableStateOf(false) }
    LaunchedEffect(uri, playlistId, startIndex) {
        try {
            if (!Prefs.orientationByVideo) return@LaunchedEffect
            val first = uri ?: playlistId
                ?.let { FlintData.playlist(it)?.items?.getOrNull(startIndex.coerceAtLeast(0))?.uri }
                ?.let { Uri.parse(it) }
                ?: return@LaunchedEffect
            val landscape = withContext(Dispatchers.IO) { videoIsLandscape(context, first) }
                ?: return@LaunchedEffect
            activity.requestedOrientation =
                if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } finally {
            orientationSettled = true
        }
    }

    // Built on a background thread: if libVLC is still setting itself up, the screen stays
    // responsive instead of freezing, and playback starts the moment it's ready.
    var failed by remember { mutableStateOf(false) }
    val engine by produceState<VlcEngine?>(null) {
        val created = withContext(Dispatchers.Default + NonCancellable) {
            runCatching { VlcEngine(context) }.getOrNull()
        }
        if (!isActive) {
            created?.release()
            return@produceState
        }
        if (created == null) failed = true
        value = created
    }

    if (failed) {
        LaunchedEffect(Unit) {
            Toast.makeText(context, "Couldn't start the player", Toast.LENGTH_LONG).show()
            activity.onBackPressedDispatcher.onBackPressed()
        }
    }

    // One loading page for the whole wait: while the engine is built and while the first
    // video starts. It stays in place between the two, so it never blinks or restarts.
    val ready = engine
    var startDone by remember { mutableStateOf(false) }
    LaunchedEffect(ready) {
        if (ready == null) return@LaunchedEffect
        snapshotFlow { ready.started || ready.startFailed }.first { it }
        // "Playing" arrives before the first picture is drawn. Keep the loading page up until
        // the video output exists. Audio-only files never get one, so don't wait forever.
        if (!ready.startFailed) {
            withTimeoutOrNull(2_500) { snapshotFlow { ready.firstFrame }.first { it } }
        }
        startDone = true
    }

    // A black cover sits over the video surface. It is fully opaque while the screen fades in
    // and the video starts, fades away to reveal the first frame, and comes back before the
    // screen closes. The video surface itself can't fade, so this is what makes opening and
    // closing smooth instead of flashing grey.
    val cover = remember { Animatable(1f) }
    // The controls stay hidden until the video is really playing and the loading screen is going away. Then they appear together with the video.
    var controlsReady by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }
    var exiting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        delay(260) // the screen-level fade has finished
        entered = true
    }
    LaunchedEffect(startDone, entered, orientationSettled) {
        if (startDone && entered && orientationSettled && !exiting) {
            delay(120) // let the first frame actually be drawn behind the cover
            // Only a video that is playing gets its controls (a failed start closes the screen)
            controlsReady = ready?.started == true
            cover.animateTo(0f, tween(240))
        }
    }
    BackHandler(enabled = !exiting) {
        exiting = true
        scope.launch {
            cover.animateTo(1f, tween(160))
            activity.onBackPressedDispatcher.onBackPressed()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (ready != null) PlayerScreen(ready, uri, playlistId, startIndex, activity, controlsReady)
        if (cover.value > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = cover.value }
                    .background(Color.Black)
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                if (!startDone) EngineLoading()
            }
        }
    }
}

@Composable
private fun PlayerScreen(
    engine: VlcEngine,
    uri: Uri?,
    playlistId: String?,
    startIndex: Int,
    activity: ComponentActivity,
    controlsReady: Boolean = true
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    val queue = remember(uri, playlistId) {
        when {
            playlistId != null -> FlintData.playlist(playlistId)?.items.orEmpty()
            uri != null -> listOf(MediaEntry(uri.toString(), resolveTitle(context, uri)))
            else -> emptyList()
        }
    }
    val isPlaylist = queue.size > 1
    val firstIndex = startIndex.coerceAtLeast(0)

    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    val pipSupported = remember { PipController.isSupported(context) }

    // These are read only by tiny parts of the screen, so changing them never redraws everything
    val clock = remember { PlaybackClock() }
    val messageHud = remember { MessageHud() }
    val brightnessHud = remember { SliderHud() }
    val volumeHud = remember { SliderHud() }
    val sleep = remember { SleepTimerState() }
    val zoom = remember { ZoomState() }
    val flash = remember { Animatable(0f) }
    var videoView by remember { mutableStateOf<View?>(null) }

    val isPlaying = engine.isPlaying
    var hasPrevious by remember { mutableStateOf(false) }
    var hasNext by remember { mutableStateOf(false) }

    // UI state
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    var showUnlock by remember { mutableStateOf(false) }
    var orientation by remember { mutableStateOf(OrientationMode.AUTO) }
    var orientationChosen by remember { mutableStateOf(false) }
    var aspect by remember {
        mutableStateOf(AspectMode.entries.getOrElse(Prefs.defaultAspect) { AspectMode.FIT })
    }
    var sheetKind by remember { mutableStateOf(PlayerSheet.MENU) }
    var sheetVisible by remember { mutableStateOf(false) }
    var scrubTarget by remember { mutableLongStateOf(-1L) }
    var scrubbing by remember { mutableStateOf(false) }
    var inPip by remember { mutableStateOf(false) }
    var justLeftPip by remember { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // ---------- Helpers ----------

    fun showHud(text: String) = messageHud.show(text)

    fun refreshClock() {
        clock.position = engine.position()
        clock.duration = engine.duration()
    }

    fun touch() {
        refreshClock()
        controlsVisible = true
        interaction++
    }

    fun openSheet(kind: PlayerSheet) {
        sheetKind = kind
        sheetVisible = true
    }

    fun applyOrientation(mode: OrientationMode) {
        orientation = mode
        activity.requestedOrientation = when (mode) {
            OrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }

    fun cycleOrientation() {
        val next = OrientationMode.entries[(orientation.ordinal + 1) % OrientationMode.entries.size]
        applyOrientation(next)
        showHud("Rotation: ${next.label}")
    }

    fun cycleAspect() {
        aspect = AspectMode.entries[(aspect.ordinal + 1) % AspectMode.entries.size]
        showHud("Aspect: ${aspect.label}")
    }

    fun saveProgress() {
        if (!Prefs.saveHistory || !engine.hasStarted) return
        val id = engine.currentUri ?: return
        FlintData.recordPlayback(
            uri = id,
            title = engine.title,
            positionMs = engine.position(),
            durationMs = engine.duration()
        )
    }

    fun seekBy(deltaMs: Long) {
        val total = engine.duration()
        var target = engine.position() + deltaMs
        target = if (total > 0L) target.coerceIn(0L, total) else target.coerceAtLeast(0L)
        engine.seekTo(target)
        clock.position = target
        showHud(if (deltaMs < 0) "-${abs(deltaMs) / 1000}s" else "+${deltaMs / 1000}s")
    }

    // A-B repeat: first tap marks A, second marks B and starts looping, third clears it
    fun cycleAbRepeat() {
        val total = engine.duration()
        when {
            clock.loopA < 0L -> {
                clock.loopA = engine.position()
                showHud("A set  ${formatDuration(clock.loopA)}")
            }
            clock.loopB < 0L -> {
                val limit = if (total > 0L) total - 300L else Long.MAX_VALUE
                val at = engine.position().coerceAtMost(limit)
                if (at - clock.loopA < 500L) {
                    showHud("Pick B after A")
                } else {
                    clock.loopB = at
                    showHud("Looping ${formatDuration(clock.loopA)} – ${formatDuration(at)}")
                }
            }
            else -> {
                clock.loopA = -1L
                clock.loopB = -1L
                showHud("A-B repeat off")
            }
        }
    }

    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        showHud(if (granted) "Tap the camera again to take it" else "Allow storage access to save screenshots")
    }

    fun takeScreenshot() {
        if (Build.VERSION.SDK_INT < 29 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        val name = ScreenshotTaker.fileName(engine.title, formatDuration(engine.position()))
        scope.launch {
            when (val result = ScreenshotTaker.capture(context, videoView, name, Prefs.screenshotPng)) {
                is ShotResult.Saved -> {
                    flash.snapTo(0.55f)
                    launch { flash.animateTo(0f, tween(280)) }
                    showHud("Screenshot saved")
                }
                is ShotResult.Failed -> showHud(result.reason)
            }
        }
    }

    fun cycleRepeat() {
        val next = when (engine.repeatMode) {
            Repeat.OFF -> if (isPlaylist) Repeat.ALL else Repeat.ONE
            Repeat.ALL -> Repeat.ONE
            else -> Repeat.OFF
        }
        engine.setRepeat(next)
        showHud(
            when (next) {
                Repeat.ALL -> "Repeat all"
                Repeat.ONE -> "Repeat one"
                else -> "Repeat off"
            }
        )
    }

    fun toggleShuffle() {
        engine.setShuffle(!engine.shuffleOn)
        showHud(if (engine.shuffleOn) "Shuffle on" else "Shuffle off")
    }

    fun toggleBackgroundPlay() {
        Prefs.updateBackgroundPlay(!Prefs.backgroundPlay)
        showHud(if (Prefs.backgroundPlay) "Background play on" else "Background play off")
    }

    fun enterPip() {
        if (PipController.enter(activity)) {
            controlsVisible = false
            sheetVisible = false
        } else {
            showHud("Picture-in-picture isn't available")
        }
    }

    fun startSleep(minutes: Int) {
        sleep.startMinutes(minutes)
        showHud("Sleep timer: $minutes min")
    }

    fun sleepAfterVideo() {
        sleep.startEndOfVideo()
        showHud("Sleep timer: after this video")
    }

    fun cancelSleep() {
        sleep.cancel()
        engine.setVolumePercent(100)
        showHud("Sleep timer off")
    }

    // The same video reopens at the same spot with the new decoder
    fun changeDecoder(mode: Int) {
        if (mode == Prefs.decoderMode) return
        Prefs.set(Prefs.DECODER, mode)
        saveProgress()
        engine.reopenCurrent()
        showHud(
            listOf("Decoding: Automatic", "Decoding: Disabled (software)", "Decoding: Full (hardware)")
                .getOrElse(mode) { "Decoding changed" }
        )
    }

    fun addSubtitle(picked: Uri) {
        val name = resolveTitle(context, picked)
        showHud("Reading subtitle…")
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                SubtitleImport.import(context, picked, name, engine.duration())
            }
            when (outcome) {
                is SubtitleImport.Outcome.Ok -> {
                    if (engine.addSubtitle(outcome.imported.uri)) {
                        showHud("Subtitle on")
                    } else {
                        showHud("The player couldn't load that subtitle")
                    }
                }
                is SubtitleImport.Outcome.Fail -> showHud(outcome.reason)
            }
        }
    }

    val subtitlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { picked -> if (picked != null) addSubtitle(picked) }

    // ---------- Effects ----------

    LaunchedEffect(queue) {
        if (queue.isEmpty()) return@LaunchedEffect
        engine.setRepeat(
            when (Prefs.defaultRepeat) {
                1 -> Repeat.ONE
                2 -> if (isPlaylist) Repeat.ALL else Repeat.ONE
                else -> Repeat.OFF
            }
        )
        engine.setSpeed(Prefs.defaultSpeed)
        val start = firstIndex.coerceIn(0, queue.lastIndex)
        engine.load(queue, start, resumeFor(queue[start].uri))
    }

    DisposableEffect(engine) {
        engine.onError = { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
        engine.onNotice = { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
        engine.onSleepFired = {
            sleep.cancel()
            engine.setVolumePercent(100)
            messageHud.show("Sleep timer: paused")
        }
        onDispose {
            engine.onError = null
            engine.onNotice = null
            engine.onSleepFired = null
        }
    }

    // Background play: a media session + notification keep playback alive off-screen
    LaunchedEffect(Prefs.backgroundPlay) {
        if (Prefs.backgroundPlay) {
            PlaybackBridge.attach(context, engine)
            if (Build.VERSION.SDK_INT >= 33 && !Prefs.notificationAsked &&
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Prefs.markNotificationAsked()
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            PlaybackBridge.detach(context, engine)
        }
    }

    LaunchedEffect(engine) {
        while (true) {
            delay(5_000)
            if (engine.isPlaying) saveProgress()
        }
    }

    // Sleep timer: counts down, fades the volume over the last 15 seconds, then pauses
    LaunchedEffect(sleep) {
        snapshotFlow { sleep.endAtMs }.collectLatest { endAt ->
            if (endAt <= 0L) {
                engine.setVolumePercent(100)
                sleep.remainingMs = 0L
                return@collectLatest
            }
            while (true) {
                val left = endAt - SystemClock.elapsedRealtime()
                if (left <= 0L) {
                    engine.pause()
                    engine.setVolumePercent(100)
                    sleep.cancel()
                    showHud("Sleep timer: paused")
                    break
                }
                sleep.remainingMs = left
                engine.setVolumePercent(
                    if (left < 15_000L) ((left / 15_000f) * 100).toInt().coerceIn(5, 100) else 100
                )
                delay(500)
            }
        }
    }

    // "After this video": the engine pauses itself when the current video ends
    LaunchedEffect(sleep) {
        snapshotFlow { sleep.endOfVideo }.collect { on -> engine.sleepAtEnd = on }
    }

    // Always show the controls while the video is paused
    LaunchedEffect(isPlaying) {
        if (!isPlaying) controlsVisible = true
        PipController.playing = isPlaying
    }

    // A-B repeat: when playback reaches B, jump back to A
    LaunchedEffect(engine) {
        while (true) {
            delay(80)
            val a = clock.loopA
            val b = clock.loopB
            if (a >= 0L && b > a && engine.isPlaying && engine.position() >= b) {
                engine.seekTo(a)
                clock.position = a
                delay(250) // give the jump time to land before checking again
            }
        }
    }

    // A new video starts with no loop and no zoom
    LaunchedEffect(engine.currentIndex) {
        clock.loopA = -1L
        clock.loopB = -1L
        zoom.reset()
    }

    // Seekbar preview: when your finger pauses while scrubbing, jump to that spot
    LaunchedEffect(engine) {
        snapshotFlow { scrubTarget }.collectLatest { target ->
            if (target >= 0L) {
                delay(150)
                engine.seekTo(target)
            }
        }
    }

    LaunchedEffect(engine, controlsVisible) {
        while (controlsVisible) {
            clock.position = engine.position()
            clock.duration = engine.duration()
            hasPrevious = engine.hasPrevious()
            hasNext = engine.hasNext()
            delay(250)
        }
    }

    LaunchedEffect(
        controlsVisible, controlsReady, isPlaying, interaction, sheetVisible, locked, scrubbing,
        Prefs.controlsHideMs
    ) {
        // The hide timer starts when the controls actually appear, not when the video starts.
        // The delay is a setting; "Never" keeps the controls up until you tap the video.
        val hideAfter = Prefs.controlsHideMs
        if (controlsVisible && controlsReady && isPlaying && !sheetVisible && !locked && !scrubbing &&
            hideAfter > 0L
        ) {
            delay(hideAfter)
            controlsVisible = false
        }
    }

    LaunchedEffect(showUnlock) {
        if (showUnlock) {
            delay(3_000)
            showUnlock = false
        }
    }

    // Video shape: picture-in-picture window shape, and landscape / portrait choice
    val videoWidth = engine.videoWidth
    val videoHeight = engine.videoHeight
    LaunchedEffect(videoWidth, videoHeight) {
        if (videoWidth > 0 && videoHeight > 0) {
            PipController.setVideoSize(videoWidth, videoHeight)
            if (!orientationChosen && Prefs.orientationByVideo) {
                orientationChosen = true
                applyOrientation(
                    if (videoWidth >= videoHeight) OrientationMode.LANDSCAPE
                    else OrientationMode.PORTRAIT
                )
            }
        }
    }

    DisposableEffect(engine) {
        PipController.arm(engine)
        onDispose { PipController.disarm(engine) }
    }

    // Picture-in-picture window opened / closed
    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            inPip = info.isInPictureInPictureMode
            if (!info.isInPictureInPictureMode) {
                justLeftPip = true
                // Closed with its X: the screen is stopped instead of coming back, so stop the
                // video too. (Expanding the window back to the app leaves it started and playing.)
                if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    engine.pause()
                }
            }
        }
        activity.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
    }

    // Leaving the app: keep playing only if background play is on (or the PiP window is open)
    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> saveProgress()
                Lifecycle.Event.ON_RESUME -> justLeftPip = false
                Lifecycle.Event.ON_START -> engine.returnToForeground()
                Lifecycle.Event.ON_STOP -> {
                    saveProgress()
                    val stillInPip = Build.VERSION.SDK_INT >= 26 && activity.isInPictureInPictureMode
                    // justLeftPip: the floating window was closed with its X
                    if (justLeftPip || (!Prefs.backgroundPlay && !stillInPip)) {
                        engine.pause()
                    } else if (!stillInPip) {
                        engine.enterBackground()
                    }
                }
                else -> Unit
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        PlayerSession.active = true
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        activity.volumeControlStream = AudioManager.STREAM_MUSIC
        onDispose {
            PlayerSession.active = false
            saveProgress()
            // Back in the app: the navigation bar returns; the status bar follows the setting
            controller.show(WindowInsetsCompat.Type.navigationBars())
            if (!Prefs.hideStatusBar) controller.show(WindowInsetsCompat.Type.statusBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            writeBrightness(activity, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
            PlaybackBridge.detach(context, engine)
            engine.release()
            LibVlcProvider.scheduleWarmUp(context)
        }
    }

    BackHandler(enabled = locked) { showUnlock = true }
    BackHandler(enabled = sheetVisible) { sheetVisible = false }

    // ---------- UI ----------

    // Takes no notice of the keyboard, system dialogs or bars: the player layout stays put
    Box(
        modifier = Modifier
            .fillMaxSize()
            .consumeWindowInsets(WindowInsets.systemBars)
            .consumeWindowInsets(WindowInsets.ime)
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    engine.attach(this)
                    videoView = this
                }
            },
            update = { view ->
                view.keepScreenOn = Prefs.keepScreenOn
                engine.setAspect(aspect)
                // Zoom is applied to the whole video view, so it works with every aspect mode
                val scale = zoom.scale
                val maxX = (scale - 1f).coerceAtLeast(0f) * view.width / 2f
                val maxY = (scale - 1f).coerceAtLeast(0f) * view.height / 2f
                view.scaleX = scale
                view.scaleY = scale
                view.translationX = zoom.panX.coerceIn(-maxX, maxX)
                view.translationY = zoom.panY.coerceIn(-maxY, maxY)
            },
            modifier = Modifier.fillMaxSize()
        )

        // In the floating window only the video is shown
        if (!inPip) {

            // Gesture layer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pinchZoom(
                        enabled = !locked && Prefs.pinchZoom,
                        zoom = zoom,
                        onFinished = { scale -> showHud(if (scale <= 1f) "Zoom reset" else "Zoom ${zoomLabel(scale)}") }
                    )
                    .pointerInput(locked, Prefs.gestureDoubleTap) {
                        val doubleTap: (Offset) -> Unit = { offset ->
                            if (!locked) {
                                val third = size.width / 3f
                                val step = Prefs.doubleTapSeconds * 1000L
                                when {
                                    offset.x < third -> seekBy(-step)
                                    offset.x > third * 2 -> seekBy(step)
                                    else -> engine.togglePlay()
                                }
                            }
                        }
                        detectTapGestures(
                            onTap = {
                                if (locked) {
                                    showUnlock = !showUnlock
                                } else {
                                    refreshClock()
                                    controlsVisible = !controlsVisible
                                    interaction++
                                }
                            },
                            onDoubleTap = if (Prefs.gestureDoubleTap) doubleTap else null
                        )
                    }
                    .pointerInput(locked) {
                        if (locked) return@pointerInput
                        var axis = 0 // 1 seek, 2 brightness, 3 volume, 4 ignore
                        var accX = 0f
                        var accY = 0f
                        var totalX = 0f
                        var startX = 0f
                        var seekStart = 0L
                        var seekTarget = 0L
                        var brightness = 0.5f
                        var volume = 0f
                        var lastBrightnessStep = -1
                        var lastVolumeStep = -1

                        detectDragGestures(
                            onDragStart = { offset ->
                                axis = 0
                                accX = 0f
                                accY = 0f
                                totalX = 0f
                                startX = offset.x
                                seekStart = engine.position()
                                seekTarget = seekStart
                                brightness = readBrightness(activity)
                                volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                                lastBrightnessStep = (brightness * 100).roundToInt()
                                lastVolumeStep = volume.roundToInt()
                            },
                            onDragEnd = {
                                if (axis == 1) engine.seekTo(seekTarget)
                                axis = 0
                            },
                            onDragCancel = { axis = 0 },
                            onDrag = { change, drag ->
                                change.consume()
                                totalX += drag.x

                                if (axis == 0) {
                                    accX += drag.x
                                    accY += drag.y
                                    if (abs(accX) < 20f && abs(accY) < 20f) {
                                        return@detectDragGestures
                                    }
                                    axis = when {
                                        abs(accX) > abs(accY) -> 1
                                        startX < size.width / 2f -> 2
                                        else -> 3
                                    }
                                    // Gestures switched off in Settings are ignored
                                    if (axis == 1 &&
                                        (!Prefs.gestureSeek || engine.duration() <= 0L)
                                    ) axis = 4
                                    if (axis == 2 && !Prefs.gestureBrightness) axis = 4
                                    if (axis == 3 && !Prefs.gestureVolume) axis = 4
                                }

                                when (axis) {
                                    1 -> {
                                        val total = engine.duration()
                                        val delta = (totalX / size.width * 120_000f).toLong()
                                        seekTarget = (seekStart + delta).coerceIn(0L, total)
                                        val sign = if (seekTarget >= seekStart) "+" else "-"
                                        showHud(
                                            "$sign${formatDuration(abs(seekTarget - seekStart))}" +
                                                    "  (${formatDuration(seekTarget)})"
                                        )
                                    }
                                    2 -> {
                                        brightness = (brightness - drag.y / size.height * 1.5f)
                                            .coerceIn(0.02f, 1f)
                                        val step = (brightness * 100).roundToInt()
                                        if (step != lastBrightnessStep) {
                                            lastBrightnessStep = step
                                            writeBrightness(activity, brightness)
                                        }
                                        brightnessHud.update(brightness)
                                    }
                                    3 -> {
                                        volume = (volume - drag.y / size.height * maxVolume * 1.5f)
                                            .coerceIn(0f, maxVolume.toFloat())
                                        val step = volume.roundToInt()
                                        if (step != lastVolumeStep) {
                                            lastVolumeStep = step
                                            runCatching {
                                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, step, 0)
                                            }
                                        }
                                        volumeHud.update(if (maxVolume > 0) volume / maxVolume else 0f)
                                    }
                                    else -> Unit
                                }
                            }
                        )
                    }
            )

            // Two looks: the classic one, and the glass one with every control on screen
            if (Prefs.playerUi == 1) {
                GlassPlayerControls(
                    visible = controlsVisible && !locked && controlsReady,
                    title = engine.title +
                        if (isPlaylist) " • ${engine.currentIndex + 1}/${engine.queueTitles.size}" else "",
                    isPlaying = isPlaying,
                    clock = clock,
                    orientation = orientation,
                    aspect = aspect,
                    isPlaylist = isPlaylist,
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                    repeatMode = engine.repeatMode,
                    shuffleOn = engine.shuffleOn,
                    speedText = speedLabel(engine.speed) + "x",
                    backgroundPlay = Prefs.backgroundPlay,
                    decoderText = listOf("Auto", "SW", "HW")
                        .getOrElse(Prefs.decoderMode) { "Auto" },
                    castName = engine.castName,
                    pipSupported = pipSupported,
                    sleepActive = sleep.active,
                    onBack = { activity.onBackPressedDispatcher.onBackPressed() },
                    onPlayPause = { engine.togglePlay(); touch() },
                    onPrevious = { engine.previous(); touch() },
                    onNext = { engine.next(); touch() },
                    onSeekBy = { delta -> seekBy(delta); touch() },
                    onScrub = { target ->
                        if (scrubTarget < 0L) scrubbing = true
                        scrubTarget = target
                    },
                    onScrubEnd = { target ->
                        scrubTarget = -1L
                        scrubbing = false
                        engine.seekTo(target)
                        clock.position = target
                    },
                    onLock = {
                        locked = true
                        controlsVisible = false
                        showUnlock = true
                    },
                    onOrientation = { cycleOrientation(); touch() },
                    onAspect = { cycleAspect(); touch() },
                    onRepeat = { cycleRepeat(); touch() },
                    onShuffle = { toggleShuffle(); touch() },
                    onQueue = { openSheet(PlayerSheet.QUEUE) },
                    onAudio = { openSheet(PlayerSheet.AUDIO) },
                    onSubtitles = { openSheet(PlayerSheet.SUBTITLES) },
                    onSpeed = { openSheet(PlayerSheet.SPEED) },
                    onSleep = { openSheet(PlayerSheet.SLEEP) },
                    onCast = { openSheet(PlayerSheet.CAST) },
                    onMixer = { openSheet(PlayerSheet.MIXER) },
                    onBackgroundPlay = { toggleBackgroundPlay(); touch() },
                    onDecoder = { openSheet(PlayerSheet.DECODER) },
                    onPip = { enterPip() },
                    onAbRepeat = { cycleAbRepeat(); touch() },
                    onScreenshot = { takeScreenshot(); touch() },
                    onInteract = { touch() }
                )
            } else {
                PlayerControls(
                    visible = controlsVisible && !locked && controlsReady,
                    title = engine.title,
                    isPlaying = isPlaying,
                    clock = clock,
                    orientation = orientation,
                    aspect = aspect,
                    isPlaylist = isPlaylist,
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                    repeatMode = engine.repeatMode,
                    shuffleOn = engine.shuffleOn,
                    onBack = { activity.onBackPressedDispatcher.onBackPressed() },
                    onPlayPause = { engine.togglePlay(); touch() },
                    onPrevious = { engine.previous(); touch() },
                    onNext = { engine.next(); touch() },
                    onSeekBy = { delta -> seekBy(delta); touch() },
                    onScrub = { target ->
                        if (scrubTarget < 0L) scrubbing = true
                        scrubTarget = target
                    },
                    onScrubEnd = { target ->
                        scrubTarget = -1L
                        scrubbing = false
                        engine.seekTo(target)
                        clock.position = target
                    },
                    onLock = {
                        locked = true
                        controlsVisible = false
                        showUnlock = true
                    },
                    onOrientation = { cycleOrientation(); touch() },
                    onAspect = { cycleAspect(); touch() },
                    onRepeat = { cycleRepeat(); touch() },
                    onShuffle = { toggleShuffle(); touch() },
                    onAbRepeat = { cycleAbRepeat(); touch() },
                    onScreenshot = { takeScreenshot(); touch() },
                    onSettings = { openSheet(PlayerSheet.MENU) }
                )
            }

            // Brightness swipe (left) shows its slider on the right; volume swipe (right) on the left
            GestureSlider(
                hud = volumeHud,
                iconFor = ::volumeIcon,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .displayCutoutPadding()
                    .padding(start = 24.dp)
            )
            GestureSlider(
                hud = brightnessHud,
                iconFor = ::brightnessIcon,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .displayCutoutPadding()
                    .padding(end = 24.dp)
            )

            // Unlock button
            if (locked && showUnlock) {
                ExpressiveIconButton(
                    icon = Icons.Outlined.LockOpen,
                    description = "Unlock",
                    onClick = {
                        locked = false
                        showUnlock = false
                        touch()
                    },
                    size = 56.dp,
                    iconSize = 26.dp,
                    tint = scheme.onSecondaryContainer,
                    container = scheme.secondaryContainer,
                    pressedContainer = scheme.tertiaryContainer,
                    pressedTint = scheme.onTertiaryContainer,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .displayCutoutPadding()
                        .padding(24.dp)
                )
            }

            // Quick white flash when a screenshot is taken
            if (flash.value > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = flash.value }
                        .background(Color.White)
                )
            }

            // Seek / repeat / subtitle message
            MessageHudPill(
                hud = messageHud,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .displayCutoutPadding()
                    .padding(top = 28.dp)
            )

            // Player settings pill that slides up from the bottom
            BottomPill(
                visible = sheetVisible,
                page = sheetKind,
                onDismiss = { sheetVisible = false }
            ) { page ->
                // The glass UI opens each page straight from its own button, so there is no menu to go back to
                if (page != PlayerSheet.MENU && Prefs.playerUi != 1) {
                    SheetBackRow(label = "Player settings") { sheetKind = PlayerSheet.MENU }
                }
                when (page) {
                    PlayerSheet.MENU -> SettingsMenuSheet(
                        isPlaylist = isPlaylist,
                        speedText = speedLabel(engine.speed) + "x",
                        backgroundPlay = Prefs.backgroundPlay,
                        decoderText = listOf("Automatic", "Disabled", "Full")
                            .getOrElse(Prefs.decoderMode) { "Automatic" },
                        castName = engine.castName,
                        pipSupported = pipSupported,
                        sleep = sleep,
                        onQueue = { sheetKind = PlayerSheet.QUEUE },
                        onAudio = { sheetKind = PlayerSheet.AUDIO },
                        onSubtitles = { sheetKind = PlayerSheet.SUBTITLES },
                        onSpeed = { sheetKind = PlayerSheet.SPEED },
                        onSleep = { sheetKind = PlayerSheet.SLEEP },
                        onCast = { sheetKind = PlayerSheet.CAST },
                        onMixer = { sheetKind = PlayerSheet.MIXER },
                        onBackgroundPlay = { toggleBackgroundPlay() },
                        onDecoder = { sheetKind = PlayerSheet.DECODER },
                        onPip = { enterPip() }
                    )
                    PlayerSheet.SPEED -> SpeedSheet(engine.speed) { engine.setSpeed(it) }
                    PlayerSheet.SUBTITLES -> SubtitleSheet(
                        tracks = engine.subtitleList,
                        selectedId = engine.subtitleSelected,
                        delayMs = engine.subtitleDelayMs,
                        onSelect = { engine.selectSubtitle(it) },
                        onDelayChange = { engine.setSubtitleDelay(it) },
                        onAddFile = { subtitlePicker.launch(arrayOf("*/*")) },
                        onDone = { sheetVisible = false }
                    )
                    PlayerSheet.AUDIO -> AudioSheet(
                        tracks = engine.audioList,
                        selectedId = engine.audioSelected,
                        onSelect = { engine.selectAudio(it) },
                        onDone = { sheetVisible = false }
                    )
                    PlayerSheet.QUEUE -> QueueSheet(
                        titles = engine.queueTitles,
                        currentIndex = engine.currentIndex,
                        onPick = { index ->
                            engine.jumpTo(index)
                            sheetVisible = false
                        }
                    )
                    PlayerSheet.DECODER -> DecoderSheet(Prefs.decoderMode) { mode ->
                        changeDecoder(mode)
                    }
                    PlayerSheet.SLEEP -> SleepTimerSheet(
                        sleep = sleep,
                        onMinutes = { startSleep(it) },
                        onEndOfVideo = { sleepAfterVideo() },
                        onCancel = { cancelSleep() }
                    )
                    PlayerSheet.MIXER -> MixerSheet(engine)
                    PlayerSheet.CAST -> CastSheet(engine)
                }
            }
        }
    }
}