package com.flightbooking

import java.sql.ResultSet
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object FlightDAO {
    /**
     * Core function: Obtain the merged list of direct and connecting flights.
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

            val inbound = mutableListOf<Pair<Flight, String>>()
            conn.prepareStatement(inboundSql).use { stmt ->
                stmt.setString(1, arrival)
                stmt.setString(2, arrival)
                stmt.setString(3, date.toString())
                val rs = stmt.executeQuery()
                while (rs.next()) inbound.add(mapResultToFlight(rs) to rs.getString("departure_airport_id"))
            }

            val connections = mutableListOf<ConnectingFlight>()
            for ((leg1, midId1) in outbound) {
                for ((leg2, midId2) in inbound) {
                    if (midId1 != midId2) continue
                    val layover = Duration.between(leg1.arrivalTime, leg2.departureTime).toMinutes()
                    if (layover in minLayover..maxLayover) {
                        // Fix: null check
                        // Use economy as the base totalPrice for the ConnectingFlight object
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

    private fun Flight.toDisplayDTO() =
        FlightDisplayDTO(
            flightId = this.flightId.toString(),
            isConnecting = false,
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

    private fun ConnectingFlight.toDisplayDTO() =
        FlightDisplayDTO(
            flightId = "${this.leg1.flightId}_${this.leg2.flightId}",
            isConnecting = true,
            departureAirport = this.leg1.departureAirportName,
            arrivalAirport = this.leg2.arrivalAirportName,
            departureTime = this.leg1.departureTime,
            arrivalTime = this.leg2.arrivalTime,
            arrivalDayOffset =
                (this.leg2.departureDate.toEpochDay() - this.leg1.departureDate.toEpochDay()).toInt() + this.leg2.arrivalDayOffset,
            totalDurationDisplay = "${this.totalDurationMinutes / 60}h ${this.totalDurationMinutes % 60}m",
            // Only calculate sum if BOTH legs have the cabin available
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
            stopCity = this.leg1.arrivalCity,
            layoverMinutes = this.layoverMinutes,
        )
}
