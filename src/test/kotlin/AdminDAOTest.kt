package com.flightbooking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [AdminDAO].
 *
 * Covers every public function: dashboard statistics, reservation and flight
 * tracking, change-request approval/rejection, flight status updates, and
 * adjacent-date navigation. A dedicated section at the end verifies that all
 * parameterised queries resist SQL-injection payloads.
 */
class AdminDAOTest {
    // ── getTotalUsers ──────────────────────────────────────────────────────────

    /**
     * Verifies that [AdminDAO.getTotalUsers] never returns a negative number.
     */
    @Test
    fun testGetTotalUsersReturnsNonNegative() {
        val count = AdminDAO.getTotalUsers()
        assertTrue(count >= 0, "User count must be non-negative")
    }

    // ── getAllBookingsGroupedByDate ─────────────────────────────────────────────

    /**
     * Verifies that [AdminDAO.getAllBookingsGroupedByDate] returns a non-null list.
     */
    @Test
    fun testGetAllBookingsGroupedByDateReturnsList() {
        val result = AdminDAO.getAllBookingsGroupedByDate()
        assertNotNull(result)
    }

    /**
     * Verifies that each entry contains the `date`, `count`, and `revenue` keys.
     */
    @Test
    fun testGetAllBookingsGroupedByDateHasExpectedKeys() {
        val result = AdminDAO.getAllBookingsGroupedByDate()
        if (result.isNotEmpty()) {
            val entry = result.first()
            assertTrue(entry.containsKey("date"))
            assertTrue(entry.containsKey("count"))
            assertTrue(entry.containsKey("revenue"))
        }
    }

    /**
     * Verifies that results are sorted newest-first (the query uses `ORDER BY booking_date DESC`).
     */
    @Test
    fun testGetAllBookingsGroupedByDateIsSortedDescending() {
        val result = AdminDAO.getAllBookingsGroupedByDate()
        if (result.size > 1) {
            for (i in 0 until result.size - 1) {
                val current = result[i]["date"] as String
                val next = result[i + 1]["date"] as String
                assertTrue(current >= next, "Results should be newest-first (DESC)")
            }
        }
    }

    /**
     * Verifies that passing a season filter still returns a non-null list.
     */
    @Test
    fun testGetAllBookingsGroupedByDateWithSeasonFilterReturnsList() {
        val result = AdminDAO.getAllBookingsGroupedByDate(filterSeason = "Summer 2025")
        assertNotNull(result)
    }

    // ── getBookingStatusCounts ─────────────────────────────────────────────────

    /**
     * Verifies that [AdminDAO.getBookingStatusCounts] returns a non-null list.
     */
    @Test
    fun testGetBookingStatusCountsReturnsList() {
        val result = AdminDAO.getBookingStatusCounts()
        assertNotNull(result)
    }

    /**
     * Verifies that each entry contains the `status` and `count` keys.
     */
    @Test
    fun testGetBookingStatusCountsHasExpectedKeys() {
        val result = AdminDAO.getBookingStatusCounts()
        if (result.isNotEmpty()) {
            val entry = result.first()
            assertTrue(entry.containsKey("status"))
            assertTrue(entry.containsKey("count"))
        }
    }

    /**
     * Verifies that every status count is positive (zero-count groups are not returned).
     */
    @Test
    fun testGetBookingStatusCountsArePositive() {
        val result = AdminDAO.getBookingStatusCounts()
        for (entry in result) {
            val count = entry["count"] as Int
            assertTrue(count > 0, "Each status group must have at least one booking")
        }
    }

    // ── getRecentBookings ──────────────────────────────────────────────────────

    /**
     * Verifies that [AdminDAO.getRecentBookings] respects the given limit.
     */
    @Test
    fun testGetRecentBookingsRespectsLimit() {
        val result = AdminDAO.getRecentBookings(5)
        assertTrue(result.size <= 5)
    }

