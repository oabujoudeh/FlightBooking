package com.flightbooking

import java.time.LocalTime

/**
 * Simple model used to show flight results on the front end.
 *
 * It is used for both direct flights and connecting flights so the UI can
 * handle them in the same way.
 *
 * @property flightId the unique identifier for the flight; direct flights use a single ID (e.g. "123"),
 * connecting flights use a combined ID (e.g. "123_456")
 * @property isConnecting whether this entry represents a connecting flight
 * @property aircraftType the type of aircraft operating the flight (e.g. "Boeing 737")
 * @property departureAirport the name of the departure airport
 * @property arrivalAirport the name of the arrival airport
 * @property departureTime the local time the flight departs
 * @property arrivalTime the local time the flight arrives
 * @property arrivalDayOffset the number of days after departure that the flight arrives; 0 = same day, 1 = next day
 * @property totalDurationDisplay the formatted total duration string (e.g. "2h 30m")
 * @property priceEconomy the economy cabin price; null if the cabin is unavailable
 * @property priceBusiness the business cabin price; null if the cabin is unavailable
 * @property priceFirst the first class cabin price; null if the cabin is unavailable
 * @property departureTerminal the departure terminal; null for connecting flights
 * @property arrivalTerminal the arrival terminal; null for connecting flights
 * @property stopCity the name of the connecting city; null for direct flights
 * @property layoverMinutes the layover duration in minutes; 0 for direct flights
 */
data class FlightDisplayDTO(
    val flightId: String,
    val isConnecting: Boolean,
    val aircraftType: String,
    val departureAirport: String,
    val arrivalAirport: String,
    val departureTime: LocalTime,
    val arrivalTime: LocalTime,
    val arrivalDayOffset: Int,
    val totalDurationDisplay: String,
    val priceEconomy: Double?,
    val priceBusiness: Double?,
    val priceFirst: Double?,
    val departureTerminal: String? = null,
    val arrivalTerminal: String? = null,
    val stopCity: String? = null,
    val layoverMinutes: Int = 0,
)