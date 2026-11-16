// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import com.flint.player.ui.TabularNumbers
import com.flint.player.library.formatDuration
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest

/**
 * Playback position, kept outside the screen so a tick every 0.25 s
 * only redraws the seekbar and the time labels, never the whole player.
 */
@Stable
class PlaybackClock {
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)

    /** 0..1 while the seekbar is being dragged, -1 otherwise. */
    var scrub by mutableFloatStateOf(-1f)

    /** A-B repeat points in milliseconds, -1 when not set. */
    var loopA by mutableLongStateOf(-1L)
    var loopB by mutableLongStateOf(-1L)

    val loopActive: Boolean get() = loopA >= 0L && loopB > loopA

    fun fraction(): Float =
        if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
}

private fun fractionAt(x: Float, width: Float, pad: Float): Float =
    ((x - pad) / (width - 2f * pad).coerceAtLeast(1f)).coerceIn(0f, 1f)

@Composable
fun CurrentTimeText(clock: PlaybackClock, modifier: Modifier = Modifier) {
    val ms = if (clock.scrub >= 0f) (clock.scrub * clock.duration).toLong() else clock.position
    Text(
        text = formatDuration(ms),
        color = Color.White,
        style = MaterialTheme.typography.labelMedium.merge(TabularNumbers),
        modifier = modifier
    )
}

@Composable
fun DurationText(clock: PlaybackClock, modifier: Modifier = Modifier) {
    Text(
        text = formatDuration(clock.duration),
        color = Color.White,
        style = MaterialTheme.typography.labelMedium.merge(TabularNumbers),
        modifier = modifier
    )
}

