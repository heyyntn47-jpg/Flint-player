// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.library

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.flint.player.ui.EmptyPlaceholder
import com.flint.player.ui.ExpressiveButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import com.flint.player.data.Prefs

class VideoLibraryState(
    val hasPermission: Boolean,
    val loading: Boolean,
    val videos: List<VideoItem>,
    val requestPermission: () -> Unit,
    val refresh: () -> Unit
)

/** Saved copy of the last scan, so the list shows instantly at startup. */
private object VideoCache {
    private fun file(context: Context) = File(context.filesDir, "video_list.json")

    fun read(context: Context): List<VideoItem> = runCatching {
        val f = file(context)
        if (!f.exists()) return emptyList()
        val array = JSONArray(f.readText())
        val base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            val id = o.getLong("id")
            VideoItem(
                id = id,
                uri = ContentUris.withAppendedId(base, id),
                name = o.getString("name"),
                durationMs = o.getLong("dur"),
                folder = o.getString("folder"),
                path = o.getString("path"),
                sizeBytes = o.getLong("size"),
                dateAddedSec = o.getLong("date")
            )
        }
    }.getOrDefault(emptyList())

    fun write(context: Context, videos: List<VideoItem>) {
        runCatching {
            val array = JSONArray()
            videos.forEach { v ->
                array.put(
                    JSONObject()
                        .put("id", v.id)
                        .put("name", v.name)
                        .put("dur", v.durationMs)
                        .put("folder", v.folder)
                        .put("path", v.path)
                        .put("size", v.sizeBytes)
                        .put("date", v.dateAddedSec)
                )
            }
            file(context).writeText(array.toString())
        }
    }
}

@Composable
fun rememberVideoLibrary(): VideoLibraryState {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    var allVideos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(hasPermission, reloadTick) {
        if (!hasPermission) return@LaunchedEffect

        // 1. Show the saved list immediately
        val cached = if (allVideos.isEmpty()) {
            withContext(Dispatchers.IO) { VideoCache.read(context) }
        } else {
            allVideos
        }
        if (allVideos.isEmpty() && cached.isNotEmpty()) allVideos = cached
        loading = allVideos.isEmpty()

        // 2. Refresh from the device in the background
        val fresh = withContext(Dispatchers.IO) {
            val list = queryVideos(context)
            if (list != cached) VideoCache.write(context, list)
            list
        }
        if (fresh != allVideos) allVideos = fresh
        loading = false
    }

    // The folder setting is applied here, so changing it updates the library right away without a rescan
    val folderMode = Prefs.folderMode
    val included = Prefs.includedFolders
    val excluded = Prefs.excludedFolders
    val videos = remember(allVideos, folderMode, included, excluded) {
        allVideos.filterByFolders(folderMode, included, excluded)
    }

    // A few seconds after the list is ready, quietly prepare thumbnails for the whole library
    LaunchedEffect(videos) {
        if (videos.isNotEmpty() && Prefs.prewarmThumbnails) {
            delay(4_000)
            val ordered = withContext(Dispatchers.Default) {
                videos.sortedWith(compareBy({ it.folder.lowercase() }, { it.name.lowercase() }))
                    .map { it.uri }
            }
            ThumbnailLoader.prewarm(context, ordered)
        }
    }

    return VideoLibraryState(
        hasPermission = hasPermission,
        loading = loading,
        videos = videos,
        requestPermission = { launcher.launch(permission) },
        refresh = { reloadTick++ }
    )
}

@Composable
fun PermissionPrompt(onRequest: () -> Unit) {
    EmptyPlaceholder(
        icon = Icons.Filled.VideoLibrary,
        title = "Find your videos",
        subtitle = "Allow access so the videos on this device show up here.",
        action = { ExpressiveButton(text = "Allow access", onClick = onRequest) }
    )
}