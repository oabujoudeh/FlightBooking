package com.flightbooking

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [FlightDAO].
 *
 * Covers [FlightDAO.searchFlights], [FlightDAO.searchConnectingFlights],
 * [FlightDAO.getFlightOverview], and [FlightDAO.getAvailableFlights].
 *
 * Seed data assumptions (from the project SQLite database):
 * - Leeds → Amsterdam flights exist on 2025-10-26 (e.g. flight_id 15, economy £55).
 * - Leeds → London direct flights exist across the timetable.
 * - Flight ID 62 is Cancelled (London → Leeds).
 */
class FlightDAOTest {
    private val seedDate = LocalDate.of(2025, 10, 26)

    // ── searchFlights: basic results ───────────────────────────────────────────

    /**
     * Verifies that [FlightDAO.searchFlights] returns at least one result for a
     * known route on a seeded date.
     */
    @Test
    fun testSearchFlightsKnownRouteReturnsList() {
        val results = FlightDAO.searchFlights("Leeds", "Amsterdam", seedDate)
        assertTrue(results.isNotEmpty(), "Expected flights for Leeds→Amsterdam on $seedDate")
    }

    /**
     * Verifies that the returned [Flight] objects have non-blank city names and a
     * positive duration.
     */
    @Test
    fun testSearchFlightsFieldsArePopulated() {
        val flights = FlightDAO.searchFlights("Leeds", "Amsterdam", seedDate)
        for (f in flights) {
            assertTrue(f.departureCity.isNotBlank(), "departureCity must not be blank")
            assertTrue(f.arrivalCity.isNotBlank(), "arrivalCity must not be blank")
            assertTrue(f.durationMinutes > 0, "durationMinutes must be positive")
            assertTrue(f.flightNumber.isNotBlank(), "flightNumber must not be blank")
        }
    }

    /**
     * Verifies that results are sorted by departure time (ascending), matching the
     * `ORDER BY r.planned_departure ASC` clause in the query.
     */
    @Test
    fun testSearchFlightsResultsAreSortedByDepartureTime() {
        val flights = FlightDAO.searchFlights("Leeds", "London", seedDate)
        if (flights.size > 1) {
            for (i in 0 until flights.size - 1) {
                assertTrue(
                    flights[i].departureTime <= flights[i + 1].departureTime,
                    "Flights must be sorted ascending by departure time",
                )
            }
        }
    }

    /**
     * Verifies that cancelled flights are excluded from results.
     * Flight 62 (London→Leeds, 2025-10-26) is seeded as Cancelled.
     */
    @Test
    fun testSearchFlightsExcludesCancelledFlights() {
        val flights = FlightDAO.searchFlights("London", "Leeds", seedDate)
        val ids = flights.map { it.flightId }
        assertFalse(ids.contains(62), "Cancelled flight 62 must not appear in results")
    }

    /**
     * Verifies that an unknown route returns an empty list rather than null or an exception.
     */
    @Test
    fun testSearchFlightsUnknownRouteReturnsEmptyList() {
        val results = FlightDAO.searchFlights("Leeds", "Tokyo", seedDate)
        assertTrue(results.isEmpty(), "Non-existent route must return empty list")
    }

    /**
     * Verifies that searching by IATA airport ID (e.g. "LBA" for Leeds Bradford)
     * returns the same results as searching by city name "Leeds".
     */
    @Test
    fun testSearchFlightsByIataCodeMatchesCitySearch() {
        val byCity = FlightDAO.searchFlights("Leeds", "Amsterdam", seedDate)
        val byCode = FlightDAO.searchFlights("LBA", "AMS", seedDate)
        assertEquals(byCity.size, byCode.size, "IATA code search must return same count as city search")
    }

    /**
     * Verifies that searching on a date with no flights returns an empty list.
     */
    @Test
    fun testSearchFlightsFarFutureDateReturnsEmptyList() {
        val results = FlightDAO.searchFlights("Leeds", "Amsterdam", LocalDate.of(9999, 1, 1))
        assertTrue(results.isEmpty(), "Far-future date must return no flights")
    }

    // ── searchFlights: SQL injection resistance ────────────────────────────────

