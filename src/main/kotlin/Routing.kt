package com.flightbooking

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import java.time.LocalDate
import java.time.LocalTime

/** Cache-Control header value that prevents browsers from caching authenticated pages. */
private const val PRIVATE_NO_STORE_CACHE_CONTROL: String = "no-store, no-cache, must-revalidate, max-age=0"

/** Expires header value that marks a cached response as immediately stale. */
private const val EXPIRED_CACHE_TIME: String = "0"

/**
 * Builds the standard session data map passed to every Pebble template.
 *
 * Returns safe defaults for all keys when [session] is null, so templates
 * can reference session values without null checks.
 *
 * @param session the current [UserSession], or null for unauthenticated requests
 * @param notifications list of resolved change-request notifications for the user
 * @return a map with keys `"loggedIn"`, `"username"`, `"message"`, `"isAdmin"`,
 *         and `"notifications"`
 */
internal fun buildSessionTemplateData(
    session: UserSession?,
    notifications: List<Map<String, Any>> = emptyList(),
): Map<String, Any> {
    if (session == null) {
        return mapOf(
            "loggedIn" to false,
            "username" to "",
            "message" to "",
            "isAdmin" to false,
            "notifications" to notifications,
        )
    }
    return mapOf(
        "loggedIn" to session.loggedIn,
        "username" to session.username,
        "message" to session.message,
        "isAdmin" to session.isAdmin,
        "notifications" to notifications,
    )
}

/**
 * Fetches resolved change-request notifications for the current user.
 *
 * Returns an empty list when there is no session, the user is not logged in,
 * or the user ID cannot be resolved.
 *
 * @param session the current [UserSession], or null
 * @return a list of notification maps, or an empty list if unavailable
 */
private fun getUserNotifications(session: UserSession?): List<Map<String, Any>> {
    if (session == null || !session.loggedIn) return emptyList()
    val userId: Int = UserDAO.getUserID(session.username)
    if (userId == -1) return emptyList()
    return UserDAO.getUserNotifications(userId)
}

/**
 * Returns the full template data map for the current request, combining session
 * values with the user's resolved notifications.
 *
 * Delegates to [buildSessionTemplateData]. All keys are present with safe
 * defaults even when no session exists.
 *
 * @receiver the current [ApplicationCall]
 * @return a map suitable for passing directly to [respondTemplate]
 */
private fun ApplicationCall.nonNullSessionData(): Map<String, Any> {
    val session: UserSession? = sessions.get<UserSession>()
    val notifications: List<Map<String, Any>> = getUserNotifications(session)
    return buildSessionTemplateData(session, notifications)
}

/**
 * Appends response headers that prevent browsers and proxies from caching
 * the current page.
 *
 * Should be called on any route that serves personalised or session-dependent
 * content (e.g. profile, dashboard, login).
 *
 * @receiver the current [ApplicationCall]
 */
private fun ApplicationCall.disableAuthenticatedPageCaching() {
    response.headers.append(HttpHeaders.CacheControl, PRIVATE_NO_STORE_CACHE_CONTROL)
    response.headers.append(HttpHeaders.Pragma, "no-cache")
    response.headers.append(HttpHeaders.Expires, EXPIRED_CACHE_TIME)
}

/**
 * Builds the nested deck-and-cabin data structure used by the seat-map template.
 *
 * Fetches (or generates) seat records for the flight, then walks the aircraft
 * layout config to produce a list of deck maps. Each deck contains its cabins,
 * and each cabin contains its rows; each row lists individual seat cells with
 * occupancy status, column label, and class. For multi-deck aircraft the seat
 * numbers are prefixed with `"M"` or `"U"` to match [SeatDAO] conventions.
 *
 * @param flightId the ID of the flight whose seats to render
 * @param aircraftType the aircraft type string used to look up the layout config
 * @return a list of deck maps ready for Pebble template rendering
 */
