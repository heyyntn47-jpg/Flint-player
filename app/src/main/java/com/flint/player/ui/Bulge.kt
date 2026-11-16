// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.ui

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.flint.player.data.Prefs
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private class BulgeState(
    private val scope: CoroutineScope,
    private val maxPx: Float,
    private val onEdge: () -> Unit
) {
    var offset by mutableFloatStateOf(0f)

    private var springJob: Job? = null

    private fun stopSpring() {
        springJob?.cancel()
        springJob = null
    }

    /** Bounces back to rest. Touching the list again cancels this instantly. */
    private fun springBack() {
        stopSpring()
        if (offset == 0f) return
        springJob = scope.launch {
            animate(
                initialValue = offset,
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.45f, stiffness = 260f)
            ) { value, _ -> offset = value }
            offset = 0f
        }
    }

    /** A fling hit the edge: swell out quickly, then bounce back. */
    private fun bulge(peak: Float) {
        stopSpring()
        springJob = scope.launch {
            animate(
                initialValue = offset,
                targetValue = peak,
                animationSpec = tween(90, easing = LinearOutSlowInEasing)
            ) { value, _ -> offset = value }
            animate(
                initialValue = offset,
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.4f, stiffness = 240f)
            ) { value, _ -> offset = value }
            offset = 0f
        }
    }

    val connection = object : NestedScrollConnection {

        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput) return Offset.Zero

            // A finger is back on the list: take over from any running animation
            stopSpring()

            val current = offset
            // Dragging back while stretched: unwind the stretch first
            if (current != 0f && available.y * current < 0f) {
                val next = current + available.y
                return if (next * current <= 0f) {
                    offset = 0f
                    Offset(0f, -current)
                } else {
                    offset = next
                    Offset(0f, available.y)
                }
            }
            return Offset.Zero
        }

        // The list hit its end while you drag: stretch with rubber-band resistance
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            val dy = available.y
            if (dy == 0f || source != NestedScrollSource.UserInput) return Offset.Zero
            stopSpring()
            if (offset == 0f) onEdge()
            val resistance = (1f - abs(offset) / maxPx).coerceIn(0.08f, 1f)
            offset = (offset + dy * 0.6f * resistance).coerceIn(-maxPx, maxPx)
            return Offset(0f, dy)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (offset != 0f) springBack()
            return Velocity.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            val v = available.y
            if (offset == 0f && springJob == null && abs(v) > 300f) {
                bulge((v * 0.025f).coerceIn(-90f, 90f))
            }
            return Velocity.Zero
        }
    }
}

/** The stretchy effect itself. Use BulgeContainer, which checks the setting first. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BulgeContainerImpl(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val maxPx = with(LocalDensity.current) { 140.dp.toPx() }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val state = remember(maxPx) {
        BulgeState(scope, maxPx) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }

    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        Box(
            modifier = modifier
                .nestedScroll(state.connection)
                .graphicsLayer {
                    val o = state.offset
                    translationY = o
                    val stretch = (abs(o) / size.height.coerceAtLeast(1f)).coerceIn(0f, 0.4f)
                    scaleY = 1f + stretch * 0.5f
                    scaleX = 1f - stretch * 0.25f
                    transformOrigin = TransformOrigin(0.5f, if (o >= 0f) 0f else 1f)
                },
            content = content
        )
    }
}

/** Uses the stretchy effect, or the normal Android scroll ends if it's switched off. */
@Composable
fun BulgeContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    if (Prefs.bulgeEffect) {
        BulgeContainerImpl(modifier, content)
    } else {
        Box(modifier = modifier, content = content)
    }
}
