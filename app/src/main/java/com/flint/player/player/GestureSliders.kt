// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.flint.player.data.Prefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** Holds a slider's value outside the screen, so moving it never redraws the whole player. */
@Stable
class SliderHud {
    var fraction by mutableFloatStateOf(0f)
    var seq by mutableIntStateOf(0)

    fun update(value: Float) {
        fraction = value
        seq++
    }
}

/** Holds the little message pill's text (seek time, repeat mode and so on). */
@Stable
class MessageHud {
    var text by mutableStateOf("")
    var seq by mutableIntStateOf(0)

    fun show(message: String) {
        text = message
        seq++
    }
}

fun brightnessIcon(fraction: Float): ImageVector = when {
    fraction < 0.34f -> Icons.Rounded.BrightnessLow
    fraction < 0.67f -> Icons.Rounded.BrightnessMedium
    else -> Icons.Rounded.BrightnessHigh
}

fun volumeIcon(fraction: Float): ImageVector = when {
    fraction <= 0f -> Icons.AutoMirrored.Rounded.VolumeOff
    fraction < 0.4f -> Icons.AutoMirrored.Rounded.VolumeMute
    fraction < 0.75f -> Icons.AutoMirrored.Rounded.VolumeDown
    else -> Icons.AutoMirrored.Rounded.VolumeUp
}

/** The volume / brightness slider designs the user can pick in Settings. [id] is what gets saved. */
enum class GestureSliderStyle(val id: Int, val label: String) {
    PILL(0, "Pill"),
    LINES(1, "Lines"),
    WAVES(2, "Waves"),
    CIRCLE(3, "Circle");

    companion object {
        fun fromId(id: Int): GestureSliderStyle = entries.firstOrNull { it.id == id } ?: PILL
    }
}

/** The accent, lightened, so the filled part of a slider is soft instead of heavy. */
@Composable
private fun sliderFillColor(): Color = lerp(MaterialTheme.colorScheme.primary, Color.White, 0.35f)

/** Dark or white, whichever reads better on top of [fill]. */
private fun onFillColor(fill: Color): Color =
    if (fill.luminance() > 0.5f) Color(0xFF1B1B1F) else Color.White

/** Shows a slider over the video for a moment whenever [hud] changes. */
@Composable
fun GestureSlider(
    hud: SliderHud,
    iconFor: (Float) -> ImageVector,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(hud) {
        snapshotFlow { hud.seq }.collectLatest { seq ->
            if (seq > 0) {
                visible = true
                delay(800)
                visible = false
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(90)) + scaleIn(
            initialScale = 0.8f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f)
        ),
        exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.9f)
    ) {
        GestureSliderContent(
            style = GestureSliderStyle.fromId(Prefs.gestureSliderStyle),
            fraction = { hud.fraction },
            iconFor = iconFor
        )
    }
}

/**
 * The slider itself, without any show/hide animation. The player wraps it in [GestureSlider];
 * Settings uses it directly for the previews. [fraction] is read lazily, so a moving value
 * only redraws the slider.
 */
