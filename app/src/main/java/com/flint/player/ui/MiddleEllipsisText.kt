// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit

/**
 * Text that, when too long, cuts out the middle instead of the end, so the part that tells
 * files apart stays visible: "Episode 12 - The Long Ti…ame (1080p).mkv" rather than
 * "Episode 12 - The Long Title Na…". Short text is shown untouched.
 */
@Composable
fun MiddleEllipsisText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    maxLines: Int = 1,
    /** How many characters at the end are always kept (the file extension is added on top). */
    minTail: Int = 8
) {
    val measurer = rememberTextMeasurer()
    val resolvedColor = if (color != Color.Unspecified) color else LocalContentColor.current
    val resolved = remember(style, resolvedColor, fontWeight, lineHeight) {
        style.copy(
            color = resolvedColor,
            fontWeight = fontWeight ?: style.fontWeight,
            lineHeight = if (lineHeight != TextUnit.Unspecified) lineHeight else style.lineHeight
        )
    }

    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth
        val shown = remember(text, width, resolved, maxLines, minTail) {
            if (width == Constraints.Infinity) text
            else fitMiddle(measurer, text, resolved, width, maxLines, minTail)
        }
        Text(
            text = shown,
            style = resolved,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun fits(measurer: TextMeasurer, s: String, style: TextStyle, width: Int, maxLines: Int): Boolean =
    !measurer.measure(
        text = AnnotatedString(s),
        style = style,
        overflow = TextOverflow.Ellipsis,
        maxLines = maxLines,
        constraints = Constraints(maxWidth = width)
    ).hasVisualOverflow

private fun fitMiddle(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    width: Int,
    maxLines: Int,
    minTail: Int
): String {
    if (text.length < 4 || fits(measurer, text, style, width, maxLines)) return text

    // Keep the extension plus a few characters before it
    val dot = text.lastIndexOf('.')
    val ext = if (dot > 0 && text.length - dot <= 6) text.length - dot else 0
    val tailLen = (ext + minTail).coerceAtMost(text.length / 2)
    val tail = text.takeLast(tailLen)
    val headMax = text.length - tailLen

    // Longest head that still fits (fitting is monotonic, so a binary search works)
    var lo = 0
    var hi = headMax
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        val candidate = text.substring(0, mid).trimEnd() + "…" + tail
        if (fits(measurer, candidate, style, width, maxLines)) lo = mid else hi = mid - 1
    }
    return text.substring(0, lo).trimEnd() + "…" + tail
}
