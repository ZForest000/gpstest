package com.example.gpstest.data.source

import com.example.gpstest.domain.model.AGpsStatus

interface AGpsDataSource {
    suspend fun injectXtraData(data: ByteArray): Result<Unit>
    suspend fun injectXtraFromUrl(url: String): Result<Unit>
    suspend fun injectTime(timeMillis: Long): Result<Unit>
    suspend fun clearApsData(): Result<Unit>
    suspend fun checkStatus(): AGpsStatus
    fun isSupported(): Boolean
}
