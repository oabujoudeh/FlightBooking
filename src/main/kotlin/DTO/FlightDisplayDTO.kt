package com.flightbooking

import java.time.LocalTime

/**
 * Simple model used to show flight results on the front end.
 *
 * It is used for both direct flights and connecting flights so the UI can
 * handle them in the same way.
 */
data class FlightDisplayDTO(
    val flightId: String, // Direct flights are "123", connecting flights are "123_456"
    val isConnecting: Boolean, // Whether it is a connecting flight

    val aircraftType: String,
    
    // Core flight information
    val departureAirport: String,
    val arrivalAirport: String,
    val departureTime: LocalTime,
    val arrivalTime: LocalTime,
    val arrivalDayOffset: Int,
    val totalDurationDisplay: String, // Formatted duration, e.g., "2h 30m"
    // Prices
    val priceEconomy: Double?,
    val priceBusiness: Double?,
    val priceFirst: Double?,
    // Terminal and connecting flight specific information
    val departureTerminal: String? = null,
    val arrivalTerminal: String? = null,
    val stopCity: String? = null, // Connecting city name
    val layoverMinutes: Int = 0 // Layover duration in minutes
)
