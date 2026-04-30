package com.flightbooking

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class UtilsTest {
    // testing the duration formatter

    @Test
    fun testFormatDurationHoursOnly() {
        assertEquals("2h", Utils.formatDuration(120))
    }

    @Test
    fun testFormatDurationHoursAndMinutes() {
        assertEquals("2h 30m", Utils.formatDuration(150))
    }

    @Test
    fun testFormatDurationMinutesOnly() {
        assertEquals("0h 45m", Utils.formatDuration(45))
    }

    @Test
    fun testFormatDurationZero() {
        assertEquals("0h", Utils.formatDuration(0))
    }

    @Test
    fun testFormatDurationOneMinute() {
        assertEquals("0h 1m", Utils.formatDuration(1))
    }

    @Test
    fun testFormatDurationLongFlight() {
        // 14 hours and 25 mins = 865 minutes
        assertEquals("14h 25m", Utils.formatDuration(865))
    }

    // flightToMap - converts a Flight object into a map for the templates

    private fun makeFlight(
        flightId: Int = 1,
        flightNumber: String = "EJ101",
        priceEconomy: Double? = 199.99,
        priceBusiness: Double? = 499.99,
        priceFirst: Double? = null,
        durationMinutes: Int = 120,
        arrivalDayOffset: Int = 0,
    ) = Flight(
        flightId = flightId,
        flightNumber = flightNumber,
        aircraftType = "A320",
        departureCity = "London",
        arrivalCity = "Paris",
        departureAirportName = "Heathrow",
        arrivalAirportName = "Charles de Gaulle",
        departureTerminal = "T5",
        arrivalTerminal = "T2",
        departureDate = LocalDate.of(2026, 6, 15),
        departureTime = LocalTime.of(10, 30),
        arrivalTime = LocalTime.of(12, 30),
        arrivalDayOffset = arrivalDayOffset,
        durationMinutes = durationMinutes,
        priceEconomy = priceEconomy,
        priceBusiness = priceBusiness,
        priceFirst = priceFirst,
    )

    @Test
    fun testFlightToMapContainsAllKeys() {
        val map = Utils.flightToMap(makeFlight())
        val expectedKeys =
            listOf(
                "flightNumber",
                "departureAirport",
                "arrivalAirport",
                "departureTerminal",
                "arrivalTerminal",
                "departureTime",
                "arrivalTime",
                "duration",
                "priceEconomy",
                "priceBusiness",
                "priceFirst",
                "date",
                "flightId",
                "arrivalDayOffset",
            )
        for (key in expectedKeys) {
            assert(map.containsKey(key)) { "should have '$key' in the map" }
        }
    }

    @Test
    fun testFlightToMapValues() {
        val map = Utils.flightToMap(makeFlight())
        assertEquals("EJ101", map["flightNumber"])
        assertEquals("Heathrow", map["departureAirport"])
        assertEquals("Charles de Gaulle", map["arrivalAirport"])
        assertEquals("T5", map["departureTerminal"])
        assertEquals("T2", map["arrivalTerminal"])
        assertEquals(199.99, map["priceEconomy"])
        assertEquals(499.99, map["priceBusiness"])
        assertEquals(null, map["priceFirst"])
        assertEquals(1, map["flightId"])
        assertEquals("2h", map["duration"])
        assertEquals("2026-06-15", map["date"])
        assertEquals(0, map["arrivalDayOffset"])
    }

    @Test
    fun testFlightToMapOvernightFlight() {
        val map = Utils.flightToMap(makeFlight(arrivalDayOffset = 1))
        assertEquals(1, map["arrivalDayOffset"])
    }

    // connecting flight map tests

    @Test
    fun testConnectingFlightToMapContainsAllKeys() {
        val cf =
            ConnectingFlight(
                leg1 = makeFlight(flightId = 1, flightNumber = "EJ101"),
                leg2 = makeFlight(flightId = 2, flightNumber = "EJ202"),
                totalDurationMinutes = 300,
                layoverMinutes = 60,
                totalPrice = 399.99,
            )
        val map = Utils.connectingFlightToMap(cf)

        val expectedKeys =
            listOf(
                "leg1DepartureTime",
                "leg1ArrivalTime",
                "leg1DepartureAirport",
                "leg1ArrivalAirport",
                "leg2DepartureTime",
                "leg2ArrivalTime",
                "leg2ArrivalAirport",
                "layoverMinutes",
                "totalDuration",
                "priceEconomy",
                "priceBusiness",
                "priceFirst",
                "leg1FlightId",
                "leg2FlightId",
                "leg2ArrivalDayOffset",
            )
        for (key in expectedKeys) {
            assert(map.containsKey(key)) { "connecting flight map should have '$key'" }
        }
    }

    @Test
    fun testConnectingFlightToMapValues() {
        val leg1 = makeFlight(priceEconomy = 100.0, priceBusiness = 200.0, priceFirst = null)
        val leg2 = makeFlight(priceEconomy = 50.0, priceBusiness = null, priceFirst = 500.0)

        val cf =
            ConnectingFlight(
                leg1 = leg1,
                leg2 = leg2,
                totalDurationMinutes = 300,
                layoverMinutes = 60,
                totalPrice = 150.0,
            )
        val map = Utils.connectingFlightToMap(cf)

        // 100 + 50 = 150
        assertEquals(150.0, map["priceEconomy"])

        // If any is null, the result should be null
        assertEquals(null, map["priceBusiness"])
        assertEquals(null, map["priceFirst"])

        assertEquals("5h", map["totalDuration"])
        assertEquals(60, map["layoverMinutes"])
    }
}
