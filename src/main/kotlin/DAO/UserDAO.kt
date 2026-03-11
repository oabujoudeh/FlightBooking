package com.flightbooking
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException
import java.time.LocalDateTime
import java.time.Duration
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

    fun signUp(user: User, inputFirstName: String, inputMiddleName: String, inputLastName: String, inputEmail: String, inputPassword: String): Boolean {
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

                    val rowsAffected = stmt.executeUpdate()
                    rowsAffected > 0
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun loginUser(inputEmail: String, inputPassword: String):Boolean{
        val sql = "SELECT * FROM users WHERE email = ?"

        try{
            val stmt = Database.getConnection().prepareStatement(sql)
            stmt.setString(1, inputEmail)
            val result = stmt.executeQuery()

            if(result.next()){
                // if email is found, extract the hashedPassword from database
                val hashedPasswordFromDb = result.getString("password_hash")
                val hashedPasswordFromUser = SecurityDAO.hashPassword(inputPassword)
                val isPasswordCorrect = SecurityDAO.verifyPassword(hashedPasswordFromDb, hashedPasswordFromUser)

                if (isPasswordCorrect){
                    return true
                }
                else{
                    return false
                }
            }
            else{
                println("Email not found")
                return false
            }
        }
        catch(e:Exception){
            println("Login Error: ${e.message}")
            return false
        }
    }

    fun resetPassword(inputEmail: String):Boolean{
        if(!emailExists(inputEmail)){
            println("Error: Email not found")
            return false
        }

        // email found, generate OTC and send email
        // val generatedCode = OtcService.generateAndSave(inputEmail)
        // EmailService.sendEmail(
            // to = inputEmail,
            // subject = "Your One-Time Code for Password Reset",
            // body = "Your one-time code is: $generatedCode. It will expire in 5 minutes."
        //)
        return true
    }

    fun confirmResetPassword(inputEmail: String, inputOTC: String, newPassword: String):Boolean{
        // check if OTC is valid and expired
        // if(!OtcService.verify(inputEmail, inputOTC)){
        //     println("Error: Invalid or expired OTC")
        //     return false
        // }
        // if OTC is valid, check new password validation
        if(!SecurityDAO.isPasswordValid(newPassword)){
            println("Error: Invalid new password")
            return false 
        }
        // if valid, hash the new password and update to database
        val hashedNewPassword = SecurityDAO.hashPassword(newPassword)
        val sql = "UPDATE users SET password_hash = ? WHERE email = ?"
        try{
            val stmt = Database.getConnection().prepareStatement(sql)
            stmt.setString(1, hashedNewPassword)
            stmt.setString(2, inputEmail)
            val rowsAffected = stmt.executeUpdate()
            if(rowsAffected > 0){
                return true // password reset successful
            }else{
                println("Error: Failed to update password")
                return false
            }
        }
        catch(e: Exception){
            println("Error: ${e.message}")
            return false
        }
    }
}