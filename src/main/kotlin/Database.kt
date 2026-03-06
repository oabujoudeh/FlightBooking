package com.flightbooking

import java.sql.Connection
import java.sql.DriverManager
import java.sql.*
import java.time.LocalDate
import java.time.LocalTime
import java.io.File


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


object Database {
    private const val URL = "jdbc:sqlite:database/flights.db"

    fun getConnection(): Connection = DriverManager.getConnection(URL)

    fun seedAirports() {
        val file = File("airports.csv")
        if (!file.exists()) {
            println("airports.csv not found")
            return
        }

        val lines = file.readLines()
        val header = lines.first().split(",").map { it.trim('"') }

    }

}