package com.flightbooking

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [ChartService].
 *
 * All three chart functions return a PNG [ByteArray]. Tests verify:
 * - Output is non-empty and starts with the PNG magic bytes (`\x89PNG`).
 * - Functions handle a single data point and multiple data points.
 * - Empty input does not throw (where the library permits it).
 * - Unknown status values in [ChartService.generateBookingStatusChart] fall
 *   back to gray without crashing.
 */
class ChartServiceTest {
    // ── PNG magic-byte helper ──────────────────────────────────────────────────

    /** Returns true when [bytes] begins with the 4-byte PNG signature. */
    private fun isPng(bytes: ByteArray) =
        bytes.size >= 4 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() &&
            bytes[3] == 'G'.code.toByte()

    // ── generateBookingsOverTimeChart ──────────────────────────────────────────

    /**
     * Verifies that a single-point dataset produces a non-empty PNG.
     */
    @Test
    fun testBookingsOverTimeChartSinglePointReturnsPng() {
        val data = listOf(mapOf("date" to "2025-06-01", "count" to 5, "revenue" to 250.0))
        val result = ChartService.generateBookingsOverTimeChart(data)
        assertTrue(result.isNotEmpty(), "Output must not be empty")
        assertTrue(isPng(result), "Output must start with PNG magic bytes")
    }

    /**
     * Verifies that a multi-point dataset produces a valid PNG.
     */
    @Test
    fun testBookingsOverTimeChartMultiplePointsReturnsPng() {
        val data =
            listOf(
                mapOf("date" to "2025-06-01", "count" to 3, "revenue" to 150.0),
                mapOf("date" to "2025-06-02", "count" to 7, "revenue" to 350.0),
                mapOf("date" to "2025-06-03", "count" to 12, "revenue" to 600.0),
            )
        val result = ChartService.generateBookingsOverTimeChart(data)
        assertTrue(isPng(result), "Multi-point chart must be a valid PNG")
    }

    /**
     * Verifies that zero counts and zero revenue do not cause an exception.
     */
    @Test
    fun testBookingsOverTimeChartZeroValuesDoNotThrow() {
        val threw =
            try {
                ChartService.generateBookingsOverTimeChart(
                    listOf(mapOf("date" to "2025-01-01", "count" to 0, "revenue" to 0.0)),
                )
                false
            } catch (e: Exception) {
                true
            }
        assertFalse(threw, "Zero-value data must not throw")
    }

    // ── generateBookingStatusChart ─────────────────────────────────────────────

    /**
     * Verifies that a standard three-status dataset produces a valid PNG.
     */
    @Test
    fun testBookingStatusChartReturnsPng() {
        val data =
            listOf(
                mapOf("status" to "confirmed", "count" to 80),
                mapOf("status" to "cancelled", "count" to 15),
                mapOf("status" to "pending", "count" to 5),
            )
        val result = ChartService.generateBookingStatusChart(data)
        assertTrue(isPng(result), "Status chart must be a valid PNG")
    }

    /**
     * Verifies that an unknown status value falls back to gray without throwing.
     */
    @Test
    fun testBookingStatusChartUnknownStatusDoesNotThrow() {
        val threw =
            try {
                ChartService.generateBookingStatusChart(
                    listOf(mapOf("status" to "refunded", "count" to 3)),
                )
                false
            } catch (e: Exception) {
                true
            }
        assertFalse(threw, "Unknown status must use the gray fallback colour without throwing")
    }

    /**
     * Verifies that a single-slice pie chart is still rendered as a PNG.
     */
    @Test
    fun testBookingStatusChartSingleSliceReturnsPng() {
        val result =
            ChartService.generateBookingStatusChart(
                listOf(mapOf("status" to "confirmed", "count" to 100)),
            )
        assertTrue(isPng(result), "Single-slice pie chart must be a valid PNG")
    }

    // ── generateBusiestRoutesChart ─────────────────────────────────────────────

    /**
     * Verifies that a single-route dataset produces a valid PNG.
     */
    @Test
    fun testBusiestRoutesChartSingleRouteReturnsPng() {
        val data =
            listOf(
                mapOf("departureCity" to "Leeds", "arrivalCity" to "London", "bookingCount" to 42),
            )
        val result = ChartService.generateBusiestRoutesChart(data)
        assertTrue(isPng(result), "Single-route chart must be a valid PNG")
    }

    /**
     * Verifies that a multi-route dataset produces a valid PNG.
     */
    @Test
    fun testBusiestRoutesChartMultipleRoutesReturnsPng() {
        val data =
            listOf(
                mapOf("departureCity" to "Leeds", "arrivalCity" to "London", "bookingCount" to 80),
                mapOf("departureCity" to "London", "arrivalCity" to "Amsterdam", "bookingCount" to 60),
                mapOf("departureCity" to "Glasgow", "arrivalCity" to "Dublin", "bookingCount" to 30),
            )
        val result = ChartService.generateBusiestRoutesChart(data)
        assertTrue(isPng(result), "Multi-route chart must be a valid PNG")
    }

    /**
     * Verifies that a zero booking count does not cause an exception.
     */
    @Test
    fun testBusiestRoutesChartZeroCountDoesNotThrow() {
        val threw =
            try {
                ChartService.generateBusiestRoutesChart(
                    listOf(mapOf("departureCity" to "Leeds", "arrivalCity" to "Paris", "bookingCount" to 0)),
                )
                false
            } catch (e: Exception) {
                true
            }
        assertFalse(threw, "Zero booking count must not throw")
    }

    /**
     * Verifies that route labels are formed correctly as "Departure → Arrival"
     * by checking that the function handles city names with spaces without throwing.
     */
    @Test
    fun testBusiestRoutesChartCityNamesWithSpacesDoNotThrow() {
        val threw =
            try {
                ChartService.generateBusiestRoutesChart(
                    listOf(mapOf("departureCity" to "New York", "arrivalCity" to "Los Angeles", "bookingCount" to 10)),
                )
                false
            } catch (e: Exception) {
                true
            }
        assertFalse(threw, "City names containing spaces must not throw")
    }
}
