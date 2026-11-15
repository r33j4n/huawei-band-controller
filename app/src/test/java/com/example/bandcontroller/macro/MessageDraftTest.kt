package com.example.bandcontroller.macro

import org.junit.Assert.*
import org.junit.Test

class MessageDraftTest {
    @Test fun sriLankanLocalNumberGetsExplicitCountryCode() {
        assertEquals("94761069598",MessageDraft.normalizePhone("0761069598"))
        assertEquals("94761069598",MessageDraft.normalizePhone("+94 (76) 106-9598"))
    }
    @Test fun internationalNumbersRemainInternational() {
        assertEquals("447700900123",MessageDraft.normalizePhone("+44 7700 900123"))
        assertEquals("94761069598",MessageDraft.normalizePhone("94761069598"))
    }
    @Test fun malformedOrLinkLikeRecipientsAreRejected() {
        listOf("", "076", "+0761069598", "++94761069598", "94761069598?text=x", "abc94761069598", "+1234567890123456").forEach {
            assertNull(it,MessageDraft.normalizePhone(it))
        }
    }
    @Test fun exactRequestedTextAndChosenTargetAreRetained() {
        assertEquals("Hi This message is send by huawei band",MessageDraft.DEFAULT_MESSAGE)
        val d = MessageDraft("94761069598",MessageDraft.DEFAULT_MESSAGE,DraftTarget.SMS)
        assertEquals(DraftTarget.SMS,d.target); assertEquals(MessageDraft.DEFAULT_MESSAGE,d.message)
    }
}
