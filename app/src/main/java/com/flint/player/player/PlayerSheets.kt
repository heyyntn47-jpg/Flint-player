// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.flint.player.data.Prefs
import com.flint.player.ui.ExpressiveButton
import com.flint.player.ui.ExpressiveChip
import com.flint.player.ui.ExpressiveSegmented
import com.flint.player.ui.bounceClick
import com.flint.player.ui.segmentShape
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Neutral dark colors for the sheets in the glass UI (no theme color). */
private val GlassSheetScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color(0xFF1B1B1F),
    primaryContainer = Color(0xFF3D3D42),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFD0D0D5),
    onSecondary = Color(0xFF1B1B1F),
    secondaryContainer = Color(0xFF34343A),
    onSecondaryContainer = Color.White,
    tertiaryContainer = Color(0xFF45454B),
    onTertiaryContainer = Color.White,
    background = Color(0xFF1C1C20),
    onBackground = Color.White,
    surface = Color(0xFF1C1C20),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF34343A),
    onSurfaceVariant = Color(0xFFC8C8CE),
    outline = Color(0xFF8E8E94),
    outlineVariant = Color(0xFF48484E),
    surfaceContainerLowest = Color(0xFF131316),
    surfaceContainerLow = Color(0xFF1C1C20),
    surfaceContainer = Color(0xFF212125),
    surfaceContainerHigh = Color(0xFF2A2A2F),
    surfaceContainerHighest = Color(0xFF34343A)
)

// ---------- Floating pill that slides up from the bottom ----------

/**
 * Slide-up pill. When [page] changes while it is open, the pill slides away and comes back
 * with the new page, using the same bounce as when it first opens.
 */
@Composable
fun <T> BoxScope.BottomPill(
    visible: Boolean,
    page: T,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(T) -> Unit
) {
    // The glass UI keeps its sheets neutral too: the theme color is only for the seekbar and sliders
    val glass = Prefs.playerUi == 1
    val scheme = if (glass) GlassSheetScheme else MaterialTheme.colorScheme
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.82f).dp
    val dismissDistance = with(LocalDensity.current) { 120.dp.toPx() }
    val dragOffset = remember { Animatable(0f) }
    val pageSlide = remember { Animatable(0f) }   // 0 = in place, 1 = fully below the screen
    val scope = rememberCoroutineScope()

    var shownPage by remember { mutableStateOf(page) }
    val visibleNow by rememberUpdatedState(visible)
    var wasVisible by remember { mutableStateOf(false) }

    LaunchedEffect(page) {
        if (!visibleNow || !wasVisible) {
            shownPage = page
        } else {
            pageSlide.animateTo(1f, tween(150))
            shownPage = page
            pageSlide.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = 380f))
        }
    }
    LaunchedEffect(visible) {
        wasVisible = visible
        if (visible) {
            dragOffset.snapTo(0f)
            pageSlide.snapTo(0f)
        }
    }

    // Dim background, tap to close
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
        )
    }

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f)
        ) { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes
        ) {
        Surface(
            modifier = Modifier
                .padding(12.dp)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .offset { IntOffset(0, dragOffset.value.roundToInt()) }
                .graphicsLayer {
                    translationY = pageSlide.value * (this.size.height + 64.dp.toPx())
                },
            shape = RoundedCornerShape(32.dp),
            // Slightly see-through in the glass UI
            color = if (glass) scheme.surfaceContainerHigh.copy(alpha = 0.88f)
            else scheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            // Same thin light border as the glass player controls, also when the app is in glass style
            border = if (glass || Prefs.glassUi) BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)) else null
        ) {
            Column {
                // Drag handle: pull down to dismiss
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                scope.launch {
                                    dragOffset.snapTo((dragOffset.value + delta).coerceAtLeast(0f))
                                }
                            },
                            onDragStopped = { velocity ->
                                if (dragOffset.value > dismissDistance || velocity > 1800f) {
                                    onDismiss()
                                } else {
                                    dragOffset.animateTo(
                                        0f, spring(dampingRatio = 0.55f, stiffness = 500f)
                                    )
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(scheme.outlineVariant)
                    )
                }
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
                ) {
                    content(shownPage)
                }
            }
        }
        }
    }
}

// ---------- Shared pieces ----------

