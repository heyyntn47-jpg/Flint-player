// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.home

import com.flint.player.ui.glassBorder
import com.flint.player.ui.stableTopInsets
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flint.player.library.SortField
import com.flint.player.library.ViewMode
import com.flint.player.ui.ExpressiveSegmented
import com.flint.player.ui.bounceClick
import com.flint.player.ui.segmentShape

@Composable
fun SortMenuOverlay(
    onDismiss: () -> Unit,
    field: SortField,
    ascending: Boolean,
    onField: (SortField) -> Unit,
    onAscending: (Boolean) -> Unit,
    viewMode: ViewMode,
    onViewMode: (ViewMode) -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    // 0 -> 1 with a bounce. Drives the scale of the card and the fade of its contents.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 500f))
    }

    BackHandler(onBack = onDismiss)

    // Tap anywhere outside the card to close
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(stableTopInsets())
                .padding(top = 60.dp, end = 12.dp)
                .graphicsLayer {
                    // Scale only. A fading layer would crop the shadow around the card.
                    val s = 0.7f + 0.3f * progress.value
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(1f, 0f)
                }
                .width(272.dp)
                .pointerInput(Unit) { detectTapGestures { } },
            shape = RoundedCornerShape(32.dp),
            color = scheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = glassBorder()
        ) {
            Column(
                Modifier
                    .padding(12.dp)
                    // Only the contents fade in, so the card and its shadow are solid from frame one
                    .graphicsLayer { alpha = (progress.value * 1.6f).coerceIn(0f, 1f) }
            ) {
                Text(
                    text = "View",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 8.dp)
                )
                val modes = ViewMode.entries
                ExpressiveSegmented(
                    options = modes.map { it.label },
                    selectedIndex = modes.indexOf(viewMode),
                    onSelect = { onViewMode(modes[it]) }
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Sort by",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 8.dp)
                )
                val fields = SortField.entries
                fields.forEachIndexed { index, option ->
                    if (index > 0) Spacer(Modifier.height(2.dp))
                    SortOptionRow(
                        label = option.label,
                        selected = option == field,
                        shape = segmentShape(index, fields.size, outer = 22.dp, inner = 8.dp),
                        onClick = { onField(option); onDismiss() }
                    )
                }
                Spacer(Modifier.height(12.dp))
                ExpressiveSegmented(
                    options = listOf("Ascending", "Descending"),
                    selectedIndex = if (ascending) 0 else 1,
                    onSelect = { onAscending(it == 0) }
                )
            }
        }
    }
}

@Composable
private fun SortOptionRow(label: String, selected: Boolean, shape: Shape, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (selected) scheme.secondaryContainer else scheme.surfaceContainerHighest,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 800f),
        label = "sortRow"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(pressedScale = 0.97f, onClick = onClick)
            .clip(shape)
            .background(container)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        AnimatedVisibility(
            visible = selected,
            enter = scaleIn(animationSpec = spring(dampingRatio = 0.45f, stiffness = 500f)) + fadeIn(),
            exit = fadeOut()
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = scheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}