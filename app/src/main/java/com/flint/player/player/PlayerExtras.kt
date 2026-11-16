// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.player

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flint.player.ui.TabularNumbers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs

// =====================================================================================
// A-B repeat icons ("A-B" while idle, "A - - -" once point A is set and B is awaited)
// =====================================================================================

internal object AbIcons {
    private fun build(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block
        ).build()

    private fun androidx.compose.ui.graphics.vector.PathBuilder.letterA() {
        moveTo(2f, 18f); lineTo(5.6f, 7f); lineTo(9.2f, 18f)
        moveTo(3.3f, 14.2f); lineTo(7.9f, 14.2f)
    }

    /** A-B */
    val Idle: ImageVector by lazy {
        build("AbRepeat") {
            letterA()
            moveTo(10.6f, 12.5f); lineTo(13.2f, 12.5f)
            // B
            moveTo(15f, 7f); lineTo(15f, 18f)
            moveTo(15f, 7f); lineTo(18f, 7f)
            arcTo(2.5f, 2.5f, 0f, false, true, 18f, 12f)
            lineTo(15f, 12f)
            moveTo(15f, 12f); lineTo(18.6f, 12f)
            arcTo(3f, 3f, 0f, false, true, 18.6f, 18f)
            lineTo(15f, 18f)
        }
    }

    /** A - - -  (waiting for point B) */
    val SetA: ImageVector by lazy {
        build("AbRepeatSetA") {
            letterA()
            moveTo(11.5f, 12.5f); lineTo(13f, 12.5f)
            moveTo(15.5f, 12.5f); lineTo(17f, 12.5f)
            moveTo(19.5f, 12.5f); lineTo(21f, 12.5f)
        }
    }
}

// =====================================================================================
// Zoom
// =====================================================================================

/** Pinch zoom of the video. Read only by the video view, so it never redraws the screen. */
@Stable
class ZoomState {
    var scale by mutableFloatStateOf(1f)
    var panX by mutableFloatStateOf(0f)
    var panY by mutableFloatStateOf(0f)

    val zoomed: Boolean get() = scale > 1.01f

    fun reset() {
        scale = 1f
        panX = 0f
        panY = 0f
    }

    /** A pinch step: keeps the point under your fingers where it is, and follows them as they move. */
    fun transform(centroid: Offset, size: IntSize, zoomChange: Float, panChange: Offset) {
        val old = scale
        val new = (old * zoomChange).coerceIn(MIN, MAX)
        val ratio = new / old
        val cx = centroid.x - size.width / 2f
        val cy = centroid.y - size.height / 2f
        scale = new
        panX = cx - ratio * (cx - panChange.x - panX)
        panY = cy - ratio * (cy - panChange.y - panY)
        clampPan(size.width.toFloat(), size.height.toFloat())
    }

    fun clampPan(width: Float, height: Float) {
        if (scale <= 1.001f) {
            panX = 0f
            panY = 0f
            return
        }
        val maxX = (scale - 1f) * width / 2f
        val maxY = (scale - 1f) * height / 2f
        panX = panX.coerceIn(-maxX, maxX)
        panY = panY.coerceIn(-maxY, maxY)
    }

    /** Pinch finished: a nearly-1x result snaps back to exactly 1x. */
    fun settle() {
        if (scale < 1.05f) reset()
    }

    companion object {
        const val MIN = 1f
        const val MAX = 5f
    }
}

fun zoomLabel(scale: Float): String = "${"%.1f".format(Locale.US, scale).trimEnd('0').trimEnd('.')}x"

/**
 * Two-finger pinch to zoom, two-finger drag to move around while zoomed. It looks at touches
 * first, but only takes over once a second finger is down, so one-finger swipes (seek, volume,
 * brightness) and taps keep working exactly as before.
 */
fun Modifier.pinchZoom(
    enabled: Boolean,
    zoom: ZoomState,
    onFinished: (Float) -> Unit
): Modifier = if (!enabled) this else pointerInput(zoom) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var zooming = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val down = event.changes.count { it.pressed }
            if (down >= 2) {
                zooming = true
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                if (zoomChange != 1f || panChange != Offset.Zero) {
                    zoom.transform(
                        event.calculateCentroid(useCurrent = true),
                        size, zoomChange, panChange
                    )
                }
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
        if (zooming) {
            zoom.settle()
            onFinished(zoom.scale)
        }
    }
}

// =====================================================================================
// A-B markers on the seekbar
// =====================================================================================

