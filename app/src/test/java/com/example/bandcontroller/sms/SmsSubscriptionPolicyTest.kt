package com.example.bandcontroller.sms

import org.junit.Assert.*
import org.junit.Test

class SmsSubscriptionPolicyTest {
    @Test fun invalidAndUnresolvedDefaultsCannotSelectASim() {
        listOf(-1, -2, Int.MIN_VALUE, Int.MAX_VALUE).forEach { assertFalse(SmsSubscriptionPolicy.usable(it)) }
    }
    @Test fun concreteSubscriptionIdsCanBeFrozenAtArmTime() {
        assertTrue(SmsSubscriptionPolicy.usable(0)); assertTrue(SmsSubscriptionPolicy.usable(5))
    }
}
