package com.flightbooking

import java.sql.Connection
import java.sql.DriverManager
import java.sql.*
import java.time.LocalDate
import java.time.LocalTime


object Database {
    private const val URL = "jdbc:sqlite:database/flights.db"

    fun getConnection(): Connection = DriverManager.getConnection(URL)

}