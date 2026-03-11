package com.flightbooking

import java.sql.Connection
import java.sql.DriverManager
import java.sql.*
import java.time.LocalDate
import java.time.LocalTime

data class User(
    val userId: Int? = null,
    val firstName: String,
    val middleName: String? = null,
    val lastName: String,
    val email: String,
    val passwordHash: String,
    val createdAt: String? = null
)

object Database {
    private const val URL = "jdbc:sqlite:database/flights.db"

    fun getConnection(): Connection = DriverManager.getConnection(URL)

}