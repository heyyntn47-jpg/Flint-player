# 📝 Changelog

All notable changes to Flint. Newest first.

## [Unreleased]

- Added `LICENSE` (GPL v3), README refresh with real screenshots, Gradle wrapper, PR template

## [1.0]

### The whole app stays still when the keyboard or system dialogs appear

App-wide: the root now consumes the keyboard inset, so no screen resizes or moves when it opens. The last places that read live bar insets now use stable ones that ignore whether a bar is showing (`stableBottomInsets()` for the Home bottom bar, `stableBottomAndSideInsets()` for the Stream menu, which used `safeDrawing` and so followed the keyboard). Top bars already used `stableTopInsets()`.

Player: 
The activity used to re-show the status bar every time the keyboard opened or closed, and that changed the window insets, so the player layout jumped. Now `PlayerSession.active` is true while a video is open: the activity leaves the system bars alone and the player owns them. The player root also consumes system bar and keyboard insets, so nothing inside it reacts to them. The manifest already had `adjustNothing`, so the keyboard does not resize the window.

### Player tools: A-B repeat, zoom, screenshot, auto-hide

All of these sit directly on the player in both looks. Nothing goes into the Player settings menu.

| Feature | Where it is | How it works |
| ------- | ----------- | ------------ |
| A-B repeat | Classic: left tool pill, bottom right (same row as the mode pill). Glass: bottom row, after Repeat. | Tap once to set A, again to set B (loop starts), a third time to clear. Both points and the looped stretch are drawn on every seekbar style (`LoopMarkers`). Cleared on a new video. |
| Zoom | Gesture only, no button. | Pinch with two fingers for any value up to 5x, drag with two fingers to move. Pinch back to 1x to reset; a new video resets it. One-finger swipes and taps are untouched. Applied as a view transform, so it works with Fit, Stretch and Crop. Switch off in Settings > Gestures. |
| Screenshot | Classic: left tool pill. Glass: bottom right. | `PixelCopy` of the video surface, saved to Pictures/Flint as JPEG or PNG (setting). Clean video frame: no controls, no subtitles. White flash and a "saved" message. Android 9 and older ask for storage permission. |
| Controls auto-hide | Settings > Playback | 2 s, 3.5 s (default), 5 s, 8 s, 12 s or Never. |

New settings: Playback > Controls auto-hide, Screenshot format. Gestures > Pinch to zoom.

New code is in `player/PlayerExtras.kt`. Classic bottom controls are a single pill on one row. The glass player keeps every control in three areas, each button on its own glass pill: top right (decoder, audio, subtitles, cast, mixer, queue), bottom left above the seekbar (speed, repeat, shuffle, A-B, sleep, background play) and bottom right (aspect, rotation, screenshot, lock, pop-out). The buttons shrink to fit the screen width. On a portrait phone the bottom tools stack in two rows of about equal length with bigger buttons; landscape keeps one row. Closing the picture-in-picture window with its X now stops playback.

### Glass interface style

New option in Settings > Appearance > **Interface style**: *Material* (default, solid surfaces) or *Glass* (translucent).

| Area | Change |
| ---- | ------ |
| `Prefs` | New `UI_STYLE` key (0 Material, 1 Glass) and `Prefs.glassUi`. Included in "Reset all settings". |
| `Theme.kt` | `ColorScheme.toGlass()` makes the `surfaceContainer*` tokens translucent and sets a light `outlineVariant`, so every card, bar, dialog, menu and chip follows with no per-screen changes. New helpers: `pageBackground()` (solid page plus soft theme-color glows in glass), `glassBorder()` and `glassStroke()` (thin light border, no-op in Material). `barColor()` is slightly more opaque in glass because lists scroll under the floating bottom bar. |
| `Touch.kt` | `pressItem` cards draw the glass border, following the corners as they swell on press. |
| Dialogs and menus | `ExpressiveDialog`, video context menu, sort menu, home popup, stream and network rows, player bottom sheets and the About card get the glass border. |
| `HomeScreen`, `SettingsScreen`, `MainActivity` | Paint `pageBackground()`. In glass, each Scaffold paints its own opaque backdrop so two screens never show through each other during navigation. |

