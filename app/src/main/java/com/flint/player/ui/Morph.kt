// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** A circle that grows rounded "petals". amplitude 0 = circle, bigger = more cookie-like. */
class CookieShape(
    private val lobes: Int,
    private val amplitude: Float,
    private val rotation: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val base = min(cx, cy)
        val steps = 96
        val fullTurn = (2.0 * PI).toFloat()
        val path = Path()
        for (i in 0..steps) {
            val angle = i.toFloat() / steps * fullTurn
            val wobble = 1f + amplitude * cos(lobes * (angle - rotation))
            val r = base * wobble / (1f + amplitude.coerceAtLeast(0f))
            val x = cx + r * cos(angle)
            val y = cy + r * sin(angle)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** The visual part: a shape that morphs and turns when pressed or switched on. */
@Composable
fun MorphBadge(
    icon: ImageVector,
    description: String?,
    lobes: Int,
    pressed: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    activeTint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    pressedTint: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    restContainer: Color = Color.Transparent,
    activeContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    pressedContainer: Color = MaterialTheme.colorScheme.secondaryContainer,
    enabled: Boolean = true
) {
    val amplitude by animateFloatAsState(
        targetValue = when {
            pressed -> 0.13f
            active -> 0.075f
            else -> 0f
        },
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 280f),
        label = "amplitude"
    )
    val rotation by animateFloatAsState(
        targetValue = (if (active) 0.35f else 0f) + (if (pressed) 0.45f else 0f),
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 220f),
        label = "rotation"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 650f),
        label = "scale"
    )
    val container by animateColorAsState(
        targetValue = when {
            pressed -> pressedContainer
            active -> activeContainer
            else -> restContainer
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 800f),
        label = "container"
    )
    val foreground by animateColorAsState(
        targetValue = when {
            pressed -> pressedTint
            active -> activeTint
            else -> tint
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 800f),
        label = "foreground"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                // Only the background is shaped; the icon on top is never clipped
                val outline = CookieShape(lobes, amplitude, rotation)
                    .createOutline(this.size, layoutDirection, this)
                if (outline is Outline.Generic) {
                    drawPath(path = outline.path, color = container)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) foreground else foreground.copy(alpha = 0.38f),
            modifier = Modifier.size(iconSize)
        )
    }
}

/** Icon button whose shape morphs into a rotating cookie while pressed or switched on. */
@Composable
fun MorphIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    lobes: Int,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    active: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    activeTint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    pressedTint: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    restContainer: Color = Color.Transparent,
    activeContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    pressedContainer: Color = MaterialTheme.colorScheme.secondaryContainer,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .size(size)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
    ) {
        MorphBadge(
            icon = icon,
            description = description,
            lobes = lobes,
            pressed = pressed,
            active = active,
            size = size,
            iconSize = iconSize,
            tint = tint,
            activeTint = activeTint,
            pressedTint = pressedTint,
            restContainer = restContainer,
            activeContainer = activeContainer,
            pressedContainer = pressedContainer,
            enabled = enabled
        )
    }
}