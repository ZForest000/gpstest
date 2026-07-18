package com.example.gpstest.data.local

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ExternalGpsEphemerisStoreTest {
    @Test
    fun `builds BKG mixed BRDC URL using UTC year and day of year`() {
        val url = ExternalGpsEphemerisStore.downloadUrl(LocalDate.of(2026, 7, 18))

        assertEquals(
            "https://igs.bkg.bund.de/root_ftp/IGS/BRDC/2026/199/BRDC00WRD_R_20261990000_01D_MN.rnx.gz",
            url,
        )
    }
}
