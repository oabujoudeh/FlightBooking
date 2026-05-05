package com.flightbooking

import io.ktor.server.application.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import io.ktor.server.sessions.*
import io.ktor.server.response.*
import io.ktor.http.*
import java.time.LocalDate


/**
* Gets session data and replaces any null values with empty strings.
*/
private fun ApplicationCall.nonNullSessionData(): Map<String, Any> =
    getSessionData(this).mapValues { it.value ?: "" }


/**
 * Builds the deckData map for a given flight, used for seat map rendering.
 */
private fun buildDeckData(flightId: Int, aircraftType: String): List<Map<String, Any>> {
    val config = AircraftConfigs.getConfig(aircraftType)
    val seats = SeatDAO.getOrGenerateSeats(flightId, aircraftType)
    val seatMap = seats.associateBy { it.seatNumber }

    return config.decks.map { deck ->
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
}


/**
* Sets up the main routes for the app.
*
* This includes pages for login, register, flights, bookings, profile, admin charts, and payment.
*/
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

            if (session != null && session.isAdmin) {
                val recentBookings = AdminDAO.getRecentBookings()
                val recentCancellations = AdminDAO.getRecentCancellations()
                val upcomingFlights = AdminDAO.getUpcomingFlights()
                val totalUsers = AdminDAO.getTotalUsers()
                call.respondTemplate("adminHome.peb", call.nonNullSessionData() + mapOf(
                    "recentBookings" to recentBookings,
                    "recentCancellations" to recentCancellations,
                    "upcomingFlights" to upcomingFlights,
                    "totalUsers" to totalUsers
                ))
            } else {
                call.respondTemplate("searchFlight.peb", call.nonNullSessionData())
            }

            if (session != null && session.message.isNotEmpty()) {
                call.sessions.set(session.copy(message = ""))
            }
        }

        get("/login") {
            val referer = call.request.headers["Referer"] ?: "/"
            call.respondTemplate("login.peb", mapOf("error" to "", "redirect_url" to referer))
        }

        post("/login") {
            val params = call.receiveParameters()
            val email = params["username"] ?: ""
            val password = params["password"] ?: ""

            var redirectUrl = params["redirect_url"] ?: "/profile"

            if (redirectUrl.contains("forgot-password") || redirectUrl.contains("reset-password") || redirectUrl.contains("register")) {
                redirectUrl = "/profile"
            }

            val loginResult = UserDAO.loginUser(email, password)

            if (loginResult.success) {
                call.sessions.set(
                    UserSession(
                        username = email,
                        loggedIn = true,
                        isAdmin = loginResult.isAdmin
                    )
                )
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

        get("/forgot-password") {
            call.respondTemplate("forgotPwd.peb", mapOf<String, Any>())
        }

        post("/forgot-password") {
            val params = call.receiveParameters()
            val email = params["email"] ?: ""

            if (UserDAO.resetPassword(email)) {
                call.respondTemplate("reset-password.peb", mapOf("email" to email))
            } else {
                call.respondTemplate("forgotPwd.peb", mapOf("error" to "Email not found"))
            }
        }

        post("/reset-password") {
            val params = call.receiveParameters()
            val email = params["email"] ?: ""
            val otc = params["otc"] ?: ""
            val newPassword = params["newPassword"] ?: ""

            if (UserDAO.confirmResetPassword(email, otc, newPassword)) {
                call.respondRedirect("/login")
            } else {
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

        post("/cancel-booking") {
            val session = call.sessions.get<UserSession>()
            if (session == null || !session.loggedIn) {
                call.respondRedirect("/login")
                return@post
            }

            val params = call.receiveParameters()
            val bookingId = params["bookingId"]?.toIntOrNull()

            if (bookingId == null) {
                call.respondRedirect("/profile")
                return@post
            }

            val userID = UserDAO.getUserID(session.username)
            UserDAO.cancelBooking(bookingId, userID)

            call.respondRedirect("/profile")
        }

        post("/update-booking") {
            val session = call.sessions.get<UserSession>()
            if (session == null || !session.loggedIn) {
                call.respondRedirect("/login")
                return@post
            }

            val params = call.receiveParameters()
            val bookingId = params["bookingId"]?.toIntOrNull()
            val passengerCount = params["passengerCount"]?.toIntOrNull() ?: 0

            if (bookingId == null) {
                call.respondRedirect("/profile")
                return@post
            }

            val passengers = mutableListOf<Map<String, String>>()
            for (i in 0 until passengerCount) {
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

        get("/edit-booking") {
            val session = call.sessions.get<UserSession>()
            if (session == null || !session.loggedIn) {
                call.respondRedirect("/login")
                return@get
            }

            val bookingId = call.request.queryParameters["id"]?.toIntOrNull()
            if (bookingId == null) {
                call.respondRedirect("/profile")
                return@get
            }

            val userID = UserDAO.getUserID(session.username)
            val booking = UserDAO.getBookingById(bookingId, userID)

            if (booking == null) {
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

            val depLocalDate = LocalDate.parse(departureDate)

            val combinedFlights = FlightDAO.getAvailableFlights(departure, destination, depLocalDate)

            var returnFlightsList = emptyList<FlightDisplayDTO>()
            if (tripType != "oneway" && returnDate != null) {
                returnFlightsList = FlightDAO.getAvailableFlights(destination, departure, LocalDate.parse(returnDate))
            }

            val templateData = mapOf<String, Any>(
                "departure" to departure,
                "destination" to destination,
                "departureDate" to departureDate,
                "returnDate" to (returnDate ?: ""),
                "tripType" to tripType,
                "adults" to adults,
                "children" to children,
                "combinedFlights" to combinedFlights,
                "returnFlights" to returnFlightsList
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

            // Extract the raw selection strings (e.g., "123_economy" or "45_67_business")
            val outboundRaw = params["outboundFlight"] ?: ""
            val returnRaw = params["returnFlight"] ?: ""

            val adults = params["adults"]?.toIntOrNull() ?: 1
            val children = params["children"]?.toIntOrNull() ?: 0

            if (outboundRaw.isEmpty()) {
                call.respondRedirect("/")
                return@post
            }

            // Parse cabin and flight IDs from outbound selection
            // Format: "flightId_cabin" for direct, "flightId1_flightId2_cabin" for connecting
            val outboundParts = outboundRaw.split("_")
            val outboundCabin = outboundParts.last()
            val outboundFlightIds = outboundParts.dropLast(1).mapNotNull { it.toIntOrNull() }

            if (outboundFlightIds.isEmpty()) {
                call.respondRedirect("/")
                return@post
            }

            // Fetch outbound flight(s) for summary display
            val primaryOutboundFlight = FlightDAO.getFlightOverview(outboundFlightIds.first())
            if (primaryOutboundFlight == null) {
                call.respondRedirect("/")
                return@post
            }

            // Parse return flight if present
            var returnCabin: String? = null
            var returnFlightIds: List<Int> = emptyList()
            var primaryReturnFlight: Flight? = null

            if (returnRaw.isNotEmpty()) {
                val returnParts = returnRaw.split("_")
                returnCabin = returnParts.last()
                returnFlightIds = returnParts.dropLast(1).mapNotNull { it.toIntOrNull() }
                if (returnFlightIds.isNotEmpty()) {
                    primaryReturnFlight = FlightDAO.getFlightOverview(returnFlightIds.first())
                }
            }

            // Build flightSteps — one step per leg per direction
            // Each step contains the deckData for that specific flight leg
            val flightSteps = mutableListOf<Map<String, Any>>()

            if (outboundFlightIds.size == 2) {
                // Connecting outbound
                val leg2Flight = FlightDAO.getFlightOverview(outboundFlightIds[1])
                flightSteps.add(mapOf(
                    "stepIndex" to 1,
                    "label" to "Outbound – Leg 1",
                    "flightId" to outboundFlightIds[0],
                    "cabin" to outboundCabin,
                    "deckData" to buildDeckData(outboundFlightIds[0], primaryOutboundFlight.aircraftType)
                ))
                if (leg2Flight != null) {
                    flightSteps.add(mapOf(
                        "stepIndex" to 2,
                        "label" to "Outbound – Leg 2",
                        "flightId" to outboundFlightIds[1],
                        "cabin" to outboundCabin,
                        "deckData" to buildDeckData(outboundFlightIds[1], leg2Flight.aircraftType)
                    ))
                }
            } else {
                // Direct outbound
                flightSteps.add(mapOf(
                    "stepIndex" to 1,
                    "label" to "Outbound",
                    "flightId" to outboundFlightIds[0],
                    "cabin" to outboundCabin,
                    "deckData" to buildDeckData(outboundFlightIds[0], primaryOutboundFlight.aircraftType)
                ))
            }

            if (returnFlightIds.isNotEmpty() && primaryReturnFlight != null) {
                val baseStep = flightSteps.size + 1
                if (returnFlightIds.size == 2) {
                    // Connecting return
                    val retLeg2Flight = FlightDAO.getFlightOverview(returnFlightIds[1])
                    flightSteps.add(mapOf(
                        "stepIndex" to baseStep,
                        "label" to "Return – Leg 1",
                        "flightId" to returnFlightIds[0],
                        "cabin" to (returnCabin ?: "economy"),
                        "deckData" to buildDeckData(returnFlightIds[0], primaryReturnFlight.aircraftType)
                    ))
                    if (retLeg2Flight != null) {
                        flightSteps.add(mapOf(
                            "stepIndex" to baseStep + 1,
                            "label" to "Return – Leg 2",
                            "flightId" to returnFlightIds[1],
                            "cabin" to (returnCabin ?: "economy"),
                            "deckData" to buildDeckData(returnFlightIds[1], retLeg2Flight.aircraftType)
                        ))
                    }
                } else {
                    // Direct return
                    flightSteps.add(mapOf(
                        "stepIndex" to baseStep,
                        "label" to "Return",
                        "flightId" to returnFlightIds[0],
                        "cabin" to (returnCabin ?: "economy"),
                        "deckData" to buildDeckData(returnFlightIds[0], primaryReturnFlight.aircraftType)
                    ))
                }
            }

            // Prepare template data
            val templateData = mutableMapOf<String, Any>(
                "outboundFlight" to Utils.flightToMap(primaryOutboundFlight),
                "outboundCabin" to outboundCabin,
                "adults" to adults,
                "children" to children,
                "flightSteps" to flightSteps,
                "totalSteps" to flightSteps.size,
                // Pass raw IDs as hidden fields for confirm-booking
                "outboundFlightIdsRaw" to outboundFlightIds.joinToString(","),
                "returnFlightIdsRaw" to returnFlightIds.joinToString(","),
                "returnCabinVal" to (returnCabin ?: "")
            )

            // Pass leg 2 flight info and layover duration for connecting flights (for summary display)
            if (outboundFlightIds.size == 2) {
                val outboundLeg2 = FlightDAO.getFlightOverview(outboundFlightIds[1])
                if (outboundLeg2 != null) {
                    templateData["outboundLeg2Flight"] = Utils.flightToMap(outboundLeg2)
                    val leg2DayOffset = (outboundLeg2.departureDate.toEpochDay() - primaryOutboundFlight.departureDate.toEpochDay()).toInt()
                    templateData["outboundLeg2DayOffset"] = leg2DayOffset
                    templateData["outboundLeg2ArrivalDayOffset"] = leg2DayOffset + outboundLeg2.arrivalDayOffset
                    val leg1ArrivalDate = primaryOutboundFlight.departureDate.plusDays(primaryOutboundFlight.arrivalDayOffset.toLong())
                    val arr = java.time.ZonedDateTime.of(leg1ArrivalDate, primaryOutboundFlight.arrivalTime, java.time.ZoneId.of("UTC"))
                    val dep = java.time.ZonedDateTime.of(outboundLeg2.departureDate, outboundLeg2.departureTime, java.time.ZoneId.of("UTC"))
                    val layoverMins = java.time.Duration.between(arr, dep).toMinutes()
                    templateData["outboundLayoverDisplay"] = Utils.formatDuration(layoverMins.toInt())
                }
            }

            if (primaryReturnFlight != null) {
                templateData["returnFlight"] = Utils.flightToMap(primaryReturnFlight)
                templateData["returnCabin"] = returnCabin ?: "economy"

                if (returnFlightIds.size == 2) {
                    val returnLeg2 = FlightDAO.getFlightOverview(returnFlightIds[1])
                    if (returnLeg2 != null) {
                        templateData["returnLeg2Flight"] = Utils.flightToMap(returnLeg2)
                        val leg2DayOffset = (returnLeg2.departureDate.toEpochDay() - primaryReturnFlight.departureDate.toEpochDay()).toInt()
                        templateData["returnLeg2DayOffset"] = leg2DayOffset
                        templateData["returnLeg2ArrivalDayOffset"] = leg2DayOffset + returnLeg2.arrivalDayOffset
                        val leg1ArrivalDate = primaryReturnFlight.departureDate.plusDays(primaryReturnFlight.arrivalDayOffset.toLong())
                        val arr = java.time.ZonedDateTime.of(leg1ArrivalDate, primaryReturnFlight.arrivalTime, java.time.ZoneId.of("UTC"))
                        val dep = java.time.ZonedDateTime.of(returnLeg2.departureDate, returnLeg2.departureTime, java.time.ZoneId.of("UTC"))
                        val layoverMins = java.time.Duration.between(arr, dep).toMinutes()
                        templateData["returnLayoverDisplay"] = Utils.formatDuration(layoverMins.toInt())
                    }
                }
            }

            call.respondTemplate("confirmBooking.peb", templateData)
        }

        get("/admin/chart/booking-status") {
            val session = call.sessions.get<UserSession>()
            if (session == null || !session.isAdmin) {
                call.respondRedirect("/")
                return@get
            }

            val data = AdminDAO.getBookingStatusCounts()
            val imageBytes = ChartService.generateBookingStatusChart(data)
            call.respondBytes(imageBytes, ContentType.Image.PNG)
        }

        get("/admin/chart/busiest-routes") {
            val session = call.sessions.get<UserSession>()
            if (session == null || !session.isAdmin) {
                call.respondRedirect("/")
                return@get
            }

            val data = AdminDAO.getBusiestRoutes()
            val imageBytes = ChartService.generateBusiestRoutesChart(data)
            call.respondBytes(imageBytes, ContentType.Image.PNG)
        }

        get("/admin/chart/bookings-over-time") {
            val session = call.sessions.get<UserSession>()
            if (session == null || !session.isAdmin) {
                call.respondRedirect("/")
                return@get
            }

            val data = AdminDAO.getAllBookingsGroupedByDate()
            val imageBytes = ChartService.generateBookingsOverTimeChart(data)
            call.respondBytes(imageBytes, ContentType.Image.PNG)
        }

        post("/confirm-booking") {
            val session = call.sessions.get<UserSession>()
            if (session == null || !session.loggedIn) {
                call.respondRedirect("/login")
                return@post
            }

            val params = call.receiveParameters()

            val outboundCabin = params["outboundCabin"] ?: "economy"
            val returnCabin = params["returnCabin"] ?: "economy"
            val adults = params["adults"]?.toIntOrNull() ?: 1
            val children = params["children"]?.toIntOrNull() ?: 0

            // Parse all flight IDs (comma-separated)
            val outboundFlightIds = params["outboundFlightIdsRaw"]
                ?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
            val returnFlightIds = params["returnFlightIdsRaw"]
                ?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()

            if (outboundFlightIds.isEmpty()) {
                call.respondRedirect("/")
                return@post
            }

            // Helper to pick price by cabin
            fun getPriceByCabin(flight: Flight, cabin: String): Double {
                return when (cabin.lowercase()) {
                    "business" -> flight.priceBusiness
                    "first" -> flight.priceFirst
                    else -> flight.priceEconomy
                } ?: 0.0
            }

            // Calculate total price across all legs
            var totalPrice = 0.0
            val allFlightIds = mutableListOf<Int>()

            for (id in outboundFlightIds) {
                val flight = FlightDAO.getFlightOverview(id)
                if (flight != null) {
                    totalPrice += getPriceByCabin(flight, outboundCabin) * (adults + children)
                    allFlightIds.add(id)
                }
            }

            for (id in returnFlightIds) {
                val flight = FlightDAO.getFlightOverview(id)
                if (flight != null) {
                    totalPrice += getPriceByCabin(flight, returnCabin) * (adults + children)
                    allFlightIds.add(id)
                }
            }

            // Collect passenger info
            // Seats are stored per step: adult_0_seat_step1, adult_0_seat_step2, etc.
            // For the booking record we store seats as comma-separated per passenger across all steps
            val totalSteps = outboundFlightIds.size + returnFlightIds.size

            val passengers = mutableListOf<Map<String, String>>()

            for (i in 0 until adults) {
                val seats = (1..totalSteps).map { step ->
                    params["adult_${i}_seat_step${step}"] ?: ""
                }.filter { it.isNotEmpty() }.joinToString(",")

                passengers.add(mapOf(
                    "fullName" to (params["adult_${i}_fullName"] ?: ""),
                    "idNumber" to (params["adult_${i}_ID"] ?: ""),
                    "type" to "adult",
                    "seat" to seats,
                    "cabin" to outboundCabin
                ))
            }

            for (i in 0 until children) {
                val seats = (1..totalSteps).map { step ->
                    params["child_${i}_seat_step${step}"] ?: ""
                }.filter { it.isNotEmpty() }.joinToString(",")

                passengers.add(mapOf(
                    "fullName" to (params["child_${i}_fullName"] ?: ""),
                    "idNumber" to (params["child_${i}_ID"] ?: ""),
                    "type" to "child",
                    "seat" to seats,
                    "cabin" to outboundCabin
                ))
            }

            val userID = UserDAO.getUserID(session.username)
            val isSuccess = UserDAO.createBooking(userID, session.username, allFlightIds, totalPrice, passengers)

            if (isSuccess) {
                call.respondRedirect("/profile")
            } else {
                call.respondRedirect("/")
            }
        }

        get("/payment") {
            call.respond(PebbleContent("payment.peb", mapOf()))
        }

        post("/payment") {
            call.respondRedirect("/payment-success")
        }

        get("/payment-success") {
            call.respond(PebbleContent("payment-success.peb", mapOf()))
        }
    }
}