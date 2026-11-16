# Flint audit

> **Status after this release:** the ABI list, About URL, unused imports, SPDX headers, `NOTICE.md` and CI are fixed (see `CHANGELOG.md`). The remaining performance findings (2 to 5) are not applied, because I couldn't test them on a device.

Source: `MediaPlayer.zip` (133 files, 63 Kotlin files). This was a **read-only static review**: I didn't build the app, run it on a device, or change any file. Nothing here is measured. Items marked *verify* need a profiler or a device to confirm.

Not legal advice. For anything that matters, ask a lawyer or the FSF/SFC.

---

## Changes needed for GPL v3

### What's missing right now

| Item | State | Action |
|---|---|---|
| `LICENSE` (GPL v3 text) | Added | Done. |
| SPDX headers | None of the original `.kt` files had one | **Done in this release.** Every `.kt` and `.kts` file now has one. |
| `NOTICE` / third-party notices | Missing | Add (below). |
| Wrong repo URL in app | `AboutCard.kt:45` points to `github.com/heyyntn47/MediaPlayer` | Change to `https://github.com/heyyntn47-jpg/Flint-player` and delete the `TODO` above it. |
| License shown in app | About card lists libraries, not Flint's own license | Add a "GPL v3" line and a link to the source repo. |

### SPDX header

Put this at the very top of every `.kt` file, before `package`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47
```

Choose deliberately: `GPL-3.0-only` locks to v3; `GPL-3.0-or-later` (what the README text matches) lets users move to later GPL versions. Use the same one in `LICENSE` notices, the README and the About card. For `.kts`, `.xml` and `.yml` use the same text in that file's comment syntax (`<!-- -->`, `#`). Generic one-liner for Kotlin files (review the diff before committing):

```bash
find app/src -name '*.kt' -exec sed -i '1i // SPDX-License-Identifier: GPL-3.0-or-later\n// Copyright (C) 2026 heyyntn47\n' {} +
```

Tools like `reuse lint` (REUSE spec) can verify coverage.

### libVLC and GPL v3: how the licenses fit