    /**
     * Security test — `OR '1'='1` injection in the departure field.
     *
     * The prepared statement must treat the payload as a literal city name; no
     * real city matches it, so the result must be empty.
     */
    @Test
    fun testSearchFlightsOrInjectionInDepartureReturnsEmpty() {
        val results = FlightDAO.searchFlights("' OR '1'='1", "Amsterdam", seedDate)
        assertTrue(results.isEmpty(), "OR injection in departure must not widen results")
    }

    /**
     * Security test — DROP TABLE injection in the arrival field must not destroy
     * the flights table. Subsequent calls must still succeed.
     */
    @Test
    fun testSearchFlightsDropTableInjectionDoesNotDestroyTable() {
        FlightDAO.searchFlights("Leeds", "'; DROP TABLE flights; --", seedDate)
        val stillWorks =
            try {
                FlightDAO.searchFlights("Leeds", "Amsterdam", seedDate)
                true
            } catch (e: Exception) {
                false
            }
        assertTrue(stillWorks, "flights table must survive a DROP TABLE injection attempt")
    }

    // ── searchConnectingFlights ────────────────────────────────────────────────

    /**
     * Verifies that [FlightDAO.searchConnectingFlights] returns a list without throwing,
     * even when no connections exist.
     */
    @Test
    fun testSearchConnectingFlightsDoesNotThrow() {
        val threw =
            try {
                FlightDAO.searchConnectingFlights("Leeds", "Edinburgh", seedDate)
                false
            } catch (e: Exception) {
                true
            }
        assertFalse(threw, "searchConnectingFlights must not throw for any valid input")
    }

    /**
     * Verifies that each [ConnectingFlight] in the result has a layover within the
     * default [45, 480] minute window.
     */
    @Test
    fun testSearchConnectingFlightsLayoverIsWithinDefaultBounds() {
        val connections = FlightDAO.searchConnectingFlights("Leeds", "Dublin", seedDate)
        for (c in connections) {
            assertTrue(c.layoverMinutes >= 45, "Layover must be at least 45 min, got ${c.layoverMinutes}")
            assertTrue(c.layoverMinutes <= 480, "Layover must be at most 480 min, got ${c.layoverMinutes}")
        }
    }

    /**
     * Verifies that [ConnectingFlight.totalDurationMinutes] equals the sum of both
     * legs' durations plus the layover.
     */
    @Test
    fun testSearchConnectingFlightsTotalDurationIsConsistent() {
        val connections = FlightDAO.searchConnectingFlights("Leeds", "Dublin", seedDate)
        for (c in connections) {
            val expected = c.leg1.durationMinutes + c.layoverMinutes + c.leg2.durationMinutes
            assertEquals(expected, c.totalDurationMinutes, "totalDurationMinutes must equal leg1 + layover + leg2")
        }
    }

    /**
     * Verifies that a zero-width layover window (min > max) returns no connections.
     */
    @Test
    fun testSearchConnectingFlightsImpossibleLayoverWindowReturnsEmpty() {
        val results = FlightDAO.searchConnectingFlights("Leeds", "Amsterdam", seedDate, minLayover = 500, maxLayover = 10)
        assertTrue(results.isEmpty(), "Impossible layover window must return no connections")
    }

    /**
     * Verifies that an unknown destination returns an empty list.
     */
    @Test
    fun testSearchConnectingFlightsUnknownDestinationReturnsEmptyList() {
        val results = FlightDAO.searchConnectingFlights("Leeds", "Atlantis", seedDate)
        assertTrue(results.isEmpty(), "Unknown destination must return empty list")
    }

    // ── getFlightOverview ──────────────────────────────────────────────────────

    /**
     * Verifies that [FlightDAO.getFlightOverview] returns a non-null [Flight] for a
     * known seeded flight ID.
     */
    @Test
    fun testGetFlightOverviewKnownIdReturnsNonNull() {
        val flight = FlightDAO.getFlightOverview(15)
        assertNotNull(flight, "Flight 15 must exist in the database")
    }

    /**
     * Verifies that the returned [Flight] carries the correct city names for flight 15
     * (Leeds → Amsterdam).
     */
    @Test
    fun testGetFlightOverviewReturnsCorrectCities() {
        val flight = FlightDAO.getFlightOverview(15)!!
        assertEquals("Leeds", flight.departureCity)
        assertEquals("Amsterdam", flight.arrivalCity)
    }

