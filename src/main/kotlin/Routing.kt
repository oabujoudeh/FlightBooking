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
            val airports = listOf(
                Airport("LBA", "Leeds Bradford"),
                Airport("LHR", "London Heathrow")
            )
            val session = call.sessions.get<UserSession>()
            if (session != null && session.message.isNotEmpty()) {
                call.sessions.set(session.copy(message = ""))
            }
            call.respondTemplate("booking.peb", getSessionData(call) + mapOf(
                "airports" to airports
            ))
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
            val adults = params["adults"]?.toIntOrNull() ?: 1
            val children = params["children"]?.toIntOrNull() ?: 0

            val templateData = getSessionData(call).toMutableMap()

            if (departure == null || destination == null || departureDate == null) {
                templateData["results"] = emptyList<Any>()
                call.respondTemplate("booking.peb", templateData)
                return@post
            }

            val flights = FlightDAO.searchFlights(departure, destination, LocalDate.parse(departureDate))

            templateData["results"] = flights

            // data appears after refresh
            templateData["departure"] = departure
            templateData["departureLabel"] = AirportDAO.getLabel(departure)
            templateData["destination"] = destination
            templateData["destinationLabel"] = AirportDAO.getLabel(destination)
            templateData["departureDate"] = departureDate
            templateData["adults"] = adults
            templateData["children"] = children

            call.respondTemplate("booking.peb", templateData)
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