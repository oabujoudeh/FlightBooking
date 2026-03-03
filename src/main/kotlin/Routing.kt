package com.example.com

import io.ktor.server.application.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import com.password4j.Password
import io.ktor.server.sessions.*
import io.ktor.server.response.*



fun Application.configureRouting() {
    routing {
        static("/static") {
            resources("static")
        }

        get("/") {
            val session = call.sessions.get<UserSession>()
            call.respondTemplate("home.peb", getSessionData(call))
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

        post("/search-flights") {

            val session = call.sessions.get<UserSession>()

            val params = call.receiveParameters()
            val departDate = params["departDate"]
            val returnDate = params["returnDate"]
            val adults = params["adults"]
            val children = params["children"]
            val cabinClass = params["cabinClass"]

            // process info here and search database for possible flights
            data class Flight(
                val id: String,
                val airline: String,
                val origin: String,
                val destination: String,
                val departureTime: String,
                val arrivalTime: String,
                val duration: String,
                val stops: String,
                val price: String
            )

            val fakeFlights = listOf(
                Flight("1", "British Airways", "LBA", "LHR", "06:00", "07:05", "1h 05m", "Direct", "89.99"),
                Flight("2", "EasyJet", "LBA", "LHR", "11:30", "13:10", "1h 40m", "1 stop", "54.99"),
                Flight("3", "Ryanair", "LBA", "LHR", "17:45", "19:00", "1h 15m", "Direct", "42.99")
            )

            call.respondTemplate("home.peb", getSessionData(call) + mapOf(
                "results" to fakeFlights
            ))

        }
    }
}
