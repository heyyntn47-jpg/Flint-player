// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.settings

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flint.player.MainActivity
import com.flint.player.data.FlintData
import com.flint.player.data.Prefs
import com.flint.player.library.SortField
import com.flint.player.ui.barCornerBackdrop
import com.flint.player.library.ViewMode
import com.flint.player.library.folderOf
import com.flint.player.library.queryVideos
import com.flint.player.library.formatSize
import com.flint.player.player.LibVlcProvider
import com.flint.player.player.PlaybackClock
import com.flint.player.player.GestureSliderContent
import com.flint.player.player.GestureSliderStyle
import com.flint.player.player.PlayerSeekBar
import com.flint.player.player.volumeIcon
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.outlined.Tune
import com.flint.player.player.SeekBarStyle
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.flint.player.ui.BulgeContainer
import com.flint.player.ui.ColorPickerDialog
import com.flint.player.ui.ExpressiveChip
import com.flint.player.ui.ExpressiveDialog
import com.flint.player.ui.ExpressiveIconButton
import com.flint.player.ui.ExpressiveSwitch
import com.flint.player.ui.pressItem
import com.flint.player.ui.segmentShape
import com.flint.player.ui.glassStroke
import com.flint.player.ui.pageBackground
import com.flint.player.ui.stableTopInsets
import com.flint.player.ui.topBarColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ---------- Model ----------

private sealed interface Item

private class ToggleItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String?,
    val checked: Boolean,
    val enabled: Boolean,
    val onChange: (Boolean) -> Unit
) : Item

private class ChoiceItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String?,
    val options: List<String>,
    val selected: Int,
    val onSelect: (Int) -> Unit
) : Item

private class ActionItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String?,
    val destructive: Boolean = false,
    val onClick: () -> Unit
) : Item

private class ColorItem(
    val title: String,
    val subtitle: String?,
    val color: Color,
    val dimmed: Boolean,
    val onClick: () -> Unit
) : Item

private class SeekBarItem(
    val selected: Int,
    val onSelect: (Int) -> Unit
) : Item

private class PlayerUiItem(
    val selected: Int,
    val onSelect: (Int) -> Unit
) : Item

private class GestureSliderItem(
    val selected: Int,
    val onSelect: (Int) -> Unit
) : Item

private class InfoItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String?
) : Item

private class AboutCardItem(
    val version: String,
    val onLibraries: () -> Unit
) : Item

private class Group(val title: String, val items: List<Item>)

/** One entry on the settings home page. [title] matches the title of a Group. */
private const val FOLDERS_PAGE = "Folders"

private class Category(val title: String, val icon: ImageVector, val summary: String)

private val Categories = listOf(
    Category("Appearance", Icons.Outlined.Palette, "Theme, glass or material, colors, player controls, seekbar, sliders"),
    Category("Playback", Icons.Outlined.PlayCircle, "Resume, speed, decoding acceleration, controls timing, screenshots"),
    Category("Gestures", Icons.Outlined.TouchApp, "Swipe, double-tap, pinch zoom"),
    Category("Subtitles", Icons.Outlined.Subtitles, "Size, style, color"),
    Category("Background", Icons.Outlined.PictureInPictureAlt, "Background play, picture-in-picture"),
    Category("Library", Icons.Outlined.VideoLibrary, "Sorting and thumbnails"),
    Category(FOLDERS_PAGE, Icons.Outlined.FolderOpen, "Choose which folders are scanned"),
    Category("Storage and privacy", Icons.Outlined.Storage, "History and cache"),
    Category("About", Icons.Outlined.Info, "Version and reset")
)

private enum class Confirm { HISTORY, RESET }

private fun toggle(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    key: String,
    enabled: Boolean = true,
    after: () -> Unit = {}
) = ToggleItem(icon, title, subtitle, checked, enabled) { value ->
    Prefs.set(key, value)
    after()
}

private fun choice(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    options: List<String>,
    selected: Int,
    key: String,
    after: () -> Unit = {}
) = ChoiceItem(icon, title, subtitle, options, selected) { index ->
    Prefs.set(key, index)
    after()
}

// ---------- Helpers ----------

