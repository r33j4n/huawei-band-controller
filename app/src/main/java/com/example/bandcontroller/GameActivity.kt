package com.example.bandcontroller

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.*
import android.view.*
import android.widget.*
import com.example.bandcontroller.game.*

/** Native accessible controls surrounding a lightweight Canvas; no hardware connection is assumed. */
class GameActivity : Activity() {
    private lateinit var engine: GameEngine
    private lateinit var game: GameView
    private lateinit var diagnostics: TextView
    private lateinit var primary: Button
    private lateinit var bandButton: Button
    private lateinit var fireButton: Button
    private lateinit var leftButton: Button
    private lateinit var rightButton: Button
    private lateinit var audible: CheckBox
    private lateinit var effects: CheckBox
    private val handler = Handler(Looper.getMainLooper())
    private var bandEnabled = false
    private var notificationAsked = false
    private var leftHeld = false
    private var rightHeld = false
    private var notice = "Touch ready · band mode off"
    private val refresh = object : Runnable {
        override fun run() { renderPanel(); handler.postDelayed(this, 200) }
    }
    private val cyan = Color.rgb(77, 241, 255)
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val retained = lastNonConfigurationInstance as? GameEngine
        notificationAsked = savedInstanceState?.getBoolean("notificationAsked") ?: false
        engine = retained ?: GameEngine(getSharedPreferences("game", MODE_PRIVATE).getInt("highScore", 0))
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(5,10,24)); setPadding(dp(12),dp(4),dp(12),dp(8)) }
        setContentView(root)
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val b = insets.getInsets(WindowInsets.Type.systemBars()); v.setPadding(b.left + dp(12), b.top + dp(4), b.right + dp(12), b.bottom + dp(8))
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(insets.systemWindowInsetLeft + dp(12), insets.systemWindowInsetTop + dp(4), insets.systemWindowInsetRight + dp(12), insets.systemWindowInsetBottom + dp(8))
            }
            insets
        }
        root.requestApplyInsets()
        val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(TextView(this).apply { text = "NEON ORBIT  /  BAND 6"; textSize = 15f; setTextColor(cyan); typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0, dp(48), 1f))
        heading.addView(button("Exit") { finish() }, LinearLayout.LayoutParams(dp(64), dp(48)))
        root.addView(heading)
        game = GameView(this, engine).apply { onHighScore = { saveHighScore() } }
        root.addView(game, LinearLayout.LayoutParams(-1, 0, 1f))
        val sessionRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        bandButton = button("Enable band mode") { if (bandEnabled) disableBand() else enableBand() }
        sessionRow.addView(bandButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        primary = button("Launch") {
            when (engine.state) {
                GameState.TITLE, GameState.GAME_OVER -> engine.start()
                GameState.RUNNING -> engine.pause()
                GameState.PAUSED -> engine.resume()
            }
            renderPanel()
        }
        sessionRow.addView(primary, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(sessionRow)
        val options = LinearLayout(this)
        audible = CheckBox(this).apply {
            text = "Audible loop"; buttonTintList = ColorStateList.valueOf(cyan); textSize = 12f; setTextColor(Color.LTGRAY)
            setOnCheckedChangeListener { _, _ -> if (bandEnabled) { disableBand(); notice = "Loop option changed · enable band mode again" } }
        }
        effects = CheckBox(this).apply { text = "Sound FX"; buttonTintList = ColorStateList.valueOf(cyan); isChecked = true; textSize = 12f; setTextColor(Color.LTGRAY); setOnCheckedChangeListener { _, value -> game.soundEnabled = value } }
        options.addView(audible, LinearLayout.LayoutParams(0, dp(48), 1f)); options.addView(effects, LinearLayout.LayoutParams(0, dp(48), 1f)); root.addView(options)
        diagnostics = TextView(this).apply { textSize = 10f; setTextColor(Color.rgb(158,183,210)); typeface = Typeface.MONOSPACE; setPadding(dp(4),dp(4),dp(4),dp(4)) }
        root.addView(diagnostics)
        val controls = LinearLayout(this)
        leftButton = holdButton("◀", { leftHeld = it; updateDirection() }, { engine.moveBy(-1) }).apply { contentDescription = "Move left; hold for continuous movement" }
        fireButton = holdButton("FIRE", { engine.fireHeld = it; if (it) engine.fire() }, { engine.fire() })
        rightButton = holdButton("▶", { rightHeld = it; updateDirection() }, { engine.moveBy(1) }).apply { contentDescription = "Move right; hold for continuous movement" }
        listOf(leftButton, fireButton, rightButton).forEach { controls.addView(it, LinearLayout.LayoutParams(0, dp(60), 1f).apply { setMargins(dp(3), dp(4), dp(3), 0) }) }
        root.addView(controls)
        Diagnostics.record(this, "Game opened; touch works without a media session")
    }
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 12f; isAllCaps = false; typeface = Typeface.DEFAULT_BOLD
        setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()), intArrayOf(Color.rgb(73, 100, 123), cyan)))
        val shape = GradientDrawable().apply { setColor(Color.rgb(15,32,51)); cornerRadius = dp(10).toFloat(); setStroke(dp(1), Color.rgb(42,87,110)) }
        background = RippleDrawable(ColorStateList.valueOf(Color.argb(60,77,241,255)), shape, null)
        setOnClickListener { action() }
    }
    private fun holdButton(label: String, held: (Boolean) -> Unit, tap: () -> Unit): Button {
        var touching = false
        return button(label) { if (!touching) tap() }.apply {
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { touching = true; v.isPressed = true; held(true); true }
                    MotionEvent.ACTION_UP -> { held(false); v.isPressed = false; v.performClick(); touching = false; true }
                    MotionEvent.ACTION_CANCEL -> { held(false); v.isPressed = false; touching = false; true }
                    else -> true
                }
            }
        }
    }
    private fun updateDirection() { engine.moveDirection = (if (rightHeld) 1 else 0) - (if (leftHeld) 1 else 0) }
    private fun clearInput() {
        leftHeld = false; rightHeld = false; engine.clearInput()
        if (::leftButton.isInitialized) listOf(leftButton, rightButton, fireButton).forEach { it.isPressed = false }
    }
    private fun enableBand() {
        if (!notificationAsked && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationAsked = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
            notice = "Notification prompt shown · tap Enable band mode afterward"
            return
        }
        bandEnabled = true
        notice = "${if (audible.isChecked) "Audible" else "Silent"} media loop enabled; source unknown"
        startForegroundService(Intent(this, PlaybackService::class.java).setAction(PlaybackService.START).putExtra("game", true).putExtra("audible", audible.isChecked))
        renderPanel()
    }
    private fun disableBand() {
        if (bandEnabled) stopService(Intent(this, PlaybackService::class.java))
        bandEnabled = false; notice = "Band mode off · touch controls remain available"
        renderPanel()
    }
    private fun renderPanel() {
        if (!::diagnostics.isInitialized) return
        primary.text = when (engine.state) { GameState.TITLE -> "Launch"; GameState.RUNNING -> "Pause"; GameState.PAUSED -> "Resume"; GameState.GAME_OVER -> "Play again" }
        bandButton.text = if (bandEnabled) "Disable band mode" else "Enable band mode"
        val running = engine.state == GameState.RUNNING
        game.keepScreenOn = running
        leftButton.isEnabled = running; rightButton.isEnabled = running; fireButton.isEnabled = running
        val b = BandMediaController
        fun verified(count: Int) = if (count == 0) "unverified" else "received; source unknown"
        val session = getSharedPreferences("diagnostics", MODE_PRIVATE).getString("state", "stopped")
        diagnostics.text = "${engine.state} · session: $session · score ${engine.scores.score} / best ${engine.scores.highScore}\nLast: ${b.lastCommand} · Play/Pause: ${b.playPauseCount}\nNext: ${b.nextCount} (${verified(b.nextCount)}) · Previous: ${b.previousCount} (${verified(b.previousCount)})\n$notice\nSession activity does not prove a band connection."
    }
    private fun saveHighScore() { getSharedPreferences("game", MODE_PRIVATE).edit().putInt("highScore", engine.scores.highScore).apply() }
    override fun onResume() {
        super.onResume()
        BandMediaController.attach(this, { command ->
            if (bandEnabled) when (command) { "PLAY", "PAUSE", "TOGGLE" -> engine.fire(); "NEXT" -> engine.moveBy(1); "PREVIOUS" -> engine.moveBy(-1) }
            renderPanel()
        }, { reason ->
            if (bandEnabled) { engine.pause(); clearInput(); disableBand(); notice = reason; renderPanel() }
        })
        game.setActive(true); handler.post(refresh)
    }
    override fun onPause() {
        engine.pause(); clearInput(); game.keepScreenOn = false; game.setActive(false); saveHighScore()
        handler.removeCallbacks(refresh); BandMediaController.detach(this)
        super.onPause()
    }
    override fun onStop() { disableBand(); super.onStop() }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && ::engine.isInitialized) { engine.pause(); clearInput() }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("notificationAsked", notificationAsked)
        super.onSaveInstanceState(outState)
    }
    @Deprecated("Retains only the Android-free simulation")
    override fun onRetainNonConfigurationInstance(): Any = engine
}
