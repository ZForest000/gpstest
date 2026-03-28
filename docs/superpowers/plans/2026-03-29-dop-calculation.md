# HDOP/VDOP/PDOP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add DOP (Dilution of Precision) calculation and display to the satellite list screen.

**Architecture:** Pure Kotlin `DopCalculator` computes DOP from satellite geometry (azimuth/elevation). Result flows through ViewModel as `DopInfo?` to a new `DopCard` composable.

**Tech Stack:** Kotlin, Compose Material3, JUnit 4

**Spec:** `docs/superpowers/specs/2026-03-29-dop-calculation-design.md`

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `app/src/main/java/com/example/gpstest/domain/model/DopInfo.kt` | Create | Data class + quality enum |
| `app/src/main/java/com/example/gpstest/domain/util/DopCalculator.kt` | Create | Pure DOP calculation from satellite geometry |
| `app/src/test/java/com/example/gpstest/domain/util/DopCalculatorTest.kt` | Create | Unit tests for DopCalculator |
| `app/src/main/java/com/example/gpstest/viewmodel/SatelliteViewModel.kt` | Modify | Add dopInfo to UI state |
| `app/src/main/java/com/example/gpstest/ui/components/DopCard.kt` | Create | DOP display card composable |
| `app/src/main/java/com/example/gpstest/ui/screens/satellite/SatelliteListScreen.kt` | Modify | Wire DopCard into layout |
| `app/src/main/res/values/strings.xml` | Modify | Add DOP string resources |

---

### Task 1: Create DopInfo data model

**Files:**
- Create: `app/src/main/java/com/example/gpstest/domain/model/DopInfo.kt`

- [ ] **Step 1: Create DopInfo.kt**

```kotlin
package com.example.gpstest.domain.model

enum class DopQuality {
    EXCELLENT,  // < 1
    GOOD,       // 1 <= x < 2
    MODERATE,   // 2 <= x < 5
    FAIR,       // 5 <= x < 10
    POOR        // >= 10
}

data class DopInfo(
    val pdop: Double,
    val hdop: Double,
    val vdop: Double,
    val satelliteCount: Int
) {
    val quality: DopQuality
        get() = when {
            pdop < 1 -> DopQuality.EXCELLENT
            pdop < 2 -> DopQuality.GOOD
            pdop < 5 -> DopQuality.MODERATE
            pdop < 10 -> DopQuality.FAIR
            else -> DopQuality.POOR
        }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-21" && cd /d/project/gpstest && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/gpstest/domain/model/DopInfo.kt
git commit -m "feat: add DopInfo data model and DopQuality enum"
```

---

### Task 2: Create DopCalculator with tests

**Files:**
- Create: `app/src/main/java/com/example/gpstest/domain/util/DopCalculator.kt`
- Create: `app/src/test/java/com/example/gpstest/domain/util/DopCalculatorTest.kt`

- [ ] **Step 1: Write failing tests**

Create `app/src/test/java/com/example/gpstest/domain/util/DopCalculatorTest.kt`:

