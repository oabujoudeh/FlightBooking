package com.flightbooking

object FlightNotificationService {
    fun buildBookingCancellationEmail(notification: BookingCancellationNotification): FlightNotificationEmail {
        val subject: String = "Your EAJO Air booking has been cancelled"
        val body: String = """
            Hello,

            Your EAJO Air Booking #${notification.bookingId} has been cancelled successfully.

            Flight details:
            ${notification.flightSummary}

            Thank you for choosing EAJO Air.
        """.trimIndent()
        return FlightNotificationEmail(
            to = notification.recipientEmail,
            subject = subject,
            body = body,
        )
    }

    fun buildFlightStatusUpdateEmails(notification: FlightStatusNotification): List<FlightNotificationEmail> {
        val subject: String = "EAJO Air flight ${notification.flightNumber} is now ${notification.newStatus}"
        val body: String = """
            Hello,

            Your flight ${notification.flightNumber} from ${notification.originCity} to ${notification.destinationCity} on ${notification.flightDate} has been updated.

            Previous status: ${notification.oldStatus}
            New status: ${notification.newStatus}

            Please check your booking for the latest travel information.
        """.trimIndent()
        return notification.recipientEmails
            .map { email -> email.trim() }
            .filter { email -> email.isNotEmpty() }
            .distinctBy { email -> email.lowercase() }
            .map { email ->
                FlightNotificationEmail(
                    to = email,
                    subject = subject,
                    body = body,
                )
            }
    }

    fun sendBookingCancellation(notification: BookingCancellationNotification): Unit {
        val email: FlightNotificationEmail = buildBookingCancellationEmail(notification)
        EmailService.sendEmail(
            to = email.to,
            subject = email.subject,
            body = email.body,
        )
    }

    fun sendFlightStatusUpdate(notification: FlightStatusNotification): Unit {
        val emails: List<FlightNotificationEmail> = buildFlightStatusUpdateEmails(notification)
        emails.forEach { email ->
            EmailService.sendEmail(
                to = email.to,
                subject = email.subject,
                body = email.body,
            )
        }
    }
}
