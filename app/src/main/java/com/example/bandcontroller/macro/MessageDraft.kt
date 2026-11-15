package com.example.bandcontroller.macro

/** A draft request, never evidence that a message was sent or delivered. */
enum class DraftTarget { WHATSAPP_BUSINESS, SMS }

data class MessageDraft(val phone: String, val message: String, val target: DraftTarget = DraftTarget.WHATSAPP_BUSINESS) {
    companion object {
        const val BUSINESS_PACKAGE = "com.whatsapp.w4b"
        const val DEFAULT_PHONE = "+94761069598"
        const val DEFAULT_MESSAGE = "Hi This message is send by huawei band"
        fun normalizePhone(input: String): String? {
            val trimmed = input.trim()
            if (!trimmed.matches(Regex("\\+?[0-9 ()-]+"))) return null
            var digits = trimmed.filter { it in '0'..'9' }
            // Local ten-digit numbers explicitly use Sri Lanka's +94 country code.
            if (!trimmed.startsWith('+') && digits.matches(Regex("0[0-9]{9}"))) digits = "94" + digits.drop(1)
            if (!digits.matches(Regex("[1-9][0-9]{7,14}"))) return null
            return digits
        }
    }
}
