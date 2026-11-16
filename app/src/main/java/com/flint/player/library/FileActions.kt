// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.library

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.io.File

class FileActions(
    val delete: (VideoItem) -> Unit,
    val rename: (VideoItem, String) -> Unit,
    val share: (VideoItem) -> Unit
)

val LocalFileActions = compositionLocalOf<FileActions?> { null }

private sealed class PendingOp(val video: VideoItem) {
    class Delete(video: VideoItem) : PendingOp(video)
    class Rename(video: VideoItem, val newName: String) : PendingOp(video)
}

private class FileOps(private val context: Context) {
    var onChanged: () -> Unit = {}
    var launchSender: (IntentSenderRequest) -> Unit = {}
    var requestPermission: () -> Unit = {}
    private var pending: PendingOp? = null

    private fun toast(text: String) =
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun share(video: VideoItem) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, video.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Share video")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }
            .onFailure { toast("Can't share this video") }
    }

    fun start(op: PendingOp) {
        when {
            Build.VERSION.SDK_INT >= 30 -> {
                val resolver = context.contentResolver
                val request = when (op) {
                    is PendingOp.Delete -> MediaStore.createDeleteRequest(resolver, listOf(op.video.uri))
                    is PendingOp.Rename -> MediaStore.createWriteRequest(resolver, listOf(op.video.uri))
                }
                pending = op
                launchSender(IntentSenderRequest.Builder(request).build())
            }
            Build.VERSION.SDK_INT >= 29 -> execute(op)
            else -> {
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    execute(op)
                } else {
                    pending = op
                    requestPermission()
                }
            }
        }
    }

    fun onSenderResult(ok: Boolean) {
        val op = pending
        pending = null
        if (!ok || op == null) return
        if (Build.VERSION.SDK_INT >= 30 && op is PendingOp.Delete) {
            toast("Deleted")
            onChanged()
        } else {
            execute(op, retried = true)
        }
    }

    fun onPermissionResult(granted: Boolean) {
        val op = pending
        pending = null
        if (granted && op != null) execute(op) else toast("Permission needed")
    }

    private fun execute(op: PendingOp, retried: Boolean = false) {
        try {
            when (op) {
                is PendingOp.Delete -> deleteNow(op.video)
                is PendingOp.Rename -> renameNow(op.video, op.newName)
            }
            onChanged()
        } catch (e: SecurityException) {
            if (!retried && Build.VERSION.SDK_INT >= 29 && e is RecoverableSecurityException) {
                pending = op
                launchSender(
                    IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()
                )
            } else {
                toast("Permission denied")
            }
        } catch (e: Exception) {
            toast(e.message ?: "Operation failed")
        }
    }

    private fun deleteNow(video: VideoItem) {
        val rows = context.contentResolver.delete(video.uri, null, null)
        if (rows <= 0) throw IllegalStateException("Could not delete")
        toast("Deleted")
    }

    private fun renameNow(video: VideoItem, requested: String) {
        val clean = requested.trim().replace('/', '_')
        require(clean.isNotEmpty()) { "Name can't be empty" }
        val ext = video.name.substringAfterLast('.', "")
        val finalName =
            if (ext.isNotEmpty() && !clean.endsWith(".$ext", ignoreCase = true)) "$clean.$ext"
            else clean
        if (finalName == video.name) return

        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
            }
            val rows = context.contentResolver.update(video.uri, values, null, null)
            if (rows <= 0) throw IllegalStateException("Rename failed")
        } else {
            val source = File(video.path)
            val target = File(source.parentFile, finalName)
            if (target.exists()) throw IllegalStateException("A file with that name already exists")
            if (!source.renameTo(target)) throw IllegalStateException("Rename failed")
            MediaScannerConnection.scanFile(context, arrayOf(source.path, target.path), null) { _, _ ->
                Handler(Looper.getMainLooper()).post { onChanged() }
            }
        }
        toast("Renamed")
    }
}

@Composable
fun rememberFileActions(onChanged: () -> Unit): FileActions {
    val context = LocalContext.current
    val ops = remember { FileOps(context.applicationContext) }
    ops.onChanged = onChanged

    val senderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> ops.onSenderResult(result.resultCode == Activity.RESULT_OK) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> ops.onPermissionResult(granted) }

    ops.launchSender = { request -> senderLauncher.launch(request) }
    ops.requestPermission = {
        permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    return remember(ops) {
        FileActions(
            delete = { video -> ops.start(PendingOp.Delete(video)) },
            rename = { video, name -> ops.start(PendingOp.Rename(video, name)) },
            share = { video -> ops.share(video) }
        )
    }
}