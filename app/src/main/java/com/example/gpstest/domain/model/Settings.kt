package com.example.gpstest.domain.model

/** 深色模式：跟随系统 / 强制深色 / 强制浅色 */
enum class DarkModeConfig {
    SYSTEM,
    ON,
    OFF,
}

/** 应用全局设置。快照默认 100 条 × 60s ≈ 1.7h，保留 7 天。 */
data class AppSettings(
    val darkMode: DarkModeConfig = DarkModeConfig.SYSTEM,
    val autoSaveEnabled: Boolean = true,
    val snapshotIntervalMs: Long = DEFAULT_SNAPSHOT_INTERVAL_MS,
    val maxSnapshots: Int = DEFAULT_MAX_SNAPSHOTS,
    val retentionDays: Int = DEFAULT_RETENTION_DAYS,
) {
    companion object {
        const val DEFAULT_SNAPSHOT_INTERVAL_MS = 60_000L
        const val DEFAULT_MAX_SNAPSHOTS = 100
        const val DEFAULT_RETENTION_DAYS = 7

        val INTERVAL_OPTIONS_MS = listOf(30_000L, 60_000L, 120_000L, 300_000L)
        val MAX_SNAPSHOTS_OPTIONS = listOf(50, 100, 200, 500)
        val RETENTION_DAYS_OPTIONS = listOf(3, 7, 14, 30)
    }

    fun resolveDarkTheme(isSystemInDarkTheme: Boolean): Boolean =
        when (darkMode) {
            DarkModeConfig.SYSTEM -> isSystemInDarkTheme
            DarkModeConfig.ON -> true
            DarkModeConfig.OFF -> false
        }
}
