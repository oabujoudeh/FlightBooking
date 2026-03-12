package com.flightbooking

import java.time.LocalDate
import java.time.LocalTime

data class Flight(
    val flightId: Int,
    val flightNumber: String,
    val departureCity: String,
    val arrivalCity: String,
    val departureAirportName: String,
    val arrivalAirportName: String,
    val departureTerminal: String,
    val arrivalTerminal: String,
    val departureDate: LocalDate,
    val departureTime: LocalTime,
    val arrivalTime: LocalTime,
    val durationMinutes: Int,
    val price: Double,
)

data class ConnectingFlight(
    val leg1: Flight,
    val leg2: Flight,
    val totalDurationMinutes: Int,
    val layoverMinutes: Int,
    val totalPrice: Double
)