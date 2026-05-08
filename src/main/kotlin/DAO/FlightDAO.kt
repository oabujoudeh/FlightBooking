package com.flightbooking

import java.sql.ResultSet
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Data Access Object for flight search and retrieval operations.
 *
 * Handles direct flight queries, connecting flight assembly, and conversion
 * of raw database rows into typed flight objects and display DTOs.
 */
object FlightDAO {
    /**
     * Returns all available flights for a given route and date, sorted by departure time.
     *
     * Combines direct flights and connecting flights between [dep] and [arr],
     * converts each to a [FlightDisplayDTO], and merges them into a single list
     * ordered by departure time.
     *
     * @param dep the departure airport ID or city name
     * @param arr the arrival airport ID or city name
     * @param date the travel date to search
     * @return a time-sorted list of direct and connecting [FlightDisplayDTO] objects
     */
    fun getAvailableFlights(
        dep: String,
        arr: String,
        date: LocalDate,
    ): List<FlightDisplayDTO> {
        val direct = searchFlights(dep, arr, date).map { it.toDisplayDTO() }
        val connecting = searchConnectingFlights(dep, arr, date).map { it.toDisplayDTO() }

        return (direct + connecting).sortedBy { it.departureTime }
    }

    /**
     * Maps a single result-set row to a [Flight] object.
     *
     * Reads all flight and route columns from the current row, then derives
     * the arrival time and arrival date by adding the route duration to the
     * departure datetime and converting to the arrival airport's timezone.
     * Missing timezone strings fall back to `"UTC"`; optional text fields
     * fall back to empty strings.
     *
     * The [Flight.arrivalDayOffset] field indicates the number of calendar
     * days between departure and arrival (0 = same day, 1 = next day, etc.).
     *
     * @param result the active [ResultSet] positioned on the row to read
     * @return a [Flight] object populated from the current row
     */
    private fun mapResultToFlight(result: ResultSet): Flight {
        val durationMinutes = result.getInt("base_duration_minutes")
        val depZone = ZoneId.of(result.getString("departure_timezone") ?: "UTC")
        val arrZone = ZoneId.of(result.getString("arrival_timezone") ?: "UTC")

        val departureTime = LocalTime.parse(result.getString("planned_departure"))
        val departureDate = LocalDate.parse(result.getString("flight_date"))

        val departureZdt = ZonedDateTime.of(departureDate, departureTime, depZone)
        val arrivalZdt = departureZdt.plusMinutes(durationMinutes.toLong()).withZoneSameInstant(arrZone)

        val arrivalTime = arrivalZdt.toLocalTime()
        val arrivalDate = arrivalZdt.toLocalDate()
        val arrivalDayOffset = (arrivalDate.toEpochDay() - departureDate.toEpochDay()).toInt()

        return Flight(
            flightId = result.getInt("flight_id"),
            flightNumber = result.getString("flight_number"),
            aircraftType = result.getString("aircraft_type") ?: "",
            departureCity = result.getString("departure_city"),
            arrivalCity = result.getString("arrival_city"),
            departureAirportName = result.getString("departure_airport_name") ?: "",
            arrivalAirportName = result.getString("arrival_airport_name") ?: "",
            departureTerminal = result.getString("departure_terminal") ?: "",
            arrivalTerminal = result.getString("arrival_terminal") ?: "",
            departureDate = departureDate,
            departureTime = departureTime,
            arrivalTime = arrivalTime,
            arrivalDayOffset = arrivalDayOffset,
            durationMinutes = durationMinutes,
            priceEconomy = result.getObject("price_economy") as? Double,
            priceBusiness = result.getObject("price_business") as? Double,
            priceFirst = result.getObject("price_first") as? Double,
        )
    }

