package com.flightbooking

object SeatDAO {
    fun getOrGenerateSeats(
        flightId: Int,
        aircraftType: String,
    ): List<Seat> {
        if (!seatsExist(flightId)) {
            generateSeats(flightId, aircraftType)
        }
        return getSeats(flightId)
    }

    private fun seatsExist(flightId: Int): Boolean {
        val sql = "SELECT COUNT(*) FROM seats WHERE flight_id = ?"
        Database.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, flightId)
                val rs = stmt.executeQuery()
                return rs.next() && rs.getInt(1) > 0
            }
        }
    }

    private fun getSeats(flightId: Int): List<Seat> {
        val sql = """
            SELECT seat_id, flight_id, seat_number, class, is_occupied 
            FROM seats 
            WHERE flight_id = ?
        """
        // seat number acsending order
        val seats = mutableListOf<Seat>()
        Database.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, flightId)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    seats.add(
                        Seat(
                            seatId = rs.getInt("seat_id"),
                            flightId = rs.getInt("flight_id"),
                            seatNumber = rs.getString("seat_number"),
                            seatClass = rs.getString("class"),
                            isOccupied = rs.getInt("is_occupied") == 1,
                        ),
                    )
                }
            }
        }
        return seats
    }

    private fun generateSeats(
        flightId: Int,
        aircraftType: String,
    ) {
        val config = AircraftConfigs.getConfig(aircraftType)
        val sql = "INSERT INTO seats (flight_id, seat_number, class, is_occupied) VALUES (?, ?, ?, ?)"
        val deckPrefix = mapOf("Main Deck" to "M", "Upper Deck" to "U")

        Database.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                for (deck in config.decks) {
                    val prefix = if (config.decks.size > 1) deckPrefix[deck.deckName] ?: "" else ""
                    for (cabin in deck.cabins) {
                        for (row in cabin.rows) {
                            for (group in cabin.layout) {
                                for (col in group) {
                                    val isOccupied = 0
                                    stmt.setInt(1, flightId)
                                    stmt.setString(2, "$prefix$row$col")
                                    stmt.setString(3, cabin.seatClass)
                                    stmt.setInt(4, isOccupied)
                                    stmt.addBatch()
                                }
                            }
                        }
                    }
                }
                stmt.executeBatch()
            }
        }
    }
}
