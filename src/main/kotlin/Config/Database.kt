package com.flightbooking

import java.sql.Connection
import java.sql.DriverManager

object Database {
    private const val URL = "jdbc:sqlite:database/flights.db"

    /**
     * Returns a new SQLite connection with foreign key enforcement enabled.
     */
    fun getConnection(): Connection {
        val conn = DriverManager.getConnection(URL)
        conn.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
        return conn
    }
}