private fun applyStatusBar(context: Context) {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is MainActivity) {
            current.applyStatusBar()
            return
        }
        current = current.baseContext
    }
}

private fun thumbCacheSize(context: Context): Long =
    File(context.cacheDir, "thumbs").listFiles()?.sumOf { it.length() } ?: 0L

private fun clearThumbCache(context: Context) {
    File(context.cacheDir, "thumbs").listFiles()?.forEach { it.delete() }
}

// ---------- Screen ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val topColor = topBarColor()
    val dynamicSupported = Build.VERSION.SDK_INT >= 31

    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var showLibraries by remember { mutableStateOf(false) }
    // null = the settings home page, otherwise the title of the open category
    var pageTitle by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = pageTitle != null) { pageTitle = null }
    var cacheBytes by remember { mutableLongStateOf(-1L) }
    var cacheTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(cacheTick) {
        cacheBytes = withContext(Dispatchers.IO) { thumbCacheSize(context) }
    }

    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "1.0"
    }

    val speedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    val tapOptions = listOf(5, 10, 15, 30)
    val dynamicOn = dynamicSupported && Prefs.dynamicColor

    val appearance = buildList<Item> {
        add(
            choice(
                Icons.Outlined.Palette, "Theme", null,
                listOf("System", "Light", "Dark"), Prefs.themeMode, Prefs.THEME_MODE
            )
        )
        add(
            choice(
                Icons.Outlined.BlurOn, "Interface style",
                "Material is solid. Glass is see-through with soft borders, like the glass player controls.",
                listOf("Material", "Glass"), Prefs.uiStyle, Prefs.UI_STYLE
            )
        )
        // The wallpaper option only exists on Android 12 and newer
        if (dynamicSupported) {
            add(
                toggle(
                    Icons.Outlined.ColorLens, "Dynamic colors", "Use your wallpaper's colors",
                    Prefs.dynamicColor, Prefs.DYNAMIC_COLOR
                )
            )
        }
        // The color picker is there on every Android version
        add(
            ColorItem(
                title = "Custom color",
                subtitle = if (dynamicOn) "Turn off dynamic colors to use your own color"
                else "Pick any color, the whole app follows it",
                color = Color(Prefs.customColor),
                dimmed = dynamicOn,
                onClick = { showPicker = true }
            )
        )
        add(
            toggle(
                Icons.Outlined.DarkMode, "Pure black", "Black backgrounds in the dark theme",
                Prefs.pureBlack, Prefs.PURE_BLACK
            )
        )
        add(
            toggle(
                Icons.Outlined.Fullscreen, "Hide status bar",
                "Swipe down from the top edge to peek at it",
                Prefs.hideStatusBar, Prefs.HIDE_STATUS_BAR
            ) { applyStatusBar(context) }
        )
        add(
            toggle(
                Icons.Outlined.Vibration, "Haptic feedback", "Small vibrations when you touch things",
                Prefs.hapticsEnabled, Prefs.HAPTICS
            )
        )
        add(
            toggle(
                Icons.Outlined.TouchApp, "Stretchy scrolling", "Lists stretch and bounce at the ends",
                Prefs.bulgeEffect, Prefs.BULGE
            )
        )
        add(
            toggle(
                Icons.Outlined.VerticalAlignBottom, "Hide bottom bar while scrolling", null,
                Prefs.hideNavOnScroll, Prefs.HIDE_NAV
            )
        )
        add(PlayerUiItem(Prefs.playerUi) { Prefs.set(Prefs.PLAYER_UI, it) })
        add(SeekBarItem(Prefs.seekBarStyle) { Prefs.set(Prefs.SEEKBAR_STYLE, it) })
        add(GestureSliderItem(Prefs.gestureSliderStyle) { Prefs.set(Prefs.GESTURE_SLIDER_STYLE, it) })
    }

    val groups = listOf(
        Group("Appearance", appearance),
        Group(
            "Playback", listOf(
                toggle(
                    Icons.Outlined.History, "Resume where you left off",
                    "Continue videos from the spot you stopped",
                    Prefs.resumePlayback, Prefs.RESUME
                ),
                choice(
                    Icons.Outlined.Repeat, "Repeat by default", null,
                    listOf("Off", "Repeat one", "Repeat all"), Prefs.defaultRepeat, Prefs.DEFAULT_REPEAT
                ),
                ChoiceItem(
                    Icons.Outlined.Speed, "Default speed", null,
                    speedOptions.map { "${it.toString().trimEnd('0').trimEnd('.')}x" },
                    speedOptions.indexOfFirst { kotlin.math.abs(it - Prefs.defaultSpeed) < 0.01f }
                ) { index -> Prefs.set(Prefs.DEFAULT_SPEED, speedOptions[index]) },
                choice(
                    Icons.Outlined.AspectRatio, "Default aspect ratio", null,
                    listOf("Fit", "Stretch", "Crop"), Prefs.defaultAspect, Prefs.DEFAULT_ASPECT
                ),
                toggle(
                    Icons.Outlined.ScreenRotation, "Match screen to video",
                    "Landscape for wide videos, portrait for tall ones",
                    Prefs.orientationByVideo, Prefs.ORIENTATION_BY_VIDEO
                ),
                toggle(
                    Icons.Outlined.Brightness6, "Keep screen on", "While a video is open",
                    Prefs.keepScreenOn, Prefs.KEEP_SCREEN_ON
                ),
                choice(
                    Icons.Outlined.Timer, "Controls auto-hide",
                    "How long the player controls stay on screen before fading out. \"Never\" keeps them until you tap the video.",
                    Prefs.controlsHideLabels, Prefs.controlsHideIndex, Prefs.CONTROLS_HIDE
                ),
                choice(
                    Icons.Outlined.CameraAlt, "Screenshot format",
                    "Screenshots are saved to Pictures/Flint",
                    listOf("JPEG", "PNG"), Prefs.screenshotFormat, Prefs.SCREENSHOT_FORMAT
                ),
                choice(
                    Icons.Outlined.Memory, "Decoding acceleration",
                    "Automatic uses hardware and switches to software if a video won't play. " +
                        "Disable always uses software. Full always uses hardware.",
                    listOf("Automatic", "Disable", "Full"),
                    Prefs.decoderMode, Prefs.DECODER
                ) { LibVlcProvider.reset(context) },
                toggle(
                    Icons.Outlined.Headphones, "Pause when headphones unplug", null,
                    Prefs.pauseOnUnplug, Prefs.PAUSE_ON_UNPLUG
                ) { LibVlcProvider.reset(context) }
            )
        ),
        Group(
            "Gestures", listOf(
                toggle(
                    Icons.Outlined.FastForward, "Swipe to seek", "Drag left or right on the video",
                    Prefs.gestureSeek, Prefs.GESTURE_SEEK
                ),
                toggle(
                    Icons.Outlined.Brightness6, "Brightness swipe", "Drag up or down on the left side",
                    Prefs.gestureBrightness, Prefs.GESTURE_BRIGHTNESS
                ),
                toggle(
                    Icons.AutoMirrored.Outlined.VolumeUp, "Volume swipe", "Drag up or down on the right side",
                    Prefs.gestureVolume, Prefs.GESTURE_VOLUME
                ),
                toggle(
                    Icons.Outlined.TouchApp, "Double-tap to skip",
                    "Left and right edges skip, the middle plays or pauses",
                    Prefs.gestureDoubleTap, Prefs.GESTURE_DOUBLE_TAP
                ),
                toggle(
                    Icons.Outlined.ZoomIn, "Pinch to zoom",
                    "Pinch with two fingers to zoom the video, drag with two fingers to move it",
                    Prefs.pinchZoom, Prefs.PINCH_ZOOM
                ),
                ChoiceItem(
                    Icons.Outlined.Forward10, "Double-tap skip length", null,
                    tapOptions.map { "$it s" },
                    tapOptions.indexOf(Prefs.doubleTapSeconds)
                ) { index -> Prefs.set(Prefs.DOUBLE_TAP_SECONDS, tapOptions[index]) }
            )
        ),
        Group(
            "Subtitles", listOf(
                choice(
                    Icons.Outlined.FormatSize, "Size", null,
                    listOf("Small", "Medium", "Large", "Huge"), Prefs.subtitleSize, Prefs.SUBTITLE_SIZE
                ),
                choice(
                    Icons.Outlined.Subtitles, "Style", null,
                    listOf("Outline", "Shadow", "Box"), Prefs.subtitleStyle, Prefs.SUBTITLE_STYLE
                ),
                choice(
                    Icons.Outlined.FormatColorText, "Color", null,
                    listOf("White", "Yellow"), Prefs.subtitleColor, Prefs.SUBTITLE_COLOR
                )
            )
        ),
        Group(
            "Background", listOf(
                toggle(
                    Icons.Outlined.MusicNote, "Background play",
                    "Keep playing when you leave the app, with notification controls",
                    Prefs.backgroundPlay, Prefs.BACKGROUND_PLAY
                ),
                toggle(
                    Icons.Outlined.PictureInPictureAlt, "Picture-in-picture",
                    "Shrink the video into a window when you press Home",
                    Prefs.autoPip, Prefs.AUTO_PIP
                )
            )
        ),
        Group(
            "Library", listOf(
                choice(
                    Icons.Outlined.GridView, "Default view",
                    "Layout of the video lists",
                    ViewMode.entries.map { it.label }, Prefs.viewMode.ordinal, Prefs.VIEW_MODE
                ),
                choice(
                    Icons.AutoMirrored.Filled.Sort, "Default sort",
                    "Used when the app starts",
                    SortField.entries.map { it.label }, Prefs.sortField.ordinal, Prefs.SORT_FIELD
                ),
                ChoiceItem(
                    Icons.AutoMirrored.Filled.Sort, "Default order", null,
                    listOf("Ascending", "Descending"),
                    if (Prefs.sortAscending) 0 else 1
                ) { index -> Prefs.set(Prefs.SORT_ASCENDING, index == 0) },
                toggle(
                    Icons.Outlined.Image, "Prepare thumbnails in the background",
                    "Makes scrolling smoother, uses a little battery after launch",
                    Prefs.prewarmThumbnails, Prefs.PREWARM
                )
            )
        ),
        Group(
            "Storage and privacy", listOf(
                toggle(
                    Icons.Outlined.History, "Save watch history",
                    "Also remembers where you stopped",
                    Prefs.saveHistory, Prefs.SAVE_HISTORY
                ),
                ActionItem(
                    Icons.Outlined.DeleteSweep, "Thumbnail cache",
                    if (cacheBytes >= 0) "${formatSize(cacheBytes)} • tap to clear" else "Calculating…"
                ) {
                    clearThumbCache(context)
                    cacheTick++
                    Toast.makeText(context, "Thumbnail cache cleared", Toast.LENGTH_SHORT).show()
                },
                ActionItem(
                    Icons.Outlined.Delete, "Clear watch history",
                    if (FlintData.history.size == 1) "1 video" else "${FlintData.history.size} videos",
                    destructive = true
                ) { confirm = Confirm.HISTORY }
            )
        ),
        Group(
            "About", listOf(
                AboutCardItem(version) { showLibraries = true },
                ActionItem(
                    Icons.Outlined.RestartAlt, "Reset all settings",
                    "Put every option back to its default",
                    destructive = true
                ) { confirm = Confirm.RESET }
            )
        )
    )

    Scaffold(
        // Glass: each screen paints its own opaque backdrop, so two screens never show through
        // each other while navigating, and the translucent bar sits on that backdrop
        modifier = if (Prefs.glassUi) Modifier.pageBackground() else Modifier,
        containerColor = if (Prefs.glassUi) Color.Transparent else topColor,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topColor,
                    scrolledContainerColor = topColor
                ),
                windowInsets = stableTopInsets(),
                navigationIcon = {
                    ExpressiveIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        description = "Back",
                        onClick = { if (pageTitle != null) pageTitle = null else onBack() },
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                },
                title = {
                    AnimatedContent(
                        targetState = pageTitle ?: "Settings",
                        transitionSpec = {
                            (fadeIn(tween(200)) + slideInVertically(tween(220)) { it / 3 }) togetherWith
                                (fadeOut(tween(100)) + slideOutVertically(tween(160)) { -it / 3 })
                        },
                        label = "settingsTitle"
                    ) { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            )
        }
    ) { padding ->
        // The same wide pill that connects to the top bar as on the home screen
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .barCornerBackdrop()
                .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                .pageBackground()
        ) {
            BulgeContainer(Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = pageTitle,
                    transitionSpec = {
                        if (targetState != null) {
                            (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) { it / 4 } +
                                fadeIn(tween(200))) togetherWith
                                (slideOutHorizontally(tween(200)) { -it / 6 } + fadeOut(tween(120)))
                        } else {
                            (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) { -it / 6 } +
                                fadeIn(tween(200))) togetherWith
                                (slideOutHorizontally(tween(200)) { it / 4 } + fadeOut(tween(120)))
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "settingsPage"
                ) { page ->
                    val open = groups.firstOrNull { it.title == page }
                    if (page == FOLDERS_PAGE) FolderManagerPage() else LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 56.dp)
                    ) {
                        if (open == null) {
                            // Home: one row per category
                            val firstBlock = Categories.take(7)
                            val secondBlock = Categories.drop(7)
                            itemsIndexed(firstBlock, key = { _, c -> "cat-${c.title}" }) { index, c ->
                                StaggerIn(index) {
                                    CategoryRow(c, segmentShape(index, firstBlock.size)) { pageTitle = c.title }
                                }
                            }
                            item(key = "cat-gap") { Spacer(Modifier.height(16.dp)) }
                            itemsIndexed(secondBlock, key = { _, c -> "cat-${c.title}" }) { index, c ->
                                StaggerIn(firstBlock.size + index) {
                                    CategoryRow(c, segmentShape(index, secondBlock.size)) { pageTitle = c.title }
                                }
                            }
                        } else {
                            itemsIndexed(
                                open.items,
                                key = { index, _ -> "${open.title}-$index" }
                            ) { index, item ->
                                // The About card is its own pill, so the row under it stands alone
                                val shape = if (open.items.any { it is AboutCardItem }) segmentShape(0, 1)
                                else segmentShape(index, open.items.size)
                                SettingsRow(item, shape)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLibraries) LibrariesDialog(onDismiss = { showLibraries = false })

    if (showPicker) {
        ColorPickerDialog(
            initial = Color(Prefs.customColor),
            onApply = { picked ->
                Prefs.set(Prefs.CUSTOM_COLOR, picked.toArgb())
                // A custom color only shows when the wallpaper colors are off
                if (dynamicSupported && Prefs.dynamicColor) {
                    Prefs.set(Prefs.DYNAMIC_COLOR, false)
                    Toast.makeText(
                        context, "Dynamic colors turned off to use your color", Toast.LENGTH_SHORT
                    ).show()
                }
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }

    when (confirm) {
        Confirm.HISTORY -> ExpressiveDialog(
            title = "Clear watch history?",
            onDismiss = { confirm = null },
            confirmText = "Clear",
            destructive = true,
            onConfirm = {
                FlintData.clearHistory()
                confirm = null
                Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
            }
        ) {
            Text(
                text = "This also forgets where you stopped in each video.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
        }

        Confirm.RESET -> ExpressiveDialog(
            title = "Reset all settings?",
            onDismiss = { confirm = null },
            confirmText = "Reset",
            destructive = true,
            onConfirm = {
                Prefs.resetAll()
                applyStatusBar(context)
                LibVlcProvider.reset(context)
                confirm = null
                Toast.makeText(context, "Settings reset", Toast.LENGTH_SHORT).show()
            }
        ) {
            Text(
                text = "Every option on this screen goes back to its default. Your videos, history and playlists are not touched.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
        }

        null -> Unit
    }
}

// ---------- Rows ----------

@Composable
private fun SettingsRow(item: Item, shape: Shape) {
    when (item) {
        is ToggleItem -> ToggleRow(item, shape)
        is ChoiceItem -> ChoiceRow(item, shape)
        is ActionItem -> ActionRow(item, shape)
        is ColorItem -> ColorRow(item, shape)
        is PlayerUiItem -> PlayerUiRow(item, shape)
        is SeekBarItem -> SeekBarRow(item, shape)
        is GestureSliderItem -> GestureSliderRow(item, shape)
        is InfoItem -> InfoRow(item, shape)
        is AboutCardItem -> Box(Modifier.padding(bottom = 16.dp)) {
            AboutCard(item.version, item.onLibraries)
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector, destructive: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (destructive) scheme.errorContainer else scheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (destructive) scheme.onErrorContainer else scheme.onSecondaryContainer,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun TitleBlock(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    destructive: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (destructive) scheme.error else scheme.onSurface
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ToggleRow(item: ToggleItem, shape: Shape) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 1.dp)
            .graphicsLayer { alpha = if (item.enabled) 1f else 0.5f }
            .pressItem(
                shape = shape,
                container = scheme.surfaceContainer,
                onClick = { if (item.enabled) item.onChange(!item.checked) }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(item.icon)
        Spacer(Modifier.width(14.dp))
        TitleBlock(item.title, item.subtitle, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        ExpressiveSwitch(checked = item.checked)
    }
}

@Composable
private fun ChoiceRow(item: ChoiceItem, shape: Shape) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 1.dp)
            .clip(shape)
            .background(scheme.surfaceContainer)
            .glassStroke(shape)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(item.icon)
            Spacer(Modifier.width(14.dp))
            TitleBlock(item.title, item.subtitle, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item.options.forEachIndexed { index, label ->
                ExpressiveChip(
                    label = label,
                    selected = index == item.selected,
                    onClick = { item.onSelect(index) }
                )
            }
        }
    }
}

/** Lets you pick a seekbar design. Every option shows a live, moving preview of itself. */
@Composable
private fun SeekBarRow(item: SeekBarItem, shape: Shape) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 1.dp)
            .clip(shape)
            .background(scheme.surfaceContainer)
            .glassStroke(shape)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Outlined.LinearScale)
            Spacer(Modifier.width(14.dp))
            TitleBlock("Seekbar style", "Tap one to use it in the player", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        SeekBarStyle.entries.forEach { style ->
            SeekBarPreviewCard(
                style = style,
                selected = style.id == item.selected,
                onClick = { item.onSelect(style.id) }
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SeekBarPreviewCard(style: SeekBarStyle, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme

    // A fake playback that ticks like the real player (every 0.25 s) and loops.
    // It starts part-way in, so there is something to see the moment the page opens.
    val clock = remember {
        PlaybackClock().apply {
            duration = 100_000L
            position = 35_000L
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(250)
            val next = clock.position + 1_000L
            clock.position = if (next > clock.duration) 35_000L else next
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) scheme.primary else scheme.outlineVariant,
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = style.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) scheme.onPrimaryContainer else scheme.onSurface
                    )
                    Text(
                        text = style.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant
                    )
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = "Selected",
                        tint = scheme.primary
                    )
                }
            }
            PlayerSeekBar(
                style = style,
                clock = clock,
                onScrub = {},
                onScrubEnd = {},
                inactiveColor = scheme.onSurface.copy(alpha = 0.2f),
                modifier = Modifier.fillMaxWidth()
            )
        }
        // Sits on top, so touching a preview selects it instead of scrubbing it
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) { detectTapGestures { onClick() } }
        )
    }
}

