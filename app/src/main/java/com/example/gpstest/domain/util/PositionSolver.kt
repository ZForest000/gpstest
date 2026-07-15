package com.example.gpstest.domain.util

import com.example.gpstest.domain.model.EcefCoordinate
import com.example.gpstest.domain.model.PositionSolution
import com.example.gpstest.domain.model.PositionSolutionStatus
import com.example.gpstest.domain.model.PseudorangeObservation
import kotlin.math.abs
import kotlin.math.sqrt

object PositionSolver {
    private const val MIN_OBSERVATIONS = 4
    private const val STATE_SIZE = 4
    private const val MAX_ITERATIONS = 20
    private const val POSITION_CONVERGENCE_METERS = 0.001
    private const val CLOCK_BIAS_CONVERGENCE_METERS = 0.001
    private const val PIVOT_TOLERANCE = 1e-12

    fun solve(
        observations: List<PseudorangeObservation>,
        initialPosition: EcefCoordinate = EcefCoordinate.ZERO,
    ): PositionSolution = solve(observations, initialPosition, MAX_ITERATIONS)

    internal fun solve(
        observations: List<PseudorangeObservation>,
        initialPosition: EcefCoordinate,
        maxIterations: Int,
    ): PositionSolution {
        if (observations.size < MIN_OBSERVATIONS) {
            return PositionSolution(status = PositionSolutionStatus.INSUFFICIENT_OBSERVATIONS)
        }
        if (!initialPosition.isFinite()) {
            return PositionSolution(status = PositionSolutionStatus.INVALID_INITIAL_POSITION)
        }
        if (observations.any { !it.isValid() }) {
            return PositionSolution(status = PositionSolutionStatus.INVALID_OBSERVATION)
        }

        val referenceUncertaintyMeters = observations.minOf { it.uncertaintyMeters }
        val iterationLimit = maxIterations.coerceAtLeast(0)
        var receiverPosition = initialPosition
        var receiverClockBiasMeters = 0.0

        for (iteration in 1..iterationLimit) {
            val normalMatrix = Array(STATE_SIZE) { DoubleArray(STATE_SIZE) }
            val normalVector = DoubleArray(STATE_SIZE)

            for (observation in observations) {
                val deltaX = receiverPosition.xMeters - observation.satellitePosition.xMeters
                val deltaY = receiverPosition.yMeters - observation.satellitePosition.yMeters
                val deltaZ = receiverPosition.zMeters - observation.satellitePosition.zMeters
                val geometricRange = sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)
                if (!geometricRange.isFinite() || geometricRange <= 0.0) {
                    return PositionSolution(
                        usedObservationCount = observations.size,
                        iterationCount = iteration - 1,
                        status = PositionSolutionStatus.SINGULAR_GEOMETRY,
                    )
                }

                val residual = observation.pseudorangeMeters - (geometricRange + receiverClockBiasMeters)
                if (!residual.isFinite()) {
                    return PositionSolution(
                        usedObservationCount = observations.size,
                        iterationCount = iteration - 1,
                        status = PositionSolutionStatus.DID_NOT_CONVERGE,
                    )
                }

                val weight = relativeWeight(observation.uncertaintyMeters, referenceUncertaintyMeters)
                val h =
                    doubleArrayOf(
                        deltaX / geometricRange,
                        deltaY / geometricRange,
                        deltaZ / geometricRange,
                        1.0,
                    )

                for (row in 0 until STATE_SIZE) {
                    normalVector[row] += weight * h[row] * residual
                    for (column in 0 until STATE_SIZE) {
                        normalMatrix[row][column] += weight * h[row] * h[column]
                    }
                }
            }

            val stateDelta =
                solve4x4(normalMatrix, normalVector)
                    ?: return PositionSolution(
                        usedObservationCount = observations.size,
                        iterationCount = iteration - 1,
                        status = PositionSolutionStatus.SINGULAR_GEOMETRY,
                    )

            receiverPosition =
                EcefCoordinate(
                    xMeters = receiverPosition.xMeters + stateDelta[0],
                    yMeters = receiverPosition.yMeters + stateDelta[1],
                    zMeters = receiverPosition.zMeters + stateDelta[2],
                )
            receiverClockBiasMeters += stateDelta[3]

            if (!receiverPosition.xMeters.isFinite() ||
                !receiverPosition.yMeters.isFinite() ||
                !receiverPosition.zMeters.isFinite() ||
                !receiverClockBiasMeters.isFinite()
            ) {
                return PositionSolution(
                    usedObservationCount = observations.size,
                    iterationCount = iteration,
                    status = PositionSolutionStatus.DID_NOT_CONVERGE,
                )
            }

            val positionDeltaMeters = sqrt(stateDelta[0] * stateDelta[0] + stateDelta[1] * stateDelta[1] + stateDelta[2] * stateDelta[2])
            if (positionDeltaMeters <= POSITION_CONVERGENCE_METERS &&
                abs(stateDelta[3]) <= CLOCK_BIAS_CONVERGENCE_METERS
            ) {
                return PositionSolution(
                    receiverPosition = receiverPosition,
                    receiverClockBiasMeters = receiverClockBiasMeters,
                    weightedResidualRmsMeters =
                        calculateWeightedResidualRms(
                            observations = observations,
                            receiverPosition = receiverPosition,
                            receiverClockBiasMeters = receiverClockBiasMeters,
                            referenceUncertaintyMeters = referenceUncertaintyMeters,
                        ),
                    usedObservationCount = observations.size,
                    iterationCount = iteration,
                    status = PositionSolutionStatus.AVAILABLE,
                )
            }
        }

