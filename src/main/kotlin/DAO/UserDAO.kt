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