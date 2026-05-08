package com.flightbooking

/**
 * Data Access Object for flight seat operations.
 *
 * Handles seat retrieval and on-demand generation for flights based on
 * their assigned aircraft configuration.
 */
object SeatDAO {
    /**
     * Returns all seats for a flight, generating them first if they do not yet exist.
     *
     * This is the primary entry point for seat access. If no seat records are found
     * for the given flight, they are created from the aircraft layout config before
     * being returned.
     *
     * @param flightId the ID of the flight whose seats to retrieve
     * @param aircraftType the aircraft type string used to look up the seat layout config
     * @return a list of [Seat] objects for the flight
     */
    fun getOrGenerateSeats(
        flightId: Int,
        aircraftType: String,
    ): List<Seat> {
        if (!seatsExist(flightId)) {
            generateSeats(flightId, aircraftType)
        }
        return getSeats(flightId)
    }

    /**
     * Checks whether seat records already exist for a given flight.
     *
     * @param flightId the ID of the flight to check
     * @return true if at least one seat row exists for the flight, otherwise false
     */
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

    /**
     * Fetches all seat records for a flight from the database.
     *
     * Results are returned in the order the database yields them; callers that
     * need a specific order (e.g. ascending seat number) should sort after calling.
     *
     * @param flightId the ID of the flight whose seats to fetch
     * @return a list of [Seat] objects for the flight; empty if none found
     */
    private fun getSeats(flightId: Int): List<Seat> {
        val sql = """
            SELECT seat_id, flight_id, seat_number, class, is_occupied 
            FROM seats 
            WHERE flight_id = ?
        """
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

    /**
     * Generates and persists seat records for a flight from the aircraft layout config.
     *
     * Looks up the cabin and deck layout for [aircraftType] via [AircraftConfigs],
     * then inserts one row per seat. For multi-deck aircraft (e.g. A380), seat numbers
     * are prefixed with `"M"` (Main Deck) or `"U"` (Upper Deck) to avoid collisions.
     * All generated seats start as unoccupied.
     *
     * @param flightId the ID of the flight to generate seats for
     * @param aircraftType the aircraft type string used to look up the layout config
     */
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
                    // Only apply a deck prefix for multi-deck aircraft
                    val prefix = if (config.decks.size > 1) deckPrefix[deck.deckName] ?: "" else ""
                    for (cabin in deck.cabins) {
                        for (row in cabin.rows) {
                            for (group in cabin.layout) {
                                for (col in group) {
                                    stmt.setInt(1, flightId)
                                    stmt.setString(2, "$prefix$row$col")
                                    stmt.setString(3, cabin.seatClass)
                                    stmt.setInt(4, 0) // all seats start unoccupied
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