        return PositionSolution(
            usedObservationCount = observations.size,
            iterationCount = iterationLimit,
            status = PositionSolutionStatus.DID_NOT_CONVERGE,
        )
    }

    private fun calculateWeightedResidualRms(
        observations: List<PseudorangeObservation>,
        receiverPosition: EcefCoordinate,
        receiverClockBiasMeters: Double,
        referenceUncertaintyMeters: Double,
    ): Double {
        var weightedSquaredResidualSum = 0.0
        var weightSum = 0.0

        for (observation in observations) {
            val deltaX = receiverPosition.xMeters - observation.satellitePosition.xMeters
            val deltaY = receiverPosition.yMeters - observation.satellitePosition.yMeters
            val deltaZ = receiverPosition.zMeters - observation.satellitePosition.zMeters
            val geometricRange = sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)
            val residual = observation.pseudorangeMeters - (geometricRange + receiverClockBiasMeters)
            val weight = relativeWeight(observation.uncertaintyMeters, referenceUncertaintyMeters)
            weightedSquaredResidualSum += weight * residual * residual
            weightSum += weight
        }

        return sqrt(weightedSquaredResidualSum / weightSum)
    }

    private fun relativeWeight(
        uncertaintyMeters: Double,
        referenceUncertaintyMeters: Double,
    ): Double {
        // A common weight scale preserves both the WLS solution and normalized residual RMS.
        val uncertaintyRatio = referenceUncertaintyMeters / uncertaintyMeters
        return uncertaintyRatio * uncertaintyRatio
    }

    private fun solve4x4(
        matrix: Array<DoubleArray>,
        vector: DoubleArray,
    ): DoubleArray? {
        val augmented = Array(STATE_SIZE) { DoubleArray(STATE_SIZE + 1) }
        var matrixScale = 0.0

        for (row in 0 until STATE_SIZE) {
            for (column in 0 until STATE_SIZE) {
                val value = matrix[row][column]
                if (!value.isFinite()) return null
                augmented[row][column] = value
                matrixScale = maxOf(matrixScale, abs(value))
            }
            if (!vector[row].isFinite()) return null
            augmented[row][STATE_SIZE] = vector[row]
        }

        if (matrixScale == 0.0) return null

        for (column in 0 until STATE_SIZE) {
            var pivotRow = column
            var pivotMagnitude = abs(augmented[column][column])
            for (row in column + 1 until STATE_SIZE) {
                val candidateMagnitude = abs(augmented[row][column])
                if (candidateMagnitude > pivotMagnitude) {
                    pivotMagnitude = candidateMagnitude
                    pivotRow = row
                }
            }

            if (!pivotMagnitude.isFinite() || pivotMagnitude <= matrixScale * PIVOT_TOLERANCE) return null

            if (pivotRow != column) {
                val temporaryRow = augmented[column]
                augmented[column] = augmented[pivotRow]
                augmented[pivotRow] = temporaryRow
            }

            val pivot = augmented[column][column]
            for (index in column..STATE_SIZE) {
                augmented[column][index] /= pivot
            }

            for (row in 0 until STATE_SIZE) {
                if (row == column) continue
                val factor = augmented[row][column]
                for (index in column..STATE_SIZE) {
                    augmented[row][index] -= factor * augmented[column][index]
                }
            }
        }

        return DoubleArray(STATE_SIZE) { row -> augmented[row][STATE_SIZE] }
    }

    private fun EcefCoordinate.isFinite(): Boolean = xMeters.isFinite() && yMeters.isFinite() && zMeters.isFinite()

    private fun PseudorangeObservation.isValid(): Boolean =
        satellitePosition.isFinite() &&
            pseudorangeMeters.isFinite() &&
            pseudorangeMeters > 0.0 &&
            uncertaintyMeters.isFinite() &&
            uncertaintyMeters > 0.0
}
