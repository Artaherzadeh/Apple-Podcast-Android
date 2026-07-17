package com.alireza.podcasts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.MediaMetadataCompat
import androidx.media.session.MediaButtonReceiver
import java.net.URL
import kotlin.concurrent.thread

class MediaPlaybackService : Service() {

    companion object {
        const val CHANNEL_ID = "media_playback_channel"
        const val NOTIFICATION_ID = 101

        const val ACTION_PLAY = "com.alireza.podcasts.action.PLAY"
        const val ACTION_PAUSE = "com.alireza.podcasts.action.PAUSE"
        const val ACTION_UPDATE_STATE = "com.alireza.podcasts.action.UPDATE_STATE"

        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_IMAGE_URL = "extra_image_url"
        const val EXTRA_DURATION = "extra_duration"
        const val EXTRA_POSITION = "extra_position"

        // Broadcast actions from service to Activity
        const val BROADCAST_PLAY = "com.alireza.podcasts.broadcast.PLAY"
        const val BROADCAST_PAUSE = "com.alireza.podcasts.broadcast.PAUSE"
        const val BROADCAST_SEEK = "com.alireza.podcasts.broadcast.SEEK"
        const val EXTRA_SEEK_POS = "extra_seek_pos"
    }

    private lateinit var mediaSession: MediaSessionCompat
    private var isPlaying = false
    private var currentTitle = "Apple Podcasts"
    private var currentArtist = "Playing"
    private var currentImageUrl = ""
    private var duration = 0L
    private var position = 0L
    private var albumArtBitmap: Bitmap? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        if (Intent.ACTION_MEDIA_BUTTON == intent.action) {
            MediaButtonReceiver.handleIntent(mediaSession, intent)
            return START_STICKY
        }

        when (intent.action) {
            ACTION_UPDATE_STATE -> {
                isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
                currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: currentTitle
                currentArtist = intent.getStringExtra(EXTRA_ARTIST) ?: currentArtist
                val newImageUrl = intent.getStringExtra(EXTRA_IMAGE_URL) ?: ""
                duration = intent.getLongExtra(EXTRA_DURATION, 0L)
                position = intent.getLongExtra(EXTRA_POSITION, 0L)

                updatePlaybackState()
                updateMetadata()

                if (newImageUrl.isNotEmpty() && newImageUrl != currentImageUrl) {
                    currentImageUrl = newImageUrl
                    loadAlbumArt(currentImageUrl)
                } else {
                    updateNotification()
                }

                // If playing, promote service to foreground
                if (isPlaying) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(NOTIFICATION_ID, buildNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                    } else {
                        startForeground(NOTIFICATION_ID, buildNotification())
                    }
                } else {
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                    updateNotification()
                }
            }
            ACTION_PLAY -> {
                isPlaying = true
                updatePlaybackState()
                sendBroadcast(Intent(BROADCAST_PLAY))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, buildNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(NOTIFICATION_ID, buildNotification())
                }
            }
            ACTION_PAUSE -> {
                isPlaying = false
                updatePlaybackState()
                sendBroadcast(Intent(BROADCAST_PAUSE))
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                updateNotification()
            }
        }
        return START_STICKY
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "ApplePodcastsSession").apply {
            isActive = true
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            
            val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON, null, this@MediaPlaybackService, MediaButtonReceiver::class.java)
            val mediaButtonPendingIntent = PendingIntent.getBroadcast(
                this@MediaPlaybackService, 0, mediaButtonIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            setMediaButtonReceiver(mediaButtonPendingIntent)

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    isPlaying = true
                    updatePlaybackState()
                    sendBroadcast(Intent(BROADCAST_PLAY))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(NOTIFICATION_ID, buildNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                    } else {
                        startForeground(NOTIFICATION_ID, buildNotification())
                    }
                }

                override fun onPause() {
                    isPlaying = false
                    updatePlaybackState()
                    sendBroadcast(Intent(BROADCAST_PAUSE))
                    ServiceCompat.stopForeground(this@MediaPlaybackService, ServiceCompat.STOP_FOREGROUND_DETACH)
                    updateNotification()
                }

                override fun onSeekTo(pos: Long) {
                    position = pos
                    updatePlaybackState()
                    val seekIntent = Intent(BROADCAST_SEEK).apply {
                        putExtra(EXTRA_SEEK_POS, pos)
                    }
                    sendBroadcast(seekIntent)
                }
            })
        }
    }

    private fun updatePlaybackState() {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(state, position, if (isPlaying) 1.0f else 0.0f)
        mediaSession.setPlaybackState(stateBuilder.build())
    }

    private fun updateMetadata() {
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
        
        albumArtBitmap?.let {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
        }
        
        mediaSession.setMetadata(metadataBuilder.build())
    }

    private fun loadAlbumArt(urlStr: String) {
        thread {
            try {
                val url = URL(urlStr)
                val connection = url.openConnection()
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                val input = connection.getInputStream()
                val bitmap = BitmapFactory.decodeStream(input)
                
                albumArtBitmap = bitmap
                updateMetadata()
                updateNotification()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingOpenIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Actions
        val playPauseIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        }
        val pendingPlayPauseIntent = PendingIntent.getService(
            this, 1, playPauseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseText = if (isPlaying) "Pause" else "Play"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setContentIntent(pendingOpenIntent)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        albumArtBitmap?.let {
            builder.setLargeIcon(it)
        }

        // Add play/pause button for older API fallback (Android 12-)
        builder.addAction(
            NotificationCompat.Action.Builder(playPauseIcon, playPauseText, pendingPlayPauseIntent).build()
        )

        // Set MediaStyle
        val mediaStyle = MediaStyle()
            .setMediaSession(mediaSession.sessionToken)
            .setShowActionsInCompactView(0)
        
        builder.setStyle(mediaStyle)

        return builder.build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Podcast Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows lockscreen controls for Apple Podcasts"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Completely stop the background service when the user swipe-closes the app
        stopSelf()
    }

    override fun onDestroy() {
        mediaSession.isActive = false
        mediaSession.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }
}