    /**
     * Searches for direct flights between two locations on a given date.
     *
     * Matches flights where the departure airport or city equals [departure] and
     * the arrival airport or city equals [arrival]. Cancelled flights are excluded.
     * Results are ordered by departure time and each row is mapped via
     * [mapResultToFlight].
     *
     * @param departure the departure airport ID or city name
     * @param arrival the arrival airport ID or city name
     * @param date the date to search for flights
     * @return a list of matching [Flight] objects in departure-time order
     */
    fun searchFlights(
        departure: String,
        arrival: String,
        date: LocalDate,
    ): List<Flight> {
        val sql = """
            SELECT f.flight_id, f.flight_date, r.flight_number, r.aircraft_type,
                   r.departure_terminal, r.arrival_terminal, r.planned_departure, 
                   r.base_duration_minutes, r.price_economy, r.price_business, r.price_first,
                   dep.city as departure_city, arr.city as arrival_city,
                   dep.name as departure_airport_name, arr.name as arrival_airport_name,
                   dep.timezone as departure_timezone, arr.timezone as arrival_timezone
            FROM flights f
            JOIN routes r ON f.route_id = r.route_id
            JOIN airports dep ON r.departure_airport = dep.airport_id
            JOIN airports arr ON r.arrival_airport = arr.airport_id
            WHERE (dep.city = ? OR dep.airport_id = ?)
              AND (arr.city = ? OR arr.airport_id = ?)
              AND f.flight_date = ?
              AND f.status != 'Cancelled'
            ORDER BY r.planned_departure ASC
        """
        Database.getConnection().use { conn ->
            val stmt = conn.prepareStatement(sql)
            stmt.setString(1, departure)
            stmt.setString(2, departure)
            stmt.setString(3, arrival)
            stmt.setString(4, arrival)
            stmt.setString(5, date.toString())
            val result = stmt.executeQuery()
            val flights = mutableListOf<Flight>()
            while (result.next()) flights.add(mapResultToFlight(result))
            return flights
        }
    }

