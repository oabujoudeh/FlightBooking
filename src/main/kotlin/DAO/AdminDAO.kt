package com.flightbooking

object AdminDAO {

    fun getAllBookingsGroupedByDate(): List<Map<String, Any>> {
        val sql = """
            SELECT DATE(booking_date) as date, COUNT(*) as count, SUM(total_price) as revenue
            FROM bookings
            GROUP BY DATE(booking_date)
            ORDER BY date ASC
        """
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.executeQuery().use { rs ->
                        val results = mutableListOf<Map<String, Any>>()
                        while (rs.next()) {
                            results.add(mapOf(
                                "date" to (rs.getString("date") ?: ""),
                                "count" to rs.getInt("count"),
                                "revenue" to rs.getDouble("revenue")
                            ))
                        }
                        results
                    }
                }
            }
        } catch (e: Exception) {
            println("Error getting bookings by date: ${e.message}")
            emptyList()
        }
    }

    fun getBookingStatusCounts(): List<Map<String, Any>> {
        val sql = """
            SELECT status, COUNT(*) as count
            FROM bookings
            GROUP BY status
        """
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.executeQuery().use { rs ->
                        val results = mutableListOf<Map<String, Any>>()
                        while (rs.next()) {
                            results.add(mapOf(
                                "status" to rs.getString("status"),
                                "count" to rs.getInt("count")
                            ))
                        }
                        results
                    }
                }
            }
        } catch (e: Exception) {
            println("Error getting booking status counts: ${e.message}")
            emptyList()
        }
    }

    fun getBusiestRoutes(limit: Int = 10): List<Map<String, Any>> {
        val sql = """
            SELECT dep.city as departure_city, arr.city as arrival_city,
                   r.flight_number, COUNT(*) as booking_count
            FROM booking_flights bf
            JOIN flights f ON bf.flight_id = f.flight_id
            JOIN routes r ON f.route_id = r.route_id
            JOIN airports dep ON r.departure_airport = dep.airport_id
            JOIN airports arr ON r.arrival_airport = arr.airport_id
            GROUP BY r.route_id
            ORDER BY booking_count DESC
            LIMIT ?
        """
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, limit)
                    stmt.executeQuery().use { rs ->
                        val results = mutableListOf<Map<String, Any>>()
                        while (rs.next()) {
                            results.add(mapOf(
                                "departureCity" to rs.getString("departure_city"),
                                "arrivalCity" to rs.getString("arrival_city"),
                                "flightNumber" to rs.getString("flight_number"),
                                "bookingCount" to rs.getInt("booking_count")
                            ))
                        }
                        results
                    }
                }
            }
        } catch (e: Exception) {
            println("Error getting busiest routes: ${e.message}")
            emptyList()
        }
    }
}
