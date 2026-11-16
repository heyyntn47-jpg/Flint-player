// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.ui

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.flint.player.data.Prefs
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private val Presets = listOf(
    0xFF2F6BFF, 0xFF5B5BD6, 0xFF9B51E0, 0xFFE5468F, 0xFFE5383B, 0xFFF97316,
    0xFFF2B705, 0xFF34A853, 0xFF00A896, 0xFF06B6D4, 0xFF64748B
).map { Color(it) }

private fun hexOf(color: Color): String = "#%06X".format(color.toArgb() and 0xFFFFFF)

private fun parseHex(text: String): Color? {
    val clean = text.trim().removePrefix("#")
    if (clean.length != 6) return null
    val value = clean.toLongOrNull(16) ?: return null
    return Color(0xFF000000L or value)
}

/** Color picker drawn inside the app window. The dialog itself previews the new colors live. */
@Composable
fun ColorPickerDialog(
    initial: Color,
    onApply: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val start = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var bright by remember { mutableFloatStateOf(start[2]) }
    var hexText by remember { mutableStateOf(hexOf(initial)) }

    val picked = Color.hsv(hue.coerceIn(0f, 360f), sat.coerceIn(0f, 1f), bright.coerceIn(0f, 1f))
    val dark = isAppDark()
    val context = LocalContext.current
    val preview = seedColorScheme(picked, dark, dark && Prefs.pureBlack)

    LaunchedEffect(picked) { hexText = hexOf(picked) }

    fun setFrom(color: Color) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        hue = hsv[0]
        sat = hsv[1]
        bright = hsv[2]
    }

    ModalOverlay(onDismiss = onDismiss) {
        // Everything inside uses the new colors, so you see the result as you pick
        MaterialTheme(colorScheme = preview, typography = FlintTypography) {
            val scheme = MaterialTheme.colorScheme
            val pop = rememberPopProgress()
            val config = LocalConfiguration.current
            val landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
            val maxHeight = (config.screenHeightDp - (if (landscape) 56 else 24)).dp
            val focus = LocalFocusManager.current

            val wheel: @Composable (Modifier) -> Unit = { mod ->
                ColorWheel(
                    hue = hue,
                    saturation = sat,
                    value = bright,
                    onChange = { newHue, newSat ->
                        hue = newHue
                        sat = newSat
                    },
                    modifier = mod
                )
            }
            val swatches: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Presets.chunked(6).forEach { rowColors ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowColors.forEach { preset ->
                                Swatch(
                                    color = preset,
                                    selected = preset.toArgb() == picked.toArgb(),
                                    onClick = { setFrom(preset) }
                                )
                            }
                        }
                    }
                }
            }
            val hexField: @Composable () -> Unit = {
                PillTextField(
                    value = hexText,
                    onValueChange = { text ->
                        hexText = text
                        parseHex(text)?.let { setFrom(it) }
                    },
                    placeholder = "#RRGGBB",
                    onDone = { focus.clearFocus() }
                )
            }

            val actions: @Composable () -> Unit = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PillButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        container = scheme.secondaryContainer,
                        content = scheme.onSecondaryContainer
                    )
                    PillButton(
                        text = "Apply",
                        onClick = {
                            // Save right here so Apply works no matter what the caller does
                            Prefs.set(Prefs.CUSTOM_COLOR, picked.toArgb())
                            Toast.makeText(context, "Color applied", Toast.LENGTH_SHORT).show()
                            onApply(picked)
                            onDismiss()
                        },
                        container = scheme.primary,
                        content = scheme.onPrimary
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .padding(if (landscape) 8.dp else 24.dp)
                    .widthIn(max = if (landscape) 560.dp else 380.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .popScale(pop)
                    .pointerInput(Unit) { detectTapGestures { } },
                shape = RoundedCornerShape(36.dp),
                color = scheme.surfaceContainerHigh,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                // Nothing in here scrolls. Landscape puts the wheel beside the controls to fit.
                Column(
                    Modifier
                        .popFade(pop)
                        .padding(horizontal = 24.dp, vertical = if (landscape) 12.dp else 22.dp)
                ) {
                    if (landscape) {
                        // Buttons live in the header so they can never be pushed off the screen
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Custom color",
                                style = MaterialTheme.typography.titleLarge,
                                color = scheme.onSurface
                            )
                            actions()
                        }
                    } else {
                        Text(
                            text = "Custom color",
                            style = MaterialTheme.typography.headlineSmall,
                            color = scheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(if (landscape) 8.dp else 16.dp))

                    if (landscape) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            wheel(
                                Modifier
                                    .heightIn(max = 200.dp)
                                    .fillMaxHeight()
                                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TokenStrip()
                                swatches()
                                hexField()
                            }
                        }
                    } else {
                        // The wheel takes whatever height is left, so the buttons always stay visible
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                            contentAlignment = Alignment.Center
                        ) {
                            wheel(
                                Modifier
                                    .heightIn(max = 220.dp)
                                    .fillMaxHeight()
                                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        TokenStrip()
                        Spacer(Modifier.height(16.dp))
                        swatches()
                        Spacer(Modifier.height(16.dp))
                        hexField()
                    }

                    if (!landscape) {
                        Spacer(Modifier.height(24.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            actions()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.18f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 500f),
        label = "swatchScale"
    )
    Box(
        Modifier
            .size(40.dp)
            .bounceClick(pressedScale = 0.88f, onClick = onClick)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(color)
            .then(if (selected) Modifier.border(3.dp, scheme.onSurface, CircleShape) else Modifier)
    )
}

/** Hue goes around the circle, strength (saturation) grows from the center outwards. */
@Composable
private fun ColorWheel(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnChange by rememberUpdatedState(onChange)
    val handleColor = Color.hsv(hue.coerceIn(0f, 360f), saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f))

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()

                fun update(p: Offset) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val radius = min(cx, cy) - 14.dp.toPx()
                    val dx = p.x - cx
                    val dy = p.y - cy
                    var angle = atan2(dy, dx) * 180f / PI.toFloat()
                    if (angle < 0f) angle += 360f
                    currentOnChange(angle, (hypot(dx, dy) / radius).coerceIn(0f, 1f))
                }

                update(down.position)
                down.consume()
                do {
                    val event = awaitPointerEvent()
                    event.changes.forEach { change ->
                        if (change.pressed) {
                            update(change.position)
                            change.consume()
                        }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
    ) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = this.size.minDimension / 2f - 14.dp.toPx()
        val v = value.coerceIn(0f, 1f)

        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color.hsv(0f, 1f, v), Color.hsv(60f, 1f, v), Color.hsv(120f, 1f, v),
                    Color.hsv(180f, 1f, v), Color.hsv(240f, 1f, v), Color.hsv(300f, 1f, v),
                    Color.hsv(0f, 1f, v)
                ),
                center = center
            ),
            radius = radius,
            center = center
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.hsv(0f, 0f, v), Color.Transparent),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )

        val rad = hue * PI.toFloat() / 180f
        val handle = Offset(
            center.x + cos(rad) * saturation.coerceIn(0f, 1f) * radius,
            center.y + sin(rad) * saturation.coerceIn(0f, 1f) * radius
        )
        drawCircle(Color.White, radius = 14.dp.toPx(), center = handle)
        drawCircle(handleColor, radius = 10.dp.toPx(), center = handle)
    }
}

/** Live preview of the generated palette: primary, secondaryContainer, tertiaryContainer, surfaceContainerHigh. */
@Composable
private fun TokenStrip() {
    val scheme = MaterialTheme.colorScheme
    val tokens = listOf(
        scheme.primary to scheme.onPrimary,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
        scheme.surfaceContainerHighest to scheme.onSurface
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(18.dp)),
    ) {
        tokens.forEach { (container, _) ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(container)
            )
        }
    }
}
