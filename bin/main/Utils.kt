package com.flightbooking

object Utils {
    fun formatDuration(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return if (mins == 0) "${hours}h" else "${hours}h ${mins}m"
    }

    fun flightToMap(f: Flight): Map<String, Any> = mapOf(
        "flightNumber" to f.flightNumber,
        "departureAirport" to f.departureAirportName,
        "arrivalAirport" to f.arrivalAirportName,
        "departureTerminal" to f.departureTerminal,
        "arrivalTerminal" to f.arrivalTerminal,
        "departureTime" to f.departureTime.toString(),
        "arrivalTime" to f.arrivalTime.toString(),
        "duration" to formatDuration(f.durationMinutes),
        "price" to f.price,
        "date" to f.departureDate.toString(),
        "flightId" to f.flightId
    )
}