// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.home

import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.flint.player.ui.barCornerBackdrop
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.flint.player.ui.WordmarkStyle
import com.flint.player.browse.BrowseTab
import com.flint.player.stream.StreamMenu
import com.flint.player.history.HistoryTab
import com.flint.player.library.LocalFileActions
import com.flint.player.library.SearchResults
import com.flint.player.library.SortField
import com.flint.player.library.VideosTab
import com.flint.player.library.ViewMode
import com.flint.player.library.rememberFileActions
import com.flint.player.library.rememberVideoLibrary
import com.flint.player.playlists.PlaylistsTab
import com.flint.player.ui.BulgeContainer
import com.flint.player.ui.ExpressiveChip
import com.flint.player.ui.ExpressiveDialog
import com.flint.player.ui.ExpressiveIconButton
import com.flint.player.ui.ExpressiveSegmented
import com.flint.player.ui.PillTextField
import com.flint.player.ui.bounceClick
import com.flint.player.ui.segmentShape
import com.flint.player.data.Prefs
import com.flint.player.ui.navBarColor
import com.flint.player.ui.glassBorder
import com.flint.player.ui.pageBackground
import com.flint.player.ui.stableBottomInsets
import com.flint.player.ui.stableTopInsets
import com.flint.player.ui.topBarColor

private enum class HomeTab(val label: String, val icon: ImageVector) {
    VIDEO("Video", Icons.Filled.VideoLibrary),
    BROWSE("Browse", Icons.Filled.Folder),
    PLAYLISTS("Playlists", Icons.AutoMirrored.Filled.PlaylistPlay),
    HISTORY("History", Icons.Filled.History)
}

/** Reports whether the user is scrolling down (true) or up (false). */
private class ScrollDirectionConnection(
    private val onDirection: (scrollingDown: Boolean) -> Unit
) : NestedScrollConnection {
    private var acc = 0f

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val dy = available.y
        if (dy != 0f) {
            if (dy * acc < 0f) acc = 0f
            acc += dy
            if (acc < -40f) onDirection(true)
            else if (acc > 40f) onDirection(false)
        }
        return Offset.Zero
    }
}