/** Draws the A and B points and the looped stretch between them over any seekbar style. */
@Composable
internal fun LoopMarkers(clock: PlaybackClock, padDp: Float, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val color = scheme.tertiary
    val measurer = rememberTextMeasurer()
    Canvas(modifier.fillMaxSize()) {
        val duration = clock.duration
        val a = clock.loopA
        val b = clock.loopB
        if (duration <= 0L || a < 0L) return@Canvas
        val pad = padDp.dp.toPx()
        val usable = (size.width - 2f * pad).coerceAtLeast(1f)
        fun xOf(ms: Long) = pad + (ms.toFloat() / duration).coerceIn(0f, 1f) * usable
        val cy = size.height / 2f

        if (b >= 0L) {
            val left = xOf(a)
            val right = xOf(b)
            drawRoundRect(
                color = color.copy(alpha = 0.55f),
                topLeft = Offset(left, cy - 5.dp.toPx()),
                size = Size((right - left).coerceAtLeast(2.dp.toPx()), 10.dp.toPx()),
                cornerRadius = CornerRadius(5.dp.toPx())
            )
        }

        fun marker(x: Float, label: String) {
            drawRoundRect(
                color = color,
                topLeft = Offset(x - 1.5.dp.toPx(), cy - 11.dp.toPx()),
                size = Size(3.dp.toPx(), 22.dp.toPx()),
                cornerRadius = CornerRadius(1.5.dp.toPx())
            )
            val text = measurer.measure(
                label,
                TextStyle(color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            )
            drawText(
                text,
                topLeft = Offset(
                    (x - text.size.width / 2f).coerceIn(0f, size.width - text.size.width),
                    (cy - 11.dp.toPx() - text.size.height).coerceAtLeast(0f)
                )
            )
        }
        marker(xOf(a), "A")
        if (b >= 0L) marker(xOf(b), "B")
    }
}

// =====================================================================================
// Screenshot
// =====================================================================================

internal sealed interface ShotResult {
    data class Saved(val uri: Uri) : ShotResult
    data class Failed(val reason: String) : ShotResult
}

/**
 * Copies the current video picture and saves it to Pictures/Flint. The picture is the clean
 * video frame: the player controls and subtitles are not part of it.
 */
internal object ScreenshotTaker {
    private fun findSurface(view: View): SurfaceView? {
        if (view is SurfaceView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findSurface(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    suspend fun capture(context: Context, videoView: View?, name: String, png: Boolean): ShotResult {
        val surface = videoView?.let { findSurface(it) }
            ?: return ShotResult.Failed("Nothing to capture yet")
        val width = surface.width
        val height = surface.height
        if (width <= 0 || height <= 0 || !surface.holder.surface.isValid) {
            return ShotResult.Failed("Couldn't capture this frame")
        }
        val bitmap = runCatching { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }
            .getOrNull() ?: return ShotResult.Failed("Not enough memory for a screenshot")

        val copied = suspendCancellableCoroutine<Int> { cont ->
            runCatching {
                PixelCopy.request(
                    surface, bitmap,
                    { result -> if (cont.isActive) cont.resume(result) },
                    Handler(Looper.getMainLooper())
                )
            }.onFailure { if (cont.isActive) cont.resume(-1) }
        }
        if (copied != PixelCopy.SUCCESS) {
            bitmap.recycle()
            return ShotResult.Failed("Couldn't capture this frame")
        }

        val uri = withContext(Dispatchers.IO) { runCatching { save(context, bitmap, name, png) }.getOrNull() }
        bitmap.recycle()
        return if (uri != null) ShotResult.Saved(uri) else ShotResult.Failed("Couldn't save the screenshot")
    }

    @Suppress("DEPRECATION")
    private fun save(context: Context, bitmap: Bitmap, name: String, png: Boolean): Uri? {
        val resolver = context.contentResolver
        val ext = if (png) "png" else "jpg"
        val fileName = "$name.$ext"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, if (png) "image/png" else "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Flint")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                val dir = java.io.File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Flint"
                ).apply { mkdirs() }
                put(MediaStore.Images.Media.DATA, java.io.File(dir, fileName).absolutePath)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            val written = resolver.openOutputStream(uri)?.use { out ->
                if (png) bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                else bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            } ?: false
            if (!written) {
                resolver.delete(uri, null, null)
                return null
            }
            if (Build.VERSION.SDK_INT >= 29) {
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            }
            uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    /** "Title_01-23_20260115_201530": easy to tell apart and sorted by time. */
    fun fileName(title: String, positionText: String): String {
        val clean = title.replace(Regex("[^\\p{L}\\p{N} _.-]"), "").trim().take(40).ifBlank { "Flint" }
            .replace(' ', '_')
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${clean}_${positionText.replace(':', '-')}_$stamp"
    }
}
