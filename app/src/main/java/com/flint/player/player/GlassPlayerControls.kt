// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.CastConnected
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.ScreenLockLandscape
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flint.player.data.Prefs
import com.flint.player.ui.MiddleEllipsisText

// Neutral, see-through controls. The theme color is not used on any button: it only shows
// on the seekbar and on the volume / brightness sliders.
private val GlassFill = Color.Black.copy(alpha = 0.38f)
private val GlassActive = Color.White.copy(alpha = 0.30f)
private val GlassBorder = Color.White.copy(alpha = 0.20f)

private val ButtonSize = 40.dp

@Composable
private fun GlassButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = ButtonSize,
    iconSize: Dp = 20.dp,
    active: Boolean = false,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 600f),
        label = "glassScale"
    )
    Box(
        modifier = modifier
            .requiredSize(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.4f
            }
            .clip(CircleShape)
            .background(if (active || pressed) GlassActive else GlassFill)
            .border(1.dp, GlassBorder, CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClickLabel = description,
                role = Role.Button
            ) {
                if (Prefs.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

private val LocalToolSize = compositionLocalOf { 38.dp }
private val ToolGap = 4.dp

/** A row of separate glass buttons. */
@Composable
private fun ToolRow(content: @Composable RowScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ToolGap),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/** A tool button: its own glass pill, sized to fit the screen. */
@Composable
private fun GlassTool(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    active: Boolean = false,
    enabled: Boolean = true
) {
    val size = LocalToolSize.current
    GlassButton(
        icon = icon,
        description = description,
        onClick = onClick,
        size = size,
        iconSize = size * 0.55f,
        active = active,
        enabled = enabled
    )
}

/** The decoder badge: a short text on its own glass pill. */
@Composable
private fun GlassTextTool(text: String, description: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .height(LocalToolSize.current)
            .clip(shape)
            .background(if (pressed) GlassActive else GlassFill)
            .border(1.dp, GlassBorder, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = description,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1)
    }
}

/**
 * The glass player UI: translucent round controls over the video, with every player control
 * (decoder, audio, subtitles, cast, queue, lock, rotate, speed, repeat, shuffle, sleep, mixer,
 * aspect, pop-out, ...) right on the screen and no separate settings menu. Only the seekbar
 * (and the volume / brightness sliders) use the theme color.
 */
@Composable
fun GlassPlayerControls(
    visible: Boolean,
    title: String,
    isPlaying: Boolean,
    clock: PlaybackClock,
    orientation: OrientationMode,
    aspect: AspectMode,
    isPlaylist: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    repeatMode: Int,
    shuffleOn: Boolean,
    speedText: String,
    backgroundPlay: Boolean,
    decoderText: String,
    castName: String?,
    pipSupported: Boolean,
    sleepActive: Boolean,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    onLock: () -> Unit,
    onOrientation: () -> Unit,
    onAspect: () -> Unit,
    onRepeat: () -> Unit,
    onShuffle: () -> Unit,
    onQueue: () -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onSpeed: () -> Unit,
    onSleep: () -> Unit,
    onCast: () -> Unit,
    onMixer: () -> Unit,
    onBackgroundPlay: () -> Unit,
    onDecoder: () -> Unit,
    onPip: () -> Unit,
    onAbRepeat: () -> Unit,
    onScreenshot: () -> Unit,
    onInteract: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Three areas hold every control: top right, bottom left and bottom right. The buttons
        // shrink to whatever the screen width allows, so a narrow portrait phone still fits
        // everything, playlist buttons included. Wide screens get full-size buttons.
        val leftCount = 5 + if (isPlaylist) 1 else 0     // speed, repeat, A-B, sleep, background (+ shuffle)
        val rightCount = 4 + if (pipSupported) 1 else 0  // aspect, rotation, screenshot, lock (+ pop-out)
        val bottomCount = leftCount + rightCount
        val topCount = 4 + if (isPlaylist) 1 else 0      // audio, subtitles, cast, mixer (+ queue)
        // Portrait gets the stacked layout; landscape keeps one row, whatever the screen width
        val compact = maxHeight > maxWidth
        // Top: screen padding, back button, a little room for the title, the decoder badge
        val topSize = ((maxWidth - 24.dp - 36.dp - 16.dp - 72.dp - 44.dp - ToolGap * topCount) / topCount)
            .coerceIn(22.dp, 38.dp)
        // Bottom: on a phone the tools stack in two rows of about the same length, so they can be
        // bigger. On a wide screen they share one row (playback left, screen tools right).
        val bottomSize = if (compact) {
            val perRow = (bottomCount + 1) / 2
            ((maxWidth - 24.dp - 8.dp * (perRow - 1)) / perRow).coerceIn(24.dp, 40.dp)
        } else {
            ((maxWidth - 24.dp - 8.dp - ToolGap * (bottomCount - 2)) / bottomCount).coerceIn(22.dp, 38.dp)
        }

        CompositionLocalProvider(LocalToolSize provides topSize) {

            // Top: back, the plain title, and the track tools in the top-right corner
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn() + slideInVertically(
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f)
                ) { -it / 2 },
                exit = fadeOut() + slideOutVertically { -it / 2 }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent)
                            )
                        )
                        .displayCutoutPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassButton(
                        Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack,
                        size = 36.dp, iconSize = 20.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    MiddleEllipsisText(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(
                            shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 6f)
                        ),
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    ToolRow {
                        GlassTextTool(decoderText, "Decoder", onDecoder)
                        GlassTool(Icons.Outlined.Audiotrack, "Audio", onAudio)
                        GlassTool(Icons.Outlined.Subtitles, "Subtitles", onSubtitles)
                        GlassTool(
                            if (castName != null) Icons.Outlined.CastConnected else Icons.Outlined.Cast,
                            "Cast", onCast, active = castName != null
                        )
                        GlassTool(Icons.Outlined.Tune, "Mixer", onMixer)
                        if (isPlaylist) {
                            GlassTool(Icons.AutoMirrored.Outlined.PlaylistPlay, "Queue", onQueue)
                        }
                    }
                }
            }

            // Center: transport controls
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(Alignment.Center),
                enter = fadeIn() + scaleIn(
                    initialScale = 0.85f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 450f)
                ),
                exit = fadeOut() + scaleOut(targetScale = 0.92f)
            ) {
                val skip = if (compact) 42.dp else 48.dp
                val seek = if (compact) 46.dp else 52.dp
                val play = if (compact) 68.dp else 76.dp
                Row(
                    horizontalArrangement = Arrangement.spacedBy(
                        if (compact) (if (isPlaylist) 6.dp else 14.dp) else (if (isPlaylist) 10.dp else 16.dp)
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isPlaylist) {
                        GlassButton(
                            Icons.Rounded.SkipPrevious, "Previous", onPrevious,
                            size = skip, iconSize = 24.dp, enabled = hasPrevious
                        )
                    }
                    GlassButton(
                        Icons.Rounded.Replay10, "Back 10 seconds", { onSeekBy(-10_000L) },
                        size = seek, iconSize = 26.dp
                    )
                    GlassButton(
                        icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        description = if (isPlaying) "Pause" else "Play",
                        onClick = onPlayPause,
                        size = play,
                        iconSize = 38.dp
                    )
                    GlassButton(
                        Icons.Rounded.Forward10, "Forward 10 seconds", { onSeekBy(10_000L) },
                        size = seek, iconSize = 26.dp
                    )
                    if (isPlaylist) {
                        GlassButton(
                            Icons.Rounded.SkipNext, "Next", onNext,
                            size = skip, iconSize = 24.dp, enabled = hasNext
                        )
                    }
                }
            }

            // Bottom: playback tools on the left, screen tools on the right, then the seekbar
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn() + slideInVertically(
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f)
                ) { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))
                            )
                        )
                        .displayCutoutPadding()
                        .padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 10.dp)
                ) {
                    // Playback tools first, then screen tools
                    val bottomTools = buildList<@Composable () -> Unit> {
                        add { GlassTool(Icons.Outlined.Speed, "Speed", onSpeed, active = speedText != "1x") }
                        add {
                            GlassTool(
                                if (repeatMode == Repeat.ONE) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat,
                                "Repeat", onRepeat, active = repeatMode != Repeat.OFF
                            )
                        }
                        if (isPlaylist) {
                            add { GlassTool(Icons.Outlined.Shuffle, "Shuffle", onShuffle, active = shuffleOn) }
                        }
                        add {
                            GlassTool(
                                icon = if (clock.loopA >= 0L && clock.loopB < 0L) AbIcons.SetA else AbIcons.Idle,
                                description = "A-B repeat",
                                onClick = onAbRepeat,
                                active = clock.loopA >= 0L
                            )
                        }
                        add { GlassTool(Icons.Outlined.Timer, "Sleep timer", onSleep, active = sleepActive) }
                        add {
                            GlassTool(
                                Icons.Outlined.Headphones, "Background play", onBackgroundPlay,
                                active = backgroundPlay
                            )
                        }
                        add {
                            GlassTool(
                                Icons.Outlined.AspectRatio, "Aspect ratio", onAspect,
                                active = aspect != AspectMode.FIT
                            )
                        }
                        add {
                            GlassTool(
                                icon = when (orientation) {
                                    OrientationMode.AUTO -> Icons.Outlined.ScreenRotation
                                    OrientationMode.LANDSCAPE -> Icons.Outlined.ScreenLockLandscape
                                    OrientationMode.PORTRAIT -> Icons.Outlined.ScreenLockPortrait
                                },
                                description = "Screen orientation",
                                onClick = onOrientation,
                                active = orientation != OrientationMode.AUTO
                            )
                        }
                        add { GlassTool(Icons.Outlined.CameraAlt, "Screenshot", onScreenshot) }
                        add { GlassTool(Icons.Outlined.Lock, "Lock controls", onLock) }
                        if (pipSupported) {
                            add { GlassTool(Icons.Outlined.PictureInPictureAlt, "Pop-out", onPip) }
                        }
                    }

                    CompositionLocalProvider(LocalToolSize provides bottomSize) {
                        if (compact) {
                            // Phone: two stacked rows with about the same number of buttons each
                            val firstRow = (bottomTools.size + 1) / 2
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) { bottomTools.take(firstRow).forEach { it() } }
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) { bottomTools.drop(firstRow).forEach { it() } }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ToolRow { bottomTools.take(leftCount).forEach { it() } }
                                ToolRow { bottomTools.drop(leftCount).forEach { it() } }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CurrentTimeText(clock)
                        PlayerSeekBar(
                            style = SeekBarStyle.fromId(Prefs.seekBarStyle),
                            clock = clock,
                            onScrub = onScrub,
                            onScrubEnd = onScrubEnd,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        )
                        DurationText(clock)
                    }
                }
            }
        }
    }
}
