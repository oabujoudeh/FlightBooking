package com.flightbooking

/**
 * DAO object used for admin database queries.
 *
 * This object contains functions that help fetch data for the admin side of
 * the flight booking system. It is used for the dashboard
 * information, such as total users, booking statistics, recent bookings,
 * cancellations, upcoming flights, and busiest routes.
 *
 * Each function connects to the database, runs a query, and returns the
 * results in a simple format that can be used by the rest of the program.
 *
 * If a query fails, the functions usually return a default value like `0`
 * or an empty list instead of crashing the program.
 */
object AdminDAO {
    /**
    * Returns the total number of users stored in the database.
    *
    * This function executes a SQL `COUNT(*)` query on the `users` table and
    * returns the result as an `Int`.
    *
    * If the query fails or any database error occurs, the function returns `0`.
    *
    * @return the total number of users in the `users` table, or `0` if the query fails
    */
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


    /**
    * Retrieves all bookings grouped by booking date.
    *
    * This function queries the `bookings` table and groups records by the date
    * portion of `booking_date`. For each date, it returns the total number of
    * bookings and the total revenue generated.
    *
    * Each result map contains:
    * - `"date"`: the booking date as a `String`
    * - `"count"`: the number of bookings on that date
    * - `"revenue"`: the total revenue for that date as a `Double`
    *
    * The results are ordered by date in ascending order.
    *
    * If an error occurs while accessing the database, an empty list is returned.
    *
    * @return a list of maps containing the date, booking count, and total revenue
    * for each booking date, or an empty list if the query fails
    */
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


    /**
    * Retrieves the number of bookings for each booking status.
    *
    * This function queries the `bookings` table, groups records by `status`,
    * and counts how many bookings exist for each status value.
    *
    * Each result map contains:
    * - `"status"`: the booking status as a `String`
    * - `"count"`: the number of bookings with that status as an `Int`
    *
    * If an error occurs while querying the database, an empty list is returned.
    *
    * @return a list of maps containing each booking status and its corresponding
    * count, or an empty list if the query fails
    */
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


    /**
    * Retrieves the most recent bookings from the database.
    *
    * This function queries the `bookings` table, orders bookings by
    * `booking_date` in descending order, and returns up to the specified
    * number of results.
    *
    * Each result map contains:
    * - `"bookingId"`: the booking ID as an `Int`
    * - `"bookingDate"`: the booking date as a `String`
    * - `"totalPrice"`: the total booking price as a `Double`
    * - `"status"`: the booking status as a `String`
    * - `"contactEmail"`: the contact email associated with the booking
    *
    * If an error occurs while querying the database, an empty list is returned.
    *
    * @param limit the maximum number of recent bookings to return; defaults to 20
    * @return a list of maps containing recent booking details, or an empty list
    * if the query fails
    */
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


    /**
    * Retrieves the most recent cancelled bookings from the database.
    *
    * This function queries the `bookings` table for records with a status of
    * `"cancelled"`, orders them by `booking_date` in descending order, and
    * returns up to the specified number of results.
    *
    * Each result map contains:
    * - `"bookingId"`: the booking ID as an `Int`
    * - `"bookingDate"`: the booking date as a `String`
    * - `"totalPrice"`: the total price of the booking as a `Double`
    * - `"contactEmail"`: the contact email associated with the booking
    *
    * If an error occurs while querying the database, an empty list is returned.
    *
    * @param limit the maximum number of recent cancelled bookings to return; defaults to 20
    * @return a list of maps containing recent cancelled booking details, or an empty list
    * if the query fails
    */
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


