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

        route("/register") {
            get {
                call.respondTemplate("register.peb", mapOf("error" to ""))
            }

            post {
                val params = call.receiveParameters()

                val firstName = params["firstName"] ?: ""
                val middleName = params["middleName"] ?: ""
                val lastName = params["lastName"] ?: ""
                val email = params["email"] ?: ""
                val password = params["password"] ?: ""

                val tempUser = User(
                    firstName = firstName,
                    lastName = lastName,
                    email = email,
                    middleName = middleName,
                    passwordHash = ""
                )

                val isSuccess = UserDAO.register(tempUser, firstName, middleName, lastName, email, password)

                if (isSuccess) {
                    call.respondRedirect("/login")
                } else {
                    call.respondTemplate("register.peb", mapOf("error" to "Registration failed"))
                }
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
            val adults = params["adults"] ?: "1"
            val children = params["children"] ?: "0"

            if (departure == null || destination == null || departureDate == null) {
                call.respondRedirect("/")
                return@post
            }

            val flights = FlightDAO.searchFlights(
                departure,
                destination,
                LocalDate.parse(departureDate)
            )

            // if no direct flights, search for connecting flights
            val connectingFlights = if (flights.isEmpty()) {
                FlightDAO.searchConnectingFlights(departure, destination, LocalDate.parse(departureDate))
            } else emptyList()

            val flightsList = flights.map { f ->
                Utils.flightToMap(f) + mapOf("stopType" to "Direct")
            }

            val connectingFlightsList = connectingFlights.map { cf ->
                mapOf(
                    "leg1DepartureTime" to cf.leg1.departureTime.toString(),
                    "leg1ArrivalTime" to cf.leg1.arrivalTime.toString(),
                    "leg1DepartureAirport" to cf.leg1.departureAirportName,
                    "leg1ArrivalAirport" to cf.leg1.arrivalAirportName,
                    "leg2DepartureTime" to cf.leg2.departureTime.toString(),
                    "leg2ArrivalTime" to cf.leg2.arrivalTime.toString(),
                    "leg2ArrivalAirport" to cf.leg2.arrivalAirportName,
                    "layoverMinutes" to cf.layoverMinutes,
                    "totalDuration" to Utils.formatDuration(cf.totalDurationMinutes),
                    "price" to cf.totalPrice,
                    "leg1FlightId" to cf.leg1.flightId,
                    "leg2FlightId" to cf.leg2.flightId
                )
            }

            var returnFlightsList = emptyList<Map<String, Any>>()

            if (tripType != "oneway") {
                val returnFlights = FlightDAO.searchFlights(destination, departure, LocalDate.parse(returnDate))
                returnFlightsList = returnFlights.map { f ->
                    Utils.flightToMap(f) + mapOf("stopType" to "Direct")
                }
            }

            val templateData = mapOf<String, Any>(
                "departure" to departure!!,
                "destination" to destination!!,
                "departureDate" to departureDate!!,
                "returnDate" to (returnDate ?: ""),
                "tripType" to tripType,
                "adults" to adults,
                "children" to children,
                "flights" to flightsList,
                "returnFlights" to returnFlightsList,
                "connectingFlights" to connectingFlightsList
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


        post("/book-flights") {
            val params = call.receiveParameters()
            val outboundFlightId = params["outboundFlight"]?.toIntOrNull()
            val returnFlightId = params["returnFlight"]?.toIntOrNull()
            val adults = params["adults"]?.toIntOrNull() ?: 1
            val children = params["children"]?.toIntOrNull() ?: 0

            if (outboundFlightId == null) {
                call.respondRedirect("/")
                return@post
            }

            val outboundFlight = FlightDAO.getFlightOverview(outboundFlightId)

            if (outboundFlight == null) {
                call.respondRedirect("/")
                return@post
            }

            var returnFlight: Flight? = null
            if (returnFlightId != null) {
                returnFlight = FlightDAO.getFlightOverview(returnFlightId)
            }

            val templateData = mutableMapOf<String, Any>(
                "outboundFlight" to Utils.flightToMap(outboundFlight),
                "adults" to adults,
                "children" to children
            )

            if (returnFlight != null) {
                templateData["returnFlight"] = Utils.flightToMap(returnFlight)
            }

            call.respondTemplate("confirmBooking.peb", templateData)
        }
    }
}