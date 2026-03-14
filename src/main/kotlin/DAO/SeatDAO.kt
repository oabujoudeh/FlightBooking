package com.flightbooking


private fun generateSeats(flightId: Int, aircraftType: String) {
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
                                val isOccupied = if (Math.random() < 0.3) 1 else 0
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