    /**
     * Verifies that a non-existent flight ID returns null.
     */
    @Test
    fun testGetFlightOverviewInvalidIdReturnsNull() {
        assertNull(FlightDAO.getFlightOverview(-1), "Non-existent ID must return null")
    }

    /**
     * Verifies that the [Flight.departureDate] parsed from the database matches the
     * seeded date for flight 15.
     */
    @Test
    fun testGetFlightOverviewDepartureDateIsCorrect() {
        val flight = FlightDAO.getFlightOverview(15)!!
        assertEquals(seedDate, flight.departureDate)
    }

    /**
     * Verifies that [Flight.durationMinutes] is positive for a real flight.
     */
    @Test
    fun testGetFlightOverviewDurationIsPositive() {
        val flight = FlightDAO.getFlightOverview(15)!!
        assertTrue(flight.durationMinutes > 0, "duration must be positive")
    }

    /**
     * Verifies that [Flight.arrivalDayOffset] is non-negative (arrival is never
     * before departure).
     */
    @Test
    fun testGetFlightOverviewArrivalDayOffsetIsNonNegative() {
        val flight = FlightDAO.getFlightOverview(15)!!
        assertTrue(flight.arrivalDayOffset >= 0, "arrivalDayOffset must be >= 0")
    }

    // ── getAvailableFlights ────────────────────────────────────────────────────

    /**
     * Verifies that [FlightDAO.getAvailableFlights] returns a non-empty list for a
     * known route on a seeded date.
     */
    @Test
    fun testGetAvailableFlightsReturnsNonEmptyList() {
        val results = FlightDAO.getAvailableFlights("Leeds", "Amsterdam", seedDate)
        assertTrue(results.isNotEmpty(), "Expected results for Leeds→Amsterdam on $seedDate")
    }

    /**
     * Verifies that all returned [FlightDisplayDTO] objects have [FlightDisplayDTO.isConnecting]
     * set to false when the route has no connecting options.
     */
    @Test
    fun testGetAvailableFlightsDirectFlightsAreNotMarkedConnecting() {
        val results = FlightDAO.getAvailableFlights("Leeds", "Amsterdam", seedDate)
        val directOnly = results.filter { !it.isConnecting }
        assertTrue(directOnly.isNotEmpty(), "Direct flights must be present and marked isConnecting=false")
    }

    /**
     * Verifies that [FlightDisplayDTO.totalDurationDisplay] follows the "Xh Ym" format
     * for direct flights (e.g. "1h 30m").
     */
    @Test
    fun testGetAvailableFlightsDurationDisplayFormat() {
        val results = FlightDAO.getAvailableFlights("Leeds", "Amsterdam", seedDate)
        val direct = results.first { !it.isConnecting }
        assertTrue(
            direct.totalDurationDisplay.matches(Regex("\\d+h \\d+m")),
            "Duration display must match 'Xh Ym' format, got '${direct.totalDurationDisplay}'",
        )
    }

    /**
     * Verifies that the combined list from [FlightDAO.getAvailableFlights] is sorted
     * by departure time, as documented.
     */
    @Test
    fun testGetAvailableFlightsResultsAreSortedByDepartureTime() {
        val results = FlightDAO.getAvailableFlights("Leeds", "Amsterdam", seedDate)
        if (results.size > 1) {
            for (i in 0 until results.size - 1) {
                assertTrue(
                    results[i].departureTime <= results[i + 1].departureTime,
                    "getAvailableFlights must return results sorted by departure time",
                )
            }
        }
    }

    /**
     * Verifies that an unknown route returns an empty list without throwing.
     */
    @Test
    fun testGetAvailableFlightsUnknownRouteReturnsEmptyList() {
        val results = FlightDAO.getAvailableFlights("Leeds", "Atlantis", seedDate)
        assertTrue(results.isEmpty(), "Unknown route must return empty list")
    }

    /**
     * Verifies that connecting [FlightDisplayDTO] entries encode the flight ID as
     * "leg1Id_leg2Id" and set [FlightDisplayDTO.isConnecting] to true.
     */
    @Test
    fun testGetAvailableFlightsConnectingDtoEncoding() {
        val results = FlightDAO.getAvailableFlights("Leeds", "Dublin", seedDate)
        val connecting = results.filter { it.isConnecting }
        for (dto in connecting) {
            assertTrue(dto.flightId.contains("_"), "Connecting flight ID must contain '_', got '${dto.flightId}'")
        }
    }
}
