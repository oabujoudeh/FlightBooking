package com.flightbooking

/**
 * Holds the details needed to notify a passenger that their booking has been cancelled.
 *
 * @property bookingId the ID of the cancelled booking
 * @property recipientEmail the email address of the passenger to notify
 * @property flightSummary a human-readable summary of the cancelled flight details
 */
data class BookingCancellationNotification(
    val bookingId: Int,
    val recipientEmail: String,
    val flightSummary: String,
)

/**
 * Holds the details needed to notify passengers of a flight status change.
 *
 * @property flightId the unique identifier of the affected flight
 * @property flightNumber the flight number (e.g. "EJ101")
 * @property flightDate the date of the flight
 * @property originCity the city the flight departs from
 * @property destinationCity the city the flight arrives at
 * @property oldStatus the previous status of the flight
 * @property newStatus the updated status of the flight
 * @property recipientEmails the list of passenger email addresses to notify
 */
data class FlightStatusNotification(
    val flightId: Int,
    val flightNumber: String,
    val flightDate: String,
    val originCity: String,
    val destinationCity: String,
    val oldStatus: String,
    val newStatus: String,
    val recipientEmails: List<String>,
)

/**
 * Represents an outgoing notification email for a flight-related event.
 *
 * @property to the recipient email address
 * @property subject the subject line of the email
 * @property body the main text content of the email
 */
data class FlightNotificationEmail(
    val to: String,
    val subject: String,
    val body: String,
)