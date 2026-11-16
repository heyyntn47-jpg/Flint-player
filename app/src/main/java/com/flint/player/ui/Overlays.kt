// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Holds every open dialog / menu. They are drawn inside the main window
 * (not as separate Android windows), so the status bar can never pop back up.
 */
@Stable
class OverlayHost {
    private class Entry(val token: Any, val content: @Composable () -> Unit)

    private val entries = mutableStateListOf<Entry>()

    fun put(token: Any, content: @Composable () -> Unit) {
        val entry = Entry(token, content)
        val index = entries.indexOfFirst { it.token === token }
        if (index >= 0) entries[index] = entry else entries.add(entry)
    }

    fun remove(token: Any) {
        entries.removeAll { it.token === token }
    }

    @Composable
    fun Render() {
        entries.forEach { entry ->
            key(entry.token) { entry.content() }
        }
    }
}

val LocalOverlayHost = staticCompositionLocalOf<OverlayHost> {
    error("No OverlayHost provided")
}

/** Shows [content] centered over the whole app with a dimmed, tap-to-close background. */
@Composable
fun ModalOverlay(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val host = LocalOverlayHost.current
    val token = remember { Any() }

    SideEffect {
        host.put(token) { OverlayScrim(onDismiss, content) }
    }
    DisposableEffect(token) {
        onDispose { host.remove(token) }
    }
}

@Composable
private fun OverlayScrim(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val dismiss by rememberUpdatedState(onDismiss)
    val dim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { dim.animateTo(0.34f, tween(160)) }

    BackHandler { dismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = dim.value)) }
            .pointerInput(Unit) { detectTapGestures { dismiss() } },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** 0 -> 1 with a bounce, started when the overlay appears. */
@Composable
fun rememberPopProgress(): Animatable<Float, AnimationVector1D> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 450f))
    }
    return progress
}

/** Scale only. A fading layer would crop the shadow around the card. */
fun Modifier.popScale(progress: Animatable<Float, AnimationVector1D>, from: Float = 0.8f): Modifier =
    graphicsLayer {
        val s = from + (1f - from) * progress.value
        scaleX = s
        scaleY = s
    }

/** Fades the contents only, so the card and its shadow are solid from the first frame. */
fun Modifier.popFade(progress: Animatable<Float, AnimationVector1D>): Modifier =
    graphicsLayer { alpha = (progress.value * 1.6f).coerceIn(0f, 1f) }