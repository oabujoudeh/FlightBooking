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

            if(redirectUrl.contains("forgot-password") || redirectUrl.contains("reset-password") || redirectUrl.contains("register")){
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
                val username = session.username
                
                val userID = UserDAO.getUserID(username)

                val bookings = UserDAO.getBookings(userID)

                call.respondTemplate("profile.peb", call.nonNullSessionData() + mapOf("bookings" to bookings))
            } else {
                call.respondRedirect("/login")
            }
        }

        post("/cancel-booking"){
            val session = call.sessions.get<UserSession>()
            if(session == null || !session.loggedIn){
                call.respondRedirect("/login")
                return@post
            }

            val params = call.receiveParameters()
            val bookingId = params["bookingId"]?.toIntOrNull()

            if(bookingId == null){
                call.respondRedirect("/profile")
                return@post
            }

            val userID = UserDAO.getUserID(session.username)
            UserDAO.cancelBooking(bookingId, userID)

            call.respondRedirect("/profile")
        }

        post("/update-booking"){
            val session = call.sessions.get<UserSession>()
            if(session == null || !session.loggedIn){
                call.respondRedirect("/login")
                return@post
            }

            val params = call.receiveParameters()
            val bookingId = params["bookingId"]?.toIntOrNull()
            val passengerCount = params["passengerCount"]?.toIntOrNull() ?: 0

            if(bookingId == null){
                call.respondRedirect("/profile")
                return@post
            }

            val passengers = mutableListOf<Map<String, String>>()
            for(i in 0 until passengerCount){
                passengers.add(mapOf(
                    "fullName" to (params["passenger_${i}_fullName"] ?: ""),
                    "idNumber" to (params["passenger_${i}_ID"] ?: ""),
                    "type" to (params["passenger_${i}_type"] ?: "adult"),
                    "seat" to (params["passenger_${i}_seat"] ?: "")
                ))
            }

            val userID = UserDAO.getUserID(session.username)
            UserDAO.updateBookingPassengers(bookingId, userID, passengers)

            call.respondRedirect("/profile")
        }

        get("/edit-booking"){
            val session = call.sessions.get<UserSession>()
            if(session == null || !session.loggedIn){
                call.respondRedirect("/login")
                return@get
            }

            val bookingId = call.request.queryParameters["id"]?.toIntOrNull()
            if(bookingId == null){
                call.respondRedirect("/profile")
                return@get
            }

            val userID = UserDAO.getUserID(session.username)
            val booking = UserDAO.getBookingById(bookingId, userID)

            if(booking == null){
                call.respondRedirect("/profile")
                return@get
            }

            call.respondTemplate("editBooking.peb", call.nonNullSessionData() + mapOf("booking" to booking))
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

            val connectingFlightsList = connectingFlights.map { Utils.connectingFlightToMap(it) }

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

            // seat selection!!
            val config = AircraftConfigs.getConfig(outboundFlight.aircraftType)
            val seats = SeatDAO.getOrGenerateSeats(outboundFlightId, outboundFlight.aircraftType)
            val seatMap = seats.associateBy { it.seatNumber }

            val deckData = config.decks.map { deck ->
                val prefix = if (config.decks.size > 1)
                    mapOf("Main Deck" to "M", "Upper Deck" to "U")[deck.deckName] ?: "" else ""

                mapOf(
                    "deckName" to deck.deckName,
                    "cabins" to deck.cabins.map { cabin ->
                        mapOf(
                            "seatClass" to cabin.seatClass,
                            "rows" to cabin.rows.map { row ->
                                mapOf(
                                    "rowNumber" to row,
                                    "isExit" to (row in deck.exitRows),
                                    "isBassinet" to (row in deck.bassinetRows),
                                    "groups" to cabin.layout.map { group ->
                                        group.map { col ->
                                            val seatNumber = "$prefix$row$col"
                                            val seat = seatMap[seatNumber]
                                            mapOf(
                                                "seatNumber" to seatNumber,
                                                "col" to col,
                                                "isOccupied" to (seat?.isOccupied ?: false),
                                                "seatClass" to cabin.seatClass
                                            )
                                        }
                                    }
                                )
                            }
                        )
                    }
                )
            }

            val templateData = mutableMapOf<String, Any>(
                "outboundFlight" to Utils.flightToMap(outboundFlight),
                "deckData" to deckData,
                "adults" to adults,
                "children" to children
            )

            if (returnFlight != null) {
                templateData["returnFlight"] = Utils.flightToMap(returnFlight)
            }

            call.respondTemplate("confirmBooking.peb", templateData)
        }

        post("/confirm-booking"){
            val session = call.sessions.get<UserSession>()
            if(session == null || !session.loggedIn){
                call.respondRedirect("/login")
                return@post
            }

            val params = call.receiveParameters()
            val outboundFlightId = params["outboundFlightId"]?.toIntOrNull()
            val returnFlightId = params["returnFlightId"]?.toIntOrNull()
            val adults = params["adults"]?.toIntOrNull() ?: 1
            val children = params["children"]?.toIntOrNull() ?: 0

            if(outboundFlightId == null){
                call.respondRedirect("/")
                return@post
            }

            // get the flight prices to calculate total
            val outboundFlight = FlightDAO.getFlightOverview(outboundFlightId)
            if(outboundFlight == null){
                call.respondRedirect("/")
                return@post
            }

            var totalPrice = outboundFlight.price * (adults + children)
            val flightIds = mutableListOf(outboundFlightId)

            if(returnFlightId != null){
                val returnFlight = FlightDAO.getFlightOverview(returnFlightId)
                if(returnFlight != null){
                    totalPrice += returnFlight.price * (adults + children)
                    flightIds.add(returnFlightId)
                }
            }

            // collect passenger info from form
            val passengers = mutableListOf<Map<String, String>>()

            for(i in 0 until adults){
                passengers.add(mapOf(
                    "fullName" to (params["adult_${i}_fullName"] ?: ""),
                    "idNumber" to (params["adult_${i}_ID"] ?: ""),
                    "type" to "adult",
                    "seat" to (params["adult_${i}_seat"] ?: "")
                ))
            }

            for(i in 0 until children){
                passengers.add(mapOf(
                    "fullName" to (params["child_${i}_fullName"] ?: ""),
                    "idNumber" to (params["child_${i}_ID"] ?: ""),
                    "type" to "child",
                    "seat" to (params["child_${i}_seat"] ?: "")
                ))
            }

            val userID = UserDAO.getUserID(session.username)
            val isSuccess = UserDAO.createBooking(userID, session.username, flightIds, totalPrice, passengers)

            if(isSuccess){
                call.respondRedirect("/profile")
            } else {
                call.respondRedirect("/")
            }
        }

    }
}