/** Lets you pick the player controls design. Each option shows a small moving preview. */
@Composable
private fun PlayerUiRow(item: PlayerUiItem, shape: Shape) {
    val scheme = MaterialTheme.colorScheme

    // One shared fake playback position that loops, so both previews move together
    val progress = rememberInfiniteTransition(label = "playerUiPreview").animateFloat(
        initialValue = 0.15f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
        label = "playerUiPreviewValue"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 1.dp)
            .clip(shape)
            .background(scheme.surfaceContainer)
            .glassStroke(shape)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Outlined.SmartDisplay)
            Spacer(Modifier.width(14.dp))
            TitleBlock(
                "Player controls",
                "Tap one to use it in the player. The seekbar and sliders below work with both.",
                Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayerUiOption.entries.forEach { option ->
                val selected = option.id == item.selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) scheme.primary else scheme.outlineVariant,
                            shape = RoundedCornerShape(20.dp)
                        )
                ) {
                    Column(Modifier.padding(8.dp)) {
                        PlayerUiPreview(
                            option = option,
                            progress = progress,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = scheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = option.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant
                        )
                    }
                    // Sits on top, so touching a preview selects it
                    Box(
                        Modifier
                            .matchParentSize()
                            .pointerInput(Unit) { detectTapGestures { item.onSelect(option.id) } }
                    )
                }
            }
        }
    }
}

