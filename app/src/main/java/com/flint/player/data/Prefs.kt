// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.flint.player.library.SortField
import com.flint.player.library.ViewMode

/** Every saved switch and option. The Settings screen edits these. */
object Prefs {
    const val BACKGROUND_PLAY = "background_play"
    const val AUTO_PIP = "auto_pip"
    const val THEME_MODE = "theme_mode"
    const val DYNAMIC_COLOR = "dynamic_color"
    const val PURE_BLACK = "pure_black"
    const val UI_STYLE = "ui_style"
    const val HIDE_STATUS_BAR = "hide_status_bar"
    const val HAPTICS = "haptics"
    const val BULGE = "bulge"
    const val HIDE_NAV = "hide_nav_on_scroll"
    const val SORT_FIELD = "sort_field"
    const val SORT_ASCENDING = "sort_ascending"
    const val VIEW_MODE = "view_mode"
    const val PREWARM = "prewarm_thumbnails"
    const val SAVE_HISTORY = "save_history"
    const val RESUME = "resume_playback"
    const val DEFAULT_REPEAT = "default_repeat"
    const val DEFAULT_SPEED = "default_speed"
    const val DEFAULT_ASPECT = "default_aspect"
    const val ORIENTATION_BY_VIDEO = "orientation_by_video"
    const val KEEP_SCREEN_ON = "keep_screen_on"
    const val DOUBLE_TAP_SECONDS = "double_tap_seconds"
    const val GESTURE_SEEK = "gesture_seek"
    const val GESTURE_BRIGHTNESS = "gesture_brightness"
    const val GESTURE_VOLUME = "gesture_volume"
    const val GESTURE_DOUBLE_TAP = "gesture_double_tap"
    const val PAUSE_ON_UNPLUG = "pause_on_unplug"
    const val DECODER = "decoder_mode"
    const val SUBTITLE_SIZE = "subtitle_size"
    const val SUBTITLE_STYLE = "subtitle_style"
    const val SUBTITLE_COLOR = "subtitle_color"
    const val CUSTOM_COLOR = "custom_color"
    const val SEEKBAR_STYLE = "seekbar_style"
    const val PLAYER_UI = "player_ui"
    const val GESTURE_SLIDER_STYLE = "gesture_slider_style"
    const val CONTROLS_HIDE = "controls_hide_delay"
    const val PINCH_ZOOM = "pinch_zoom"
    const val SCREENSHOT_FORMAT = "screenshot_format"
    const val FOLDER_MODE = "folder_mode"
    private const val KEY_INCLUDED = "included_folders"
    private const val KEY_EXCLUDED = "excluded_folders"

    private val defaults: Map<String, Any> = mapOf(
        BACKGROUND_PLAY to true,
        AUTO_PIP to true,
        THEME_MODE to 0,            // 0 system, 1 light, 2 dark
        DYNAMIC_COLOR to true,
        PURE_BLACK to false,
        UI_STYLE to 0,              // 0 material (default, solid), 1 glass (translucent)
        HIDE_STATUS_BAR to true,
        HAPTICS to true,
        BULGE to true,
        HIDE_NAV to true,
        SORT_FIELD to 0,
        SORT_ASCENDING to true,
        VIEW_MODE to 0,             // 0 list, 1 grid
        PREWARM to true,
        SAVE_HISTORY to true,
        RESUME to true,
        DEFAULT_REPEAT to 0,        // 0 off, 1 one, 2 all
        DEFAULT_SPEED to 1f,
        DEFAULT_ASPECT to 0,        // 0 fit, 1 stretch, 2 crop
        ORIENTATION_BY_VIDEO to true,
        KEEP_SCREEN_ON to true,
        DOUBLE_TAP_SECONDS to 10,
        GESTURE_SEEK to true,
        GESTURE_BRIGHTNESS to true,
        GESTURE_VOLUME to true,
        GESTURE_DOUBLE_TAP to true,
        PAUSE_ON_UNPLUG to true,
        DECODER to 0,               // decoding acceleration: 0 automatic, 1 disable (software), 2 full (hardware)
        SUBTITLE_SIZE to 1,         // 0 small, 1 medium, 2 large, 3 huge
        SUBTITLE_STYLE to 0,        // 0 outline, 1 shadow, 2 box
        CUSTOM_COLOR to 0xFF9A4DAE.toInt(),
        PLAYER_UI to 0,             // 0 classic (default), 1 glass
        SEEKBAR_STYLE to 0,         // 0 current (default), 1 wavy, 2 thin, 3 chunky
        GESTURE_SLIDER_STYLE to 0,  // 0 pill (default), 1 lines, 2 waves, 3 circle
        CONTROLS_HIDE to 1,         // index into controlsHideOptions: 2s, 3.5s (default), 5s, 8s, 12s, never
        PINCH_ZOOM to true,
        SCREENSHOT_FORMAT to 0,     // 0 jpeg, 1 png
        FOLDER_MODE to 0,           // 0 all folders, 1 only chosen folders, 2 all except chosen folders
        SUBTITLE_COLOR to 0         // 0 white, 1 yellow
    )

