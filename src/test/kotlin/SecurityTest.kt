package com.flightbooking

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/* testing the password hashing and validation stuff
   making sure bcrypt is working and our password rules are right */

class SecurityTest {

    /**
    * Tests for password hashing.
    *
    * These checks make sure the password gets turned into a hash and that the
    * same password does not give the exact same hash every time.
    */
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


    /**
    * Tests for password checking.
    *
    * These checks make sure the correct password matches, the wrong one fails,
    * and password checking is case sensitive.
    */
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


    /**
    * Tests for password validation rules.
    *
    * These checks make sure only passwords that meet the basic rules are
    * accepted, and invalid ones are rejected.
    */
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
