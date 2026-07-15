package com.example.gpstest.domain.model

data class EcefCoordinate(
    val xMeters: Double,
    val yMeters: Double,
    val zMeters: Double,
) {
    companion object {
        val ZERO = EcefCoordinate(0.0, 0.0, 0.0)
    }
}

data class PseudorangeObservation(
    val satellitePosition: EcefCoordinate,
    val pseudorangeMeters: Double,
    val uncertaintyMeters: Double,
)

enum class PositionSolutionStatus {
    AVAILABLE,
    INSUFFICIENT_OBSERVATIONS,
    INVALID_OBSERVATION,
    INVALID_INITIAL_POSITION,
    SINGULAR_GEOMETRY,
    DID_NOT_CONVERGE,
}

data class PositionSolution(
    val receiverPosition: EcefCoordinate? = null,
    val receiverClockBiasMeters: Double? = null,
    val weightedResidualRmsMeters: Double? = null,
    val usedObservationCount: Int = 0,
    val iterationCount: Int = 0,
    val status: PositionSolutionStatus,
)
