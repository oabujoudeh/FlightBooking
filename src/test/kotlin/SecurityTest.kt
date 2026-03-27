package com.flightbooking

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals


/* testing the password hashing and validation stuff
   making sure bcrypt is working and our password rules are right */

class SecurityTest {

    // hashing tests

    @Test
    fun testHashPasswordReturnsHashedString() {
        val hash = Security.hashPassword("TestPassword1")
        assertNotEquals("TestPassword1", hash, "the hash shouldnt just be the plain text lol")
    }

    @Test
    fun testHashPasswordProducesDifferentHashesForSameInput() {
        val hash1 = Security.hashPassword("TestPassword1")
        val hash2 = Security.hashPassword("TestPassword1")
        assertNotEquals(hash1, hash2, "bcrypt should use different salts each time")
    }


    // verification - checking passwords match their hashes

    @Test
    fun testVerifyPasswordWithCorrectPassword() {
        val hash = Security.hashPassword("MyPassword123")
        assertTrue(Security.verifyPassword("MyPassword123", hash), "right password should work")
    }

    @Test
    fun testVerifyPasswordWithWrongPassword() {
        val hash = Security.hashPassword("MyPassword123")
        assertFalse(Security.verifyPassword("WrongPassword1", hash), "wrong password shouldnt work")
    }

    @Test
    fun testVerifyPasswordIsCaseSensitive() {
        val hash = Security.hashPassword("MyPassword123")
        assertFalse(Security.verifyPassword("mypassword123", hash), "should be case sensitive")
    }


    // password validation rules - min 8 chars, upper, lower, digit

    @Test
    fun testValidPasswordAccepted() {
        assertTrue(Security.isPasswordValid("StrongPass1"), "this should be fine")
    }

    @Test
    fun testPasswordTooShort() {
        assertFalse(Security.isPasswordValid("Short1"), "too short should fail")
    }

    @Test
    fun testPasswordExactly8Chars() {
        assertTrue(Security.isPasswordValid("Abcdefg1"), "exactly 8 chars should be ok")
    }

    @Test
    fun testPasswordNoUppercase() {
        assertFalse(Security.isPasswordValid("lowercase1"), "needs at least one uppercase")
    }

    @Test
    fun testPasswordNoLowercase() {
        assertFalse(Security.isPasswordValid("UPPERCASE1"), "needs at least one lowercase")
    }

    @Test
    fun testPasswordNoDigit() {
        assertFalse(Security.isPasswordValid("NoDigitsHere"), "needs at least one number")
    }

    @Test
    fun testEmptyPasswordRejected() {
        assertFalse(Security.isPasswordValid(""), "empty password obviously shouldnt work")
    }
}
