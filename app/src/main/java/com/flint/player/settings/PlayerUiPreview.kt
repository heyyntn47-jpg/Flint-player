// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** The two player control designs. [id] is what gets saved. */
internal enum class PlayerUiOption(val id: Int, val label: String, val description: String) {
    CLASSIC(0, "Classic", "Solid buttons, with the extras in a settings menu"),
    GLASS(1, "Glass", "Dark glass buttons, every control on screen")
}

/**
 * A small drawing of the player with the chosen controls on it. [progress] moves the seekbar,
 * so the preview feels alive like the real thing. It is read only while drawing.
 */
@Composable
internal fun PlayerUiPreview(option: PlayerUiOption, progress: State<Float>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.primary
    val classicSide = scheme.secondaryContainer
    val classicMain = scheme.primaryContainer
    val onMain = scheme.onPrimaryContainer
    val pill = scheme.surfaceContainerHigh

    Canvas(modifier = modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
        val w = size.width
        val h = size.height
        val dp = 1.dp.toPx()

        // A fake "video": dark, with a soft colored blob so see-through controls show
        drawRect(Brush.verticalGradient(listOf(Color(0xFF232838), Color(0xFF0D0E13))))
        drawCircle(accent.copy(alpha = 0.38f), radius = w * 0.2f, center = Offset(w * 0.3f, h * 0.45f))
        drawCircle(Color(0xFF4FC3F7).copy(alpha = 0.25f), radius = w * 0.16f, center = Offset(w * 0.72f, h * 0.55f))

        when (option) {
            PlayerUiOption.CLASSIC -> {
                // Top and bottom fades, like the real classic UI
                drawRect(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), 0f, h * 0.25f),
                    size = Size(w, h * 0.25f)
                )
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)), h * 0.7f, h),
                    topLeft = Offset(0f, h * 0.7f), size = Size(w, h * 0.3f)
                )
                topLine(w, h, dp, x0 = 0.15f)
                // Settings dot, top right
                drawCircle(Color.White.copy(alpha = 0.9f), 3.5f * dp, Offset(w * 0.93f, h * 0.1f))

                // Center buttons
                drawCircle(classicSide, w * 0.06f, Offset(w * 0.33f, h * 0.5f))
                drawCircle(classicSide, w * 0.06f, Offset(w * 0.67f, h * 0.5f))
                drawCircle(classicMain, w * 0.085f, Offset(w * 0.5f, h * 0.5f))
                playTriangle(w * 0.5f, h * 0.5f, w * 0.03f, onMain)

                // Seekbar, then the little pill of buttons
                seekLine(w, h, dp, progress.value, accent, Color.White.copy(alpha = 0.3f), left = 0.08f, right = 0.92f, y = 0.8f)
                drawRoundRect(
                    pill, Offset(w * 0.56f, h * 0.86f), Size(w * 0.38f, h * 0.11f),
                    CornerRadius(h * 0.055f)
                )
                repeat(5) { i ->
                    drawCircle(scheme.onSurfaceVariant, 2.2f * dp, Offset(w * (0.61f + i * 0.07f), h * 0.915f))
                }
            }

            PlayerUiOption.GLASS -> {
                val fill = Color.Black.copy(alpha = 0.4f)
                val border = Color.White.copy(alpha = 0.25f)
                fun button(cx: Float, cy: Float, r: Float) {
                    drawCircle(fill, r, Offset(cx, cy))
                    drawCircle(border, r, Offset(cx, cy), style = Stroke(dp))
                    drawCircle(Color.White.copy(alpha = 0.9f), r * 0.32f, Offset(cx, cy))
                }

                // Top: back, a plain title (no pill) and the track controls
                button(w * 0.07f, h * 0.11f, h * 0.06f)
                drawLine(
                    Color.White.copy(alpha = 0.85f), Offset(w * 0.15f, h * 0.11f), Offset(w * 0.43f, h * 0.11f),
                    strokeWidth = 3f * dp, cap = StrokeCap.Round
                )
                // Decoder chip, then round buttons
                drawRoundRect(fill, Offset(w * 0.58f, h * 0.05f), Size(w * 0.1f, h * 0.12f), CornerRadius(h * 0.06f))
                drawRoundRect(border, Offset(w * 0.58f, h * 0.05f), Size(w * 0.1f, h * 0.12f), CornerRadius(h * 0.06f), style = Stroke(dp))
                for (i in 0 until 3) button(w * (0.75f + i * 0.09f), h * 0.11f, h * 0.06f)

                // Center buttons
                button(w * 0.34f, h * 0.45f, w * 0.055f)
                button(w * 0.66f, h * 0.45f, w * 0.055f)
                drawCircle(fill, w * 0.085f, Offset(w * 0.5f, h * 0.45f))
                drawCircle(border, w * 0.085f, Offset(w * 0.5f, h * 0.45f), style = Stroke(dp))
                playTriangle(w * 0.5f, h * 0.45f, w * 0.03f, Color.White)

                // Bottom: a row of controls, then the seekbar (the only colored part)
                for (i in 0 until 6) button(w * (0.07f + i * 0.085f), h * 0.74f, h * 0.05f)
                for (i in 0 until 2) button(w * (0.86f + i * 0.085f), h * 0.74f, h * 0.05f)
                seekLine(w, h, dp, progress.value, accent, Color.White.copy(alpha = 0.3f), left = 0.16f, right = 0.84f, y = 0.9f)
                drawCircle(Color.White.copy(alpha = 0.8f), 1.6f * dp, Offset(w * 0.07f, h * 0.9f))
                drawCircle(Color.White.copy(alpha = 0.8f), 1.6f * dp, Offset(w * 0.93f, h * 0.9f))
            }
        }
    }
}

private fun DrawScope.topLine(w: Float, h: Float, dp: Float, x0: Float, y: Float = 0.1f) {
    // Back arrow dot and the title
    if (x0 < 0.16f) drawCircle(Color.White.copy(alpha = 0.9f), 3.5f * dp, Offset(w * 0.07f, h * y))
    drawLine(
        Color.White.copy(alpha = 0.85f), Offset(w * x0, h * y), Offset(w * (x0 + 0.34f), h * y),
        strokeWidth = 3f * dp, cap = StrokeCap.Round
    )
}

private fun DrawScope.seekLine(
    w: Float, h: Float, dp: Float, fraction: Float,
    active: Color, inactive: Color, left: Float, right: Float, y: Float
) {
    val x0 = w * left
    val x1 = w * right
    val yy = h * y
    drawLine(inactive, Offset(x0, yy), Offset(x1, yy), strokeWidth = 3f * dp, cap = StrokeCap.Round)
    val xe = x0 + (x1 - x0) * fraction.coerceIn(0f, 1f)
    drawLine(active, Offset(x0, yy), Offset(xe, yy), strokeWidth = 3f * dp, cap = StrokeCap.Round)
    drawCircle(active, 3.5f * dp, Offset(xe, yy))
}

private fun DrawScope.playTriangle(cx: Float, cy: Float, r: Float, color: Color) {
    val path = Path().apply {
        moveTo(cx - r * 0.7f, cy - r)
        lineTo(cx + r, cy)
        lineTo(cx - r * 0.7f, cy + r)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.glassRect(
    topLeft: Offset, size: Size, radius: Float, fill: Color, border: Color, dp: Float
) {
    drawRoundRect(fill, topLeft, size, CornerRadius(radius))
    drawRoundRect(border, topLeft, size, CornerRadius(radius), style = Stroke(dp))
}
