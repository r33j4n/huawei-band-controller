package com.example.bandcontroller.sms

import org.junit.Assert.*
import org.junit.Test

class SmsProgressTest {
    private fun describe(codes: List<Int?>) = SmsProgress.summary(codes, -1) { "error $it" }
    @Test fun noRequestNeverClaimsSent() {
        assertEquals("No SMS requested.", describe(emptyList()))
    }
    @Test fun queuedRequestIsNotSent() {
        assertTrue(describe(listOf(null)).contains("awaiting send results"))
        assertFalse(describe(listOf(null)).contains("Android reported SMS sent"))
    }
    @Test fun partialMultipartSuccessIsStillPending() {
        val text = describe(listOf(-1, null, null))
        assertTrue(text.contains("1/3")); assertTrue(text.contains("awaiting"))
    }
    @Test fun everySegmentMustSucceedBeforeSentIsReported() {
        val text = describe(listOf(-1, -1))
        assertTrue(text.contains("Android reported SMS sent (2/2"))
        assertTrue(text.contains("delivery is not verified"))
    }
    @Test fun partialFailureWinsOverSuccessAndWarnsAboutDuplicateRetries() {
        val text = describe(listOf(-1, 4, null))
        assertTrue(text.contains("SMS failed: error 4")); assertTrue(text.contains("1/3"))
        assertTrue(text.contains("No automatic retry")); assertTrue(text.contains("duplicate"))
    }
    @Test fun zeroResultCodeIsFailureNotAnAbsentResult() {
        assertTrue(describe(listOf(0)).contains("SMS failed: error 0"))
    }
}
