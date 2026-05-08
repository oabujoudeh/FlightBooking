package com.flightbooking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [SeatDAO].
 *
 * Only [SeatDAO.getOrGenerateSeats] is public; all tests go through that entry
 * point. The suite covers:
 * - Seat counts derived from known [AircraftConfigs] layouts.
 * - Seat-class distribution (business / economy / first).
 * - Seat-number format for single-deck and multi-deck (M/U prefix) aircraft.
 * - Idempotency: calling the function twice must not duplicate seats.
 * - On-demand generation: a flight with no pre-existing seats gets them created.
 * - All seats start unoccupied.
 *
 * Seed data used:
 * - Flight 16819 — Airbus A320, seats already generated (170 total).
 * - Flight 16839 — Boeing 777, seats already generated (360 total).
 * - Flight 17501 — Airbus A380 (multi-deck), seats already generated (510 total).
 * - Flight 1     — Airbus A320, no seats in the DB; used to exercise generation.
 */
class SeatDAOTest {

    // ── Airbus A320: seat count and class split ────────────────────────────────

    /**
     * Verifies that [SeatDAO.getOrGenerateSeats] returns the correct total for an
     * Airbus A320 layout:
     * - Business rows 1–5, 2-2 layout → 4 seats/row × 5 rows = 20
     * - Economy rows 6–30, 3-3 layout → 6 seats/row × 25 rows = 150
     * - Total = 170
     */
    @Test
    fun testA320TotalSeatCount() {
        val seats = SeatDAO.getOrGenerateSeats(16819, "Airbus A320")
        assertEquals(170, seats.size, "Airbus A320 must have exactly 170 seats")
    }

    /**
     * Verifies the business-cabin count for an Airbus A320 (expected: 20).
     */
    @Test
    fun testA320BusinessSeatCount() {
        val seats = SeatDAO.getOrGenerateSeats(16819, "Airbus A320")
        val business = seats.count { it.seatClass == "business" }
        assertEquals(20, business, "Airbus A320 must have 20 business seats")
    }

    /**
     * Verifies the economy-cabin count for an Airbus A320 (expected: 150).
     */
    @Test
    fun testA320EconomySeatCount() {
        val seats = SeatDAO.getOrGenerateSeats(16819, "Airbus A320")
        val economy = seats.count { it.seatClass == "economy" }
        assertEquals(150, economy, "Airbus A320 must have 150 economy seats")
    }

    /**
     * Verifies that the Airbus A320 has no first-class seats.
     */
    @Test
    fun testA320HasNoFirstClassSeats() {
        val seats = SeatDAO.getOrGenerateSeats(16819, "Airbus A320")
        val first = seats.count { it.seatClass == "first" }
        assertEquals(0, first, "Airbus A320 must have 0 first-class seats")
    }

    // ── Boeing 777: seat count and class split ─────────────────────────────────

    /**
     * Verifies the total seat count for a Boeing 777 layout:
     * - First rows 1–4, 1-2-1 → 4/row × 4 = 16
     * - Business rows 5–13, 2-2-2 → 6/row × 9 = 54
     * - Economy rows 14–42, 3-4-3 → 10/row × 29 = 290
     * - Total = 360
     */
    @Test
    fun testB777TotalSeatCount() {
        val seats = SeatDAO.getOrGenerateSeats(16839, "Boeing 777")
        assertEquals(360, seats.size, "Boeing 777 must have exactly 360 seats")
    }

    /**
     * Verifies the first-class seat count for a Boeing 777 (expected: 16).
     */
    @Test
    fun testB777FirstClassSeatCount() {
        val seats = SeatDAO.getOrGenerateSeats(16839, "Boeing 777")
        assertEquals(16, seats.count { it.seatClass == "first" })
    }

    /**
     * Verifies the business-class seat count for a Boeing 777 (expected: 54).
     */
    @Test
    fun testB777BusinessSeatCount() {
        val seats = SeatDAO.getOrGenerateSeats(16839, "Boeing 777")
        assertEquals(54, seats.count { it.seatClass == "business" })
    }

