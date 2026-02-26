package com.example.com

import model.Flight
// import java.time.LocalDateTime

import io.ktor.server.application.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.response.*

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondTemplate("base.peb", emptyMap<String, Any>())
            }

        get("/Flights"){
            val fakeFlights = listOf(
                Flight(
                    flight_id = 1,
                    route_id = 101,
                    departure_datetime = "abc",
                    arrival_datetime = "abc",
                    departure_terminal = "T1",
                    arrival_terminal = "A3",
                    status = "Scheduled",
                    price = 1200.0,
                    award_available = true
                ),
                Flight(
                    flight_id = 2,
                    route_id = 102,
                    departure_datetime = "abc",
                    arrival_datetime = "acb",
                    departure_terminal = "T2",
                    arrival_terminal = "B1",
                    status = "Delayed",
                    price = 1800.0,
                    award_available = false
                )
            )

            call.respond(fakeFlights)
        }
    }
}
