package com.flightbooking

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.serialization.Serializable

@Serializable
data class UserSession(
    val username: String = "",
    val loggedIn: Boolean = false,
    val message: String = "",
    val isAdmin: Boolean = false,
)

fun Application.configureSessions() {
    install(Sessions) {
        cookie<UserSession>("user_session") {
            cookie.path = "/"
            cookie.maxAgeInSeconds = 3600
        }
    }
}

fun getSessionData(call: ApplicationCall): Map<String, Any> {
    val session = call.sessions.get<UserSession>()
    return mapOf(
        "loggedIn" to (session?.loggedIn ?: false),
        "username" to (session?.username ?: ""),
        "message" to (session?.message ?: ""),
        "isAdmin" to (session?.isAdmin ?: false),
    )
}
