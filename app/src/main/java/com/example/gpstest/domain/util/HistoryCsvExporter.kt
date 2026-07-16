package com.example.gpstest.domain.util

import com.example.gpstest.domain.model.SatelliteHistorySnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 将历史快照导出为 CSV 文本。
 * 包含两段：快照摘要表 + 卫星明细表，便于 Excel/Sheets 分析。
 */
object HistoryCsvExporter {
    private val isoFormat =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    fun toCsv(snapshots: List<SatelliteHistorySnapshot>): String {
        val sb = StringBuilder()
        sb.appendLine("# GPS Test History Export")
        sb.appendLine("# Generated: ${isoFormat.format(Date())}")
        sb.appendLine("# Snapshot count: ${snapshots.size}")
        sb.appendLine()

        sb.appendLine("# === Snapshot Summary ===")
        sb.appendLine(
            listOf(
                "timestamp_iso",
                "timestamp_ms",
                "used_in_fix",
                "visible",
                "total_entries",
                "avg_cn0_dbhz",
                "latitude",
                "longitude",
                "accuracy_m",
                "pdop",
                "hdop",
                "vdop",
                "ttff_ms",
            ).joinToString(","),
        )

        // 导出按时间正序（旧→新），便于图表工具
        val ordered = snapshots.sortedBy { it.timestamp }
        for (snapshot in ordered) {
            val entries = snapshot.getEntries()
            sb.appendLine(
                listOf(
                    escape(isoFormat.format(Date(snapshot.timestamp))),
                    snapshot.timestamp.toString(),
                    snapshot.usedInFixCount.toString(),
                    snapshot.visibleCount.toString(),
                    entries.size.toString(),
                    formatFloat(snapshot.averageSignalStrength),
                    formatNullable(snapshot.latitude),
                    formatNullable(snapshot.longitude),
                    formatNullable(snapshot.accuracy),
                    formatNullable(snapshot.pdop),
                    formatNullable(snapshot.hdop),
                    formatNullable(snapshot.vdop),
                    formatNullable(snapshot.ttffMs),
                ).joinToString(","),
            )
        }

        sb.appendLine()
        sb.appendLine("# === Satellite Entries ===")
        sb.appendLine(
            listOf(
                "snapshot_timestamp_ms",
                "constellation",
                "svid",
                "cn0_dbhz",
                "used_in_fix",
            ).joinToString(","),
        )

        for (snapshot in ordered) {
            for (entry in snapshot.getEntries()) {
                sb.appendLine(
                    listOf(
                        snapshot.timestamp.toString(),
                        escape(entry.constellationName),
                        entry.svid.toString(),
                        formatFloat(entry.cn0DbHz),
                        entry.usedInFix.toString(),
                    ).joinToString(","),
                )
            }
        }

        return sb.toString()
    }

    /** 单条快照的 CSV（含该时刻卫星明细）。 */
    fun toCsv(snapshot: SatelliteHistorySnapshot): String = toCsv(listOf(snapshot))

    private fun formatFloat(value: Float): String = String.format(Locale.US, "%.2f", value)

    private fun formatNullable(value: Double?): String =
        if (value == null) {
            ""
        } else {
            String.format(Locale.US, "%.6f", value)
        }

    private fun formatNullable(value: Float?): String =
        if (value == null) {
            ""
        } else {
            String.format(Locale.US, "%.2f", value)
        }

    private fun formatNullable(value: Long?): String = value?.toString() ?: ""

    private fun escape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
}
