package com.example.bandcontroller

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var report: TextView
    private val refresh = object : Runnable {
        override fun run() { render(); handler.postDelayed(this, 500) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24) }
        val scroll = ScrollView(this).apply { addView(layout) }
        setContentView(scroll)
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        scroll.requestApplyInsets()
        layout.addView(TextView(this).apply { text = "Band Controller"; textSize = 26f })
        layout.addView(TextView(this).apply { text = "Tests Android media commands only. Huawei Health must route the band’s music controls to this session. No band connection or raw touches are read." })
        val audible = CheckBox(this).apply { text = "Audible quiet test tone (default: silent playback)" }
        layout.addView(audible)
        fun button(label: String, action: () -> Unit) { layout.addView(Button(this).apply { text = label; setOnClickListener { action() } }) }
        button("Play Neon Orbit space shooter") { startActivity(Intent(this, GameActivity::class.java)) }
        button("Start / resume test session") {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
            startForegroundService(Intent(this, PlaybackService::class.java).setAction(PlaybackService.START).putExtra("audible", audible.isChecked))
        }
        button("Stop session") { startService(Intent(this, PlaybackService::class.java).setAction(PlaybackService.STOP)) }
        button("Clear counters and logs") {
            val prefs = getSharedPreferences("diagnostics", MODE_PRIVATE)
            val state = prefs.getString("state", "stopped")
            prefs.edit().clear().putString("state", state).apply()
            Diagnostics.record(this, "Counters and logs cleared")
            render()
        }
        button("Share diagnostics") {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report.text.toString()), "Share diagnostics"))
        }
        report = TextView(this).apply { setTextIsSelectable(true); textSize = 14f }
        layout.addView(report)
        Diagnostics.record(this, "UI opened; Android ${Build.VERSION.RELEASE}, ${Build.MANUFACTURER} ${Build.MODEL}")
    }
    private fun render() {
        val p = getSharedPreferences("diagnostics", MODE_PRIVATE)
        report.text = buildString {
            append("Session: ${p.getString("state", "stopped")}\n")
            listOf("NEXT", "PREVIOUS", "PLAY", "PAUSE", "TOGGLE").forEach {
                append("$it: ${p.getInt("count_$it", 0)} | last: ${p.getString("last_$it", "—")}\n")
            }
            append("\nDiagnostics (persisted; newest at bottom)\n")
            append(p.getString("log", "No commands recorded."))
        }
    }
    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
}
