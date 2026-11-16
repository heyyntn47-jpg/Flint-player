// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleConverterTest {

    @Test
    fun srt_roundTripsAllCues() {
        val input = "1\n00:00:01,000 --> 00:00:03,000\nHello\n\n" +
            "2\n00:00:04,000 --> 00:00:06,500\nWorld\n"
        val result = SubtitleConverter.convert(input, 0L)
        assertNotNull(result)
        assertEquals(2, result!!.cueCount)
        assertTrue(result.srt.contains("00:00:04,000 --> 00:00:06,500"))
        assertTrue(result.srt.contains("World"))
    }

    @Test
    fun webVtt_isConvertedToSrtTimings_andKeepsItalics() {
        val input = "WEBVTT\n\n00:00:01.500 --> 00:00:03.000\n<i>Hi</i>\n"
        val result = SubtitleConverter.convert(input, 0L)
        assertNotNull(result)
        assertEquals(1, result!!.cueCount)
        assertTrue(result.srt.contains("00:00:01,500 --> 00:00:03,000"))
        assertTrue(result.srt.contains("<i>Hi</i>"))
    }

    @Test
    fun cueWithNoDuration_getsTwoSeconds() {
        val input = "1\n00:00:05,000 --> 00:00:05,000\nBlink\n"
        val result = SubtitleConverter.convert(input, 0L)
        assertNotNull(result)
        assertTrue(result!!.srt.contains("00:00:05,000 --> 00:00:07,000"))
    }

    @Test
    fun windowsLineEndings_areHandled() {
        val input = "1\r\n00:00:01,000 --> 00:00:02,000\r\nHello\r\n"
        assertEquals(1, SubtitleConverter.convert(input, 0L)?.cueCount)
    }

    @Test
    fun plainText_isNotASubtitle() {
        assertNull(SubtitleConverter.convert("just some text", 0L))
    }
}
