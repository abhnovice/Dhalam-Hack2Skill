package com.dhalam.ap

import com.dhalam.ap.data.SecurityFact
import org.junit.Assert.*
import org.junit.Test

class AskEvidenceTest {
    private fun fact(id: Long, detail: String, time: Long = 10L, subject: String = "other.app") =
        SecurityFact(id, "permission:$id", "permission", subject, detail, "granted", time)

    @Test fun exactAppAndPermissionMatchesComeBeforeGenericSensitiveFacts() {
        val camera = fact(1, "android.permission.CAMERA")
        val contacts = fact(2, "android.permission.READ_CONTACTS", subject = "target.app")
        val result = selectAskEvidence(listOf(camera, contacts), "Explain target.app permissions", 10)
        assertEquals(2L, result.first().id)
        assertEquals(1L, selectAskEvidence(listOf(contacts, camera), "Why camera access?", 10).first().id)
    }

    @Test fun excludesRetainedOlderFactsAndLimitsRequestEvidence() {
        val facts = (1L..100L).map { fact(it, "android.permission.CAMERA") } + fact(101, "camera", 1)
        val result = selectAskEvidence(facts, "camera", 10)
        assertEquals(48, result.size)
        assertFalse(result.any { it.id == 101L })
        assertEquals(result.map { it.id }, selectAskEvidence(facts.reversed(), "camera", 10).map { it.id })
    }

    @Test fun enabledAccessibilityIsIncludedBeforeUnrelatedPermissions() {
        val service = SecurityFact(3, "enabled:service", "accessibility_enabled", "service",
            "Read window content", "enabled", 10)
        assertEquals(3L, selectAskEvidence(listOf(fact(1, "INTERNET"), service), "Review device exposure", 10).first().id)
    }

    @Test fun acceptsHttpsAndOnlyExplicitLocalHttpOrigins() {
        assertEquals("https://demo.example", normalizeBackendUrl(" https://demo.example/ "))
        assertEquals("http://10.0.2.2:18765", normalizeBackendUrl("http://10.0.2.2:18765"))
        assertEquals("http://127.0.0.1:18765", normalizeBackendUrl("http://127.0.0.1:18765"))
    }

    @Test fun rejectsTokenLeakingOrInsecureUrls() {
        listOf("http://remote.example", "http://192.168.1.1:8000", "https://user:password@example.com",
            "https://example.com?token=secret", "https://example.com#secret",
            "https://example.com/ask", "file:///tmp/backend", "https://example.com:99999").forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { normalizeBackendUrl(url) }
        }
    }
}
