// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.flint.player.data.Prefs
import com.flint.player.data.UriAccess
import com.flint.player.navigation.FlintNavHost
import com.flint.player.player.PlayerSession
import com.flint.player.player.PipController
import com.flint.player.ui.FlintTheme
import com.flint.player.ui.LocalOverlayHost
import com.flint.player.ui.OverlayHost
import com.flint.player.ui.pageBackground
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private var incomingUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Let the app draw into the camera-notch area, so hiding the status bar leaves no black strip
        if (Build.VERSION.SDK_INT >= 28) {
            val attrs = window.attributes
            attrs.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = attrs
        }

        incomingUri = intent?.data
        UriAccess.keepReadAccess(this, intent)

        setContent {
            FlintTheme {
                val overlayHost = remember { OverlayHost() }

                // Some phones show the status bar while the keyboard opens or closes: apply the setting again
                // Read through derivedStateOf so only the open/closed flip recomposes, not every frame
                val ime = WindowInsets.ime
                val density = LocalDensity.current
                val imeVisible by remember { derivedStateOf { ime.getBottom(density) > 0 } }
                LaunchedEffect(imeVisible) {
                    if (!PlayerSession.active) {
                        applyStatusBar()
                        delay(120)
                        applyStatusBar()
                        delay(400)
                        applyStatusBar()
                    }
                }

                // Glass: the root is transparent and draws the soft color backdrop that the
                // translucent bars and cards show through
                Surface(
                    modifier = Modifier.fillMaxSize().pageBackground(),
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    CompositionLocalProvider(LocalOverlayHost provides overlayHost) {
                        // The keyboard never resizes or moves anything in the app
                        Box(Modifier.fillMaxSize().consumeWindowInsets(WindowInsets.ime)) {
                            // Keeps content clear of a notch on the side in landscape
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .windowInsetsPadding(
                                        WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                                    )
                            ) {
                                FlintNavHost(
                                    activity = this@MainActivity,
                                    incomingUri = incomingUri,
                                    onIncomingHandled = { incomingUri = null }
                                )
                            }

                            // Dialogs and menus are drawn here, on top of everything
                            overlayHost.Render()
                        }
                    }
                }
            }
        }

        applyStatusBar()
    }

    /** Hides or shows the status bar, following the setting. A swipe from the top peeks at it. */
    fun applyStatusBar() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (Prefs.hideStatusBar) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    override fun onResume() {
        super.onResume()
        if (!PlayerSession.active) applyStatusBar()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyStatusBar()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { incomingUri = it }
        UriAccess.keepReadAccess(this, intent)
    }

    // Pressing Home while a video plays shrinks it into a floating window
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        PipController.onUserLeaveHint(this)
    }
}