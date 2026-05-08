package com.flightbooking

import java.sql.Connection

/**
 * Data Access Object for admin dashboard queries.
 *
 * Provides read and write operations for the admin side of the flight booking
 * system. Covers dashboard statistics (user counts, booking trends, revenue),
 * reservation and flight tracking, change request approval/rejection, and
 * flight status management.
 *
 * All functions return safe default values (`0`, `null`, or empty lists) on
 * database errors rather than propagating exceptions.
 */
object AdminDAO {
    /**
     * Returns the display name and email for an admin account looked up by email.
     *
     * Queries the `admins` table for a row whose `email` column matches [email].
     * The returned map contains:
     * - `"firstName"`: the first word of `full_name`, or an empty string if not set
     * - `"lastName"`: the remainder of `full_name` after the first word, or empty
     * - `"email"`: the admin's email address
     *
     * @param email the admin's email address (used as the session username)
     * @return a map of profile fields, or a map of empty strings if not found
     */
    fun getAdminDetails(login: String): Map<String, String> {
        val sql = "SELECT full_name, email FROM admins WHERE email = ? OR username = ?"
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, login)
                    stmt.setString(2, login)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            mapOf(
                                "fullName" to (rs.getString("full_name") ?: ""),
                                "email" to (rs.getString("email") ?: ""),
                            )
                        } else {
                            mapOf("fullName" to "", "email" to "")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            mapOf("fullName" to "", "email" to "")
        }
    }

    /**
     * Returns the total number of registered users in the database.
     *
     * Executes a `COUNT(*)` query on the `users` table.
     *
     * @return the total user count, or `0` if the query fails
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
     * Returns booking counts and total revenue grouped by booking date.
     *
     * When [filterSeason] is provided, only bookings whose associated flight
     * belongs to that season are included; otherwise all bookings are counted.
     * Results are ordered by date descending.
     *
     * Each result map contains:
     * - `"date"`: the booking date as a `String`
     * - `"count"`: the number of bookings on that date as an `Int`
     * - `"revenue"`: the total revenue for that date as a `Double`
     *
     * @param filterSeason optional season string to filter by (e.g. `"Summer 2025"`);
     *        pass null or empty to include all seasons
     * @return a list of per-date booking summary maps, or an empty list on error
     */
    fun getAllBookingsGroupedByDate(filterSeason: String? = null): List<Map<String, Any>> {
        var sql: String
        if (!filterSeason.isNullOrEmpty()) {
            sql = """
                SELECT DATE(b.booking_date) as booking_date,
                    COUNT(*) as booking_count,
                    SUM(b.total_price) as total_revenue
                FROM bookings b
                JOIN booking_flights bf ON b.booking_id = bf.booking_id
                JOIN flights f ON bf.flight_id = f.flight_id
                JOIN routes r ON f.route_id = r.route_id
                WHERE r.season = ?
                GROUP BY DATE(b.booking_date)
                ORDER BY booking_date DESC
            """
        } else {
            sql = """
                SELECT DATE(b.booking_date) as booking_date,
                    COUNT(*) as booking_count,
                    SUM(b.total_price) as total_revenue
                FROM bookings b
                GROUP BY DATE(b.booking_date)
                ORDER BY booking_date DESC
            """
        }
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    if (!filterSeason.isNullOrEmpty()) {
                        stmt.setString(1, filterSeason)
                    }
                    stmt.executeQuery().use { rs ->
                        val results = mutableListOf<Map<String, Any>>()
                        while (rs.next()) {
                            results.add(
                                mapOf(
                                    "date" to (rs.getString("booking_date") ?: ""),
                                    "count" to rs.getInt("booking_count"),
                                    "revenue" to rs.getDouble("total_revenue"),
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
     * Returns the number of bookings for each booking status.
     *
     * Groups all records in the `bookings` table by `status` and counts each group.
     *
     * Each result map contains:
     * - `"status"`: the booking status string
     * - `"count"`: the number of bookings with that status as an `Int`
     *
     * @return a list of status-count maps, or an empty list if the query fails
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
     * Returns the most recent bookings across all users, ordered by booking date descending.
     *
     * Each result map contains:
     * - `"bookingId"`: the booking ID as an `Int`
     * - `"bookingDate"`: the booking date as a `String`
     * - `"totalPrice"`: the total booking price as a `Double`
     * - `"status"`: the booking status string
     * - `"contactEmail"`: the contact email associated with the booking
     *
     * @param limit the maximum number of bookings to return (default 20)
     * @return a list of recent booking maps, or an empty list if the query fails
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
     * Returns the most recent cancelled bookings, ordered by booking date descending.
     *
     * Each result map contains:
     * - `"bookingId"`: the booking ID as an `Int`
     * - `"bookingDate"`: the booking date as a `String`
     * - `"totalPrice"`: the total price of the booking as a `Double`
     * - `"contactEmail"`: the contact email associated with the booking
     *
     * @param limit the maximum number of cancellations to return (default 20)
     * @return a list of recent cancellation maps, or an empty list if the query fails
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
     * Returns upcoming flights whose departure date is today or later.
     *
     * Joins `flights`, `routes`, and `airports` to include route metadata.
     * Results are ordered by flight date ascending, then by planned departure time.
     *
     * Each result map contains:
     * - `"flightId"`: the flight ID as an `Int`
     * - `"flightDate"`: the flight date as a `String`
     * - `"status"`: the current flight status string
     * - `"flightNumber"`: the flight number string
     * - `"departureTime"`: the planned departure time string
     * - `"departureCity"`: the departure airport name
     * - `"arrivalCity"`: the arrival airport name
     * - `"price"`: the base flight price as a `Double`
     *
     * @param limit the maximum number of flights to return (default 20)
     * @return a list of upcoming flight maps, or an empty list if the query fails
     */
    fun getUpcomingFlights(limit: Int = 20): List<Map<String, Any>> {
        val sql = """
            SELECT f.flight_id, f.flight_date, f.status, f.price,
                   r.flight_number, r.planned_departure,
                   dep.name as departure_city, arr.name as arrival_city
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
     * Returns the routes with the highest number of bookings.
     *
     * Joins booking, flight, route, and airport tables and groups by route ID.
     * Optional filters narrow results by season and/or date range. Results are
     * ordered from most to least booked.
     *
     * Each result map contains:
     * - `"departureCity"`: the departure airport name
     * - `"arrivalCity"`: the arrival airport name
     * - `"flightNumber"`: the route's flight number
     * - `"bookingCount"`: total number of bookings for this route as an `Int`
     *
     * @param limit the maximum number of routes to return (default 10)
     * @param startDate optional lower bound for flight date (inclusive, `"yyyy-MM-dd"`)
     * @param endDate optional upper bound for flight date (inclusive, `"yyyy-MM-dd"`)
     * @param filterSeason optional season string to filter routes by
     * @return a list of busiest route maps, or an empty list if the query fails
     */
    fun getBusiestRoutes(
        limit: Int = 10,
        startDate: String? = null,
        endDate: String? = null,
        filterSeason: String? = null,
    ): List<Map<String, Any>> {
        var sql = """
            SELECT dep.name as departure_city, arr.name as arrival_city,
                   r.flight_number, COUNT(*) as booking_count
            FROM booking_flights bf
            JOIN flights f ON bf.flight_id = f.flight_id
            JOIN routes r ON f.route_id = r.route_id
            JOIN airports dep ON r.departure_airport = dep.airport_id
            JOIN airports arr ON r.arrival_airport = arr.airport_id
            WHERE 1=1
        """
        if (!filterSeason.isNullOrEmpty()) {
            sql += " AND r.season = ?"
        }
        if (!startDate.isNullOrEmpty()) {
            sql += " AND f.flight_date >= ?"
        }
        if (!endDate.isNullOrEmpty()) {
            sql += " AND f.flight_date <= ?"
        }
        sql += """
            GROUP BY r.route_id
            HAVING booking_count > 0
            ORDER BY booking_count DESC
            LIMIT ?
        """
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    var paramIndex = 1
                    if (!filterSeason.isNullOrEmpty()) {
                        stmt.setString(paramIndex++, filterSeason)
                    }
                    if (!startDate.isNullOrEmpty()) {
                        stmt.setString(paramIndex++, startDate)
                    }
                    if (!endDate.isNullOrEmpty()) {
                        stmt.setString(paramIndex++, endDate)
                    }
                    stmt.setInt(paramIndex, limit)
                    stmt.setInt(paramIndex, limit)
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

    /**
     * Returns all bookings with optional filtering by date, username, and status.
     *
     * Used on the admin reservations tracking page. When [filterUsername] is
     * provided, the query matches against the booking user's first name, last
     * name, and any passenger names on the booking. Results are ordered by
     * booking date descending.
     *
     * Each result map contains:
     * - `"bookingId"`: the booking ID as an `Int`
     * - `"bookingDate"`: the booking timestamp string
     * - `"totalPrice"`: the total fare as a `Double`
     * - `"status"`: the booking status string
     * - `"contactEmail"`: the contact email on the booking
     * - `"bookedBy"`: the full name of the user who made the booking
     * - `"passengers"`: comma-separated passenger names, or `"N/A"` if none
     *
     * @param filterDate optional date string (`"yyyy-MM-dd"`) to restrict results to
     * @param filterUsername optional name substring to search across user and passenger names
     * @param filterStatus optional exact booking status to filter by
     * @return a list of reservation maps, or an empty list if the query fails
     */
    fun trackReservations(
        filterDate: String? = null,
        filterUsername: String? = null,
        filterStatus: String? = null,
    ): List<Map<String, Any>> {
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

    /**
     * Returns flights for the admin flight-tracking page, defaulting to today's date.
     *
     * Defaults to the current date when [filterDate] is omitted, to avoid loading
     * all flights at once. An optional [filterNumber] performs a partial match
     * against the flight number. Results are ordered by local departure time ascending.
     *
     * Each result map contains:
     * - `"flightId"`: the flight ID as an `Int`
     * - `"flightDate"`: the flight date string
     * - `"status"`: the current flight status string
     * - `"flightNumber"`: the flight number string
     * - `"originCity"`: the departure airport name
     * - `"destCity"`: the arrival airport name
     * - `"depTime"`: the local departure time string
     * - `"arrTime"`: the local arrival time string
     * - `"overnight"`: true if the flight arrives on a different calendar day
     *
     * @param filterDate optional date string (`"yyyy-MM-dd"`); defaults to today if null or empty
     * @param filterNumber optional flight number substring for partial matching
     * @return a list of flight tracking maps, or an empty list if the query fails
     */
    fun trackFlights(
        filterDate: String? = null,
        filterNumber: String? = null,
    ): List<Map<String, Any>> {
        // Default to today if no date is provided — avoids loading all 10000+ flights at once
        val effectiveDate =
            if (filterDate.isNullOrEmpty()) {
                java.time.LocalDate
                    .now()
                    .toString()
            } else {
                filterDate
            }

        var sql = """
            SELECT f.flight_id, 
                f.flight_date, 
                f.status, 
                r.flight_number, 
                f.departure_localtime,
                f.arrival_localtime,
                f.overnight,
                dep.name AS dep_name,
                arr.name AS arr_name
            FROM flights f
            JOIN routes r ON f.route_id = r.route_id
            JOIN airports dep ON r.departure_airport = dep.airport_id
            JOIN airports arr ON r.arrival_airport = arr.airport_id
            WHERE f.flight_date = ?
        """

        if (!filterNumber.isNullOrEmpty()) {
            sql += " AND r.flight_number LIKE ?"
        }

        sql += " ORDER BY f.departure_localtime ASC"

        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    var paramIndex = 1
                    stmt.setString(paramIndex++, effectiveDate)
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
                            row["originCity"] = rs.getString("dep_name") ?: ""
                            row["destCity"] = rs.getString("arr_name") ?: ""
                            row["depTime"] = rs.getString("departure_localtime") ?: ""
                            row["arrTime"] = rs.getString("arrival_localtime") ?: ""
                            row["overnight"] = rs.getInt("overnight") == 1
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

    /**
     * Returns the previous and next calendar dates relative to [currentDate].
     *
     * Used for day-by-day navigation on the admin flight tracking page.
     * If [currentDate] cannot be parsed as a `yyyy-MM-dd` date, the current
     * date is used as the reference point.
     *
     * @param currentDate the reference date string in `"yyyy-MM-dd"` format
     * @return a map with keys `"prevDate"` and `"nextDate"`, each as a `"yyyy-MM-dd"` string
     */
    fun getAdjacentFlightDates(currentDate: String): Map<String, String> {
        val date =
            try {
                java.time.LocalDate.parse(currentDate)
            } catch (e: Exception) {
                java.time.LocalDate.now()
            }
        return mapOf(
            "prevDate" to date.minusDays(1).toString(),
            "nextDate" to date.plusDays(1).toString(),
        )
    }

    /**
     * Updates the status of a flight after validating the new value.
     *
     * Only the following status values are accepted:
     * `"Scheduled"`, `"Delayed"`, `"Cancelled"`, `"Departed"`, `"Arrived"`.
     * Any other value causes an immediate `false` return without touching the database.
     *
     * @param flightId the ID of the flight to update
     * @param newStatus the new status string to set
     * @return true if the update affected at least one row, otherwise false
     */
    fun updateFlightStatus(
        flightId: Int,
        newStatus: String,
    ): Boolean {
        val allowedStatuses = setOf("Scheduled", "Delayed", "Cancelled", "Departed", "Arrived")
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

    /**
     * Builds a [FlightStatusNotification] for a flight status change, if notification is required.
     *
     * Only `"Delayed"` and `"Cancelled"` statuses trigger a notification. The function
     * also returns null if the flight's current status is not `"Scheduled"` (to avoid
     * duplicate notifications for already-changed flights).
     *
     * The notification includes the contact email from each active booking as well as
     * the owning user's email as recipient addresses.
     *
     * @param flightId the ID of the flight whose status is changing
     * @param newStatus the new status being applied
     * @return a [FlightStatusNotification] with recipient emails and flight details,
     *         or null if notification is not required or the query fails
     */
    fun getFlightStatusNotification(
        flightId: Int,
        newStatus: String,
    ): FlightStatusNotification? {
        val notifiableStatuses: Set<String> = setOf("Delayed", "Cancelled")
        if (newStatus !in notifiableStatuses) return null
        val sql = """
            SELECT f.flight_id,
                f.status,
                f.flight_date,
                r.flight_number,
                dep.city AS departure_city,
                arr.city AS arrival_city,
                b.contact_email,
                u.email AS user_email
            FROM flights f
            JOIN routes r ON f.route_id = r.route_id
            JOIN airports dep ON r.departure_airport = dep.airport_id
            JOIN airports arr ON r.arrival_airport = arr.airport_id
            LEFT JOIN booking_flights bf ON f.flight_id = bf.flight_id
            LEFT JOIN bookings b ON bf.booking_id = b.booking_id AND b.status != 'cancelled'
            LEFT JOIN users u ON b.user_id = u.user_id
            WHERE f.flight_id = ?
        """
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, flightId)
                    stmt.executeQuery().use { result ->
                        val recipientEmails: MutableList<String> = mutableListOf()
                        var flightNumber: String = ""
                        var flightDate: String = ""
                        var originCity: String = ""
                        var destinationCity: String = ""
                        var oldStatus: String = ""
                        var hasFlight: Boolean = false
                        while (result.next()) {
                            hasFlight = true
                            flightNumber = result.getString("flight_number") ?: flightNumber
                            flightDate = result.getString("flight_date") ?: flightDate
                            originCity = result.getString("departure_city") ?: originCity
                            destinationCity = result.getString("arrival_city") ?: destinationCity
                            oldStatus = result.getString("status") ?: oldStatus
                            recipientEmails.add(result.getString("contact_email") ?: "")
                            recipientEmails.add(result.getString("user_email") ?: "")
                        }
                        if (!hasFlight || oldStatus != "Scheduled") return@use null
                        FlightStatusNotification(
                            flightId = flightId,
                            flightNumber = flightNumber,
                            flightDate = flightDate,
                            originCity = originCity,
                            destinationCity = destinationCity,
                            oldStatus = oldStatus,
                            newStatus = newStatus,
                            recipientEmails = recipientEmails,
                        )
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Returns the number of bookings per flight, with optional date and season filters.
     *
     * Joins flights with routes, airports, and booking_flights to count how many
     * bookings reference each flight. Results are ordered by flight date descending,
     * then by booking count descending.
     *
     * Each result map contains:
     * - `"flightNumber"`: the route's flight number string
     * - `"flightDate"`: the flight date string
     * - `"origin"`: the departure airport name
     * - `"dest"`: the arrival airport name
     * - `"bookingCount"`: the number of bookings for this flight as an `Int`
     *
     * @param limit the maximum number of rows to return (default 20)
     * @param filterDate optional exact flight date (`"yyyy-MM-dd"`) to filter by
     * @param filterSeason optional season string to filter routes by
     * @return a list of per-flight booking count maps, or an empty list if the query fails
     */
    fun getBookingsPerFlight(
        limit: Int = 20,
        filterDate: String? = null,
        filterSeason: String? = null,
    ): List<Map<String, Any>> {
        var sql = """
            SELECT r.flight_number,
                f.flight_date,
                dep.name AS origin,
                arr.name AS dest,
                COUNT(bf.booking_id) AS booking_count
            FROM flights f
            JOIN routes r ON f.route_id = r.route_id
            JOIN airports dep ON r.departure_airport = dep.airport_id
            JOIN airports arr ON r.arrival_airport = arr.airport_id
            LEFT JOIN booking_flights bf ON f.flight_id = bf.flight_id
            WHERE 1=1
        """
        if (!filterDate.isNullOrEmpty()) {
            sql += " AND f.flight_date = ?"
        }
        if (!filterSeason.isNullOrEmpty()) {
            sql += " AND r.season = ?"
        }
        sql += """
            GROUP BY f.flight_id, r.flight_number, f.flight_date, dep.name, arr.name
            ORDER BY f.flight_date DESC, booking_count DESC
            LIMIT ?
        """
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    var paramIndex = 1
                    if (!filterDate.isNullOrEmpty()) {
                        stmt.setString(paramIndex++, filterDate)
                    }
                    if (!filterSeason.isNullOrEmpty()) {
                        stmt.setString(paramIndex++, filterSeason)
                    }
                    stmt.setInt(paramIndex, limit)
                    stmt.executeQuery().use { rs ->
                        val results = mutableListOf<Map<String, Any>>()
                        while (rs.next()) {
                            results.add(
                                mapOf(
                                    "flightNumber" to (rs.getString("flight_number") ?: ""),
                                    "flightDate" to (rs.getString("flight_date") ?: ""),
                                    "origin" to (rs.getString("origin") ?: ""),
                                    "dest" to (rs.getString("dest") ?: ""),
                                    "bookingCount" to rs.getInt("booking_count"),
                                ),
                            )
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

    /**
     * Returns all pending change requests, joined with the submitting user's email.
     *
     * Each row that fails to parse is skipped with a console warning rather than
     * aborting the entire result set.
     *
     * Each result map contains:
     * - `"requestId"`: the row ID of the change request as a `Long`
     * - `"userId"`: the ID of the user who submitted the request as an `Int`
     * - `"changeTo"`: the requested new value string
     * - `"type"`: the change type string (e.g. `"name"`)
     * - `"username"`: the user's email address, or `"Unknown Email"` if not found
     *
     * @return a list of pending change request maps, or an empty list if the query fails
     */
    fun getPendingChangeRequests(): List<Map<String, Any>> {
        val sql = """
            SELECT 
                change_requests.rowid AS request_id,
                change_requests.user_id, 
                change_requests.change_to, 
                change_requests.change_type, 
                users.email AS username 
            FROM change_requests 
            LEFT JOIN users ON change_requests.user_id = users.user_id 
            WHERE change_requests.status = 'pending'
        """

        val requests = mutableListOf<Map<String, Any>>()

        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    val rs = stmt.executeQuery()
                    while (rs.next()) {
                        try {
                            val row = mutableMapOf<String, Any>()
                            row["requestId"] = rs.getLong("request_id")
                            row["userId"] = rs.getInt("user_id")
                            row["changeTo"] = rs.getString("change_to") ?: ""
                            row["type"] = rs.getString("change_type") ?: ""
                            row["username"] = rs.getString("username") ?: "Unknown Email"

                            requests.add(row)
                        } catch (rowError: Exception) {
                            println("Row parsing error: ${rowError.message}")
                        }
                    }
                }
            }
            requests
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Updates the status of all pending change requests belonging to a user.
     *
     * Only rows with status `'pending'` are affected. Intended as a bulk
     * approve/reject fallback; prefer the overloads that accept a specific
     * [requestId] for targeted updates.
     *
     * @param userId the ID of the user whose pending requests to update
     * @param newStatus the new status string to apply (e.g. `"accepted"` or `"rejected"`)
     * @return true if at least one row was updated, otherwise false
     */
    fun updateRequestStatus(
        userId: Int,
        newStatus: String,
    ): Boolean {
        val sql = "UPDATE change_requests SET status = ? WHERE user_id = ? AND status = 'pending'"
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, newStatus)
                    stmt.setInt(2, userId)
                    stmt.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Approves the oldest pending change request for a user.
     *
     * Looks up the first pending request via [getFirstPendingRequestId], then
     * delegates to [approveChangeRequest] with the resolved request ID.
     *
     * @param userId the ID of the user whose oldest pending request to approve
     * @return true if the approval was committed successfully, otherwise false
     */
    fun approveChangeRequest(userId: Int): Boolean {
        return try {
            Database.getConnection().use { conn ->
                val requestId: Long = getFirstPendingRequestId(conn, userId) ?: return false
                approveChangeRequest(conn, requestId, userId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Approves a specific pending change request identified by [requestId].
     *
     * Opens a new database connection and delegates to the internal
     * [approveChangeRequest] overload that accepts an existing [Connection].
     *
     * @param requestId the row ID of the change request to approve
     * @param userId the ID of the user who owns the request (used as a safety check)
     * @return true if the approval was committed successfully, otherwise false
     */
    fun approveChangeRequest(
        requestId: Long,
        userId: Int,
    ): Boolean =
        try {
            Database.getConnection().use { conn ->
                approveChangeRequest(conn, requestId, userId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }

    /**
     * Core implementation: applies a pending change request within an existing transaction.
     *
     * Reads the `change_to` value for the request, parses it as
     * `"<fullName> <idNumber>"` (whitespace-separated), and applies the update
     * to all passengers on the user's bookings. The request status is then set
     * to `'accepted'`. Both updates are wrapped in a single transaction; any
     * failure triggers a rollback and re-throws the exception.
     *
     * @param conn an open [Connection] to use (auto-commit is managed internally)
     * @param requestId the row ID of the change request to approve
     * @param userId the ID of the user who owns the request
     * @return true if the transaction was committed and the request row was updated,
     *         otherwise false
     */
    internal fun approveChangeRequest(
        conn: Connection,
        requestId: Long,
        userId: Int,
    ): Boolean {
        val pendingData: String = getPendingDataByRequestId(conn, requestId, userId) ?: return false
        val parts = pendingData.trim().split(Regex("\\s+"))
        if (parts.size < 2) return false
        val newFullName: String = parts[0]
        val newIdNumber: String = parts[1]
        val sqlUpdatePassenger = """
            UPDATE booking_passengers 
            SET full_name = ?, id_number = ? 
            WHERE booking_id IN (SELECT booking_id FROM bookings WHERE user_id = ?)
        """
        val sqlUpdateStatus = "UPDATE change_requests SET status = 'accepted' WHERE rowid = ? AND user_id = ? AND status = 'pending'"
        return try {
            val originalAutoCommit: Boolean = conn.autoCommit
            conn.autoCommit = false
            try {
                conn.prepareStatement(sqlUpdatePassenger).use { stmt ->
                    stmt.setString(1, newFullName)
                    stmt.setString(2, newIdNumber)
                    stmt.setInt(3, userId)
                    stmt.executeUpdate()
                }
                val updatedRequestCount: Int =
                    conn.prepareStatement(sqlUpdateStatus).use { stmt ->
                        stmt.setLong(1, requestId)
                        stmt.setInt(2, userId)
                        stmt.executeUpdate()
                    }
                conn.commit()
                updatedRequestCount > 0
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = originalAutoCommit
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Returns the row ID of the earliest pending change request for a user.
     *
     * Used internally to resolve a user-scoped approval/rejection to a specific
     * request row before delegating to the ID-based overloads.
     *
     * @param conn an open [Connection] to reuse
     * @param userId the ID of the user to query
     * @return the `rowid` of the oldest pending request, or null if none exist
     */
    private fun getFirstPendingRequestId(
        conn: Connection,
        userId: Int,
    ): Long? {
        val sql = "SELECT rowid FROM change_requests WHERE user_id = ? AND status = 'pending' ORDER BY rowid LIMIT 1"
        return try {
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, userId)
                val rs = stmt.executeQuery()
                if (rs.next()) rs.getLong("rowid") else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Returns the `change_to` value for a specific pending change request.
     *
     * Used internally by [approveChangeRequest] to retrieve the data to apply
     * before committing the approval.
     *
     * @param conn an open [Connection] to reuse
     * @param requestId the row ID of the change request
     * @param userId the ID of the user who owns the request (safety check)
     * @return the `change_to` string if the request exists and is pending, otherwise null
     */
    private fun getPendingDataByRequestId(
        conn: Connection,
        requestId: Long,
        userId: Int,
    ): String? {
        val sql = "SELECT change_to FROM change_requests WHERE rowid = ? AND user_id = ? AND status = 'pending'"
        return try {
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, requestId)
                stmt.setInt(2, userId)
                val rs = stmt.executeQuery()
                if (rs.next()) rs.getString("change_to") else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Rejects the oldest pending change request for a user.
     *
     * Looks up the first pending request via [getFirstPendingRequestId], then
     * delegates to [rejectChangeRequest] with the resolved request ID.
     *
     * @param userId the ID of the user whose oldest pending request to reject
     * @return true if the rejection was applied successfully, otherwise false
     */
    fun rejectChangeRequest(userId: Int): Boolean {
        return try {
            Database.getConnection().use { conn ->
                val requestId: Long = getFirstPendingRequestId(conn, userId) ?: return false
                rejectChangeRequest(conn, requestId, userId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Rejects a specific pending change request identified by [requestId].
     *
     * Opens a new database connection and delegates to the internal
     * [rejectChangeRequest] overload that accepts an existing [Connection].
     *
     * @param requestId the row ID of the change request to reject
     * @param userId the ID of the user who owns the request (used as a safety check)
     * @return true if the rejection was applied successfully, otherwise false
     */
    fun rejectChangeRequest(
        requestId: Long,
        userId: Int,
    ): Boolean =
        try {
            Database.getConnection().use { conn ->
                rejectChangeRequest(conn, requestId, userId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }

    /**
     * Core implementation: sets a pending change request status to `'rejected'`.
     *
     * The update is scoped to the given [requestId] and [userId] and only affects
     * rows that are still `'pending'`.
     *
     * @param conn an open [Connection] to reuse
     * @param requestId the row ID of the change request to reject
     * @param userId the ID of the user who owns the request
     * @return true if the row was updated, otherwise false
     */
    internal fun rejectChangeRequest(
        conn: Connection,
        requestId: Long,
        userId: Int,
    ): Boolean {
        val sql = "UPDATE change_requests SET status = 'rejected' WHERE rowid = ? AND user_id = ? AND status = 'pending'"
        return try {
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, requestId)
                stmt.setInt(2, userId)
                stmt.executeUpdate() > 0
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
