package com.flightbooking

import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.http.*
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.http.content.staticResources
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.pebble.respondTemplate
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
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
 * Returns the price for a given flight and cabin class.
 */
private fun getPriceByCabin(flight: Flight, cabin: String): Double {
    return when (cabin.lowercase()) {
        "business" -> flight.priceBusiness
        "first"    -> flight.priceFirst
        else       -> flight.priceEconomy
    } ?: 0.0
}


/**
* Sets up the main routes for the app.
*
* This includes pages for login, register, flights, bookings, profile, admin charts, and payment.
*/
fun Application.configureRouting() {
    routing {
        staticResources("/static", "static")

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

                val tempUser =
                    User(
                        firstName = firstName,
                        lastName = lastName,
                        email = email,
                        middleName = middleName,
                        passwordHash = "",
                    )

                val isSuccess = UserDAO.register(tempUser, firstName, middleName, lastName, email, password)

                if (isSuccess) {
                    call.respondRedirect("/login")
                } else {
                    call.respondTemplate("register.peb", mapOf("error" to "Registration failed"))
                }
            }
        }

        post("/admin/update-flight-status") {
            val session = call.sessions.get<UserSession>()
            if (session == null || !session.isAdmin) {
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }
            val params = call.receiveParameters()
            val flightId = params["flightId"]?.toIntOrNull()
            val newStatus = params["newStatus"]
    
            if (flightId == null || newStatus.isNullOrEmpty()) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
    
            val success = AdminDAO.updateFlightStatus(flightId, newStatus)
            if (success) {
        
                val flightDate = params["flightDate"] ?: ""
                val flightNumber = params["flightNumber"] ?: ""
                call.respondRedirect("/?flightDate=$flightDate&flightNumber=$flightNumber")
            } else {
                call.respond(HttpStatusCode.InternalServerError, "Failed to update status")
            }
        }


        get("/") {
            val session = call.sessions.get<UserSession>()

            if (session != null && session.isAdmin) {

                val filterDate = call.request.queryParameters["filterDate"]
                val filterUsername = call.request.queryParameters["filterUsername"]
                val filterStatus = call.request.queryParameters["filterStatus"]

                val trackedResults = AdminDAO.trackReservations(
                    filterDate = if (filterDate.isNullOrEmpty()) null else filterDate,
                    filterUsername = if (filterUsername.isNullOrEmpty()) null else filterUsername,
                    filterStatus = if (filterStatus.isNullOrEmpty()) null else filterStatus
                )

                val recentBookings = AdminDAO.getRecentBookings()
                val recentCancellations = AdminDAO.getRecentCancellations()
                val upcomingFlights = AdminDAO.getUpcomingFlights()
                val totalUsers = AdminDAO.getTotalUsers()

                val bookingsPerFlightDate = call.request.queryParameters["bpfDate"]
                val routesStartDate = call.request.queryParameters["routesStart"]
                val routesEndDate = call.request.queryParameters["routesEnd"]

                val bookingsPerFlight = AdminDAO.getBookingsPerFlight(
                    filterDate = if (bookingsPerFlightDate.isNullOrEmpty()) null else bookingsPerFlightDate
                )
                val popularRoutes = AdminDAO.getBusiestRoutes(
                    startDate = if (routesStartDate.isNullOrEmpty()) null else routesStartDate,
                    endDate = if (routesEndDate.isNullOrEmpty()) null else routesEndDate
                )
                val peakBookingTimes = AdminDAO.getAllBookingsGroupedByDate()

                val flightDate = call.request.queryParameters["flightDate"]
                val flightNumber = call.request.queryParameters["flightNumber"]

                val trackedFlights = AdminDAO.trackFlights(
                    filterDate = if (flightDate.isNullOrEmpty()) null else flightDate,
                    filterNumber = if (flightNumber.isNullOrEmpty()) null else flightNumber
                )

                call.respondTemplate(
                    "adminHome.peb",
                    call.nonNullSessionData() +
                        mapOf(
                            "trackedReservations" to trackedResults,
                            "trackedFlights" to trackedFlights,
                            "lastFilterDate" to (filterDate ?: ""),
                            "lastFilterUsername" to (filterUsername ?: ""),
                            "lastFilterStatus" to (filterStatus ?: ""),
                            //original - joe 
                            "recentBookings" to recentBookings,
                            "recentCancellations" to recentCancellations,
                            "upcomingFlights" to upcomingFlights,
                            "totalUsers" to totalUsers,
                            "bookingsPerFlight" to bookingsPerFlight,
                            "popularRoutes" to popularRoutes,
                            "peakBookingTimes" to peakBookingTimes,
                            "lastBpfDate" to (bookingsPerFlightDate ?: ""),
                            "lastRoutesStart" to (routesStartDate ?: ""),
                            "lastRoutesEnd" to (routesEndDate ?: "")
                        )
                )
            } else {
                val dep = call.request.queryParameters["departure"] ?: ""
                val dest = call.request.queryParameters["destination"] ?: ""
                val depLabel = call.request.queryParameters["departureLabel"] ?: ""
                val destLabel = call.request.queryParameters["destinationLabel"] ?: ""
                
                call.respondTemplate(
                    "searchFlight.peb", 
                    call.nonNullSessionData() + mapOf(
                        "departure" to dep,
                        "destination" to dest,
                        "departureLabel" to depLabel,
                        "destinationLabel" to destLabel
                    )
                )
            }

            if (session != null && session.message.isNotEmpty()) {
                call.sessions.set(session.copy(message = ""))
            }
        }

        get("/login") {
            // get Referer, if empty then go to home page
            val referer = call.request.headers["Referer"] ?: "/"

            call.respondTemplate("login.peb", mapOf("error" to "", "redirect_url" to referer))
        }

        post("/login") {
            val params = call.receiveParameters()
            val email = params["username"] ?: ""
            val password = params["password"] ?: ""

            // if not get redirect_url, goto /profile
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
                        isAdmin = loginResult.isAdmin,
                    ),
                )
                call.respondRedirect(redirectUrl)
            } else {
                call.respondTemplate(
                    "login.peb",
                    mapOf(
                        "loggedIn" to false,
                        "error" to "Invalid email or password",
                        "redirect_url" to redirectUrl,
                    ),
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
                call.respondTemplate(
                    "reset-password.peb",
                    mapOf(
                        "email" to email,
                        "error" to "Invalid code or password",
                    ),
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
                val username = session.username
                val userID = UserDAO.getUserID(username)
                val bookings = UserDAO.getBookings(userID)
                val loyaltyPoints = UserDAO.getLoyaltyPoints(userID)

                call.respondTemplate(
                    "profile.peb",
                    call.nonNullSessionData() +
                        mapOf(
                            "bookings" to bookings,
                            "loyaltyPoints" to loyaltyPoints,
                        ),
                )        
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
                passengers.add(
                    mapOf(
                        "fullName" to (params["passenger_${i}_fullName"] ?: ""),
                        "idNumber" to (params["passenger_${i}_ID"] ?: ""),
                        "type" to (params["passenger_${i}_type"] ?: "adult"),
                        "seat" to (params["passenger_${i}_seat"] ?: ""),
                    ),
                )
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

            // Prepare Template Data
            val templateData =
                mapOf<String, Any>(
                    "departure" to departure,
                    "destination" to destination,
                    "departureDate" to departureDate,
                    "returnDate" to (returnDate ?: ""),
                    "tripType" to tripType,
                    "adults" to adults,
                    "children" to children,
                    "combinedFlights" to combinedFlights, // The unified list for the frontend loop
                    "returnFlights" to returnFlightsList, // Also a unified list for the return journey
                )

            call.respondTemplate("flights.peb",
             call.nonNullSessionData() + templateData,
             )
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
            val totalPassengers = adults + children

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

            // Calculate preview total price:
            // Sum each leg's per-person price × total passengers (adults + children, no discount)
            var previewTotalPrice = 0.0
            for (id in outboundFlightIds) {
                val flight = FlightDAO.getFlightOverview(id) ?: continue
                previewTotalPrice += getPriceByCabin(flight, outboundCabin) * totalPassengers
            }
            for (id in returnFlightIds) {
                val flight = FlightDAO.getFlightOverview(id) ?: continue
                previewTotalPrice += getPriceByCabin(flight, returnCabin ?: "economy") * totalPassengers
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
                "returnCabinVal" to (returnCabin ?: ""),
                // Preview price shown on the confirmation page before final booking
                "totalPrice" to "%.2f".format(previewTotalPrice)
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

            call.respondTemplate("confirmBooking.peb", call.nonNullSessionData() + templateData)
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
            val adults = params["adults"]?.toIntOrNull() ?: 1
            val children = params["children"]?.toIntOrNull() ?: 0
            val totalPassengers = adults + children

            val outboundFlightIds = params["outboundFlightIdsRaw"]
                ?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
            val returnFlightIds = params["returnFlightIdsRaw"]
                ?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()

            if (outboundFlightIds.isEmpty()) {
                call.respondRedirect("/")
                return@post
            }

            var totalPrice = 0.0
            val allFlightIds = mutableListOf<Int>()
            for (id in (outboundFlightIds + (params["returnFlightIdsRaw"]?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()))) {
                val flight = FlightDAO.getFlightOverview(id) ?: continue
                totalPrice += getPriceByCabin(flight, outboundCabin) * totalPassengers
                allFlightIds.add(id)
            }

            val passengers = mutableListOf<Map<String, String>>()
            val rescheduleSeatMap = mutableMapOf<Int, Int>()
            val rescheduleId = session.rescheduleBookingId
            
            val oldPassengerIds = if (rescheduleId != null) {
                UserDAO.getPassengerIdsByBooking(rescheduleId)
            } else emptyList()

            for (i in 0 until adults) {
                val seatIdStr = params["adult_${i}_seat_step1"] ?: ""
                val seatId = seatIdStr.toIntOrNull() ?: 0
                
                if (rescheduleId != null && i < oldPassengerIds.size) {
                    rescheduleSeatMap[oldPassengerIds[i]] = seatId
                }

                passengers.add(mapOf(
                    "fullName" to (params["adult_${i}_fullName"] ?: ""),
                    "idNumber" to (params["adult_${i}_ID"] ?: ""),
                    "type" to "adult",
                    "seat" to seatIdStr,
                    "cabin" to outboundCabin
                ))
            }

            if (rescheduleId != null) {
                val success = UserDAO.executeReschedule(
                    bookingId = rescheduleId,
                    newFlightId = allFlightIds.first(),
                    newTotalPrice = totalPrice,
                    passengerSeatSelections = rescheduleSeatMap
                )
                
                if (success) {
                    call.sessions.set(session.copy(rescheduleBookingId = null))
                    call.respondRedirect("/profile?message=Reschedule%20Successful")
                } else {
                    call.respondRedirect("/profile?message=Reschedule%20Failed")
                }
            } else {
                val userID = UserDAO.getUserID(session.username)
                val isSuccess = UserDAO.createBooking(userID, session.username, allFlightIds, totalPrice, passengers)
                
                if (isSuccess) {
                    val totalPriceFormatted = "%.2f".format(totalPrice)
                    call.respondRedirect("/payment?totalPrice=$totalPriceFormatted")
                } else {
                    call.respondRedirect("/?error=ProcessFailed")
                }
            }
        }

        get("/payment") {
            val totalPrice = call.parameters["totalPrice"] ?: "0.0"

            call.respond(
                PebbleContent(
                    "payment.peb",
                    call.nonNullSessionData() +
                        mapOf(
                            "totalPrice" to totalPrice,
                        ),
                ),
            )
        }

        post("/payment") {
            call.respondRedirect("/payment-success")
        }

        get("/payment-success") {
            call.respond(
                PebbleContent(
                    "payment-success.peb",
                    call.nonNullSessionData(),
                ),
            )
        }

        get("/reschedule/start") {
            val bookingId = call.request.queryParameters["bookingId"]?.toIntOrNull()
            if (bookingId == null) {
                call.respondRedirect("/")
                return@get
            }

            val oldBooking = UserDAO.getBookingForReschedule(bookingId) 
    
            if (oldBooking != null) {
                val currentSession = call.sessions.get<UserSession>()
                if (currentSession != null) {
                    call.sessions.set(currentSession.copy(rescheduleBookingId = bookingId))
                }

                val depId = oldBooking["origin"]
                val destId = oldBooking["destination"]
        
                call.respondRedirect("/?departure=$depId&destination=$destId&departureLabel=$depId&destinationLabel=$destId")
            } else {
                println("DEBUG: Could not find booking #$bookingId for reschedule.")
                call.respondRedirect("/")
            }
        }

    }
}