package com.flightbooking

import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminChangeRequestTest {
    @Test
    fun approveChangeRequestAcceptsOnlySelectedPendingRequest(): Unit {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection: Connection ->
            createChangeRequestTables(connection)
            val userId: Int = insertUser(connection)
            insertBookingPassenger(connection, userId)
            val selectedRequestId: Long = insertChangeRequest(connection, userId, "Alice A123")
            insertChangeRequest(connection, userId, "Betty B456")
            val actualResult: Boolean = AdminDAO.approveChangeRequest(connection, selectedRequestId, userId)
            assertTrue(actualResult)
            assertEquals("accepted", getRequestStatus(connection, selectedRequestId))
            assertEquals(1, countRequestsByStatus(connection, userId, "pending"))
            assertEquals("Alice", getPassengerFullName(connection, userId))
        }
    }

    @Test
    fun rejectChangeRequestRejectsOnlySelectedPendingRequest(): Unit {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection: Connection ->
            createChangeRequestTables(connection)
            val userId: Int = insertUser(connection)
            val selectedRequestId: Long = insertChangeRequest(connection, userId, "Alice A123")
            insertChangeRequest(connection, userId, "Betty B456")
            val actualResult: Boolean = AdminDAO.rejectChangeRequest(connection, selectedRequestId, userId)
            assertTrue(actualResult)
            assertEquals("rejected", getRequestStatus(connection, selectedRequestId))
            assertEquals(1, countRequestsByStatus(connection, userId, "pending"))
        }
    }

    private fun createChangeRequestTables(connection: Connection): Unit {
        connection.createStatement().use { statement ->
            statement.executeUpdate("CREATE TABLE users (user_id INTEGER PRIMARY KEY, email TEXT NOT NULL)")
            statement.executeUpdate("CREATE TABLE bookings (booking_id INTEGER PRIMARY KEY, user_id INTEGER NOT NULL)")
            statement.executeUpdate("CREATE TABLE booking_passengers (booking_id INTEGER NOT NULL, full_name TEXT NOT NULL, id_number TEXT NOT NULL)")
            statement.executeUpdate("CREATE TABLE change_requests (user_id INTEGER NOT NULL, change_to TEXT NOT NULL, change_type TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'pending')")
        }
    }

    private fun insertUser(connection: Connection): Int {
        val userId: Int = 10
        connection.prepareStatement("INSERT INTO users (user_id, email) VALUES (?, ?)").use { statement ->
            statement.setInt(1, userId)
            statement.setString(2, "passenger@example.com")
            statement.executeUpdate()
        }
        return userId
    }

    private fun insertBookingPassenger(connection: Connection, userId: Int): Unit {
        connection.prepareStatement("INSERT INTO bookings (booking_id, user_id) VALUES (?, ?)").use { statement ->
            statement.setInt(1, 20)
            statement.setInt(2, userId)
            statement.executeUpdate()
        }
        connection.prepareStatement("INSERT INTO booking_passengers (booking_id, full_name, id_number) VALUES (?, ?, ?)").use { statement ->
            statement.setInt(1, 20)
            statement.setString(2, "Original")
            statement.setString(3, "O000")
            statement.executeUpdate()
        }
    }

    private fun insertChangeRequest(connection: Connection, userId: Int, changeTo: String): Long {
        connection.prepareStatement("INSERT INTO change_requests (user_id, change_to, change_type, status) VALUES (?, ?, 'personal_info', 'pending')").use { statement ->
            statement.setInt(1, userId)
            statement.setString(2, changeTo)
            statement.executeUpdate()
        }
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT last_insert_rowid()").use { resultSet ->
                return resultSet.getLong(1)
            }
        }
    }

    private fun getRequestStatus(connection: Connection, requestId: Long): String {
        connection.prepareStatement("SELECT status FROM change_requests WHERE rowid = ?").use { statement ->
            statement.setLong(1, requestId)
            statement.executeQuery().use { resultSet ->
                resultSet.next()
                return resultSet.getString("status")
            }
        }
    }

    private fun countRequestsByStatus(connection: Connection, userId: Int, status: String): Int {
        connection.prepareStatement("SELECT COUNT(*) AS request_count FROM change_requests WHERE user_id = ? AND status = ?").use { statement ->
            statement.setInt(1, userId)
            statement.setString(2, status)
            statement.executeQuery().use { resultSet ->
                resultSet.next()
                return resultSet.getInt("request_count")
            }
        }
    }

    private fun getPassengerFullName(connection: Connection, userId: Int): String {
        connection.prepareStatement(
            """
            SELECT bp.full_name
            FROM booking_passengers bp
            JOIN bookings b ON b.booking_id = bp.booking_id
            WHERE b.user_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setInt(1, userId)
            statement.executeQuery().use { resultSet ->
                resultSet.next()
                return resultSet.getString("full_name")
            }
        }
    }
}
