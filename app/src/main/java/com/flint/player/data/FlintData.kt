// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class MediaEntry(val uri: String, val title: String, val durationMs: Long = 0L)

data class HistoryEntry(
    val uri: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastPlayed: Long
)

data class Playlist(val id: String, val name: String, val items: List<MediaEntry>)

object FlintData {
    private const val MAX_HISTORY = 100
    private var prefs: SharedPreferences? = null

    var history by mutableStateOf<List<HistoryEntry>>(emptyList())
        private set

    var playlists by mutableStateOf<List<Playlist>>(emptyList())
        private set

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("flint_data", Context.MODE_PRIVATE)
        prefs = p
        history = runCatching { parseHistory(p.getString("history", null)) }
            .getOrDefault(emptyList())
        playlists = runCatching { parsePlaylists(p.getString("playlists", null)) }
            .getOrDefault(emptyList())
    }

    // ---------- History ----------

    fun recordPlayback(uri: String, title: String, positionMs: Long, durationMs: Long) {
        val entry = HistoryEntry(uri, title, positionMs, durationMs, System.currentTimeMillis())
        history = (listOf(entry) + history.filter { it.uri != uri }).take(MAX_HISTORY)
        saveHistory()
    }

    fun resumePosition(uri: String): Long {
        val entry = history.firstOrNull { it.uri == uri } ?: return 0L
        val nearEnd = entry.durationMs > 0 && entry.positionMs >= entry.durationMs - 5_000
        return if (nearEnd || entry.positionMs < 3_000) 0L else entry.positionMs
    }

    fun removeHistory(uri: String) {
        history = history.filter { it.uri != uri }
        saveHistory()
    }

    fun clearHistory() {
        history = emptyList()
        saveHistory()
    }

    // ---------- Playlists ----------

    fun playlist(id: String): Playlist? = playlists.firstOrNull { it.id == id }

    fun createPlaylist(name: String): Playlist {
        val playlist = Playlist(UUID.randomUUID().toString(), name.trim().ifEmpty { "Untitled" }, emptyList())
        playlists = playlists + playlist
        savePlaylists()
        return playlist
    }

    fun renamePlaylist(id: String, name: String) {
        playlists = playlists.map { p ->
            if (p.id == id) p.copy(name = name.trim().ifEmpty { p.name }) else p
        }
        savePlaylists()
    }

    fun deletePlaylist(id: String) {
        playlists = playlists.filter { it.id != id }
        savePlaylists()
    }

    /** Returns false if the video was already in the playlist. */
    fun addToPlaylist(id: String, entry: MediaEntry): Boolean {
        val target = playlist(id) ?: return false
        if (target.items.any { it.uri == entry.uri }) return false
        playlists = playlists.map { p ->
            if (p.id == id) p.copy(items = p.items + entry) else p
        }
        savePlaylists()
        return true
    }

    fun removeFromPlaylist(id: String, uri: String) {
        playlists = playlists.map { p ->
            if (p.id == id) p.copy(items = p.items.filter { it.uri != uri }) else p
        }
        savePlaylists()
    }

    // ---------- Storage ----------

    private fun saveHistory() {
        val array = JSONArray()
        history.forEach { h ->
            array.put(
                JSONObject()
                    .put("uri", h.uri)
                    .put("title", h.title)
                    .put("pos", h.positionMs)
                    .put("dur", h.durationMs)
                    .put("time", h.lastPlayed)
            )
        }
        prefs?.edit()?.putString("history", array.toString())?.apply()
    }

    private fun savePlaylists() {
        val array = JSONArray()
        playlists.forEach { p ->
            val items = JSONArray()
            p.items.forEach { e ->
                items.put(
                    JSONObject()
                        .put("uri", e.uri)
                        .put("title", e.title)
                        .put("dur", e.durationMs)
                )
            }
            array.put(JSONObject().put("id", p.id).put("name", p.name).put("items", items))
        }
        prefs?.edit()?.putString("playlists", array.toString())?.apply()
    }

    private fun parseHistory(json: String?): List<HistoryEntry> {
        if (json.isNullOrEmpty()) return emptyList()
        val array = JSONArray(json)
        return List(array.length()) { i ->
            val o = array.getJSONObject(i)
            HistoryEntry(
                uri = o.getString("uri"),
                title = o.optString("title"),
                positionMs = o.optLong("pos"),
                durationMs = o.optLong("dur"),
                lastPlayed = o.optLong("time")
            )
        }
    }

    private fun parsePlaylists(json: String?): List<Playlist> {
        if (json.isNullOrEmpty()) return emptyList()
        val array = JSONArray(json)
        return List(array.length()) { i ->
            val o = array.getJSONObject(i)
            val items = o.getJSONArray("items")
            Playlist(
                id = o.getString("id"),
                name = o.getString("name"),
                items = List(items.length()) { j ->
                    val item = items.getJSONObject(j)
                    MediaEntry(item.getString("uri"), item.optString("title"), item.optLong("dur"))
                }
            )
        }
    }
}

/** Best-effort display name for a video uri. */
fun resolveTitle(context: Context, uri: Uri): String {
    if (uri.scheme == "content") {
        runCatching {
            context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.let { name -> return name }
                }
            }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: uri.toString()
}