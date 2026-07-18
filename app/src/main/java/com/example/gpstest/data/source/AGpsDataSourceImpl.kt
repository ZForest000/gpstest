package com.example.gpstest.data.source

import android.content.Context
import android.location.LocationManager
import android.os.Bundle
import com.example.gpstest.domain.model.AGpsStatus
import com.example.gpstest.domain.model.DataStatus
import java.io.IOException
import timber.log.Timber

class AGpsDataSourceImpl(
    private val context: Context,
) : AGpsDataSource {
    companion object {
        private const val TAG = "AGpsDataSource"
    }

    private val locationManager: LocationManager?
        get() = context.getSystemService(LocationManager::class.java)

    override suspend fun injectXtraFromUrl(url: String): Result<Unit> {
        Timber.tag(TAG).d("injectXtraFromUrl: url = $url")
        if (url.isBlank()) {
            return Result.failure(IOException("XTRA下载地址为空"))
        }
        val bundle = Bundle().apply { putString("url", url) }
        return sendGpsCommand("force_xtra_injection", bundle)
    }

    override suspend fun injectTime(timeMillis: Long): Result<Unit> {
        Timber.tag(TAG).d("injectTime: timeMillis = $timeMillis")
        return sendGpsCommand("force_time_injection", null)
    }

    override suspend fun clearApsData(): Result<Unit> {
        Timber.tag(TAG).d("clearApsData: clearing A-GPS data")

        val commands =
            listOf(
                "delete_aiding_data",
                "delete_xtra_data",
                "delete_all_data",
            )

        var anySuccess = false
        val errors = mutableListOf<String>()

        for (command in commands) {
            try {
                val result =
                    locationManager?.sendExtraCommand(
                        LocationManager.GPS_PROVIDER,
                        command,
                        null,
                    )
                Timber.tag(TAG).d("clearApsData: command '$command' result: $result")
                if (result == true) {
                    anySuccess = true
                }
            } catch (e: SecurityException) {
                Timber.tag(TAG).e("clearApsData: SecurityException for '$command': ${e.message}")
                errors.add("$command: Permission denied")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "clearApsData: Exception for '$command': ${e.message}")
                errors.add("$command: ${e.message}")
            }
        }

        return if (anySuccess) {
            Timber.tag(TAG).d("clearApsData: At least one command succeeded")
            Result.success(Unit)
        } else {
            Timber.tag(TAG).e("clearApsData: All commands failed: ${errors.joinToString()}")
            Result.failure(Exception("清除命令执行失败，设备可能不支持此功能"))
        }
    }

    override suspend fun checkStatus(): AGpsStatus =
        AGpsStatus(
            timeStatus = DataStatus.UNKNOWN,
            ephemerisStatus = DataStatus.UNKNOWN,
            almanacStatus = DataStatus.UNKNOWN,
            lastUpdateTime = null,
        )

    override fun isSupported(): Boolean = locationManager?.getProvider(LocationManager.GPS_PROVIDER) != null

    private fun sendGpsCommand(
        command: String,
        extras: Bundle?,
    ): Result<Unit> {
        val manager =
            locationManager ?: return Result.failure(
                IllegalStateException("LocationManager不可用"),
            )
        return try {
            val success =
                manager.sendExtraCommand(
                    LocationManager.GPS_PROVIDER,
                    command,
                    extras,
                )
            if (success) {
                Timber.tag(TAG).d("sendGpsCommand: '$command' command sent successfully")
                Result.success(Unit)
            } else {
                val error = "设备拒绝执行命令: $command"
                Timber.tag(TAG).e("sendGpsCommand: $error")
                Result.failure(IOException(error))
            }
        } catch (e: SecurityException) {
            Timber.tag(TAG).e("sendGpsCommand: SecurityException for '$command': ${e.message}")
            Result.failure(Exception("Permission denied: ${e.message}"))
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "sendGpsCommand: Exception for '$command': ${e.message}")
            Result.failure(e)
        }
    }
}
