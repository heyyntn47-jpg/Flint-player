// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi
import com.flint.player.data.Prefs
import kotlin.math.roundToInt

object PipController {
    private var owner: Any? = null
    var playing = false

    private var videoWidth = 16
    private var videoHeight = 9

    fun isSupported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    fun arm(token: Any) {
        owner = token
    }

    fun disarm(token: Any) {
        if (owner === token) {
            owner = null
            playing = false
        }
    }

    fun setVideoSize(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            videoWidth = width
            videoHeight = height
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildParams(): PictureInPictureParams {
        // Android only accepts shapes between roughly 1:2.39 and 2.39:1
        val ratio = (videoWidth.toFloat() / videoHeight).coerceIn(0.42f, 2.38f)
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational((ratio * 1000).roundToInt(), 1000))
            .build()
    }

    fun enter(activity: Activity): Boolean {
        if (!isSupported(activity)) return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activity.enterPictureInPictureMode(buildParams())
            } else {
                false
            }
        }.getOrDefault(false)
    }

    fun onUserLeaveHint(activity: Activity) {
        if (owner != null && playing && Prefs.autoPip) enter(activity)
    }
}