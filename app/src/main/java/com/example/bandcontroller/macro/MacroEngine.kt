package com.example.bandcontroller.macro

enum class MacroButton { PREVIOUS, PLAY_PAUSE, NEXT }
enum class MacroResult { IGNORED, PROGRESS, RESET, TRIGGERED }

/** One-shot sequence recognition, independent of Android and transport. Clock input must be monotonic. */
class MacroEngine(private val windowMs: Long = 8_000) {
    val sequence = listOf(MacroButton.PREVIOUS, MacroButton.PLAY_PAUSE, MacroButton.NEXT)
    var armed = false
        private set
    var progress = 0
        private set
    private var startedAt: Long? = null

    fun arm() { armed = true; resetProgress() }
    fun disarm() { armed = false; resetProgress() }
    private fun resetProgress() { progress = 0; startedAt = null }
    fun expire(nowMs: Long): Boolean {
        val start = startedAt ?: return false
        if (nowMs - start > windowMs || nowMs < start) { resetProgress(); return true }
        return false
    }
    fun accept(button: MacroButton, nowMs: Long): MacroResult {
        if (!armed) return MacroResult.IGNORED
        expire(nowMs)
        if (button != sequence[progress]) {
            resetProgress()
            // A new Previous can begin the next attempt immediately.
            if (button != sequence.first()) return MacroResult.RESET
        }
        if (progress == 0) startedAt = nowMs
        progress++
        if (progress == sequence.size) { disarm(); return MacroResult.TRIGGERED }
        return MacroResult.PROGRESS
    }
    companion object {
        fun fromMedia(command: String): MacroButton? = when (command) {
            "PREVIOUS" -> MacroButton.PREVIOUS
            "PLAY", "PAUSE", "TOGGLE" -> MacroButton.PLAY_PAUSE
            "NEXT" -> MacroButton.NEXT
            else -> null
        }
    }
}
