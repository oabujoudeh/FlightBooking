package com.flightbooking

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [UserSession] and [getSessionData].
 *
 * [UserSession] is a pure Kotlin data class, so the bulk of the suite tests its
 * default values, copy semantics, equality, and interactions with [PendingBooking]
 * and [PendingReschedule]. [getSessionData] requires an [io.ktor.server.application.ApplicationCall],
 * so those tests use Ktor's [testApplication] engine with a minimal in-process
 * server that installs [configureSessions] and exposes session state via a helper route.
 */
class UserSessionTest {

    // ── Default values ─────────────────────────────────────────────────────────

    /**
     * Verifies that a [UserSession] constructed with no arguments has an empty
     * username, representing the unauthenticated state.
     */
    @Test
    fun testDefaultUsernameIsEmpty() {
        val session = UserSession()
        assertEquals("", session.username)
    }

    /**
     * Verifies that the default session is not marked as logged in.
     */
    @Test
    fun testDefaultLoggedInIsFalse() {
        val session = UserSession()
        assertFalse(session.loggedIn)
    }

    /**
     * Verifies that the default session carries no flash message.
     */
    @Test
    fun testDefaultMessageIsEmpty() {
        val session = UserSession()
        assertEquals("", session.message)
    }

    /**
     * Verifies that the default session has no admin privileges.
     */
    @Test
    fun testDefaultIsAdminIsFalse() {
        val session = UserSession()
        assertFalse(session.isAdmin)
    }

    /**
     * Verifies that the default session has no pending booking.
     */
    @Test
    fun testDefaultPendingBookingIsNull() {
        val session = UserSession()
        assertNull(session.pendingBooking)
    }

    /**
     * Verifies that the default session has no reschedule booking ID.
     */
    @Test
    fun testDefaultRescheduleBookingIdIsNull() {
        val session = UserSession()
        assertNull(session.rescheduleBookingId)
    }

    /**
     * Verifies that the default session has no pending reschedule.
     */
    @Test
    fun testDefaultPendingRescheduleIsNull() {
        val session = UserSession()
        assertNull(session.pendingReschedule)
    }

    // ── Authenticated session ──────────────────────────────────────────────────

    /**
     * Verifies that a logged-in session correctly stores the username and
     * [UserSession.loggedIn] flag.
     */
    @Test
    fun testLoggedInSessionStoresUsername() {
        val session = UserSession(username = "alice@example.com", loggedIn = true)
        assertEquals("alice@example.com", session.username)
        assertTrue(session.loggedIn)
    }

    /**
     * Verifies that an admin session has both [UserSession.loggedIn] and
     * [UserSession.isAdmin] set to true.
     */
    @Test
    fun testAdminSessionHasBothFlags() {
        val session = UserSession(username = "admin@example.com", loggedIn = true, isAdmin = true)
        assertTrue(session.loggedIn)
        assertTrue(session.isAdmin)
    }

    /**
     * Verifies that a non-admin authenticated session has [UserSession.isAdmin]
     * set to false even when [UserSession.loggedIn] is true.
     */
    @Test
    fun testNonAdminAuthenticatedSessionIsNotAdmin() {
        val session = UserSession(username = "user@example.com", loggedIn = true, isAdmin = false)
        assertTrue(session.loggedIn)
        assertFalse(session.isAdmin)
    }

    // ── Flash message ──────────────────────────────────────────────────────────

    /**
     * Verifies that a flash message is stored and retrieved correctly.
     */
    @Test
    fun testFlashMessageIsStoredCorrectly() {
        val msg = "Your booking was confirmed."
        val session = UserSession(message = msg)
        assertEquals(msg, session.message)
    }

    /**
     * Verifies that clearing the message via [copy] produces an empty string.
     */
    @Test
    fun testClearingMessageProducesEmptyString() {
        val session = UserSession(message = "old message").copy(message = "")
        assertEquals("", session.message)
    }

    // ── copy semantics ─────────────────────────────────────────────────────────

    /**
     * Verifies that [copy] produces a new session with updated fields while
     * leaving all other fields unchanged.
     */
    @Test
    fun testCopyUpdatesOnlySpecifiedField() {
        val original = UserSession(username = "user@example.com", loggedIn = true, isAdmin = false)
        val promoted = original.copy(isAdmin = true)
        assertTrue(promoted.isAdmin)
        assertEquals(original.username, promoted.username)
        assertEquals(original.loggedIn, promoted.loggedIn)
    }

    /**
     * Verifies that [copy] with no arguments produces a session equal to the original.
     */
    @Test
    fun testCopyWithNoArgsProducesEqualSession() {
        val session = UserSession(username = "x@x.com", loggedIn = true)
        assertEquals(session, session.copy())
    }

    // ── Equality and identity ──────────────────────────────────────────────────

