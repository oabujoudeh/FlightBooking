package com.flightbooking

data class BookingCancellationNotification(
    val bookingId: Int,
    val recipientEmail: String,
    val flightSummary: String,
)

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

data class FlightNotificationEmail(
    val to: String,
    val subject: String,
    val body: String,
)
