// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.player

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.os.SystemClock
import com.flint.player.library.formatDuration
import com.flint.player.ui.ExpressiveButton
import com.flint.player.ui.ExpressiveChip
import com.flint.player.ui.PillButton

/** Sleep timer state. Read only by the menu, so the countdown never redraws the whole player. */
@Stable
class SleepTimerState {
    /** When the timer ends, in SystemClock.elapsedRealtime() time. 0 = no timer. */
    var endAtMs by mutableLongStateOf(0L)
    var endOfVideo by mutableStateOf(false)
    var remainingMs by mutableLongStateOf(0L)
    var minutes by mutableIntStateOf(0)

    val active: Boolean get() = endAtMs > 0L || endOfVideo

    fun startMinutes(value: Int) {
        minutes = value
        endOfVideo = false
        remainingMs = value * 60_000L
        endAtMs = SystemClock.elapsedRealtime() + remainingMs
    }

    fun startEndOfVideo() {
        minutes = 0
        endAtMs = 0L
        remainingMs = 0L
        endOfVideo = true
    }

    fun cancel() {
        minutes = 0
        endAtMs = 0L
        remainingMs = 0L
        endOfVideo = false
    }
}

@Composable
fun SleepTimerSheet(
    sleep: SleepTimerState,
    onMinutes: (Int) -> Unit,
    onEndOfVideo: () -> Unit,
    onCancel: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val status = when {
        sleep.endOfVideo -> "After this video"
        sleep.endAtMs > 0L -> "Stops in ${formatDuration(sleep.remainingMs)}"
        else -> "Off"
    }

    SheetHeader("Sleep timer", status)

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(10, 15, 30, 45, 60, 90).forEach { value ->
            ExpressiveChip(
                label = "$value min",
                selected = sleep.endAtMs > 0L && sleep.minutes == value,
                onClick = { onMinutes(value) }
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    ExpressiveButton(
        text = "After this video",
        onClick = onEndOfVideo,
        icon = Icons.Rounded.SkipNext,
        tonal = !sleep.endOfVideo,
        modifier = Modifier.fillMaxWidth()
    )

    if (sleep.active) {
        Spacer(Modifier.height(8.dp))
        PillButton(
            text = "Turn off timer",
            onClick = onCancel,
            container = scheme.errorContainer,
            content = scheme.onErrorContainer,
            modifier = Modifier.fillMaxWidth()
        )
    }
}