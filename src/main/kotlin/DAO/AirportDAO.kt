package com.flightbooking


object AirportDAO {


    /**
    * Searches for airports or cities that match a text.
    *
    * This function looks in the `airports` table for matches based on the search query. It first finds matching cities and adds them as options
    * for searching all airports in that city. After that, it looks for individual airports that match by city name, airport name, or airport ID.
    *
    * The returned list contains maps with:
    * - `"label"`: the text shown to the user
    * - `"value"`: the value used for the search
    * - `"type"`: whether the result is a `"city"` or an `"airport"`
    *
    * City matches are added first, followed by airport matches.
    *
    * @param query the text to search for
    * @return a list of matching city and airport results
    */
    fun searchAirport(query: String): List<Map<String, String>> {
        // match city (all airports)
        val citySql = """
            SELECT DISTINCT city 
            FROM airports 
            WHERE LOWER(city) LIKE LOWER(?) 
            LIMIT 5
        """
        // match airport
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
            // city options first
            conn.prepareStatement(citySql).use { stmt ->
                stmt.setString(1, pattern)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val city = rs.getString("city")
                    results.add(mapOf(
                        "label" to "$city (all airports)",
                        "value" to city,  // send city to search
                        "type" to "city"
                    ))
                }
            }
            // airport options
            conn.prepareStatement(airportSql).use { stmt ->
                stmt.setString(1, pattern)
                stmt.setString(2, pattern)
                stmt.setString(3, pattern)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val code = rs.getString("airport_id")
                    val name = rs.getString("name")
                    val city = rs.getString("city")
                    results.add(mapOf(
                        "label" to "$city - $name ($code)",
                        "value" to code,
                        "type" to "airport"
                    ))
                }
            }
        }
        return results
    }

    /**
    * Gets a display label for a city or airport value.
    *
    * This function checks the `airports` table to see if the given value
    * matches either an airport ID or a city name. If it finds a match,
    * it returns a label in a user-friendly format.
    *
    * If the value matches a city, the label will be like:
    * `"City (all airports)"`
    *
    * If the value matches an airport ID, the label will be like:
    * `"City - Airport Name (Code)"`
    *
    * If nothing is found in the database, the original value is returned.
    *
    * @param value the airport ID or city name to look up
    * @return a formatted label for the value, or the original value if no match is found
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
                    return if (value == city) "$city (all airports)"
                        else "$city - $name ($code)"
                }
            }
        }
        return value  // fallback
    }
}