package com.flightbooking
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Data Access Object for all user-related database operations.
 *
 * Handles user authentication, registration, booking management,
 * password reset, and profile updates.
 */
object UserDAO{

    /**
     * Retrieves basic profile details for a user by their ID.
     *
     * @param userId the ID of the user to look up
     * @return a [User] object with name and email fields, or null if not found
     */
    fun getUserDetails(userId: Int): User? {
        val sql = "SELECT first_name, middle_name, last_name, email FROM users WHERE user_id = ?"
        return Database.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, userId)
                val rs = stmt.executeQuery()
                if (rs.next()){
                    User(
                        firstName = rs.getString("first_name"),
                        middleName = rs.getString("middle_name"),
                        lastName = rs.getString("last_name"),
                        email = rs.getString("email"),
                        passwordHash = ""
                    )
                }else null
            }
        }
    }

    /**
     * Checks if an email is already in the users table.
     *
     * @param email the email to check
     * @return true if the email exists, otherwise false
     */
    fun emailExists(email: String): Boolean {
        val sql = "SELECT count(*) FROM users WHERE email = ?"
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, email)
                    stmt.executeQuery().use { result ->
                        if (result.next()) {
                            result.getInt(1) > 0
                        } else {
                            false
                        }
                    }
                }
            }
        } catch (e: Exception) {
            false
        }
    }


    /**
     * Registers a new user in the database.
     *
     * Validates the password and checks that the email is not already in use
     * before hashing the password and inserting the new user record.
     *
     * @param user the user object (currently unused; kept for API compatibility)
     * @param inputFirstName the user's first name
     * @param inputMiddleName the user's middle name
     * @param inputLastName the user's last name
     * @param inputEmail the user's email
     * @param inputPassword the user's plain-text password to be hashed before storage
     * @return true if the user was successfully registered, otherwise false
     */
    fun register(user: User, inputFirstName: String, inputMiddleName: String, inputLastName: String, inputEmail: String, inputPassword: String): Boolean {
        if (!Security.isPasswordValid(inputPassword)) return false
        if (emailExists(inputEmail)) return false

        val hashedPassword = Security.hashPassword(inputPassword)
        val sql = "INSERT INTO users(first_name, middle_name, last_name, email, password_hash) VALUES(?,?,?,?,?)"

        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, inputFirstName)
                    stmt.setString(2, inputMiddleName)
                    stmt.setString(3, inputLastName)
                    stmt.setString(4, inputEmail)
                    stmt.setString(5, hashedPassword)
                    stmt.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }




    /**
     * Holds the result of a login attempt.
     *
     * @property success whether the login credentials were valid
     * @property isAdmin whether the authenticated user has admin privileges
     */
    data class LoginResult(val success: Boolean, val isAdmin: Boolean = false)


    /**
     * Attempts to authenticate a user with the given credentials.
     *
     * Checks the admins table first (matching by email or username), then falls
     * back to the regular users table. The supplied password is verified against
     * the stored bcrypt hash.
     *
     * @param inputEmail the email or username entered by the user
     * @param inputPassword the plain-text password entered by the user
     * @return a [LoginResult] indicating whether authentication succeeded and whether the user is an admin
     */
    fun loginUser(inputEmail: String, inputPassword: String): LoginResult {
        // Check admins table first — match by email or username
        val adminSql = "SELECT password_hash FROM admins WHERE email = ? OR username = ?"
        try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(adminSql).use { stmt ->
                    stmt.setString(1, inputEmail)
                    stmt.setString(2, inputEmail)
                    stmt.executeQuery().use { result ->
                        if (result.next()) {
                            val hash = result.getString("password_hash")
                            return if (Security.verifyPassword(inputPassword, hash)) {
                                LoginResult(success = true, isAdmin = true)
                            } else {
                                LoginResult(success = false)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return LoginResult(success = false)
        }

        // Fall back to regular users table
        val userSql = "SELECT password_hash FROM users WHERE email = ?"
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(userSql).use { stmt ->
                    stmt.setString(1, inputEmail)
                    stmt.executeQuery().use { result ->
                        if (result.next()) {
                            val hash = result.getString("password_hash")
                            if (Security.verifyPassword(inputPassword, hash)) {
                                LoginResult(success = true)
                            } else {
                                LoginResult(success = false)
                            }
                        } else {
                            LoginResult(success = false)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LoginResult(success = false)
        }
    }


    /**
     * Initiates the password reset flow for a user.
     *
     * Verifies that the email exists, generates a one-time code (OTC), and
     * sends it to the user's email address. The code expires after 5 minutes.
     *
     * @param inputEmail the email address requesting the password reset
     * @return true if the reset email was sent successfully, otherwise false
     */
    fun resetPassword(inputEmail: String):Boolean{
        if(!emailExists(inputEmail)){
            return false
        }
        return try {
            // Email found — generate OTC and send reset email
            val generatedCode = OTC.generateAndSave(inputEmail)

            EmailService.sendEmail(
                to = inputEmail,
                subject = "Your One-Time Code for Password Reset",
                body = "Your one-time code is: $generatedCode. It will expire in 5 minutes.",
            )
            true
        } catch (e: Exception) {
            false
        }
    }


    /**
     * Completes the password reset process after OTC verification.
     *
     * Validates the one-time code, checks that the new password meets the
     * security requirements, hashes it, and updates the record in the database.
     *
     * @param inputEmail the user's email address
     * @param inputOTC the one-time code supplied by the user
     * @param newPassword the new plain-text password to set
     * @return true if the password was updated successfully, otherwise false
     */
    fun confirmResetPassword(inputEmail: String, inputOTC: String, newPassword: String):Boolean{
        // Verify that the OTC is valid and has not expired
        if (!OTC.verify(inputEmail, inputOTC)) {
            return false
        }

        // If OTC is valid, validate the new password before hashing
        if (!Security.isPasswordValid(newPassword)) {
            return false
        }

        // Hash the new password and persist it
        val hashedNewPassword = Security.hashPassword(newPassword)
        val sql = "UPDATE users SET password_hash = ? WHERE email = ?"
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, hashedNewPassword)
                    stmt.setString(2, inputEmail)

                    val rowsAffected = stmt.executeUpdate()

                    rowsAffected > 0
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Looks up the numeric user ID associated with a given email address.
     *
     * @param username the user's email address
     * @return the user's integer ID, or -1 if no matching record is found
     */
    fun getUserID(username: String): Int {
        val sql = "SELECT user_id FROM users WHERE email = ?"
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, username)
                    stmt.executeQuery().use { result ->
                        if (result.next()) result.getInt("user_id") else -1
                    }
                }
            }
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Maps a single result-set row to a flight information map.
     *
     * Reads flight and route columns from the current row, computes the arrival
     * time by adding the route duration to the departure time (accounting for
     * timezone differences), and returns all values as a keyed map.
     *
     * @param result the active [java.sql.ResultSet] positioned on the row to read
     * @return a map containing flight details: times, cities, airport codes,
     *         terminals, duration, and day offset between departure and arrival
     */
    private fun getFlightInfoFromRow(result: java.sql.ResultSet): Map<String, Any> {
        val durationMinutes = result.getInt("base_duration_minutes")
    
        val departureDate = LocalDate.parse(result.getString("flight_date"))
        val departureTime = LocalTime.parse(result.getString("planned_departure"))
    
        val departureTimezone = ZoneId.of(result.getString("departure_timezone") ?: "UTC")
        val arrivalTimezone = ZoneId.of(result.getString("arrival_timezone") ?: "UTC")
    
        val departureDateTime = ZonedDateTime.of(departureDate, departureTime, departureTimezone)
        val arrivalDateTime = departureDateTime.plusMinutes(durationMinutes.toLong())
        val arrivalTime = arrivalDateTime.withZoneSameInstant(arrivalTimezone).toLocalTime()
    
        val flightInfo = mutableMapOf<String, Any>()
        flightInfo["flightNumber"]      = result.getString("flight_number")
        flightInfo["aircraftType"]      = result.getString("aircraft_type") ?: ""
        flightInfo["departureCity"]     = result.getString("departure_city")
        flightInfo["arrivalCity"]       = result.getString("arrival_city")
        flightInfo["departureAirport"]  = result.getString("departure_airport_name")
        flightInfo["arrivalAirport"]    = result.getString("arrival_airport_name")

        flightInfo["departureCode"]     = result.getString("departure_code")
        flightInfo["arrivalCode"]       = result.getString("arrival_code")
        flightInfo["departureLat"]      = result.getDouble("departure_lat")
        flightInfo["departureLng"]      = result.getDouble("departure_lng")
        flightInfo["arrivalLat"]        = result.getDouble("arrival_lat")
        flightInfo["arrivalLng"]        = result.getDouble("arrival_lng")

        flightInfo["departureTerminal"] = result.getString("departure_terminal") ?: ""
        flightInfo["arrivalTerminal"]   = result.getString("arrival_terminal") ?: ""
        flightInfo["departureDate"]     = departureDate.toString()
        flightInfo["departureTime"]     = departureTime.toString()
        flightInfo["arrivalTime"]       = arrivalTime.toString()
        flightInfo["arrivalDate"]       = arrivalDateTime.withZoneSameInstant(arrivalTimezone).toLocalDate().toString()
        flightInfo["dayOffset"]         = java.time.temporal.ChronoUnit.DAYS.between(departureDate, arrivalDateTime.withZoneSameInstant(arrivalTimezone).toLocalDate()).toInt()
        flightInfo["duration"]          = Utils.formatDuration(durationMinutes)
        return flightInfo
    }


    /**
     * Returns all non-cancelled bookings for a user, sorted by departure date.
     *
     * Each booking entry includes top-level metadata (ID, date, price, status,
     * trip type) and a nested list of flight detail maps produced by
     * [getFlightInfoFromRow]. Multi-leg bookings are grouped under a single
     * booking entry.
     *
     * @param userID the ID of the user whose bookings should be retrieved
     * @return a list of booking maps ordered by first-flight departure; empty if none found
     */
    fun getBookings(userID: Int): List<Map<String, Any>> {
        // Fetch all bookings for the user, joining flight and route info.
        // A booking can have multiple flights, so results are grouped by booking_id.
       val sql = """
            SELECT
                b.booking_id, b.booking_date, b.total_price, b.status, b.trip_type,
                f.flight_date,
                r.flight_number, r.aircraft_type, r.departure_terminal, r.arrival_terminal,
                r.planned_departure, r.base_duration_minutes,
                dep.city            as departure_city,
                arr.city            as arrival_city,
                dep.name            as departure_airport_name,
                arr.name            as arrival_airport_name,
                dep.timezone        as departure_timezone,
                arr.timezone        as arrival_timezone,
                dep.airport_id      as departure_code,
                arr.airport_id      as arrival_code,
                dep.latitude        as departure_lat,
                dep.longitude       as departure_lng,
                arr.latitude        as arrival_lat,
                arr.longitude       as arrival_lng,
                bf.flight_sequence
            FROM bookings b
            JOIN booking_flights bf ON b.booking_id = bf.booking_id
            JOIN flights f ON bf.flight_id = f.flight_id
            JOIN routes r ON f.route_id = r.route_id
            JOIN airports dep ON r.departure_airport = dep.airport_id
            JOIN airports arr ON r.arrival_airport = arr.airport_id
            WHERE b.user_id = ? AND b.status != 'cancelled'
            ORDER BY b.booking_id, bf.flight_sequence
        """
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, userID)
                    stmt.executeQuery().use { result ->
                        // Use a map keyed by booking_id to group multiple flights under one booking
                        val bookings = mutableListOf<MutableMap<String, Any>>()
                        val seenBookingIds = mutableMapOf<Int, MutableMap<String, Any>>()

                        while (result.next()) {
                            val bookingId = result.getInt("booking_id")

                            // If this is the first row for this booking, create a new entry
                            if (!seenBookingIds.containsKey(bookingId)) {
                                val newBooking = mutableMapOf<String, Any>()
                                newBooking["bookingId"] = bookingId
                                newBooking["bookingDate"] = result.getString("booking_date") ?: ""
                                newBooking["totalPrice"] = result.getDouble("total_price")
                                newBooking["status"] = result.getString("status")
                                newBooking["tripType"] = result.getString("trip_type") ?: ""
                                newBooking["flights"] = mutableListOf<Map<String, Any>>()
                                bookings.add(newBooking)
                                seenBookingIds[bookingId] = newBooking
                            }

                            // Append this flight to the booking's flight list
                            val booking = seenBookingIds[bookingId]!!

                            @Suppress("UNCHECKED_CAST")
                            val flightList = booking["flights"] as MutableList<Map<String, Any>>
                            flightList.add(getFlightInfoFromRow(result))
                        }
                        bookings.sortedBy { booking ->
                            val flights = booking["flights"] as List<Map<String, Any>>
                            if (flights.isNotEmpty()) {
                                val firstFlight = flights.first()
                                "${firstFlight["departureDate"]} ${firstFlight["departureTime"]}"
                            } else {
                                ""
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }


    /**
     * Retrieves a single booking with its flights and passengers.
     *
     * Each flight is annotated with its cabin class (queried via a nested helper)
     * and its direction (`"outbound"` or `"return"`). The booking must belong to
     * the specified user; returns null if no matching record is found.
     *
     * @param bookingId the ID of the booking to retrieve
     * @param userID the ID of the user who owns the booking
     * @return a map containing booking metadata, a list of annotated flight maps,
     *         and a list of passenger maps; or null if not found
     */
    fun getBookingById(bookingId: Int, userID: Int): Map<String, Any>? {
        // Fetch booking with all associated flights and route details
        val bookingSql = """
            SELECT
                b.booking_id, b.booking_date, b.total_price, b.status,
                b.contact_email, b.contact_phone, b.trip_type,
                f.flight_date,
                r.flight_number, r.aircraft_type, r.departure_terminal, r.arrival_terminal,
                r.planned_departure, r.base_duration_minutes,
                dep.city            as departure_city,
                arr.city            as arrival_city,
                dep.name            as departure_airport_name,
                arr.name            as arrival_airport_name,
                dep.timezone        as departure_timezone,
                arr.timezone        as arrival_timezone,
                dep.airport_id      as departure_code,
                arr.airport_id      as arrival_code,
                dep.latitude        as departure_lat,
                dep.longitude       as departure_lng,
                arr.latitude        as arrival_lat,
                arr.longitude       as arrival_lng,
                bf.flight_sequence
            FROM bookings b
            JOIN booking_flights bf ON b.booking_id = bf.booking_id
            JOIN flights f ON bf.flight_id = f.flight_id
            JOIN routes r ON f.route_id = r.route_id
            JOIN airports dep ON r.departure_airport = dep.airport_id
            JOIN airports arr ON r.arrival_airport = arr.airport_id
            WHERE b.booking_id = ? AND b.user_id = ?
            ORDER BY bf.flight_sequence
        """

        val passengerSql = "SELECT full_name, id_number, type, seat_id FROM booking_passengers WHERE booking_id = ?"

        /**
         * Determines the cabin class for a specific flight leg within a booking.
         *
         * Looks up the first passenger's seat number for the given sequence, then
         * resolves that seat to a class (e.g. `"economy"`, `"business"`).
         *
         * @param conn an open database connection to reuse
         * @param bookingId the booking to inspect
         * @param sequence the 1-based flight leg sequence number
         * @return the lowercase cabin class string, defaulting to `"economy"` if not found
         */
        fun queryCabin(conn: java.sql.Connection, bookingId: Int, sequence: Int): String {
            val seatSql = """
                SELECT bp.seat_id, bf.flight_id
                FROM booking_passengers bp
                JOIN booking_flights bf ON bf.booking_id = bp.booking_id AND bf.flight_sequence = ?
                WHERE bp.booking_id = ?
                ORDER BY bp.passenger_id ASC
                LIMIT 1
            """
            val seatNumber = conn.prepareStatement(seatSql).use { stmt ->
                stmt.setInt(1, sequence)
                stmt.setInt(2, bookingId)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) return "economy"
                    val seatId = rs.getString("seat_id") ?: return "economy"
                    val seats = seatId.split(",").map { it.trim() }
                    seats.getOrNull(sequence - 1) ?: seats.firstOrNull() ?: return "economy"
                }
            }
            val classSql = """
                SELECT s.class as seat_class
                FROM booking_flights bf
                JOIN seats s ON s.flight_id = bf.flight_id AND s.seat_number = ?
                WHERE bf.booking_id = ? AND bf.flight_sequence = ?
                LIMIT 1
            """
            return conn.prepareStatement(classSql).use { stmt ->
                stmt.setString(1, seatNumber)
                stmt.setInt(2, bookingId)
                stmt.setInt(3, sequence)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getString("seat_class")?.lowercase() ?: "economy"
                    else "economy"
                }
            }
        }

        return try {
            Database.getConnection().use { conn ->
                // Fetch booking metadata and flights
                val booking = mutableMapOf<String, Any>()
                conn.prepareStatement(bookingSql).use { stmt ->
                    stmt.setInt(1, bookingId)
                    stmt.setInt(2, userID)
                    stmt.executeQuery().use { result ->
                        val flights = mutableListOf<Map<String, Any>>()
                        var found = false

                        while (result.next()) {
                            if (!found) {
                                booking["bookingId"] = result.getInt("booking_id")
                                booking["bookingDate"] = result.getString("booking_date") ?: ""
                                booking["totalPrice"] = result.getDouble("total_price")
                                booking["status"] = result.getString("status")
                                booking["contactEmail"] = result.getString("contact_email") ?: ""
                                booking["contactPhone"] = result.getString("contact_phone") ?: ""
                                booking["tripType"] = result.getString("trip_type") ?: "oneway"
                                found = true
                            }
                            flights.add(getFlightInfoFromRow(result))
                        }

                        if (!found) return@use null

                        // Annotate each flight with its cabin class and direction
                        val tripType = booking["tripType"] as String
                        val totalFlights = flights.size
                        // Outbound legs: first half for return trips, all legs for one-way
                        val outboundCount = when {
                            tripType == "return" && totalFlights >= 2 -> totalFlights / 2
                            else -> totalFlights
                        }
                        val annotatedFlights = flights.mapIndexed { i, flight ->
                            val seq = i + 1
                            val cabin = queryCabin(conn, bookingId, seq)
                            val direction = if (i < outboundCount) "outbound" else "return"
                            (flight as MutableMap).also {
                                it["cabin"] = cabin
                                it["direction"] = direction
                            }
                        }
                        booking["flights"] = annotatedFlights
                    }
                }

                // Fetch passengers for the booking
                conn.prepareStatement(passengerSql).use { stmt ->
                    stmt.setInt(1, bookingId)
                    stmt.executeQuery().use { result ->
                        val passengers = mutableListOf<Map<String, Any>>()
                        while (result.next()) {
                            passengers.add(
                                mapOf(
                                    "fullName" to result.getString("full_name"),
                                    "idNumber" to result.getString("id_number"),
                                    "type" to result.getString("type"),
                                    "seat" to result.getString("seat_id"),
                                ),
                            )
                        }
                        booking["passengers"] = passengers
                    }
                }

                booking
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Cancels a booking by setting its status to `'cancelled'`.
     *
     * The booking must belong to the specified user; the update will affect no
     * rows (and return false) if the IDs do not match.
     *
     * @param bookingId the ID of the booking to cancel
     * @param userID the ID of the user who owns the booking
     * @return true if the booking was successfully cancelled, otherwise false
     */
    fun cancelBooking(bookingId: Int, userID: Int): Boolean {
        val sql = "UPDATE bookings SET status = 'cancelled' WHERE booking_id = ? AND user_id = ?"
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, bookingId)
                    stmt.setInt(2, userID)
                    stmt.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
    * Retrieves booking information required for sending a cancellation notification email.
    *
    * This function queries the database for all flights associated with the specified booking,
    * including flight number, departure city, arrival city, and flight date. The booking must
    * belong to the specified user and must not already be cancelled.
    *
    * @param bookingId the ID of the booking to retrieve notification details for
    * @param userID the ID of the user who owns the booking
    * @return a [BookingCancellationNotification] containing the recipient email and formatted
    * flight summary if the booking exists and is valid; otherwise `null`
    */
    fun getBookingCancellationNotification(bookingId: Int, userID: Int): BookingCancellationNotification? {
        val sql = """
            SELECT b.booking_id,
                b.contact_email,
                f.flight_date,
                r.flight_number,
                dep.city AS departure_city,
                arr.city AS arrival_city
            FROM bookings b
            JOIN booking_flights bf ON b.booking_id = bf.booking_id
            JOIN flights f ON bf.flight_id = f.flight_id
            JOIN routes r ON f.route_id = r.route_id
            JOIN airports dep ON r.departure_airport = dep.airport_id
            JOIN airports arr ON r.arrival_airport = arr.airport_id
            WHERE b.booking_id = ? AND b.user_id = ? AND b.status != 'cancelled'
            ORDER BY bf.flight_sequence
        """
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, bookingId)
                    stmt.setInt(2, userID)
                    stmt.executeQuery().use { result ->
                        val flightSummaries: MutableList<String> = mutableListOf()
                        var recipientEmail: String = ""
                        while (result.next()) {
                            recipientEmail = result.getString("contact_email") ?: recipientEmail
                            flightSummaries.add(
                                "${result.getString("flight_number")} ${result.getString("departure_city")} to ${result.getString("arrival_city")} on ${result.getString("flight_date")}",
                            )
                        }
                        if (recipientEmail.isBlank() || flightSummaries.isEmpty()) return@use null
                        BookingCancellationNotification(
                            bookingId = bookingId,
                            recipientEmail = recipientEmail,
                            flightSummary = flightSummaries.joinToString("\n"),
                        )
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }


    /**
     * Replaces all passengers on a booking with a new list.
     *
     * Verifies that the booking belongs to the specified user, then deletes the
     * existing passenger records and inserts the updated list within a single
     * transaction.
     *
     * @param bookingId the ID of the booking to update
     * @param userID the ID of the user who owns the booking
     * @param passengers the new passenger list; each map must contain `fullName`,
     *        `idNumber`, `type`, and `seat` keys
     * @return true if the passengers were updated successfully, otherwise false
     */
    fun updateBookingPassengers(bookingId: Int, userID: Int, passengers: List<Map<String, String>>): Boolean {
        // Confirm that this booking belongs to the requesting user
        val checkSql = "SELECT count(*) FROM bookings WHERE booking_id = ? AND user_id = ?"
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(checkSql).use { stmt ->
                    stmt.setInt(1, bookingId)
                    stmt.setInt(2, userID)
                    stmt.executeQuery().use { result ->
                        if (!result.next() || result.getInt(1) == 0) return false
                    }
                }

                // Delete old passengers and re-insert with updated info
                conn.autoCommit = false

                conn.prepareStatement("DELETE FROM booking_passengers WHERE booking_id = ?").use { stmt ->
                    stmt.setInt(1, bookingId)
                    stmt.executeUpdate()
                }

                val insertSql = "INSERT INTO booking_passengers(booking_id, full_name, id_number, type, seat_id) VALUES(?, ?, ?, ?, ?)"
                conn.prepareStatement(insertSql).use { stmt ->
                    for (p in passengers) {
                        stmt.setInt(1, bookingId)
                        stmt.setString(2, p["fullName"] ?: "")
                        stmt.setString(3, p["idNumber"] ?: "")
                        stmt.setString(4, p["type"] ?: "adult")
                        stmt.setString(5, p["seat"] ?: "")
                        stmt.addBatch()
                    }
                    stmt.executeBatch()
                }

                conn.commit()
                true
            }
        } catch (e: Exception) {
            false
        }
    }


    /**
     * Creates a new confirmed booking with its flights, passengers, and seat locks.
     *
     * Runs within a single transaction: inserts the booking record, links each
     * flight leg, inserts all passengers, and marks the selected seats as occupied.
     * Rolls back automatically if any step throws an exception.
     *
     * @param userID the ID of the user making the booking
     * @param username the user's email/username (currently unused; kept for compatibility)
     * @param flightIds ordered list of flight IDs comprising this booking
     * @param totalPrice the total fare charged for the booking
     * @param passengers list of passenger detail maps; each must contain `fullName`,
     *        `idNumber`, `type`, and `seat` (comma-separated seat numbers per leg)
     * @param contactEmail the contact email to store on the booking
     * @param contactPhone the contact phone number to store on the booking
     * @param tripType the trip type string, e.g. `"oneway"` or `"return"`
     * @return the generated booking ID on success, or null if the transaction failed
     */
    fun createBooking(
        userID: Int,
        username: String,
        flightIds: List<Int>,
        totalPrice: Double,
        passengers: List<Map<String, String>>,
        contactEmail: String,
        contactPhone: String,
        tripType: String,
    ): Int? {
        return try {
            Database.getConnection().use { conn ->
                conn.autoCommit = false

                // Insert the top-level booking record
                val bookingSql =
                    """
                    INSERT INTO bookings(
                        user_id,
                        booking_date,
                        total_price,
                        status,
                        contact_email,
                        contact_phone,
                        trip_type
                    )
                    VALUES (?, datetime('now'), ?, 'confirmed', ?, ?, ?)
                    """.trimIndent()
                val bookingStmt = conn.prepareStatement(bookingSql, java.sql.Statement.RETURN_GENERATED_KEYS)
                bookingStmt.setInt(1, userID)
                bookingStmt.setDouble(2, totalPrice)
                bookingStmt.setString(3, contactEmail)
                bookingStmt.setString(4, contactPhone)
                bookingStmt.setString(5, tripType)
                bookingStmt.executeUpdate()

                val keys = bookingStmt.generatedKeys
                if (!keys.next()) {
                    conn.rollback()
                    return null
                }
                val bookingId = keys.getInt(1)

                // Link each flight leg to the booking in sequence order
                val flightSql = "INSERT INTO booking_flights(booking_id, flight_id, flight_sequence) VALUES(?, ?, ?)"
                val flightStmt = conn.prepareStatement(flightSql)
                for ((index, flightId) in flightIds.withIndex()) {
                    flightStmt.setInt(1, bookingId)
                    flightStmt.setInt(2, flightId)
                    flightStmt.setInt(3, index + 1)
                    flightStmt.addBatch()
                }
                flightStmt.executeBatch()

                // Insert each passenger record
                val passengerSql = "INSERT INTO booking_passengers(booking_id, full_name, id_number, type, seat_id) VALUES(?, ?, ?, ?, ?)"
                val passengerStmt = conn.prepareStatement(passengerSql)
                for (p in passengers) {
                    passengerStmt.setInt(1, bookingId)
                    passengerStmt.setString(2, p["fullName"] ?: "")
                    passengerStmt.setString(3, p["idNumber"] ?: "")
                    passengerStmt.setString(4, p["type"] ?: "adult")
                    passengerStmt.setString(5, p["seat"] ?: "")
                    passengerStmt.addBatch()
                }
                passengerStmt.executeBatch()

                // Mark each selected seat as occupied per flight leg
                val seatSql = "UPDATE seats SET is_occupied = 1 WHERE flight_id = ? AND seat_number = ?"
                val seatStmt = conn.prepareStatement(seatSql)

                for (p in passengers) {
                    // Split comma-separated seat string e.g. "12A,14C" into individual seats
                    val seatString = p["seat"] ?: ""
                    val individualSeats = seatString.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                    // Match each seat to its corresponding flight leg by index
                    for ((index, flightId) in flightIds.withIndex()) {
                        if (index < individualSeats.size) {
                            val seatNumber = individualSeats[index]
                            
                            seatStmt.setInt(1, flightId)
                            seatStmt.setString(2, seatNumber)
                            seatStmt.addBatch()
                        }
                    }
                }
                seatStmt.executeBatch()

                conn.commit()
                bookingId
            }
        } catch (e: Exception) {
             e.printStackTrace()
            null
        }
    }

    /**
     * Returns the total loyalty points accumulated by a user.
     *
     * Points are calculated as the sum of `total_price` across all confirmed
     * bookings, truncated to an integer.
     *
     * @param userID the ID of the user to query
     * @return the user's total loyalty points, or 0 if none or on error
     */
    fun getLoyaltyPoints(userID: Int): Int {
        val sql = 
            """
            SELECT COALESCE(SUM(total_price), 0) AS points
            FROM bookings
            WHERE user_id = ?
            AND status = 'confirmed'
            """.trimIndent()

        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, userID)
                    
                    stmt.executeQuery().use { result ->
                        if (result.next()) {
                            result.getDouble("points").toInt()
                        } else {
                            0
                        }

                    }
                }
            }

        } catch (e: Exception){
            e.printStackTrace()
            0
        }
        }  

    /**
     * Retrieves a summary of a booking for use on the reschedule screen.
     *
     * Returns origin, destination, passenger count, current price, trip type,
     * and outbound/return cabin classes. For return trips the origin and
     * destination are derived from the outbound legs only.
     *
     * @param bookingId the ID of the booking to summarise
     * @return a map with keys `origin`, `destination`, `oldPrice`, `passengerCount`,
     *         `tripType`, `outboundCabin`, and `returnCabin`; or null on failure
     */
    fun getBookingForReschedule(bookingId: Int): Map<String, Any>? {
        // Fetch all legs in sequence order
        val sql = """
            SELECT r.departure_airport, r.arrival_airport, b.total_price, b.trip_type,
                bf.flight_sequence,
                (SELECT COUNT(*) FROM booking_flights WHERE booking_id = b.booking_id) as total_legs,
                (SELECT COUNT(*) FROM booking_passengers WHERE booking_id = b.booking_id) as passenger_count
            FROM bookings b
            JOIN booking_flights bf ON b.booking_id = bf.booking_id
            JOIN flights f ON bf.flight_id = f.flight_id
            JOIN routes r ON f.route_id = r.route_id
            WHERE b.booking_id = ?
            ORDER BY bf.flight_sequence ASC
        """

        /**
         * Resolves the cabin class for a specific flight sequence within a booking.
         *
         * Looks up the seat number for the first passenger on the given leg, then
         * queries the seats table for its class. Falls back to `"economy"` when
         * the seat or class cannot be resolved.
         *
         * seat_id is stored as comma-separated (e.g. `"6A,1A"`); the index matches
         * `flight_sequence`, which is 1-based (sequence=1 → index 0).
         *
         * @param conn an open database connection to reuse
         * @param bookingId the booking to inspect
         * @param sequence the 1-based leg sequence number
         * @return the lowercase cabin class string, defaulting to `"economy"`
         */
        fun queryCabin(conn: java.sql.Connection, bookingId: Int, sequence: Int): String {
            val seatSql = """
                SELECT bp.seat_id, bf.flight_id
                FROM booking_passengers bp
                JOIN booking_flights bf ON bf.booking_id = bp.booking_id AND bf.flight_sequence = ?
                WHERE bp.booking_id = ?
                ORDER BY bp.passenger_id ASC
                LIMIT 1
            """
            val seatNumber = conn.prepareStatement(seatSql).use { stmt ->
                stmt.setInt(1, sequence)
                stmt.setInt(2, bookingId)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) return "economy"
                    val seatId  = rs.getString("seat_id") ?: return "economy"
                    val seats   = seatId.split(",").map { it.trim() }
                    // sequence is 1-based; index = sequence - 1
                    seats.getOrNull(sequence - 1) ?: seats.firstOrNull() ?: return "economy"
                }
            }

            val classSql = """
                SELECT s.class as seat_class
                FROM booking_flights bf
                JOIN seats s ON s.flight_id = bf.flight_id AND s.seat_number = ?
                WHERE bf.booking_id = ? AND bf.flight_sequence = ?
                LIMIT 1
            """
            return conn.prepareStatement(classSql).use { stmt ->
                stmt.setString(1, seatNumber)
                stmt.setInt(2, bookingId)
                stmt.setInt(3, sequence)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getString("seat_class")?.lowercase() ?: "economy"
                    else "economy"
                }
            }
        }
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, bookingId)
                    stmt.executeQuery().use { rs ->
                        data class LegRow(val dep: String, val arr: String, val seq: Int)
                        val legs = mutableListOf<LegRow>()
                        var totalPrice = 0.0
                        var passengerCount = 0
                        var tripType = "oneway"
                        var totalLegs = 0

                        while (rs.next()) {
                            if (legs.isEmpty()) {
                                totalPrice     = rs.getDouble("total_price")
                                passengerCount = rs.getInt("passenger_count")
                                tripType       = rs.getString("trip_type") ?: "oneway"
                                totalLegs      = rs.getInt("total_legs")
                            }
                            legs.add(LegRow(
                                dep = rs.getString("departure_airport"),
                                arr = rs.getString("arrival_airport"),
                                seq = rs.getInt("flight_sequence")
                            ))
                        }

                        if (legs.isEmpty()) return@use null

                        val outboundLegs = if (tripType == "return") {
                            legs.take(totalLegs / 2)
                        } else {
                            legs
                        }

                        val origin      = outboundLegs.first().dep
                        val destination = outboundLegs.last().arr

                        // totalLegs and tripType are now known — safe to call queryCabin
                        val outboundCabin = queryCabin(conn, bookingId, 1)

                        val returnSeq = totalLegs / 2 + 1
                        val returnCabin = if (tripType == "return") {
                            queryCabin(conn, bookingId, returnSeq)
                        } else null

                        mapOf(
                            "origin"         to origin,
                            "destination"    to destination,
                            "oldPrice"       to totalPrice,
                            "passengerCount" to passengerCount,
                            "tripType"       to tripType,
                            "outboundCabin"  to outboundCabin,
                            "returnCabin"    to (returnCabin ?: outboundCabin)
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Executes a full reschedule transaction for an existing booking.
     *
     * Performs the following steps atomically:
     * 1. Releases (unlocks) all seats from the original flight legs.
     * 2. Updates the booking's total price and resets its status to `'confirmed'`.
     * 3. Replaces all entries in `booking_flights` with the new flight IDs.
     * 4. Updates each passenger's `seat_id` to the newly selected seats and
     *    marks those seats as occupied.
     *
     * Rolls back the entire transaction if any step fails.
     *
     * @param bookingId the ID of the booking to reschedule
     * @param newFlightIds ordered list of new flight IDs (outbound legs followed by return legs)
     * @param newTotalPrice the updated total fare after rescheduling
     * @param passengerSeatSelections map of passenger ID to comma-separated seat numbers
     *        for all new legs (e.g. `3 to "14A,7C"`)
     * @return true if the reschedule was committed successfully, otherwise false
     */
    fun executeReschedule(
        bookingId: Int,
        newFlightIds: List<Int>,
        newTotalPrice: Double,
        passengerSeatSelections: Map<Int, String>  // passengerId -> seatNumber string e.g. "14A"
    ): Boolean {
        val conn = Database.getConnection()
        return try {
            conn.setAutoCommit(false)

            // 1. Release old seats: seat_id is stored as "6A,1A" (comma-separated per leg).
            //    Split and match each seat number against its corresponding flight.
            val getOldSeats = """
                SELECT bp.seat_id, bf.flight_id, bf.flight_sequence
                FROM booking_passengers bp
                JOIN booking_flights bf ON bf.booking_id = bp.booking_id
                WHERE bp.booking_id = ?
                ORDER BY bf.flight_sequence
            """
            val releaseStmt = conn.prepareStatement(
                "UPDATE seats SET is_occupied = 0 WHERE flight_id = ? AND seat_number = ?"
            )
            conn.prepareStatement(getOldSeats).use { stmt ->
                stmt.setInt(1, bookingId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val seatId   = rs.getString("seat_id") ?: continue
                        val flightId = rs.getInt("flight_id")
                        val seq      = rs.getInt("flight_sequence")
                        val seats    = seatId.split(",").map { it.trim() }
                        val seatNum  = seats.getOrNull(seq - 1) ?: seats.firstOrNull() ?: continue
                        releaseStmt.setInt(1, flightId)
                        releaseStmt.setString(2, seatNum)
                        releaseStmt.addBatch()
                    }
                }
            }
            releaseStmt.executeBatch()

            // 2. Update total price on the booking
            conn.prepareStatement("UPDATE bookings SET total_price = ?, status = 'confirmed' WHERE booking_id = ?").use { stmt ->
                stmt.setDouble(1, newTotalPrice)
                stmt.setInt(2, bookingId)
                stmt.executeUpdate()
            }

            // 3. Replace all flights in booking_flights
            conn.prepareStatement("DELETE FROM booking_flights WHERE booking_id = ?").use { stmt ->
                stmt.setInt(1, bookingId)
                stmt.executeUpdate()
            }
            val insertFlight = "INSERT INTO booking_flights(booking_id, flight_id, flight_sequence) VALUES(?, ?, ?)"
            conn.prepareStatement(insertFlight).use { stmt ->
                for ((index, flightId) in newFlightIds.withIndex()) {
                    stmt.setInt(1, bookingId)
                    stmt.setInt(2, flightId)
                    stmt.setInt(3, index + 1)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }

            // 4. Update each passenger's seat_id (comma-separated for all legs) and lock all new seats.
            // passengerSeatSelections: Map<passengerId, "3A,7A,16A,8A">
            val updatePassengerSeat = "UPDATE booking_passengers SET seat_id = ? WHERE booking_id = ? AND passenger_id = ?"
            val lockNewSeat = "UPDATE seats SET is_occupied = 1 WHERE flight_id = ? AND seat_number = ?"

            passengerSeatSelections.forEach { (passengerId, seatsJoined) ->
                // Update seat_id with full comma-separated string
                conn.prepareStatement(updatePassengerSeat).use { stmt ->
                    stmt.setString(1, seatsJoined)
                    stmt.setInt(2, bookingId)
                    stmt.setInt(3, passengerId)
                    stmt.executeUpdate()
                }
                // Lock each seat on its corresponding flight leg
                val seatList = seatsJoined.split(",").map { it.trim() }
                seatList.forEachIndexed { index, seatNumber ->
                    val flightId = newFlightIds.getOrNull(index) ?: return@forEachIndexed
                    val rows = conn.prepareStatement(lockNewSeat).use { stmt ->
                        stmt.setInt(1, flightId)
                        stmt.setString(2, seatNumber)
                        stmt.executeUpdate()
                    }
                }
            }

            conn.commit()
            true
        } catch (e: Exception) {
            conn.rollback()
            println("ExecuteReschedule Error:")
            e.printStackTrace()
            false
        } finally {
            conn.setAutoCommit(true)
            conn.close()
        }
    }

    /**
     * Returns all passenger IDs associated with a booking, ordered ascending.
     *
     * @param bookingId the ID of the booking to query
     * @return a list of integer passenger IDs; empty if none found
     */
    fun getPassengerIdsByBooking(bookingId: Int): List<Int> {
        val ids = mutableListOf<Int>()
        val sql = "SELECT passenger_id FROM booking_passengers WHERE booking_id = ? ORDER BY passenger_id"
        Database.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, bookingId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) { ids.add(rs.getInt("passenger_id")) }
                }
            }
        }
        return ids
    }

    /**
     * Returns all flight IDs linked to a booking, in sequence order.
     *
     * Used to exclude a booking's original flights when presenting reschedule options.
     *
     * @param bookingId the ID of the booking to query
     * @return an ordered list of flight IDs; empty on error or if not found
     */
    fun getFlightIdsForBooking(bookingId: Int): List<Int> {
        val sql = "SELECT flight_id FROM booking_flights WHERE booking_id = ? ORDER BY flight_sequence"
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, bookingId)
                    stmt.executeQuery().use { rs ->
                        val ids = mutableListOf<Int>()
                        while (rs.next()) ids.add(rs.getInt("flight_id"))
                        ids
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Returns passenger details for a booking, including their internal passenger ID.
     *
     * Used to pre-fill passenger names and seats on the reschedule seat-selection screen.
     *
     * @param bookingId the ID of the booking to query
     * @return a list of maps with keys `passengerId`, `fullName`, `idNumber`, `type`,
     *         and `seat`; empty on error or if not found
     */
    fun getPassengersForBooking(bookingId: Int): List<Map<String, Any>> {
        val sql = """
            SELECT passenger_id, full_name, id_number, type, seat_id
            FROM booking_passengers
            WHERE booking_id = ?
            ORDER BY passenger_id
        """
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, bookingId)
                    stmt.executeQuery().use { rs ->
                        val passengers = mutableListOf<Map<String, Any>>()
                        while (rs.next()) {
                            passengers.add(mapOf(
                                "passengerId" to rs.getInt("passenger_id"),
                                "fullName"    to (rs.getString("full_name") ?: ""),
                                "idNumber"    to (rs.getString("id_number") ?: ""),
                                "type"        to (rs.getString("type") ?: "adult"),
                                "seat"        to (rs.getString("seat_id") ?: "")
                            ))
                        }
                        passengers
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Updates the email address stored directly on a user record.
     *
     * Note: for changes that require admin approval, use [insertChangeRequest] instead.
     *
     * @param userId the ID of the user to update
     * @param newEmail the new email address to set
     * @return true if the record was updated, otherwise false
     */
    fun updateUserEmail(userId: Int, newEmail: String): Boolean {
        val sql = "UPDATE users SET email = ? WHERE user_id = ?"
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, newEmail)
                    stmt.setInt(2, userId)
                    stmt.executeUpdate() > 0
                }
            }
        } catch (e: Exception) { false }
    }

    /**
     * Inserts a pending change request for admin review.
     *
     * Used for profile changes (e.g. name or email updates) that require
     * approval before they take effect. The request is created with a
     * status of `'pending'`.
     *
     * @param userId the ID of the user submitting the change
     * @param changeTo the new value being requested
     * @param type the type of change (e.g. `"email"`, `"name"`)
     * @return true if the request was inserted successfully, otherwise false
     */
    fun insertChangeRequest(userId: Int, changeTo: String, type: String): Boolean {
        val sql = "INSERT INTO change_requests (user_id, change_to, change_type, status) VALUES (?, ?, ?, 'pending')"
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, userId)
                    stmt.setString(2, changeTo)
                    stmt.setString(3, type)
                    stmt.executeUpdate() > 0
                }
            }
        } catch (e: Exception) { 
            e.printStackTrace()
            false 
        }
    }

    /**
     * Returns all resolved (non-pending) change request notifications for a user.
     *
     * Each entry contains the requested value, the change type, and the final
     * status (e.g. `"approved"` or `"rejected"`).
     *
     * @param userId the ID of the user whose notifications to retrieve
     * @return a list of notification maps with keys `content`, `type`, and `status`;
     *         empty on error or if none found
     */
    fun getUserNotifications(userId: Int): List<Map<String, Any>> {
        val sql = """
            SELECT change_to, change_type, status 
            FROM change_requests 
            WHERE user_id = ? AND status != 'pending'
        """
        val notifications = mutableListOf<Map<String, Any>>()
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, userId)
                    val rs = stmt.executeQuery()
                    while (rs.next()) {
                        notifications.add(mapOf(
                            "content" to rs.getString("change_to"),
                            "type" to rs.getString("change_type"),
                            "status" to rs.getString("status")
                        ))
                    }
                }
            }
         notifications
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Updates the full profile of a user in a single operation.
     *
     * Sets the first name, middle name, last name, and email simultaneously.
     * Pass an empty string for [middleName] to store a null value.
     *
     * @param userId the ID of the user to update
     * @param firstName the user's updated first name
     * @param middleName the user's updated middle name, or an empty string to clear it
     * @param lastName the user's updated last name
     * @param email the user's updated email address
     * @return true if the record was updated successfully, otherwise false
     */
    fun updateUserProfile(userId: Int, firstName: String, middleName: String, lastName: String, email: String): Boolean {
        val sql = "UPDATE users SET first_name = ?, middle_name = ?, last_name = ?, email = ? WHERE user_id = ?"
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, firstName)
                    stmt.setString(2, middleName.ifEmpty { null })
                    stmt.setString(3, lastName)
                    stmt.setString(4, email)
                    stmt.setInt(5, userId)
                    stmt.executeUpdate() > 0
                }
            }
        } catch (e: Exception) { false }
    }

    /**
     * Updates the contact email and phone number stored on a booking.
     *
     * The booking must belong to the specified user; the update affects no rows
     * (and returns false) if the IDs do not match.
     *
     * @param bookingId the ID of the booking to update
     * @param userId the ID of the user who owns the booking
     * @param newEmail the updated contact email address
     * @param newPhone the updated contact phone number
     * @return true if the contact info was updated successfully, otherwise false
     */
    fun updateContactInfo(bookingId: Int, userId: Int, newEmail: String, newPhone: String): Boolean {
        val sql = "UPDATE bookings SET contact_email = ?, contact_phone = ? WHERE booking_id = ? AND user_id = ?"
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, newEmail)
                    stmt.setString(2, newPhone)
                    stmt.setInt(3, bookingId)
                    stmt.setInt(4, userId)
                    stmt.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
        
}