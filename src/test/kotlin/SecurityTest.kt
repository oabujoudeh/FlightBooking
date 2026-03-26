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
        val hash = SecurityDAO.hashPassword("TestPassword1")
        assertNotEquals("TestPassword1", hash, "the hash shouldnt just be the plain text lol")
    }

    @Test
    fun testHashPasswordProducesDifferentHashesForSameInput() {
        val hash1 = SecurityDAO.hashPassword("TestPassword1")
        val hash2 = SecurityDAO.hashPassword("TestPassword1")
        assertNotEquals(hash1, hash2, "bcrypt should use different salts each time")
    }


    // verification - checking passwords match their hashes

    @Test
    fun testVerifyPasswordWithCorrectPassword() {
        val hash = SecurityDAO.hashPassword("MyPassword123")
        assertTrue(SecurityDAO.verifyPassword("MyPassword123", hash), "right password should work")
    }

    @Test
    fun testVerifyPasswordWithWrongPassword() {
        val hash = SecurityDAO.hashPassword("MyPassword123")
        assertFalse(SecurityDAO.verifyPassword("WrongPassword1", hash), "wrong password shouldnt work")
    }

    @Test
    fun testVerifyPasswordIsCaseSensitive() {
        val hash = SecurityDAO.hashPassword("MyPassword123")
        assertFalse(SecurityDAO.verifyPassword("mypassword123", hash), "should be case sensitive")
    }


    // password validation rules - min 8 chars, upper, lower, digit

    @Test
    fun testValidPasswordAccepted() {
        assertTrue(SecurityDAO.isPasswordValid("StrongPass1"), "this should be fine")
    }

    @Test
    fun testPasswordTooShort() {
        assertFalse(SecurityDAO.isPasswordValid("Short1"), "too short should fail")
    }

    @Test
    fun testPasswordExactly8Chars() {
        assertTrue(SecurityDAO.isPasswordValid("Abcdefg1"), "exactly 8 chars should be ok")
    }

    @Test
    fun testPasswordNoUppercase() {
        assertFalse(SecurityDAO.isPasswordValid("lowercase1"), "needs at least one uppercase")
    }

    @Test
    fun testPasswordNoLowercase() {
        assertFalse(SecurityDAO.isPasswordValid("UPPERCASE1"), "needs at least one lowercase")
    }

    @Test
    fun testPasswordNoDigit() {
        assertFalse(SecurityDAO.isPasswordValid("NoDigitsHere"), "needs at least one number")
    }

    @Test
    fun testEmptyPasswordRejected() {
        assertFalse(SecurityDAO.isPasswordValid(""), "empty password obviously shouldnt work")
    }
}
