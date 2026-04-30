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
* Stores the current user's session data.
*/
@Serializable
data class UserSession(
    val username: String = "",
    val loggedIn: Boolean = false,
    val message: String = "",
    val isAdmin: Boolean = false,
)

/**
* Sets up session support for the app.
*
* It stores user session data in a cookie.
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
* Gets the main session values for the current user.
*
* @param call the current application call
* @return a map with login and session details
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
