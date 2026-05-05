package com.flightbooking

import io.ktor.server.request.*
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
* Sets up the main routes for the app.
*
* This includes pages for login, register, flights, bookings, profile,admin charts, and payment.
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
                            "totalUsers" to totalUsers
                        )
                )
            } else {
                // use non‑nullable version of the map
                call.respondTemplate("searchFlight.peb", call.nonNullSessionData())
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

            // check
            val loginResult = UserDAO.loginUser(email, password)

            if (loginResult.success) {
                call.sessions.set(
                    UserSession(
                        username = email,
                        loggedIn = true,
                        isAdmin = loginResult.isAdmin,
                    ),
                )
                // should decide whether redirect to profile or booking page
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

            // Get the unified list (Direct + Connecting) from DAO
            // These are FlightDisplayDTO objects sorted by time
            val combinedFlights = FlightDAO.getAvailableFlights(departure, destination, depLocalDate)

            // Handle Return Flights if necessary
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

            if (outboundRaw.isEmpty()) {
                call.respondRedirect("/")
                return@post
            }

            // Parse the ID and the Cabin Class from the selection string
            val outboundParts = outboundRaw.split("_")
            val outboundCabin = outboundParts.last() // "economy", "business", or "first"

            // Handle both direct ("ID") and connecting ("ID1_ID2") by joining all but the last part
            val outboundFlightIdStr = outboundParts.dropLast(1).joinToString("_")

            // For seat selection, need the first leg's ID if it's a connecting flight
            val primaryOutboundId = outboundParts.first().toIntOrNull() ?: 0

            // Fetch flight details
            val outboundFlight = FlightDAO.getFlightOverview(primaryOutboundId)
            if (outboundFlight == null) {
                call.respondRedirect("/")
                return@post
            }

            // Handle return flight parsing
            var returnFlight: Flight? = null
            var returnCabin: String? = null
            if (returnRaw.isNotEmpty()) {
                val returnParts = returnRaw.split("_")
                returnCabin = returnParts.last()
                val returnPrimaryId = returnParts.first().toIntOrNull()
                if (returnPrimaryId != null) {
                    returnFlight = FlightDAO.getFlightOverview(returnPrimaryId)
                }
            }

            // Seat selection
            val config = AircraftConfigs.getConfig(outboundFlight.aircraftType)
            val seats = SeatDAO.getOrGenerateSeats(primaryOutboundId, outboundFlight.aircraftType)
            val seatMap = seats.associateBy { it.seatNumber }

            val deckData =
                config.decks.map { deck ->
                    val prefix =
                        if (config.decks.size > 1) {
                            mapOf("Main Deck" to "M", "Upper Deck" to "U")[deck.deckName] ?: ""
                        } else {
                            ""
                        }

                    mapOf(
                        "deckName" to deck.deckName,
                        "cabins" to
                            deck.cabins.map { cabin ->
                                mapOf(
                                    "seatClass" to cabin.seatClass,
                                    "rows" to
                                        cabin.rows.map { row ->
                                            mapOf(
                                                "rowNumber" to row,
                                                "isExit" to (row in deck.exitRows),
                                                "isBassinet" to (row in deck.bassinetRows),
                                                "groups" to
                                                    cabin.layout.map { group ->
                                                        group.map { col ->
                                                            val seatNumber = "$prefix$row$col"
                                                            val seat = seatMap[seatNumber]
                                                            mapOf(
                                                                "seatNumber" to seatNumber,
                                                                "col" to col,
                                                                "isOccupied" to (seat?.isOccupied ?: false),
                                                                "seatClass" to cabin.seatClass,
                                                            )
                                                        }
                                                    },
                                            )
                                        },
                                )
                            },
                    )
                }
            // calculate price w children as well

            val passengerCount = adults + children

            val outboundPrice = 
                when (outboundCabin.lowercase()){
                    "business" -> outboundFlight.priceBusiness ?: 0.0
                    "first" -> outboundFlight.priceFirst ?: 0.0
                    else -> outboundFlight.priceEconomy ?: 0.0
                }
            var totalPrice = outboundPrice * passengerCount

            // Prepare Template Data
            val templateData =
                mutableMapOf<String, Any>(
                    "outboundFlight" to Utils.flightToMap(outboundFlight),
                    "outboundCabin" to outboundCabin, // Pass the chosen class to the next page
                    "deckData" to deckData,
                    "adults" to adults,
                    "children" to children,
                    "totalPrice" to totalPrice,
                    "passengerCount" to passengerCount,
                )

            if (returnFlight != null) {
                val returnPrice =
                    when ((returnCabin ?: "economy").lowercase()) {
                        "business" -> returnFlight.priceBusiness ?: 0.0
                        "first" -> returnFlight.priceFirst ?: 0.0
                        else -> returnFlight.priceEconomy ?: 0.0
                    }
                 totalPrice += returnPrice * passengerCount

                templateData["returnFlight"] = Utils.flightToMap(returnFlight)
                templateData["returnCabin"] = returnCabin ?: "economy"
                templateData["totalPrice"] = totalPrice
            }

            call.respondTemplate("confirmBooking.peb",
             call.nonNullSessionData() + templateData,
             )
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
            

            val outboundFlightId = params["outboundFlightId"]?.toIntOrNull()
            val returnFlightId = params["returnFlightId"]?.toIntOrNull()
            val totalPrice = params["totalPrice"]?.toDoubleOrNull() ?: 0.0

            if (outboundFlightId == null) {
                call.respondRedirect("/")
                return@post
            }

            val flightIds = mutableListOf(outboundFlightId)

            if (returnFlightId != null) {
                flightIds.add(returnFlightId)
            }

            val adults = params["adults"]?.toIntOrNull() ?: 1
            val children = params["children"]?.toIntOrNull() ?: 0

            val passengers = mutableListOf<Map<String, String>>()

            for (i in 0 until adults) {
                passengers.add(
                    mapOf(
                        "fullName" to (params["adult_${i}_fullName"] ?: ""),
                        "idNumber" to (params["adult_${i}_ID"] ?: ""),
                        "type" to "adult",
                        "seat" to (params["adult_${i}_seat"] ?: ""),

                    )
                )
            }

            for(i in 0 until children) {
                passengers.add(
                    mapOf(
                        "fullName" to (params["child_${i}_fullName"] ?: ""),
                        "idNumber" to (params["child_${i}_ID"] ?: ""),
                        "type" to "child",
                        "seat" to (params["child_${i}_seat"] ?: ""),
                    )
                )
            }

            val userID = UserDAO.getUserID(session.username)

            val isSuccess =
                UserDAO.createBooking(
                    userID,
                    session.username,
                    flightIds,
                    totalPrice,
                    passengers,
                )
                
            if (isSuccess) {
                call.respondRedirect("/payment?totalPrice=$totalPrice")
            } else{
                call.respondRedirect("/")
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

            
        }
    }
