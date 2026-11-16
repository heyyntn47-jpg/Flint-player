// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.stream

import com.flint.player.ui.glassBorder
import com.flint.player.ui.stableBottomAndSideInsets
import com.flint.player.ui.stableTopInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flint.player.data.FlintData
import com.flint.player.network.NetworkTab
import com.flint.player.ui.EmptyPlaceholder
import com.flint.player.ui.ExpressiveChip
import com.flint.player.ui.ExpressiveIconButton
import com.flint.player.ui.ExpressiveSegmented
import com.flint.player.ui.ExpressiveSwitch
import com.flint.player.ui.LocalOverlayHost
import com.flint.player.ui.PillTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Links the player can open. Anything else is rejected before it reaches libVLC. */
private val VALID_SCHEMES = listOf(
    "http://", "https://", "rtsp://", "rtmp://", "mms://", "mmsh://", "mmst://",
    "udp://", "rtp://", "smb://", "ftp://", "sftp://", "nfs://"
)

/** Shortcuts under the link field; tapping one starts the link with that protocol. */
private val QUICK_SCHEMES = listOf(
    "http://", "https://", "rtsp://", "rtmp://", "udp://@", "rtp://@", "mms://",
    "smb://", "ftp://", "sftp://", "nfs://"
)

/**
 * The one place for everything network and streaming: open a link (with VLC's stream options),
 * import a web playlist, reopen recent and favorite streams, and browse the network.
 *
 * Drawn through the app's overlay host like the dialogs, so the status bar stays hidden.
 */
@Composable
fun StreamMenu(
    onDismiss: () -> Unit,
    onPlay: (Uri) -> Unit,
    onPlayPlaylist: (String, Int) -> Unit
) {
    val host = LocalOverlayHost.current
    val token = remember { Any() }
    SideEffect { host.put(token) { StreamScreen(onDismiss, onPlay, onPlayPlaylist) } }
    DisposableEffect(token) { onDispose { host.remove(token) } }
}

@Composable
private fun StreamScreen(
    onDismiss: () -> Unit,
    onPlay: (Uri) -> Unit,
    onPlayPlaylist: (String, Int) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var tab by remember { mutableStateOf(0) }
    var browsing by remember { mutableStateOf(false) }

    // While a network folder is open the Network tab handles back (one folder up) itself
    BackHandler(enabled = !(tab == 2 && browsing)) { onDismiss() }

    fun playUrl(url: String, options: StreamOptions) {
        StreamStore.record(url, options)
        onPlay(Uri.parse(url))
        onDismiss()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .windowInsetsPadding(stableBottomAndSideInsets())
            .windowInsetsPadding(stableTopInsets())
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Network",
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            ExpressiveIconButton(icon = Icons.Filled.Close, description = "Close", onClick = onDismiss)
        }
        ExpressiveSegmented(
            options = listOf("Link", "Saved", "Servers"),
            selectedIndex = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        when (tab) {
            0 -> LinkTab(
                onPlayUrl = { url, options -> playUrl(url, options) },
                onPlaylistReady = { id -> onPlayPlaylist(id, 0); onDismiss() }
            )
            1 -> SavedTab(onPlay = { playUrl(it.url, it.options) })
            else -> NetworkTab(
                onOpenVideo = { onPlay(it); onDismiss() },
                onBrowsingChange = { browsing = it }
            )
        }
    }
}

