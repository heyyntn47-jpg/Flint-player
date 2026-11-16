// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flint.player.ui.ExpressiveButton
import com.flint.player.ui.ExpressiveChip
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Audio mixer: equalizer with presets, pre-amp, volume boost and audio delay. */
@Composable
fun MixerSheet(engine: VlcEngine) {
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme

    // ----- Equalizer -----
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Equalizer",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Switch(checked = engine.eqEnabled, onCheckedChange = { engine.setEqEnabled(it) })
    }

    if (engine.eqPresetNames.isNotEmpty()) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            engine.eqPresetNames.forEachIndexed { index, name ->
                ExpressiveChip(
                    label = name,
                    selected = engine.eqEnabled && engine.eqPreset == index,
                    onClick = { engine.applyEqPreset(index) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        engine.eqBands.forEachIndexed { index, db ->
            EqBand(
                label = engine.eqBandLabels.getOrElse(index) { "" },
                value = db,
                active = engine.eqEnabled,
                onChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    engine.setEqBand(index, it)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }

    Spacer(Modifier.height(12.dp))
    SheetHeader("Pre-amp", dbLabel(engine.eqPreamp))
    Slider(
        value = engine.eqPreamp,
        onValueChange = { raw ->
            val snapped = (raw * 2).roundToInt() / 2f
            if (snapped != engine.eqPreamp) engine.setEqPreamp(snapped)
        },
        valueRange = -EQ_MAX_DB..EQ_MAX_DB
    )
    ExpressiveButton(
        text = "Reset equalizer",
        onClick = { engine.resetEq() },
        icon = Icons.Rounded.Refresh,
        tonal = true,
        modifier = Modifier.fillMaxWidth()
    )

    // ----- Volume boost -----
    Spacer(Modifier.height(20.dp))
    SheetHeader("Volume boost", engine.boostPercent.toString() + "%")
    Slider(
        value = engine.boostPercent.toFloat(),
        onValueChange = { raw ->
            val snapped = (raw / 5f).roundToInt() * 5
            if (snapped != engine.boostPercent) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                engine.setBoost(snapped)
            }
        },
        valueRange = 100f..200f
    )
    if (engine.boostPercent > 150) {
        Text(
            text = "High boost can cause distortion",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
        )
    }

    // ----- Audio delay -----
    Spacer(Modifier.height(20.dp))
    val delay = engine.audioDelayMs
    SheetHeader("Audio delay", audioDelayLabel(delay))
    Text(
        text = when {
            delay > 0 -> "Audio plays later"
            delay < 0 -> "Audio plays earlier"
            else -> "In sync with the video"
        },
        style = MaterialTheme.typography.bodySmall,
        color = scheme.onSurfaceVariant
    )
    Slider(
        value = delay.toFloat().coerceIn(-5_000f, 5_000f),
        onValueChange = { raw ->
            val snapped = (raw / 50f).roundToInt() * 50L
            if (snapped != delay) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                engine.setAudioDelay(snapped)
            }
        },
        valueRange = -5_000f..5_000f
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(-500L, -100L, -50L, 50L, 100L, 500L).forEach { step ->
            ExpressiveChip(
                label = (if (step > 0) "+" else "\u2212") + abs(step) + "ms",
                selected = false,
                onClick = { engine.setAudioDelay(delay + step) }
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    ExpressiveButton(
        text = "Reset delay",
        onClick = { engine.setAudioDelay(0L) },
        icon = Icons.Rounded.Refresh,
        tonal = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
}

/** One vertical equalizer band: tap or drag to set its gain. */
@Composable
private fun EqBand(
    label: String,
    value: Float,
    active: Boolean,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var heightPx by remember { mutableFloatStateOf(1f) }
    val currentOnChange by rememberUpdatedState(onChange)
    val currentValue by rememberUpdatedState(value)

    fun fromY(y: Float): Float {
        val frac = 1f - (y / heightPx).coerceIn(0f, 1f)
        val db = (frac * 2f - 1f) * EQ_MAX_DB
        return (db * 2f).roundToInt() / 2f
    }

    val trackColor = scheme.surfaceContainerHighest
    val fillColor = if (active) scheme.primary else scheme.outline
    val thumbColor = if (active) scheme.primary else scheme.outline

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = String.format(Locale.US, "%+.0f", value),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        Canvas(
            modifier = Modifier
                .height(140.dp)
                .width(26.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(trackColor)
                .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val v = fromY(offset.y)
                        if (v != currentValue) currentOnChange(v)
                    }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val v = fromY(offset.y)
                            if (v != currentValue) currentOnChange(v)
                        }
                    ) { change, _ ->
                        change.consume()
                        val v = fromY(change.position.y)
                        if (v != currentValue) currentOnChange(v)
                    }
                }
        ) {
            val mid = size.height / 2f
            val y = size.height * (1f - (currentValue / EQ_MAX_DB + 1f) / 2f)
            val top = minOf(mid, y)
            val h = abs(mid - y)
            if (h > 0f) {
                drawRoundRect(
                    color = if (active) scheme.primaryContainer else scheme.secondaryContainer,
                    topLeft = Offset(0f, top),
                    size = Size(size.width, h),
                    cornerRadius = CornerRadius(size.width / 2f)
                )
            }
            drawLine(
                color = scheme.outlineVariant,
                start = Offset(4f, mid),
                end = Offset(size.width - 4f, mid),
                strokeWidth = 2f
            )
            drawCircle(color = thumbColor, radius = size.width / 2f - 2f, center = Offset(size.width / 2f, y.coerceIn(size.width / 2f, size.height - size.width / 2f)))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

private fun dbLabel(db: Float): String =
    String.format(Locale.US, "%+.1f dB", db).let { if (db == 0f) "0 dB" else it }

private fun audioDelayLabel(ms: Long): String = when {
    ms == 0L -> "0 ms"
    else -> (if (ms > 0) "+" else "\u2212") + abs(ms) + " ms"
}