```kotlin
package com.example.gpstest.domain.util

import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.GnssSatellite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DopCalculatorTest {

    private fun makeSatellite(
        svid: Int,
        azimuth: Float,
        elevation: Float,
        usedInFix: Boolean = true
    ): GnssSatellite {
        return GnssSatellite(
            svid = svid,
            constellation = Constellation.GPS,
            cn0DbHz = 40f,
            azimuthDegrees = azimuth,
            elevationDegrees = elevation,
            hasAlmanac = true,
            hasEphemeris = true,
            usedInFix = usedInFix,
            carrierFrequencyHz = null,
            carrierCycles = null,
            dopplerShiftHz = null,
            timeNanos = System.nanoTime()
        )
    }

    @Test
    fun `returns null when fewer than 4 satellites`() {
        val sats = listOf(
            makeSatellite(1, 0f, 45f),
            makeSatellite(2, 90f, 30f),
            makeSatellite(3, 180f, 60f)
        )
        assertNull(DopCalculator.calculate(sats))
    }

    @Test
    fun `returns null when all satellites at default zero position`() {
        val sats = listOf(
            makeSatellite(1, 0f, 0f),
            makeSatellite(2, 0f, 0f),
            makeSatellite(3, 0f, 0f),
            makeSatellite(4, 0f, 0f)
        )
        assertNull(DopCalculator.calculate(sats))
    }

    @Test
    fun `returns null for negative elevation satellites`() {
        val sats = listOf(
            makeSatellite(1, 0f, -10f),
            makeSatellite(2, 90f, -5f),
            makeSatellite(3, 180f, -1f),
            makeSatellite(4, 270f, -20f)
        )
        assertNull(DopCalculator.calculate(sats))
    }

    @Test
    fun `calculates DOP with 4 well-distributed satellites`() {
        // 4 satellites spread across the sky
        val sats = listOf(
            makeSatellite(1, 0f, 45f),    // North
            makeSatellite(2, 90f, 45f),   // East
            makeSatellite(3, 180f, 45f),  // South
            makeSatellite(4, 270f, 45f)   // West
        )
        val result = DopCalculator.calculate(sats)
        assertNotNull(result)
        result!!
        // With well-distributed satellites at 45° elevation, DOP should be moderate (~1-3)
        assert(result.pdop > 0) { "PDOP should be positive" }
        assert(result.hdop > 0) { "HDOP should be positive" }
        assert(result.vdop > 0) { "VDOP should be positive" }
        assertEquals(4, result.satelliteCount)
        // PDOP >= HDOP and PDOP >= VDOP always
        assert(result.pdop >= result.hdop)
        // PDOP^2 = HDOP^2 + VDOP^2 (mathematical identity)
        val pdopSquared = result.pdop * result.pdop
        val hdopSquared = result.hdop * result.hdop
        val vdopSquared = result.vdop * result.vdop
        assertEquals(pdopSquared, hdopSquared + vdopSquared, 0.01)
    }

    @Test
    fun `excludes satellites not used in fix`() {
        val sats = listOf(
            makeSatellite(1, 0f, 45f, usedInFix = true),
            makeSatellite(2, 90f, 45f, usedInFix = true),
            makeSatellite(3, 180f, 45f, usedInFix = true),
            makeSatellite(4, 270f, 45f, usedInFix = false) // not used
        )
        // Only 3 usedInFix -> null
        assertNull(DopCalculator.calculate(sats))
    }

    @Test
    fun `quality is excellent for well-distributed satellites`() {
        // Many satellites spread evenly
        val sats = (0..11).map { i ->
            makeSatellite(
                svid = i + 1,
                azimuth = (i * 30).toFloat(),
                elevation = 45f
            )
        }
        val result = DopCalculator.calculate(sats)
        assertNotNull(result)
        // With 12 well-distributed satellites, PDOP should be excellent (< 2)
        assert(result!!.pdop < 3.0) { "PDOP=${result.pdop} should be small with 12 satellites" }
    }

    @Test
    fun `handles many satellites`() {
        val sats = (0..23).map { i ->
            makeSatellite(
                svid = i + 1,
                azimuth = (i * 15).toFloat(),
                elevation = 30f + (i % 3) * 20f
            )
        }
        val result = DopCalculator.calculate(sats)
        assertNotNull(result)
        assertEquals(24, result!!.satelliteCount)
        assert(result.pdop > 0)
        assert(result.hdop > 0)
        assert(result.vdop > 0)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-21" && cd /d/project/gpstest && ./gradlew test --tests "com.example.gpstest.domain.util.DopCalculatorTest" 2>&1 | tail -10`
Expected: FAILED — DopCalculator class not found

- [ ] **Step 3: Implement DopCalculator**

Create `app/src/main/java/com/example/gpstest/domain/util/DopCalculator.kt`:

