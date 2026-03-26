package com.flightbooking
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime


object UserDAO{
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

    fun register(user: User, inputFirstName: String, inputMiddleName: String, inputLastName: String, inputEmail: String, inputPassword: String): Boolean {
        if (!SecurityDAO.isPasswordValid(inputPassword)) return false
        if (emailExists(inputEmail)) return false

        val hashedPassword = SecurityDAO.hashPassword(inputPassword)
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

    data class LoginResult(val success: Boolean, val isAdmin: Boolean = false)

    fun loginUser(inputEmail: String, inputPassword: String): LoginResult {
        val sql = "SELECT password_hash, is_admin FROM users WHERE email = ?"

        return try{
            Database.getConnection().use{ conn ->
                conn.prepareStatement(sql).use{ stmt ->
                    stmt.setString(1, inputEmail)
                    stmt.executeQuery().use{ result->
                        if(result.next()){
                            val hashedPasswordFromDb = result.getString("password_hash")
                            val isAdmin = result.getInt("is_admin") == 1
                            if(SecurityDAO.verifyPassword(inputPassword, hashedPasswordFromDb)){
                                LoginResult(success = true, isAdmin = isAdmin)
                            } else {
                                LoginResult(success = false)
                            }
                        }else{
                            LoginResult(success = false)
                        }
                    }
                }
            }
        }catch(e:Exception){
            LoginResult(success = false)
        }
    }

    fun resetPassword(inputEmail: String):Boolean{
        if(!emailExists(inputEmail)){
            return false
        }
        return try{
            // email found, generate OTC and send email
            val generatedCode = OTC.generateAndSave(inputEmail)

            EmailService.sendEmail(
                to = inputEmail,
                subject = "Your One-Time Code for Password Reset",
                body = "Your one-time code is: $generatedCode. It will expire in 5 minutes."
            )
            true
        }
        catch(e:Exception){
            false
        }
    }


    fun confirmResetPassword(inputEmail: String, inputOTC: String, newPassword: String):Boolean{
        // check if OTC is valid and expired
        if(!OTC.verify(inputEmail, inputOTC)){
            return false
        }

        // if OTC is valid, check new password validation
        if(!SecurityDAO.isPasswordValid(newPassword)){
            return false
        }

        // if valid, hash the new password and update to database
        val hashedNewPassword = SecurityDAO.hashPassword(newPassword)
        val sql = "UPDATE users SET password_hash = ? WHERE email = ?"
        return try{
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, hashedNewPassword)
                    stmt.setString(2, inputEmail)
                
                    val rowsAffected = stmt.executeUpdate()
                
                    rowsAffected > 0
                }
            }
        }catch(e: Exception){
            false
        }
    }

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

    // pulls the flight info out of a single row and converts it into a map
    // we need to do some timezone fixes ton get correct arrival time i think 
    private fun getFlightInfoFromRow(result: java.sql.ResultSet): Map<String, Any> {
        val durationMinutes = result.getInt("base_duration_minutes")

        val departureDate = LocalDate.parse(result.getString("flight_date"))
        val departureTime = LocalTime.parse(result.getString("planned_departure"))

        val departureTimezone = ZoneId.of(result.getString("departure_timezone") ?: "UTC")
        val arrivalTimezone = ZoneId.of(result.getString("arrival_timezone") ?: "UTC")

        // work out the arrival time by adding the duration and converting to the arrival timezone
        val departureDateTime = ZonedDateTime.of(departureDate, departureTime, departureTimezone)
        val arrivalDateTime = departureDateTime.plusMinutes(durationMinutes.toLong())
        val arrivalTime = arrivalDateTime.withZoneSameInstant(arrivalTimezone).toLocalTime()

        val flightInfo = mutableMapOf<String, Any>()
        flightInfo["flightNumber"] = result.getString("flight_number")
        flightInfo["departureCity"] = result.getString("departure_city")
        flightInfo["arrivalCity"] = result.getString("arrival_city")
        flightInfo["departureAirport"] = result.getString("departure_airport_name")
        flightInfo["arrivalAirport"] = result.getString("arrival_airport_name")
        flightInfo["departureTerminal"] = result.getString("departure_terminal") ?: ""
        flightInfo["arrivalTerminal"] = result.getString("arrival_terminal") ?: ""
        flightInfo["departureDate"] = departureDate.toString()
        flightInfo["departureTime"] = departureTime.toString()
        flightInfo["arrivalTime"] = arrivalTime.toString()
        flightInfo["duration"] = Utils.formatDuration(durationMinutes)
        return flightInfo
    }

    fun getBookings(userID: Int): List<Map<String, Any>> {
        // get all bookings for the user, joining to get the flight and route info
        // a booking can have multiple flights so we group by booking_id at the end
        val sql = """
            SELECT
                b.booking_id, b.booking_date, b.total_price, b.status,
                f.flight_date,
                r.flight_number, r.departure_terminal, r.arrival_terminal,
                r.planned_departure, r.base_duration_minutes,
                dep.city as departure_city, arr.city as arrival_city,
                dep.name as departure_airport_name, arr.name as arrival_airport_name,
                dep.timezone as departure_timezone, arr.timezone as arrival_timezone,
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
                        // use a map so we can group flights under the same booking
                        val bookings = mutableListOf<MutableMap<String, Any>>()
                        val seenBookingIds = mutableMapOf<Int, MutableMap<String, Any>>()

                        while (result.next()) {
                            val bookingId = result.getInt("booking_id")

                            // if we haven't seen this booking before, create a new entry for it
                            if (!seenBookingIds.containsKey(bookingId)) {
                                val newBooking = mutableMapOf<String, Any>()
                                newBooking["bookingId"] = bookingId
                                newBooking["bookingDate"] = result.getString("booking_date") ?: ""
                                newBooking["totalPrice"] = result.getDouble("total_price")
                                newBooking["status"] = result.getString("status")
                                newBooking["flights"] = mutableListOf<Map<String, Any>>()
                                bookings.add(newBooking)
                                seenBookingIds[bookingId] = newBooking
                            }

                            // add this flight to the booking's flight list
                            val booking = seenBookingIds[bookingId]!!
                            @Suppress("UNCHECKED_CAST")
                            val flightList = booking["flights"] as MutableList<Map<String, Any>>
                            flightList.add(getFlightInfoFromRow(result))
                        }
                        bookings
                    }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getBookingById(bookingId: Int, userID: Int): Map<String, Any>? {
        // get a single booking with its flights and passengers
        val bookingSql = """
            SELECT
                b.booking_id, b.booking_date, b.total_price, b.status, b.contact_email,
                f.flight_date,
                r.flight_number, r.departure_terminal, r.arrival_terminal,
                r.planned_departure, r.base_duration_minutes,
                dep.city as departure_city, arr.city as arrival_city,
                dep.name as departure_airport_name, arr.name as arrival_airport_name,
                dep.timezone as departure_timezone, arr.timezone as arrival_timezone,
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

        return try {
            Database.getConnection().use { conn ->
                // get booking and flights
                val booking = mutableMapOf<String, Any>()
                conn.prepareStatement(bookingSql).use { stmt ->
                    stmt.setInt(1, bookingId)
                    stmt.setInt(2, userID)
                    stmt.executeQuery().use { result ->
                        val flights = mutableListOf<Map<String, Any>>()
                        var found = false

                        while(result.next()){
                            if(!found){
                                booking["bookingId"] = result.getInt("booking_id")
                                booking["bookingDate"] = result.getString("booking_date") ?: ""
                                booking["totalPrice"] = result.getDouble("total_price")
                                booking["status"] = result.getString("status")
                                booking["contactEmail"] = result.getString("contact_email") ?: ""
                                found = true
                            }
                            flights.add(getFlightInfoFromRow(result))
                        }

                        if(!found) return@use null
                        booking["flights"] = flights
                    }
                }

                // get passengers
                conn.prepareStatement(passengerSql).use { stmt ->
                    stmt.setInt(1, bookingId)
                    stmt.executeQuery().use { result ->
                        val passengers = mutableListOf<Map<String, Any>>()
                        while(result.next()){
                            passengers.add(mapOf(
                                "fullName" to result.getString("full_name"),
                                "idNumber" to result.getString("id_number"),
                                "type" to result.getString("type"),
                                "seat" to result.getString("seat_id")
                            ))
                        }
                        booking["passengers"] = passengers
                    }
                }

                booking
            }
        } catch(e: Exception){
            null
        }
    }

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
        } catch(e: Exception){
            false
        }
    }

    fun updateBookingPassengers(bookingId: Int, userID: Int, passengers: List<Map<String, String>>): Boolean {
        // make sure booking belongs to this user
        val checkSql = "SELECT count(*) FROM bookings WHERE booking_id = ? AND user_id = ?"
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(checkSql).use { stmt ->
                    stmt.setInt(1, bookingId)
                    stmt.setInt(2, userID)
                    stmt.executeQuery().use { result ->
                        if(!result.next() || result.getInt(1) == 0) return false
                    }
                }

                // delete old passengers and re-insert with updated info
                conn.autoCommit = false

                conn.prepareStatement("DELETE FROM booking_passengers WHERE booking_id = ?").use { stmt ->
                    stmt.setInt(1, bookingId)
                    stmt.executeUpdate()
                }

                val insertSql = "INSERT INTO booking_passengers(booking_id, full_name, id_number, type, seat_id) VALUES(?, ?, ?, ?, ?)"
                conn.prepareStatement(insertSql).use { stmt ->
                    for(p in passengers){
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
        } catch(e: Exception){
            false
        }
    }

    fun createBooking(userID: Int, email: String, flightIds: List<Int>, totalPrice: Double, passengers: List<Map<String, String>>): Boolean {
        return try {
            Database.getConnection().use { conn ->
                conn.autoCommit = false

                // insert booking
                val bookingSql = "INSERT INTO bookings(user_id, booking_date, total_price, status, contact_email, contact_phone) VALUES(?, datetime('now'), ?, 'confirmed', ?, 0)"
                val bookingStmt = conn.prepareStatement(bookingSql, java.sql.Statement.RETURN_GENERATED_KEYS)
                bookingStmt.setInt(1, userID)
                bookingStmt.setDouble(2, totalPrice)
                bookingStmt.setString(3, email)
                bookingStmt.executeUpdate()

                val keys = bookingStmt.generatedKeys
                if(!keys.next()){
                    conn.rollback()
                    return false
                }
                val bookingId = keys.getInt(1)

                // insert booking flights
                val flightSql = "INSERT INTO booking_flights(booking_id, flight_id, flight_sequence) VALUES(?, ?, ?)"
                val flightStmt = conn.prepareStatement(flightSql)
                for((index, flightId) in flightIds.withIndex()){
                    flightStmt.setInt(1, bookingId)
                    flightStmt.setInt(2, flightId)
                    flightStmt.setInt(3, index + 1)
                    flightStmt.addBatch()
                }
                flightStmt.executeBatch()

                // insert passengers
                val passengerSql = "INSERT INTO booking_passengers(booking_id, full_name, id_number, type, seat_id) VALUES(?, ?, ?, ?, ?)"
                val passengerStmt = conn.prepareStatement(passengerSql)
                for(p in passengers){
                    passengerStmt.setInt(1, bookingId)
                    passengerStmt.setString(2, p["fullName"] ?: "")
                    passengerStmt.setString(3, p["idNumber"] ?: "")
                    passengerStmt.setString(4, p["type"] ?: "adult")
                    passengerStmt.setString(5, p["seat"] ?: "")
                    passengerStmt.addBatch()
                }
                passengerStmt.executeBatch()

                // mark seats as occupied
                val seatSql = "UPDATE seats SET is_occupied = 1 WHERE flight_id = ? AND seat_number = ?"
                val seatStmt = conn.prepareStatement(seatSql)
                for(p in passengers){
                    for(flightId in flightIds){
                        seatStmt.setInt(1, flightId)
                        seatStmt.setString(2, p["seat"] ?: "")
                        seatStmt.addBatch()
                    }
                }
                seatStmt.executeBatch()

                conn.commit()
                true
            }
        } catch(e: Exception){
            false
        }
    }
}