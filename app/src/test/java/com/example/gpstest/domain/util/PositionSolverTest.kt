package com.example.gpstest.domain.util

import com.example.gpstest.domain.model.EcefCoordinate
import com.example.gpstest.domain.model.PositionSolutionStatus
import com.example.gpstest.domain.model.PseudorangeObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionSolverTest {
    private val expectedPosition = EcefCoordinate(1_113_194.907, -4_841_695.486, 3_985_350.0)

    private val fourExactObservations =
        listOf(
            PseudorangeObservation(
                satellitePosition = EcefCoordinate(15_600_000.0, 7_540_000.0, 20_140_000.0),
                pseudorangeMeters = 25_065_426.60064276,
                uncertaintyMeters = 3.0,
            ),
            PseudorangeObservation(
                satellitePosition = EcefCoordinate(18_760_000.0, 2_750_000.0, 18_610_000.0),
                pseudorangeMeters = 24_226_318.21429625,
                uncertaintyMeters = 3.0,
            ),
            PseudorangeObservation(
                satellitePosition = EcefCoordinate(17_610_000.0, -14_630_000.0, 13_480_000.0),
                pseudorangeMeters = 21_485_861.001434412,
                uncertaintyMeters = 3.0,
            ),
            PseudorangeObservation(
                satellitePosition = EcefCoordinate(19_170_000.0, 610_000.0, -18_390_000.0),
                pseudorangeMeters = 29_347_248.10178901,
                uncertaintyMeters = 3.0,
            ),
        )

    private val sixExactObservations =
        fourExactObservations +
            listOf(
                PseudorangeObservation(
                    satellitePosition = EcefCoordinate(1_780_000.0, -18_000_000.0, 19_400_000.0),
                    pseudorangeMeters = 20_360_494.0166108,
                    uncertaintyMeters = 3.0,
                ),
                PseudorangeObservation(
                    satellitePosition = EcefCoordinate(-14_500_000.0, 17_200_000.0, 15_400_000.0),
                    pseudorangeMeters = 29_406_592.990645483,
                    uncertaintyMeters = 3.0,
                ),
            )

    @Test
    fun `reports insufficient observations when fewer than four are supplied`() {
        val observation =
            PseudorangeObservation(
                satellitePosition = EcefCoordinate(15_600_000.0, 7_540_000.0, 20_140_000.0),
                pseudorangeMeters = 25_000_000.0,
                uncertaintyMeters = 3.0,
            )

        val result = PositionSolver.solve(List(3) { observation })

        assertEquals(PositionSolutionStatus.INSUFFICIENT_OBSERVATIONS, result.status)
        assertNull(result.receiverPosition)
        assertNull(result.receiverClockBiasMeters)
        assertNull(result.weightedResidualRmsMeters)
    }

    @Test
    fun `solves an exact four satellite ECEF scenario`() {
        val result = PositionSolver.solve(fourExactObservations)

        assertEquals(PositionSolutionStatus.AVAILABLE, result.status)
        assertEquals(expectedPosition.xMeters, result.receiverPosition!!.xMeters, 0.01)
        assertEquals(expectedPosition.yMeters, result.receiverPosition!!.yMeters, 0.01)
        assertEquals(expectedPosition.zMeters, result.receiverPosition!!.zMeters, 0.01)
        assertEquals(82_500.0, result.receiverClockBiasMeters!!, 0.01)
        assertEquals(0.0, result.weightedResidualRmsMeters!!, 0.01)
        assertEquals(4, result.usedObservationCount)
    }

    @Test
    fun `reports invalid observation for non finite or non positive observation fields`() {
        val invalidObservations =
            listOf(
                fourExactObservations[0].copy(pseudorangeMeters = Double.NaN),
                fourExactObservations[0].copy(pseudorangeMeters = Double.POSITIVE_INFINITY),
                fourExactObservations[0].copy(pseudorangeMeters = Double.NEGATIVE_INFINITY),
                fourExactObservations[0].copy(pseudorangeMeters = 0.0),
                fourExactObservations[0].copy(pseudorangeMeters = -1.0),
                fourExactObservations[0].copy(uncertaintyMeters = Double.NaN),
                fourExactObservations[0].copy(uncertaintyMeters = Double.POSITIVE_INFINITY),
                fourExactObservations[0].copy(uncertaintyMeters = Double.NEGATIVE_INFINITY),
                fourExactObservations[0].copy(uncertaintyMeters = 0.0),
                fourExactObservations[0].copy(uncertaintyMeters = -1.0),
                fourExactObservations[0].copy(
                    satellitePosition = fourExactObservations[0].satellitePosition.copy(xMeters = Double.NaN),
                ),
                fourExactObservations[0].copy(
                    satellitePosition = fourExactObservations[0].satellitePosition.copy(yMeters = Double.POSITIVE_INFINITY),
                ),
                fourExactObservations[0].copy(
                    satellitePosition = fourExactObservations[0].satellitePosition.copy(zMeters = Double.NEGATIVE_INFINITY),
                ),
            )

        invalidObservations.forEach { invalidObservation ->
            val result = PositionSolver.solve(fourExactObservations.drop(1) + invalidObservation)

            assertEquals(PositionSolutionStatus.INVALID_OBSERVATION, result.status)
            assertNull(result.receiverPosition)
            assertNull(result.receiverClockBiasMeters)
            assertNull(result.weightedResidualRmsMeters)
        }
    }

    @Test
    fun `reports invalid initial position when it contains a non finite coordinate`() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { invalidX ->
            val result =
                PositionSolver.solve(
                    observations = fourExactObservations,
                    initialPosition = EcefCoordinate(invalidX, 0.0, 0.0),
                )

            assertEquals(PositionSolutionStatus.INVALID_INITIAL_POSITION, result.status)
            assertNull(result.receiverPosition)
            assertNull(result.receiverClockBiasMeters)
            assertNull(result.weightedResidualRmsMeters)
        }
    }

    @Test
    fun `solves an exact six satellite ECEF scenario`() {
        val result = PositionSolver.solve(sixExactObservations)

        assertEquals(PositionSolutionStatus.AVAILABLE, result.status)
        assertEquals(expectedPosition.xMeters, result.receiverPosition!!.xMeters, 0.01)
        assertEquals(expectedPosition.yMeters, result.receiverPosition!!.yMeters, 0.01)
        assertEquals(expectedPosition.zMeters, result.receiverPosition!!.zMeters, 0.01)
        assertEquals(82_500.0, result.receiverClockBiasMeters!!, 0.01)
        assertEquals(0.0, result.weightedResidualRmsMeters!!, 0.01)
        assertEquals(6, result.usedObservationCount)
    }

    @Test
    fun `suppresses a high uncertainty pseudorange outlier`() {
        val weightedOutlierObservations =
            sixExactObservations.dropLast(1) +
                sixExactObservations.last().copy(
                    pseudorangeMeters = 29_407_592.990645483,
                    uncertaintyMeters = 10_000.0,
                )

        val result = PositionSolver.solve(weightedOutlierObservations)

        assertEquals(PositionSolutionStatus.AVAILABLE, result.status)
        assertEquals(expectedPosition.xMeters, result.receiverPosition!!.xMeters, 0.01)
        assertEquals(expectedPosition.yMeters, result.receiverPosition!!.yMeters, 0.01)
        assertEquals(expectedPosition.zMeters, result.receiverPosition!!.zMeters, 0.01)
        assertEquals(82_500.0, result.receiverClockBiasMeters!!, 0.01)
        assertEquals(0.134164030433, result.weightedResidualRmsMeters!!, 0.01)
    }

    @Test
    fun `converges from a poor initial ECEF position`() {
        val result =
            PositionSolver.solve(
                observations = sixExactObservations,
                initialPosition = EcefCoordinate(-6_000_000.0, 4_000_000.0, 3_000_000.0),
            )

        assertEquals(PositionSolutionStatus.AVAILABLE, result.status)
        assertEquals(expectedPosition.xMeters, result.receiverPosition!!.xMeters, 0.01)
        assertEquals(expectedPosition.yMeters, result.receiverPosition!!.yMeters, 0.01)
        assertEquals(expectedPosition.zMeters, result.receiverPosition!!.zMeters, 0.01)
        assertEquals(82_500.0, result.receiverClockBiasMeters!!, 0.01)
    }

    @Test
    fun `reports singular geometry for repeated satellite positions`() {
        val result = PositionSolver.solve(List(4) { fourExactObservations.first() })

        assertEquals(PositionSolutionStatus.SINGULAR_GEOMETRY, result.status)
        assertNull(result.receiverPosition)
        assertNull(result.receiverClockBiasMeters)
        assertNull(result.weightedResidualRmsMeters)
    }

    @Test
    fun `reports did not converge when iteration limit is exhausted`() {
        val result =
            PositionSolver.solve(
                observations = sixExactObservations,
                initialPosition = EcefCoordinate(-6_000_000.0, 4_000_000.0, 3_000_000.0),
                maxIterations = 1,
            )

        assertEquals(PositionSolutionStatus.DID_NOT_CONVERGE, result.status)
        assertNull(result.receiverPosition)
        assertNull(result.receiverClockBiasMeters)
        assertNull(result.weightedResidualRmsMeters)
        assertEquals(1, result.iterationCount)
    }

    @Test
    fun `keeps finite weights and RMS for extremely small finite uncertainties`() {
        val extremelyPreciseObservations =
            sixExactObservations
                .map { observation -> observation.copy(uncertaintyMeters = Double.MIN_VALUE) }
                .toMutableList()
        extremelyPreciseObservations[5] =
            extremelyPreciseObservations[5].copy(
                pseudorangeMeters = extremelyPreciseObservations[5].pseudorangeMeters + 1.0,
            )

        val result = PositionSolver.solve(extremelyPreciseObservations)

        assertEquals(PositionSolutionStatus.AVAILABLE, result.status)
        assertTrue(result.weightedResidualRmsMeters!!.isFinite())
        assertTrue(result.weightedResidualRmsMeters > 0.0)
    }

    @Test
    fun `clamps a negative internal iteration limit to zero`() {
        val result =
            PositionSolver.solve(
                observations = fourExactObservations,
                initialPosition = EcefCoordinate.ZERO,
                maxIterations = -1,
            )

        assertEquals(PositionSolutionStatus.DID_NOT_CONVERGE, result.status)
        assertEquals(0, result.iterationCount)
    }
}