@Composable
internal fun SheetHeader(title: String, value: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun OptionList(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    labels.forEachIndexed { index, label ->
        if (index > 0) Spacer(Modifier.height(2.dp))
        val selected = index == selectedIndex
        val container by animateColorAsState(
            targetValue = if (selected) scheme.secondaryContainer else scheme.surfaceContainerHighest,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 800f),
            label = "option"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bounceClick(pressedScale = 0.98f, onClick = { onSelect(index) })
                .clip(segmentShape(index, labels.size, outer = 22.dp, inner = 8.dp))
                .background(container)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = if (selected) scheme.onSecondaryContainer else scheme.onSurface
            )
            AnimatedVisibility(
                visible = selected,
                enter = scaleIn(animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f)) + fadeIn(),
                exit = fadeOut()
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = scheme.onSecondaryContainer)
            }
        }
    }
}

// ---------- Sheets ----------

@Composable
fun SpeedSheet(speed: Float, onChange: (Float) -> Unit) {
    val haptics = LocalHapticFeedback.current

    SheetHeader("Playback speed", speedLabel(speed) + "x")
    Slider(
        value = speed,
        onValueChange = { raw ->
            val snapped = (raw * 20).roundToInt() / 20f
            if (snapped != speed) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onChange(snapped)
            }
        },
        valueRange = 0.25f..3f
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f).forEach { preset ->
            ExpressiveChip(
                label = speedLabel(preset) + "x",
                selected = abs(preset - speed) < 0.01f,
                onClick = { onChange(preset) }
            )
        }
    }

    Spacer(Modifier.height(16.dp))
    ExpressiveButton(
        text = "Reset",
        onClick = { onChange(1f) },
        icon = Icons.Rounded.Refresh,
        tonal = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun SubtitleSheet(
    tracks: List<TrackItem>,
    selectedId: Int,
    delayMs: Long,
    onSelect: (Int) -> Unit,
    onDelayChange: (Long) -> Unit,
    onAddFile: () -> Unit,
    onDone: () -> Unit
) {
    val labels = listOf("Off") + tracks.map { it.name }
    val selected = if (selectedId < 0) 0 else tracks.indexOfFirst { it.id == selectedId } + 1

    SheetHeader("Subtitles")
    ExpressiveButton(
        text = "Add subtitle file",
        onClick = {
            onDone()
            onAddFile()
        },
        icon = Icons.Rounded.Add,
        tonal = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(12.dp))
    OptionList(labels, selected.coerceAtLeast(0)) { index ->
        onSelect(if (index == 0) -1 else tracks[index - 1].id)
        onDone()
    }

    Spacer(Modifier.height(20.dp))
    SubtitleDelayControls(delayMs = delayMs, onChange = onDelayChange)
}

/** Shifts subtitle timing: positive shows subtitles later, negative earlier. */
@Composable
private fun SubtitleDelayControls(delayMs: Long, onChange: (Long) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val limit = MAX_SUBTITLE_DELAY_MS

    SheetHeader("Subtitle delay", delayLabel(delayMs))
    Text(
        text = when {
            delayMs > 0 -> "Subtitles appear later"
            delayMs < 0 -> "Subtitles appear earlier"
            else -> "In sync with the video"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Slider(
        value = delayMs.toFloat().coerceIn(-10_000f, 10_000f),
        onValueChange = { raw ->
            val snapped = (raw / 100f).roundToInt() * 100L
            if (snapped != delayMs) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onChange(snapped)
            }
        },
        valueRange = -10_000f..10_000f
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(-1000L, -500L, -100L, 100L, 500L, 1000L).forEach { step ->
            ExpressiveChip(
                label = (if (step > 0) "+" else "\u2212") + (abs(step) / 1000f).toString().trimEnd('0').trimEnd('.') + "s",
                selected = false,
                onClick = { onChange((delayMs + step).coerceIn(-limit, limit)) }
            )
        }
    }

    Spacer(Modifier.height(16.dp))
    ExpressiveButton(
        text = "Reset delay",
        onClick = { onChange(0L) },
        icon = Icons.Rounded.Refresh,
        tonal = true,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun delayLabel(ms: Long): String = when {
    ms == 0L -> "0s"
    else -> (if (ms > 0) "+" else "\u2212") + String.format(java.util.Locale.US, "%.1fs", abs(ms) / 1000f)
}

@Composable
fun AudioSheet(
    tracks: List<TrackItem>,
    selectedId: Int,
    onSelect: (Int) -> Unit,
    onDone: () -> Unit
) {
    val labels = if (tracks.isEmpty()) listOf("Default") else tracks.map { it.name }
    val selected = tracks.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)

    SheetHeader("Audio track")
    OptionList(labels, selected) { index ->
        tracks.getOrNull(index)?.let { onSelect(it.id) }
        onDone()
    }
}

@Composable
fun ChoiceSheet(title: String, options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    SheetHeader(title)
    ExpressiveSegmented(options = options, selectedIndex = selectedIndex, onSelect = onSelect)
    Spacer(Modifier.height(4.dp))
}