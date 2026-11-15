package com.example.bandcontroller.sms

/** Only sent callbacks establish sent status; missing callbacks never imply success. */
object SmsProgress {
    fun summary(results: List<Int?>, ok: Int, describeError: (Int) -> String): String {
        val sent = results.count { it == ok }
        val failed = results.firstOrNull { it != null && it != ok }
        return when {
            results.isEmpty() -> "No SMS requested."
            failed != null -> "SMS failed: ${describeError(failed)}. $sent/${results.size} segments reported sent. No automatic retry; retrying can duplicate sent segments."
            sent == results.size -> "Android reported SMS sent (${results.size}/${results.size} segments). Recipient delivery is not verified."
            else -> "SMS requested; awaiting send results ($sent/${results.size} segments reported sent). Do not retry while the result is unknown."
        }
    }
}
