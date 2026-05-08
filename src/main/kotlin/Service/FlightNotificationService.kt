package com.flightbooking

object FlightNotificationService {

    /**
     * Builds a cancellation confirmation email for a booking.
     *
     * @param notification the cancellation notification containing booking and recipient details
     * @return a [FlightNotificationEmail] ready to be sent
     */
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

    /**
     * Builds a list of flight status update emails for all relevant recipients.
     *
     * Recipient emails are trimmed, filtered for empty values, and deduplicated
     * (case-insensitive) before the emails are constructed.
     *
     * @param notification the status notification containing flight and recipient details
     * @return a list of [FlightNotificationEmail] objects, one per unique recipient
     */
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

    /**
     * Sends a booking cancellation email to the recipient in the notification.
     *
     * @param notification the cancellation notification containing booking and recipient details
     */
    fun sendBookingCancellation(notification: BookingCancellationNotification): Unit {
        val email: FlightNotificationEmail = buildBookingCancellationEmail(notification)
        EmailService.sendEmail(
            to = email.to,
            subject = email.subject,
            body = email.body,
        )
    }

    /**
     * Sends flight status update emails to all recipients in the notification.
     *
     * @param notification the status notification containing flight and recipient details
     */
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