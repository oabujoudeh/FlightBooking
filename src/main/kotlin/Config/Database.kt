package com.flightbooking

import java.sql.Connection
import java.sql.DriverManager

object Database {
    private const val URL = "jdbc:sqlite:database/flights.db"

    fun getConnection(): Connection = DriverManager.getConnection(URL)
}
