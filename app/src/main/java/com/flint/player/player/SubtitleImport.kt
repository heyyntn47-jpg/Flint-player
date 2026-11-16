// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.player

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

/** Reads any subtitle file, converts it, and saves a clean UTF-8 .srt the player can load. */
object SubtitleImport {

    class Imported(val uri: Uri, val label: String, val mime: String, val cueCount: Int)

    sealed interface Outcome {
        class Ok(val imported: Imported) : Outcome
        class Fail(val reason: String) : Outcome
    }

    /** Call from a background thread. */
    fun import(context: Context, picked: Uri, fileName: String, videoDurationMs: Long): Outcome {
        // Read at most one byte past the limit, so a huge file can't fill memory before the check
        val bytes = runCatching {
            context.contentResolver.openInputStream(picked)?.use { readUpTo(it, MAX_BYTES + 1) }
        }.getOrNull() ?: return Outcome.Fail("Couldn't open that file")

        if (bytes.isEmpty()) return Outcome.Fail("That file is empty")
        if (bytes.size > MAX_BYTES) return Outcome.Fail("That file is too big for a subtitle")

        val text = decode(bytes).removePrefix("\uFEFF")
        val converted = SubtitleConverter.convert(text, videoDurationMs)
            ?: return Outcome.Fail("No subtitle lines found in that file")

        val dir = File(context.cacheDir, "subs").apply { mkdirs() }
        cleanOld(dir)
        val file = File(dir, "${System.currentTimeMillis()}.srt")
        val saved = runCatching { file.writeText(converted.srt, Charsets.UTF_8) }.isSuccess
        if (!saved) return Outcome.Fail("Couldn't save the subtitle")

        return Outcome.Ok(
            Imported(Uri.fromFile(file), fileName, "application/x-subrip", converted.cueCount)
        )
    }

    private const val MAX_BYTES = 10_000_000

    private fun readUpTo(input: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (total < limit) {
            val read = input.read(buffer, 0, minOf(buffer.size, limit - total))
            if (read < 0) break
            out.write(buffer, 0, read)
            total += read
        }
        return out.toByteArray()
    }

    private fun cleanOld(dir: File) {
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { file ->
            if (now - file.lastModified() > 24 * 60 * 60 * 1000L) file.delete()
        }
    }

    private fun decode(bytes: ByteArray): String {
        // Byte order marks
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)

        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 0xFF && b1 == 0xFE) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            if (b0 == 0xFE && b1 == 0xFF) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }

        // UTF-16 without a byte order mark: lots of zero bytes
        val sample = minOf(bytes.size, 400)
        var evenZeros = 0
        var oddZeros = 0
        for (i in 0 until sample) {
            if (bytes[i].toInt() == 0) {
                if (i % 2 == 0) evenZeros++ else oddZeros++
            }
        }
        if (evenZeros + oddZeros > sample / 4) {
            return String(bytes, if (oddZeros >= evenZeros) Charsets.UTF_16LE else Charsets.UTF_16BE)
        }

        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            String(bytes, fallbackCharset())
        }
    }

    // Old subtitle files are often in a legacy code page that matches the user's language
    private fun fallbackCharset(): Charset {
        val name = when (Locale.getDefault().language) {
            "ar", "fa", "ur" -> "windows-1256"
            "ru", "uk", "bg", "sr", "be", "mk" -> "windows-1251"
            "tr" -> "windows-1254"
            "he" -> "windows-1255"
            "el" -> "windows-1253"
            "zh" -> "GBK"
            "ja" -> "Shift_JIS"
            "ko" -> "EUC-KR"
            "th" -> "windows-874"
            "vi" -> "windows-1258"
            "pl", "cs", "sk", "hu", "ro", "hr", "sl" -> "windows-1250"
            else -> "windows-1252"
        }
        return runCatching { Charset.forName(name) }.getOrDefault(Charsets.ISO_8859_1)
    }
}