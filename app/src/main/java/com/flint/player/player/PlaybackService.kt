// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder

/** Keeps playback alive in the background and shows the notification controls. */
class PlaybackService : Service() {

    private var session: MediaSession? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        PlaybackBridge.service = this
        createChannel()

        val created = MediaSession(this, "Flint")
        created.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { PlaybackBridge.engine?.play() }
            override fun onPause() { PlaybackBridge.engine?.pause() }
            override fun onSkipToNext() { PlaybackBridge.engine?.next() }
            override fun onSkipToPrevious() { PlaybackBridge.engine?.previous() }
            override fun onSeekTo(pos: Long) { PlaybackBridge.engine?.seekTo(pos) }
        })
        created.isActive = true
        session = created

        goForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> PlaybackBridge.engine?.togglePlay()
            ACTION_NEXT -> PlaybackBridge.engine?.next()
            ACTION_PREVIOUS -> PlaybackBridge.engine?.previous()
        }
        goForeground()
        update()
        return START_NOT_STICKY
    }

    private fun goForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    fun update() {
        val engine = PlaybackBridge.engine
        val state = if (engine?.isPlaying == true) PlaybackState.STATE_PLAYING
        else PlaybackState.STATE_PAUSED

        session?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                            PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO
                )
                .setState(state, engine?.position() ?: 0L, engine?.speed ?: 1f)
                .build()
        )
        session?.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, engine?.title ?: "Flint")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, engine?.duration() ?: 0L)
                .build()
        )

        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    private fun action(name: String): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).setAction(name)
        return PendingIntent.getService(
            this, name.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(): Notification {
        val engine = PlaybackBridge.engine
        val playing = engine?.isPlaying == true
        val title = engine?.title?.takeIf { it.isNotBlank() } ?: "Flint"

        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID)
        else Notification.Builder(this)

        builder.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(if (playing) "Playing" else "Paused")
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Previous", action(ACTION_PREVIOUS))
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "Pause" else "Play",
                action(ACTION_TOGGLE)
            )
            .addAction(android.R.drawable.ic_media_next, "Next", action(ACTION_NEXT))

        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            builder.setContentIntent(
                PendingIntent.getActivity(
                    this, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }
        session?.let { current ->
            builder.setStyle(
                Notification.MediaStyle()
                    .setMediaSession(current.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
        }
        return builder.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    // Swiping the app away from recents stops everything
    override fun onTaskRemoved(rootIntent: Intent?) {
        PlaybackBridge.engine?.pause()
        stopSelf()
    }

    override fun onDestroy() {
        if (PlaybackBridge.service === this) PlaybackBridge.service = null
        runCatching { session?.release() }
        session = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL_ID = "playback"
        const val NOTIFICATION_ID = 7
        const val ACTION_TOGGLE = "com.flint.player.TOGGLE"
        const val ACTION_NEXT = "com.flint.player.NEXT"
        const val ACTION_PREVIOUS = "com.flint.player.PREVIOUS"
    }
}