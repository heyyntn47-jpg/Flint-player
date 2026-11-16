// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.network

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flint.player.ui.glassBorder
import com.flint.player.ui.EmptyPlaceholder
import com.flint.player.ui.ExpressiveChip
import com.flint.player.ui.ExpressiveDialog
import com.flint.player.ui.ExpressiveIconButton
import java.util.UUID

/** What is open in the folder browser: a saved server, or a share found on the network. */
private data class Target(
    val title: String,
    val subtitle: String,
    val server: NetworkServer?,
    /** True for UPnP / DLNA, where items have titles instead of file names. */
    val relaxed: Boolean
)

/**
 * Saved SMB / FTP / SFTP / NFS servers, shares found on the local network (including UPnP / DLNA),
 * and a folder browser for whichever one is open. [onBrowsingChange] tells the parent when a
 * folder is showing, so it can leave the back button to this screen.
 */
@Composable
fun NetworkTab(onOpenVideo: (Uri) -> Unit, onBrowsingChange: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val browser = remember { NetworkBrowser(context.applicationContext) }
    val discovery = remember { NetworkDiscovery(context.applicationContext) }
    DisposableEffect(Unit) {
        discovery.scan()
        onDispose {
            browser.release()
            discovery.release()
        }
    }

    var target by remember { mutableStateOf<Target?>(null) }
    var stack by remember { mutableStateOf(listOf<String>()) }
    var editing by remember { mutableStateOf<NetworkServer?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<NetworkServer?>(null) }

    LaunchedEffect(target, stack) {
        val current = target
        if (current != null && stack.isNotEmpty()) {
            val login = current.server ?: NetworkAuth.findServer(stack.last(), NetworkStore.servers)
            browser.browse(stack.last(), login, current.relaxed)
        }
    }
    LaunchedEffect(target != null) { onBrowsingChange(target != null) }

    fun goUp() {
        if (stack.size > 1) {
            stack = stack.dropLast(1)
        } else {
            target = null
            stack = emptyList()
        }
    }

    BackHandler(enabled = target != null) { goUp() }

    val open = target
    if (open == null) {
        ServerList(
            servers = NetworkStore.servers,
            discovery = discovery,
            onOpen = { s ->
                target = Target(s.name.ifBlank { s.host }, "${s.protocol.label} · ${s.host}", s, false)
                stack = listOf(NetworkAuth.baseUrl(s))
            },
            onOpenShare = { share ->
                val upnp = share.url.startsWith("upnp", ignoreCase = true)
                target = Target(share.name, if (upnp) "UPnP / DLNA" else "Found on your network", null, upnp)
                stack = listOf(share.url)
            },
            onRescan = { discovery.scan() },
            onAdd = { editing = null; showEditor = true },
            onEdit = { editing = it; showEditor = true },
            onDelete = { deleting = it }
        )
    } else {
        FolderList(
            title = open.title,
            subtitle = open.subtitle,
            depth = stack.size,
            browser = browser,
            onUp = { goUp() },
            onFolder = { stack = stack + it.url },
            onVideo = { onOpenVideo(Uri.parse(it.url)) },
            onRetry = {
                browser.browse(
                    stack.last(),
                    open.server ?: NetworkAuth.findServer(stack.last(), NetworkStore.servers),
                    open.relaxed
                )
            }
        )
    }

    if (showEditor) {
        ServerEditor(
            existing = editing,
            onDismiss = { showEditor = false },
            onSave = {
                NetworkStore.save(it)
                showEditor = false
            }
        )
    }

    deleting?.let { victim ->
        ExpressiveDialog(
            title = "Remove server?",
            onDismiss = { deleting = null },
            confirmText = "Remove",
            destructive = true,
            onConfirm = {
                NetworkStore.remove(victim.id)
                deleting = null
            }
        ) {
            Text(
                text = "${victim.name} will be removed from Flint. Nothing on the server changes.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
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
private fun ServerRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    actions: @Composable () -> Unit = {}
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = glassBorder()
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            actions()
        }
    }
}

@Composable
private fun ServerList(
    servers: List<NetworkServer>,
    discovery: NetworkDiscovery,
    onOpen: (NetworkServer) -> Unit,
    onOpenShare: (DiscoveredShare) -> Unit,
    onRescan: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (NetworkServer) -> Unit,
    onDelete: (NetworkServer) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 128.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "add") {
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add server (SMB, FTP, SFTP, NFS)")
            }
        }
        if (servers.isNotEmpty()) {
            item(key = "saved-header") { SectionHeader("Saved servers") }
            items(servers, key = { "s-" + it.id }) { s ->
                ServerRow(
                    title = s.name.ifBlank { s.host },
                    subtitle = "${s.protocol.label} · ${s.host}",
                    icon = Icons.Filled.Dns,
                    onClick = { onOpen(s) }
                ) {
                    ExpressiveIconButton(
                        icon = Icons.Filled.Edit,
                        description = "Edit server",
                        onClick = { onEdit(s) }
                    )
                    ExpressiveIconButton(
                        icon = Icons.Filled.Delete,
                        description = "Remove server",
                        onClick = { onDelete(s) }
                    )
                }
            }
        }
        item(key = "nearby-header") {
            SectionHeader("On your network") {
                if (discovery.scanning) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp).padding(end = 0.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                } else {
                    TextButton(onClick = onRescan) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Scan again")
                    }
                }
            }
        }
        if (discovery.shares.isEmpty()) {
            item(key = "nearby-empty") {
                Text(
                    text = if (discovery.scanning) "Looking for SMB, NFS, FTP and UPnP / DLNA servers…"
                    else "Nothing found. Check that the phone is on the same Wi-Fi as the server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
            }
        } else {
            items(discovery.shares, key = { "d-" + it.url }) { share ->
                ServerRow(
                    title = share.name,
                    subtitle = share.url.substringBefore("://").uppercase() + " · " + share.url.substringAfter("://").trimEnd('/'),
                    icon = Icons.Filled.Wifi,
                    onClick = { onOpenShare(share) }
                )
            }
        }
    }
}