/** What the home screen shows under the top bar: a tab, or the search results over it. */
private data class HomeContent(val tab: HomeTab, val searching: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenVideo: (Uri) -> Unit,
    onPlayPlaylist: (String, Int) -> Unit,
    onOpenSettings: () -> Unit
) {
    val library = rememberVideoLibrary()
    val fileActions = rememberFileActions(onChanged = library.refresh)
    val scheme = MaterialTheme.colorScheme
    val topColor = topBarColor()

    var tab by rememberSaveable { mutableStateOf(HomeTab.VIDEO) }
    var sortField by rememberSaveable { mutableStateOf(Prefs.sortField) }
    var ascending by rememberSaveable { mutableStateOf(Prefs.sortAscending) }
    var viewMode by rememberSaveable { mutableStateOf(Prefs.viewMode) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showStreamDialog by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var navVisible by remember { mutableStateOf(true) }

    // The bottom bar squeezes and drops away while you scroll down
    val navHide = animateFloatAsState(
        targetValue = if (navVisible) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 330f),
        label = "navHide"
    )
    val scrollDirection = remember {
        ScrollDirectionConnection { scrollingDown -> navVisible = !scrollingDown || !Prefs.hideNavOnScroll }
    }
    LaunchedEffect(tab, searching) { navVisible = true }

    // The query is kept while search closes, so the results fade out as they were instead of
    // snapping to the empty "Search your videos" page. It is reset when search opens.
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val closeSearch: () -> Unit = {
        keyboard?.hide()
        focusManager.clearFocus(force = true)
        searching = false
    }
    BackHandler(enabled = searching) { closeSearch() }

    CompositionLocalProvider(LocalFileActions provides fileActions) {
        Box(Modifier.fillMaxSize()) {
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
                        title = {
                            Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.CenterStart) {
                                Crossfade(targetState = searching, animationSpec = tween(180), label = "topTitle") { isSearching ->
                                    if (isSearching) {
                                        SearchField(query = query, onQueryChange = { query = it })
                                    } else {
                                        Text(
                                            text = "Flint",
                                            style = WordmarkStyle,
                                            color = scheme.onSurface
                                        )
                                    }
                                }
                            }
                        },
                        actions = {
                            if (searching) {
                                TextButton(onClick = closeSearch) {
                                    Text("Cancel")
                                }
                            } else {
                                val canSearchAndSort = tab == HomeTab.VIDEO || tab == HomeTab.BROWSE
                                if (canSearchAndSort) {
                                    TopAction(Icons.Filled.Search, "Search") { query = ""; searching = true }
                                }
                                if (canSearchAndSort) {
                                    TopAction(Icons.AutoMirrored.Filled.Sort, "Sort") { showSortMenu = true }
                                }
                                TopAction(Icons.Filled.Settings, "Settings", onOpenSettings)
                            }
                        }
                    )
                }
            ) { padding ->
                // One wide pill that connects to the top bar
                Box(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .barCornerBackdrop()
                        .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                        .pageBackground()
                        .nestedScroll(scrollDirection)
                ) {
                    BulgeContainer(Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = HomeContent(tab, searching),
                            transitionSpec = {
                                if (initialState.tab == targetState.tab) {
                                    // Search opening or closing: the search page is solid and always
                                    // on top, so it fades over the list instead of blending with it
                                    // (two lists showing through each other looked like a distortion)
                                    fadeIn(tween(160))
                                        .togetherWith(ExitTransition.KeepUntilTransitionsFinished)
                                        .apply { targetContentZIndex = if (targetState.searching) 1f else 0f }
                                        .using(null) // never animate the container's size
                                } else {
                                    (fadeIn(tween(210, delayMillis = 90)) +
                                            scaleIn(initialScale = 0.94f, animationSpec = tween(210, delayMillis = 90)))
                                        .togetherWith(fadeOut(tween(90)))
                                        .using(null)
                                }
                            },
                            label = "tabContent"
                        ) { current ->
                            if (current.searching) {
                                Box(Modifier.fillMaxSize().pageBackground()) {
                                    SearchResults(library, query, sortField, ascending, viewMode, onOpenVideo)
                                }
                            } else {
                                when (current.tab) {
                                    HomeTab.VIDEO -> VideosTab(library, sortField, ascending, viewMode, onOpenVideo)
                                    HomeTab.BROWSE -> BrowseTab(
                                        library, sortField, ascending, viewMode, onOpenVideo,
                                        onOpenNetwork = { showStreamDialog = true }
                                    )
                                    HomeTab.PLAYLISTS -> PlaylistsTab(onPlay = onPlayPlaylist)
                                    HomeTab.HISTORY -> HistoryTab(onOpenVideo = onOpenVideo)
                                }
                            }
                        }
                    }
                }
            }

            FloatingNavBar(
                selected = tab,
                hide = navHide,
                onSelect = {
                    tab = it
                    searching = false
                    showSortMenu = false
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(stableBottomInsets())
                    .padding(bottom = 16.dp)
            )

            if (showSortMenu) {
                SortMenuOverlay(
                    onDismiss = { showSortMenu = false },
                    field = sortField,
                    ascending = ascending,
                    onField = { sortField = it },
                    onAscending = { ascending = it },
                    viewMode = viewMode,
                    onViewMode = {
                        viewMode = it
                        Prefs.set(Prefs.VIEW_MODE, it.ordinal)
                    }
                )
            }
        }
    }

    if (showStreamDialog) {
        StreamMenu(
            onDismiss = { showStreamDialog = false },
            onPlay = onOpenVideo,
            onPlayPlaylist = onPlayPlaylist
        )
    }
}

@Composable
private fun TopAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    ExpressiveIconButton(
        icon = icon,
        description = description,
        onClick = onClick,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Let the search bar finish appearing first, so the keyboard doesn't drop frames
        delay(160)
        runCatching { focusRequester.requestFocus() }
    }

    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search videos") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                ExpressiveIconButton(
                    icon = Icons.Filled.Close,
                    description = "Clear",
                    onClick = { onQueryChange("") },
                    size = 40.dp,
                    iconSize = 20.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(end = 4.dp)
            .focusRequester(focusRequester)
    )
}

