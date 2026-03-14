package com.flightbooking

import java.sql.ResultSet
import java.time.LocalDate
import java.time.LocalTime


object FlightDAO {

    private fun mapResultToFlight(result: ResultSet): Flight {
        val durationMinutes = result.getInt("base_duration_minutes")
        val departureTime = LocalTime.parse(result.getString("planned_departure"))
        val arrivalTime = departureTime.plusMinutes(durationMinutes.toLong())

        return Flight(
            flightId             = result.getInt("flight_id"),
            flightNumber         = result.getString("flight_number"),
            aircraftType         = result.getString("aircraft_type") ?: "",
            departureCity        = result.getString("departure_city"),
            arrivalCity          = result.getString("arrival_city"),
            departureAirportName = result.getString("departure_airport_name") ?: "",
            arrivalAirportName   = result.getString("arrival_airport_name") ?: "",
            departureTerminal    = result.getString("departure_terminal") ?: "",
            arrivalTerminal      = result.getString("arrival_terminal") ?: "",
            departureDate        = LocalDate.parse(result.getString("flight_date")),
            departureTime        = departureTime,
            arrivalTime          = arrivalTime,
            durationMinutes      = durationMinutes,
            price                = result.getDouble("price")
        )
    }

    fun searchFlights(
        departure: String,
        arrival: String,
        date: LocalDate
    ): List<Flight> {

        val sql = """
            SELECT 
                f.flight_id, f.flight_date, f.price,
                r.flight_number, r.aircraft_type,
                r.departure_terminal, r.arrival_terminal,
                r.planned_departure, r.base_duration_minutes,
                dep.city as departure_city, arr.city as arrival_city,
                dep.name as departure_airport_name, arr.name as arrival_airport_name
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
        minLayoverMinutes: Int = 45,
        maxLayoverMinutes: Int = 480
    ): List<ConnectingFlight> {

        val outboundSql = """
            SELECT 
                f.flight_id, f.flight_date, f.price,
                r.flight_number, r.aircraft_type,
                r.departure_terminal, r.arrival_terminal,
                r.planned_departure, r.base_duration_minutes,
                dep.city as departure_city, arr.city as arrival_city,
                dep.name as departure_airport_name, arr.name as arrival_airport_name,
                arr.airport_id as arrival_airport_id
            FROM flights f
            JOIN routes r ON f.route_id = r.route_id
            JOIN airports dep ON r.departure_airport = dep.airport_id
            JOIN airports arr ON r.arrival_airport = arr.airport_id
            WHERE (dep.city = ? OR dep.airport_id = ?)
              AND f.flight_date = ?
              AND f.status != 'Cancelled'
        """

        val inboundSql = """
            SELECT 
                f.flight_id, f.flight_date, f.price,
                r.flight_number, r.aircraft_type,
                r.departure_terminal, r.arrival_terminal,
                r.planned_departure, r.base_duration_minutes,
                dep.city as departure_city, arr.city as arrival_city,
                dep.name as departure_airport_name, arr.name as arrival_airport_name,
                dep.airport_id as departure_airport_id
            FROM flights f
            JOIN routes r ON f.route_id = r.route_id
            JOIN airports dep ON r.departure_airport = dep.airport_id
            JOIN airports arr ON r.arrival_airport = arr.airport_id
            WHERE (arr.city = ? OR arr.airport_id = ?)
              AND f.flight_date = ?
              AND f.status != 'Cancelled'
        """

        Database.getConnection().use { conn ->

            val outboundFlights = mutableListOf<Pair<Flight, String>>()
            conn.prepareStatement(outboundSql).use { stmt ->
                stmt.setString(1, departure)
                stmt.setString(2, departure)
                stmt.setString(3, date.toString())
                val result = stmt.executeQuery()
                while (result.next()) {
                    outboundFlights.add(Pair(mapResultToFlight(result), result.getString("arrival_airport_id")))
                }
            }

            val inboundFlights = mutableListOf<Pair<Flight, String>>()
            conn.prepareStatement(inboundSql).use { stmt ->
                stmt.setString(1, arrival)
                stmt.setString(2, arrival)
                stmt.setString(3, date.toString())
                val result = stmt.executeQuery()
                while (result.next()) {
                    inboundFlights.add(Pair(mapResultToFlight(result), result.getString("departure_airport_id")))
                }
            }

            val connectingFlights = mutableListOf<ConnectingFlight>()
            for ((leg1, connectingAirport) in outboundFlights) {
                for ((leg2, leg2Departure) in inboundFlights) {
                    if (connectingAirport != leg2Departure) continue

                    val layover = java.time.Duration.between(leg1.arrivalTime, leg2.departureTime).toMinutes()
                    if (layover < minLayoverMinutes || layover > maxLayoverMinutes) continue

                    connectingFlights.add(ConnectingFlight(
                        leg1 = leg1,
                        leg2 = leg2,
                        totalDurationMinutes = leg1.durationMinutes + layover.toInt() + leg2.durationMinutes,
                        layoverMinutes = layover.toInt(),
                        totalPrice = leg1.price + leg2.price
                    ))
                }
            }

            return connectingFlights.sortedBy { it.leg1.departureTime }
        }
    }

    fun getFlightOverview(flightId: Int): Flight? {
        val sql = """
            SELECT
                f.flight_id, f.flight_date, f.price,
                r.flight_number, r.aircraft_type,
                r.departure_terminal, r.arrival_terminal,
                r.planned_departure, r.base_duration_minutes,
                dep.city as departure_city, arr.city as arrival_city,
                dep.name as departure_airport_name, arr.name as arrival_airport_name
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
}