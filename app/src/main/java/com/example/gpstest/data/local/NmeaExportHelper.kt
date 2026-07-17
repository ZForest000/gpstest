package com.example.gpstest.data.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.gpstest.domain.model.NmeaSentence
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 将 NMEA 句子写入 cache 并通过 FileProvider 分享 .nmea 文件。
 */
object NmeaExportHelper {
    private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun shareNmea(
        context: Context,
        sentences: List<NmeaSentence>,
        chooserTitle: String,
    ): Boolean {
        if (sentences.isEmpty()) return false
        return try {
            val uri = writeNmeaToCache(context, sentences)
            val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "NMEA ${fileNameFormat.format(Date())}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            val chooser = Intent.createChooser(shareIntent, chooserTitle)
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun writeNmeaToCache(
        context: Context,
        sentences: List<NmeaSentence>,
    ): Uri {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val fileName = "nmea_${fileNameFormat.format(Date())}.nmea"
        val file = File(exportDir, fileName)
        file.writeText(sentences.joinToString("\n") { it.message }, Charsets.UTF_8)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }
}
