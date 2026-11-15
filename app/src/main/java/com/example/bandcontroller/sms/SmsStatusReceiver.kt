package com.example.bandcontroller.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Manifest receiver keeps results observable even if the activity has closed. */
class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DirectSmsSender.SENT_ACTION) return
        val id = intent.getStringExtra("request") ?: return
        SmsStatusStore.received(context, id, intent.getIntExtra("part", -1), resultCode)
    }
}
