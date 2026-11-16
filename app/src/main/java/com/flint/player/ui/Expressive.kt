// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Connected shapes for grouped lists: big corners on the ends, small in between. */
fun segmentShape(index: Int, count: Int, outer: Dp = 24.dp, inner: Dp = 6.dp): Shape {
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
}

/**
 * Click with a springy squish and haptic tick.
 * Apply clip / background AFTER this modifier so they squish too.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.bounceClick(
    enabled: Boolean = true,
    pressedScale: Float = 0.95f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 600f),
        label = "bounce"
    )
    return this
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
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
            scaleX = scale
            scaleY = scale
        }
}

/** Icon button that squishes and morphs from a circle into a squircle while pressed. */
@Composable
fun ExpressiveIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    container: Color = Color.Transparent,
    pressedContainer: Color = MaterialTheme.colorScheme.secondaryContainer,
    pressedTint: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    restCorner: Int = 50,
    pressedCorner: Int = 28,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current

    val corner by animateIntAsState(
        targetValue = if (pressed) pressedCorner else restCorner,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "corner"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 650f),
        label = "scale"
    )
    val background by animateColorAsState(
        targetValue = if (pressed) pressedContainer else container,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 800f),
        label = "background"
    )
    val foreground by animateColorAsState(
        targetValue = if (pressed) pressedTint else tint,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 800f),
        label = "foreground"
    )

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
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(percent = corner.coerceIn(0, 50)))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(targetState = icon, label = "icon") { current ->
            Icon(
                imageVector = current,
                contentDescription = description,
                tint = if (enabled) foreground else foreground.copy(alpha = 0.38f),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/** Pill button that squares off while pressed. */
@Composable
fun ExpressiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tonal: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current

    val corner by animateIntAsState(
        targetValue = if (pressed) 22 else 50,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "buttonCorner"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 650f),
        label = "buttonScale"
    )
    val container = if (tonal) scheme.secondaryContainer else scheme.primary
    val content = if (tonal) scheme.onSecondaryContainer else scheme.onPrimary

    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(percent = corner.coerceIn(0, 50)))
            .background(container)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = content,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Chip that morphs from a squircle to a full pill when selected. */
@Composable
fun ExpressiveChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val corner by animateIntAsState(
        targetValue = if (selected) 50 else 28,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 450f),
        label = "chipCorner"
    )
    val container by animateColorAsState(
        targetValue = if (selected) scheme.primaryContainer else scheme.surfaceContainerHighest,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 800f),
        label = "chipContainer"
    )
    val content by animateColorAsState(
        targetValue = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 800f),
        label = "chipContent"
    )

    Box(
        modifier = modifier
            .bounceClick(pressedScale = 0.92f, onClick = onClick)
            .clip(RoundedCornerShape(percent = corner.coerceIn(0, 50)))
            .background(container)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            color = content,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Segmented toggle with a highlight that slides and bounces to the chosen option. */
@Composable
fun ExpressiveSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(scheme.surfaceContainerHighest)
            .padding(4.dp)
    ) {
        val itemWidth = maxWidth / options.size
        val x by animateDpAsState(
            targetValue = itemWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
            label = "segmentX"
        )

        Box(
            Modifier
                .offset(x = x)
                .width(itemWidth)
                .height(40.dp)
                .clip(CircleShape)
                .background(scheme.primary)
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val color by animateColorAsState(
                    targetValue = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 800f),
                    label = "segmentText"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(index)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = color,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Switch whose thumb slides and bounces. The whole row is the touch target. */
@Composable
fun ExpressiveSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme

    val thumbCenter by animateDpAsState(
        targetValue = if (checked) 36.dp else 16.dp,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "thumbCenter"
    )
    val thumbSize by animateDpAsState(
        targetValue = if (checked) 24.dp else 16.dp,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 600f),
        label = "thumbSize"
    )
    val track by animateColorAsState(
        targetValue = if (checked) scheme.primary else scheme.surfaceContainerHighest,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 800f),
        label = "track"
    )
    val border by animateColorAsState(
        targetValue = if (checked) scheme.primary else scheme.outline,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 800f),
        label = "border"
    )
    val thumb by animateColorAsState(
        targetValue = if (checked) scheme.onPrimary else scheme.outline,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 800f),
        label = "thumb"
    )

    Box(
        modifier = modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(CircleShape)
            .background(track)
            .border(2.dp, border, CircleShape)
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbCenter - thumbSize / 2)
                .size(thumbSize)
                .background(thumb, CircleShape)
        )
    }
}
