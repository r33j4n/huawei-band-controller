package com.example.bandcontroller

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Diagnostics {
    @Synchronized fun record(context: Context, message: String, event: String? = null) {
        val prefs = context.getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date())
        val line = "$timestamp  $message"
        val edit = prefs.edit()
        if (event != null) {
            edit.putInt("count_$event", prefs.getInt("count_$event", 0) + 1)
            edit.putString("last_$event", timestamp)
        }
        edit.putString("log", (prefs.getString("log", "") + "\n" + line).takeLast(24000)).apply()
        Log.i("BandController", line)
    }
}
