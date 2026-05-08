package com.flightbooking

import kotlin.test.Test
import kotlin.test.assertEquals

class TemplateDataTest {
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
