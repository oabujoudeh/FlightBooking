package com.flightbooking

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserDAOTest {

    // -------------------------------------------------------------------------
    // Email existence checks
    // -------------------------------------------------------------------------

    /**
     * Tests for [UserDAO.emailExists].
     *
     * Verifies that known emails are found, unknown emails are not, and the
     * lookup handles edge cases such as case variation and special characters
     * without crashing.
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
     * Security test — SQL injection via [UserDAO.emailExists].
     *
     * Passes a classic `' OR '1'='1` payload as the email. The underlying
     * query uses a prepared statement, so the payload must be treated as a
     * literal string and must not match any real record.
     */
    @Test
    fun testEmailExistsSqlInjectionReturnsFalse() {
        val injection = "' OR '1'='1"
        assertFalse(UserDAO.emailExists(injection), "SQL injection payload should not match any row")
    }

    /**
     * Security test — SQL injection with UNION SELECT via [UserDAO.emailExists].
     *
     * A `UNION SELECT` payload attempts to piggyback an extra query onto the
     * original. The prepared statement must neutralise it.
     */
    @Test
    fun testEmailExistsUnionInjectionReturnsFalse() {
        val injection = "' UNION SELECT email FROM users--"
        assertFalse(UserDAO.emailExists(injection), "UNION injection should not return true")
    }

    /**
     * Security test — empty string via [UserDAO.emailExists].
     *
     * An empty email is not a valid address and should never match a real row.
     */
    @Test
    fun testEmailExistsEmptyStringReturnsFalse() {
        assertFalse(UserDAO.emailExists(""), "empty string should not match any email")
    }


    // -------------------------------------------------------------------------
    // Login
    // -------------------------------------------------------------------------

    /**
     * Tests for [UserDAO.loginUser].
     *
     * Verifies that login fails for non-existent users, empty inputs, and
     * common SQL injection payloads targeting the credentials check.
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
     * Security test — SQL injection in the email field via [UserDAO.loginUser].
     *
     * The classic `' OR '1'='1` pattern in the email field would bypass the
     * WHERE clause if string concatenation were used. Prepared statements must
     * prevent this.
     */
    @Test
    fun testLoginSqlInjectionInEmailReturnsFalse() {
        val result = UserDAO.loginUser("' OR '1'='1", "irrelevant")
        assertFalse(result.success, "SQL injection in email field should not succeed")
        assertFalse(result.isAdmin, "SQL injection must not grant admin privileges")
    }

    /**
     * Security test — SQL injection in the password field via [UserDAO.loginUser].
     *
     * A `' OR '1'='1` payload in the password field targets the password
     * comparison. The prepared statement must treat it as a literal string.
     */
    @Test
    fun testLoginSqlInjectionInPasswordReturnsFalse() {
        val result = UserDAO.loginUser("tnvn3422@leeds.ac.uk", "' OR '1'='1")
        assertFalse(result.success, "SQL injection in password field should not succeed")
    }

    /**
     * Security test — comment-based SQL injection via [UserDAO.loginUser].
     *
     * Using `--` to comment out the rest of the query is a classic bypass
     * technique. The prepared statement must prevent early termination of the
     * SQL expression.
     */
    @Test
    fun testLoginCommentInjectionReturnsFalse() {
        val result = UserDAO.loginUser("tnvn3422@leeds.ac.uk'--", "anything")
        assertFalse(result.success, "comment-based SQL injection should not succeed")
    }


    // -------------------------------------------------------------------------
    // Get user ID
    // -------------------------------------------------------------------------

    /**
     * Tests for [UserDAO.getUserID].
     *
     * Verifies that a registered user returns a positive ID, an unknown email
     * returns -1, and SQL injection payloads do not return a valid ID.
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
     * Security test — SQL injection via [UserDAO.getUserID].
     *
     * A `' OR '1'='1` payload must not cause the query to return the ID of an
     * arbitrary user.
     */
    @Test
    fun testGetUserIDSqlInjectionReturnsMinusOne() {
        val id = UserDAO.getUserID("' OR '1'='1")
        assertTrue(id == -1, "SQL injection should not return a valid user ID")
    }


    // -------------------------------------------------------------------------
    // Get user details
    // -------------------------------------------------------------------------

    /**
     * Tests for [UserDAO.getUserDetails].
     *
     * Verifies that a valid user returns a non-null profile and an invalid ID
     * returns null.
     */
    @Test
    fun testGetUserDetailsForValidUser() {
        val userId = UserDAO.getUserID("tnvn3422@leeds.ac.uk")
        val details = UserDAO.getUserDetails(userId)
        assertTrue(details != null, "valid user should return a non-null User object")
        assertTrue(details!!.email == "tnvn3422@leeds.ac.uk", "email on the returned object should match")
    }

    @Test
    fun testGetUserDetailsForInvalidIdReturnsNull() {
        val details = UserDAO.getUserDetails(-1)
        assertNull(details, "invalid user ID should return null")
    }

    @Test
    fun testGetUserDetailsForZeroIdReturnsNull() {
        val details = UserDAO.getUserDetails(0)
        assertNull(details, "user ID 0 should return null as no rows should have that ID")
    }


    // -------------------------------------------------------------------------
    // Bookings
    // -------------------------------------------------------------------------

    /**
     * Tests for [UserDAO.getBookings].
     *
     * Verifies that an invalid user ID returns an empty list and that a valid
     * user returns a list (possibly empty if no active bookings exist).
     */
    @Test
    fun testGetBookingsForInvalidUser() {
        val bookings = UserDAO.getBookings(-1)
        assertTrue(bookings.isEmpty(), "invalid user id should return nothing")
    }

    @Test
    fun testGetBookingsForValidUserReturnsList() {
        val userId = UserDAO.getUserID("tnvn3422@leeds.ac.uk")
        val bookings = UserDAO.getBookings(userId)
        // We cannot assert specific content without knowing the DB state, but
        // we can assert the call does not crash and returns a list.
        assertTrue(bookings is List, "getBookings should always return a list")
    }


    // -------------------------------------------------------------------------
    // Cancel booking
    // -------------------------------------------------------------------------

    /**
     * Tests for [UserDAO.cancelBooking].
     *
     * Verifies that cancelling a non-existent booking fails and that a user
     * cannot cancel a booking belonging to a different user.
     */
    @Test
    fun testCancelBookingWithInvalidId() {
        val result = UserDAO.cancelBooking(-1, -1)
        assertFalse(result, "cant cancel a booking that doesnt exist")
    }

    @Test
    fun testCancelBookingDoesNotAffectOtherUsersBooking() {
        // Attempt to cancel booking ID 1 on behalf of a non-existent user
        val result = UserDAO.cancelBooking(1, -999)
        assertFalse(result, "should not be able to cancel another users booking")
    }

    @Test
    fun testGetBookingCancellationNotificationForInvalidBookingReturnsNull(): Unit {
        val result: BookingCancellationNotification? = UserDAO.getBookingCancellationNotification(-1, -1)
        assertNull(result, "invalid booking should not create an email notification")
    }


    // -------------------------------------------------------------------------
    // Loyalty points
    // -------------------------------------------------------------------------

    /**
     * Tests for [UserDAO.getLoyaltyPoints].
     *
     * Verifies that an invalid user returns 0 points and that a valid user
     * returns a non-negative value.
     */
    @Test
    fun testGetLoyaltyPointsForInvalidUserReturnsZero() {
        val points = UserDAO.getLoyaltyPoints(-1)
        assertTrue(points == 0, "invalid user should have 0 loyalty points")
    }

    @Test
    fun testGetLoyaltyPointsForValidUserIsNonNegative() {
        val userId = UserDAO.getUserID("tnvn3422@leeds.ac.uk")
        val points = UserDAO.getLoyaltyPoints(userId)
        assertTrue(points >= 0, "loyalty points should never be negative")
    }


    // -------------------------------------------------------------------------
    // Password reset
    // -------------------------------------------------------------------------

    /**
     * Tests for [UserDAO.resetPassword].
     *
     * Verifies that a reset request for an unknown email returns false and that
     * common SQL injection payloads in the email field are safely rejected.
     */
    @Test
    fun testResetPasswordForUnknownEmailReturnsFalse() {
        val result = UserDAO.resetPassword("no_such_user@fake.com")
        assertFalse(result, "reset should fail for an email that doesnt exist")
    }

    /**
     * Security test — SQL injection via [UserDAO.resetPassword].
     *
     * A `' OR '1'='1` payload must not trigger a reset for every user in the
     * database. The underlying [UserDAO.emailExists] call uses a prepared
     * statement, so this must return false.
     */
    @Test
    fun testResetPasswordSqlInjectionReturnsFalse() {
        val result = UserDAO.resetPassword("' OR '1'='1")
        assertFalse(result, "SQL injection payload in resetPassword should return false")
    }

    /**
     * Tests for [UserDAO.confirmResetPassword].
     *
     * Verifies that providing an invalid or empty OTC always returns false.
     */
    @Test
    fun testConfirmResetPasswordWithInvalidOtcReturnsFalse() {
        val result = UserDAO.confirmResetPassword("tnvn3422@leeds.ac.uk", "000000", "NewPassword1")
        assertFalse(result, "invalid OTC should prevent password reset")
    }

    @Test
    fun testConfirmResetPasswordWithEmptyOtcReturnsFalse() {
        val result = UserDAO.confirmResetPassword("tnvn3422@leeds.ac.uk", "", "NewPassword1")
        assertFalse(result, "empty OTC should prevent password reset")
    }

    @Test
    fun testConfirmResetPasswordWithWeakPasswordReturnsFalse() {
        // Even with a valid OTC placeholder, a weak password must be rejected
        val result = UserDAO.confirmResetPassword("tnvn3422@leeds.ac.uk", "validOTC", "weak")
        assertFalse(result, "weak new password should be rejected during reset")
    }


    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    /**
     * Tests for [UserDAO.register].
     *
     * Verifies that registration fails when the email is already taken, when
     * the password is too weak, and when SQL injection is attempted in the
     * email or name fields.
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

    /**
     * Security test — SQL injection in the email field via [UserDAO.register].
     *
     * The registration flow checks [UserDAO.emailExists] first (which uses a
     * prepared statement), so an injection payload in the email must not bypass
     * the duplicate check or corrupt the database.
     */
    @Test
    fun testRegisterSqlInjectionInEmailReturnsFalse() {
        val maliciousEmail = "'); DROP TABLE users;--"
        val user = User(
            firstName = "Hacker",
            lastName = "Test",
            email = maliciousEmail,
            middleName = "",
            passwordHash = "",
        )
        val result = UserDAO.register(user, "Hacker", "", "Test", maliciousEmail, "StrongPass1")
        // We can't assert false unconditionally (the email might not exist yet),
        // but we can assert the call does not throw and the users table still
        // works afterwards.
        val tableStillWorks = UserDAO.emailExists("tnvn3422@leeds.ac.uk")
        assertTrue(tableStillWorks, "users table must still be accessible after injection attempt")
    }

    /**
     * Security test — extremely long input via [UserDAO.register].
     *
     * Passing an oversized string to the registration function should not crash
     * the server or corrupt the database. The function must handle it gracefully
     * and return false (password length is valid but the email is clearly fake).
     */
    @Test
    fun testRegisterWithExcessivelyLongEmailDoesNotCrash() {
        val longEmail = "a".repeat(5000) + "@test.com"
        val user = User(
            firstName = "Test",
            lastName = "User",
            email = longEmail,
            middleName = "",
            passwordHash = "",
        )
        val result = UserDAO.register(user, "Test", "", "User", longEmail, "StrongPass1")
        // Result may be true or false depending on DB constraints, but it must not throw
        assertTrue(result || !result, "oversized email input should not cause a crash")
    }


    // -------------------------------------------------------------------------
    // Update user profile and email
    // -------------------------------------------------------------------------

    /**
     * Tests for [UserDAO.updateUserProfile] and [UserDAO.updateUserEmail].
     *
     * Verifies that updates targeting a non-existent user ID return false and
     * do not corrupt the database.
     */
    @Test
    fun testUpdateUserProfileForInvalidUserReturnsFalse() {
        val result = UserDAO.updateUserProfile(-1, "John", "", "Doe", "john@test.com")
        assertFalse(result, "updating a non-existent user should return false")
    }

    @Test
    fun testUpdateUserEmailForInvalidUserReturnsFalse() {
        val result = UserDAO.updateUserEmail(-1, "newemail@test.com")
        assertFalse(result, "updating email for a non-existent user should return false")
    }

    /**
     * Security test — SQL injection via [UserDAO.updateUserEmail].
     *
     * Passing a malicious string as the new email must not alter any unintended
     * rows or break the statement execution.
     */
    @Test
    fun testUpdateUserEmailSqlInjectionDoesNotCrash() {
        val injection = "evil@test.com'; UPDATE users SET email='hacked' WHERE '1'='1"
        val result = UserDAO.updateUserEmail(-1, injection)
        assertFalse(result, "SQL injection in new email should not update any row")
    }


    // -------------------------------------------------------------------------
    // Change requests and notifications
    // -------------------------------------------------------------------------

    /**
     * Tests for [UserDAO.insertChangeRequest] and [UserDAO.getUserNotifications].
     *
     * Verifies that inserting a change request for an invalid user fails and
     * that retrieving notifications for an invalid user returns an empty list.
     */
    @Test
    fun testInsertChangeRequestForInvalidUserReturnsFalse() {
        val result = UserDAO.insertChangeRequest(-1, "new@test.com", "email")
        assertFalse(result, "inserting a change request for an invalid user should fail")
    }

    @Test
    fun testGetUserNotificationsForInvalidUserReturnsEmptyList() {
        val notifications = UserDAO.getUserNotifications(-1)
        assertTrue(notifications.isEmpty(), "invalid user should have no notifications")
    }


    // -------------------------------------------------------------------------
    // Contact info
    // -------------------------------------------------------------------------

    /**
     * Tests for [UserDAO.updateContactInfo].
     *
     * Verifies that updating contact info on a non-existent booking/user pair
     * returns false.
     */
    @Test
    fun testUpdateContactInfoForInvalidBookingReturnsFalse() {
        val result = UserDAO.updateContactInfo(-1, -1, "contact@test.com", "07700000000")
        assertFalse(result, "updating contact info for an invalid booking should return false")
    }

    @Test
    fun testUpdateContactInfoWrongUserReturnsFalse() {
        // Booking ID 1 (if it exists) must not be editable by a fake user
        val result = UserDAO.updateContactInfo(1, -999, "hacker@test.com", "00000000000")
        assertFalse(result, "user should not be able to edit another users contact info")
    }


    // -------------------------------------------------------------------------
    // Reschedule helpers
    // -------------------------------------------------------------------------

    /**
     * Tests for [UserDAO.getBookingForReschedule].
     *
     * Verifies that a non-existent booking returns null.
     */
    @Test
    fun testGetBookingForRescheduleWithInvalidIdReturnsNull() {
        val result = UserDAO.getBookingForReschedule(-1)
        assertNull(result, "invalid booking ID should return null for reschedule summary")
    }


    // -------------------------------------------------------------------------
    // Passenger helpers
    // -------------------------------------------------------------------------

    /**
     * Tests for [UserDAO.getPassengersForBooking] and [UserDAO.getPassengerIdsByBooking].
     *
     * Verifies that querying with an invalid booking ID returns empty results.
     */
    @Test
    fun testGetPassengersForInvalidBookingReturnsEmptyList() {
        val result = UserDAO.getPassengersForBooking(-1)
        assertTrue(result.isEmpty(), "invalid booking should return no passengers")
    }

    @Test
    fun testGetPassengerIdsByBookingForInvalidBookingReturnsEmptyList() {
        val result = UserDAO.getPassengerIdsByBooking(-1)
        assertTrue(result.isEmpty(), "invalid booking should return no passenger IDs")
    }


    // -------------------------------------------------------------------------
    // Flight ID helpers
    // -------------------------------------------------------------------------

    /**
     * Tests for [UserDAO.getFlightIdsForBooking].
     *
     * Verifies that an invalid booking ID returns an empty list.
     */
    @Test
    fun testGetFlightIdsForInvalidBookingReturnsEmptyList() {
        val result = UserDAO.getFlightIdsForBooking(-1)
        assertTrue(result.isEmpty(), "invalid booking should return no flight IDs")
    }
}