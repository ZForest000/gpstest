package com.example.gpstest.data.source

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * dumpsys location GNSS 节解析结果。
 * @property avgBasebandCn0 所有跟踪卫星的平均基带 CN0（需 root/Shizuku 访问）
 * @property measurementCount 每秒原始 GNSS 测量值数量
 * @property usedInFixConstellations 参与定位解算的星座列表
 */
data class DumpsysGnssData(
    val avgBasebandCn0: Float?,
    val measurementCount: Int,
    val usedInFixConstellations: List<String>,
)

/**
 * Shizuku 权限辅助工具。
 *
 * Shizuku 提供系统级 API 访问（通过 ADB 或 root），用于读取 dumpsys GNSS 诊断数据。
 * 此为可选功能：无 Shizuku 时应用功能完整，但失去基带 CN0 和测量计数统计。
 *
 * 根模式（[isRootMode] = true，UID 0）有完全访问权限；
 * 非根 Shizuku 仅有 ADB 级别权限。
 */
object ShizukuHelper {
    val isShizukuAvailable: Boolean
        get() =
            try {
                Shizuku.pingBinder()
            } catch (e: Exception) {
                false
            }

    val isPermissionGranted: Boolean
        get() =
            try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (e: Exception) {
                false
            }

    val isRootMode: Boolean
        get() =
            try {
                Shizuku.getUid() == 0
            } catch (e: Exception) {
                false
            }
}