    /**
     * Searches for one-stop connecting flights between two locations on a given date.
     *
     * Queries outbound legs departing from [departure] and inbound legs arriving at
     * [arrival] (on [date] and the following day to handle overnight first legs).
     * Two legs are paired as a valid connection when they share the same intermediate
     * airport and the layover duration falls within [[minLayover], [maxLayover]] minutes.
     *
     * The combined price for each cabin class is set only when both legs offer that cabin;
     * the base `totalPrice` on the [ConnectingFlight] object uses economy fares.
     *
     * @param departure the departure airport ID or city name
     * @param arrival the arrival airport ID or city name
     * @param date the outbound travel date to search
     * @param minLayover minimum acceptable layover in minutes (default 45)
     * @param maxLayover maximum acceptable layover in minutes (default 480)
     * @return a list of valid [ConnectingFlight] objects sorted by first-leg departure time
     */
    fun searchConnectingFlights(
        departure: String,
        arrival: String,
        date: LocalDate,
        minLayover: Int = 45,
        maxLayover: Int = 480,
    ): List<ConnectingFlight> {
        val outboundSql = """
            SELECT f.*, r.*, dep.city as departure_city, arr.city as arrival_city,
                   dep.name as departure_airport_name, arr.name as arrival_airport_name,
                   dep.timezone as departure_timezone, arr.timezone as arrival_timezone,
                   arr.airport_id as arrival_airport_id
            FROM flights f
            JOIN routes r ON f.route_id = r.route_id
            JOIN airports dep ON r.departure_airport = dep.airport_id
            JOIN airports arr ON r.arrival_airport = arr.airport_id
            WHERE (dep.city = ? OR dep.airport_id = ?) AND f.flight_date = ? AND f.status != 'Cancelled'
        """

        val inboundSql = """
            SELECT f.*, r.*, dep.city as departure_city, arr.city as arrival_city,
                   dep.name as departure_airport_name, arr.name as arrival_airport_name,
                   dep.timezone as departure_timezone, arr.timezone as arrival_timezone,
                   dep.airport_id as departure_airport_id
            FROM flights f
            JOIN routes r ON f.route_id = r.route_id
            JOIN airports dep ON r.departure_airport = dep.airport_id
            JOIN airports arr ON r.arrival_airport = arr.airport_id
            WHERE (arr.city = ? OR arr.airport_id = ?) AND f.flight_date = ? AND f.status != 'Cancelled'
        """

        Database.getConnection().use { conn ->
            val outbound = mutableListOf<Pair<Flight, String>>()
            conn.prepareStatement(outboundSql).use { stmt ->
                stmt.setString(1, departure)
                stmt.setString(2, departure)
                stmt.setString(3, date.toString())
                val rs = stmt.executeQuery()
                while (rs.next()) outbound.add(mapResultToFlight(rs) to rs.getString("arrival_airport_id"))
            }

            // Also search the next day for inbound legs, to handle overnight first legs
            val inbound = mutableListOf<Pair<Flight, String>>()
            for (searchDate in listOf(date, date.plusDays(1))) {
                conn.prepareStatement(inboundSql).use { stmt ->
                    stmt.setString(1, arrival)
                    stmt.setString(2, arrival)
                    stmt.setString(3, searchDate.toString())
                    val rs = stmt.executeQuery()
                    while (rs.next()) inbound.add(mapResultToFlight(rs) to rs.getString("departure_airport_id"))
                }
            }

            val connections = mutableListOf<ConnectingFlight>()
            for ((leg1, midId1) in outbound) {
                for ((leg2, midId2) in inbound) {
                    if (midId1 != midId2) continue

                    // Account for overnight flights: leg1 may arrive the next day
                    val leg1ArrivalDate = leg1.departureDate.plusDays(leg1.arrivalDayOffset.toLong())
                    val leg1ArrivalZdt = java.time.ZonedDateTime.of(leg1ArrivalDate, leg1.arrivalTime, java.time.ZoneId.of("UTC"))
                    val leg2DepartureZdt =
                        java.time.ZonedDateTime.of(
                            leg2.departureDate,
                            leg2.departureTime,
                            java.time.ZoneId.of("UTC"),
                        )

                    val layover =
                        java.time.Duration
                            .between(leg1ArrivalZdt, leg2DepartureZdt)
                            .toMinutes()
                    if (layover in minLayover..maxLayover) {
                        // Use economy as the base totalPrice for the ConnectingFlight object;
                        // null-safe: treat a missing economy price as 0.0
                        val basePrice = (leg1.priceEconomy ?: 0.0) + (leg2.priceEconomy ?: 0.0)

                        connections.add(
                            ConnectingFlight(
                                leg1 = leg1,
                                leg2 = leg2,
                                totalDurationMinutes = leg1.durationMinutes + layover.toInt() + leg2.durationMinutes,
                                layoverMinutes = layover.toInt(),
                                totalPrice = basePrice,
                            ),
                        )
                    }
                }
            }
            return connections.sortedBy { it.leg1.departureTime }
        }
    }

    /**
     * Retrieves the overview details for a single flight by its ID.
     *
     * Joins the flights, routes, and airports tables to return a fully populated
     * [Flight] object. Returns null if no flight with the given ID exists.
     *
     * @param flightId the ID of the flight to look up
     * @return the matching [Flight], or null if not found
     */
    fun getFlightOverview(flightId: Int): Flight? {
        val sql = """
            SELECT 
                f.flight_id, f.flight_date,
                r.flight_number, r.aircraft_type,
                r.departure_terminal, r.arrival_terminal,
                r.planned_departure, r.base_duration_minutes,
                r.price_economy, r.price_business, r.price_first,
                dep.city as departure_city, arr.city as arrival_city,
                dep.name as departure_airport_name, arr.name as arrival_airport_name,
                dep.timezone as departure_timezone,
                arr.timezone as arrival_timezone
            FROM flights f
            JOIN routes r ON f.route_id = r.route_id
            JOIN airports dep ON r.departure_airport = dep.airport_id
            JOIN airports arr ON r.arrival_airport = arr.airport_id
            WHERE f.flight_id = ?
        """

        return Database.getConnection().use { conn ->
            val stmt = conn.prepareStatement(sql)
            stmt.setInt(1, flightId)
            val result = stmt.executeQuery()
            if (result.next()) mapResultToFlight(result) else null
        }
    }