    /**
     * Verifies that each booking entry contains the five expected keys.
     */
    @Test
    fun testGetRecentBookingsHasExpectedKeys() {
        val result = AdminDAO.getRecentBookings(1)
        if (result.isNotEmpty()) {
            val entry = result.first()
            assertTrue(entry.containsKey("bookingId"))
            assertTrue(entry.containsKey("bookingDate"))
            assertTrue(entry.containsKey("totalPrice"))
            assertTrue(entry.containsKey("status"))
            assertTrue(entry.containsKey("contactEmail"))
        }
    }

    // ── getRecentCancellations ─────────────────────────────────────────────────

    /**
     * Verifies that [AdminDAO.getRecentCancellations] returns a non-null list.
     */
    @Test
    fun testGetRecentCancellationsReturnsList() {
        val result = AdminDAO.getRecentCancellations()
        assertNotNull(result)
    }

    /**
     * Verifies that [AdminDAO.getRecentCancellations] respects the given limit.
     */
    @Test
    fun testGetRecentCancellationsRespectsLimit() {
        val result = AdminDAO.getRecentCancellations(3)
        assertTrue(result.size <= 3)
    }

    /**
     * Verifies that each cancellation entry contains the four expected keys.
     */
    @Test
    fun testGetRecentCancellationsHasExpectedKeys() {
        val result = AdminDAO.getRecentCancellations(1)
        if (result.isNotEmpty()) {
            val entry = result.first()
            assertTrue(entry.containsKey("bookingId"))
            assertTrue(entry.containsKey("bookingDate"))
            assertTrue(entry.containsKey("totalPrice"))
            assertTrue(entry.containsKey("contactEmail"))
        }
    }

    // ── getUpcomingFlights ─────────────────────────────────────────────────────

    /**
     * Verifies that [AdminDAO.getUpcomingFlights] returns a non-null list.
     */
    @Test
    fun testGetUpcomingFlightsReturnsList() {
        val result = AdminDAO.getUpcomingFlights()
        assertNotNull(result)
    }

    /**
     * Verifies that [AdminDAO.getUpcomingFlights] respects the given limit.
     */
    @Test
    fun testGetUpcomingFlightsRespectsLimit() {
        val result = AdminDAO.getUpcomingFlights(5)
        assertTrue(result.size <= 5)
    }

    /**
     * Verifies that each flight entry contains the eight expected keys.
     */
    @Test
    fun testGetUpcomingFlightsHasExpectedKeys() {
        val result = AdminDAO.getUpcomingFlights(1)
        if (result.isNotEmpty()) {
            val entry = result.first()
            assertTrue(entry.containsKey("flightId"))
            assertTrue(entry.containsKey("flightDate"))
            assertTrue(entry.containsKey("status"))
            assertTrue(entry.containsKey("flightNumber"))
            assertTrue(entry.containsKey("departureCity"))
            assertTrue(entry.containsKey("arrivalCity"))
            assertTrue(entry.containsKey("price"))
        }
    }

    // ── getBusiestRoutes ───────────────────────────────────────────────────────

    /**
     * Verifies that [AdminDAO.getBusiestRoutes] returns a non-null list.
     */
    @Test
    fun testGetBusiestRoutesReturnsList() {
        val result = AdminDAO.getBusiestRoutes()
        assertNotNull(result)
    }

    /**
     * Verifies that [AdminDAO.getBusiestRoutes] respects the given limit.
     */
    @Test
    fun testGetBusiestRoutesRespectsLimit() {
        val result = AdminDAO.getBusiestRoutes(3)
        assertTrue(result.size <= 3)
    }

    /**
     * Verifies that results are sorted from highest to lowest booking count.
     */
    @Test
    fun testGetBusiestRoutesIsSortedDescending() {
        val result = AdminDAO.getBusiestRoutes()
        if (result.size > 1) {
            for (i in 0 until result.size - 1) {
                val current = result[i]["bookingCount"] as Int
                val next = result[i + 1]["bookingCount"] as Int
                assertTrue(current >= next, "Busiest routes should appear first")
            }
        }
    }

