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
     * Inverts a 4x4 matrix using Gauss-Jordan elimination with partial pivoting.
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