    /**
     * Verifies the economy-class seat count for a Boeing 777 (expected: 290).
     */
    @Test
    fun testB777EconomySeatCount() {
        val seats = SeatDAO.getOrGenerateSeats(16839, "Boeing 777")
        assertEquals(290, seats.count { it.seatClass == "economy" })
    }

    // ── Airbus A380: multi-deck seat counts and prefix format ──────────────────

    /**
     * Verifies the total seat count for an Airbus A380 across both decks:
     * - Main Deck: First(20) + Business(60) + Economy(350) = 430
     * - Upper Deck: Business rows 1–20, 2-2 → 4/row × 20 = 80
     * - Total = 510
     */
    @Test
    fun testA380TotalSeatCount() {
        val seats = SeatDAO.getOrGenerateSeats(17501, "Airbus A380")
        assertEquals(510, seats.size, "Airbus A380 must have exactly 510 seats")
    }

    /**
     * Verifies that main-deck seats use the "M" prefix (e.g. "M1A").
     */
    @Test
    fun testA380MainDeckSeatsHaveMPrefix() {
        val seats = SeatDAO.getOrGenerateSeats(17501, "Airbus A380")
        val mainSeats = seats.filter { it.seatNumber.startsWith("M") }
        assertEquals(430, mainSeats.size, "A380 must have 430 main-deck seats prefixed with 'M'")
    }

    /**
     * Verifies that upper-deck seats use the "U" prefix (e.g. "U1A").
     */
    @Test
    fun testA380UpperDeckSeatsHaveUPrefix() {
        val seats = SeatDAO.getOrGenerateSeats(17501, "Airbus A380")
        val upperSeats = seats.filter { it.seatNumber.startsWith("U") }
        assertEquals(80, upperSeats.size, "A380 must have 80 upper-deck seats prefixed with 'U'")
    }

    /**
     * Verifies that every A380 seat number starts with either "M" or "U"
     * (no unprefixed seats on a multi-deck aircraft).
     */
    @Test
    fun testA380AllSeatsHaveDeckPrefix() {
        val seats = SeatDAO.getOrGenerateSeats(17501, "Airbus A380")
        for (seat in seats) {
            assertTrue(
                seat.seatNumber.startsWith("M") || seat.seatNumber.startsWith("U"),
                "Every A380 seat must start with M or U, got '${seat.seatNumber}'"
            )
        }
    }

    // ── Single-deck seat number format ─────────────────────────────────────────

    /**
     * Verifies that single-deck A320 seat numbers are NOT prefixed with "M" or "U".
     * The deck prefix is only applied when the aircraft has more than one deck.
     */
    @Test
    fun testA320SeatsHaveNoDeckPrefix() {
        val seats = SeatDAO.getOrGenerateSeats(16819, "Airbus A320")
        for (seat in seats) {
            assertFalse(
                seat.seatNumber.startsWith("M") || seat.seatNumber.startsWith("U"),
                "Single-deck A320 seats must not have a deck prefix, got '${seat.seatNumber}'"
            )
        }
    }

    /**
     * Verifies that seat numbers follow the row-number + column-letter pattern
     * (e.g. "1A", "12F") for a single-deck aircraft.
     */
    @Test
    fun testA320SeatNumberFormat() {
        val seats = SeatDAO.getOrGenerateSeats(16819, "Airbus A320")
        val pattern = Regex("^\\d+[A-Z]$")
        for (seat in seats) {
            assertTrue(
                pattern.matches(seat.seatNumber),
                "A320 seat number '${seat.seatNumber}' must match pattern \\d+[A-Z]"
            )
        }
    }

    // ── All seats start unoccupied ─────────────────────────────────────────────