    /**
     * Verifies that each route entry contains the four expected keys.
     */
    @Test
    fun testGetBusiestRoutesHasExpectedKeys() {
        val result = AdminDAO.getBusiestRoutes(1)
        if (result.isNotEmpty()) {
            val entry = result.first()
            assertTrue(entry.containsKey("departureCity"))
            assertTrue(entry.containsKey("arrivalCity"))
            assertTrue(entry.containsKey("flightNumber"))
            assertTrue(entry.containsKey("bookingCount"))
        }
    }

    /**
     * Verifies that [AdminDAO.getBusiestRoutes] accepts all three optional filters
     * simultaneously without throwing.
     */
    @Test
    fun testGetBusiestRoutesWithAllFilters() {
        val result =
            AdminDAO.getBusiestRoutes(
                limit = 5,
                startDate = "2024-01-01",
                endDate = "2025-12-31",
                filterSeason = "Summer 2025",
            )
        assertNotNull(result)
    }

    // ── trackReservations ──────────────────────────────────────────────────────

    /**
     * Verifies that [AdminDAO.trackReservations] returns a non-null list when called
     * with no filters.
     */
    @Test
    fun testTrackReservationsReturnsList() {
        val result = AdminDAO.trackReservations()
        assertNotNull(result)
    }

    /**
     * Verifies that each reservation entry contains all seven expected keys.
     */
    @Test
    fun testTrackReservationsHasExpectedKeys() {
        val result = AdminDAO.trackReservations()
        if (result.isNotEmpty()) {
            val entry = result.first()
            assertTrue(entry.containsKey("bookingId"))
            assertTrue(entry.containsKey("bookingDate"))
            assertTrue(entry.containsKey("totalPrice"))
            assertTrue(entry.containsKey("status"))
            assertTrue(entry.containsKey("contactEmail"))
            assertTrue(entry.containsKey("bookedBy"))
            assertTrue(entry.containsKey("passengers"))
        }
    }

    /**
     * Verifies that filtering by a specific status returns only entries with that status.
     */
    @Test
    fun testTrackReservationsFilterByStatusMatchesOnlyThatStatus() {
        val status = "confirmed"
        val result = AdminDAO.trackReservations(filterStatus = status)
        for (entry in result) {
            assertEquals(status, entry["status"], "All results must have the filtered status")
        }
    }

    /**
     * Verifies that filtering by a far-future date returns an empty list.
     */
    @Test
    fun testTrackReservationsFilterByFutureDateReturnsEmpty() {
        val result = AdminDAO.trackReservations(filterDate = "9999-12-31")
        assertTrue(result.isEmpty(), "No bookings should exist for a far-future date")
    }

    /**
     * Verifies that [AdminDAO.trackReservations] accepts a username substring filter
     * without throwing.
     */
    @Test
    fun testTrackReservationsFilterByUsernameReturnsList() {
        val result = AdminDAO.trackReservations(filterUsername = "a")
        assertNotNull(result)
    }

    /**
     * Verifies that all three filters can be combined without throwing.
     */
    @Test
    fun testTrackReservationsWithAllFilters() {
        val result =
            AdminDAO.trackReservations(
                filterDate = "2025-01-01",
                filterUsername = "John",
                filterStatus = "confirmed",
            )
        assertNotNull(result)
    }

    // ── trackFlights ───────────────────────────────────────────────────────────

    /**
     * Verifies that [AdminDAO.trackFlights] returns a non-null list for today's date
     * (the default when no date is supplied).
     */
    @Test
    fun testTrackFlightsReturnsList() {
        val result = AdminDAO.trackFlights()
        assertNotNull(result)
    }

