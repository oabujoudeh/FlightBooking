package com.flightbooking

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
               

    route("/api") {
        get("/flights/search") {

            val departure = call.request.queryParameters["departure"]
            val arrival = call.request.queryParameters["arrival"]
            val date = call.request.queryParameters["date"]

            if (departure == null || arrival == null || date == null) {
                call.respond(
                    mapOf(
                        "success" to false,
                        "error" to "Please provide departure city, arrival city, and date"
                    )
                )
                return@get
            }

            val flights = Database.searchFlights(
                departure,
                arrival,
                LocalDate.parse(date)
            )

            call.respond(
                mapOf(
                    "success" to true,
                    "count" to flights.size,
                    "flights" to flights.map { flight ->
                        mapOf(
                            "flightId" to flight.flightId,
                            "flightNumber" to flight.flightNumber,
                            "departureCity" to flight.departureCity,
                            "arrivalCity" to flight.arrivalCity,
                            "departureTerminal" to flight.departureTerminal,
                            "arrivalTerminal" to flight.arrivalTerminal,
                            "departureDate" to flight.departureDate.toString(),
                            "departureTime" to flight.departureTime.toString(),
                            "arrivalTime" to flight.arrivalTime.toString(),
                            "price" to flight.price
                        )
                    }
                )
            )
        }
    }

}