@Composable
private fun FolderList(
    title: String,
    subtitle: String,
    depth: Int,
    browser: NetworkBrowser,
    onUp: () -> Unit,
    onFolder: (NetEntry) -> Unit,
    onVideo: (NetEntry) -> Unit,
    onRetry: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExpressiveIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                description = if (depth > 1) "Up one folder" else "Back to servers",
                onClick = onUp
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        val entries = browser.entries
        when {
            browser.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            browser.emptyOrUnreachable -> EmptyPlaceholder(
                icon = Icons.Filled.Folder,
                title = "Nothing to show",
                subtitle = "This folder has no videos, or the server didn't answer. " +
                    "Check the address, login and that the phone is on the same network.",
                action = { Button(onClick = onRetry) { Text("Try again") } }
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 128.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(entries, key = { it.url }) { entry ->
                    Surface(
                        onClick = { if (entry.isDirectory) onFolder(entry) else onVideo(entry) },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = glassBorder()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerEditor(
    existing: NetworkServer?,
    onDismiss: () -> Unit,
    onSave: (NetworkServer) -> Unit
) {
    var protocol by remember { mutableStateOf(existing?.protocol ?: NetProtocol.SMB) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var host by remember { mutableStateOf(existing?.host ?: "") }
    var port by remember { mutableStateOf(existing?.port?.takeIf { it > 0 }?.toString() ?: "") }
    var path by remember { mutableStateOf(existing?.path ?: "") }
    var user by remember { mutableStateOf(existing?.username ?: "") }
    var pass by remember { mutableStateOf(existing?.password ?: "") }

    val cleanHost = host.trim()
        .removePrefix("smb://").removePrefix("ftp://").removePrefix("sftp://").removePrefix("nfs://")
        .trim('/')
    val valid = cleanHost.isNotEmpty() && !cleanHost.contains(' ')

    ExpressiveDialog(
        title = if (existing == null) "Add server" else "Edit server",
        onDismiss = onDismiss,
        confirmText = "Save",
        confirmEnabled = valid,
        onConfirm = {
            onSave(
                NetworkServer(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    name = name.trim(),
                    protocol = protocol,
                    host = cleanHost,
                    port = port.trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: 0,
                    path = path.trim(),
                    username = user.trim(),
                    password = pass
                )
            )
        }
    ) {
        Column(
            modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NetProtocol.entries.forEach { p ->
                    ExpressiveChip(label = p.label, selected = protocol == p, onClick = { protocol = p })
                }
            }
            Field("Name (optional)", name) { name = it }
            Field("Host or IP address", host, KeyboardType.Uri) { host = it }
            Field("Port (optional)", port, KeyboardType.Number) { port = it }
            Field(
                when (protocol) {
                    NetProtocol.SMB -> "Share and folder, e.g. Movies/Action"
                    NetProtocol.NFS -> "Export path, e.g. volume1/video"
                    else -> "Folder (optional)"
                },
                path,
                KeyboardType.Uri
            ) { path = it }
            Field("User name (empty = guest / anonymous)", user) { user = it }
            Field("Password", pass, KeyboardType.Password, hidden = true) { pass = it }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Saved on this phone only. For SMB domains use DOMAIN\\user.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    keyboard: KeyboardType = KeyboardType.Text,
    hidden: Boolean = false,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (hidden) PasswordVisualTransformation()
        else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth()
    )
}
