package com.flightbooking

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
            if (session != null && session.message.isNotEmpty()) {
                call.sessions.set(session.copy(message = ""))
            }
        }
        get("/login") {
            call.respondTemplate("login.peb", mapOf("error" to ""))
        }
        post("/login") {
            val params = call.receiveParameters()
            val username = params["username"]
            val password = params["password"]
            // TODO
            if (password == password && username == username) {
                call.sessions.set(UserSession(username = username.orEmpty(), loggedIn = true))
                call.respondRedirect("/profile")
            } else {
                call.respondTemplate("login.peb", mapOf(
                    "loggedIn" to false,
                    "error" to "A credential was incorrect"
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

        post("/api/search-flights") {
            val params = call.receiveParameters()
            val departure = params["departure"]
            val destination = params["destination"]
            val departureDate = params["departureDate"]

            if (departure == null || destination == null || departureDate == null) {
                call.respondRedirect("/")
                return@post
            }

            val flights = FlightDAO.searchFlights(
                departure,
                destination,
                LocalDate.parse(departureDate)
            )

            call.respondTemplate("flights.peb", mapOf(
                "departure" to departure,
                "destination" to destination,
                "flights" to flights.map { f ->
                    mapOf(
                        "departureTime" to f.departureTime.toString(),
                        "arrivalTime" to f.arrivalTime.toString(),
                        "departureAirport" to f.departureAirportName,
                        "destinationAirport" to f.arrivalAirportName,
                        "departureTerminal" to f.departureTerminal,
                        "arrivalTerminal" to f.arrivalTerminal,
                        "duration" to "${f.durationMinutes} mins",
                        "stopType" to "Direct",
                        "price" to f.price,
                        "flightId" to f.flightId
                    )
                }
            ))
        }
        
        get("/api/search-airports") {
            val query = call.request.queryParameters["q"] ?: ""
            if (query.length < 2) {
                call.respond(emptyList<Any>())
                return@get
            }
            val airports = AirportDAO.searchAirport(query)
            call.respond(airports)
        }
        
    }
}