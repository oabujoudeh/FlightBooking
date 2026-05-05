package com.flightbooking

import java.time.LocalDate
import java.time.LocalTime

/**
 * Stores the main details for one flight.
 */
data class Flight(
    val flightId: Int,
    val flightNumber: String,
    val aircraftType: String,
    val departureCity: String,
    val arrivalCity: String,
    val departureAirportName: String,
    val arrivalAirportName: String,
    val departureTerminal: String,
    val arrivalTerminal: String,
    val departureDate: LocalDate,
    val departureTime: LocalTime,
    val arrivalTime: LocalTime,
    val arrivalDayOffset: Int = 0, // 0 = same day, 1 = next day, 2 = next next day
    val durationMinutes: Int,
    val priceEconomy: Double?,
    val priceBusiness: Double?,
    val priceFirst: Double?
)

/**
 * Stores two flights that make up a connecting journey.
 */
data class ConnectingFlight(
    val leg1: Flight,
    val leg2: Flight,
    val totalDurationMinutes: Int,
    val layoverMinutes: Int,
    val totalPrice: Double
)