package com.example.gpstest.data.local

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.gpstest.domain.export.RinexEpoch
import com.example.gpstest.domain.export.RinexHeader
import com.example.gpstest.domain.export.RinexWriter
import com.example.gpstest.domain.model.LocationInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

object RinexExportHelper {
    private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun share(
        context: Context,
        epochs: List<RinexEpoch>,
        location: LocationInfo?,
        chooserTitle: String,
    ): Boolean {
        if (epochs.isEmpty()) return false
        return try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, "gnss_${fileNameFormat.format(Date())}.obs")
            file.writeText(
                RinexWriter.write(
                    header = RinexHeader(approximatePositionXyz = location?.toEcef()),
                    epochs = epochs,
                ),
                Charsets.US_ASCII,
            )
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, file.name)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            context.startActivity(Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun LocationInfo.toEcef(): DoubleArray {
        val a = 6_378_137.0
        val e2 = 6.69437999014e-3
        val lat = Math.toRadians(latitude)
        val lon = Math.toRadians(longitude)
        val n = a / kotlin.math.sqrt(1 - e2 * sin(lat) * sin(lat))
        return doubleArrayOf(
            (n + altitude) * cos(lat) * cos(lon),
            (n + altitude) * cos(lat) * sin(lon),
            (n * (1 - e2) + altitude) * sin(lat),
        )
    }
}
