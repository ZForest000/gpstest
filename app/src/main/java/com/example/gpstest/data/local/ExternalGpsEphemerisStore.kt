package com.example.gpstest.data.local

import android.content.Context
import com.example.gpstest.domain.ephemeris.GpsBroadcastEphemeris
import com.example.gpstest.domain.ephemeris.RinexGpsNavigationParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream

enum class ExternalEphemerisSource {
    DOWNLOAD,
    CACHE,
    UNAVAILABLE,
}

data class ExternalGpsEphemerisResult(
    val ephemerides: List<GpsBroadcastEphemeris>,
    val source: ExternalEphemerisSource,
    val message: String? = null,
)

interface ExternalGpsEphemerisProvider {
    fun load(now: Instant = Instant.now()): ExternalGpsEphemerisResult
}

/** 下载、缓存并解析 BKG BRDC GPS-only RINEX 导航文件。 */
class ExternalGpsEphemerisStore(
    context: Context,
    private val client: OkHttpClient = OkHttpClient(),
) : ExternalGpsEphemerisProvider {
    private val cacheDirectory = File(context.filesDir, "ephemeris").apply { mkdirs() }

    override fun load(now: Instant): ExternalGpsEphemerisResult {
        val today = now.atZone(ZoneOffset.UTC).toLocalDate()
        val fresh = cacheFile(today).takeIf { it.isFile && now.toEpochMilli() - it.lastModified() < CACHE_MAX_AGE_MS }
        parseFile(fresh)?.let { return ExternalGpsEphemerisResult(it, ExternalEphemerisSource.CACHE) }

        listOf(today, today.minusDays(1)).forEach { date ->
            download(date)?.let { bytes ->
                val target = cacheFile(date)
                target.writeBytes(bytes)
                parseFile(target)?.let { return ExternalGpsEphemerisResult(it, ExternalEphemerisSource.DOWNLOAD) }
                target.delete()
            }
        }

        cacheDirectory.listFiles()?.sortedByDescending(File::lastModified)?.forEach { file ->
            parseFile(file)?.let { return ExternalGpsEphemerisResult(it, ExternalEphemerisSource.CACHE, "使用过期缓存") }
        }
        return ExternalGpsEphemerisResult(emptyList(), ExternalEphemerisSource.UNAVAILABLE, "无法获取 GPS 广播星历")
    }

    private fun download(date: LocalDate): ByteArray? =
        runCatching {
            val request = Request.Builder().url(downloadUrl(date)).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.bytes()
            }
        }.getOrNull()

    private fun parseFile(file: File?): List<GpsBroadcastEphemeris>? {
        if (file == null || !file.isFile) return null
        return runCatching {
            GZIPInputStream(file.inputStream()).bufferedReader().use { reader ->
                RinexGpsNavigationParser.parse(reader.readText()).takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }

    private fun cacheFile(date: LocalDate): File = File(cacheDirectory, "gps_${date.format(DATE_KEY)}.rnx.gz")

    companion object {
        private const val CACHE_MAX_AGE_MS = 6 * 60 * 60 * 1000L
        private val DATE_KEY = DateTimeFormatter.ofPattern("yyyyDDD")

        fun downloadUrl(date: LocalDate): String {
            val year = date.year
            val day = "%03d".format(date.dayOfYear)
            return "https://igs.bkg.bund.de/root_ftp/IGS/BRDC/$year/$day/BRDC00WRD_R_${year}${day}0000_01D_MN.rnx.gz"
        }
    }
}
