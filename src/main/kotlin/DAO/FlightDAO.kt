object FlightDAO {

    fun searchFlights(
        departureCity: String,
        arrivalCity: String,
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
            FROM flights f
            JOIN routes r ON f.route_id = r.route_id
            JOIN airports dep ON r.departure_airport = dep.airport_id
            JOIN airports arr ON r.arrival_airport = arr.airport_id
            WHERE dep.city = ? 
              AND arr.city = ? 
              AND f.flight_date = ?
        """
        

        getConnection().use { conn ->
            val stmt = conn.prepareStatement(sql)
            stmt.setString(1, departureCity)
            stmt.setString(2, arrivalCity)
            stmt.setString(3, date.toString())
            
            val result = stmt.executeQuery()
            val flights = mutableListOf<Flight>()
            
            while (result.next()) {
                // get departure time string
                val departureTimeStr = result.getString("planned_departure")
                val departureTime = LocalTime.parse(departureTimeStr)
                
                // get duration
                val durationMinutes = result.getInt("base_duration_minutes")
                
                // calculate arrival time
                val arrivalTime = departureTime.plusMinutes(durationMinutes.toLong())
                
                flights.add(Flight(
                    flightId = result.getInt("flight_id"),
                    flightNumber = result.getString("flight_number"),
                    departureCity = result.getString("departure_city"),
                    arrivalCity = result.getString("arrival_city"),
                    departureTerminal = result.getString("departure_terminal"),
                    arrivalTerminal = result.getString("arrival_terminal"),
                    departureDate = LocalDate.parse(result.getString("flight_date")),
                    departureTime = departureTime,
                    arrivalTime = arrivalTime,
                    price = result.getDouble("price"),
                ))
            }
            return flights
        }
    }
}