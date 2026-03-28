package com.example.gpstest.domain.model

data class AGpsStatus(
    val timeStatus: DataStatus = DataStatus.UNKNOWN,
    val ephemerisStatus: DataStatus = DataStatus.UNKNOWN,
    val almanacStatus: DataStatus = DataStatus.UNKNOWN,
    val lastUpdateTime: Long? = null,
    val lastInjectionTime: Long? = null
)

enum class DataStatus {
    VALID,
    EXPIRED,
    PARTIAL,
    MISSING,
    UNKNOWN
}

data class AGpsInjectionRecord(
    val id: String,
    val type: InjectionType,
    val source: InjectionSource,
    val timestamp: Long,
    val success: Boolean,
    val errorMessage: String? = null
)

enum class InjectionType {
    TIME,
    EPHEMERIS,
    ALMANAC,
    XTRA
}

enum class InjectionSource {
    MANUAL,
    AUTO_DOWNLOAD,
    NETWORK
}

data class AGpsSettings(
    val autoUpdateEnabled: Boolean = false,
    val updateIntervalHours: Int = 24,
    val lastAutoUpdateTime: Long? = null,
    val downloadUrl: String = DEFAULT_XTRA_URL
) {
    companion object {
        const val DEFAULT_XTRA_URL = "https://xtrapath1.izatcloud.net/xtra3grc.bin"
    }
}