@Composable
private fun SortPopup(
    onDismiss: () -> Unit,
    field: SortField,
    ascending: Boolean,
    onField: (SortField) -> Unit,
    onAscending: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    val density = LocalDensity.current
    val top = stableTopInsets().getTop(density) + with(density) { 56.dp.roundToPx() }
    val edge = with(density) { 12.dp.roundToPx() }

    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(-edge, top),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(tween(120)) + scaleIn(
                initialScale = 0.7f,
                transformOrigin = TransformOrigin(1f, 0f),
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f)
            )
        ) {
            Surface(
                modifier = Modifier.width(272.dp),
                shape = RoundedCornerShape(32.dp),
                color = scheme.surfaceContainerHigh,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = glassBorder()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        text = "Sort by",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.primary,
                        modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 8.dp)
                    )
                    val fields = SortField.entries
                    fields.forEachIndexed { index, option ->
                        if (index > 0) Spacer(Modifier.height(2.dp))
                        SortRow(
                            label = option.label,
                            selected = option == field,
                            shape = segmentShape(index, fields.size, outer = 22.dp, inner = 8.dp),
                            onClick = { onField(option); onDismiss() }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    ExpressiveSegmented(
                        options = listOf("Ascending", "Descending"),
                        selectedIndex = if (ascending) 0 else 1,
                        onSelect = { onAscending(it == 0) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SortRow(label: String, selected: Boolean, shape: Shape, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (selected) scheme.secondaryContainer else scheme.surfaceContainerHighest,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 800f),
        label = "sortRow"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(pressedScale = 0.97f, onClick = onClick)
            .clip(shape)
            .background(container)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        AnimatedVisibility(
            visible = selected,
            enter = scaleIn(animationSpec = spring(dampingRatio = 0.45f, stiffness = 500f)) + fadeIn(),
            exit = fadeOut()
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = scheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun FloatingNavBar(
    selected: HomeTab,
    hide: State<Float>,
    onSelect: (HomeTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val tabs = HomeTab.entries
    val collapsed = 56.dp
    val expanded = 116.dp
    val selectedIndex = tabs.indexOf(selected)

    val bounce = spring<androidx.compose.ui.unit.Dp>(dampingRatio = 0.6f, stiffness = 380f)
    val indicatorX by animateDpAsState(
        targetValue = collapsed * selectedIndex,
        animationSpec = bounce,
        label = "indicatorX"
    )

    Surface(
        modifier = modifier.graphicsLayer {
            // Squeezes and drops away while scrolling down, bulges back on the way up
            val h = hide.value
            translationY = h * (size.height + 96.dp.toPx())
            scaleX = 1f - 0.28f * h
            scaleY = 1f - 0.18f * h
            transformOrigin = TransformOrigin(0.5f, 1f)
        },
        shape = CircleShape,
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = glassBorder()
            ?: if (scheme.background == Color.Black) BorderStroke(1.dp, scheme.outlineVariant) else null
    ) {
        // One tonal step above the page, plain Material surface
        Box(Modifier.background(navBarColor()).padding(6.dp)) {
            Box(
                Modifier
                    .offset(x = indicatorX)
                    .width(expanded)
                    .height(52.dp)
                    .clip(CircleShape)
                    .background(scheme.primaryContainer)
            )
            Row {
                tabs.forEach { item ->
                    val isSelected = item == selected
                    val width by animateDpAsState(
                        targetValue = if (isSelected) expanded else collapsed,
                        animationSpec = bounce,
                        label = "tabWidth"
                    )
                    NavItem(
                        tab = item,
                        selected = isSelected,
                        modifier = Modifier.width(width),
                        onClick = { onSelect(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItem(tab: HomeTab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 500f),
        label = "navPress"
    )
    val tint by animateColorAsState(
        targetValue = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 700f),
        label = "navTint"
    )
    val selectScale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.35f, stiffness = 500f),
        label = "navSelect"
    )
    val stateLayer = scheme.onSurface

    Box(
        modifier = modifier
            .height(52.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .drawBehind {
                // Touch highlight pill for tabs that are not selected yet
                if (!selected && press > 0.01f) {
                    drawRoundRect(
                        color = stateLayer.copy(alpha = (0.14f * press).coerceIn(0f, 1f)),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.wrapContentWidth(unbounded = true),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = tint,
                modifier = Modifier.graphicsLayer {
                    // The icon hops up and swells while pressed
                    val s = selectScale * (1f + 0.2f * press)
                    scaleX = s
                    scaleY = s
                    translationY = -5.dp.toPx() * press
                }
            )
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn() + expandHorizontally(
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)
                ),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Text(
                    text = tab.label,
                    color = tint,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, text: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}