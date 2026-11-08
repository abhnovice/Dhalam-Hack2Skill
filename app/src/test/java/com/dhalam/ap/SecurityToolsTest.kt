package com.dhalam.ap

import org.junit.Assert.*
import org.junit.Test

class SecurityToolsTest {
    @Test fun connectionErrorsExplainPhoneAndCloudFailuresWithoutLeakingDetails() {
        val failure = java.io.IOException("secret-token must never appear")
        assertTrue(connectionFailureMessage("http://10.0.2.2:18765", failure).contains("only in an Android emulator"))
        assertTrue(connectionFailureMessage("http://127.0.0.1:18765", failure).contains("adb reverse"))
        val remote = connectionFailureMessage("https://demo.run.app", failure)
        assertTrue(remote.contains("internet connection"))
        assertFalse(remote.contains("secret-token"))
        assertTrue(connectionFailureMessage("https://demo.run.app", java.net.SocketTimeoutException()).contains("too long"))
        assertTrue(connectionFailureMessage("bad url", IllegalArgumentException()).contains("backend URL is invalid"))
    }

    @Test fun chatHeadlinesDoNotPromiseSafety() {
        assertEquals("No clear scam signs.", triageHeadline("LOW"))
        assertEquals("Pause. Let’s check first.", triageHeadline("MEDIUM"))
        assertEquals("Stop. This looks risky.", triageHeadline("HIGH"))
        assertEquals("Pause. Let’s check first.", triageHeadline("UNKNOWN"))
    }

    @Test fun triageConnectionRejectsRemoteCleartextAndCredentialUrls() {
        assertEquals("http://127.0.0.1:18765", triageOrigin("http://127.0.0.1:18765/", true))
        assertEquals("https://demo.run.app", triageOrigin("https://demo.run.app", false))
        listOf("http://remote.example", "https://user:password@example.com", "https://example.com?token=x",
            "https://example.com/path", "https://example.com:99999").forEach { value ->
            assertTrue(runCatching { triageOrigin(value, true) }.isFailure)
        }
        assertTrue(runCatching { triageOrigin("http://127.0.0.1:18765", false) }.isFailure)
    }
    @Test fun boundedImageReadRejectsOversizeInput() {
        assertArrayEquals(byteArrayOf(1, 2), byteArrayOf(1, 2).inputStream().readBounded(2))
        assertTrue(runCatching { byteArrayOf(1, 2, 3).inputStream().readBounded(2) }.isFailure)
    }

    @Test fun linksExposeTheDestinationWithoutOpeningIt() {
        val result = inspectSecurityLink("https://trusted.example@evil.example/login")
        assertEquals("evil.example", result.host)
        assertTrue(result.notes.any { "before @" in it })
        assertTrue(inspectSecurityLink("https://login.bank.example.evil.test").notes.any { "Many domain labels" in it })
        assertTrue(inspectSecurityLink("https://аррӏе.com").notes.any { "Internationalized" in it })
        assertTrue(inspectSecurityLink("https://xn--pple-43d.example").notes.any { "Internationalized" in it })
        assertTrue(inspectSecurityLink("http://127.0.0.1:8080").notes.any { "unusual port" in it })
        assertTrue(inspectSecurityLink("https://example.com").notes.any { "does not establish" in it })
    }
    @Test fun invalidLinksAreRejected() {
        listOf("", "javascript://alert", "https://a b.com", "https://a\\b.com", "http://", "https://example.com:abc", "https://example.com:99999", "https://bad_host.com").forEach {
            assertEquals(it, "", inspectSecurityLink(it).host)
        }
    }
    @Test fun unknownResultsAreNotConfigurationChangesOrPasses() {
        val known = SecurityCheckRecord(1, listOf(explainSecurity("Device lock", "Configured")))
        val unknown = SecurityCheckRecord(2, listOf(explainSecurity("Device lock", "Unavailable")))
        val journal = SecurityJournalData(listOf(unknown, known))
        assertEquals("Unavailable", unknown.findings.single().state)
        assertEquals(1L, journal.lastSuccess("Device lock"))
        assertTrue(journal.changes().single().contains("comparison unavailable"))
    }
    @Test fun missingLockHasActionAndReportIncludesScope() {
        val finding = explainSecurity("Device lock", "Not configured — set a screen lock")
        assertEquals("Needs attention", finding.state)
        assertTrue(finding.action.contains("Set a screen lock"))
        val journal = SecurityJournalData(listOf(SecurityCheckRecord(1, listOf(finding))))
        assertTrue(journal.report().contains("not a malware scan"))
        assertTrue(journal.report().contains("user-confirmed, not automatically verified"))
    }
}
