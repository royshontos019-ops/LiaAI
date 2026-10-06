package com.Lia.assistant.action

import org.junit.Assert.assertEquals
import org.junit.Test

class CommIntentsTest {
    @Test fun digitsKeepsAsciiDigitsOnly() = assertEquals("1555123", CommIntents.digits(" +1 (555) 123"))

    @Test fun telAndSmsUris() {
        assertEquals("tel:+8801712345678", CommIntents.telUri("+880 1712-345678"))
        assertEquals("smsto:5551234567", CommIntents.smsUri("(555) 123-4567"))
    }

    @Test fun whatsappUrlUsesDigitsAndPercentTwentyForSpaces() =
        assertEquals(
            "https://wa.me/8801712345678?text=hello%20world%20%26%20more",
            CommIntents.waUrl("+880 1712-345678", "hello world & more"),
        )

    @Test fun whatsappUrlEncodesNonLatinText() =
        assertEquals("https://wa.me/123?text=%E0%A6%B9%E0%A6%BE%E0%A6%87", CommIntents.waUrl("123", "হাই"))

    @Test fun whatsappUrlWithoutTextHasNoQuery() = assertEquals("https://wa.me/123", CommIntents.waUrl("123", ""))

    @Test fun bothWhatsAppVariantsAreKnown() =
        assertEquals(listOf("com.whatsapp", "com.whatsapp.w4b"), CommIntents.WHATSAPP_PACKAGES)
}
