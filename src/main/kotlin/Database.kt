package com.flightbooking

import java.sql.Connection
import java.sql.DriverManager
import java.sql.*
import java.io.File


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