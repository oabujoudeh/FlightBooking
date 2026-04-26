package com.flightbooking

import org.mindrot.jbcrypt.BCrypt

object Security {
    fun hashPassword(plainTextPassword: String): String {
        val salt = BCrypt.gensalt()
        return BCrypt.hashpw(plainTextPassword, salt)
    }

    fun isPasswordValid(plainTextPassword: String): Boolean {
        // password has at least one upper case letter, one lower case, one digtit and length > 8
        return plainTextPassword.length >= 8 &&
            plainTextPassword.any { it.isUpperCase() } &&
            plainTextPassword.any { it.isLowerCase() } &&
            plainTextPassword.any { it.isDigit() }
    }

    fun verifyPassword(
        plainTextPassword: String,
        hashedPasswordFromDb: String,
    ): Boolean = BCrypt.checkpw(plainTextPassword, hashedPasswordFromDb)
}