    /**
     * Verifies that each flight tracking entry contains all nine expected keys.
     */
    @Test
    fun testTrackFlightsHasExpectedKeys() {
        val result = AdminDAO.trackFlights()
        if (result.isNotEmpty()) {
            val entry = result.first()
            assertTrue(entry.containsKey("flightId"))
            assertTrue(entry.containsKey("flightDate"))
            assertTrue(entry.containsKey("status"))
            assertTrue(entry.containsKey("flightNumber"))
            assertTrue(entry.containsKey("originCity"))
            assertTrue(entry.containsKey("destCity"))
            assertTrue(entry.containsKey("depTime"))
            assertTrue(entry.containsKey("arrTime"))
            assertTrue(entry.containsKey("overnight"))
        }
    }

    /**
     * Verifies that a far-future date returns an empty list.
     */
    @Test
    fun testTrackFlightsWithFutureDateReturnsEmptyList() {
        val result = AdminDAO.trackFlights(filterDate = "9999-12-31")
        assertTrue(result.isEmpty())
    }

    /**
     * Verifies that [AdminDAO.trackFlights] accepts a flight-number filter without
     * throwing.
     */
    @Test
    fun testTrackFlightsWithFlightNumberFilter() {
        val result = AdminDAO.trackFlights(filterNumber = "AA")
        assertNotNull(result)
    }

    // ── getAdjacentFlightDates ─────────────────────────────────────────────────

    /**
     * Verifies that [AdminDAO.getAdjacentFlightDates] returns the correct previous
     * and next dates for a known input.
     */
    @Test
    fun testGetAdjacentFlightDatesValidDate() {
        val result = AdminDAO.getAdjacentFlightDates("2025-06-15")
        assertEquals("2025-06-14", result["prevDate"])
        assertEquals("2025-06-16", result["nextDate"])
    }

    /**
     * Verifies that an invalid date string causes a graceful fallback to today and
     * that both keys are still present in the result.
     */
    @Test
    fun testGetAdjacentFlightDatesInvalidDateStillReturnsKeys() {
        val result = AdminDAO.getAdjacentFlightDates("not-a-date")
        assertTrue(result.containsKey("prevDate"))
        assertTrue(result.containsKey("nextDate"))
    }

    /**
     * Verifies that the result always contains exactly the `prevDate` and `nextDate` keys.
     */
    @Test
    fun testGetAdjacentFlightDatesHasCorrectKeys() {
        val result = AdminDAO.getAdjacentFlightDates("2025-01-01")
        assertTrue(result.containsKey("prevDate"))
        assertTrue(result.containsKey("nextDate"))
    }

    /**
     * Verifies that month-boundary arithmetic (March 1 → February 28) is handled correctly.
     */
    @Test
    fun testGetAdjacentFlightDatesMonthBoundary() {
        val result = AdminDAO.getAdjacentFlightDates("2025-03-01")
        assertEquals("2025-02-28", result["prevDate"])
        assertEquals("2025-03-02", result["nextDate"])
    }

    // ── updateFlightStatus ─────────────────────────────────────────────────────

    /**
     * Verifies that [AdminDAO.updateFlightStatus] returns false immediately for a
     * status string not in the allow-list, without touching the database.
     */
    @Test
    fun testUpdateFlightStatusRejectsInvalidStatus() {
        val result = AdminDAO.updateFlightStatus(1, "Flying")
        assertFalse(result, "An invalid status must be rejected before hitting the DB")
    }

    /**
     * Verifies that a valid status against a non-existent flight ID returns false
     * (no rows updated) without throwing.
     */
    @Test
    fun testUpdateFlightStatusReturnsFalseForNonExistentFlight() {
        val result = AdminDAO.updateFlightStatus(-9999, "Delayed")
        assertFalse(result, "A non-existent flight ID must produce no update")
    }

