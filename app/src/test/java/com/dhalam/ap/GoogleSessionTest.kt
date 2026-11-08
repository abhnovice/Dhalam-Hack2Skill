package com.dhalam.ap

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GoogleSessionTest {
    @Test fun expiredIdentityRefreshesAndRetriesOnce() = runBlocking {
        val old = AskBackendSession("https://example.com", "old")
        val fresh = AskBackendSession("https://example.com", "fresh")
        var refreshes = 0
        var invalidations = 0
        var requests = 0
        val result = withRefreshedGoogleSession(old, { refreshes++; fresh }, { invalidations++ }) {
            requests++
            if (it === old) throw AskSignInRequiredException()
            "answer"
        }
        assertEquals("answer", result)
        assertEquals(1, refreshes)
        assertEquals(1, invalidations)
        assertEquals(2, requests)
    }

    @Test fun rejectedRefreshDoesNotLoop() = runBlocking {
        var refreshes = 0
        var requests = 0
        try {
            withRefreshedGoogleSession(AskBackendSession("https://example.com"),
                { refreshes++; AskBackendSession("https://example.com") }, {}) {
                requests++
                throw AskSignInRequiredException()
            }
            fail("Expected sign-in required")
        } catch (_: AskSignInRequiredException) { }
        assertEquals(1, refreshes)
        assertEquals(2, requests)
    }

    @Test fun networkErrorsDoNotTriggerCredentialRefresh() = runBlocking {
        var refreshes = 0
        try {
            withRefreshedGoogleSession(AskBackendSession("https://example.com"),
                { refreshes++; AskBackendSession("https://example.com") }, {}) {
                throw java.io.IOException("offline")
            }
            fail("Expected network error")
        } catch (_: java.io.IOException) { }
        assertEquals(0, refreshes)
    }
}
