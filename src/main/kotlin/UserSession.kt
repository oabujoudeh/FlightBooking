package com.flightbooking

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.serialization.Serializable

/**
 * Holds all session state for the currently authenticated user.
 *
 * Stored as a signed cookie named `"user_session"` with a 1-hour lifetime.
 * Default values represent an unauthenticated, empty session.
 *
 * @property username the logged-in user's email address, or empty if not authenticated
 * @property loggedIn whether the user has an active authenticated session
 * @property message a one-time flash message to display on the next page load
 * @property isAdmin whether the authenticated user has admin privileges
 * @property pendingBooking a booking in progress that has not yet been confirmed, if any
 * @property rescheduleBookingId the ID of the booking currently being rescheduled, if any
 * @property pendingReschedule reschedule details awaiting confirmation, if any
 */
@Serializable
data class UserSession(
    val username: String = "",
    val loggedIn: Boolean = false,
    val message: String = "",
    val isAdmin: Boolean = false,
    val pendingBooking: PendingBooking? = null,
    val rescheduleBookingId: Int? = null,
    val pendingReschedule: PendingReschedule? = null
)

/**
 * Installs cookie-based session support for the application.
 *
 * Registers a [UserSession] cookie named `"user_session"` scoped to the
 * root path (`"/"`) with a maximum age of 3600 seconds (1 hour).
 */
fun Application.configureSessions() {
    install(Sessions) {
        cookie<UserSession>("user_session") {
            cookie.path = "/"
            cookie.maxAgeInSeconds = 3600
        }
    }
}

/**
 * Extracts the core session values from the current request as a template-friendly map.
 *
 * Returns safe defaults for all keys when no session exists, so templates
 * can reference session values without null checks.
 *
 * @param call the current [ApplicationCall] from which the session is read
 * @return a map with keys `"loggedIn"` (`Boolean`), `"username"` (`String`),
 *         `"message"` (`String`), and `"isAdmin"` (`Boolean`)
 */
fun getSessionData(call: ApplicationCall): Map<String, Any> {
    val session = call.sessions.get<UserSession>()
    return mapOf(
        "loggedIn" to (session?.loggedIn ?: false),
        "username" to (session?.username ?: ""),
        "message" to (session?.message ?: ""),
        "isAdmin" to (session?.isAdmin ?: false),
    )
}