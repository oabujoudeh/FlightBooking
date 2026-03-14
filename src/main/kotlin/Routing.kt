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
            // get Referer, if empty then go to home page
            val referer = call.request.headers["Referer"] ?:"/"

            call.respondTemplate("login.peb", mapOf("error" to "", "redirect_url" to referer))
        }

        post("/login") {
            val params = call.receiveParameters()
            val email = params["username"]?:""
            val password = params["password"]?:""

            // if not get redirect_url, goto /profile
            var redirectUrl = params["redirect_url"]?:"/profile"

            if(redirectUrl.contains("forgot-password") || redirectUrl.contains("reset-password")){
                redirectUrl = "/profile"
            }

            // check 
            val isSuccess = UserDAO.loginUser(email, password)

            if (isSuccess) {
                call.sessions.set(
                    UserSession(
                        username = email,
                        loggedIn = true
                    )
                )
                // should decide whether redirect to profile or booking page
                call.respondRedirect(redirectUrl)
            } else {
                call.respondTemplate(
                    "login.peb",
                    mapOf(
                        "loggedIn" to false,
                        "error" to "Invalid email or password",
                        "redirect_url" to redirectUrl
                    )
                )
            }
        }

        get("/forgot-password"){
            call.respondTemplate("forgotPwd.peb", mapOf<String, Any>())
        }

        post("/forgot-password"){
            val params = call.receiveParameters()
            val email = params["email"] ?:""

            if(UserDAO.resetPassword(email)){
                call.respondTemplate("reset-password.peb", mapOf("email" to email))
            }else{
                call.respondTemplate("forgotPwd.peb", mapOf("error" to "Email not found"))
            }
        }

        post("/reset-password"){
            val params = call.receiveParameters()
            val email = params["email"]?:""
            val otc = params["otc"]?:""
            val newPassword = params["newPassword"]?:""

            if(UserDAO.confirmResetPassword(email, otc, newPassword)){
                call.respondRedirect("/login")
            }else{
                call.respondTemplate("reset-password.peb", mapOf(
                    "email" to email,
                    "error" to "Invalid code or password"
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
                call.respondTemplate("profile.peb", call.nonNullSessionData())
            } else {
                call.respondRedirect("/login")
            }
        }

        post("/search-flights") {

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

        get("/search-airports") {

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

        get("/my-bookings") {
            call.respondTemplate("my-bookings.peb", mapOf("loggedIn" to true))
    
        }

        get("/book/{flightId}/seats") {
            val flightId = call.parameters["flightId"]?.toIntOrNull()
            if (flightId == null) { call.respondRedirect("/"); return@get }

            val flight = FlightDAO.getFlightById(flightId)
            if (flight == null) { call.respondRedirect("/"); return@get }

            val seats = SeatDAO.getOrGenerateSeats(flightId, flight.aircraftType)
            val config = AircraftConfigs.getConfig(flight.aircraftType)

            val seatData = seats.map { s ->
                val row = s.seatNumber.filter { it.isDigit() }.toInt()
                mapOf(
                    "seatNumber" to s.seatNumber,
                    "class" to s.seatClass,
                    "isOccupied" to s.isOccupied,
                    "isExit" to config.decks.any { deck -> row in deck.exitRows },
                    "isBassinet" to config.decks.any { deck -> row in deck.bassinetRows }
                )
            }

            call.respondTemplate("seats.peb", mapOf(
                "flight" to mapOf(
                    "flightId" to flight.flightId,
                    "flightNumber" to flight.flightNumber,
                    "departureAirport" to flight.departureAirportName,
                    "arrivalAirport" to flight.arrivalAirportName,
                    "departureTime" to flight.departureTime.toString(),
                    "arrivalTime" to flight.arrivalTime.toString(),
                    "aircraftType" to flight.aircraftType
                ),
                "seats" to seatData,
                "loggedIn" to true
            ))
        }

    }
}