@Composable
private fun LinkTab(
    onPlayUrl: (String, StreamOptions) -> Unit,
    onPlaylistReady: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    var caching by remember { mutableStateOf("") }
    var userAgent by remember { mutableStateOf("") }
    var referrer by remember { mutableStateOf("") }
    var subtitleUrl by remember { mutableStateOf("") }
    var rtspTcp by remember { mutableStateOf(false) }
    var reconnect by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val trimmed = url.trim()
    val valid = VALID_SCHEMES.any { trimmed.startsWith(it, ignoreCase = true) && trimmed.length > it.length }
    val isWeb = trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)

    fun options() = StreamOptions(
        cachingMs = caching.trim().toIntOrNull()?.coerceIn(0, 60_000) ?: 0,
        userAgent = userAgent.trim(),
        referrer = referrer.trim(),
        subtitleUrl = subtitleUrl.trim(),
        rtspTcp = rtspTcp,
        reconnect = reconnect
    )

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun openAsPlaylist() {
        val link = trimmed
        val opts = options()
        busy = true
        scope.launch {
            val parsed = withContext(Dispatchers.IO) {
                runCatching { PlaylistImport.load(link, opts.userAgent) }.getOrNull()
            }
            if (parsed == null) {
                toast("Couldn't download that playlist")
                busy = false
                return@launch
            }
            if (parsed.adaptive || parsed.entries.isEmpty()) {
                toast("No list of videos in there, playing it as one stream")
                busy = false
                onPlayUrl(link, opts)
                return@launch
            }
            val created = withContext(Dispatchers.Default) {
                val playlist = FlintData.createPlaylist(StreamStore.titleFor(link))
                parsed.entries.forEach { FlintData.addToPlaylist(playlist.id, it) }
                playlist
            }
            busy = false
            toast("Saved as playlist \"${created.name}\"")
            onPlaylistReady(created.id)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
    ) {
        PillTextField(
            value = url,
            onValueChange = { url = it },
            placeholder = "https://example.com/video.m3u8",
            autoFocus = false,
            onDone = { if (valid && !busy) onPlayUrl(trimmed, options()) }
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExpressiveChip(
                label = "Paste",
                selected = false,
                onClick = {
                    scope.launch {
                        val clip = clipboard.getClipEntry()?.clipData
                        val pasted = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).text else null
                        if (pasted != null) url = pasted.toString().trim()
                    }
                }
            )
            QUICK_SCHEMES.forEach { prefix ->
                ExpressiveChip(
                    label = prefix.removeSuffix("://@").removeSuffix("://"),
                    selected = trimmed.startsWith(prefix, ignoreCase = true),
                    onClick = { url = prefix }
                )
            }
        }
        Text(
            text = "Web links, HLS, DASH, RTSP, RTMP, MMS, UDP / RTP multicast, SMB, FTP, SFTP and NFS",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp, start = 4.dp)
        )

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onPlayUrl(trimmed, options()) },
            enabled = valid && !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Play") }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { openAsPlaylist() },
                enabled = valid && isWeb && !busy,
                modifier = Modifier.weight(1f)
            ) { Text(if (busy) "Loading…" else "Open as playlist") }
            OutlinedButton(
                onClick = {
                    StreamStore.saveFavorite(trimmed, options())
                    toast("Saved to favorites")
                },
                enabled = valid && !busy,
                modifier = Modifier.weight(1f)
            ) { Text("Save") }
        }

        Spacer(Modifier.height(12.dp))
        ExpressiveChip(
            label = if (advanced) "Hide stream options" else "Stream options",
            selected = advanced,
            onClick = { advanced = !advanced }
        )
        if (advanced) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OptionField("Network cache in ms (empty = default)", caching, KeyboardType.Number) { caching = it }
                OptionField("User agent", userAgent, KeyboardType.Uri) { userAgent = it }
                OptionField("Referrer", referrer, KeyboardType.Uri) { referrer = it }
                OptionField("Subtitle file link", subtitleUrl, KeyboardType.Uri) { subtitleUrl = it }
                ToggleRow("RTSP over TCP", "Fixes cameras that stutter or fail over UDP", rtspTcp) { rtspTcp = !rtspTcp }
                ToggleRow("Reconnect on drop", "Tries again when the web server cuts the connection", reconnect) { reconnect = !reconnect }
                Text(
                    text = "These apply to this link only and are remembered with it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun OptionField(
    label: String,
    value: String,
    keyboard: KeyboardType,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ToggleRow(label: String, hint: String, checked: Boolean, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = glassBorder()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ExpressiveSwitch(checked = checked)
        }
    }
}

@Composable
private fun SavedTab(onPlay: (SavedStream) -> Unit) {
    val all = StreamStore.streams
    if (all.isEmpty()) {
        EmptyPlaceholder(
            icon = Icons.Filled.Link,
            title = "No saved streams",
            subtitle = "Links you play or save from the Link tab show up here, with the options you chose."
        )
        return
    }
    val favorites = all.filter { it.favorite }
    val recents = all.filterNot { it.favorite }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (favorites.isNotEmpty()) {
            item(key = "fav-header") { ListHeader("Favorites") }
            items(favorites, key = { "f-" + it.url }) { StreamRow(it, onPlay) }
        }
        if (recents.isNotEmpty()) {
            item(key = "recent-header") {
                ListHeader("Recent") {
                    TextButton(onClick = { StreamStore.clearRecents() }) { Text("Clear") }
                }
            }
            items(recents, key = { "r-" + it.url }) { StreamRow(it, onPlay) }
        }
    }
}

@Composable
private fun ListHeader(text: String, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

@Composable
private fun StreamRow(stream: SavedStream, onPlay: (SavedStream) -> Unit) {
    Surface(
        onClick = { onPlay(stream) },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = glassBorder()
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(
                    text = stream.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stream.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ExpressiveIconButton(
                icon = if (stream.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                description = if (stream.favorite) "Remove from favorites" else "Add to favorites",
                onClick = { StreamStore.toggleFavorite(stream.url) }
            )
            ExpressiveIconButton(
                icon = Icons.Filled.Delete,
                description = "Remove",
                onClick = { StreamStore.remove(stream.url) }
            )
        }
    }
}
