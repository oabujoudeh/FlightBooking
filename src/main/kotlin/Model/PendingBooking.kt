package com.flightbooking

import kotlinx.serialization.Serializable

/**
 * Holds the details of a booking that is awaiting confirmation.
 *
 * @property flightIds the IDs of the flights included in this booking
 * @property totalPrice the total price for all passengers and flights
 * @property passengers a list of passenger details, each represented as a map of field names to values
 * @property contactEmail the email address to use for booking notifications
 * @property contactPhone the phone number to use for booking notifications
 * @property tripType the type of trip; defaults to "oneway"
 */
@Serializable
data class PendingBooking(
    val flightIds: List<Int>,
    val totalPrice: Double,
    val passengers: List<Map<String, String>>,
    val contactEmail: String,
    val contactPhone: String,
    val tripType: String = "oneway",
)