    /**
     * Converts a [Flight] to a [FlightDisplayDTO] suitable for UI rendering.
     *
     * Sets [FlightDisplayDTO.isConnecting] to false and formats the total
     * duration as a human-readable string (e.g. `"2h 35m"`).
     *
     * @receiver the [Flight] to convert
     * @return a [FlightDisplayDTO] representing this direct flight
     */
    private fun Flight.toDisplayDTO() =
        FlightDisplayDTO(
            flightId = this.flightId.toString(),
            isConnecting = false,
            aircraftType = this.aircraftType,
            departureAirport = this.departureAirportName,
            arrivalAirport = this.arrivalAirportName,
            departureTime = this.departureTime,
            arrivalTime = this.arrivalTime,
            arrivalDayOffset = this.arrivalDayOffset,
            totalDurationDisplay = "${this.durationMinutes / 60}h ${this.durationMinutes % 60}m",
            priceEconomy = this.priceEconomy,
            priceBusiness = this.priceBusiness,
            priceFirst = this.priceFirst,
            departureTerminal = this.departureTerminal,
            arrivalTerminal = this.arrivalTerminal,
        )

    /**
     * Converts a [ConnectingFlight] to a [FlightDisplayDTO] suitable for UI rendering.
     *
     * The flight ID is encoded as `"leg1Id_leg2Id"` and the aircraft type as
     * `"leg1Type_leg2Type"`. Cabin prices are only summed when **both** legs offer
     * that cabin; if either leg is missing a cabin, the combined price is null.
     * The [FlightDisplayDTO.arrivalDayOffset] accounts for any date difference
     * between the two legs' departure dates plus the second leg's own day offset.
     *
     * @receiver the [ConnectingFlight] to convert
     * @return a [FlightDisplayDTO] representing this connecting flight
     */
    private fun ConnectingFlight.toDisplayDTO() =
        FlightDisplayDTO(
            flightId = "${this.leg1.flightId}_${this.leg2.flightId}",
            isConnecting = true,
            aircraftType = "${this.leg1.aircraftType}_${this.leg2.aircraftType}",
            departureAirport = this.leg1.departureAirportName,
            arrivalAirport = this.leg2.arrivalAirportName,
            departureTime = this.leg1.departureTime,
            arrivalTime = this.leg2.arrivalTime,
            arrivalDayOffset =
                (this.leg2.departureDate.toEpochDay() - this.leg1.departureDate.toEpochDay()).toInt() + this.leg2.arrivalDayOffset,
            totalDurationDisplay = "${this.totalDurationMinutes / 60}h ${this.totalDurationMinutes % 60}m",
            // Only calculate the sum if BOTH legs offer the cabin; otherwise null
            priceEconomy =
                if (leg1.priceEconomy != null && leg2.priceEconomy != null) {
                    leg1.priceEconomy + leg2.priceEconomy
                } else {
                    null
                },
            priceBusiness =
                if (leg1.priceBusiness != null && leg2.priceBusiness != null) {
                    leg1.priceBusiness + leg2.priceBusiness
                } else {
                    null
                },
            priceFirst =
                if (leg1.priceFirst != null && leg2.priceFirst != null) {
                    leg1.priceFirst + leg2.priceFirst
                } else {
                    null
                },
            stopCity = this.leg1.arrivalAirportName,
            layoverMinutes = this.layoverMinutes,
            departureTerminal = this.leg1.departureTerminal,
            arrivalTerminal = this.leg2.arrivalTerminal,
        )
}
