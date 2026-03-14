package com.flightbooking

object Utils {
    fun formatDuration(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return if (mins == 0) "${hours}h" else "${hours}h ${mins}m"
    }

    fun flightToMap(f: Flight): Map<String, Any> = mapOf(
        "flightNumber"      to f.flightNumber,
        "departureAirport"  to f.departureAirportName,
        "arrivalAirport"    to f.arrivalAirportName,
        "departureTerminal" to f.departureTerminal,
        "arrivalTerminal"   to f.arrivalTerminal,
        "departureTime"     to f.departureTime.toString(),
        "arrivalTime"       to f.arrivalTime.toString(),
        "duration"          to formatDuration(f.durationMinutes),
        "price"             to f.price,
        "date"              to f.departureDate.toString(),
        "flightId"          to f.flightId,
        "arrivalDayOffset"  to f.arrivalDayOffset
    )

    fun connectingFlightToMap(cf: ConnectingFlight): Map<String, Any> = mapOf(
        "leg1DepartureTime"    to cf.leg1.departureTime.toString(),
        "leg1ArrivalTime"      to cf.leg1.arrivalTime.toString(),
        "leg1DepartureAirport" to cf.leg1.departureAirportName,
        "leg1ArrivalAirport"   to cf.leg1.arrivalAirportName,
        "leg2DepartureTime"    to cf.leg2.departureTime.toString(),
        "leg2ArrivalTime"      to cf.leg2.arrivalTime.toString(),
        "leg2ArrivalAirport"   to cf.leg2.arrivalAirportName,
        "layoverMinutes"       to cf.layoverMinutes,
        "totalDuration"        to formatDuration(cf.totalDurationMinutes),
        "price"                to cf.totalPrice,
        "leg1FlightId"         to cf.leg1.flightId,
        "leg2FlightId"         to cf.leg2.flightId,
        "leg2ArrivalDayOffset" to cf.leg2.arrivalDayOffset
    )
}