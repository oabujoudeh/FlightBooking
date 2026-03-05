package com.flightbooking

import java.sql.Connection
import java.sql.DriverManager
import java.sql.*
import java.time.LocalDate


data class Flight(
    val flightNumber: String,

    val departureCity: String,
    val arrivalCity: String,

    val aircraftType: String,

    val departureDate: LocalDate,
    val departureTime: String,
    val duration: String,
    val arrivalTime: String,     //calculated
    val departureTimezone: String,
    val arrivalTimezone: String,

    val departureTerminal: String,
    val arrivalTerminal: String,

    val status: String,
    val price: Double,
    val awardAvailable: Boolean
)


object Database {
    private const val URL = "flightbooking/database/flights.db"

    fun getConnection(): Connection = DriverManager.getConnection(URL)

}