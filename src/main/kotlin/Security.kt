package com.flightbooking
import org.mindrot.jbcrypt.BCrypt

object SecurityDAO{
    fun hashPassword(plainTextPassword: String): String {
        val salt = BCrypt.gensalt()
        return BCrypt.hashpw(plainTextPassword, salt)
    }

    fun isPasswordValid(plainTextPassword: String):Boolean{
        // password has at least one upper case letter, one lower case, one digtit and length > 8
        if(plainTextPassword.length < 8) return false
        val hasUppercase = plainTextPassword.any{it.isUpperCase()}
        val hasLowercase = plainTextPassword.any{it.isLowerCase()}
        val hasDigit = plainTextPassword.any{it.isDigit()}
        return hasUppercase && hasLowercase && hasDigit
    }

    fun verifyPassword(plainTextPassword: String, hashedPasswordFromDb: String): Boolean {
        return BCrypt.checkpw(plainTextPassword, hashedPasswordFromDb)
    }
}