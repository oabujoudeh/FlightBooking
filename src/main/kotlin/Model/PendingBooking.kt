package com.flightbooking

import kotlinx.serialization.Serializable


@Serializable
data class PendingBooking(
    val flightIds: List<Int>,
    val totalPrice: Double,
    val passengers: List<Map<String, String>>,
    val contactEmail: String,
    val contactPhone: String,
)