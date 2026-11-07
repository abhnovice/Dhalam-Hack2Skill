package com.dhalam.ap

import org.junit.Assert.*
import org.junit.Test

class SecurityToolsTest {
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
