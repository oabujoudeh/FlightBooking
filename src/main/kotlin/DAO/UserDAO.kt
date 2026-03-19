package com.flightbooking
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Random
import java.util.concurrent.ConcurrentHashMap


object UserDAO{
    // private val connection get() = Database.connection
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
            println("Error checking email: ${e.message}")
            e.printStackTrace()
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

    fun loginUser(inputEmail: String, inputPassword: String):Boolean{
        val sql = "SELECT * FROM users WHERE email = ?"

        return try{
            Database.getConnection().use{ conn ->
                conn.prepareStatement(sql).use{ stmt ->
                    stmt.setString(1, inputEmail)
                    stmt.executeQuery().use{ result->
                        if(result.next()){
                            val hashedPasswordFromDb = result.getString("password_hash")
                            SecurityDAO.verifyPassword(inputPassword, hashedPasswordFromDb)
                        }else{
                            println("Email not found")
                            false
                        }
                    }
                }
            }
        }catch(e:Exception){
            println("Login Error: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    fun resetPassword(inputEmail: String):Boolean{
        if(!emailExists(inputEmail)){
            println("Error: Email not found")
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
            println("Reset Password Error: ${e.message}")
            e.printStackTrace()
            false
        }
    }


    fun confirmResetPassword(inputEmail: String, inputOTC: String, newPassword: String):Boolean{
        // check if OTC is valid and expired
        if(!OTC.verify(inputEmail, inputOTC)){
            println("Error: Invalid or expired OTC")
            return false
        }

        // if OTC is valid, check new password validation
        if(!SecurityDAO.isPasswordValid(newPassword)){
            println("Error: Invalid new password")
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
                
                    if (rowsAffected > 0) {
                        true
                    } else {
                        println("Error: Failed to update password (user not found)")
                        false
                    }
                }
            }
        }catch(e: Exception){
            println("Error: ${e.message}")
            e.printStackTrace()
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
            println("Error getting user ID: ${e.message}")
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
            WHERE b.user_id = ?
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
            println("Error getting bookings: ${e.message}")
            emptyList()
        }
    }

    fun main(){
        // test reset page only in backend
        val testEmail = "wangbeiduo_ashely@outlook.com"
        println("start to reset")
        val isRequested = UserDAO.resetPassword(testEmail)
        println("request is $isRequested")

        if(isRequested){
            println("请获取六位数验证码")

            println("please enter your code:")
            val codeFromUser = readLine() ?:""

            println("confirmResetPassword")
            val isConfirmed = UserDAO.confirmResetPassword(testEmail, codeFromUser, "Test123456")

            if(isConfirmed){
                println("resect password successful")
            }
            else{
                println("failed to reset password")
            }
        }
    }
}