package com.flightbooking

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for session template data generation.
 *
 * These tests verify that template data created for the user interface
 * correctly includes session information and user notifications.
 */
class TemplateDataTest {
    /**
    * Verifies that notifications are included in the generated template data
    * when the user is logged in.
    */
    @Test
    fun buildSessionTemplateDataIncludesNotificationsForLoggedInUser(): Unit {
        val inputSession: UserSession =
            UserSession(
                username = "passenger@example.com",
                loggedIn = true,
                isAdmin = false,
            )
        val inputNotifications: List<Map<String, Any>> =
            listOf(
                mapOf(
                    "content" to "Alice A123",
                    "type" to "personal_info",
                    "status" to "accepted",
                ),
            )
        val actualData: Map<String, Any> = buildSessionTemplateData(inputSession, inputNotifications)
        assertEquals(inputNotifications, actualData["notifications"])
    }
}
