package com.flightbooking

import java.sql.Connection
import java.sql.DriverManager

/**
 * Singleton that manages SQLite database connectivity.
 *
 * All DAO objects obtain their connections through [getConnection].
 * The database file is located at `database/flights.db` relative to
 * the working directory.
 */
object Database {
    private const val URL = "jdbc:sqlite:database/flights.db"

    /**
     * Opens and returns a new SQLite [Connection] with foreign key enforcement enabled.
     *
     * Foreign key support is off by default in SQLite and must be activated per
     * connection via `PRAGMA foreign_keys = ON`. Callers are responsible for
     * closing the connection when done, typically via a `use` block.
     *
     * @return a new [Connection] to the SQLite database
     */
    fun getConnection(): Connection {
        val conn = DriverManager.getConnection(URL)
        conn.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
        return conn
    }
}
