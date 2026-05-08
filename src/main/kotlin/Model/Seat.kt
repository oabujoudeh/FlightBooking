package com.flightbooking

/**
 * Stores the details for one seat on a flight.
 *
 * @property seatId the unique identifier for the seat
 * @property flightId the ID of the flight this seat belongs to
 * @property seatNumber the seat label combining row and column (e.g. "12A")
 * @property seatClass the cabin class of the seat (e.g. "economy", "business", "first")
 * @property isOccupied whether the seat is currently booked
 */
data class Seat(
    val seatId: Int,
    val flightId: Int,
    val seatNumber: String,
    val seatClass: String,
    val isOccupied: Boolean,
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
