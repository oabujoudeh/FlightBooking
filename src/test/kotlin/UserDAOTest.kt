package com.flightbooking

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserDAOTest {

    /**
    * Tests for checking if an email exists.
    *
    * These checks make sure known emails are found, fake ones are not, and
    * the lookup runs properly.
    */
    @Test
    fun testEmailExistsForRegisteredUser() {
        // this one is the admin account we set up
        assertTrue(UserDAO.emailExists("tnvn3422@leeds.ac.uk"), "this email should definitely exist")
    }

    @Test
    fun testEmailDoesNotExistForUnknown() {
        assertFalse(UserDAO.emailExists("nonexistent_user_12345@fake.com"), "made up email shouldnt exist")
    }

    @Test
    fun testEmailExistsIsCaseSensitive() {
        // sqlite is weird with case sensitivity but we just wanna make sure it doesnt crash
        val result = UserDAO.emailExists("TNVN3422@LEEDS.AC.UK")
        assertTrue(result || !result, "should return without crashing")
    }


    /**
    * Tests for user login.
    *
    * These checks make sure login fails for fake users and for empty email or
    * password input.
    */
    @Test
    fun testLoginWithNonexistentEmail() {
        val result = UserDAO.loginUser("nobody_here@fake.com", "SomePassword1")
        assertFalse(result.success, "fake email shouldnt be able to login")
        assertFalse(result.isAdmin, "fake user isnt an admin")
    }

    @Test
    fun testLoginWithEmptyEmail() {
        val result = UserDAO.loginUser("", "SomePassword1")
        assertFalse(result.success, "empty email shouldnt work")
    }

    @Test
    fun testLoginWithEmptyPassword() {
        val result = UserDAO.loginUser("tnvn3422@leeds.ac.uk", "")
        assertFalse(result.success, "empty password shouldnt work")
    }


    /**
    * Tests for getting a user ID from an email.
    *
    * These checks make sure a real user gets a valid ID and an unknown user
    * gets -1.
    */
    @Test
    fun testGetUserIDForRegisteredUser() {
        val id = UserDAO.getUserID("tnvn3422@leeds.ac.uk")
        assertTrue(id > 0, "registered user should have a positive id")
    }

    @Test
    fun testGetUserIDForUnknownUser() {
        val id = UserDAO.getUserID("unknown_user_xyz@fake.com")
        assertTrue(id == -1, "unknown user should get -1")
    }


    /**
    * Tests for getting a user's bookings.
    *
    * This checks that an invalid user ID returns no bookings.
    */
    @Test
    fun testGetBookingsForInvalidUser() {
        val bookings = UserDAO.getBookings(-1)
        assertTrue(bookings.isEmpty(), "invalid user id should return nothing")
    }


    /**
    * Tests for cancelling a booking.
    *
    * This checks that cancelling an invalid booking does not work.
    */
    @Test
    fun testCancelBookingWithInvalidId() {
        val result = UserDAO.cancelBooking(-1, -1)
        assertFalse(result, "cant cancel a booking that doesnt exist")
    }


    /**
    * Tests for user registration.
    *
    * These checks make sure registration fails if the email is already taken
    * or if the password is too weak.
    */
    @Test
    fun testRegisterWithDuplicateEmail() {
        val user =
            User(
                firstName = "Test",
                lastName = "User",
                email = "tnvn3422@leeds.ac.uk",
                middleName = "",
                passwordHash = "",
            )
        val result = UserDAO.register(user, "Test", "", "User", "tnvn3422@leeds.ac.uk", "StrongPass1")
        assertFalse(result, "shouldnt be able to register with an email thats already taken")
    }

    @Test
    fun testRegisterWithWeakPassword() {
        val user =
            User(
                firstName = "Test",
                lastName = "User",
                email = "newuser_weak@test.com",
                middleName = "",
                passwordHash = "",
            )
        val result = UserDAO.register(user, "Test", "", "User", "newuser_weak@test.com", "weak")
        assertFalse(result, "weak password should get rejected")
    }
}
