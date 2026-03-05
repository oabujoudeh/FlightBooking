package com.flightbooking

import io.ktor.server.application.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondTemplate("base.peb", emptyMap<String, Any>())
        }
    }

    route("/api") {
        get("/flights/search") {

            val departure = call.request.queryParameters["departure"]
            val arrival = call.request.queryParameters["arrival"]
            val date = call.request.queryParameters["date"]

            if (departure == null || arrival == null || date == null) {
                call.respond(
                    mapOf(
                        "success" to false,
                        "error" to "Please provide departure city, arrival city, and date"
                    )
                )
                return@get
            }

            val flights = Database.searchFlights(
                departure,
                arrival,
                LocalDate.parse(date)
            )

            call.respond(
                mapOf(
                    "success" to true,
                    "count" to flights.size,
                    "flights" to flights.map { flight ->
                        mapOf(
                            "flightId" to flight.flightId,
                            "flightNumber" to flight.flightNumber,
                            "departureCity" to flight.departureCity,
                            "arrivalCity" to flight.arrivalCity,
                            "departureTerminal" to flight.departureTerminal,
                            "arrivalTerminal" to flight.arrivalTerminal,
                            "departureDate" to flight.departureDate.toString(),
                            "departureTime" to flight.departureTime.toString(),
                            "arrivalTime" to flight.arrivalTime.toString(),
                            "price" to flight.price
                        )
                    }
                )
            )
        }
    }

}
