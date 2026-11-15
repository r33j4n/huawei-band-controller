package com.example.bandcontroller.sms

object SmsSubscriptionPolicy {
    // Negative IDs are invalid/ask-every-time; MAX_VALUE is Android's default placeholder,
    // not a concrete subscription frozen for a user-reviewed send.
    fun usable(id: Int) = id >= 0 && id < Int.MAX_VALUE
}
