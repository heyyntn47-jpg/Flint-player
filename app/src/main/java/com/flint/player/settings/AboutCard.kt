// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.settings

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flint.player.R
import com.flint.player.ui.glassStroke
import com.flint.player.ui.ExpressiveDialog
import com.flint.player.ui.pressItem

/**
 * Everything the About card shows lives here. Edit this one object and the card,
 * the GitHub button and the Libraries dialog all follow.
 */
object AppInfo {
    const val NAME = "Flint"
    const val DESCRIPTION =
        "A fast, libVLC-powered video player for Android with network playback, " +
            "subtitles, an equalizer, background play and casting."
    const val DEVELOPER = "heyyntn47"

    const val GITHUB_URL = "https://github.com/heyyntn47-jpg/Flint-player"

    class Library(val name: String, val version: String, val license: String)

    /** One line per library. Keep in sync with app/build.gradle.kts and licenses/FONTS.md. */
    val libraries = listOf(
        Library("libVLC", "3.7.6", "LGPL 2.1"),
        Library("Jetpack Compose UI", "1.12.1", "Apache 2.0"),
        Library("Jetpack Compose Foundation", "1.12.1", "Apache 2.0"),
        Library("Material 3 for Compose", "1.5.0-rc01", "Apache 2.0"),
        Library("Material Icons Extended", "1.7.8", "Apache 2.0"),
        Library("Activity Compose", "1.13.0", "Apache 2.0"),
        Library("Navigation Compose", "2.10.2", "Apache 2.0"),
        Library("Material Kolor", "5.0.0", "MIT"),
        Library("ProfileInstaller", "1.4.1", "Apache 2.0"),
        Library("Kotlin Coroutines", "bundled", "Apache 2.0"),
        Library("Inter (font)", "bundled", "SIL OFL 1.1"),
        Library("Poppins (font)", "bundled", "SIL OFL 1.1")
    )
}

/** Rounded pill: icon on top, description, developer + version, then GitHub and Libraries side by side. */
@Composable
internal fun AboutCard(version: String, onLibraries: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(scheme.surfaceContainer)
            .glassStroke(RoundedCornerShape(36.dp))
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The adaptive icon's foreground has a safe-zone margin, so it is scaled up to fill the tile
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(Color.Black)
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = "${AppInfo.NAME} icon",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = 1.5f; scaleY = 1.5f }
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = AppInfo.NAME,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = AppInfo.DESCRIPTION,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "by ${AppInfo.DEVELOPER}  •  v$version",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = scheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AboutButton(
                icon = Icons.Outlined.Code,
                label = "GitHub",
                modifier = Modifier.weight(1f)
            ) {
                runCatching { uriHandler.openUri(AppInfo.GITHUB_URL) }.onFailure {
                    Toast.makeText(context, "No app found to open the link", Toast.LENGTH_SHORT).show()
                }
            }
            AboutButton(
                icon = Icons.Outlined.Inventory2,
                label = "Libraries",
                modifier = Modifier.weight(1f),
                onClick = onLibraries
            )
        }
    }
}

@Composable
private fun AboutButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .pressItem(
                shape = RoundedCornerShape(26.dp),
                container = scheme.secondaryContainer,
                onClick = onClick
            )
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = scheme.onSecondaryContainer
        )
    }
}

@Composable
internal fun LibrariesDialog(onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    ExpressiveDialog(
        title = "Libraries",
        onDismiss = onDismiss,
        dismissText = "Close"
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppInfo.libraries.forEach { lib ->
                Column {
                    Text(
                        text = lib.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = scheme.onSurface
                    )
                    Text(
                        text = "${lib.version} • ${lib.license}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
