package com.example.gpstest.data.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.gpstest.domain.model.SatelliteHistorySnapshot
import com.example.gpstest.domain.util.HistoryCsvExporter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 将历史快照写入 cache 并通过 FileProvider 分享 CSV。
 */
object HistoryExportHelper {
    private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun shareCsv(
        context: Context,
        snapshots: List<SatelliteHistorySnapshot>,
        chooserTitle: String,
    ): Boolean {
        if (snapshots.isEmpty()) return false
        return try {
            val uri = writeCsvToCache(context, snapshots)
            val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "GPS Test History ${fileNameFormat.format(Date())}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            val chooser = Intent.createChooser(shareIntent, chooserTitle)
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun writeCsvToCache(
        context: Context,
        snapshots: List<SatelliteHistorySnapshot>,
    ): Uri {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val fileName = "gps_history_${fileNameFormat.format(Date())}.csv"
        val file = File(exportDir, fileName)
        file.writeText(HistoryCsvExporter.toCsv(snapshots), Charsets.UTF_8)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }
}
