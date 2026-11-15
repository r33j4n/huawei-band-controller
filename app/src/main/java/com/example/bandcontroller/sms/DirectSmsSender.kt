package com.example.bandcontroller.sms

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import com.example.bandcontroller.Diagnostics
import com.example.bandcontroller.macro.MessageDraft

object DirectSmsSender {
    const val SENT_ACTION = "com.example.bandcontroller.SMS_SENT"
    fun defaultSubscription(): Int? = SubscriptionManager.getDefaultSmsSubscriptionId()
        .takeIf(SmsSubscriptionPolicy::usable)

    /** One API call per matched macro; there is deliberately no retry worker or queue. */
    fun send(context: Context, request: MessageDraft, subscriptionId: Int): String {
        require(SmsSubscriptionPolicy.usable(subscriptionId)) { "A concrete SMS subscription is required" }
        require(request.target == com.example.bandcontroller.macro.DraftTarget.SMS) { "SMS action required" }
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return "SMS permission is missing. No SMS requested; grant it and arm again."
        }
        var id: String? = null
        val callbacks = arrayListOf<PendingIntent>()
        try {
            @Suppress("DEPRECATION")
            val manager = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
                else SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            val parts = manager.divideMessage(request.message)
            check(parts.isNotEmpty()) { "Empty SMS" }
            val requestId = SmsStatusStore.begin(context, parts.size)
            id = requestId
            parts.indices.forEach { part ->
                val callback = Intent(context, SmsStatusReceiver::class.java).setAction(SENT_ACTION)
                    .setData(Uri.parse("bandcontroller://sms/$requestId/$part"))
                    .putExtra("request", requestId).putExtra("part", part)
                callbacks.add(PendingIntent.getBroadcast(context, 0, callback, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE))
            }
            val number = "+${request.phone}"
            if (parts.size == 1) manager.sendTextMessage(number, null, parts[0], callbacks[0], null)
            else manager.sendMultipartTextMessage(number, null, parts, callbacks, null)
            Diagnostics.record(context, "SMS submitted to Android: ${parts.size} segment(s); waiting for sent callbacks")
            return "SMS request submitted to Android. See Latest SMS result below; recipient delivery is not verified."
        } catch (_: SecurityException) {
            id?.let { SmsStatusStore.failed(context, it, "Android denied the SMS request; check SMS permission/SIM restrictions") }
            return "Android denied the SMS request. Check SMS permission/SIM restrictions. No automatic retry."
        } catch (e: RuntimeException) {
            // Do not retry: Android may already have accepted some segments before an exception.
            id?.let { SmsStatusStore.failed(context, it, "SMS request failed or has an unknown result (${e.javaClass.simpleName})") }
            return "SMS request failed or has an unknown result (${e.javaClass.simpleName}). Check SIM settings/service. No automatic retry."
        }
    }
}