/** Thin seekbar: grows, bounces and shows a time bubble while you drag it. */
@Composable
fun SleekSeekBar(
    clock: PlaybackClock,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    modifier: Modifier = Modifier,
    inactiveColor: Color = Color.White.copy(alpha = 0.3f)
) {
    val scheme = MaterialTheme.colorScheme
    val activeColor = scheme.primary


    val dragging by remember { derivedStateOf { clock.scrub >= 0f } }
    val trackHeight by animateDpAsState(
        targetValue = if (dragging) 8.dp else 4.dp,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "trackHeight"
    )
    val handleWidth by animateDpAsState(
        targetValue = if (dragging) 3.dp else 4.dp,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "handleWidth"
    )
    val handleHeight by animateDpAsState(
        targetValue = if (dragging) 32.dp else 22.dp,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 450f),
        label = "handleHeight"
    )
    val gap by animateDpAsState(
        targetValue = if (dragging) 6.dp else 4.dp,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "gap"
    )

    // Smooth, continuous motion instead of a jump every tick
    val shown = remember { Animatable(Snapshot.withoutReadObservation { clock.fraction() }) }
    LaunchedEffect(clock) {
        snapshotFlow { if (clock.scrub >= 0f) clock.scrub else clock.fraction() }
            .collectLatest { target ->
                if (clock.scrub >= 0f) shown.snapTo(target)
                else shown.animateTo(target, tween(300, easing = LinearEasing))
            }
    }

    // The trail follows the handle with a lag, so a seek leaves a streak that fades out behind it
    val trail = remember { Animatable(Snapshot.withoutReadObservation { clock.fraction() }) }
    LaunchedEffect(shown) {
        snapshotFlow { shown.value }
            .collectLatest { target ->
                trail.animateTo(target, spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow))
            }
    }

    Box(modifier.height(44.dp)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    val pad = 4.dp.toPx()
                    detectTapGestures { offset ->
                        val duration = clock.duration
                        if (duration > 0L) {
                            val f = fractionAt(offset.x, size.width.toFloat(), pad)
                            val target = (f * duration).toLong()
                            clock.position = target
                            onScrubEnd(target)
                        }
                    }
                }
                .pointerInput(Unit) {
                    val pad = 4.dp.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            val duration = clock.duration
                            if (duration > 0L) {
                                val f = fractionAt(offset.x, size.width.toFloat(), pad)
                                clock.scrub = f
                                onScrub((f * duration).toLong())
                            }
                        },
                        onDragEnd = {
                            val f = clock.scrub
                            if (f >= 0f) {
                                val target = (f * clock.duration).toLong()
                                clock.position = target
                                clock.scrub = -1f
                                onScrubEnd(target)
                            }
                        },
                        onDragCancel = {
                            val f = clock.scrub
                            if (f >= 0f) {
                                val target = (f * clock.duration).toLong()
                                clock.position = target
                                clock.scrub = -1f
                                onScrubEnd(target)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (clock.scrub >= 0f) {
                                val usable = (size.width - 2f * pad).coerceAtLeast(1f)
                                val f = (clock.scrub + dragAmount / usable).coerceIn(0f, 1f)
                                clock.scrub = f
                                onScrub((f * clock.duration).toLong())
                            }
                        }
                    )
                }
        ) {
            val pad = 4.dp.toPx()
            val th = trackHeight.toPx()
            val hw = handleWidth.toPx()
            val hh = handleHeight.toPx()
            val g = gap.toPx()
            val w = size.width
            val cy = size.height / 2f
            val x = pad + shown.value * (w - 2f * pad)

            val activeEnd = x - g - hw / 2f
            if (activeEnd > 0f) {
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(0f, cy - th / 2f),
                    size = Size(activeEnd, th),
                    cornerRadius = CornerRadius(th / 2f)
                )
            }
            val inactiveStart = x + g + hw / 2f
            if (inactiveStart < w) {
                drawRoundRect(
                    color = inactiveColor,
                    topLeft = Offset(inactiveStart, cy - th / 2f),
                    size = Size(w - inactiveStart, th),
                    cornerRadius = CornerRadius(th / 2f)
                )
            }
            // Trail: forward seeks leave it on the left of the handle, backward seeks on the right
            val tx = pad + trail.value * (w - 2f * pad)
            val forward = tx < x
            val trailLeft = if (forward) tx else x + hw / 2f
            val trailRight = if (forward) x - hw / 2f else tx
            if (trailRight - trailLeft > 1.5f) {
                val trailHeight = th + 6.dp.toPx()
                val solid = activeColor
                val faded = activeColor.copy(alpha = 0f)
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = if (forward) listOf(faded, solid) else listOf(solid, faded),
                        startX = trailLeft,
                        endX = trailRight
                    ),
                    topLeft = Offset(trailLeft, cy - trailHeight / 2f),
                    size = Size(trailRight - trailLeft, trailHeight),
                    cornerRadius = CornerRadius(trailHeight / 2f)
                )
            }
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(x - hw / 2f, cy - hh / 2f),
                size = Size(hw, hh),
                cornerRadius = CornerRadius(hw / 2f)
            )
        }

        // Time bubble that follows the handle while dragging
        Box(
            Modifier
                .fillMaxWidth()
                .offset(y = (-42).dp)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        constraints.copy(minWidth = 0, minHeight = 0)
                    )
                    val pad = 4.dp.roundToPx()
                    layout(constraints.maxWidth, placeable.height) {
                        val usable = constraints.maxWidth - 2 * pad
                        val centerX = pad + (clock.scrub.coerceAtLeast(0f) * usable).roundToInt()
                        val maxX = (constraints.maxWidth - placeable.width).coerceAtLeast(0)
                        placeable.place((centerX - placeable.width / 2).coerceIn(0, maxX), 0)
                    }
                }
        ) {
            AnimatedVisibility(
                visible = dragging,
                enter = fadeIn(tween(90)) + scaleIn(
                    initialScale = 0.6f,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f)
                ),
                exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.8f)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = scheme.primaryContainer
                ) {
                    Text(
                        text = formatDuration((clock.scrub.coerceAtLeast(0f) * clock.duration).toLong()),
                        color = scheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelLarge.merge(TabularNumbers),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

/** The seekbar designs the user can pick in Settings. [id] is what gets saved. */
enum class SeekBarStyle(val id: Int, val label: String, val description: String) {
    SLEEK(0, "Default", "Bouncy bar with a handle, trail and time bubble"),
    WAVY(1, "Wavy", "Material 3 wave that ripples as the video plays"),
    THIN(2, "Thin minimal", "A hairline track with a tiny dot"),
    CHUNKY(3, "Chunky", "A fat, rounded bar with no thumb");

    companion object {
        fun fromId(id: Int): SeekBarStyle = entries.firstOrNull { it.id == id } ?: SLEEK
    }
}

/**
 * Picks the seekbar the user chose and puts the A-B loop markers on top of it, so every style gets them.
 */
@Composable
fun PlayerSeekBar(
    style: SeekBarStyle,
    clock: PlaybackClock,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    modifier: Modifier = Modifier,
    inactiveColor: Color = Color.White.copy(alpha = 0.3f)
) {
    // Each bar keeps a little room at its ends; the markers use the same room
    val pad = when (style) {
        SeekBarStyle.SLEEK -> 4
        SeekBarStyle.THIN -> 8
        else -> 0
    }
    Box(modifier) {
        val bar = Modifier.fillMaxWidth()
        when (style) {
            SeekBarStyle.SLEEK -> SleekSeekBar(clock, onScrub, onScrubEnd, bar, inactiveColor)
            SeekBarStyle.WAVY -> WavySeekBar(clock, onScrub, onScrubEnd, bar, inactiveColor)
            SeekBarStyle.THIN -> ThinSeekBar(clock, onScrub, onScrubEnd, bar, inactiveColor)
            SeekBarStyle.CHUNKY -> ChunkySeekBar(clock, onScrub, onScrubEnd, bar, inactiveColor)
        }
        LoopMarkers(clock, pad.toFloat(), Modifier.height(44.dp))
    }
}

// ---------- Shared pieces ----------

private fun fraction(x: Float, width: Float, pad: Float): Float =
    ((x - pad) / (width - 2f * pad).coerceAtLeast(1f)).coerceIn(0f, 1f)

/** Smoothly follows the playback position, and snaps to the finger while scrubbing. */
@Composable
private fun rememberShownFraction(clock: PlaybackClock): Animatable<Float, *> {
    val shown = remember { Animatable(Snapshot.withoutReadObservation { clock.fraction() }) }
    LaunchedEffect(clock) {
        snapshotFlow { if (clock.scrub >= 0f) clock.scrub else clock.fraction() }
            .collectLatest { target ->
                if (clock.scrub >= 0f) shown.snapTo(target)
                else shown.animateTo(target, tween(300, easing = LinearEasing))
            }
    }
    return shown
}

/** Tap to jump, drag to scrub. Same behaviour as the original seekbar. */
private fun Modifier.seekGestures(
    clock: PlaybackClock,
    padDp: Float,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit
): Modifier = this
    .pointerInput(Unit) {
        val pad = padDp.dp.toPx()
        detectTapGestures { offset ->
            val duration = clock.duration
            if (duration > 0L) {
                val target = (fraction(offset.x, size.width.toFloat(), pad) * duration).toLong()
                clock.position = target
                onScrubEnd(target)
            }
        }
    }
    .pointerInput(Unit) {
        val pad = padDp.dp.toPx()
        fun finish() {
            val f = clock.scrub
            if (f >= 0f) {
                val target = (f * clock.duration).toLong()
                clock.position = target
                clock.scrub = -1f
                onScrubEnd(target)
            }
        }
        detectHorizontalDragGestures(
            onDragStart = { offset ->
                val duration = clock.duration
                if (duration > 0L) {
                    val f = fraction(offset.x, size.width.toFloat(), pad)
                    clock.scrub = f
                    onScrub((f * duration).toLong())
                }
            },
            onDragEnd = { finish() },
            onDragCancel = { finish() },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                if (clock.scrub >= 0f) {
                    val usable = (size.width - 2f * pad).coerceAtLeast(1f)
                    val f = (clock.scrub + dragAmount / usable).coerceIn(0f, 1f)
                    clock.scrub = f
                    onScrub((f * clock.duration).toLong())
                }
            }
        )
    }

/** The time bubble that follows the finger while dragging. */
@Composable
private fun ScrubBubble(clock: PlaybackClock, dragging: Boolean, padDp: Int) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .offset(y = (-42).dp)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                val pad = padDp.dp.roundToPx()
                layout(constraints.maxWidth, placeable.height) {
                    val usable = constraints.maxWidth - 2 * pad
                    val centerX = pad + (clock.scrub.coerceAtLeast(0f) * usable).roundToInt()
                    val maxX = (constraints.maxWidth - placeable.width).coerceAtLeast(0)
                    placeable.place((centerX - placeable.width / 2).coerceIn(0, maxX), 0)
                }
            }
    ) {
        AnimatedVisibility(
            visible = dragging,
            enter = fadeIn(tween(90)) + scaleIn(
                initialScale = 0.6f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f)
            ),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.8f)
        ) {
            Surface(shape = RoundedCornerShape(14.dp), color = scheme.primaryContainer) {
                Text(
                    text = formatDuration((clock.scrub.coerceAtLeast(0f) * clock.duration).toLong()),
                    color = scheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelLarge.merge(TabularNumbers),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

// ---------- Wavy ----------

/**
 * Material 3 wavy seekbar, drawn by the built-in [LinearWavyProgressIndicator].
 * The indicator only shows progress, so touch handling and the position pill are added here.
 * The wave calms down to a flat line while you drag.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WavySeekBar(
    clock: PlaybackClock,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    modifier: Modifier = Modifier,
    inactiveColor: Color = Color.White.copy(alpha = 0.3f)
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val shown = rememberShownFraction(clock)

    val dragging by remember { derivedStateOf { clock.scrub >= 0f } }
    val waveAmount by animateFloatAsState(
        targetValue = if (dragging) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "waveAmount"
    )
    val thumbHeight by animateDpAsState(
        targetValue = if (dragging) 30.dp else 24.dp,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 450f),
        label = "waveThumb"
    )

    Box(modifier.height(44.dp)) {
        LinearWavyProgressIndicator(
            progress = { shown.value },
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            color = activeColor,
            trackColor = inactiveColor,
            gapSize = 8.dp,
            amplitude = { waveAmount }
        )
        Canvas(
            Modifier
                .fillMaxSize()
                .seekGestures(clock, 0f, onScrub, onScrubEnd)
        ) {
            val thumbW = 4.dp.toPx()
            val thumbH = thumbHeight.toPx()
            val x = shown.value * size.width
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(x - thumbW / 2f, size.height / 2f - thumbH / 2f),
                size = Size(thumbW, thumbH),
                cornerRadius = CornerRadius(thumbW / 2f)
            )
        }
        ScrubBubble(clock, dragging, 0)
    }
}

// ---------- Thin minimal ----------

/** A hairline track with a small dot that swells a little while you drag. */
@Composable
fun ThinSeekBar(
    clock: PlaybackClock,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    modifier: Modifier = Modifier,
    inactiveColor: Color = Color.White.copy(alpha = 0.3f)
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val shown = rememberShownFraction(clock)

    val dragging by remember { derivedStateOf { clock.scrub >= 0f } }
    val trackHeight by animateDpAsState(
        targetValue = if (dragging) 4.dp else 2.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "thinTrack"
    )
    val dotRadius by animateDpAsState(
        targetValue = if (dragging) 8.dp else 5.dp,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "thinDot"
    )

    Box(modifier.height(44.dp)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .seekGestures(clock, 8f, onScrub, onScrubEnd)
        ) {
            val pad = 8.dp.toPx()
            val th = trackHeight.toPx()
            val cy = size.height / 2f
            val x = pad + shown.value * (size.width - 2f * pad)

            drawLine(
                color = inactiveColor,
                start = Offset(pad, cy),
                end = Offset(size.width - pad, cy),
                strokeWidth = th,
                cap = StrokeCap.Round
            )
            if (x > pad) {
                drawLine(
                    color = activeColor,
                    start = Offset(pad, cy),
                    end = Offset(x, cy),
                    strokeWidth = th,
                    cap = StrokeCap.Round
                )
            }
            drawCircle(color = activeColor, radius = dotRadius.toPx(), center = Offset(x, cy))
        }
        ScrubBubble(clock, dragging, 8)
    }
}

// ---------- Chunky ----------

/** A fat rounded bar that fills from the left. No thumb: the fill edge is the position. */
@Composable
fun ChunkySeekBar(
    clock: PlaybackClock,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    modifier: Modifier = Modifier,
    inactiveColor: Color = Color.White.copy(alpha = 0.3f)
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val shown = rememberShownFraction(clock)

    val dragging by remember { derivedStateOf { clock.scrub >= 0f } }
    val trackHeight by animateDpAsState(
        targetValue = if (dragging) 26.dp else 18.dp,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 450f),
        label = "chunkyTrack"
    )

    Box(modifier.height(44.dp)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .seekGestures(clock, 0f, onScrub, onScrubEnd)
        ) {
            val th = trackHeight.toPx()
            val cy = size.height / 2f
            val top = cy - th / 2f
            val radius = CornerRadius(th / 2f)
            val x = shown.value * size.width

            drawRoundRect(
                color = inactiveColor,
                topLeft = Offset(0f, top),
                size = Size(size.width, th),
                cornerRadius = radius
            )

            if (x > 0.5f) {
                // Clip to the full pill so the fill keeps the same rounded ends
                val pill = Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = 0f, top = top, right = size.width, bottom = top + th,
                            cornerRadius = radius
                        )
                    )
                }
                clipPath(pill) {
                    // Rounded leading edge, so the fill never ends in a hard corner
                    drawRoundRect(
                        color = activeColor,
                        topLeft = Offset(-th, top),
                        size = Size(x + th, th),
                        cornerRadius = radius
                    )
                }
            }
        }
        ScrubBubble(clock, dragging, 0)
    }
}
