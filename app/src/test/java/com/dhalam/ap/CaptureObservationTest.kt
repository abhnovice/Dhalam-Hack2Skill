package com.dhalam.ap

import org.junit.Assert.*
import org.junit.Test

class CaptureObservationTest {
    @Test fun unknownRecordingValuesNeverBecomeInactive() {
        assertEquals("Active", recordingStatus(1))
        assertEquals("Inactive", recordingStatus(0))
        assertEquals("Unavailable", recordingStatus(-1))
        assertEquals("Unavailable", recordingStatus(99))
        assertEquals("Unavailable", explainSecurity("Screen capture", "Unknown").state)
        assertEquals("Unavailable", explainSecurity("Screen capture", "Unavailable").state)
    }

    @Test fun shortSettingsTextPreservesUnknownAndMissingStates() {
        assertEquals("Not set", compactSettingStatus("Not configured — set a screen lock"))
        assertEquals("Unavailable", compactSettingStatus("Unavailable — hardware"))
        assertEquals("Review", compactSettingStatus("Check in Settings"))
        assertEquals("On", compactSettingStatus("Reported on"))
    }
}
