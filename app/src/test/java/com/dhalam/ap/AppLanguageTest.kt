package com.dhalam.ap

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class AppLanguageTest {
    @After fun reset() { appLanguage.value = languageForCode("en") }

    @Test fun everyTranslationPreservesPlaceholders() {
        val placeholders = Regex("\\{\\w+}")
        appStringTable.forEach { (english, translations) ->
            assertEquals(english, 5, translations.size)
            translations.forEach { translated ->
                assertTrue(english, translated.isNotBlank())
                assertEquals(english, placeholders.findAll(english).map { it.value }.toSet(),
                    placeholders.findAll(translated).map { it.value }.toSet())
            }
        }
    }

    @Test fun sharedLanguageChangesDashboardAndToolsTogether() {
        chatLanguages.forEach { language ->
            appLanguage.value = language
            for (label in listOf("Device access", "Security overview", "Sign in with Google", "Scam check")) {
                if (language.code == "en") assertEquals(label, tr(label))
                else assertNotEquals(label, tr(label))
            }
        }
    }

    @Test fun englishAndUnknownIdentifiersRemainUnchanged() {
        assertEquals("Checked 10:30", trText("Checked 10:30"))
        appLanguage.value = languageForCode("hi")
        assertEquals("com.example.app", trText("com.example.app"))
        assertEquals("example.org", trText("example.org"))
        assertEquals("Unknown template 7", tr("Unknown template {n}", mapOf("n" to 7)))
    }

    @Test fun dynamicCountsAndHostnamesKeepTheirValues() {
        appLanguage.value = languageForCode("te")
        assertEquals(tr("{n} enabled services reported", mapOf("n" to 12)), trText("12 enabled services reported"))
        assertEquals(tr("Scheme: {scheme} · transport only, not trust", mapOf("scheme" to "https")),
            trText("Scheme: https · transport only, not trust"))
    }

    @Test fun renderingFindingsAndReportsNeverChangesStoredState() {
        val finding = SecurityFinding("Device lock", "Reported on", "Needs attention", "Check in Settings")
        val original = finding.copy()
        val report = "Device lock [Needs attention]: Reported on\nNext step: Check in Settings\n"
        appLanguage.value = languageForCode("bn")
        assertNotEquals(finding.state, trText(finding.state))
        assertTrue(translateSecurityReport(report).contains(tr("Needs attention")))
        assertEquals(original, finding)
        appLanguage.value = languageForCode("en")
        assertEquals(report, translateSecurityReport(report))
    }
}