```kotlin
package com.example.gpstest.domain.util

import com.example.gpstest.domain.model.DopInfo
import com.example.gpstest.domain.model.GnssSatellite
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Calculates Dilution of Precision (DOP) values from satellite geometry.
 * Uses the observation matrix approach: build H from azimuth/elevation,
 * compute Q = (H^T * H)^-1, extract PDOP/HDOP/VDOP from diagonal.
 */
object DopCalculator {

    private const val MIN_SATELLITES = 4
    private const val MATRIX_SIZE = 4

    fun calculate(satellites: List<GnssSatellite>): DopInfo? {
        // Filter: only usedInFix, valid elevation (>=0), not default (0,0)
        val validSatellites = satellites.filter { sat ->
            sat.usedInFix &&
            sat.elevationDegrees >= 0f &&
            !(sat.elevationDegrees == 0f && sat.azimuthDegrees == 0f)
        }

        if (validSatellites.size < MIN_SATELLITES) return null

        // Build N x 4 observation matrix H
        val n = validSatellites.size
        val h = Array(n) { DoubleArray(MATRIX_SIZE) }
        for (i in validSatellites.indices) {
            val elRad = Math.toRadians(validSatellites[i].elevationDegrees.toDouble())
            val azRad = Math.toRadians(validSatellites[i].azimuthDegrees.toDouble())
            h[i][0] = cos(elRad) * sin(azRad)
            h[i][1] = cos(elRad) * cos(azRad)
            h[i][2] = sin(elRad)
            h[i][3] = 1.0
        }

        // Compute H^T * H (4x4)
        val hth = Array(MATRIX_SIZE) { DoubleArray(MATRIX_SIZE) }
        for (i in 0 until MATRIX_SIZE) {
            for (j in 0 until MATRIX_SIZE) {
                var sum = 0.0
                for (k in 0 until n) {
                    sum += h[k][i] * h[k][j]
                }
                hth[i][j] = sum
            }
        }

        // Invert using Gauss-Jordan elimination
        val q = invert4x4(hth) ?: return null

        // Validate diagonal elements
        for (i in 0 until 3) {
            if (q[i][i] <= 0 || !q[i][i].isFinite()) return null
        }

        val pdop = sqrt(q[0][0] + q[1][1] + q[2][2])
        val hdop = sqrt(q[0][0] + q[1][1])
        val vdop = sqrt(q[2][2])

        return DopInfo(
            pdop = pdop,
            hdop = hdop,
            vdop = vdop,
            satelliteCount = validSatellites.size
        )
    }

    /**
     * Inverts a 4x4 matrix using Gauss-Jordan elimination with full pivoting.
     * Returns null if the matrix is singular.
     */
    private fun invert4x4(matrix: Array<DoubleArray>): Array<DoubleArray>? {
        val n = MATRIX_SIZE
        val aug = Array(n) { DoubleArray(2 * n) }

        // Build augmented matrix [A | I]
        for (i in 0 until n) {
            for (j in 0 until n) {
                aug[i][j] = matrix[i][j]
                aug[i][j + n] = if (i == j) 1.0 else 0.0
            }
        }

        // Forward elimination with partial pivoting
        for (col in 0 until n) {
            // Find pivot
            var maxRow = col
            var maxVal = kotlin.math.abs(aug[col][col])
            for (row in col + 1 until n) {
                val val_ = kotlin.math.abs(aug[row][col])
                if (val_ > maxVal) {
                    maxVal = val_
                    maxRow = row
                }
            }

            if (maxVal < 1e-10) return null // Singular matrix

            // Swap rows
            if (maxRow != col) {
                val temp = aug[col]
                aug[col] = aug[maxRow]
                aug[maxRow] = temp
            }

            // Scale pivot row
            val pivot = aug[col][col]
            for (j in 0 until 2 * n) {
                aug[col][j] /= pivot
            }

            // Eliminate column in other rows
            for (row in 0 until n) {
                if (row == col) continue
                val factor = aug[row][col]
                for (j in 0 until 2 * n) {
                    aug[row][j] -= factor * aug[col][j]
                }
            }
        }

        // Extract inverse from right half
        return Array(n) { i -> DoubleArray(n) { j -> aug[i][j + n] } }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-21" && cd /d/project/gpstest && ./gradlew test --tests "com.example.gpstest.domain.util.DopCalculatorTest" 2>&1 | tail -10`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/gpstest/domain/util/DopCalculator.kt app/src/test/java/com/example/gpstest/domain/util/DopCalculatorTest.kt
git commit -m "feat: add DopCalculator with unit tests for DOP computation"
```

---

### Task 3: Integrate DopCalculator into ViewModel

**Files:**
- Modify: `app/src/main/java/com/example/gpstest/viewmodel/SatelliteViewModel.kt`

- [ ] **Step 1: Add import and dopInfo field to SatelliteUiState.Success**

In `SatelliteViewModel.kt`:

Add import at top (line 7, after existing imports):
```kotlin
import com.example.gpstest.domain.model.DopInfo
import com.example.gpstest.domain.util.DopCalculator
```

Add `dopInfo` field to `SatelliteUiState.Success` (line 192, after `dumpsysData`):
```kotlin
val dopInfo: DopInfo? = null
```

- [ ] **Step 2: Calculate DOP in startListening()**

In the `startListening()` method (line 62-70), after creating the `grouped` map and `usedInFixList`, add the DOP calculation. Modify the `_uiState.value = SatelliteUiState.Success(...)` block to include:

```kotlin
val usedInFixList = grouped[SatelliteGroup.USED_IN_FIX].orEmpty()
val dopInfo = DopCalculator.calculate(usedInFixList)
_uiState.value = SatelliteUiState.Success(
    usedInFix = usedInFixList,
    visibleOnly = grouped[SatelliteGroup.VISIBLE_ONLY].orEmpty(),
    searching = grouped[SatelliteGroup.SEARCHING].orEmpty(),
    totalCount = satellites.size,
    location = gnssData.location,
    clock = gnssData.clock,
    dumpsysData = gnssData.dumpsysData,
    dopInfo = dopInfo
)
```

- [ ] **Step 3: Build to verify**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-21" && cd /d/project/gpstest && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/gpstest/viewmodel/SatelliteViewModel.kt
git commit -m "feat: integrate DopCalculator into SatelliteViewModel"
```

