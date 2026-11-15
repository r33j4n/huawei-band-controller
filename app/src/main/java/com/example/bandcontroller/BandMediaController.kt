package com.example.bandcontroller

import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** In-process bridge, always delivered on the UI thread. This detects commands, never a band connection. */
object BandMediaController {
    private val main = Handler(Looper.getMainLooper())
    private var owner: Any? = null
    private var commandListener: ((String) -> Unit)? = null
    private var unavailableListener: ((String) -> Unit)? = null
    var lastCommand = "None received"
        private set
    var playPauseCount = 0
        private set
    var nextCount = 0
        private set
    var previousCount = 0
        private set
    fun attach(owner: Any, onCommand: (String) -> Unit, onUnavailable: (String) -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper())
        this.owner = owner; commandListener = onCommand; unavailableListener = onUnavailable
    }
    fun detach(owner: Any) {
        if (this.owner === owner) { this.owner = null; commandListener = null; unavailableListener = null }
    }
    fun dispatch(command: String) {
        val recipient = owner
        main.post {
            val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            lastCommand = "$command · $stamp"
            when (command) {
                "PLAY", "PAUSE", "TOGGLE" -> playPauseCount++
                "NEXT" -> nextCount++
                "PREVIOUS" -> previousCount++
            }
            if (recipient != null && owner === recipient) commandListener?.invoke(command)
        }
    }
    fun unavailable(reason: String) {
        val recipient = owner
        main.post { if (recipient != null && owner === recipient) unavailableListener?.invoke(reason) }
    }
}