/** Lets you pick the volume / brightness slider design, each shown moving like the real one. */
@Composable
private fun GestureSliderRow(item: GestureSliderItem, shape: Shape) {
    val scheme = MaterialTheme.colorScheme

    // One shared fake volume that rises and falls, so all four previews move together
    val progress = rememberInfiniteTransition(label = "sliderPreview").animateFloat(
        initialValue = 0.1f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Reverse),
        label = "sliderPreviewValue"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 1.dp)
            .clip(shape)
            .background(scheme.surfaceContainer)
            .glassStroke(shape)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Outlined.Tune)
            Spacer(Modifier.width(14.dp))
            TitleBlock(
                "Volume and brightness sliders",
                "Tap one to use it in the player",
                Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GestureSliderStyle.entries.forEach { style ->
                GestureSliderPreviewCard(
                    style = style,
                    selected = style.id == item.selected,
                    progress = progress,
                    onClick = { item.onSelect(style.id) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun GestureSliderPreviewCard(
    style: GestureSliderStyle,
    selected: Boolean,
    progress: State<Float>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) scheme.primary else scheme.outlineVariant,
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Dark backdrop, like the video the slider sits on in the player
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF16161A))
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                GestureSliderContent(
                    style = style,
                    fraction = { progress.value },
                    iconFor = ::volumeIcon
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = style.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) scheme.onPrimaryContainer else scheme.onSurface
            )
        }
        // Sits on top, so touching a preview selects it
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) { detectTapGestures { onClick() } }
        )
    }
}