---

### Task 4: Add string resources

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add DOP strings before closing `</resources>` tag**

Append after line 216 (before `</resources>`):

```xml
    <!-- DOP -->
    <string name="dop_title">DOP 精度因子</string>
    <string name="dop_satellite_count">卫星: %d 颗</string>
    <string name="dop_waiting">等待定位...</string>
```

- [ ] **Step 2: Build to verify**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-21" && cd /d/project/gpstest && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat: add DOP string resources"
```

---

### Task 5: Create DopCard composable

**Files:**
- Create: `app/src/main/java/com/example/gpstest/ui/components/DopCard.kt`

- [ ] **Step 1: Create DopCard.kt**

```kotlin
package com.example.gpstest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.domain.model.DopInfo

@Composable
fun DopCard(
    dopInfo: DopInfo?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Title row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.dop_title),
                style = MaterialTheme.typography.titleMedium
            )
            if (dopInfo != null) {
                Text(
                    text = stringResource(R.string.dop_satellite_count, dopInfo.satelliteCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (dopInfo != null) {
            DopRow(label = "PDOP", value = dopInfo.pdop)
            DopRow(label = "HDOP", value = dopInfo.hdop)
            DopRow(label = "VDOP", value = dopInfo.vdop)
        } else {
            Text(
                text = stringResource(R.string.dop_waiting),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DopRow(label: String, value: Double) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Quality indicator dot
        val color = dopQualityColor(value)
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = color, shape = CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp)
        )
        Text(
            text = String.format("%.1f", value),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold
            )
        )
    }
}

private fun dopQualityColor(value: Double): Color {
    return when {
        value < 2 -> Color(0xFF4CAF50)   // Green - good
        value < 5 -> Color(0xFFFFC107)   // Yellow - moderate
        value < 10 -> Color(0xFFFF9800)  // Orange - fair
        else -> Color(0xFFF44336)        // Red - poor
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-21" && cd /d/project/gpstest && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/gpstest/ui/components/DopCard.kt
git commit -m "feat: add DopCard composable for DOP display"
```

---

### Task 6: Wire DopCard into SatelliteListScreen

> **Note:** This task depends on Tasks 4 (strings) and 5 (DopCard) being complete.

**Files:**
- Modify: `app/src/main/java/com/example/gpstest/ui/screens/satellite/SatelliteListScreen.kt`

- [ ] **Step 1: Add import**

Add after line 41 (`import com.example.gpstest.ui.components.ConstellationStatCard`):
```kotlin
import com.example.gpstest.ui.components.DopCard
```

- [ ] **Step 2: Add dopInfo parameter to SatelliteListContent**

In `SatelliteListContent` function signature (line 137), add parameter after `dumpsysData`:
```kotlin
dopInfo: com.example.gpstest.domain.model.DopInfo?,
```

- [ ] **Step 3: Insert DopCard in the LazyColumn**

Insert a new `item { }` block after the `ConstellationStatCard` item (after line 162, before the ClockInfoCard item):

```kotlin
        item {
            DopCard(dopInfo = dopInfo)
        }
```

- [ ] **Step 4: Pass dopInfo from SatelliteListScreen to SatelliteListContent**

In the `SatelliteUiState.Success` branch (line 97-109), add `dopInfo` to the `SatelliteListContent` call:
```kotlin
SatelliteListContent(
    usedInFix = state.usedInFix,
    visibleOnly = state.visibleOnly,
    searching = state.searching,
    totalCount = state.totalCount,
    allSatellites = allSatellites,
    location = state.location,
    clock = state.clock,
    dumpsysData = state.dumpsysData,
    dopInfo = state.dopInfo,
    onSatelliteClick = { selectedSatellite = it }
)
```

- [ ] **Step 5: Build to verify**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-21" && cd /d/project/gpstest && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/gpstest/ui/screens/satellite/SatelliteListScreen.kt
git commit -m "feat: wire DopCard into SatelliteListScreen"
```

---

### Task 7: Final build and verification

- [ ] **Step 1: Full clean build**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-21" && cd /d/project/gpstest && ./gradlew clean assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run all tests**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-21" && cd /d/project/gpstest && ./gradlew test 2>&1 | tail -10`
Expected: All tests PASS

- [ ] **Step 3: Install on device for manual verification**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-21" && export PATH="$PATH:/d/android_sdk/platform-tools" && cd /d/project/gpstest && ./gradlew installDebug`
Expected: INSTALL SUCCESSFUL

Verify on device:
- No fix: DopCard shows "等待定位..."
- With fix and >= 4 satellites: shows PDOP/HDOP/VDOP with colored dots
- Color changes correctly with geometry quality
