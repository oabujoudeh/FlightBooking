package com.flightbooking

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals


/* tests for the one time code system
   used for password resets */

class OTCTest {


    /**
    * Tests for one-time code generation.
    *
    * These checks make sure the code is 6 digits, only contains numbers,
    * and that making a new code replaces the old one.
    */
    @Test
    fun testGenerateCodeReturns6Digits() {
        val code = OTC.generateAndSave("test@example.com")
        assertEquals(6, code.length, "should be 6 digits long")
    }

    @Test
    fun testGenerateCodeIsNumeric() {
        val code = OTC.generateAndSave("numeric@example.com")
        assertTrue(code.all { it.isDigit() }, "should only have numbers in it")
    }

    @Test
    fun testGenerateCodeOverwritesPrevious() {
        val code1 = OTC.generateAndSave("overwrite@example.com")
        val code2 = OTC.generateAndSave("overwrite@example.com")
        // the old one should stop working after you generate a new one
        assertFalse(OTC.verify("overwrite@example.com", code1), "old code shouldnt work anymore")
    }


    /**
    * Tests for one-time code checking.
    *
    * These checks make sure the right code works, wrong ones fail, unknown
    * emails fail, and a code cant be used twice.
    */

    @Test
    fun testVerifyWithCorrectCode() {
        val code = OTC.generateAndSave("correct@example.com")
        assertTrue(OTC.verify("correct@example.com", code), "correct code should work")
    }

    @Test
    fun testVerifyWithWrongCode() {
        OTC.generateAndSave("wrong@example.com")
        assertFalse(OTC.verify("wrong@example.com", "000000"), "wrong code shouldnt verify")
    }

    @Test
    fun testVerifyWithUnknownEmail() {
        assertFalse(OTC.verify("unknown@example.com", "123456"), "random email shouldnt have a code")
    }

    @Test
    fun testCodeIsConsumedAfterVerification() {
        val code = OTC.generateAndSave("consumed@example.com")
        assertTrue(OTC.verify("consumed@example.com", code), "first time should work")
        assertFalse(OTC.verify("consumed@example.com", code), "second time should fail cos its used up")
    }


    /**
    * Tests for keeping one-time codes separate between emails.
    *
    * These checks make sure each email uses its own code and one user's code
    * cannot be used for another user.
    */
    @Test
    fun testDifferentEmailsDifferentCodes() {
        val code1 = OTC.generateAndSave("user1@example.com")
        val code2 = OTC.generateAndSave("user2@example.com")
        // each email should only work with its own code
        assertTrue(OTC.verify("user1@example.com", code1))
        assertTrue(OTC.verify("user2@example.com", code2))
    }

    @Test
    fun testCannotVerifyOneEmailWithAnothersCode() {
        val code1 = OTC.generateAndSave("email1@example.com")
        OTC.generateAndSave("email2@example.com")
        assertFalse(OTC.verify("email2@example.com", code1), "shouldnt be able to use someone elses code")
    }
}
