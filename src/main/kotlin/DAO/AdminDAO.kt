package com.flightbooking

object AdminDAO {
    fun getTotalUsers(): Int {
        val sql = "SELECT COUNT(*) FROM users"
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) rs.getInt(1) else 0
                    }
                }
            }
        } catch (e: Exception) {
            0
        }
    }

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
                            results.add(
                                mapOf(
                                    "date" to (rs.getString("date") ?: ""),
                                    "count" to rs.getInt("count"),
                                    "revenue" to rs.getDouble("revenue"),
                                ),
                            )
                        }
                        results
                    }
                }
            }
        } catch (e: Exception) {
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
                            results.add(
                                mapOf(
                                    "status" to rs.getString("status"),
                                    "count" to rs.getInt("count"),
                                ),
                            )
                        }
                        results
                    }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getRecentBookings(limit: Int = 20): List<Map<String, Any>> {
        val sql = """
            SELECT b.booking_id, b.booking_date, b.total_price, b.status, b.contact_email
            FROM bookings b
            ORDER BY b.booking_date DESC
            LIMIT ?
        """
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, limit)
                    stmt.executeQuery().use { rs ->
                        val results = mutableListOf<Map<String, Any>>()
                        while (rs.next()) {
                            results.add(
                                mapOf(
                                    "bookingId" to rs.getInt("booking_id"),
                                    "bookingDate" to (rs.getString("booking_date") ?: ""),
                                    "totalPrice" to rs.getDouble("total_price"),
                                    "status" to rs.getString("status"),
                                    "contactEmail" to (rs.getString("contact_email") ?: ""),
                                ),
                            )
                        }
                        results
                    }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getRecentCancellations(limit: Int = 20): List<Map<String, Any>> {
        val sql = """
            SELECT b.booking_id, b.booking_date, b.total_price, b.contact_email
            FROM bookings b
            WHERE b.status = 'cancelled'
            ORDER BY b.booking_date DESC
            LIMIT ?
        """
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, limit)
                    stmt.executeQuery().use { rs ->
                        val results = mutableListOf<Map<String, Any>>()
                        while (rs.next()) {
                            results.add(
                                mapOf(
                                    "bookingId" to rs.getInt("booking_id"),
                                    "bookingDate" to (rs.getString("booking_date") ?: ""),
                                    "totalPrice" to rs.getDouble("total_price"),
                                    "contactEmail" to (rs.getString("contact_email") ?: ""),
                                ),
                            )
                        }
                        results
                    }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getUpcomingFlights(limit: Int = 20): List<Map<String, Any>> {
        val sql = """
            SELECT f.flight_id, f.flight_date, f.status, f.price,
                   r.flight_number, r.planned_departure,
                   dep.city as departure_city, arr.city as arrival_city
            FROM flights f
            JOIN routes r ON f.route_id = r.route_id
            JOIN airports dep ON r.departure_airport = dep.airport_id
            JOIN airports arr ON r.arrival_airport = arr.airport_id
            WHERE f.flight_date >= DATE('now')
            ORDER BY f.flight_date ASC, r.planned_departure ASC
            LIMIT ?
        """
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, limit)
                    stmt.executeQuery().use { rs ->
                        val results = mutableListOf<Map<String, Any>>()
                        while (rs.next()) {
                            results.add(
                                mapOf(
                                    "flightId" to rs.getInt("flight_id"),
                                    "flightDate" to (rs.getString("flight_date") ?: ""),
                                    "status" to rs.getString("status"),
                                    "flightNumber" to rs.getString("flight_number"),
                                    "departureTime" to (rs.getString("planned_departure") ?: ""),
                                    "departureCity" to rs.getString("departure_city"),
                                    "arrivalCity" to rs.getString("arrival_city"),
                                    "price" to rs.getDouble("price"),
                                ),
                            )
                        }
                        results
                    }
                }
            }
        } catch (e: Exception) {
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
                            results.add(
                                mapOf(
                                    "departureCity" to rs.getString("departure_city"),
                                    "arrivalCity" to rs.getString("arrival_city"),
                                    "flightNumber" to rs.getString("flight_number"),
                                    "bookingCount" to rs.getInt("booking_count"),
                                ),
                            )
                        }
                        results
                    }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
