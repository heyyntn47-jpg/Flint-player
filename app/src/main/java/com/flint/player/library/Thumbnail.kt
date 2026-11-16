// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.library

import com.flint.player.player.LibVlcProvider
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.Executors

object ThumbnailLoader {
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8 / 1024).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private fun pool(name: String, threads: Int, priority: Int) =
        Executors.newFixedThreadPool(threads) { task ->
            Thread(task, name).apply { this.priority = priority }
        }.asCoroutineDispatcher()

    private val diskDispatcher = pool("thumb-disk", 2, Thread.NORM_PRIORITY)      // quick file reads
    private val decodeDispatcher = pool("thumb-decode", 2, Thread.MIN_PRIORITY)   // heavy video decoding
    private val prewarmDispatcher = pool("thumb-prewarm", 1, Thread.MIN_PRIORITY) // background catch-up

    @Volatile
    private var thumbDir: File? = null

    fun cached(uri: Uri): Bitmap? = cache.get(uri.toString())

    private fun isLocal(uri: Uri) = uri.scheme == "content" || uri.scheme == "file"

    /** Fast path: memory or the disk cache only. Never decodes a video. */
    suspend fun loadCached(context: Context, uri: Uri): Bitmap? {
        val key = uri.toString()
        cache.get(key)?.let { return it }
        if (!isLocal(uri)) return null
        return withContext(diskDispatcher) {
            cache.get(key) ?: readDisk(diskFile(context.applicationContext, uri))
                ?.also { cache.put(key, it) }
        }
    }

    /** Slow path: decode a frame from the video and save it to disk. */
    suspend fun generate(context: Context, uri: Uri): Bitmap? {
        val key = uri.toString()
        cache.get(key)?.let { return it }
        if (!isLocal(uri)) return null
        // Right after install libVLC is setting itself up; don't compete with it for the CPU
        LibVlcProvider.awaitReady(8_000)
        return withContext(decodeDispatcher) {
            cache.get(key) ?: run {
                val app = context.applicationContext
                val file = diskFile(app, uri)
                val bitmap = readDisk(file) ?: decode(app, uri)?.also { writeDisk(file, it) }
                if (bitmap != null) cache.put(key, bitmap)
                bitmap
            }
        }
    }

    /** Quietly generates missing thumbnails in the background, so scrolling finds them ready. */
    suspend fun prewarm(context: Context, uris: List<Uri>) {
        LibVlcProvider.awaitReady(15_000)
        withContext(prewarmDispatcher) {
            val app = context.applicationContext
            for (uri in uris) {
                ensureActive()
                if (!isLocal(uri)) continue
                if (cache.get(uri.toString()) != null) continue
                val file = diskFile(app, uri)
                if (file.exists()) continue
                decode(app, uri)?.let { writeDisk(file, it) }
                delay(20)
            }
        }
    }

    private fun diskFile(context: Context, uri: Uri): File {
        val dir = thumbDir ?: File(context.cacheDir, "thumbs").also {
            it.mkdirs()
            thumbDir = it
        }
        val name = MessageDigest.getInstance("SHA-1")
            .digest(uri.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$name.jpg")
    }

    private fun readDisk(file: File): Bitmap? {
        if (!file.exists()) return null
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return runCatching { BitmapFactory.decodeFile(file.path, options) }.getOrNull()
    }

    private fun writeDisk(file: File, bitmap: Bitmap) {
        runCatching {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
        }
    }

    private fun decode(context: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val thumb = runCatching {
                context.contentResolver.loadThumbnail(uri, Size(320, 180), null)
            }.getOrNull()
            if (thumb != null) return thumb
        } else {
            val thumb = runCatching { systemThumbnail(context, uri) }.getOrNull()
            if (thumb != null) return thumb
        }
        return runCatching { frameFromRetriever(context, uri) }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun systemThumbnail(context: Context, uri: Uri): Bitmap? {
        if (uri.authority != MediaStore.AUTHORITY) return null
        val id = runCatching { ContentUris.parseId(uri) }.getOrNull() ?: return null
        return MediaStore.Video.Thumbnails.getThumbnail(
            context.contentResolver, id, MediaStore.Video.Thumbnails.MINI_KIND, null
        )
    }

    private fun frameFromRetriever(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
            val width = 320
            val height = (frame.height * width / frame.width).coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(frame, width, height, true)
            if (scaled !== frame) frame.recycle()
            return scaled
        } finally {
            runCatching { retriever.release() }
        }
    }
}

@Composable
fun Thumbnail(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(ThumbnailLoader.cached(uri), uri) {
        value = ThumbnailLoader.cached(uri)
            ?: ThumbnailLoader.loadCached(context, uri)   // instant for anything already saved
            ?: run {
                delay(150)                                 // new ones wait; cancelled if scrolled away
                ThumbnailLoader.generate(context, uri)
            }
    }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        val loaded = bitmap
        if (loaded != null) {
            val image = remember(loaded) { loaded.asImageBitmap() }
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}