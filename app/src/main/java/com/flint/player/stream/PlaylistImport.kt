// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.stream

import com.flint.player.data.MediaEntry
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Downloads a web playlist (M3U, PLS or XSPF) and turns it into a list of videos. */
object PlaylistImport {
    class Parsed(val entries: List<MediaEntry>, val adaptive: Boolean)

    private const val MAX_ENTRIES = 500
    private const val MAX_BYTES = 2_000_000

    /** Blocking. Call from a background thread. */
    fun load(url: String, userAgent: String): Parsed {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", userAgent.ifBlank { "Flint" })
        val text = try {
            connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (out.size() < MAX_BYTES) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                }
                out.toString("UTF-8")
            }
        } finally {
            connection.disconnect()
        }
        return parse(text, url)
    }

    fun parse(text: String, baseUrl: String): Parsed {
        // HLS lists are one live or on-demand stream, not a list of separate videos
        val adaptive = text.contains("#EXT-X-STREAM-INF") ||
            text.contains("#EXT-X-TARGETDURATION") ||
            text.contains("#EXT-X-MEDIA-SEQUENCE")
        if (adaptive) return Parsed(emptyList(), true)
        val entries = when {
            text.contains("<playlist", ignoreCase = true) -> parseXspf(text, baseUrl)
            text.trimStart().startsWith("[playlist]", ignoreCase = true) -> parsePls(text, baseUrl)
            else -> parseM3u(text, baseUrl)
        }
        return Parsed(entries.take(MAX_ENTRIES), false)
    }

    private fun parseM3u(text: String, base: String): List<MediaEntry> {
        val result = ArrayList<MediaEntry>()
        var pendingTitle: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim().removePrefix("\uFEFF")
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                pendingTitle = line.substringAfter(',', "").trim().ifEmpty { null }
                continue
            }
            if (line.startsWith("#")) continue
            val url = resolve(base, line) ?: continue
            result.add(MediaEntry(url, pendingTitle ?: StreamStore.titleFor(url)))
            pendingTitle = null
        }
        return result
    }

    private fun parsePls(text: String, base: String): List<MediaEntry> {
        val files = LinkedHashMap<Int, String>()
        val titles = HashMap<Int, String>()
        val line = Regex("^(File|Title)(\\d+)=(.*)$", RegexOption.IGNORE_CASE)
        for (raw in text.lineSequence()) {
            val m = line.matchEntire(raw.trim()) ?: continue
            val index = m.groupValues[2].toIntOrNull() ?: continue
            if (m.groupValues[1].equals("File", ignoreCase = true)) files[index] = m.groupValues[3].trim()
            else titles[index] = m.groupValues[3].trim()
        }
        return files.entries.sortedBy { it.key }.mapNotNull { (index, file) ->
            val url = resolve(base, file) ?: return@mapNotNull null
            MediaEntry(url, titles[index]?.ifEmpty { null } ?: StreamStore.titleFor(url))
        }
    }

    private fun parseXspf(text: String, base: String): List<MediaEntry> {
        val track = Regex("<track>(.*?)</track>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val location = Regex("<location>(.*?)</location>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val title = Regex("<title>(.*?)</title>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        return track.findAll(text).mapNotNull { t ->
            val body = t.groupValues[1]
            val loc = location.find(body)?.groupValues?.get(1)?.let(::unescape)?.trim() ?: return@mapNotNull null
            val url = resolve(base, loc) ?: return@mapNotNull null
            val name = title.find(body)?.groupValues?.get(1)?.let(::unescape)?.trim()
            MediaEntry(url, name?.ifEmpty { null } ?: StreamStore.titleFor(url))
        }.toList()
    }

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&lt;", "<")
        .replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")

    private fun resolve(base: String, ref: String): String? {
        if (ref.contains("://")) return ref
        val resolved = runCatching { URI(base).resolve(ref).toString() }.getOrNull() ?: return null
        return resolved.takeIf { it.contains("://") }
    }
}
