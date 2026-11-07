package com.dhalam.ap

import org.junit.Assert.*
import org.junit.Test

class TemporarySignInTest {
    @Test fun correctCredentialsAreAcceptedOnlyInDebugBuilds() {
        assertEquals(BuildConfig.DEBUG, temporaryCredentialsMatch("abhyudayamarya", "dummydhalam"))
    }

    @Test fun wrongCredentialsAreRejected() {
        assertFalse(temporaryCredentialsMatch("other", "dummydhalam"))
        assertFalse(temporaryCredentialsMatch("abhyudayamarya", "wrong"))
        assertFalse(temporaryCredentialsMatch("", ""))
    }

    @Test fun passwordMustMatchExactly() {
        assertFalse(temporaryCredentialsMatch("abhyudayamarya", "dummydhalam "))
        assertFalse(temporaryCredentialsMatch("abhyudayamarya", "DummyDhalam"))
    }
}
