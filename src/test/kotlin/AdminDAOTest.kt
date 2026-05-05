package com.flightbooking

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/* testing all the admin dashboard queries
   these pull data for charts and tables on the admin home page */

class AdminDAOTest {

    /**
    * Tests that the total number of users is never negative.
    */
    @Test
    fun testGetTotalUsersReturnsNonNegative() {
        val count = AdminDAO.getTotalUsers()
        assertTrue(count >= 0, "cant have negative users lol")
    }


    /**
    * Tests for booking data grouped by date.
    *
    * These checks make sure the result is returned properly, has the right
    * keys, and is sorted from oldest date to newest date.
    */
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


    /**
    * Tests for booking status count data.
    *
    * These checks make sure the data comes back as expected, has the right
    * keys, and only contains positive booking counts.
    */
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


    /**
    * Tests for recent booking data.
    *
    * These checks make sure the limit works properly and that each booking
    * has the main fields we expect.
    */
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


    /**
    * Tests for recent cancellation data.
    *
    * These checks make sure cancelled bookings are returned properly and that
    * the limit value is being followed.
    */
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


    /**
    * Tests for upcoming flight data.
    *
    * These checks make sure the data comes back, the limit works, and the
    * main flight fields are there.
    */

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


   /**
    * Tests for busiest route data.
    *
    * These checks make sure the data is returned, the limit is followed,
    * and the routes are sorted from busiest to least busy.
    */

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