    /**
     * Verifies that every seat returned for an A320 flight has [Seat.isOccupied]
     * set to false on initial generation.
     */
    @Test
    fun testGeneratedSeatsAreAllUnoccupied() {
        val seats = SeatDAO.getOrGenerateSeats(16819, "Airbus A320")
        val occupied = seats.count { it.isOccupied }
        assertEquals(0, occupied, "All generated seats must start as unoccupied")
    }

    // ── Seat model fields are populated ───────────────────────────────────────

    /**
     * Verifies that every returned [Seat] has a positive [Seat.seatId], the correct
     * [Seat.flightId], a non-blank [Seat.seatNumber], and a non-blank [Seat.seatClass].
     */
    @Test
    fun testSeatFieldsAreFullyPopulated() {
        val seats = SeatDAO.getOrGenerateSeats(16819, "Airbus A320")
        for (seat in seats) {
            assertTrue(seat.seatId > 0, "seatId must be positive")
            assertEquals(16819, seat.flightId, "flightId must match the requested flight")
            assertTrue(seat.seatNumber.isNotBlank(), "seatNumber must not be blank")
            assertTrue(seat.seatClass.isNotBlank(), "seatClass must not be blank")
        }
    }

    /**
     * Verifies that [Seat.seatClass] values are restricted to the known set:
     * "economy", "business", or "first".
     */
    @Test
    fun testSeatClassValuesAreValid() {
        val valid = setOf("economy", "business", "first")
        val seats = SeatDAO.getOrGenerateSeats(16839, "Boeing 777")
        for (seat in seats) {
            assertTrue(
                seat.seatClass in valid,
                "seatClass must be one of $valid, got '${seat.seatClass}'"
            )
        }
    }

    // ── Seat numbers are unique within a flight ────────────────────────────────

    /**
     * Verifies that all seat numbers within a single flight are distinct.
     * Duplicate seat numbers would indicate a generation bug.
     */
    @Test
    fun testSeatNumbersAreUniquePerFlight() {
        val seats = SeatDAO.getOrGenerateSeats(16819, "Airbus A320")
        val numbers = seats.map { it.seatNumber }
        assertEquals(numbers.size, numbers.distinct().size, "Seat numbers must be unique within a flight")
    }

    /**
     * Verifies seat-number uniqueness for the A380 multi-deck configuration,
     * where the M/U prefix prevents collisions between decks.
     */
    @Test
    fun testA380SeatNumbersAreUniqueAcrossDecks() {
        val seats = SeatDAO.getOrGenerateSeats(17501, "Airbus A380")
        val numbers = seats.map { it.seatNumber }
        assertEquals(numbers.size, numbers.distinct().size, "A380 seat numbers must be unique across both decks")
    }

    // ── Idempotency ────────────────────────────────────────────────────────────

    /**
     * Verifies that calling [SeatDAO.getOrGenerateSeats] twice for the same flight
     * returns the same count both times (seats are not duplicated on the second call).
     */
    @Test
    fun testGetOrGenerateSeatsIsIdempotent() {
        val first = SeatDAO.getOrGenerateSeats(16819, "Airbus A320").size
        val second = SeatDAO.getOrGenerateSeats(16819, "Airbus A320").size
        assertEquals(first, second, "Calling getOrGenerateSeats twice must not duplicate seats")
    }

    // ── On-demand generation ───────────────────────────────────────────────────

    /**
     * Verifies that [SeatDAO.getOrGenerateSeats] generates seats on the fly for
     * flight 1 (Airbus A320, not pre-seeded in the DB). After the call the
     * returned list must contain the full 170-seat A320 layout.
     *
     * Subsequent runs of this test are safe: [SeatDAO.getOrGenerateSeats] checks
     * [seatsExist] before generating, so it is idempotent.
     */
    @Test
    fun testSeatsAreGeneratedOnDemandForUnseededFlight() {
        val seats = SeatDAO.getOrGenerateSeats(1, "Airbus A320")
        assertEquals(170, seats.size, "On-demand generation must produce 170 seats for an A320")
    }

}
