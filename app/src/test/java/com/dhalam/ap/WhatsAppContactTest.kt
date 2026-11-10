package com.dhalam.ap

import org.junit.Assert.*
import org.junit.Test

class WhatsAppContactTest {
    @Test fun acceptsPublicNumbersAndPlainWaLinks() {
        assertEquals("14155550123", whatsappContactNumber("+1 (415) 555-0123"))
        assertEquals("14155550123", whatsappContactNumber("https://wa.me/14155550123"))
        assertEquals("https://wa.me/14155550123?text=CHECK%3A%20", whatsappBotChatUrl("+14155550123"))
    }

    @Test fun rejectsCredentialsOtherHostsAndMalformedContacts() {
        listOf("", "token-secret", "https://evil.example/14155550123",
            "https://wa.me@evil.example/14155550123", "https://wa.me/14155550123/extra",
            "https://wa.me/14155550123?text=secret", "0000", "12345678901234567890").forEach {
            assertNull(whatsappContactNumber(it))
        }
    }
}
