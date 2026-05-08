package com.flightbooking

import kotlinx.serialization.Serializable

/**
 * Holds the details of a reschedule request that is awaiting confirmation.
 *
 * @property bookingId the ID of the booking to be rescheduled
 * @property newFlightIds the IDs of the new flights replacing the original ones
 * @property newTotalPrice the updated total price after rescheduling
 * @property passengerSeatSelections maps each passenger ID to their comma-separated seat selections (e.g. "3A,7A,16A,8A")
 */
@Serializable
data class PendingReschedule(
    val bookingId: Int,
    val newFlightIds: List<Int>,
    val newTotalPrice: Double,
    val passengerSeatSelections: Map<Int, String>
)