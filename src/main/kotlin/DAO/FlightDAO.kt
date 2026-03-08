package com.flightbooking

import java.time.LocalDate
import java.time.LocalTime


object FlightDAO {

    fun searchFlights(
        departure: String,
        arrival: String,
        date: LocalDate
    ): List<Flight> {

        val sql = """
            SELECT 
                f.flight_id,
                f.flight_date,
                f.price,
                r.flight_number,
                r.departure_terminal,
                r.arrival_terminal,
                r.planned_departure,
                r.base_duration_minutes,
                dep.city as departure_city,
                arr.city as arrival_city,
                dep.name as departure_airport_name,
                arr.name as arrival_airport_name
            FROM flights f
            JOIN routes r ON f.route_id = r.route_id
            JOIN airports dep ON r.departure_airport = dep.airport_id
            JOIN airports arr ON r.arrival_airport = arr.airport_id
            WHERE (dep.city = ? OR dep.airport_id = ?)
              AND (arr.city = ? OR arr.airport_id = ?)
              AND f.flight_date = ?
              AND f.status != 'Cancelled'
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

            while (result.next()) {
                val durationMinutes = result.getInt("base_duration_minutes")

                val departureTime = LocalTime.parse(result.getString("planned_departure"))
                val arrivalTime = departureTime.plusMinutes(durationMinutes.toLong())

                flights.add(Flight(
                    flightId = result.getInt("flight_id"),
                    flightNumber = result.getString("flight_number"),
                    departureCity = result.getString("departure_city"),
                    arrivalCity = result.getString("arrival_city"),
                    departureAirportName = result.getString("departure_airport_name") ?: "",
                    arrivalAirportName = result.getString("arrival_airport_name") ?: "",
                    departureTerminal = result.getString("departure_terminal") ?: "",
                    arrivalTerminal = result.getString("arrival_terminal") ?: "",
                    departureDate = LocalDate.parse(result.getString("flight_date")),
                    departureTime = departureTime,
                    arrivalTime = arrivalTime,
                    durationMinutes = durationMinutes,
                    price = result.getDouble("price"),
                ))
            }
            return flights
        }
    }
}