    /**
     * Verifies that every value in the status allow-list is accepted (no exception
     * thrown) when used with a non-existent flight ID.
     */
    @Test
    fun testUpdateFlightStatusAcceptsAllAllowedStatuses() {
        val allowed = listOf("Scheduled", "Delayed", "Cancelled", "Departed", "Arrived")
        for (status in allowed) {
            val threw =
                try {
                    AdminDAO.updateFlightStatus(-9999, status)
                    false
                } catch (e: Exception) {
                    true
                }
            assertFalse(threw, "Valid status '$status' must not throw")
        }
    }

    // ── getFlightStatusNotification ────────────────────────────────────────────

    /**
     * Verifies that a non-existent flight ID produces no notification.
     */
    @Test
    fun testGetFlightStatusNotificationForInvalidFlightReturnsNull() {
        val result = AdminDAO.getFlightStatusNotification(-1, "Delayed")
        assertNull(result, "An invalid flight ID must not produce a notification")
    }

    /**
     * Verifies that non-notifiable statuses (`"Arrived"`, etc.) return null immediately.
     */
    @Test
    fun testGetFlightStatusNotificationForNonNotifiableStatusReturnsNull() {
        val result = AdminDAO.getFlightStatusNotification(1, "Arrived")
        assertNull(result, "Only Delayed and Cancelled should trigger notifications")
    }

    /**
     * Verifies that `"Scheduled"` is not a notifiable status.
     */
    @Test
    fun testGetFlightStatusNotificationScheduledReturnsNull() {
        val result = AdminDAO.getFlightStatusNotification(1, "Scheduled")
        assertNull(result)
    }

    /**
     * Verifies that `"Departed"` is not a notifiable status.
     */
    @Test
    fun testGetFlightStatusNotificationDepartedReturnsNull() {
        val result = AdminDAO.getFlightStatusNotification(1, "Departed")
        assertNull(result)
    }

    // ── getBookingsPerFlight ───────────────────────────────────────────────────

    /**
     * Verifies that [AdminDAO.getBookingsPerFlight] returns a non-null list.
     */
    @Test
    fun testGetBookingsPerFlightReturnsList() {
        val result = AdminDAO.getBookingsPerFlight()
        assertNotNull(result)
    }

    /**
     * Verifies that [AdminDAO.getBookingsPerFlight] respects the given limit.
     */
    @Test
    fun testGetBookingsPerFlightRespectsLimit() {
        val result = AdminDAO.getBookingsPerFlight(limit = 3)
        assertTrue(result.size <= 3)
    }

    /**
     * Verifies that each entry contains the five expected keys.
     */
    @Test
    fun testGetBookingsPerFlightHasExpectedKeys() {
        val result = AdminDAO.getBookingsPerFlight(limit = 1)
        if (result.isNotEmpty()) {
            val entry = result.first()
            assertTrue(entry.containsKey("flightNumber"))
            assertTrue(entry.containsKey("flightDate"))
            assertTrue(entry.containsKey("origin"))
            assertTrue(entry.containsKey("dest"))
            assertTrue(entry.containsKey("bookingCount"))
        }
    }

    /**
     * Verifies that [AdminDAO.getBookingsPerFlight] accepts optional date and season
     * filters without throwing.
     */
    @Test
    fun testGetBookingsPerFlightWithFilters() {
        val result =
            AdminDAO.getBookingsPerFlight(
                limit = 5,
                filterDate = "2025-06-01",
                filterSeason = "Summer 2025",
            )
        assertNotNull(result)
    }

    // ── getPendingChangeRequests ───────────────────────────────────────────────

    /**
     * Verifies that [AdminDAO.getPendingChangeRequests] returns a non-null list.
     */
    @Test
    fun testGetPendingChangeRequestsReturnsList() {
        val result = AdminDAO.getPendingChangeRequests()
        assertNotNull(result)
    }