@Composable
private fun ActionRow(item: ActionItem, shape: Shape) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 1.dp)
            .pressItem(shape = shape, container = scheme.surfaceContainer, onClick = item.onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(item.icon, item.destructive)
        Spacer(Modifier.width(14.dp))
        TitleBlock(item.title, item.subtitle, Modifier.weight(1f), item.destructive)
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = scheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ColorRow(item: ColorItem, shape: Shape) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 1.dp)
            .pressItem(shape = shape, container = scheme.surfaceContainer, onClick = item.onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(item.color.copy(alpha = if (item.dimmed) 0.45f else 1f))
                .border(2.dp, scheme.outlineVariant, CircleShape)
        )
        Spacer(Modifier.width(14.dp))
        TitleBlock(item.title, item.subtitle, Modifier.weight(1f))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = scheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoRow(item: InfoItem, shape: Shape) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 1.dp)
            .clip(shape)
            .background(scheme.surfaceContainer)
            .glassStroke(shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(item.icon)
        Spacer(Modifier.width(14.dp))
        TitleBlock(item.title, item.subtitle, Modifier.weight(1f))
    }
}

// ---------- Home page rows ----------

@Composable
private fun CategoryRow(category: Category, shape: Shape, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 1.dp)
            .pressItem(shape = shape, container = scheme.surfaceContainer, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(category.icon)
        Spacer(Modifier.width(14.dp))
        TitleBlock(category.title, category.summary, Modifier.weight(1f))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = scheme.onSurfaceVariant
        )
    }
}

