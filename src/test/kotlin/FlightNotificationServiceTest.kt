package com.flightbooking

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * Unit tests for [FlightNotificationService].
 *
 * These tests verify that notification emails are generated correctly for booking
 * cancellations and flight status updates, including recipient handling, subject
 * formatting, and email body content.
 */
class FlightNotificationServiceTest {
    /**
     * Verifies that a booking cancellation email is created using the booking
     * contact email address and contains the expected booking and flight details.
     */
    @Test
    fun buildBookingCancellationEmailUsesBookingContactEmail() {
        val inputNotification: BookingCancellationNotification =
            BookingCancellationNotification(
                bookingId = 42,
                recipientEmail = "booking-contact@example.com",
                flightSummary = "EA101 Leeds to London on 2026-05-08",
            )
        val actualEmail: FlightNotificationEmail =
            FlightNotificationService.buildBookingCancellationEmail(inputNotification)
        assertEquals("booking-contact@example.com", actualEmail.to)
        assertEquals("Your EAJO Air booking has been cancelled", actualEmail.subject)
        assertContains(actualEmail.body, "Booking #42")
        assertContains(actualEmail.body, "EA101 Leeds to London on 2026-05-08")
    }

    /**
     * Verifies that duplicate recipient email addresses are removed when generating
     * flight status update emails, while preserving unique recipients and correct
     * email content.
     */
    @Test
    fun buildFlightStatusUpdateEmailsDeduplicatesContactAndAccountEmails() {
        val inputNotification: FlightStatusNotification =
            FlightStatusNotification(
                flightId = 9,
                flightNumber = "EA202",
                flightDate = "2026-05-08",
                originCity = "Leeds",
                destinationCity = "Paris",
                oldStatus = "Scheduled",
                newStatus = "Delayed",
                recipientEmails = listOf("Passenger@example.com", "passenger@example.com", "account@example.com"),
            )
        val actualEmails: List<FlightNotificationEmail> =
            FlightNotificationService.buildFlightStatusUpdateEmails(inputNotification)
        assertEquals(listOf("Passenger@example.com", "account@example.com"), actualEmails.map { email -> email.to })
        assertEquals("EAJO Air flight EA202 is now Delayed", actualEmails.first().subject)
        assertContains(actualEmails.first().body, "Scheduled")
        assertContains(actualEmails.first().body, "Delayed")
        assertContains(actualEmails.first().body, "Leeds to Paris")
    }
}
