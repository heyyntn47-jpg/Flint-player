// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.player

import com.flint.player.ui.MiddleEllipsisText
import com.flint.player.data.Prefs
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.ScreenLockLandscape
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flint.player.ui.ExpressiveIconButton
import com.flint.player.ui.MorphIconButton

enum class OrientationMode(val label: String) {
    AUTO("Auto"),
    LANDSCAPE("Landscape"),
    PORTRAIT("Portrait")
}

enum class AspectMode(val label: String) {
    FIT("Fit"),
    STRETCH("Stretch"),
    CROP("Crop")
}

enum class PlayerSheet { MENU, SPEED, SUBTITLES, AUDIO, QUEUE, DECODER, SLEEP, MIXER, CAST }

internal fun speedLabel(speed: Float): String =
    speed.toString().trimEnd('0').trimEnd('.')

@Composable
private fun TonalControl(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    size: Dp,
    iconSize: Dp,
    enabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    ExpressiveIconButton(
        icon = icon,
        description = description,
        onClick = onClick,
        // requiredSize: a button is never squeezed smaller than this
        modifier = Modifier.requiredSize(size),
        size = size,
        iconSize = iconSize,
        tint = scheme.onSecondaryContainer,
        container = scheme.secondaryContainer,
        pressedContainer = scheme.tertiaryContainer,
        pressedTint = scheme.onTertiaryContainer,
        pressedCorner = 26,
        enabled = enabled
    )
}

@Composable
fun PlayerControls(
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
    onAbRepeat: () -> Unit,
    onScreenshot: () -> Unit,
    onSettings: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Box(Modifier.fillMaxSize()) {

        // Top bar: back, two-line title, settings
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
                            listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                        )
                    )
                    .displayCutoutPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpressiveIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    description = "Back",
                    onClick = onBack,
                    tint = Color.White
                )
                Spacer(Modifier.width(8.dp))
                MiddleEllipsisText(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                MorphIconButton(
                    icon = Icons.Outlined.Settings,
                    description = "Player settings",
                    onClick = onSettings,
                    lobes = 8,
                    tint = Color.White
                )
            }
        }

        // Center controls (smaller, and a fixed size so none gets squeezed)
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn() + scaleIn(
                initialScale = 0.8f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 450f)
            ),
            exit = fadeOut() + scaleOut(targetScale = 0.9f)
        ) {
            val seekSize = if (isPlaylist) 44.dp else 50.dp
            val seekIcon = if (isPlaylist) 22.dp else 26.dp
            val playSize = if (isPlaylist) 64.dp else 72.dp
            val playIcon = if (isPlaylist) 32.dp else 36.dp
            val skipSize = 40.dp

            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(if (isPlaylist) 8.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isPlaylist) {
                    TonalControl(Icons.Rounded.SkipPrevious, "Previous", onPrevious, skipSize, 20.dp, hasPrevious)
                }
                TonalControl(Icons.Rounded.Replay10, "Back 10 seconds", { onSeekBy(-10_000L) }, seekSize, seekIcon)

                // Circle when paused, squircle when playing
                ExpressiveIconButton(
                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    description = if (isPlaying) "Pause" else "Play",
                    onClick = onPlayPause,
                    modifier = Modifier.requiredSize(playSize),
                    size = playSize,
                    iconSize = playIcon,
                    tint = scheme.onPrimaryContainer,
                    container = scheme.primaryContainer,
                    pressedContainer = scheme.primary,
                    pressedTint = scheme.onPrimary,
                    restCorner = if (isPlaying) 30 else 50,
                    pressedCorner = 22
                )

                TonalControl(Icons.Rounded.Forward10, "Forward 10 seconds", { onSeekBy(10_000L) }, seekSize, seekIcon)
                if (isPlaylist) {
                    TonalControl(Icons.Rounded.SkipNext, "Next", onNext, skipSize, 20.dp, hasNext)
                }
            }
        }

        // Bottom: seekbar, then the quick buttons in a pill at the bottom-right
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
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
                    .displayCutoutPadding()
                    .padding(start = 20.dp, end = 12.dp, top = 28.dp, bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(end = 8.dp),
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
                            .padding(horizontal = 10.dp)
                    )
                    DurationText(clock)
                }

                // One pill, one row: video tools first, then the playback modes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = scheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier.padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val loopOn = clock.loopA >= 0L
                            MorphIconButton(
                                icon = if (loopOn && clock.loopB < 0L) AbIcons.SetA else AbIcons.Idle,
                                description = "A-B repeat",
                                onClick = onAbRepeat,
                                lobes = 6,
                                size = 40.dp,
                                active = loopOn
                            )
                            MorphIconButton(
                                icon = Icons.Outlined.CameraAlt,
                                description = "Screenshot",
                                onClick = onScreenshot,
                                lobes = 8,
                                size = 40.dp
                            )
                            if (isPlaylist) {
                                MorphIconButton(
                                    icon = Icons.Outlined.Shuffle,
                                    description = "Shuffle",
                                    onClick = onShuffle,
                                    lobes = 6,
                                    size = 40.dp,
                                    active = shuffleOn
                                )
                            }
                            MorphIconButton(
                                icon = if (repeatMode == Repeat.ONE) Icons.Outlined.RepeatOne
                                else Icons.Outlined.Repeat,
                                description = "Repeat",
                                onClick = onRepeat,
                                lobes = 8,
                                size = 40.dp,
                                active = repeatMode != Repeat.OFF
                            )
                            MorphIconButton(
                                icon = Icons.Outlined.AspectRatio,
                                description = "Aspect ratio",
                                onClick = onAspect,
                                lobes = 4,
                                size = 40.dp,
                                active = aspect != AspectMode.FIT
                            )
                            MorphIconButton(
                                icon = when (orientation) {
                                    OrientationMode.AUTO -> Icons.Outlined.ScreenRotation
                                    OrientationMode.LANDSCAPE -> Icons.Outlined.ScreenLockLandscape
                                    OrientationMode.PORTRAIT -> Icons.Outlined.ScreenLockPortrait
                                },
                                description = "Screen orientation",
                                onClick = onOrientation,
                                lobes = 5,
                                size = 40.dp,
                                active = orientation != OrientationMode.AUTO
                            )
                            MorphIconButton(
                                icon = Icons.Outlined.Lock,
                                description = "Lock controls",
                                onClick = onLock,
                                lobes = 7,
                                size = 40.dp
                            )
                        }
                    }
                }
            }
        }
    }
}