/** Rows rise and fade in one after another when the page opens. */
@Composable
private fun StaggerIn(index: Int, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index * 40L).coerceAtMost(320L))
        progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
    }
    Box(
        Modifier.graphicsLayer {
            alpha = progress.value.coerceIn(0f, 1f)
            translationY = (1f - progress.value) * 24.dp.toPx()
        }
    ) { content() }
}


// ---------- Folder manager ----------

private class FolderEntry(val path: String, val count: Int)

private fun prettyPath(path: String): String =
    path.replace("/storage/emulated/0", "Internal storage")

/**
 * Choose which folders show up in the library.
 * "Only chosen": nothing outside the checked folders is scanned.
 * "Skip chosen": everything is scanned except the checked folders.
 */
@Composable
private fun FolderManagerPage() {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    // null while looking, then every folder on the device that holds videos
    var detected by remember { mutableStateOf<List<FolderEntry>?>(null) }
    LaunchedEffect(Unit) {
        detected = withContext(Dispatchers.IO) {
            runCatching { queryVideos(context) }.getOrDefault(emptyList())
                .groupBy { folderOf(it.path) }
                .filterKeys { it.isNotEmpty() }
                .map { (dir, videos) -> FolderEntry(dir, videos.size) }
        }
    }

    val mode = Prefs.folderMode
    val chosen: Set<String> = when (mode) {
        1 -> Prefs.includedFolders
        2 -> Prefs.excludedFolders
        else -> emptySet()
    }

    // Folders that were chosen earlier but no longer hold videos stay in the list, so they can be unchecked
    val entries = remember(detected, chosen) {
        val found = detected.orEmpty()
        val known = found.map { it.path }.toSet()
        (found + chosen.filter { it !in known }.map { FolderEntry(it, 0) })
            .sortedWith(compareBy({ it.path.substringAfterLast('/').lowercase() }, { it.path }))
    }

    fun save(folders: Set<String>) = Prefs.setFolders(onlyScanList = mode == 1, folders = folders)

    val modeItem = ChoiceItem(
        Icons.Outlined.FolderOpen,
        "Folders to scan",
        when (mode) {
            1 -> "Only the folders you check are scanned"
            2 -> "Everything is scanned except the folders you check"
            else -> "Every folder with videos is scanned"
        },
        listOf("All folders", "Only chosen", "Skip chosen"),
        mode
    ) { index ->
        // Switching to "only chosen" starts with every folder checked, so nothing vanishes until you uncheck it
        if (index == 1 && Prefs.includedFolders.isEmpty() && detected != null) {
            Prefs.setFolders(true, detected.orEmpty().map { it.path }.toSet())
        }
        Prefs.set(Prefs.FOLDER_MODE, index)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 56.dp)
    ) {
        item(key = "folder-mode") { ChoiceRow(modeItem, segmentShape(0, 1)) }

        if (mode == 0) {
            item(key = "folder-all") {
                Spacer(Modifier.height(16.dp))
                InfoRow(
                    InfoItem(
                        Icons.Outlined.Info, "Everything is scanned",
                        "Pick \"Only chosen\" or \"Skip chosen\" to limit which folders show up"
                    ),
                    segmentShape(0, 1)
                )
            }
        } else {
            item(key = "folder-label") {
                Text(
                    text = if (mode == 1) "Folders to scan" else "Folders to skip",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.primary,
                    modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 8.dp)
                )
            }
            item(key = "folder-quick") {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExpressiveChip("Select all", false, { save(entries.map { it.path }.toSet()) })
                    ExpressiveChip("Clear", false, { save(emptySet()) })
                }
            }
            if (mode == 1 && chosen.isEmpty()) {
                item(key = "folder-empty-warning") {
                    InfoRow(
                        InfoItem(
                            Icons.Outlined.Info, "No folders checked",
                            "Your library stays empty until you check at least one folder"
                        ),
                        segmentShape(0, 1)
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
            when {
                detected == null -> item(key = "folder-loading") {
                    InfoRow(InfoItem(Icons.Outlined.Info, "Looking for folders…", null), segmentShape(0, 1))
                }
                entries.isEmpty() -> item(key = "folder-none") {
                    InfoRow(
                        InfoItem(
                            Icons.Outlined.Info, "No folders with videos found",
                            "Allow video access on the home screen, then come back"
                        ),
                        segmentShape(0, 1)
                    )
                }
                else -> itemsIndexed(entries, key = { _, e -> "folder-${e.path}" }) { index, entry ->
                    val checked = entry.path in chosen
                    FolderRow(
                        entry = entry,
                        checked = checked,
                        shape = segmentShape(index, entries.size)
                    ) { save(if (checked) chosen - entry.path else chosen + entry.path) }
                }
            }
        }
    }
}

@Composable
private fun FolderRow(entry: FolderEntry, checked: Boolean, shape: Shape, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val count = if (entry.count == 1) "1 video" else "${entry.count} videos"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 1.dp)
            .pressItem(shape = shape, container = scheme.surfaceContainer, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(Icons.Outlined.Folder)
        Spacer(Modifier.width(14.dp))
        TitleBlock(
            title = entry.path.substringAfterLast('/').ifEmpty { entry.path },
            subtitle = "$count • ${prettyPath(entry.path)}",
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Checkbox(checked = checked, onCheckedChange = null)
    }
}