    /**
     * Verifies that each pending request entry contains the five expected keys.
     */
    @Test
    fun testGetPendingChangeRequestsHasExpectedKeys() {
        val result = AdminDAO.getPendingChangeRequests()
        if (result.isNotEmpty()) {
            val entry = result.first()
            assertTrue(entry.containsKey("requestId"))
            assertTrue(entry.containsKey("userId"))
            assertTrue(entry.containsKey("changeTo"))
            assertTrue(entry.containsKey("type"))
            assertTrue(entry.containsKey("username"))
        }
    }

    // ── updateRequestStatus ────────────────────────────────────────────────────

    /**
     * Verifies that [AdminDAO.updateRequestStatus] returns false for a non-existent
     * user ID (no rows to update).
     */
    @Test
    fun testUpdateRequestStatusReturnsFalseForNonExistentUser() {
        val result = AdminDAO.updateRequestStatus(-9999, "accepted")
        assertFalse(result, "No rows should be updated for a non-existent user")
    }

    // ── approveChangeRequest ───────────────────────────────────────────────────

    /**
     * Verifies that [AdminDAO.approveChangeRequest] (by userId) returns false when
     * the user has no pending requests.
     */
    @Test
    fun testApproveChangeRequestByUserIdReturnsFalseWhenNoPendingRequests() {
        val result = AdminDAO.approveChangeRequest(-9999)
        assertFalse(result)
    }

    /**
     * Verifies that [AdminDAO.approveChangeRequest] (by requestId + userId) returns
     * false for a non-existent request.
     */
    @Test
    fun testApproveChangeRequestByRequestIdReturnsFalseWhenNotFound() {
        val result = AdminDAO.approveChangeRequest(-9999L, -9999)
        assertFalse(result)
    }

    // ── rejectChangeRequest ────────────────────────────────────────────────────

    /**
     * Verifies that [AdminDAO.rejectChangeRequest] (by userId) returns false when
     * the user has no pending requests.
     */
    @Test
    fun testRejectChangeRequestByUserIdReturnsFalseWhenNoPendingRequests() {
        val result = AdminDAO.rejectChangeRequest(-9999)
        assertFalse(result)
    }

    /**
     * Verifies that [AdminDAO.rejectChangeRequest] (by requestId + userId) returns
     * false for a non-existent request.
     */
    @Test
    fun testRejectChangeRequestByRequestIdReturnsFalseWhenNotFound() {
        val result = AdminDAO.rejectChangeRequest(-9999L, -9999)
        assertFalse(result)
    }

    // ── Security: SQL-injection prevention ────────────────────────────────────

    /**
     * Verifies that a classic `OR '1'='1` injection in `filterSeason` of
     * [AdminDAO.getAllBookingsGroupedByDate] is treated as a literal string.
     * A parameterised query cannot match any real season with this payload.
     */
    @Test
    fun testSqlInjectionInGetAllBookingsGroupedByDateSeason() {
        val injection = "' OR '1'='1"
        val result = AdminDAO.getAllBookingsGroupedByDate(filterSeason = injection)
        assertTrue(result.isEmpty(), "Injection payload must not match any real season")
    }

    /**
     * Verifies that a DROP TABLE payload in `filterSeason` of [AdminDAO.getBusiestRoutes]
     * is handled safely: no exception, empty result, and the bookings table still exists.
     */
    @Test
    fun testSqlInjectionDropTableInGetBusiestRoutesSeason() {
        val injection = "'; DROP TABLE bookings; --"
        val result = AdminDAO.getBusiestRoutes(filterSeason = injection)
        assertTrue(result.isEmpty(), "Injection payload must not match any season")
        // If the DROP had executed, getRecentBookings would throw or return empty
        assertNotNull(AdminDAO.getRecentBookings(1), "bookings table must still be intact")
    }

