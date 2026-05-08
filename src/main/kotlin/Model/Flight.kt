package com.flightbooking

import java.time.LocalDate
import java.time.LocalTime

/**
 * Stores the main details for one flight.
 *
 * @property flightId the unique identifier for the flight
 * @property flightNumber the flight number (e.g. "EJ101")
 * @property aircraftType the type of aircraft operating the flight (e.g. "Boeing 737")
 * @property departureCity the city the flight departs from
 * @property arrivalCity the city the flight arrives at
 * @property departureAirportName the full name of the departure airport
 * @property arrivalAirportName the full name of the arrival airport
 * @property departureTerminal the terminal at the departure airport
 * @property arrivalTerminal the terminal at the arrival airport
 * @property departureDate the date the flight departs
 * @property departureTime the local time the flight departs
 * @property arrivalTime the local time the flight arrives
 * @property arrivalDayOffset the number of days after departure that the flight arrives; 0 = same day, 1 = next day, 2 = two days later
 * @property durationMinutes the total flight duration in minutes
 * @property priceEconomy the economy cabin price; null if the cabin is unavailable
 * @property priceBusiness the business cabin price; null if the cabin is unavailable
 * @property priceFirst the first class cabin price; null if the cabin is unavailable
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
    val arrivalDayOffset: Int = 0,
    val durationMinutes: Int,
    val priceEconomy: Double?,
    val priceBusiness: Double?,
    val priceFirst: Double?,
)

/**
 * Stores two flights that make up a connecting journey.
 *
 * @property leg1 the first flight segment
 * @property leg2 the second flight segment
 * @property totalDurationMinutes the total journey duration in minutes, including the layover
 * @property layoverMinutes the duration of the layover between the two legs in minutes
 * @property totalPrice the combined price for both flight segments
 */
data class ConnectingFlight(
    val leg1: Flight,
    val leg2: Flight,
    val totalDurationMinutes: Int,
    val layoverMinutes: Int,
    val totalPrice: Double,
)
