// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.stream

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.flint.player.util.Logger
import org.json.JSONArray
import org.json.JSONObject

/** Per-stream settings, handed to libVLC as media options when that exact url is opened. */
data class StreamOptions(
    /** Network cache in milliseconds. 0 keeps the player's default. */
    val cachingMs: Int = 0,
    val userAgent: String = "",
    val referrer: String = "",
    /** Address of an external subtitle file to load with the stream. */
    val subtitleUrl: String = "",
    val rtspTcp: Boolean = false,
    val reconnect: Boolean = false
) {
    fun toMediaOptions(): List<String> {
        val result = ArrayList<String>()
        if (cachingMs > 0) {
            result.add(":network-caching=$cachingMs")
            result.add(":live-caching=$cachingMs")
        }
        clean(userAgent).takeIf { it.isNotEmpty() }?.let { result.add(":http-user-agent=$it") }
        clean(referrer).takeIf { it.isNotEmpty() }?.let { result.add(":http-referrer=$it") }
        clean(subtitleUrl).takeIf { it.isNotEmpty() }?.let { result.add(":input-slave=$it") }
        if (rtspTcp) result.add(":rtsp-tcp")
        if (reconnect) result.add(":http-reconnect")
        return result
    }

    private fun clean(text: String) = text.replace('\n', ' ').replace('\r', ' ').trim()
}

data class SavedStream(
    val url: String,
    val title: String,
    val favorite: Boolean,
    val lastUsed: Long,
    val options: StreamOptions
)

/** Streams opened from the Stream menu: recent ones, favorites, and the options each was opened with. */
object StreamStore {
    private const val MAX_RECENTS = 40
    private var prefs: SharedPreferences? = null

    var streams by mutableStateOf<List<SavedStream>>(emptyList())
        private set

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("flint_streams", Context.MODE_PRIVATE)
        prefs = p
        streams = runCatching { parse(p.getString("streams", null)) }
            .onFailure { Logger.warn("StreamStore", "Could not read saved streams", it) }
            .getOrDefault(emptyList())
    }

    fun titleFor(url: String): String {
        val uri = Uri.parse(url)
        return uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: uri.host ?: url
    }

    /** Remembers [url] as just used, keeping its favorite mark. */
    fun record(url: String, options: StreamOptions) {
        val old = streams.firstOrNull { it.url == url }
        val entry = SavedStream(url, old?.title ?: titleFor(url), old?.favorite ?: false, System.currentTimeMillis(), options)
        streams = trim(listOf(entry) + streams.filter { it.url != url })
        write()
    }

    /** Saves [url] as a favorite without playing it. */
    fun saveFavorite(url: String, options: StreamOptions) {
        val old = streams.firstOrNull { it.url == url }
        val entry = SavedStream(url, old?.title ?: titleFor(url), true, old?.lastUsed ?: System.currentTimeMillis(), options)
        streams = trim(listOf(entry) + streams.filter { it.url != url })
        write()
    }

    fun toggleFavorite(url: String) {
        streams = streams.map { if (it.url == url) it.copy(favorite = !it.favorite) else it }
        write()
    }

    fun remove(url: String) {
        streams = streams.filterNot { it.url == url }
        write()
    }

    fun clearRecents() {
        streams = streams.filter { it.favorite }
        write()
    }

    /** libVLC options for [url], or nothing if it was not opened from the Stream menu. */
    fun mediaOptions(url: String): List<String> =
        streams.firstOrNull { it.url == url }?.options?.toMediaOptions() ?: emptyList()

    private fun trim(list: List<SavedStream>): List<SavedStream> {
        val favorites = list.filter { it.favorite }
        val recents = list.filterNot { it.favorite }.take(MAX_RECENTS)
        return (favorites + recents).sortedByDescending { it.lastUsed }
    }

    private fun write() {
        val array = JSONArray()
        streams.forEach { s ->
            array.put(
                JSONObject()
                    .put("url", s.url)
                    .put("title", s.title)
                    .put("fav", s.favorite)
                    .put("used", s.lastUsed)
                    .put("cache", s.options.cachingMs)
                    .put("ua", s.options.userAgent)
                    .put("ref", s.options.referrer)
                    .put("sub", s.options.subtitleUrl)
                    .put("tcp", s.options.rtspTcp)
                    .put("reconnect", s.options.reconnect)
            )
        }
        prefs?.edit()?.putString("streams", array.toString())?.apply()
    }

    private fun parse(text: String?): List<SavedStream> {
        if (text.isNullOrEmpty()) return emptyList()
        val array = JSONArray(text)
        val result = ArrayList<SavedStream>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val url = o.optString("url")
            if (url.isEmpty()) continue
            result.add(
                SavedStream(
                    url = url,
                    title = o.optString("title").ifEmpty { titleFor(url) },
                    favorite = o.optBoolean("fav", false),
                    lastUsed = o.optLong("used", 0L),
                    options = StreamOptions(
                        cachingMs = o.optInt("cache", 0),
                        userAgent = o.optString("ua"),
                        referrer = o.optString("ref"),
                        subtitleUrl = o.optString("sub"),
                        rtspTcp = o.optBoolean("tcp", false),
                        reconnect = o.optBoolean("reconnect", false)
                    )
                )
            )
        }
        return result
    }
}