private fun buildDeckData(
    flightId: Int,
    aircraftType: String,
): List<Map<String, Any>> {
    val config = AircraftConfigs.getConfig(aircraftType)
    val seats = SeatDAO.getOrGenerateSeats(flightId, aircraftType)
    val seatMap = seats.associateBy { it.seatNumber }

    return config.decks.map { deck ->
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
}

/**
 * Returns the per-person price for a given cabin class on a flight.
 *
 * Matches [cabin] case-insensitively. Falls back to economy for any
 * unrecognised value. Returns `0.0` when the matched cabin price is null.
 *
 * @param flight the flight whose prices to read
 * @param cabin the cabin class string (e.g. `"economy"`, `"business"`, `"first"`)
 * @return the price for the cabin, or `0.0` if unavailable
 */
private fun getPriceByCabin(
    flight: Flight,
    cabin: String,
): Double =
    when (cabin.lowercase()) {
        "business" -> flight.priceBusiness
        "first" -> flight.priceFirst
        else -> flight.priceEconomy
    } ?: 0.0

/**
 * Filters out flights that have already departed, using the client's local time.
 *
 * Only applies filtering when [date] is today and [clientTimeStr] is non-null;
 * otherwise returns [flights] unchanged. If [clientTimeStr] cannot be parsed,
 * all flights are returned to avoid incorrectly hiding valid results.
 *
 * @param flights the list of flights to filter
 * @param date the searched departure date
 * @param clientTimeStr the browser's current local time as `"HH:mm"`, or null to skip filtering
 * @return the filtered list of flights, or the original list if filtering is not applicable
 */
private fun filterDepartedFlights(
    flights: List<FlightDisplayDTO>,
    date: LocalDate,
    clientTimeStr: String?,
): List<FlightDisplayDTO> {
    if (clientTimeStr.isNullOrEmpty()) return flights
    return try {
        val clientDate = LocalDate.now() // server date as proxy; same-day check done client-side
        val clientTime = LocalTime.parse(clientTimeStr)
        // Only filter if the searched date is today (client already sends time only for today)
        if (date != LocalDate.now()) return flights
        flights.filter { it.departureTime > clientTime }
    } catch (e: Exception) {
        flights // if parsing fails, show all flights
    }
}

/**
 * Registers all HTTP routes for the application.
 *
 * Covers user-facing pages (registration, login, password reset, flight search,
 * booking, seat selection, payment, profile, reschedule, complaints) and
 * admin pages (dashboard, flight tracking, chart endpoints, change-request
 * approval, and complaint management). Static resources are served from
 * the `"static"` classpath directory under `/static`.
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

            val notificationDetails: FlightStatusNotification? = AdminDAO.getFlightStatusNotification(flightId, newStatus)
            val success: Boolean = AdminDAO.updateFlightStatus(flightId, newStatus)
            if (success) {
                if (notificationDetails != null) {
                    FlightNotificationService.sendFlightStatusUpdate(notificationDetails)
                }

                val flightDate = params["flightDate"] ?: ""
                val flightNumber = params["flightNumber"] ?: ""
                call.respondRedirect("/?flightDate=$flightDate&flightNumber=$flightNumber")
            } else {
                call.respond(HttpStatusCode.InternalServerError, "Failed to update status")
            }
        }

        // AJAX endpoint: returns flight data as JSON for the flight table (no full page reload)
        get("/admin/flights-fragment") {
            val session = call.sessions.get<UserSession>()
            if (session == null || !session.isAdmin) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }
            val flightDateRaw = call.request.queryParameters["flightDate"]
            val flightNumber = call.request.queryParameters["flightNumber"]
            val flightDate = if (flightDateRaw.isNullOrEmpty()) LocalDate.now().toString() else flightDateRaw

            val trackedFlights =
                AdminDAO.trackFlights(
                    filterDate = flightDate,
                    filterNumber = if (flightNumber.isNullOrEmpty()) null else flightNumber,
                )
            val adjacentDates = AdminDAO.getAdjacentFlightDates(flightDate)

            fun esc(v: Any?): String =
                (v as? String ?: "")
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")

            val parts = mutableListOf<String>()
            trackedFlights.forEach { f ->
                val flightId = f["flightId"] ?: 0
                val flightNum = esc(f["flightNumber"])
                val origin = esc(f["originCity"])
                val dest = esc(f["destCity"])
                val status = esc(f["status"])
                val depTime = esc(f["depTime"])
                val arrTime = esc(f["arrTime"])
                val overnight = f["overnight"] as? Boolean ?: false
                parts.add(
                    """{"flightId":$flightId,"flightNumber":"$flightNum","originCity":"$origin","destCity":"$dest","status":"$status","depTime":"$depTime","arrTime":"$arrTime","overnight":$overnight}""",
                )
            }

            val prev = adjacentDates["prevDate"] ?: ""
            val next = adjacentDates["nextDate"] ?: ""
            val json =
                "{\"date\":\"$flightDate\",\"prevDate\":\"$prev\",\"nextDate\":\"$next\"" +
                    ",\"count\":${trackedFlights.size},\"flights\":[${parts.joinToString(",")}]}"

            call.respondText(json, io.ktor.http.ContentType.Application.Json)
        }

        get("/") {
            call.disableAuthenticatedPageCaching()
            val session = call.sessions.get<UserSession>()
            val userId = session?.let { UserDAO.getUserID(it.username) } ?: -1

            val userNotifications = if (userId != -1) UserDAO.getUserNotifications(userId) else emptyList()

            if (session != null && session.isAdmin) {
                val filterDate = call.request.queryParameters["filterDate"]
                val filterUsername = call.request.queryParameters["filterUsername"]
                val filterStatus = call.request.queryParameters["filterStatus"]

                val trackedResults =
                    AdminDAO.trackReservations(
                        filterDate = if (filterDate.isNullOrEmpty()) null else filterDate,
                        filterUsername = if (filterUsername.isNullOrEmpty()) null else filterUsername,
                        filterStatus = if (filterStatus.isNullOrEmpty()) null else filterStatus,
                    )

                val recentBookings = AdminDAO.getRecentBookings()
                val recentCancellations = AdminDAO.getRecentCancellations()
                val upcomingFlights = AdminDAO.getUpcomingFlights()

                val totalUsersCount = AdminDAO.getTotalUsers()
                val pendingRequests = AdminDAO.getPendingChangeRequests()

                val bookingsPerFlightDate = call.request.queryParameters["bpfDate"]
                val routesStartDate = call.request.queryParameters["routesStart"]
                val routesEndDate = call.request.queryParameters["routesEnd"]
                val filterSeason = call.request.queryParameters["filterSeason"]

                val bookingsPerFlight =
                    AdminDAO.getBookingsPerFlight(
                        filterDate = if (bookingsPerFlightDate.isNullOrEmpty()) null else bookingsPerFlightDate,
                        filterSeason = if (filterSeason.isNullOrEmpty()) null else filterSeason,
                    )
                val popularRoutes =
                    AdminDAO.getBusiestRoutes(
                        startDate = if (routesStartDate.isNullOrEmpty()) null else routesStartDate,
                        endDate = if (routesEndDate.isNullOrEmpty()) null else routesEndDate,
                        filterSeason = if (filterSeason.isNullOrEmpty()) null else filterSeason,
                    )
                val peakBookingTimes =
                    AdminDAO.getAllBookingsGroupedByDate(
                        filterSeason = if (filterSeason.isNullOrEmpty()) null else filterSeason,
                    )

                val flightDateRaw = call.request.queryParameters["flightDate"]
                val flightNumber = call.request.queryParameters["flightNumber"]
                val flightDate = if (flightDateRaw.isNullOrEmpty()) LocalDate.now().toString() else flightDateRaw

                val trackedFlights =
                    AdminDAO.trackFlights(
                        filterDate = flightDate,
                        filterNumber = if (flightNumber.isNullOrEmpty()) null else flightNumber,
                    )

                val adjacentDates = AdminDAO.getAdjacentFlightDates(flightDate)

                call.respondTemplate(
                    "adminHome.peb",
                    call.nonNullSessionData() +
                        mapOf(
                            "trackedReservations" to trackedResults,
                            "trackedFlights" to trackedFlights,
                            "lastFilterDate" to (filterDate ?: ""),
                            "lastFilterUsername" to (filterUsername ?: ""),
                            "lastFilterStatus" to (filterStatus ?: ""),
                            "lastFlightDate" to flightDate,
                            "lastFlightNumber" to (flightNumber ?: ""),
                            "flightPrevDate" to (adjacentDates["prevDate"] ?: ""),
                            "flightNextDate" to (adjacentDates["nextDate"] ?: ""),
                            "recentBookings" to recentBookings,
                            "recentCancellations" to recentCancellations,
                            "upcomingFlights" to upcomingFlights,
                            "totalUsers" to totalUsersCount,
                            "pendingRequests" to pendingRequests,
                            "bookingsPerFlight" to bookingsPerFlight,
                            "popularRoutes" to popularRoutes,
                            "peakBookingTimes" to peakBookingTimes,
                            "lastBpfDate" to (bookingsPerFlightDate ?: ""),
                            "lastRoutesStart" to (routesStartDate ?: ""),
                            "lastRoutesEnd" to (routesEndDate ?: ""),
                            "notifications" to userNotifications,
                            "lastFilterSeason" to (filterSeason ?: ""),
                        ),
                )
            } else {
                val dep = call.request.queryParameters["departure"] ?: ""
                val dest = call.request.queryParameters["destination"] ?: ""
                val depLabel = call.request.queryParameters["departureLabel"] ?: ""
                val destLabel = call.request.queryParameters["destinationLabel"] ?: ""

                call.respondTemplate(
                    "searchFlight.peb",
                    call.nonNullSessionData() +
                        mapOf(
                            "departure" to dep,
                            "destination" to dest,
                            "departureLabel" to depLabel,
                            "destinationLabel" to destLabel,
                            "notifications" to userNotifications,
                        ),
                )
            }

            if (session != null && session.message.isNotEmpty()) {
                call.sessions.set(session.copy(message = ""))
            }
        }

        get("/login") {
            call.disableAuthenticatedPageCaching()
            // get Referer, if empty then go to home page
            val referer = call.request.headers["Referer"] ?: "/"

            call.respondTemplate("login.peb", mapOf("error" to "", "redirect_url" to referer))
        }

        post("/login") {
            call.disableAuthenticatedPageCaching()
            val params = call.receiveParameters()
            val email = params["username"] ?: ""
            val password = params["password"] ?: ""

            // if not get redirect_url, goto /
            var redirectUrl = params["redirect_url"] ?: "/"

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
            call.disableAuthenticatedPageCaching()
            call.sessions.clear<UserSession>()
            call.respondRedirect("/")
        }

        get("/profile") {
            call.disableAuthenticatedPageCaching()
            val session = call.sessions.get<UserSession>()

            if (session != null && session.loggedIn) {
                val username = session.username
                val userID = UserDAO.getUserID(username)
                val bookings = UserDAO.getBookings(userID)
                val loyaltyPoints = UserDAO.getLoyaltyPoints(userID)
                val userDetails = UserDAO.getUserDetails(userID)

                val profileUpdated = call.request.queryParameters["updated"] == "true"
                call.respondTemplate(
                    "profile.peb",
                    call.nonNullSessionData() +
                        mapOf(
                            "bookings" to bookings,
                            "loyaltyPoints" to loyaltyPoints,
                            "firstName" to (userDetails?.firstName ?: ""),
                            "lastName" to (userDetails?.lastName ?: ""),
                            "email" to (userDetails?.email ?: ""),
                            "profileUpdated" to profileUpdated,
                        ),
                )
            } else {
                call.respondRedirect("/login")
            }
        }

        get("/profile/edit") {
            val session = call.sessions.get<UserSession>() ?: return@get call.respondRedirect("/login")
            val userID = UserDAO.getUserID(session.username)
            if (userID == -1) return@get call.respondRedirect("/login")
            val userDetails = UserDAO.getUserDetails(userID)
            call.respondTemplate(
                "editProfile.peb",
                call.nonNullSessionData() +
                    mapOf(
                        "firstName" to (userDetails?.firstName ?: ""),
                        "middleName" to (userDetails?.middleName ?: ""),
                        "lastName" to (userDetails?.lastName ?: ""),
                        "email" to (userDetails?.email ?: ""),
                    ),
            )
        }

        post("/profile/edit") {
            val session = call.sessions.get<UserSession>() ?: return@post call.respondRedirect("/login")
            val userID = UserDAO.getUserID(session.username)
            if (userID == -1) return@post call.respondRedirect("/login")
            val params = call.receiveParameters()
            val firstName = params["firstName"]?.trim() ?: ""
            val middleName = params["middleName"]?.trim() ?: ""
            val lastName = params["lastName"]?.trim() ?: ""
            val email = params["email"]?.trim() ?: ""
            val success = UserDAO.updateUserProfile(userID, firstName, middleName, lastName, email)
            if (success) {
                if (email != session.username) {
                    call.sessions.set(session.copy(username = email))
                }
                call.respondRedirect("/profile?updated=true#personal")
            } else {
                call.respondRedirect("/profile/edit?error=true")
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
            val notificationDetails: BookingCancellationNotification? = UserDAO.getBookingCancellationNotification(bookingId, userID)
            val success: Boolean = UserDAO.cancelBooking(bookingId, userID)
            if (success && notificationDetails != null) {
                FlightNotificationService.sendBookingCancellation(notificationDetails)
            }

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

        get("/booking-detail") {
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

            call.respondTemplate(
                "bookingDetail.peb",
                call.nonNullSessionData() + mapOf("booking" to booking),
            )
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

            val clientTime = params["clientTime"]

            val combinedFlights =
                filterDepartedFlights(
                    FlightDAO.getAvailableFlights(departure, destination, depLocalDate),
                    depLocalDate,
                    clientTime,
                )

            var returnFlightsList = emptyList<FlightDisplayDTO>()
            if (tripType != "oneway" && returnDate != null) {
                val retDate = LocalDate.parse(returnDate)
                returnFlightsList =
                    filterDepartedFlights(
                        FlightDAO.getAvailableFlights(destination, departure, retDate),
                        retDate,
                        clientTime,
                    )
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

            call.respondTemplate(
                "flights.peb",
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
            val tripType = if (params["returnFlight"].isNullOrEmpty()) "oneway" else "return"

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
                flightSteps.add(
                    mapOf(
                        "stepIndex" to 1,
                        "label" to "Outbound – Leg 1",
                        "flightId" to outboundFlightIds[0],
                        "cabin" to outboundCabin,
                        "deckData" to buildDeckData(outboundFlightIds[0], primaryOutboundFlight.aircraftType),
                    ),
                )
                if (leg2Flight != null) {
                    flightSteps.add(
                        mapOf(
                            "stepIndex" to 2,
                            "label" to "Outbound – Leg 2",
                            "flightId" to outboundFlightIds[1],
                            "cabin" to outboundCabin,
                            "deckData" to buildDeckData(outboundFlightIds[1], leg2Flight.aircraftType),
                        ),
                    )
                }
            } else {
                // Direct outbound
                flightSteps.add(
                    mapOf(
                        "stepIndex" to 1,
                        "label" to "Outbound",
                        "flightId" to outboundFlightIds[0],
                        "cabin" to outboundCabin,
                        "deckData" to buildDeckData(outboundFlightIds[0], primaryOutboundFlight.aircraftType),
                    ),
                )
            }

            if (returnFlightIds.isNotEmpty() && primaryReturnFlight != null) {
                val baseStep = flightSteps.size + 1
                if (returnFlightIds.size == 2) {
                    // Connecting return
                    val retLeg2Flight = FlightDAO.getFlightOverview(returnFlightIds[1])
                    flightSteps.add(
                        mapOf(
                            "stepIndex" to baseStep,
                            "label" to "Return – Leg 1",
                            "flightId" to returnFlightIds[0],
                            "cabin" to (returnCabin ?: "economy"),
                            "deckData" to buildDeckData(returnFlightIds[0], primaryReturnFlight.aircraftType),
                        ),
                    )
                    if (retLeg2Flight != null) {
                        flightSteps.add(
                            mapOf(
                                "stepIndex" to baseStep + 1,
                                "label" to "Return – Leg 2",
                                "flightId" to returnFlightIds[1],
                                "cabin" to (returnCabin ?: "economy"),
                                "deckData" to buildDeckData(returnFlightIds[1], retLeg2Flight.aircraftType),
                            ),
                        )
                    }
                } else {
                    // Direct return
                    flightSteps.add(
                        mapOf(
                            "stepIndex" to baseStep,
                            "label" to "Return",
                            "flightId" to returnFlightIds[0],
                            "cabin" to (returnCabin ?: "economy"),
                            "deckData" to buildDeckData(returnFlightIds[0], primaryReturnFlight.aircraftType),
                        ),
                    )
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
            val templateData =
                mutableMapOf<String, Any>(
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
                    "tripType" to tripType,
                    // Preview price shown on the confirmation page before final booking
                    "totalPrice" to "%.2f".format(previewTotalPrice),
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
                    val layoverMins =
                        java.time.Duration
                            .between(arr, dep)
                            .toMinutes()
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
                        val layoverMins =
                            java.time.Duration
                                .between(arr, dep)
                                .toMinutes()
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
            val returnCabin = params["returnCabin"] ?: "economy"
            val adults = params["adults"]?.toIntOrNull() ?: 1
            val children = params["children"]?.toIntOrNull() ?: 0
            val totalPassengers = adults + children
            val tripType = params["tripType"] ?: "oneway"

            // Read contact details
            val contactEmail = params["contactEmail"] ?: ""
            val contactPhone = params["contactPhone"] ?: ""

            val outboundFlightIds =
                params["outboundFlightIdsRaw"]
                    ?.split(",")
                    ?.mapNotNull { it.toIntOrNull() } ?: emptyList()
            val returnFlightIds =
                params["returnFlightIdsRaw"]
                    ?.split(",")
                    ?.mapNotNull { it.toIntOrNull() } ?: emptyList()

            if (outboundFlightIds.isEmpty()) {
                call.respondRedirect("/")
                return@post
            }
            // Calculate total price:
            // Each leg's per-person price × total passengers (adults + children, no discount)
            var totalPrice = 0.0
            val allFlightIds = mutableListOf<Int>()

            for (id in outboundFlightIds) {
                val flight = FlightDAO.getFlightOverview(id) ?: continue
                totalPrice += getPriceByCabin(flight, outboundCabin) * totalPassengers
                allFlightIds.add(id)
            }
            for (id in returnFlightIds) {
                val flight = FlightDAO.getFlightOverview(id) ?: continue
                totalPrice += getPriceByCabin(flight, returnCabin) * totalPassengers
                allFlightIds.add(id)
            }

            val totalSteps = outboundFlightIds.size + returnFlightIds.size
            val passengers = mutableListOf<Map<String, String>>()
            val rescheduleSeatMap = mutableMapOf<Int, Int>()
            val rescheduleId = session.rescheduleBookingId

            val oldPassengerIds =
                if (rescheduleId != null) {
                    UserDAO.getPassengerIdsByBooking(rescheduleId)
                } else {
                    emptyList()
                }

            for (i in 0 until adults) {
                val seats =
                    (1..totalSteps)
                        .map { step ->
                            params["adult_${i}_seat_step$step"] ?: ""
                        }.filter { it.isNotEmpty() }
                        .joinToString(",")
                passengers.add(
                    mapOf(
                        "fullName" to (params["adult_${i}_fullName"] ?: ""),
                        "idNumber" to (params["adult_${i}_ID"] ?: ""),
                        "type" to "adult",
                        "seat" to seats,
                        "cabin" to outboundCabin,
                    ),
                )
            }
            for (i in 0 until children) {
                val seats =
                    (1..totalSteps)
                        .map { step ->
                            params["child_${i}_seat_step$step"] ?: ""
                        }.filter { it.isNotEmpty() }
                        .joinToString(",")
                passengers.add(
                    mapOf(
                        "fullName" to (params["child_${i}_fullName"] ?: ""),
                        "idNumber" to (params["child_${i}_ID"] ?: ""),
                        "type" to "child",
                        "seat" to seats,
                        "cabin" to outboundCabin,
                    ),
                )
            }

            val totalPriceFormatted = "%.2f".format(totalPrice)

            // Save booking data to session — createBooking happens after payment
            call.sessions.set(
                session.copy(
                    pendingBooking =
                        PendingBooking(
                            flightIds = allFlightIds,
                            totalPrice = totalPrice,
                            passengers = passengers,
                            contactEmail = contactEmail,
                            contactPhone = contactPhone,
                            tripType = tripType,
                        ),
                ),
            )

            call.respondRedirect("/payment?totalPrice=$totalPriceFormatted")
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
            val session = call.sessions.get<UserSession>()
            if (session == null || !session.loggedIn) {
                call.respondRedirect("/login")
                return@post
            }

            // Check if this is a reschedule payment
            val pendingReschedule = session.pendingReschedule
            if (pendingReschedule != null) {
                val success =
                    UserDAO.executeReschedule(
                        bookingId = pendingReschedule.bookingId,
                        newFlightIds = pendingReschedule.newFlightIds,
                        newTotalPrice = pendingReschedule.newTotalPrice,
                        passengerSeatSelections = pendingReschedule.passengerSeatSelections,
                    )
                // Clear both reschedule session fields
                call.sessions.set(
                    session.copy(
                        pendingReschedule = null,
                        rescheduleBookingId = null,
                    ),
                )
                if (success) {
                    call.respondRedirect("/booking-detail?id=${pendingReschedule.bookingId}&rescheduled=true")
                } else {
                    call.respondRedirect("/")
                }
                return@post
            }

            // Normal booking payment
            val pending = session.pendingBooking
            if (pending == null) {
                call.respondRedirect("/")
                return@post
            }

            val userID = UserDAO.getUserID(session.username)
            val newBookingId =
                UserDAO.createBooking(
                    userID,
                    session.username,
                    pending.flightIds,
                    pending.totalPrice,
                    pending.passengers,
                    pending.contactEmail,
                    pending.contactPhone,
                    pending.tripType,
                )

            // Clear pending booking from session regardless of outcome
            call.sessions.set(session.copy(pendingBooking = null))

            if (newBookingId != null) {
                call.respondRedirect("/payment-success?bookingId=$newBookingId")
            } else {
                call.respondRedirect("/")
            }
        }

        get("/payment-success") {
            val bookingId = call.request.queryParameters["bookingId"]?.toIntOrNull()
            call.respond(
                PebbleContent(
                    "payment-success.peb",
                    call.nonNullSessionData() + mapOf("bookingId" to (bookingId ?: "")),
                ),
            )
        }

        // Step 1: Modal date picker submits here → search flights with reschedule flags
        get("/reschedule/flights") {
            val session = call.sessions.get<UserSession>()
            if (session == null || !session.loggedIn) {
                call.respondRedirect("/login")
                return@get
            }

            val bookingId = call.request.queryParameters["bookingId"]?.toIntOrNull()
            if (bookingId == null) {
                call.respondRedirect("/profile")
                return@get
            }

            val departureDate = call.request.queryParameters["departureDate"]
            if (departureDate == null) {
                call.respondRedirect("/profile")
                return@get
            }

            val returnDate = call.request.queryParameters["returnDate"] // null for oneway

            val oldBooking = UserDAO.getBookingForReschedule(bookingId)
            if (oldBooking == null) {
                call.respondRedirect("/profile")
                return@get
            }

            // Save reschedule booking ID in session
            call.sessions.set(session.copy(rescheduleBookingId = bookingId))

            val origin = oldBooking["origin"] as String
            val destination = oldBooking["destination"] as String
            val oldPrice = oldBooking["oldPrice"] as Double
            val passengerCount = oldBooking["passengerCount"] as Int
            val outboundCabin = oldBooking["outboundCabin"] as? String ?: "economy"
            val returnCabin = oldBooking["returnCabin"] as? String ?: outboundCabin

            // Get original flight IDs to exclude them from results
            val originalFlightIds = UserDAO.getFlightIdsForBooking(bookingId)

            val clientTime = call.request.queryParameters["clientTime"]

            val depLocalDate = LocalDate.parse(departureDate)
            val combinedFlights =
                filterDepartedFlights(
                    FlightDAO.getAvailableFlights(origin, destination, depLocalDate),
                    depLocalDate,
                    clientTime,
                ).filter { flight ->
                    flight.flightId
                        .split("_")
                        .mapNotNull { it.toIntOrNull() }
                        .none { it in originalFlightIds }
                }

            val returnFlights =
                if (returnDate != null) {
                    val retDate = LocalDate.parse(returnDate)
                    filterDepartedFlights(
                        FlightDAO.getAvailableFlights(destination, origin, retDate),
                        retDate,
                        clientTime,
                    ).filter { flight ->
                        flight.flightId
                            .split("_")
                            .mapNotNull { it.toIntOrNull() }
                            .none { it in originalFlightIds }
                    }
                } else {
                    combinedFlights.take(0)
                }

            val tripType = if (returnDate != null) "return" else "oneway"

            call.respondTemplate(
                "flights.peb",
                call.nonNullSessionData() +
                    mapOf(
                        "departure" to origin,
                        "destination" to destination,
                        "departureDate" to departureDate,
                        "returnDate" to (returnDate ?: ""),
                        "tripType" to tripType,
                        "adults" to passengerCount,
                        "children" to 0,
                        "combinedFlights" to combinedFlights,
                        "returnFlights" to returnFlights,
                        // Reschedule-specific flags passed through to the form
                        "reschedule" to true,
                        "bookingId" to bookingId,
                        "oldPrice" to oldPrice,
                        "passengerCount" to passengerCount,
                        "rescheduleCabin" to outboundCabin,
                        "rescheduleReturnCabin" to returnCabin,
                    ),
            )
        }

        // Step 2: Flight selected → build seat selection page with price diff
        post("/reschedule/select-seats") {
            val session = call.sessions.get<UserSession>()
            if (session == null || !session.loggedIn) {
                call.respondRedirect("/login")
                return@post
            }

            val params = call.receiveParameters()
            val bookingId = params["bookingId"]?.toIntOrNull() ?: return@post call.respondRedirect("/profile")
            val oldPrice = params["oldPrice"]?.toDoubleOrNull() ?: 0.0
            val passengerCount = params["passengerCount"]?.toIntOrNull() ?: 1

            // Parse outbound selection: "flightId_cabin" or "flightId1_flightId2_cabin"
            val outboundRaw = params["outboundFlight"] ?: return@post call.respondRedirect("/profile")
            val outboundParts = outboundRaw.split("_")
            val outboundCabin = outboundParts.last()
            val outboundFlightIds = outboundParts.dropLast(1).mapNotNull { it.toIntOrNull() }

            if (outboundFlightIds.isEmpty()) {
                call.respondRedirect("/profile")
                return@post
            }

            // Parse optional return selection
            val returnRaw = params["returnFlight"]
            val returnParts = returnRaw?.split("_")
            val returnCabin = returnParts?.last()
            val returnFlightIds = returnParts?.dropLast(1)?.mapNotNull { it.toIntOrNull() } ?: emptyList()

            // Calculate new total price across all legs × passengers
            var newTotalPrice = 0.0
            for (id in outboundFlightIds) {
                val flight = FlightDAO.getFlightOverview(id) ?: continue
                newTotalPrice += getPriceByCabin(flight, outboundCabin) * passengerCount
            }
            for (id in returnFlightIds) {
                val flight = FlightDAO.getFlightOverview(id) ?: continue
                newTotalPrice += getPriceByCabin(flight, returnCabin ?: "economy") * passengerCount
            }

            val priceDiff = newTotalPrice - oldPrice // negative = cheaper, positive = surcharge
            val extraCharge = if (priceDiff > 0) priceDiff else 0.0
            val isFree = priceDiff <= 0

            // Build flight steps (same logic as /book-flights) for seat map
            val primaryOutboundFlight =
                FlightDAO.getFlightOverview(outboundFlightIds.first())
                    ?: return@post call.respondRedirect("/profile")

            val flightSteps = mutableListOf<Map<String, Any>>()

            if (outboundFlightIds.size == 2) {
                val leg2 = FlightDAO.getFlightOverview(outboundFlightIds[1])
                flightSteps.add(
                    mapOf(
                        "stepIndex" to 1,
                        "label" to "Outbound – Leg 1",
                        "flightId" to outboundFlightIds[0],
                        "cabin" to outboundCabin,
                        "deckData" to buildDeckData(outboundFlightIds[0], primaryOutboundFlight.aircraftType),
                    ),
                )
                if (leg2 != null) {
                    flightSteps.add(
                        mapOf(
                            "stepIndex" to 2,
                            "label" to "Outbound – Leg 2",
                            "flightId" to outboundFlightIds[1],
                            "cabin" to outboundCabin,
                            "deckData" to buildDeckData(outboundFlightIds[1], leg2.aircraftType),
                        ),
                    )
                }
            } else {
                flightSteps.add(
                    mapOf(
                        "stepIndex" to 1,
                        "label" to "Outbound",
                        "flightId" to outboundFlightIds[0],
                        "cabin" to outboundCabin,
                        "deckData" to buildDeckData(outboundFlightIds[0], primaryOutboundFlight.aircraftType),
                    ),
                )
            }

            if (returnFlightIds.isNotEmpty()) {
                val primaryReturn = FlightDAO.getFlightOverview(returnFlightIds.first())
                if (primaryReturn != null) {
                    val base = flightSteps.size + 1
                    if (returnFlightIds.size == 2) {
                        val retLeg2 = FlightDAO.getFlightOverview(returnFlightIds[1])
                        flightSteps.add(
                            mapOf(
                                "stepIndex" to base,
                                "label" to "Return - Leg 1",
                                "flightId" to returnFlightIds[0],
                                "cabin" to (returnCabin ?: "economy"),
                                "deckData" to buildDeckData(returnFlightIds[0], primaryReturn.aircraftType),
                            ),
                        )
                        if (retLeg2 != null) {
                            flightSteps.add(
                                mapOf(
                                    "stepIndex" to base + 1,
                                    "label" to "Return - Leg 2",
                                    "flightId" to returnFlightIds[1],
                                    "cabin" to (returnCabin ?: "economy"),
                                    "deckData" to buildDeckData(returnFlightIds[1], retLeg2.aircraftType),
                                ),
                            )
                        }
                    } else {
                        flightSteps.add(
                            mapOf(
                                "stepIndex" to base,
                                "label" to "Return",
                                "flightId" to returnFlightIds[0],
                                "cabin" to (returnCabin ?: "economy"),
                                "deckData" to buildDeckData(returnFlightIds[0], primaryReturn.aircraftType),
                            ),
                        )
                    }
                }
            }

            // Fetch existing passengers so we pre-fill their names
            val existingPassengers = UserDAO.getPassengersForBooking(bookingId)

            val outboundLeg2Flight =
                if (outboundFlightIds.size == 2) {
                    FlightDAO.getFlightOverview(outboundFlightIds[1])
                } else {
                    null
                }

            val primaryReturnFlight =
                if (returnFlightIds.isNotEmpty()) {
                    FlightDAO.getFlightOverview(returnFlightIds.first())
                } else {
                    null
                }

            val returnLeg2Flight =
                if (returnFlightIds.size == 2) {
                    FlightDAO.getFlightOverview(returnFlightIds[1])
                } else {
                    null
                }

            val outboundLayoverDisplay =
                if (outboundLeg2Flight != null) {
                    val mins =
                        java.time.Duration
                            .between(
                                primaryOutboundFlight.arrivalTime,
                                outboundLeg2Flight.departureTime,
                            ).toMinutes()
                            .toInt()
                    Utils.formatDuration(mins)
                } else {
                    ""
                }

            val returnLayoverDisplay =
                if (primaryReturnFlight != null && returnLeg2Flight != null) {
                    val mins =
                        java.time.Duration
                            .between(
                                primaryReturnFlight.arrivalTime,
                                returnLeg2Flight.departureTime,
                            ).toMinutes()
                            .toInt()
                    Utils.formatDuration(mins)
                } else {
                    ""
                }

            val templateExtras =
                mutableMapOf<String, Any>(
                    "bookingId" to bookingId,
                    "outboundFlightIdsRaw" to outboundFlightIds.joinToString(","),
                    "returnFlightIdsRaw" to returnFlightIds.joinToString(","),
                    "outboundCabin" to outboundCabin,
                    "returnCabinVal" to (returnCabin ?: ""),
                    "newTotalPrice" to "%.2f".format(newTotalPrice),
                    "oldPrice" to "%.2f".format(oldPrice),
                    "priceDiff" to "%.2f".format(priceDiff),
                    "extraCharge" to "%.2f".format(extraCharge),
                    "isFree" to isFree,
                    "flightSteps" to flightSteps,
                    "totalSteps" to flightSteps.size,
                    "passengers" to existingPassengers,
                    "passengerCount" to passengerCount,
                    "outboundFlight" to Utils.flightToMap(primaryOutboundFlight),
                    "outboundLayoverDisplay" to outboundLayoverDisplay,
                    "returnLayoverDisplay" to returnLayoverDisplay,
                    "returnCabin" to (returnCabin ?: ""),
                )
            outboundLeg2Flight?.let { templateExtras["outboundLeg2Flight"] = Utils.flightToMap(it) }
            primaryReturnFlight?.let { templateExtras["returnFlight"] = Utils.flightToMap(it) }
            returnLeg2Flight?.let { templateExtras["returnLeg2Flight"] = Utils.flightToMap(it) }

            call.respondTemplate("rescheduleSeats.peb", call.nonNullSessionData() + templateExtras)
        }

        // Step 3: Seat selection submitted → execute the reschedule
        post("/reschedule/confirm") {
            val session = call.sessions.get<UserSession>()
            if (session == null || !session.loggedIn) {
                call.respondRedirect("/login")
                return@post
            }

            val params = call.receiveParameters()
            val bookingId = params["bookingId"]?.toIntOrNull() ?: return@post call.respondRedirect("/profile")
            val newTotalPrice = params["newTotalPrice"]?.toDoubleOrNull() ?: return@post call.respondRedirect("/profile")
            val oldPrice = params["oldPrice"]?.toDoubleOrNull() ?: 0.0

            // Collect all new flight IDs (outbound + return legs)
            val outboundFlightIds =
                params["outboundFlightIdsRaw"]
                    ?.split(",")
                    ?.mapNotNull { it.toIntOrNull() } ?: emptyList()
            val returnFlightIds =
                params["returnFlightIdsRaw"]
                    ?.split(",")
                    ?.mapNotNull { it.toIntOrNull() } ?: emptyList()
            val allNewFlightIds = outboundFlightIds + returnFlightIds

            if (allNewFlightIds.isEmpty()) {
                call.respondRedirect("/profile")
                return@post
            }

            // Collect all seat selections per passenger across all steps
            // key format: seat_{passengerId}_step{N} e.g. "seat_26_step1"
            // Result: Map<passengerId, comma-separated seats> e.g. {26 -> "3A,7A,16A,8A"}
            val seatsByPassenger = mutableMapOf<Int, MutableMap<Int, String>>()
            params
                .entries()
                .filter { it.key.startsWith("seat_") && it.value.firstOrNull()?.isNotEmpty() == true }
                .forEach { entry ->
                    val key = entry.key // e.g. "seat_26_step1"
                    val seatNumber = entry.value.firstOrNull() ?: return@forEach
                    // Extract passengerId and stepIndex from key
                    val stepIndex = key.substringAfterLast("step").toIntOrNull() ?: return@forEach
                    val passengerId = key.removePrefix("seat_").substringBefore("_step").toIntOrNull() ?: return@forEach
                    seatsByPassenger.getOrPut(passengerId) { mutableMapOf() }[stepIndex] = seatNumber
                }
            // Convert to Map<passengerId, comma-separated seats in step order> e.g. {26 -> "3A,7A,16A,8A"}
            val passengerSeatSelections =
                seatsByPassenger.mapValues { (_, stepMap) ->
                    stepMap.toSortedMap().values.joinToString(",")
                }

            val extraCharge = newTotalPrice - oldPrice

            if (extraCharge > 0) {
                // Store reschedule data in session and redirect to payment
                call.sessions.set(
                    session.copy(
                        pendingReschedule =
                            PendingReschedule(
                                bookingId = bookingId,
                                newFlightIds = allNewFlightIds,
                                newTotalPrice = newTotalPrice,
                                passengerSeatSelections = passengerSeatSelections,
                            ),
                    ),
                )
                val extraFormatted = "%.2f".format(extraCharge)
                call.respondRedirect("/payment?totalPrice=$extraFormatted&reschedule=true")
            } else {
                // Free reschedule — execute immediately
                val success =
                    UserDAO.executeReschedule(
                        bookingId = bookingId,
                        newFlightIds = allNewFlightIds,
                        newTotalPrice = newTotalPrice,
                        passengerSeatSelections = passengerSeatSelections,
                    )
                call.sessions.set(session.copy(rescheduleBookingId = null))
                if (success) {
                    call.respondRedirect("/booking-detail?id=$bookingId&rescheduled=true")
                } else {
                    call.respondTemplate("error.peb", mapOf("message" to "Reschedule failed. Please try again."))
                }
            }
        }

        get("/update-passenger-info") {
            val session = call.sessions.get<UserSession>() ?: return@get call.respondRedirect("/login")
            val bookingId = call.request.queryParameters["bookingId"]?.toIntOrNull() ?: return@get call.respondRedirect("/profile")

            val userId = UserDAO.getUserID(session.username)
            if (userId == -1) return@get call.respondRedirect("/login")

            val booking = UserDAO.getBookingById(bookingId, userId)

            if (booking != null) {
                call.respondTemplate("updateInfo.peb", mapOf("booking" to booking))
            } else {
                call.respondRedirect("/profile")
            }
        }

        get("/update-contact-info") {
            val session = call.sessions.get<UserSession>() ?: return@get call.respondRedirect("/login")
            val bookingId = call.request.queryParameters["bookingId"]?.toIntOrNull() ?: return@get call.respondRedirect("/profile")

            val userId = UserDAO.getUserID(session.username)

            val booking = UserDAO.getBookingById(bookingId, userId)

            if (booking != null) {
                call.respondTemplate("updateContact.peb", mapOf("booking" to booking))
            } else {
                call.respondRedirect("/profile")
            }
        }

        post("/update-contact-info") {
            val session = call.sessions.get<UserSession>() ?: return@post call.respondRedirect("/login")
            val userId = UserDAO.getUserID(session.username)
            if (userId == -1) return@post call.respondRedirect("/login")
            val params = call.receiveParameters()

            val bookingId = params["bookingId"]?.toIntOrNull() ?: return@post call.respondRedirect("/profile")
            val newEmail = params["newEmail"]?.trim() ?: ""
            val newPhone = params["newPhone"]?.trim() ?: ""

            val success = UserDAO.updateContactInfo(bookingId, userId, newEmail, newPhone)

            if (success) {
                call.respondRedirect("/booking-detail?id=$bookingId&updateSuccess=true")
            } else {
                call.respondText("Error: Could not update contact info.")
            }
        }

        post("/submit-info-request") {
            val session = call.sessions.get<UserSession>() ?: return@post call.respondRedirect("/login")
            val userId = UserDAO.getUserID(session.username)
            if (userId == -1) return@post call.respondRedirect("/login")
            val params = call.receiveParameters()

            val newName = params["newName"]?.trim() ?: ""
            val newId = params["newId"]?.trim() ?: ""

            val combinedContent = "$newName $newId"

            val success = UserDAO.insertChangeRequest(userId, combinedContent, "personal_info")

            if (success) {
                call.respondRedirect("/profile?requestSuccess=true")
            } else {
                call.respondText("Error: Could not submit request.")
            }
        }

        // 1. Admin approval page
        get("/admin/pending-requests") {
            val requests = AdminDAO.getPendingChangeRequests()
            call.respondTemplate("adminRequests.peb", mapOf("requests" to requests))
        }

        // 2. processing approval
        post("/admin/handle-request") {
            val params = call.receiveParameters()
            val requestId = params["requestId"]?.toLongOrNull() ?: return@post call.respondRedirect("/admin/pending-requests")
            val userId = params["userId"]?.toIntOrNull() ?: return@post call.respondRedirect("/admin/pending-requests")
            val action = params["action"]

            if (action == "approve") {
                AdminDAO.approveChangeRequest(requestId, userId)
            } else if (action == "reject") {
                AdminDAO.rejectChangeRequest(requestId, userId)
            }

            call.respondRedirect("/admin/pending-requests")
        }

        get("/complaint") {
            val session = call.sessions.get<UserSession>()
            if (session == null || !session.loggedIn) {
                call.respondRedirect("/login")
                return@get
            }
            val userId = UserDAO.getUserID(session.username)
            val complaints = ComplaintDAO.getComplaintsForUser(userId)
            call.respondTemplate(
                "complaint.peb",
                call.nonNullSessionData() +
                    mapOf(
                        "error" to "",
                        "success" to "",
                        "complaints" to complaints,
                    ),
            )
        }

        post("/complaint") {
            val session = call.sessions.get<UserSession>()
            if (session == null || !session.loggedIn) {
                call.respondRedirect("/login")
                return@post
            }

            val params = call.receiveParameters()
            val content = params["content"] ?: ""

            val userId = UserDAO.getUserID(session.username)
            val complaints = ComplaintDAO.getComplaintsForUser(userId)

            if (content.isEmpty()) {
                call.respondTemplate(
                    "complaint.peb",
                    call.nonNullSessionData() +
                        mapOf(
                            "error" to "Please enter your complaint before submitting.",
                            "success" to "",
                            "complaints" to complaints,
                        ),
                )
                return@post
            }

            val saved = ComplaintDAO.submitComplaint(userId, content)

            if (saved) {
                EmailService.sendEmail(
                    to = "tnvn3422@leeds.ac.uk",
                    subject = "New Complaint from ${session.username}",
                    body = "User: ${session.username}\n\n$content",
                )
                val updatedComplaints = ComplaintDAO.getComplaintsForUser(userId)
                call.respondTemplate(
                    "complaint.peb",
                    call.nonNullSessionData() +
                        mapOf(
                            "error" to "",
                            "success" to "Your complaint has been submitted. We will be in touch shortly.",
                            "complaints" to updatedComplaints,
                        ),
                )
            } else {
                call.respondTemplate(
                    "complaint.peb",
                    call.nonNullSessionData() +
                        mapOf(
                            "error" to "Something went wrong. Please try again.",
                            "success" to "",
                            "complaints" to complaints,
                        ),
                )
            }
        }

        get("/admin/complaints") {
            val session = call.sessions.get<UserSession>()
            if (session == null || !session.isAdmin) {
                call.respondRedirect("/")
                return@get
            }
            val complaints = ComplaintDAO.getAllComplaints()
            call.respondTemplate(
                "adminComplaints.peb",
                call.nonNullSessionData() + mapOf("complaints" to complaints),
            )
        }

        post("/admin/complaints/reply") {
            val session = call.sessions.get<UserSession>()
            if (session == null || !session.isAdmin) {
                call.respondRedirect("/")
                return@post
            }
            val params = call.receiveParameters()
            val complaintId = params["complaintId"]?.toIntOrNull()
            val reply = params["reply"] ?: ""

            if (complaintId != null && reply.isNotEmpty()) {
                ComplaintDAO.replyToComplaint(complaintId, reply)
            }

            call.respondRedirect("/admin/complaints")
        }
    }
}