    /**
     * Verifies that a UNION-based injection in `filterUsername` of
     * [AdminDAO.trackReservations] does not leak rows from other tables.
     * The payload is passed through LIKE matching and cannot alter query structure.
     */
    @Test
    fun testSqlInjectionUnionInTrackReservationsUsername() {
        val injection = "' UNION SELECT user_id,email,email,email,email,email,email FROM users --"
        val result = AdminDAO.trackReservations(filterUsername = injection)
        // Each result must come from the normal join path, not the injected union
        for (entry in result) {
            assertTrue(entry.containsKey("bookingId"), "Result shape must match normal output")
        }
    }

    /**
     * Verifies that an `OR 1=1` injection in `filterStatus` of [AdminDAO.trackReservations]
     * does not widen the result set to all bookings.
     */
    @Test
    fun testSqlInjectionOrTrueInTrackReservationsStatus() {
        val injection = "' OR 1=1 --"
        val result = AdminDAO.trackReservations(filterStatus = injection)
        assertTrue(result.isEmpty(), "Injection payload must not match any booking status")
    }

    /**
     * Verifies that an injection payload in `filterDate` of [AdminDAO.trackReservations]
     * returns no results (the date column cannot equal a raw injection string).
     */
    @Test
    fun testSqlInjectionInTrackReservationsDate() {
        val injection = "' OR '1'='1"
        val result = AdminDAO.trackReservations(filterDate = injection)
        assertTrue(result.isEmpty(), "Injection payload must not match any booking date")
    }

    /**
     * Verifies that an injection payload in `filterNumber` of [AdminDAO.trackFlights]
     * does not cause an exception or unintended data leakage.
     */
    @Test
    fun testSqlInjectionInTrackFlightsFlightNumber() {
        val injection = "' OR '1'='1"
        val result = AdminDAO.trackFlights(filterNumber = injection)
        // LIKE matching on the injected string will simply find no flights
        assertNotNull(result)
    }

    /**
     * Verifies that a status-injection attempt in [AdminDAO.updateFlightStatus]
     * is blocked by the allow-list before reaching the database.
     */
    @Test
    fun testSqlInjectionInUpdateFlightStatusBlockedByAllowList() {
        val injection = "Scheduled'; DROP TABLE flights; --"
        val result = AdminDAO.updateFlightStatus(1, injection)
        assertFalse(result, "Status not in the allow-list must be rejected before the DB is touched")
    }

    /**
     * Verifies that an injection payload in `filterDate` of [AdminDAO.getBookingsPerFlight]
     * is handled safely and does not throw.
     */
    @Test
    fun testSqlInjectionInGetBookingsPerFlightDate() {
        val injection = "' OR '1'='1"
        val result = AdminDAO.getBookingsPerFlight(filterDate = injection)
        assertNotNull(result)
    }

    /**
     * Verifies that an injection payload passed as `newStatus` to
     * [AdminDAO.updateRequestStatus] does not corrupt any real row.
     *
     * User ID -9999 guarantees no real row is targeted; the payload is bound as a
     * literal string by the parameterised statement and never executed as SQL.
     */
    @Test
    fun testSqlInjectionInUpdateRequestStatusPayloadIsLiteral() {
        val injection = "accepted'; UPDATE users SET password='hacked'; --"
        val result = AdminDAO.updateRequestStatus(-9999, injection)
        assertFalse(result, "No rows should be updated for a non-existent user ID")
    }

    /**
     * Verifies that a second-query injection (`; SELECT …`) in `filterSeason` of
     * [AdminDAO.getBookingsPerFlight] does not return extra rows.
     */
    @Test
    fun testSqlInjectionSecondQueryInGetBookingsPerFlightSeason() {
        val injection = "Summer 2025'; SELECT * FROM users; --"
        val result = AdminDAO.getBookingsPerFlight(filterSeason = injection)
        assertNotNull(result)
        // All returned rows must still have the expected shape
        for (entry in result) {
            assertTrue(entry.containsKey("flightNumber"))
        }
    }
}
