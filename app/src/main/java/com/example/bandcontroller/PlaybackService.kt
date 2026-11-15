package com.example.bandcontroller

import android.app.*
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.os.SystemClock
import android.view.KeyEvent
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver

class PlaybackService : Service() {
    companion object {
        const val START = "com.example.bandcontroller.START"
        const val STOP = "com.example.bandcontroller.STOP"
        private const val CHANNEL = "playback"
    }
    private lateinit var session: MediaSessionCompat
    private var player: MediaPlayer? = null
    private lateinit var audio: AudioManager
    private lateinit var focus: AudioFocusRequest
    private var hasFocus = false
    private var foreground = false
    private var playing = false
    private var audible = false
    private var gameMode = false
    private var macroMode = false
    private val interactiveMode get() = gameMode || macroMode
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Test media session", NotificationManager.IMPORTANCE_LOW))
        audio = getSystemService(AudioManager::class.java)
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                Diagnostics.record(this, "Audio focus change: $change")
                if (change < 0) {
                    hasFocus = false; setPlayback(false)
                    if (interactiveMode) {
                        BandMediaController.unavailable("Audio focus lost · media controls stopped")
                        stopSelf()
                    }
                }
            }.build()
        session = MediaSessionCompat(this, "BandController")
        session.setMetadata(MediaMetadataCompat.Builder().putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Band Controller test loop").putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Local diagnostic audio").build())
        session.setCallback(object : MediaSessionCompat.Callback() {
            override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                @Suppress("DEPRECATION")
                val key = mediaButtonEvent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return super.onMediaButtonEvent(mediaButtonEvent)
                Diagnostics.record(this@PlaybackService, "Media key code=${key.keyCode} action=${key.action} repeat=${key.repeatCount}")
                val supported = key.keyCode in listOf(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_STOP)
                if (!supported) return super.onMediaButtonEvent(mediaButtonEvent)
                if (key.action == KeyEvent.ACTION_DOWN && key.repeatCount == 0) {
                    when (key.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_NEXT -> onSkipToNext()
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> onSkipToPrevious()
                        KeyEvent.KEYCODE_MEDIA_PLAY -> onPlay()
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> onPause()
                        KeyEvent.KEYCODE_MEDIA_STOP -> onStop()
                        else -> { event("TOGGLE"); setPlayback(if (interactiveMode) true else !playing) }
                    }
                }
                return true
            }
            override fun onPlay() { event("PLAY"); setPlayback(true) }
            override fun onPause() { event("PAUSE"); setPlayback(interactiveMode) }
            override fun onSkipToNext() { event("NEXT"); player?.seekTo(0) }
            override fun onSkipToPrevious() { event("PREVIOUS"); player?.seekTo(0) }
            override fun onStop() { Diagnostics.record(this@PlaybackService, "Session stop command"); stopSelf() }
        }, Handler(Looper.getMainLooper()))
        player = MediaPlayer.create(this, com.example.bandcontroller.R.raw.probe)?.apply {
            isLooping = true
            setOnErrorListener { _, what, extra ->
                Diagnostics.record(this@PlaybackService, "Playback error: what=$what extra=$extra")
                if (interactiveMode) BandMediaController.unavailable("Playback failed · touch controls available")
                stopSelf()
                true
            }
        }
        Diagnostics.record(this, "Service created; local media loaded=${player != null}")
    }
    private fun event(name: String) {
        Diagnostics.record(this, "Session callback: $name (source not identifiable; game=$gameMode; macro=$macroMode)", name)
        if (interactiveMode) BandMediaController.dispatch(name)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopSelf(); return START_NOT_STICKY }
        // Do not let a remembered receiver restart an ended game or macro session.
        // An explicit diagnostic START below restores the original diagnostic behavior.
        val preferences = getSharedPreferences("diagnostics", MODE_PRIVATE)
        if (intent?.action != START && !foreground && preferences.getBoolean("gameSession", false)) {
            Diagnostics.record(this, "Ignored stale interactive media intent; explicit enable required")
            stopSelf(); return START_NOT_STICKY
        }
        // Foreground first, including before requesting audio focus on Android 15.
        if (!foreground) { startForeground(1, notification()); foreground = true }
        session.isActive = true
        if (intent?.action == START) {
            macroMode = intent.getBooleanExtra("macro", false)
            gameMode = !macroMode && intent.getBooleanExtra("game", false)
            preferences.edit().putBoolean("gameSession", interactiveMode).apply()
            audible = intent.getBooleanExtra("audible", false)
            session.setMetadata(MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, when { macroMode -> "Macro · Previous / Play / Next"; gameMode -> "Neon Orbit · Play/Pause fires"; else -> "Band Controller test loop" })
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Local media · connection not detected").build())
            Diagnostics.record(this, "UI start; audible=$audible (not counted as a remote event)")
            setPlayback(true)
        } else {
            Diagnostics.record(this, "Service intent: ${intent?.action}")
            MediaButtonReceiver.handleIntent(session, intent)
        }
        return START_NOT_STICKY
    }
    private fun setPlayback(value: Boolean) {
        if (value && !hasFocus) {
            val result = audio.requestAudioFocus(focus)
            hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            Diagnostics.record(this, "Audio focus request: $result")
            if (!hasFocus) {
                publishState(false)
                if (interactiveMode) { BandMediaController.unavailable("Audio focus denied · touch mode available"); stopSelf() }
                return
            }
        }
        if (value && player == null) {
            Diagnostics.record(this, "ERROR: local player unavailable"); publishState(false)
            if (interactiveMode) { BandMediaController.unavailable("Local playback unavailable · media controls stopped"); stopSelf() }
            return
        }
        player?.setVolume(if (audible) 1f else 0f, if (audible) 1f else 0f)
        if (value) player?.start() else player?.pause()
        publishState(value)
    }
    private fun publishState(value: Boolean) {
        playing = value
        session.setPlaybackState(PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_STOP)
            .setState(if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED, player?.currentPosition?.toLong() ?: 0L, if (playing) 1f else 0f, SystemClock.elapsedRealtime()).build())
        getSharedPreferences("diagnostics", MODE_PRIVATE).edit().putString("state", if (playing) "playing" else "paused").apply()
        Diagnostics.record(this, "Playback state: ${if (playing) "playing" else "paused"}")
        if (foreground) getSystemService(NotificationManager::class.java).notify(1, notification())
    }
    private fun notification(): Notification {
        val destination = when { macroMode -> MacroActivity::class.java; gameMode -> GameActivity::class.java; else -> MainActivity::class.java }
        val openIntent = Intent(this, destination).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Band Controller").setContentText(when { macroMode -> "Macro controls · draft only"; gameMode -> "Neon Orbit · Play/Pause fires"; playing -> "Test loop playing"; else -> "Test loop paused" })
            .setContentIntent(open).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, if (gameMode) "Left" else "Previous", MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS))
            .addAction(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (macroMode) "Macro input" else if (gameMode) "Fire" else if (playing) "Pause" else "Play", MediaButtonReceiver.buildMediaButtonPendingIntent(this, if (playing) PlaybackStateCompat.ACTION_PAUSE else PlaybackStateCompat.ACTION_PLAY))
            .addAction(android.R.drawable.ic_media_next, if (gameMode) "Right" else "Next", MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT))
            .setStyle(MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0,1,2)).build()
    }
    override fun onTaskRemoved(rootIntent: Intent?) { Diagnostics.record(this, "Task removed; service may be restricted by phone power policy"); super.onTaskRemoved(rootIntent) }
    override fun onDestroy() {
        if (interactiveMode) BandMediaController.unavailable("Media session stopped · enable again if needed")
        player?.release(); player = null
        audio.abandonAudioFocusRequest(focus)
        session.isActive = false; session.release()
        getSharedPreferences("diagnostics", MODE_PRIVATE).edit().putString("state", "stopped").apply()
        Diagnostics.record(this, "Service destroyed")
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
