package com.flightbooking

import io.ktor.server.application.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import io.ktor.server.sessions.*
import io.ktor.server.response.*
import java.time.LocalDate

// helper that wraps getSessionData and guarantees non‑nullable values
private fun ApplicationCall.nonNullSessionData(): Map<String, Any> =
    getSessionData(this).mapValues { it.value ?: "" }

fun Application.configureRouting() {
    routing {

        static("/static") {
            resources("static")
        }

        get("/register"){
            call.respondTemplate("register.peb", mapOf("error" to ""))
        }

        post("/register"){
            val params = call.receiveParameters()

            val firstName = params["firstName"]?: ""
            val middleName = params["middleName"] ?:""
            val lastName = params["lastName"] ?:""
            val email = params["email"]?:""
            val password = params["password"] ?:""

            val tempUser = User(firstName = firstName, lastName = lastName, email = email, middleName = middleName, passwordHash =  "")

            val isSuccess = UserDAO.signUp(
                tempUser,
                firstName,
                middleName,
                lastName,
                email,
                password
            )

            if(isSuccess){
                call.respondRedirect("/login")
            }
            else{
                call.respondTemplate("register.peb", mapOf("error" to "Registration failer"))
            }
        }

        get("/") {
            val session = call.sessions.get<UserSession>()
            // use non‑nullable version of the map
            call.respondTemplate("booking.peb", call.nonNullSessionData())

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

            // TODO: replace with real authentication
            if (password == password && username == username) {
                call.sessions.set(
                    UserSession(
                        username = username.orEmpty(),
                        loggedIn = true
                    )
                )
                call.respondRedirect("/profile")
            } else {
                call.respondTemplate(
                    "login.peb",
                    mapOf(
                        "loggedIn" to false,
                        "error" to "A credential was incorrect"
                    )
                )
            }
        }

        get("/logout") {
            call.sessions.clear<UserSession>()
            call.respondRedirect("/")
        }

        get("/profile") {
            val session = call.sessions.get<UserSession>()

            if (session != null && session.loggedIn) {
                call.respondTemplate("profile.peb", call.nonNullSessionData())
            } else {
                call.respondRedirect("/login")
            }
        }

        post("/api/search-flights") {

            val params = call.receiveParameters()

            val departure = params["departure"]
            val destination = params["destination"]
            val departureDate = params["departureDate"]
            val returnDate = params["returnDate"]
            val tripType = params["tripType"] ?: "oneway"

            if (departure == null || destination == null || departureDate == null) {
                call.respondRedirect("/")
                return@post
            }

            val flights = FlightDAO.searchFlights(
                departure,
                destination,
                LocalDate.parse(departureDate)
            )

            val flightsList = flights.map { f ->
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

            val templateData = mapOf<String, Any>(
                "departure" to departure!!,
                "destination" to destination!!,
                "departureDate" to departureDate!!,
                "returnDate" to (returnDate ?:""),
                "tripType" to tripType,
                "flights" to flightsList
            )

            call.respondTemplate("flights.peb", templateData)
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