package com.flightbooking

import kotlinx.serialization.Serializable

@Serializable
data class PendingReschedule(
    val bookingId: Int,
    val newFlightIds: List<Int>,
    val newTotalPrice: Double,
    val passengerSeatSelections: Map<Int, String>  // passengerId -> comma-separated seats e.g. "3A,7A,16A,8A"
)