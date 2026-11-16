// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.player

import java.util.Locale

/**
 * Turns most subtitle formats into a clean SRT:
 * SRT, WebVTT, ASS/SSA, TTML/DFXP, SAMI (.smi), MicroDVD (.sub), SubViewer, SBV and LRC.
 */
object SubtitleConverter {

    class Converted(val srt: String, val cueCount: Int)

    private class Cue(val startMs: Long, val endMs: Long, val text: String)

    fun convert(raw: String, videoDurationMs: Long): Converted? {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n')

        val cues = parse(text, videoDurationMs)
            .filter { it.text.isNotBlank() }
            .map { cue ->
                if (cue.endMs <= cue.startMs) Cue(cue.startMs, cue.startMs + 2_000, cue.text) else cue
            }
            .distinctBy { Triple(it.startMs, it.endMs, it.text) }
            .sortedBy { it.startMs }

        if (cues.isEmpty()) return null

        val out = StringBuilder()
        cues.forEachIndexed { index, cue ->
            out.append(index + 1).append('\n')
                .append(srtTime(cue.startMs)).append(" --> ").append(srtTime(cue.endMs)).append('\n')
                .append(cue.text).append("\n\n")
        }
        return Converted(out.toString(), cues.size)
    }

    // ---------- Detection ----------