    /** Choices for "Controls auto-hide", in milliseconds. -1 = never hide. */
    val controlsHideOptionsMs = longArrayOf(2_000L, 3_500L, 5_000L, 8_000L, 12_000L, -1L)
    val controlsHideLabels = listOf("2 s", "3.5 s", "5 s", "8 s", "12 s", "Never")

    private var prefs: SharedPreferences? = null
    private val values = mutableStateMapOf<String, Any>()

    // Folder lists are kept separately per mode, so switching modes doesn't lose either list
    private val included = mutableStateOf<Set<String>>(emptySet())
    private val excluded = mutableStateOf<Set<String>>(emptySet())

    var notificationAsked = false
        private set

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("flint_settings", Context.MODE_PRIVATE)
        prefs = p
        for ((key, default) in defaults) {
            if (!p.contains(key)) continue
            values[key] = when (default) {
                is Boolean -> p.getBoolean(key, default)
                is Int -> p.getInt(key, default)
                is Float -> p.getFloat(key, default)
                else -> continue
            }
        }
        notificationAsked = p.getBoolean("notification_asked", false)
        included.value = p.getStringSet(KEY_INCLUDED, null)?.toSet() ?: emptySet()
        excluded.value = p.getStringSet(KEY_EXCLUDED, null)?.toSet() ?: emptySet()
    }

    /** Saves the chosen folders of one mode: the "only scan these" list or the "skip these" list. */
    fun setFolders(onlyScanList: Boolean, folders: Set<String>) {
        if (onlyScanList) included.value = folders else excluded.value = folders
        prefs?.edit()
            ?.putStringSet(if (onlyScanList) KEY_INCLUDED else KEY_EXCLUDED, folders)
            ?.apply()
    }

    private fun raw(key: String): Any = values[key] ?: defaults.getValue(key)
    private fun bool(key: String): Boolean = raw(key) as Boolean
    private fun int(key: String): Int = raw(key) as Int
    private fun float(key: String): Float = raw(key) as Float

    fun set(key: String, value: Any) {
        values[key] = value
        val editor = prefs?.edit()
        editor?.put(key, value)

        // Picking a custom color only shows when wallpaper colors are off, so switch them off together
        if (key == CUSTOM_COLOR && bool(DYNAMIC_COLOR)) {
            values[DYNAMIC_COLOR] = false
            editor?.put(DYNAMIC_COLOR, false)
        }
        // apply() updates memory at once and writes to disk in the background. Android finishes
        // pending writes when the app stops, whereas a blocking write on every change would stall
        // the UI thread (for example while dragging the color picker).
        editor?.apply()
    }

    private fun SharedPreferences.Editor.put(key: String, value: Any) {
        when (value) {
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is Float -> putFloat(key, value)
        }
    }

    fun resetAll() {
        values.clear()
        included.value = emptySet()
        excluded.value = emptySet()
        val editor = prefs?.edit() ?: return
        defaults.keys.forEach { editor.remove(it) }
        editor.remove(KEY_INCLUDED)
        editor.remove(KEY_EXCLUDED)
        editor.apply()
    }

    fun markNotificationAsked() {
        notificationAsked = true
        prefs?.edit()?.putBoolean("notification_asked", true)?.apply()
    }

    // Kept for the player's quick toggle
    fun updateBackgroundPlay(value: Boolean) = set(BACKGROUND_PLAY, value)
    fun updateAutoPip(value: Boolean) = set(AUTO_PIP, value)

    // ---------- Readers ----------

    val backgroundPlay: Boolean get() = bool(BACKGROUND_PLAY)
    val autoPip: Boolean get() = bool(AUTO_PIP)
    val themeMode: Int get() = int(THEME_MODE)
    val dynamicColor: Boolean get() = bool(DYNAMIC_COLOR)
    val pureBlack: Boolean get() = bool(PURE_BLACK)
    val uiStyle: Int get() = int(UI_STYLE)
    /** True when the whole app uses the translucent glass look instead of solid Material surfaces. */
    val glassUi: Boolean get() = uiStyle == 1
    val hideStatusBar: Boolean get() = bool(HIDE_STATUS_BAR)
    val hapticsEnabled: Boolean get() = bool(HAPTICS)
    val bulgeEffect: Boolean get() = bool(BULGE)
    val hideNavOnScroll: Boolean get() = bool(HIDE_NAV)
    val sortField: SortField get() = SortField.entries.getOrElse(int(SORT_FIELD)) { SortField.NAME }
    val sortAscending: Boolean get() = bool(SORT_ASCENDING)
    val viewMode: ViewMode get() = ViewMode.entries.getOrElse(int(VIEW_MODE)) { ViewMode.LIST }
    val prewarmThumbnails: Boolean get() = bool(PREWARM)
    val saveHistory: Boolean get() = bool(SAVE_HISTORY)
    val resumePlayback: Boolean get() = bool(RESUME)
    val defaultRepeat: Int get() = int(DEFAULT_REPEAT)
    val defaultSpeed: Float get() = float(DEFAULT_SPEED)
    val defaultAspect: Int get() = int(DEFAULT_ASPECT)
    val orientationByVideo: Boolean get() = bool(ORIENTATION_BY_VIDEO)
    val keepScreenOn: Boolean get() = bool(KEEP_SCREEN_ON)
    val doubleTapSeconds: Int get() = int(DOUBLE_TAP_SECONDS)
    val gestureSeek: Boolean get() = bool(GESTURE_SEEK)
    val gestureBrightness: Boolean get() = bool(GESTURE_BRIGHTNESS)
    val gestureVolume: Boolean get() = bool(GESTURE_VOLUME)
    val gestureDoubleTap: Boolean get() = bool(GESTURE_DOUBLE_TAP)
    val pauseOnUnplug: Boolean get() = bool(PAUSE_ON_UNPLUG)
    val decoderMode: Int get() = int(DECODER)
    val subtitleSize: Int get() = int(SUBTITLE_SIZE)
    val subtitleStyle: Int get() = int(SUBTITLE_STYLE)
    val subtitleColor: Int get() = int(SUBTITLE_COLOR)
    val customColor: Int get() = int(CUSTOM_COLOR)
    val seekBarStyle: Int get() = int(SEEKBAR_STYLE)
    val playerUi: Int get() = int(PLAYER_UI)
    val gestureSliderStyle: Int get() = int(GESTURE_SLIDER_STYLE)
    val controlsHideIndex: Int get() = int(CONTROLS_HIDE).coerceIn(0, controlsHideOptionsMs.lastIndex)
    /** How long the player controls stay up before fading out, or -1 to keep them until tapped away. */
    val controlsHideMs: Long get() = controlsHideOptionsMs[controlsHideIndex]
    val pinchZoom: Boolean get() = bool(PINCH_ZOOM)
    val screenshotFormat: Int get() = int(SCREENSHOT_FORMAT)
    val screenshotPng: Boolean get() = screenshotFormat == 1
    val folderMode: Int get() = int(FOLDER_MODE)
    val includedFolders: Set<String> get() = included.value
    val excludedFolders: Set<String> get() = excluded.value
}

/** Where to resume a video, or 0 if "resume" is switched off. */
fun resumeFor(uri: String): Long =
    if (Prefs.resumePlayback) FlintData.resumePosition(uri) else 0L