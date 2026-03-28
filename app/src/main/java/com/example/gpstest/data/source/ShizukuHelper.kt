package com.example.gpstest.data.source

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

data class DumpsysGnssData(
    val avgBasebandCn0: Float?,
    val measurementCount: Int,
    val usedInFixConstellations: List<String>
)

object ShizukuHelper {
    private const val REQUEST_CODE = 1001

    val isShizukuAvailable: Boolean
        get() = try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }

    val isPermissionGranted: Boolean
        get() = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }

    val isRootMode: Boolean
        get() = try {
            Shizuku.getUid() == 0
        } catch (e: Exception) {
            false
        }

    fun requestPermission() {
        try {
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                return
            }
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