    /**
     * Verifies that two [UserSession] instances constructed with identical arguments
     * are equal (data-class structural equality).
     */
    @Test
    fun testIdenticalSessionsAreEqual() {
        val a = UserSession(username = "bob@example.com", loggedIn = true)
        val b = UserSession(username = "bob@example.com", loggedIn = true)
        assertEquals(a, b)
    }

    /**
     * Verifies that sessions with different usernames are not equal.
     */
    @Test
    fun testSessionsWithDifferentUsernamesAreNotEqual() {
        val a = UserSession(username = "alice@example.com")
        val b = UserSession(username = "bob@example.com")
        assertNotEquals(a, b)
    }

    /**
     * Verifies that a logged-in session and a default session are not equal.
     */
    @Test
    fun testLoggedInAndDefaultSessionsAreNotEqual() {
        assertNotEquals(UserSession(loggedIn = true), UserSession())
    }

 

    // ── PendingReschedule integration ──────────────────────────────────────────

    /**
     * Verifies that a [PendingReschedule] is stored and retrieved correctly inside
     * a [UserSession].
     */
    @Test
    fun testPendingRescheduleIsStoredInSession() {
        val reschedule = PendingReschedule(
            bookingId = 42,
            newFlightIds = listOf(301, 302),
            newTotalPrice = 199.0,
            passengerSeatSelections = mapOf(1 to "3A", 2 to "3B")
        )
        val session = UserSession(
            rescheduleBookingId = 42,
            pendingReschedule = reschedule
        )
        assertNotNull(session.pendingReschedule)
        assertEquals(42, session.pendingReschedule!!.bookingId)
        assertEquals(42, session.rescheduleBookingId)
        assertEquals(listOf(301, 302), session.pendingReschedule!!.newFlightIds)
    }

    /**
     * Verifies that [UserSession.rescheduleBookingId] and [UserSession.pendingReschedule]
     * can be cleared independently via [copy].
     */
    @Test
    fun testClearingRescheduleFieldsSetsThemToNull() {
        val session = UserSession(
            rescheduleBookingId = 7,
            pendingReschedule = PendingReschedule(7, listOf(1), 100.0, emptyMap())
        )
        val cleared = session.copy(rescheduleBookingId = null, pendingReschedule = null)
        assertNull(cleared.rescheduleBookingId)
        assertNull(cleared.pendingReschedule)
    }

    // ── getSessionData: no active session ─────────────────────────────────────

    /**
     * Verifies that [getSessionData] returns safe defaults when no session cookie
     * is present. Uses a minimal in-process Ktor server that installs
     * [configureSessions] and writes the session map to a response header so the
     * test can inspect it without full JSON serialisation.
     *
     * Expected defaults: `loggedIn=false`, `username=""`, `message=""`, `isAdmin=false`.
     */
    @Test
    fun testGetSessionDataDefaultsWhenNoSession() = testApplication {
        application {
            configureSessions()
            routing {
                get("/session-check") {
                    val data = getSessionData(call)
                    call.response.headers.append("X-LoggedIn", data["loggedIn"].toString())
                    call.response.headers.append("X-Username", data["username"].toString())
                    call.response.headers.append("X-Message", data["message"].toString())
                    call.response.headers.append("X-IsAdmin", data["isAdmin"].toString())
                    call.respond(HttpStatusCode.OK)
                }
            }
        }

        val response = client.get("/session-check")
        assertEquals("false", response.headers["X-LoggedIn"])
        assertEquals("", response.headers["X-Username"])
        assertEquals("", response.headers["X-Message"])
        assertEquals("false", response.headers["X-IsAdmin"])
    }


    // ── getSessionData: with an active session ─────────────────────────────────


    /**
     * Verifies that [getSessionData] correctly surfaces [UserSession.isAdmin]
     * as `"true"` for an admin session.
     */
    @Test
    fun testGetSessionDataReflectsAdminFlag() = testApplication {
        application {
            configureSessions()
            routing {
                get("/set-admin") {
                    call.sessions.set(
                        UserSession(username = "admin@example.com", loggedIn = true, isAdmin = true)
                    )
                    call.respond(HttpStatusCode.OK)
                }
                get("/read-admin") {
                    val data = getSessionData(call)
                    call.response.headers.append("X-IsAdmin", data["isAdmin"].toString())
                    call.respond(HttpStatusCode.OK)
                }
            }
        }

        val cookieClient = createClient { followRedirects = false }
        val setResp = cookieClient.get("/set-admin")
        val cookie = setResp.headers["Set-Cookie"] ?: ""
        val readResp = cookieClient.get("/read-admin") {
            headers.append("Cookie", cookie.substringBefore(";"))
        }
        assertEquals("true", readResp.headers["X-IsAdmin"])
    }
}