The glass player controls (Player controls option) are unchanged and independent of this setting.

### Review fixes

## Bugs fixed

| Area | Change |
| ---- | ------ |
| Audio focus | `VlcEngine`: after `AUDIOFOCUS_LOSS` the cached "focus granted" flag is cleared, so the next `play()` asks for focus again. |
| ABIs | `build.gradle.kts`: added `arm64-v8a` next to `armeabi-v7a`. 64-bit-only phones can now install it, and Google Play accepts it. |
| Manifest | Removed the unused `androidx.media3` intent filter from `PlaybackService`. App name moved to `strings.xml`. `allowBackup` off, because backed-up history holds uris that are invalid after a restore. |
| Config changes | Added `uiMode`, `density`, `fontScale`, `layoutDirection`, `locale` to `configChanges`. Switching dark mode or font size during playback no longer restarts the player. |
| Equalizer | Native `Equalizer` objects are released after use (they leaked on every slider move). libVLC copies the settings. |
| Video from another app | `FlintNavHost` replaces the current player (`popUpTo(HOME)`), so opening a second video while one is playing switches to it. |
| Stale history | `UriAccess` keeps read access when the sender allows it, and history entries whose file can no longer be opened are removed with a message instead of a failed player. |
| Settings writes | `Prefs` uses `apply()` instead of `commit()`, so dragging the color picker no longer blocks the UI thread. |
| Subtitle import | The 10 MB limit is checked while reading, so a huge file can't fill memory first. |
| Video scan | `queryVideos` rebuilds the path from `RELATIVE_PATH` when the deprecated `DATA` column is empty (Android 10+), so folder filtering keeps working. |

## Organisation

- `PlayerPool` renamed to `LibVlcProvider` (it holds one instance, not a pool). The unused `preloadUri` and `preloadPlaylist` shims are gone; callers use `warmUp`.
- `PlaybackBridge` moved out of `PlaybackService.kt` into its own file.
- Shared types and limits moved out of `VlcEngine.kt` into `PlayerModels.kt`.
- New `util/Logger.kt`. Failures that were swallowed silently (libVLC option sets refused, foreground service refused, media that can't be opened) now log under the `Flint` tag.
- New `README.md`, `docs/`, `.editorconfig`, `.gitattributes`.

## CI and tests

- Workflow also runs on pull requests, cancels superseded runs, and runs unit tests before building.
- A release APK signed with the debug key is uploaded under an artifact name that says so.
- Added JVM unit tests for `SubtitleConverter` and the formatting helpers.

## Not changed

- Compose UI files (Settings, Home, SeekBar and similar) were left alone.
- Library versions were not touched, including `material3:1.5.0-rc01`. Check whether a stable release exists.
- `usesCleartextTraffic` stays on, since the app opens http streams.

### Release prep

- **Network sources:** new Network tab to save SMB, FTP and SFTP servers, browse their folders and play videos. Uses libVLC's own browser and access modules (`network/`). Logins are kept off saved urls and added only when libVLC opens the file. The Stream dialog and the manifest now also accept `rtsp`, `rtmp`, `mms`, `smb`, `ftp` and `sftp` links.
- **ABIs:** `armeabi-v7a`-only filter replaced by ABI splits (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64` + universal). The earlier note about adding arm64 did not match the build file; it does now.
- **CI:** builds one APK per architecture, names them `Flint-<version>-<abi>.apk`, adds `SHA256SUMS.txt`, and attaches signed builds to a GitHub Release on `v*` tags.
- **License:** SPDX headers on every source file, `NOTICE.md`, `CONTRIBUTING.md`.
- **Cleanup:** removed 8 unused imports, fixed the repo URL in the About card.
