package com.flightbooking


data class Seat(
    val seatId: Int,
    val flightId: Int,
    val seatNumber: String,
    val seatClass: String,
    val isOccupied: Boolean
) {
    val row: Int get() = seatNumber.dropLast(1).toInt()
    val col: String get() = seatNumber.takeLast(1)
}