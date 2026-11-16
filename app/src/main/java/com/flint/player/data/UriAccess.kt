// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.flint.player.util.Logger

/**
 * Helpers for videos opened from other apps. A `content://` uri handed over by a file manager
 * normally works only for the lifetime of that task, so history entries pointing at it can go
 * stale. These helpers keep access when the sender allows it, and detect it when they can't.
 */
object UriAccess {

    /** Keeps read access across restarts, if the sending app offered a persistable grant. */
    fun keepReadAccess(context: Context, intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "content") return
        if ((intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) == 0) return
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }.onFailure { Logger.warn("UriAccess", "Could not keep access to $uri", it) }
    }

    /** False when a `content://` video can no longer be opened (file deleted, grant gone). */
    fun isReadable(context: Context, uri: Uri): Boolean {
        if (uri.scheme != "content") return true
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
    }
}
