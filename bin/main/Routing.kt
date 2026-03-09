package com.example.com

import io.ktor.server.application.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import com.password4j.Password
import io.ktor.server.sessions.*
import io.ktor.server.response.*
import java.time.LocalDate



fun Application.configureRouting() {
    routing {
        static("/static") {
            resources("static")
        }

        get("/") {
            val session = call.sessions.get<UserSession>()
            call.respondTemplate("booking.peb", getSessionData(call))
            // Clear message after displaying
            if (session != null && session.message.isNotEmpty()) {
                call.sessions.set(session.copy(message = ""))
            }
        }

        get("/login") {
            call.respondTemplate("login.peb", mapOf(
                "error" to ""
            ))
        }

        post("/login") {
            val params = call.receiveParameters()
            val username = params["username"]
            val password = params["password"]

            //set up login auth and checks for login

            if (password == password && username == username) {
                call.sessions.set(UserSession(username = username.orEmpty(), loggedIn = true))
                call.respondRedirect("/profile")
            }else {
                call.respondTemplate("login.peb", mapOf(
                        "loggedIn" to false,
                        "error" to "A credentail was incorrect"
                    ))
            }
        }

        get("/logout") {
            call.sessions.clear<UserSession>()
            call.respondRedirect("/")
        }

        get("/profile") {
            val session = call.sessions.get<UserSession>()
            if (session != null && session.loggedIn) {
                call.respondTemplate("profile.peb", getSessionData(call))
            } else {
                call.respondRedirect("/login")
            }
        }

    

        get("{...}") {
            call.respondRedirect("/")
        }
    }
}
