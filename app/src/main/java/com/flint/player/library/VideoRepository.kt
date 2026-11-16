// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.library

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val folder: String,
    val path: String,
    val sizeBytes: Long,
    val dateAddedSec: Long
)

enum class SortField(val label: String) {
    NAME("Name"),
    DATE("Date added"),
    DURATION("Duration"),
    SIZE("Size")
}

/** How video files are laid out in the library. */
enum class ViewMode(val label: String) {
    LIST("List"),
    GRID("Grid")
}

fun List<VideoItem>.sortVideos(field: SortField, ascending: Boolean): List<VideoItem> {
    val comparator: Comparator<VideoItem> = when (field) {
        SortField.NAME -> Comparator { a, b -> a.name.compareTo(b.name, ignoreCase = true) }
        SortField.DATE -> compareBy<VideoItem> { it.dateAddedSec }
        SortField.DURATION -> compareBy<VideoItem> { it.durationMs }
        SortField.SIZE -> compareBy<VideoItem> { it.sizeBytes }
    }
    return sortedWith(if (ascending) comparator else comparator.reversed())
}

/** The folder a video file sits in, e.g. /storage/emulated/0/Movies. Empty if the path is unknown. */
fun folderOf(path: String): String = path.substringBeforeLast('/', "")

/**
 * Applies the folder setting. [mode] 0 keeps everything, 1 keeps only videos in [included],
 * 2 drops videos in [excluded]. A folder matches only itself, not its subfolders.
 */
fun List<VideoItem>.filterByFolders(
    mode: Int,
    included: Set<String>,
    excluded: Set<String>
): List<VideoItem> = when (mode) {
    1 -> filter { folderOf(it.path) in included }
    2 -> filter { folderOf(it.path) !in excluded }
    else -> this
}

/**
 * Reads every video from the device's media store.
 *
 * The file path is needed to group videos by folder. The old `DATA` column is deprecated and
 * can be empty under scoped storage, so on Android 10+ the path is rebuilt from `RELATIVE_PATH`
 * when `DATA` has nothing.
 */
@Suppress("DEPRECATION")
fun queryVideos(context: Context): List<VideoItem> {
    val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val scoped = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    val projection = buildList {
        add(MediaStore.Video.Media._ID)
        add(MediaStore.Video.Media.DISPLAY_NAME)
        add(MediaStore.Video.Media.DURATION)
        add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
        add(MediaStore.Video.Media.DATA)
        add(MediaStore.Video.Media.SIZE)
        add(MediaStore.Video.Media.DATE_ADDED)
        if (scoped) add(MediaStore.MediaColumns.RELATIVE_PATH)
    }.toTypedArray()

    val videos = mutableListOf<VideoItem>()
    context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        val folderCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
        val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
        val relativeCol =
            if (scoped) cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH) else -1

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val name = cursor.getString(nameCol) ?: "Unknown"
            var path = cursor.getString(pathCol).orEmpty()
            if (path.isEmpty() && relativeCol >= 0) {
                cursor.getString(relativeCol)?.takeIf { it.isNotBlank() }?.let { relative ->
                    path = "/storage/emulated/0/" + relative.trimEnd('/') + "/" + name
                }
            }
            videos += VideoItem(
                id = id,
                uri = ContentUris.withAppendedId(collection, id),
                name = name,
                durationMs = cursor.getLong(durationCol),
                folder = cursor.getString(folderCol) ?: "Other",
                path = path,
                sizeBytes = cursor.getLong(sizeCol),
                dateAddedSec = cursor.getLong(dateCol)
            )
        }
    }
    return videos
}

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1e9)
    bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1e6)
    else -> "%.0f KB".format(bytes / 1e3)
}