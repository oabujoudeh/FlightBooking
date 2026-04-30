package com.flightbooking

object AirportDAO {
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
                    results.add(
                        mapOf(
                            "label" to "$city (all airports)",
                            "value" to city, // send city to search
                            "type" to "city",
                        ),
                    )
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
        return value // fallback
    }
}