    /**
    * Gets a list of upcoming flights from the database.
    *
    * This function looks for flights whose `flight_date` is today or later.
    * It joins the `flights`, `routes`, and `airports` tables so it can return extra information like the flight number, departure time, and the
    * departure and arrival cities.
    *
    * The flights are sorted by flight date first, and then by planned departure time. The number of results returned is limited by the `limit`
    * parameter.
    *
    * Each map in the returned list contains:
    * - `"flightId"`: the flight ID
    * - `"flightDate"`: the date of the flight
    * - `"status"`: the current flight status
    * - `"flightNumber"`: the flight number
    * - `"departureTime"`: the planned departure time
    * - `"departureCity"`: the city the flight leaves from
    * - `"arrivalCity"`: the city the flight arrives at
    * - `"price"`: the price of the flight
    *
    * If something goes wrong with the database query, the function returns an
    * empty list.
    *
    * @param limit the maximum number of upcoming flights to return, default is 20
    * @return a list of maps containing upcoming flight details, or an empty list if the query fails
    */
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


    /**
    * Gets the busiest routes based on how many bookings they have.
    *
    * This function checks the booking and flight tables, joins them with the
    * route and airport tables, and works out which routes have the highest number of bookings.
    *
    * The results are ordered from most booked to least booked. The `limit`parameter controls how many routes are returned.
    *
    * Each map in the list contains:
    * - `"departureCity"`: the city the flight leaves from
    * - `"arrivalCity"`: the city the flight goes to
    * - `"flightNumber"`: the flight number for the route
    * - `"bookingCount"`: how many bookings that route has
    *
    * If there is a problem with the database, the function returns an empty list.
    *
    * @param limit the max number of routes to return, default is 10
    * @return a list of maps with the busiest route details, or an empty list if the query fails
    */
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

