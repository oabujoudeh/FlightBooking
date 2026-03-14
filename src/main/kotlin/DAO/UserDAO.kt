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