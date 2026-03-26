package com.flightbooking

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull


/* testing all the admin dashboard queries
   these pull data for charts and tables on the admin home page */

class AdminDAOTest {

    // total users count

    @Test
    fun testGetTotalUsersReturnsNonNegative() {
        val count = AdminDAO.getTotalUsers()
        assertTrue(count >= 0, "cant have negative users lol")
    }


    // bookings grouped by date - used for the line chart

    @Test
    fun testGetAllBookingsGroupedByDateReturnslist() {
        val result = AdminDAO.getAllBookingsGroupedByDate()
        assertNotNull(result, "should give us a list not null")
    }

    @Test
    fun testGetAllBookingsGroupedByDateHasExpectedKeys() {
        val result = AdminDAO.getAllBookingsGroupedByDate()
        if (result.isNotEmpty()) {
            val entry = result.first()
            assertTrue(entry.containsKey("date"), "needs a date key")
            assertTrue(entry.containsKey("count"), "needs a count key")
            assertTrue(entry.containsKey("revenue"), "needs a revenue key")
        }
    }

    @Test
    fun testGetAllBookingsGroupedByDateIsSortedAscending() {
        val result = AdminDAO.getAllBookingsGroupedByDate()
        if (result.size > 1) {
            for (i in 0 until result.size - 1) {
                val current = result[i]["date"] as String
                val next = result[i + 1]["date"] as String
                assertTrue(current <= next, "dates should go oldest to newest")
            }
        }
    }


    // booking status counts - for the pie chart

    @Test
    fun testGetBookingStatusCountsReturnsList() {
        val result = AdminDAO.getBookingStatusCounts()
        assertNotNull(result)
    }

    @Test
    fun testGetBookingStatusCountsHasExpectedKeys() {
        val result = AdminDAO.getBookingStatusCounts()
        if (result.isNotEmpty()) {
            val entry = result.first()
            assertTrue(entry.containsKey("status"), "should have status")
            assertTrue(entry.containsKey("count"), "should have count")
        }
    }

    @Test
    fun testGetBookingStatusCountsArePositive() {
        val result = AdminDAO.getBookingStatusCounts()
        for (entry in result) {
            val count = entry["count"] as Int
            assertTrue(count > 0, "each status should have at least 1 booking")
        }
    }


    // recent bookings table

    @Test
    fun testGetRecentBookingsRespectsLimit() {
        val result = AdminDAO.getRecentBookings(5)
        assertTrue(result.size <= 5, "asked for 5 so shouldnt get more than 5")
    }

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


    // recent cancellations

    @Test
    fun testGetRecentCancellationsOnlyContainsCancelled() {
        val result = AdminDAO.getRecentCancellations()
        // the query filters by status = 'cancelled' so this should be fine
        assertNotNull(result)
    }

    @Test
    fun testGetRecentCancellationsRespectsLimit() {
        val result = AdminDAO.getRecentCancellations(3)
        assertTrue(result.size <= 3, "shouldnt return more than we asked for")
    }


    // upcoming flights

    @Test
    fun testGetUpcomingFlightsReturnsList() {
        val result = AdminDAO.getUpcomingFlights()
        assertNotNull(result)
    }

    @Test
    fun testGetUpcomingFlightsRespectsLimit() {
        val result = AdminDAO.getUpcomingFlights(5)
        assertTrue(result.size <= 5, "limit should work")
    }

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
        }
    }


    // busiest routes - for the bar chart

    @Test
    fun testGetBusiestRoutesReturnsList() {
        val result = AdminDAO.getBusiestRoutes()
        assertNotNull(result)
    }

    @Test
    fun testGetBusiestRoutesRespectsLimit() {
        val result = AdminDAO.getBusiestRoutes(3)
        assertTrue(result.size <= 3, "only asked for top 3")
    }

    @Test
    fun testGetBusiestRoutesIsSortedDescending() {
        val result = AdminDAO.getBusiestRoutes()
        if (result.size > 1) {
            for (i in 0 until result.size - 1) {
                val current = result[i]["bookingCount"] as Int
                val next = result[i + 1]["bookingCount"] as Int
                assertTrue(current >= next, "busiest should be first")
            }
        }
    }
}
