package com.example.bandcontroller.macro

import org.junit.Assert.*
import org.junit.Test

class MacroEngineTest {
    private fun complete(e: MacroEngine, start: Long = 0): MacroResult {
        e.accept(MacroButton.PREVIOUS,start)
        e.accept(MacroButton.PLAY_PAUSE,start + 1_000)
        return e.accept(MacroButton.NEXT,start + 2_000)
    }
    @Test fun unarmedInputsCannotExecute() {
        val e = MacroEngine()
        assertEquals(MacroResult.IGNORED, complete(e)); assertEquals(0,e.progress)
    }
    @Test fun sequenceFiresOnceAndRequiresRearming() {
        val e = MacroEngine(); e.arm()
        assertEquals(MacroResult.TRIGGERED,complete(e)); assertFalse(e.armed)
        assertEquals(MacroResult.IGNORED,complete(e,3_000))
        e.arm(); assertEquals(MacroResult.TRIGGERED,complete(e,6_000))
    }
    @Test fun wrongOrderDoesNotFire() {
        val e = MacroEngine(); e.arm()
        e.accept(MacroButton.PREVIOUS,0)
        assertEquals(MacroResult.RESET,e.accept(MacroButton.NEXT,10))
        assertEquals(MacroResult.RESET,e.accept(MacroButton.PLAY_PAUSE,20)); assertTrue(e.armed)
    }
    @Test fun repeatedPreviousBeginsANewAttempt() {
        val e = MacroEngine(); e.arm()
        e.accept(MacroButton.PREVIOUS,0); e.accept(MacroButton.PLAY_PAUSE,100)
        assertEquals(MacroResult.PROGRESS,e.accept(MacroButton.PREVIOUS,200))
        assertEquals(1,e.progress)
        e.accept(MacroButton.PLAY_PAUSE,300)
        assertEquals(MacroResult.TRIGGERED,e.accept(MacroButton.NEXT,400))
    }
    @Test fun staleSequenceCannotTrigger() {
        val e = MacroEngine(); e.arm()
        e.accept(MacroButton.PREVIOUS,0); e.accept(MacroButton.PLAY_PAUSE,5_000)
        assertEquals(MacroResult.RESET,e.accept(MacroButton.NEXT,8_001))
    }
    @Test fun timeoutIsMeasuredFromFirstInputRatherThanEachGap() {
        val e = MacroEngine(); e.arm()
        e.accept(MacroButton.PREVIOUS,10_000); e.accept(MacroButton.PLAY_PAUSE,17_000)
        assertEquals(MacroResult.RESET,e.accept(MacroButton.NEXT,19_000))
    }
    @Test fun exactWindowBoundaryIsAllowed() {
        val e = MacroEngine(); e.arm()
        e.accept(MacroButton.PREVIOUS,0); e.accept(MacroButton.PLAY_PAUSE,4_000)
        assertEquals(MacroResult.TRIGGERED,e.accept(MacroButton.NEXT,8_000))
    }
    @Test fun visibleTimeoutClearsProgressButLeavesMacroArmed() {
        val e = MacroEngine(); e.arm(); e.accept(MacroButton.PREVIOUS,0)
        assertFalse(e.expire(7_999)); assertTrue(e.expire(8_001))
        assertEquals(0,e.progress); assertTrue(e.armed)
    }
    @Test fun disarmingDropsPartialSequence() {
        val e = MacroEngine(); e.arm(); e.accept(MacroButton.PREVIOUS,0); e.disarm(); e.arm()
        assertEquals(MacroResult.RESET,e.accept(MacroButton.PLAY_PAUSE,100))
    }
    @Test fun mediaPlayPauseAndToggleShareOneSymbol() {
        listOf("PLAY","PAUSE","TOGGLE").forEach { assertEquals(MacroButton.PLAY_PAUSE,MacroEngine.fromMedia(it)) }
        assertEquals(MacroButton.NEXT,MacroEngine.fromMedia("NEXT"))
        assertEquals(MacroButton.PREVIOUS,MacroEngine.fromMedia("PREVIOUS"))
        assertNull(MacroEngine.fromMedia("STOP"))
    }
    @Test fun backwardsClockCannotCompleteAnOldSequence() {
        val e = MacroEngine(); e.arm(); e.accept(MacroButton.PREVIOUS,100)
        assertEquals(MacroResult.RESET,e.accept(MacroButton.PLAY_PAUSE,50))
    }
}
