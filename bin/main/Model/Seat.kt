package com.flightbooking

/**
 * Stores the details for one seat on a flight.
 */
data class Seat(
    val seatId: Int,
    val flightId: Int,
    val seatNumber: String,
    val seatClass: String,
    val isOccupied: Boolean
) {
    /**
    * Gets the row number from the seat number.
    */
    val row: Int get() = seatNumber.dropLast(1).toInt()
    /**
    * Gets the column letter from the seat number.
    */
    val col: String get() = seatNumber.takeLast(1)
}