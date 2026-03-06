package com.example.com

import java.time.LocalDate
import java.time.LocalTime

data class Flight(
    val flightId: Int,
    val flightNumber: String,
    val departureCity: String,
    val arrivalCity: String,
    val departureTerminal: String,
    val arrivalTerminal: String,
    val departureDate: LocalDate,
    val departureTime: LocalTime,
    val arrivalTime: LocalTime,
    val price: Double,
)
