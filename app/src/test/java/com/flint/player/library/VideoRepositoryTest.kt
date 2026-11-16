// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.library

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class VideoRepositoryTest {

    @Before
    fun fixLocale() {
        // The formatters use the device language; pin it so results don't depend on the CI machine
        Locale.setDefault(Locale.US)
    }

    @Test
    fun folderOf_returnsParentDirectory() {
        assertEquals(
            "/storage/emulated/0/Movies",
            folderOf("/storage/emulated/0/Movies/holiday.mp4")
        )
    }

    @Test
    fun folderOf_withoutSlash_isEmpty() {
        assertEquals("", folderOf("holiday.mp4"))
    }

    @Test
    fun formatDuration_underAnHour() {
        assertEquals("1:05", formatDuration(65_000))
    }

    @Test
    fun formatDuration_overAnHour() {
        assertEquals("1:02:05", formatDuration(3_725_000))
    }

    @Test
    fun formatSize_picksReadableUnit() {
        assertEquals("1.5 GB", formatSize(1_500_000_000))
        assertEquals("250 MB", formatSize(250_000_000))
        assertEquals("500 KB", formatSize(500_000))
    }
}