    fun trackReservations(filterDate: String? = null, filterUsername: String? = null, filterStatus: String? = null): List<Map<String, Any>> {
        
        var sql = """
            SELECT b.booking_id,
                   b.booking_date,
                   b.total_price,
                   b.status,
                   b.contact_email,
                   u.first_name,
                   u.last_name,
                   (SELECT GROUP_CONCAT(p.full_name)
                    FROM booking_passengers p
                    WHERE p.booking_id = b.booking_id) AS passenger_names
            FROM bookings b
            JOIN users u ON b.user_id = u.user_id 
            WHERE 1=1
        """

        if (filterDate != null && filterDate.isNotEmpty()) {
            sql += " AND strftime('%Y-%m-%d', b.booking_date) = ?"
        }

        if (filterUsername != null && filterUsername.isNotEmpty()) {
            sql += """ AND(
                u.first_name LIKE ?
                OR u.last_name LIKE ?
                OR EXISTS (SELECT 1 FROM booking_passengers bp WHERE bp.booking_id = b.booking_id AND bp.full_name LIKE ?)
            )"""
        }

        if (filterStatus != null && filterStatus.isNotEmpty()) {
            sql += " AND b.status = ?"
        }

        sql += " ORDER BY b.booking_date DESC"

        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    var paramIndex = 1

                    if (filterDate != null && filterDate.isNotEmpty()) {
                        stmt.setString(paramIndex++, filterDate)
                    }

                    if (filterUsername != null && filterUsername.isNotEmpty()) {
                        val searchPattern = "%$filterUsername%"
                        stmt.setString(paramIndex++, searchPattern) 
                        stmt.setString(paramIndex++, searchPattern) 
                    }

                    if (filterStatus != null && filterStatus.isNotEmpty()) {
                        stmt.setString(paramIndex++, filterStatus)
                    }

                    println("Debug: SQL query is $sql")
                    stmt.executeQuery().use { rs ->
                        val results = mutableListOf<Map<String, Any>>()
                        while (rs.next()) {
                            val row = mutableMapOf<String, Any>()
                            
                            row["bookingId"] = rs.getInt("booking_id")
                            row["bookingDate"] = rs.getString("booking_date") ?: ""
                            row["totalPrice"] = rs.getDouble("total_price")
                            row["status"] = rs.getString("status") ?: ""
                            row["contactEmail"] = rs.getString("contact_email") ?: ""
                            row["bookedBy"] = (rs.getString("first_name") ?: "") + " " + (rs.getString("last_name") ?: "")
                            row["passengers"] = rs.getString("passenger_names") ?: "N/A"

                            results.add(row)
                        }
                        results
                    }
                }
            }
        } catch (e: Exception) {
            println("Search Error:")
            e.printStackTrace()
            emptyList()
        }
    }

    fun trackFlights(filterDate: String? = null, filterNumber: String? = null): List<Map<String, Any>> {
        var sql = """
            SELECT f.flight_id, 
                f.flight_date, 
                f.status, 
                r.flight_number, 
                dep.city AS dep_city, 
                arr.city AS arr_city
            FROM flights f
            JOIN routes r ON f.route_id = r.route_id
            JOIN airports dep ON r.departure_airport = dep.airport_id
            JOIN airports arr ON r.arrival_airport = arr.airport_id
            WHERE 1=1
        """

        if (!filterDate.isNullOrEmpty()) {
            sql += " AND f.flight_date = ?"
        }
        if (!filterNumber.isNullOrEmpty()) {
            sql += " AND r.flight_number LIKE ?"
        }

        sql += " ORDER BY f.flight_date ASC, r.flight_number ASC"

        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    var paramIndex = 1
                    if (!filterDate.isNullOrEmpty()) {
                        stmt.setString(paramIndex++, filterDate)
                    }
                    if (!filterNumber.isNullOrEmpty()) {
                        stmt.setString(paramIndex++, "%$filterNumber%")
                    }

                    stmt.executeQuery().use { rs ->
                        val results = mutableListOf<Map<String, Any>>()
                        while (rs.next()) {
                            val row = mutableMapOf<String, Any>()
                            row["flightId"] = rs.getInt("flight_id")
                            row["flightDate"] = rs.getString("flight_date") ?: ""
                            row["status"] = rs.getString("status") ?: ""
                            row["flightNumber"] = rs.getString("flight_number") ?: ""
                            row["originCity"] = rs.getString("dep_city") ?: ""
                            row["destCity"] = rs.getString("arr_city") ?: ""
                            results.add(row)
                        }
                        results
                    }
                }
            }
        } catch (e: Exception) {
            println("TrackFlights Error:")
            e.printStackTrace()
            emptyList()
        }
    }

    fun updateFlightStatus(flightId: Int, newStatus: String): Boolean {
        val allowedStatuses = setOf("Scheduled", "Delayed", "Cancelled", "Departed", "Landed")
        if (newStatus !in allowedStatuses) return false

        val sql = "UPDATE flights SET status = ? WHERE flight_id = ?"
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, newStatus)
                    stmt.setInt(2, flightId)
                    stmt.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            println("UpdateFlightStatus Error:")
            e.printStackTrace()
            false
        }
    }
    
    fun getBookingsPerFlight(limit: Int = 20): List<Map<String, Any>> {
        val sql = """
            SELECT r.flight_number,
                f.flight_date,
                dep.city AS origin,
                arr.city AS dest,
                COUNT(bf.booking_id) AS booking_count
            FROM flights f
            JOIN routes r ON f.route_id = r.route_id
            JOIN airports dep ON r.departure_airport = dep.airport_id
            JOIN airports arr ON r.arrival_airport = arr.airport_id
            LEFT JOIN booking_flights bf ON f.flight_id = bf.flight_id
            GROUP BY f.flight_id, r.flight_number, f.flight_date, dep.city, arr.city
            ORDER BY f.flight_date DESC, booking_count DESC
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
                                "flightNumber" to (rs.getString("flight_number") ?: ""),
                                "flightDate"   to (rs.getString("flight_date") ?: ""),
                                "origin"       to (rs.getString("origin") ?: ""),
                                "dest"         to (rs.getString("dest") ?: ""),
                                "bookingCount" to rs.getInt("booking_count")
                            ))
                        }
                        results
                    }
                }
            }
        } catch (e: Exception) {
            println("GetBookingsPerFlight Error:")
            e.printStackTrace()
            emptyList()
        }
    }

}
