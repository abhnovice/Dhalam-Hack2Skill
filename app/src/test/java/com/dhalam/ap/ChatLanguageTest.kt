package com.dhalam.ap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatLanguageTest {
    @Test fun supportedCodesMatchTheBackendAllowList() {
        assertEquals(listOf("en", "hi", "mr", "bn", "ta", "te"), chatLanguages.map { it.code })
    }

    @Test fun unknownDeviceLanguageFallsBackToEnglish() {
        assertEquals("en", languageForCode("fr").code)
        assertEquals("en", languageForCode(null).code)
        assertEquals("hi", languageForCode("hi").code)
    }

    @Test fun headlinesAreLocalizedByThreatLevel() {
        val hindi = languageForCode("hi").strings
        assertEquals(hindi.urgent, triageHeadline("CRITICAL", hindi))
        assertEquals(hindi.low, triageHeadline("LOW", hindi))
        assertEquals(hindi.medium, triageHeadline("UNKNOWN", hindi))
        assertEquals("Stop. This looks risky.", triageHeadline("HIGH"))
    }

    @Test fun spokenAnswerReadsHeadlineSummaryAndFirstAction() {
        val hindi = languageForCode("hi").strings
        assertEquals(
            "${hindi.urgent}. पहले भेजने वाले की जाँच करें।. अपने बैंक को फ़ोन करें।",
            spokenText("HIGH", "पहले भेजने वाले की जाँच करें। दूसरा वाक्य।", "अपने बैंक को फ़ोन करें।", hindi)
        )
        assertEquals("${hindi.low}. ठीक है", spokenText("LOW", "ठीक है", "", hindi))
    }

    @Test fun everyLanguageHasEveryString() {
        chatLanguages.forEach { language ->
            val s = language.strings
            listOf(s.hint, s.emptyTitle, s.emptySubtitle, s.urgent, s.low, s.medium, s.checking, s.check,
                s.listening, s.dictate, s.readAloud, s.stopReading, s.voiceUnavailable, s.language)
                .forEach { assertTrue("${language.code} has a blank string", it.isNotBlank()) }
        }
    }
}
