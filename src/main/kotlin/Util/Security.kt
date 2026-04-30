package com.flightbooking

import org.mindrot.jbcrypt.BCrypt


object Security{
    /**
    * Hashes a password before saving it.
    *
    * @param plainTextPassword the password before hashing
    * @return the hashed password
    */
    fun hashPassword(plainTextPassword: String): String {
        val salt = BCrypt.gensalt()
        return BCrypt.hashpw(plainTextPassword, salt)
    }

    /**
    * Checks if a password meets the basic rules.
    *
    * It must be at least 8 characters long and contain an uppercase letter,
    * a lowercase letter, and a number.
    *
    * @param plainTextPassword the password to check
    * @return true if the password is valid, otherwise false
    */
    fun isPasswordValid(plainTextPassword: String):Boolean{
        // password has at least one upper case letter, one lower case, one digtit and length > 8
        return plainTextPassword.length >= 8 &&
            plainTextPassword.any { it.isUpperCase() } &&
            plainTextPassword.any { it.isLowerCase() } &&
            plainTextPassword.any { it.isDigit() }
    }

    /**
    * Checks if a password matches the saved hash.
    *
    * @param plainTextPassword the password entered by the user
    * @param hashedPasswordFromDb the hashed password from the database
    * @return true if they match, otherwise false
    */
    fun verifyPassword(plainTextPassword: String, hashedPasswordFromDb: String): Boolean {
        return BCrypt.checkpw(plainTextPassword, hashedPasswordFromDb)
    }
}
