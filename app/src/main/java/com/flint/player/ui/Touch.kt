// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Density
import com.flint.player.data.Prefs
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.hypot

private fun morphed(base: Shape, progress: Float, targetPx: Float, size: Size, density: Density): Shape {
    if (progress <= 0.001f || base !is CornerBasedShape) return base

    fun mix(corner: CornerSize): CornerSize {
        val current = corner.toPx(size, density)
        return CornerSize(current + (targetPx - current) * progress)
    }
    return RoundedCornerShape(
        topStart = mix(base.topStart),
        topEnd = mix(base.topEnd),
        bottomEnd = mix(base.bottomEnd),
        bottomStart = mix(base.bottomStart)
    )
}

/**
 * Card with full touch feedback: the corners swell, the card squishes,
 * a tonal wash fills it and a ripple spreads from the finger.
 * Add padding AFTER this modifier.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.pressItem(
    shape: Shape,
    container: Color,
    onLongClick: (() -> Unit)? = null,
    pressedRadius: Dp = 30.dp,
    onClick: () -> Unit
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val accent = MaterialTheme.colorScheme.primary
    // Glass style: thin light border around every card (transparent in the Material style)
    val stroke = if (Prefs.glassUi) MaterialTheme.colorScheme.outlineVariant else Color.Transparent

    val press = remember { Animatable(0f) }
    val wave = remember { Animatable(0f) }
    val waveAlpha = remember { Animatable(0f) }
    var origin by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(interaction) {
        interaction.interactions.collect { event ->
            when (event) {
                is PressInteraction.Press -> {
                    origin = event.pressPosition
                    launch { press.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 600f)) }
                    launch {
                        waveAlpha.snapTo(1f)
                        wave.snapTo(0f)
                        wave.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
                    }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    launch { press.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 320f)) }
                    launch { waveAlpha.animateTo(0f, tween(300)) }
                }
            }
        }
    }

    return this
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            onLongClick = onLongClick?.let { long ->
                {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    long()
                }
            },
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
        )
        .graphicsLayer {
            val p = press.value
            val s = 1f - 0.035f * p
            scaleX = s
            scaleY = s
            this.shape = morphed(shape, p, pressedRadius.toPx(), size, this)
            clip = true
        }
        .background(container)
        .drawWithContent {
            drawContent()
            val p = press.value
            if (p > 0.001f) {
                drawRect(accent.copy(alpha = (0.08f * p).coerceIn(0f, 1f)))
            }
            if (stroke.alpha > 0f) {
                // Follows the card's corners while they swell. Drawn twice as wide because
                // the clip cuts off the outer half.
                drawOutline(
                    outline = morphed(shape, p, pressedRadius.toPx(), size, this)
                        .createOutline(size, layoutDirection, this),
                    color = stroke,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            val a = waveAlpha.value
            if (a > 0.001f) {
                drawCircle(
                    color = accent.copy(alpha = (0.14f * a).coerceIn(0f, 1f)),
                    radius = hypot(size.width, size.height) * wave.value,
                    center = origin
                )
            }
        }
}