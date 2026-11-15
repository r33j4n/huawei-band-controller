package com.example.bandcontroller.sms

import android.app.Activity
import android.content.Context
import android.telephony.SmsManager
import com.example.bandcontroller.Diagnostics
import java.util.UUID

object SmsStatusStore {
    private fun prefs(context: Context) = context.getSharedPreferences("sms_status", Context.MODE_PRIVATE)
    fun begin(context: Context, parts: Int): String {
        val id = UUID.randomUUID().toString()
        check(prefs(context).edit().clear().putString("request", id).putInt("parts", parts).commit()) {
            "Could not save SMS request state; SMS was not requested"
        }
        return id
    }
    fun failed(context: Context, id: String, reason: String) {
        val p = prefs(context)
        if (p.getString("request", null) == id) p.edit().putString("error", reason).commit()
        Diagnostics.record(context, "SMS request error: $reason; no automatic retry")
    }
    fun received(context: Context, id: String, part: Int, result: Int) {
        val p = prefs(context)
        if (p.getString("request", null) != id || part !in 0 until p.getInt("parts", 0)) return
        if (p.contains("part_$part")) return
        p.edit().putInt("part_$part", result).commit()
        Diagnostics.record(context, "SMS sent callback: segment ${part + 1}, ${if (result == Activity.RESULT_OK) "sent; delivery not verified" else error(result)}")
    }
    fun summary(context: Context): String {
        val p = prefs(context)
        val codes = List(p.getInt("parts", 0)) { i -> if (p.contains("part_$i")) p.getInt("part_$i", 0) else null }
        val summary = SmsProgress.summary(codes, Activity.RESULT_OK, ::error)
        val error = p.getString("error", null) ?: return summary
        return if (codes.any { it == null }) {
            "$error. ${codes.count { it == Activity.RESULT_OK }}/${codes.size} segments reported sent; remaining outcomes unknown. No automatic retry."
        } else "$error. $summary"
    }
    private fun error(code: Int) = when (code) {
        SmsManager.RESULT_ERROR_RADIO_OFF -> "radio is off"
        SmsManager.RESULT_ERROR_NO_SERVICE -> "no mobile service"
        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "SMS sending limit exceeded"
        SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE -> "SIM fixed-dialling restriction"
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "modem/provider failure; check SIM and balance"
        else -> "Android error code $code"
    }
}
