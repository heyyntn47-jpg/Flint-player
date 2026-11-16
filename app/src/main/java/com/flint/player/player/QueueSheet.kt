// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.player

import com.flint.player.ui.MiddleEllipsisText
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flint.player.ui.bounceClick
import com.flint.player.ui.segmentShape

@Composable
fun QueueSheet(titles: List<String>, currentIndex: Int, onPick: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Queue",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "${currentIndex + 1} / ${titles.size}",
            style = MaterialTheme.typography.titleMedium,
            color = scheme.primary
        )
    }

    titles.forEachIndexed { index, title ->
        if (index > 0) Spacer(Modifier.height(2.dp))
        val current = index == currentIndex
        val container by animateColorAsState(
            targetValue = if (current) scheme.secondaryContainer else scheme.surfaceContainerHighest,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 800f),
            label = "queueRow"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bounceClick(pressedScale = 0.98f, onClick = { onPick(index) })
                .clip(segmentShape(index, titles.size, outer = 22.dp, inner = 8.dp))
                .background(container)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.labelLarge,
                color = if (current) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                modifier = Modifier.width(30.dp)
            )
            MiddleEllipsisText(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (current) FontWeight.Medium else FontWeight.Normal,
                color = if (current) scheme.onSecondaryContainer else scheme.onSurface,
                maxLines = 2
            )
            if (current) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Playing now",
                    tint = scheme.onSecondaryContainer
                )
            }
        }
    }
}