- libVLC is licensed **LGPL 2.1 or later**, not GPL v3.
- LGPL 2.1 allows combining with other code and distributing the combined work under **GPL v3** (the "or later" wording and LGPL's section 3 conversion option make this possible). So Flint as a whole is GPL v3, and libVLC inside it **stays LGPL**. You don't relicense libVLC.
- Keep libVLC's own license text and copyright notices in your repo and shipped notices.
- The part that **doesn't** work: you can't take GPL-v3-only code *into* libVLC, and you can't claim libVLC itself is GPL v3.
- **Verify the binary you link.** `org.videolan.android:libvlc-all` is published for LGPL use, but check its POM and VideoLAN's page for the exact version (3.7.6 here). VideoLAN's *VLC for Android app* code is GPL v2+; you are not linking that, just the library.

### What a NOTICE file should contain

Put `NOTICE.md` in the repo root and ship the same content in the app (About screen or a licenses page):

1. Flint name, copyright line, SPDX id and a link to `LICENSE`
2. Each third-party component: name, version, license, URL
   - libVLC 3.7.6, LGPL 2.1+, videolan.org
   - Compose, Material 3, Activity, Navigation, ProfileInstaller: Apache 2.0
   - Material Kolor: MIT
   - Inter, Poppins: SIL OFL 1.1 (you already have `licenses/FONTS.md`)
3. The "not affiliated with VideoLAN; VLC is a trademark of VideoLAN" statement
4. A pointer to where the complete corresponding source is

Apache 2.0 notices are also required to be kept with distributions; MIT requires keeping the copyright and permission text. A link in the About screen is usually accepted for Apache; including the MIT text itself is safer.

### Do you have to publish source for released binaries?

**Yes.** Under GPL v3, whenever you distribute an APK, recipients must be able to get the **complete corresponding source** for that exact release, including build scripts.

- Tag every release (`v1.0`) and make sure the tag builds the APK you uploaded.
- Link the source tag from the release notes and the About screen.
- Don't commit your keystore. GPL v3 has an "installation information" rule aimed at locked-down consumer devices; it doesn't require you to publish your private signing key for normal Android apps, but you must not stop users from building and installing their own copy.
- Google Play: allowed, but the Play listing should link the source and license. Be aware the Play terms can conflict with some GPL redistribution restrictions; many GPL apps ship there anyway.

### Other gotchas

- **Never link GPL-v2-only code.** GPL v2-only and GPL v3 are incompatible. You currently have none; keep it that way when adding dependencies.
- **Contributors:** with GPL v3 and many contributors, relicensing later is hard. Consider a DCO (`Signed-off-by`) rule in `CONTRIBUTING.md`.
- **Fonts:** OFL fonts can be bundled with GPL software; fonts must not be sold on their own. You comply with the OFL by shipping `licenses/FONTS.md`. Don't rename modified fonts using the reserved name.
- **Trademarks:** don't use the VLC cone or "VLC" in your name or icon.
- **Material Icons Extended** is Apache 2.0, fine.

---

## Source cleanup table

Nothing below was removed. Verify with the Kotlin compiler warnings or Android Studio ("Optimize imports", "Unused declaration") before deleting; my scan was text-based.

### Unused imports

| File | Import | Reason |
|---|---|---|
| `history/HistoryTab.kt` | `androidx.compose.ui.text.style.TextOverflow` | Never referenced |
| `library/VideoContextMenu.kt` | `TextOverflow` | Never referenced |
| `library/VideosTab.kt` | `TextOverflow` | Never referenced |
| `player/PlayerControls.kt` | `TextOverflow` | Never referenced |
| `player/PlayerControls.kt` | `androidx.compose.material3.Text` | Never referenced |
| `player/QueueSheet.kt` | `TextOverflow` | Never referenced |
| `player/SeekBar.kt` | `androidx.compose.ui.graphics.drawscope.Stroke` | Never referenced |
| `settings/SettingsScreen.kt` | `androidx.compose.animation.core.LinearEasing` | Never referenced |

Note: my scan also flagged `getValue`/`setValue` imports in about a dozen files. Those are **false positives**: they're needed by `by` delegates. Leave them.

### Everything else

| Category | Result |
|---|---|
| Unused dependencies | None found. All 9 `implementation` entries are used (Compose UI/foundation/M3, icons, activity, navigation, Material Kolor, libVLC, ProfileInstaller). `material-icons-extended` is large but R8 strips unused icons in release. |
| Unused drawables | None. There are no drawable resources apart from launcher mipmaps and fonts. |
| Unused strings | `strings.xml` has one entry (`app_name`), used. But **148 UI literals are hard-coded in Kotlin** (your own README notes this). Move to `strings.xml` for translation. |
| Commented-out code | None found. |
| Debug logs | None. `Logger` only calls `Log.w`, used for real failures. Fine. |
| `@Preview` | None. |
| TODO / FIXME | One, in `AboutCard.kt:44` (repo URL). |
| Duplicate utilities | None spotted in the files I read. Several large UI files (`SettingsScreen` 42 KB, `FlintPlayerScreen` 38 KB, `HomeScreen` 27 KB, `SeekBar` 24 KB) are good candidates for splitting, and duplicates may hide there. I didn't read them end to end. |
| Deprecated APIs | `AudioManager.requestAudioFocus(listener, STREAM_MUSIC, …)` and `abandonAudioFocus` (already `@Suppress`ed; migrate to `AudioFocusRequest`). `MediaStore.Video.Thumbnails.getThumbnail` (only used below API 29). `Notification.Builder` fallback below API 26 (fine). |
| Docs inconsistency | `CHANGELOG.md` says `arm64-v8a` was added to the ABI list. `app/build.gradle.kts` says **`armeabi-v7a` only**. See the first performance finding. |
| Baseline profile | `baseline-prof.txt` uses wildcards that match every class (`Lcom/flint/player/**;`, `Lorg/videolan/libvlc/**;`). That's a "compile everything" rule, not a real profile. It can increase install time and APK size without targeting startup. Generate one with Macrobenchmark instead. |
| Unnecessary recompositions | None found in the player path (see below). |

---

## Performance findings

### 🔴 Critical

**1. 32-bit only build: `app/build.gradle.kts:41`**
`ndk { abiFilters += listOf("armeabi-v7a") }`

- *Modern phones:* a flagship runs the 32-bit libVLC in compatibility mode: slower NEON/codec paths, higher CPU use, more heat. Many 2023+ phones have **dropped 32-bit support entirely**, so they can't install the APK at all.
- *Old phones:* fine, if 32-bit.
- *Play Store:* requires 64-bit.
- **Fix:**
  ```kotlin
  ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
  ```
  For GitHub releases, also consider ABI splits so each user downloads one ~20 MB build:
  ```kotlin
  splits { abi { isEnable = true; reset(); include("armeabi-v7a", "arm64-v8a"); isUniversalApk = true } }
  ```
- This is the most likely single cause if the app feels slower than VLC on your phone. **Check which ABI your test phone runs** (`adb shell getprop ro.product.cpu.abi`).

### 🟠 Moderate

**2. History is serialized on the main thread every 5 seconds: `FlintPlayerScreen.kt` (progress loop) → `FlintData.kt:112 saveHistory()`**

- Every tick rebuilds a JSON array of the entire history and calls `putString(...).apply()`. `apply()` is async on disk, but the JSON build is on the main thread, and cost grows with `MAX_HISTORY`. Also runs on every pause/stop.
- *Modern:* a small hitch, risks a missed frame on 120 Hz when the history is large. *Old:* more visible.
- **Fix:** save only the current entry's position (separate key), and write the full list on stop/exit or from `Dispatchers.IO`.

**3. No wake lock for background audio: `PlaybackService.kt`**

- The foreground service keeps the process alive, but the CPU can still sleep with the screen off, especially for network streams. libVLC may take its own lock internally; I couldn't confirm that from the code.
- **Test:** play with the screen off for 15+ minutes. If it stutters, take a `PARTIAL_WAKE_LOCK` only while playing in the background and release it on pause/stop.

**4. `setAspect()` is called on every recomposition: `FlintPlayerScreen.kt` `AndroidView(update = …)`**

- `update` runs whenever any state it reads changes. Each run makes a JNI call (`setVideoScale`).
- **Fix:** `LaunchedEffect(aspect) { engine.setAspect(aspect) }`, and set `keepScreenOn` the same way.

**5. History/queue helpers polled every 250 ms with the controls shown: `FlintPlayerScreen.kt` (position loop)**

- `engine.hasPrevious()` and `hasNext()` call `order.indexOf(currentIndex)` (O(n)) every tick. Negligible for small queues, wasteful for playlists of hundreds.
- **Fix:** compute them when `currentIndex`, `order` or repeat mode change, not on a timer.

### 🟢 Minor

**6. `--android-display-chroma=RV16` on low-RAM devices: `LibVlcProvider.kt`**
16-bit rendering saves memory but causes visible banding. Only applies when `isLowRamDevice` is true. Consider dropping it unless you measured a gain.

**7. Redundant option: `--audio-time-stretch`** is libVLC's default. Harmless.

**8. `LibVlcProvider.reset()` can silently do nothing**
If an engine is alive (`engines > 0`), settings like subtitle style don't apply until the next time the instance is rebuilt. Not a performance bug, but surprising. Tell the user "applies to the next video", or rebuild on release.

**9. Thumbnail prewarm may overlap playback: `VideoLibrary.kt:140`**
`prewarm` runs at minimum priority and is tied to the library screen's `LaunchedEffect`, so it should cancel when that screen leaves composition. *Verify* with a profiler that "thumb-prewarm" threads stop after you open a video. It's cheap to guard with a flag set while the player is open.

**10. Audio focus uses the deprecated stream API without `AudioAttributes`.**
Works, but on Android 8+ use `AudioFocusRequest` with `USAGE_MOVIE`/`CONTENT_TYPE_MOVIE` so the system routes and ducks correctly.

**11. `usesCleartextTraffic="true"`** is a security setting, not performance. Fine for http streams; scope it with a network security config if you can.

### Checked and fine

| Check | Result |
|---|---|
| **SurfaceView vs TextureView** | ✅ `attachViews(layout, null, true, false)` → `useTextureView = false`, so SurfaceView |
| **HW decode forced to software?** | ✅ Default is `setHWDecoderEnabled(true, false)` (auto). Software only if the user picks it, or after a real hardware failure |
| **Software fallback** | ✅ One retry from the same position, no loop |
| **Compose state on every frame** | ✅ None. Position updates every **250 ms** and **only while controls are visible**, held in an isolated `PlaybackClock`; the seek bar animates with `Animatable`/`snapshotFlow` |
| **Per-frame allocations** | ✅ None found in app code. Allocation happens per event or per 250 ms tick |
| **Logging in playback path** | ✅ Only `Logger.warn` on failure; libVLC verbosity not raised |
| **Release / detach** | ✅ `release()` unregisters the receiver, stops cast scans, abandons focus, clears the listener, `detachViews()`, then `stop()` + `release()` on a worker thread, then closes the file descriptor and decrements the engine count |
| **Background video** | ✅ `enterBackground()` turns the video track off and detaches the surface, so no video decoding in the background |
| **Main-thread IO** | ✅ MediaStore scan, cache and thumbnails run on `Dispatchers.IO`/dedicated pools. `Prefs` uses `apply()` |
| **libVLC events** | ✅ Re-posted to the main thread (avoids a known deadlock) |
| **Leaks** | ✅ `PlaybackBridge.engine/service` are cleared on detach and `onDestroy`. I found no static reference to the player that survives `release()` |
| **libVLC buffers** | ✅ `file-caching` 1000 ms (1500 low-end), `network-caching` 2000 ms (3000 low-end): reasonable |

I did not find frame-pacing or `Choreographer` settings to change. Frame timing is handled inside libVLC and the compositor.

---

## Is my playback actually degrading?

### What the code says

From what I read, **I did not find an app-side cause that gets worse with repeated plays.** Each play creates a `VlcEngine` and `release()` frees it. There's one shared `LibVLC` instance, which is intended. Nothing in the code caches decoded video or grows with the number of plays, apart from the history list (capped) and the thumbnail cache (small JPEGs).

So, ranked by likelihood for "slow after ~100 tests of the same video":

1. **Device state (most likely):** thermal throttling after long testing, battery saver, a full RAM/background-app load, or storage cache pressure. These make a phone feel slower and recover after a rest or reboot.
2. **Perception:** you've seen that exact clip 100 times. You notice every tiny hitch, and you're comparing against what you remember rather than a measurement.
3. **ABI mismatch (see finding 1):** a constant slowness that was always there, not a degradation. Still worth fixing.
4. **A real leak:** possible, but I didn't find one. Test it, don't assume it.

### Checklist to tell the app from your eyes

**A. Reproduce under controlled conditions**
- [ ] Reboot the phone. Let it cool for 10 minutes. Charger unplugged or plugged in the same way each time.
- [ ] Play the video once, note how it feels. Compare with VLC on the same file, same minute.
- [ ] Do the same on a second device, if you have one.

**B. Memory leak test (Android Studio Profiler → Memory)**
- [ ] Open the video, close it, repeat **20 times**.
- [ ] After each close, press "Force GC". Heap should return to about the same baseline.
- [ ] Look for **rising baseline**, or growing counts of `VlcEngine`, `MediaPlayer`, `VLCVideoLayout`. More than 1 live `VlcEngine` while no video is open = leak.
- [ ] Capture a heap dump after the 20th close and search `VlcEngine`.

**C. Native memory and threads**
```bash
adb shell dumpsys meminfo com.flint.player         # watch "Native Heap", "Graphics" after each cycle
adb shell ls /proc/$(adb shell pidof com.flint.player)/task | wc -l   # thread count
adb shell ls /proc/$(adb shell pidof com.flint.player)/fd | wc -l     # open file descriptors
```
Thread count or fd count that climbs by a fixed amount each cycle points to unreleased players or file descriptors.

**D. Dropped frames while playing**
```bash
adb shell dumpsys gfxinfo com.flint.player reset
# ...play 60 seconds...
adb shell dumpsys gfxinfo com.flint.player
```
Check **Janky frames %** and the 90th/95th percentile frame times. Compare run 1 with run 100: equal numbers mean the app isn't degrading. (Video frames are drawn by the surface/compositor, so also look at SurfaceFlinger below.)

**E. Perfetto (best tool for "why is it janky")**
- [ ] Record with `sched`, `freq`, `gfx`, `view`, `power`, `thermal` categories (the Android Studio *System Trace* works too).
- [ ] Look at the `SurfaceView` / SurfaceFlinger rows for missed deadlines, the **CPU frequency** track (dropping frequency over time = thermal throttling), and which threads are busy (decoder, `vlc-*`, `thumb-*`).
- [ ] Compare a trace from a cool phone against one from a hot phone.

**F. Media pipeline**
```bash
adb shell dumpsys media.codec          # active hardware codec instances: should drop to 0 after closing the video
adb shell dumpsys media.player         # leftover players
adb shell dumpsys thermalservice       # throttling status
adb shell dumpsys batterystats | head
```
Active codec instances that stay after you leave the player = a decoder not released.

**G. libVLC's own view**
```bash
adb logcat -s VLC LibVLC Flint
```
Look for "falling back to software", repeated errors, or "late picture skipped" lines. Note that the "Hardware decoder failed, trying software" toast in the app tells you immediately if it fell back.

**H. Verdict**

| You see | Meaning |
|---|---|
| Heap, native heap, threads, fds all return to baseline; gfxinfo equal at run 1 and run 100; slowness gone after cooldown or reboot | **Thermal / device / perception.** Not the app |
| Baseline keeps rising, extra `VlcEngine` instances, codec instances stay alive | **Real leak in the app.** Send me the heap dump summary |
| Constant jank from the first run, equal in all runs | **Not degradation.** Likely the 32-bit build (finding 1) or software decode |

---

## Files I'd still like to see if you want a deeper pass

- `home/HomeScreen.kt`, `player/SeekBar.kt`, `player/PlayerSheets.kt`, `settings/SettingsScreen.kt`: only skimmed, and the biggest places for unstable lambdas and duplicated code.
- `data/Prefs.kt` and `ui/Theme.kt`: how often `Prefs` getters are read during composition.
- A `adb shell dumpsys gfxinfo` output or Perfetto trace from a slow run, which will beat any static reading.
