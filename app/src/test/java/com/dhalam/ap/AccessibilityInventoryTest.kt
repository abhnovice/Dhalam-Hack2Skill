package com.dhalam.ap

import android.content.pm.ApplicationInfo
import org.junit.Assert.*
import org.junit.Test

class AccessibilityInventoryTest {
    @Test fun missingOrUnexpectedTogglesAreNotOff() {
        assertEquals("Check in Settings", toggleSettingStatus(null))
        assertEquals("Check in Settings", toggleSettingStatus("invalid"))
        assertEquals("Check in Settings", toggleSettingStatus("2"))
        assertEquals("Reported off", toggleSettingStatus("0"))
        assertEquals("Reported on", toggleSettingStatus("1"))
    }

    @Test fun powerButtonUsesCallEndingBitRatherThanBoolean() {
        assertTrue(powerButtonStatus("1").startsWith("Reported off"))
        assertTrue(powerButtonStatus("2").startsWith("Reported on"))
        assertTrue(powerButtonStatus("3").startsWith("Reported on"))
        assertEquals("Check in Settings", powerButtonStatus(null))
        assertEquals("Check in Settings", powerButtonStatus("99"))
    }

    @Test fun financeSuggestionIsExplicitAndUnknownAppsRemainUncategorized() {
        val finance = appCategory(ApplicationInfo.CATEGORY_UNDEFINED, "com.example.bank", "Demo", false)
        assertEquals("Banking & finance", finance.first)
        assertTrue(finance.second.contains("not verified"))
        assertEquals("Other / uncategorized", appCategory(-1, "com.example.app", "Demo", false).first)
        assertEquals("System & device tools", appCategory(-1, "android", "Android", true).first)
        assertEquals("Gaming", appCategory(ApplicationInfo.CATEGORY_GAME, "com.example.game", "Game", false).first)
    }
}
