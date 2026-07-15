package com.example.gpstest.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    @Test
    fun `AppSettings defaults match constants`() {
        val settings = AppSettings()
        assertEquals(DarkModeConfig.SYSTEM, settings.darkMode)
        assertTrue(settings.autoSaveEnabled)
        assertEquals(AppSettings.DEFAULT_SNAPSHOT_INTERVAL_MS, settings.snapshotIntervalMs)
        assertEquals(AppSettings.DEFAULT_MAX_SNAPSHOTS, settings.maxSnapshots)
        assertEquals(AppSettings.DEFAULT_RETENTION_DAYS, settings.retentionDays)
    }

    @Test
    fun `resolveDarkTheme SYSTEM with system dark returns true`() {
        val settings = AppSettings(darkMode = DarkModeConfig.SYSTEM)
        assertTrue(settings.resolveDarkTheme(isSystemInDarkTheme = true))
    }

    @Test
    fun `resolveDarkTheme SYSTEM with system light returns false`() {
        val settings = AppSettings(darkMode = DarkModeConfig.SYSTEM)
        assertFalse(settings.resolveDarkTheme(isSystemInDarkTheme = false))
    }

    @Test
    fun `resolveDarkTheme ON forces dark even when system light`() {
        val settings = AppSettings(darkMode = DarkModeConfig.ON)
        assertTrue(settings.resolveDarkTheme(isSystemInDarkTheme = false))
    }

    @Test
    fun `resolveDarkTheme OFF forces light even when system dark`() {
        val settings = AppSettings(darkMode = DarkModeConfig.OFF)
        assertFalse(settings.resolveDarkTheme(isSystemInDarkTheme = true))
    }

    @Test
    fun `copy preserves other fields`() {
        val original =
            AppSettings(
                darkMode = DarkModeConfig.ON,
                autoSaveEnabled = false,
                snapshotIntervalMs = 30_000L,
                maxSnapshots = 50,
                retentionDays = 3,
            )
        val copied = original.copy(darkMode = DarkModeConfig.OFF)
        assertEquals(DarkModeConfig.OFF, copied.darkMode)
        assertFalse(copied.autoSaveEnabled)
        assertEquals(30_000L, copied.snapshotIntervalMs)
        assertEquals(50, copied.maxSnapshots)
        assertEquals(3, copied.retentionDays)
    }

    @Test
    fun `INTERVAL_OPTIONS_MS contains 60000`() {
        assertTrue(AppSettings.INTERVAL_OPTIONS_MS.contains(60_000L))
    }
}