@Composable
fun GestureSliderContent(
    style: GestureSliderStyle,
    fraction: () -> Float,
    iconFor: (Float) -> ImageVector,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val fill = sliderFillColor()
    val onFill = onFillColor(fill)

    // These only change the screen when the icon or its color actually changes
    val icon by remember(fraction, iconFor) { derivedStateOf { iconFor(fraction()) } }
    val iconOnFill by remember(fraction) { derivedStateOf { fraction() > 0.27f } }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        when (style) {
            GestureSliderStyle.PILL, GestureSliderStyle.WAVES -> {
                val waves = style == GestureSliderStyle.WAVES
                PercentPill(fraction)
                Spacer(Modifier.height(8.dp))
                val phase = if (waves) {
                    rememberInfiniteTransition(label = "wave").animateFloat(
                        initialValue = 0f,
                        targetValue = (2.0 * PI).toFloat(),
                        animationSpec = infiniteRepeatable(tween(1700, easing = LinearEasing)),
                        label = "wavePhase"
                    )
                } else null
                val track = scheme.surfaceContainerHighest
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(150.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(track)
                        .drawBehind {
                            val f = fraction().coerceIn(0f, 1f)
                            val h = size.height * f
                            if (h <= 0f) return@drawBehind
                            if (phase == null) {
                                drawRoundRect(
                                    color = fill,
                                    topLeft = Offset(0f, size.height - h),
                                    size = Size(size.width, h),
                                    cornerRadius = CornerRadius(size.width / 2f)
                                )
                            } else {
                                // Flat when nearly empty or full, so the wave never shows a gap
                                val edge = minOf(1f, minOf(f, 1f - f) * 12f)
                                val amp = 3.5.dp.toPx() * edge
                                val top = size.height - h
                                drawWave(fill.copy(alpha = 0.45f), top, amp * 0.8f, phase.value + PI.toFloat())
                                drawWave(fill, top, amp, phase.value)
                            }
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    SliderIcon(
                        icon = icon,
                        tint = if (iconOnFill) onFill else scheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 11.dp)
                    )
                }
            }

            GestureSliderStyle.LINES -> {
                Text(
                    text = "${(fraction() * 100).roundToInt()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(150.dp)
                        .drawBehind {
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.3f),
                                cornerRadius = CornerRadius(size.width / 2f)
                            )
                            val h = size.height * fraction().coerceIn(0f, 1f)
                            if (h > 0f) {
                                drawRoundRect(
                                    color = fill,
                                    topLeft = Offset(0f, size.height - h),
                                    size = Size(size.width, h),
                                    cornerRadius = CornerRadius(size.width / 2f)
                                )
                            }
                        }
                )
                Spacer(Modifier.height(10.dp))
                SliderIcon(icon = icon, tint = Color.White, modifier = Modifier)
            }

            GestureSliderStyle.CIRCLE -> {
                PercentPill(fraction)
                Spacer(Modifier.height(8.dp))
                val track = scheme.surfaceContainerHighest
                val ringTrack = scheme.onSurface.copy(alpha = 0.12f)
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(track)
                        .drawBehind {
                            val stroke = 5.dp.toPx()
                            val inset = stroke / 2f + 3.dp.toPx()
                            val d = size.minDimension - inset * 2f
                            val topLeft = Offset(inset, inset)
                            val arcSize = Size(d, d)
                            drawArc(
                                color = ringTrack,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(stroke)
                            )
                            val f = fraction().coerceIn(0f, 1f)
                            if (f > 0f) {
                                drawArc(
                                    color = fill,
                                    startAngle = -90f,
                                    sweepAngle = 360f * f,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(stroke, cap = StrokeCap.Round)
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    SliderIcon(icon = icon, tint = scheme.onSurface, modifier = Modifier)
                }
            }
        }
    }
}

/** One horizontal wave filling everything below [top]. */
private fun DrawScope.drawWave(color: Color, top: Float, amplitude: Float, phase: Float) {
    val step = 2.dp.toPx()
    val path = Path().apply {
        moveTo(0f, size.height)
        var x = 0f
        while (x < size.width + step) {
            val xx = x.coerceAtMost(size.width)
            lineTo(xx, top + amplitude * sin(xx / size.width * 2f * PI.toFloat() + phase))
            x += step
        }
        lineTo(size.width, size.height)
        close()
    }
    drawPath(path, color)
}

@Composable
private fun PercentPill(fraction: () -> Float) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = scheme.secondaryContainer
    ) {
        Text(
            text = "${(fraction() * 100).roundToInt()}",
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SliderIcon(icon: ImageVector, tint: Color, modifier: Modifier) {
    Crossfade(targetState = icon, modifier = modifier, label = "gestureIcon") { current ->
        Icon(
            imageVector = current,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** The pill that briefly shows messages like "+10s" or "Repeat all". */
@Composable
fun MessageHudPill(hud: MessageHud, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(hud) {
        snapshotFlow { hud.seq }.collectLatest { seq ->
            if (seq > 0) {
                visible = true
                delay(1000)
                visible = false
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(100)) + scaleIn(
            initialScale = 0.7f,
            animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f)
        ),
        exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.9f)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = scheme.surfaceContainerHigh,
            tonalElevation = 0.dp
        ) {
            Text(
                text = hud.text,
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
    }
}