package com.flightbooking

/**
 * Data Access Object for airport and city lookup operations.
 *
 * Provides search and label resolution for use in flight booking UI fields
 * where users need to find departure and arrival locations.
 */
object AirportDAO {

    /**
     * Searches for airports and cities matching a query string.
     *
     * Queries the `airports` table in two passes. First, distinct city names
     * are matched to produce city-level entries (representing all airports in
     * that city). Then individual airports are matched by city name, airport
     * name, or airport ID. City results appear before airport results in the
     * returned list.
     *
     * Each result map contains:
     * - `"label"`: the display text shown to the user
     * - `"value"`: the value passed to the flight search (city name or airport ID)
     * - `"type"`: either `"city"` or `"airport"`
     *
     * @param query the search string typed by the user
     * @return a list of matching city and airport result maps, cities first
     */
    fun searchAirport(query: String): List<Map<String, String>> {
        // Match city (returns one entry covering all airports in that city)
        val citySql = """
            SELECT DISTINCT city 
            FROM airports 
            WHERE LOWER(city) LIKE LOWER(?) 
            LIMIT 5
        """
        // Match individual airport by city, name, or IATA code
        val airportSql = """
            SELECT airport_id, name, city 
            FROM airports 
            WHERE LOWER(city) LIKE LOWER(?)
               OR LOWER(name) LIKE LOWER(?)
               OR LOWER(airport_id) LIKE LOWER(?)
            LIMIT 10
        """
        val pattern = "%$query%"
        val results = mutableListOf<Map<String, String>>()

        Database.getConnection().use { conn ->
            // City options first
            conn.prepareStatement(citySql).use { stmt ->
                stmt.setString(1, pattern)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val city = rs.getString("city")
                    results.add(
                        mapOf(
                            "label" to "$city (all airports)",
                            "value" to city, // city name is passed to flight search
                            "type" to "city",
                        ),
                    )
                }
            }
            // Individual airport options
            conn.prepareStatement(airportSql).use { stmt ->
                stmt.setString(1, pattern)
                stmt.setString(2, pattern)
                stmt.setString(3, pattern)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val code = rs.getString("airport_id")
                    val name = rs.getString("name")
                    val city = rs.getString("city")
                    results.add(
                        mapOf(
                            "label" to "$city - $name ($code)",
                            "value" to code,
                            "type" to "airport",
                        ),
                    )
                }
            }
        }
        return results
    }

    /**
     * Resolves a city name or airport ID to a human-readable display label.
     *
     * Looks up [value] in the `airports` table, matching against both the
     * airport ID and city name columns. The returned format depends on what
     * was matched:
     *
     * - City match → `"City (all airports)"`
     * - Airport ID match → `"City - Airport Name (Code)"`
     *
     * If no record is found the original [value] is returned unchanged.
     *
     * @param value the airport ID or city name to resolve
     * @return a formatted display label, or [value] itself if no match is found
     */
    fun getLabel(value: String): String {
        val sql = """
            SELECT airport_id, name, city FROM airports
            WHERE airport_id = ? OR city = ?
            LIMIT 1
        """
        Database.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, value)
                stmt.setString(2, value)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    val code = rs.getString("airport_id")
                    val name = rs.getString("name")
                    val city = rs.getString("city")
                    return if (value == city) {
                        "$city (all airports)"
                    } else {
                        "$city - $name ($code)"
                    }
                }
            }
        }
        return value // fallback: return the original value if no match found
    }
}