    private val TIMING = Regex(
        """(?:(\d+):)?(\d{1,2}):(\d{2})[.,](\d{1,3})\s*-->\s*(?:(\d+):)?(\d{1,2}):(\d{2})[.,](\d{1,3})"""
    )
    private val SBV = Regex(
        """(?m)^\s*(\d+):(\d{2}):(\d{2})[.,](\d{1,3})\s*,\s*(\d+):(\d{2}):(\d{2})[.,](\d{1,3})\s*$"""
    )
    private val MICRO = Regex("""^\{(\d+)\}\{(\d*)\}(.*)$""")
    private val MICRO_HEAD = Regex("""(?m)^\{\d+\}\{\d*\}""")
    private val LRC_TAG = Regex("""\[(\d+):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val TTML_START = Regex("""<tt[\s>:]""")

    private fun parse(text: String, durationMs: Long): List<Cue> {
        val head = text.take(6_000)
        val lower = head.lowercase(Locale.ROOT)
        return when {
            "[script info]" in lower || "[events]" in lower -> parseAss(text)
            "<sami" in lower -> parseSami(text)
            TTML_START.containsMatchIn(lower) -> parseTtml(text)
            MICRO_HEAD.containsMatchIn(head) -> parseMicroDvd(text, durationMs)
            TIMING.containsMatchIn(text) -> parseTimed(text)
            SBV.containsMatchIn(text) -> parseSbv(text)
            LRC_TAG.containsMatchIn(head) -> parseLrc(text)
            else -> emptyList()
        }
    }

    // ---------- Helpers ----------

    private fun ms(h: String, m: String, s: String, frac: String): Long {
        val hours = h.toLongOrNull() ?: 0L
        val minutes = m.toLongOrNull() ?: 0L
        val seconds = s.toLongOrNull() ?: 0L
        val millis = if (frac.isEmpty()) 0L else frac.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis
    }

    private fun srtTime(value: Long): String {
        val v = value.coerceAtLeast(0L)
        return "%02d:%02d:%02d,%03d".format(
            Locale.US, v / 3_600_000, v / 60_000 % 60, v / 1_000 % 60, v % 1_000
        )
    }

    private val OVERRIDE = Regex("""\{[^}]*\}""")
    private val BR = Regex("""(?i)<br\s*/?>""")
    private val OTHER_TAGS = Regex("""(?i)<(?!/?[ibu]\s*>)[^>]*>""")

    /** Keeps italic / bold / underline, removes everything else. */
    private fun clean(raw: String): String {
        var s = raw
            .replace("{\\i1}", "<i>").replace("{\\i0}", "</i>")
            .replace("{\\b1}", "<b>").replace("{\\b0}", "</b>")
            .replace("{\\u1}", "<u>").replace("{\\u0}", "</u>")
        s = OVERRIDE.replace(s, "")
        s = BR.replace(s, "\n")
        s = OTHER_TAGS.replace(s, "")
        s = s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'")
            .replace("&amp;", "&")
        return s.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    }

    // ---------- SRT and WebVTT ----------

    private fun parseTimed(text: String): List<Cue> {
        val lines = text.split('\n')
        val cues = ArrayList<Cue>()
        var i = 0
        while (i < lines.size) {
            val match = TIMING.find(lines[i])
            if (match == null) {
                i++
                continue
            }
            val g = match.groupValues
            val start = ms(g[1], g[2], g[3], g[4])
            val end = ms(g[5], g[6], g[7], g[8])
            i++

            val body = StringBuilder()
            while (i < lines.size) {
                val line = lines[i]
                if (line.isBlank()) break
                if (TIMING.containsMatchIn(line)) break
                // A bare number right before the next timing line is the next cue's index
                if (line.trim().all { it.isDigit() } &&
                    i + 1 < lines.size && TIMING.containsMatchIn(lines[i + 1])
                ) break
                body.append(line).append('\n')
                i++
            }
            cues += Cue(start, end, clean(body.toString()))
        }
        return cues
    }

    // ---------- ASS / SSA ----------

    private val ASS_TIME = Regex("""(\d+):(\d{1,2}):(\d{1,2})[.:](\d{1,3})""")
    private val ASS_DRAWING = Regex("""\{[^}]*\\p[1-9][^}]*\}""")

    private fun assTime(value: String): Long? {
        val m = ASS_TIME.find(value) ?: return null
        val g = m.groupValues
        return ms(g[1], g[2], g[3], g[4])
    }

    private fun parseAss(text: String): List<Cue> {
        var inEvents = false
        var columns = 10
        var startIdx = 1
        var endIdx = 2
        var textIdx = 9
        val cues = ArrayList<Cue>()

        for (rawLine in text.split('\n')) {
            val line = rawLine.trim()
            if (line.startsWith("[")) {
                inEvents = line.equals("[Events]", ignoreCase = true)
                continue
            }
            if (!inEvents) continue

            if (line.startsWith("Format:", ignoreCase = true)) {
                val names = line.substringAfter(':').split(',').map { it.trim().lowercase(Locale.ROOT) }
                columns = names.size
                names.indexOf("start").takeIf { it >= 0 }?.let { startIdx = it }
                names.indexOf("end").takeIf { it >= 0 }?.let { endIdx = it }
                names.indexOf("text").takeIf { it >= 0 }?.let { textIdx = it }
                continue
            }
            if (!line.startsWith("Dialogue:", ignoreCase = true)) continue

            val fields = line.substringAfter(':').split(',', limit = columns)
            if (fields.size <= maxOf(startIdx, endIdx, textIdx)) continue
            val start = assTime(fields[startIdx].trim()) ?: continue
            val end = assTime(fields[endIdx].trim()) ?: continue
            val body = fields[textIdx]
            if (ASS_DRAWING.containsMatchIn(body)) continue   // vector drawings, not text

            cues += Cue(
                start, end,
                clean(body.replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " "))
            )
        }
        return cues
    }

    // ---------- MicroDVD (.sub, frame based) ----------

    private fun parseMicroDvd(text: String, durationMs: Long): List<Cue> {
        val frames = ArrayList<Triple<Long, Long, String>>()
        var declaredFps = 0.0

        for (line in text.split('\n')) {
            val m = MICRO.find(line.trim()) ?: continue
            val startF = m.groupValues[1].toLongOrNull() ?: continue
            val endF = m.groupValues[2].toLongOrNull() ?: (startF + 50)
            val body = m.groupValues[3]

            // The first line may just declare the frame rate: {1}{1}23.976
            if (frames.isEmpty() && declaredFps == 0.0) {
                val declared = body.trim().toDoubleOrNull()
                if (declared != null && declared in 5.0..120.0) {
                    declaredFps = declared
                    continue
                }
            }
            frames += Triple(startF, endF, body)
        }
        if (frames.isEmpty()) return emptyList()

        val fps = if (declaredFps > 0.0) {
            declaredFps
        } else {
            val lastEnd = frames.maxOf { it.second }.toDouble()
            val candidates = listOf(23.976, 24.0, 25.0, 29.97, 30.0)
            if (durationMs > 0) {
                candidates.firstOrNull { lastEnd * 1000.0 / it <= durationMs * 1.03 } ?: 30.0
            } else {
                23.976
            }
        }

        return frames.map { (s, e, body) ->
            Cue(
                (s * 1000.0 / fps).toLong(),
                (e * 1000.0 / fps).toLong(),
                clean(body.replace("|", "\n"))
            )
        }
    }

    // ---------- SBV and SubViewer 2 ----------

    private fun parseSbv(text: String): List<Cue> {
        val lines = text.split('\n')
        val cues = ArrayList<Cue>()
        var i = 0
        while (i < lines.size) {
            val m = SBV.find(lines[i])
            if (m == null) {
                i++
                continue
            }
            val g = m.groupValues
            val start = ms(g[1], g[2], g[3], g[4])
            val end = ms(g[5], g[6], g[7], g[8])
            i++
            val body = StringBuilder()
            while (i < lines.size && lines[i].isNotBlank()) {
                body.append(lines[i]).append('\n')
                i++
            }
            cues += Cue(start, end, clean(body.toString().replace("[br]", "\n")))
        }
        return cues
    }

    // ---------- LRC lyrics ----------

    private fun parseLrc(text: String): List<Cue> {
        val entries = ArrayList<Pair<Long, String>>()
        for (line in text.split('\n')) {
            val tags = LRC_TAG.findAll(line).toList()
            if (tags.isEmpty()) continue
            val body = clean(LRC_TAG.replace(line, ""))
            for (tag in tags) {
                val g = tag.groupValues
                entries += ms("0", g[1], g[2], g[3]) to body
            }
        }
        entries.sortBy { it.first }

        val cues = ArrayList<Cue>()
        for (i in entries.indices) {
            val (start, body) = entries[i]
            if (body.isBlank()) continue
            val next = entries.getOrNull(i + 1)?.first ?: (start + 4_000)
            cues += Cue(start, minOf(next, start + 8_000), body)
        }
        return cues
    }

    // ---------- SAMI (.smi) ----------

    private val SAMI_SYNC = Regex(
        """(?is)<sync\s+start\s*=\s*["']?(\d+)["']?[^>]*>(.*?)(?=<sync\b|</body|\z)"""
    )
    private val SAMI_P = Regex("""(?is)<p\b[^>]*>(.*?)(?=<p\b|\z)""")

    private fun parseSami(text: String): List<Cue> {
        val entries = SAMI_SYNC.findAll(text).map { m ->
            val start = m.groupValues[1].toLongOrNull() ?: 0L
            val inner = m.groupValues[2]
            val firstP = SAMI_P.find(inner)?.groupValues?.get(1) ?: inner
            start to clean(firstP)
        }.toList()

        val cues = ArrayList<Cue>()
        for (i in entries.indices) {
            val (start, body) = entries[i]
            if (body.isBlank()) continue
            val end = entries.getOrNull(i + 1)?.first ?: (start + 4_000)
            cues += Cue(start, end, body)
        }
        return cues
    }

    // ---------- TTML / DFXP ----------

    private val TTML_P = Regex("""(?is)<(?:\w+:)?p\b([^>]*)>(.*?)</(?:\w+:)?p>""")

    private fun attr(attrs: String, name: String): String? =
        Regex("""(?i)\b$name\s*=\s*["']([^"']*)["']""").find(attrs)?.groupValues?.get(1)

    private fun ttmlTime(value: String?): Long? {
        val v = value?.trim() ?: return null
        if (v.endsWith("ms")) return v.removeSuffix("ms").toDoubleOrNull()?.toLong()
        if (v.endsWith("s")) return v.removeSuffix("s").toDoubleOrNull()?.let { (it * 1000).toLong() }
        if (v.endsWith("t")) return null   // tick based timing is not supported
        val parts = v.split(':')
        if (parts.size < 3) return null
        val h = parts[0].toLongOrNull() ?: return null
        val m = parts[1].toLongOrNull() ?: return null
        val s = parts[2].toDoubleOrNull() ?: return null
        val frames = parts.getOrNull(3)?.toDoubleOrNull() ?: 0.0
        return ((h * 60 + m) * 60 * 1000 + s * 1000 + frames * 1000 / 30).toLong()
    }

    private fun parseTtml(text: String): List<Cue> {
        val cues = ArrayList<Cue>()
        for (m in TTML_P.findAll(text)) {
            val attrs = m.groupValues[1]
            val begin = ttmlTime(attr(attrs, "begin")) ?: continue
            val end = ttmlTime(attr(attrs, "end"))
                ?: ttmlTime(attr(attrs, "dur"))?.let { begin + it }
                ?: (begin + 3_000)
            cues += Cue(begin, end, clean(m.groupValues[2]))
        }
        return cues
    }
}