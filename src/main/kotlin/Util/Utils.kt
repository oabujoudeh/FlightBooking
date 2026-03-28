package com.flightbooking


object Utils {
    fun formatDuration(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return if (mins == 0) "${hours}h" else "${hours}h ${mins}m"
    }

    // Return Map<String, Any?> to allow nullable prices
    fun flightToMap(f: Flight): Map<String, Any?> = mapOf(
        "flightNumber"      to f.flightNumber,
        "departureAirport"  to f.departureAirportName,
        "arrivalAirport"    to f.arrivalAirportName,
        "departureTerminal" to f.departureTerminal,
        "arrivalTerminal"   to f.arrivalTerminal,
        "departureTime"     to f.departureTime.toString(),
        "arrivalTime"       to f.arrivalTime.toString(),
        "duration"          to formatDuration(f.durationMinutes),
        "priceEconomy"      to f.priceEconomy,
        "priceBusiness"     to f.priceBusiness,
        "priceFirst"        to f.priceFirst,
        "date"              to f.departureDate.toString(),
        "flightId"          to f.flightId,
        "arrivalDayOffset"  to f.arrivalDayOffset
    )

    // Use if-checks to safely add prices for connecting flights
    fun connectingFlightToMap(cf: ConnectingFlight): Map<String, Any?> = mapOf(
        "leg1DepartureTime"    to cf.leg1.departureTime.toString(),
        "leg1ArrivalTime"      to cf.leg1.arrivalTime.toString(),
        "leg1DepartureAirport" to cf.leg1.departureAirportName,
        "leg1ArrivalAirport"   to cf.leg1.arrivalAirportName,
        "leg2DepartureTime"    to cf.leg2.departureTime.toString(),
        "leg2ArrivalTime"      to cf.leg2.arrivalTime.toString(),
        "leg2ArrivalAirport"   to cf.leg2.arrivalAirportName,
        "layoverMinutes"       to cf.layoverMinutes,
        "totalDuration"        to formatDuration(cf.totalDurationMinutes),
        
        // Sum prices only if both legs have the cabin available
        "priceEconomy" to if (cf.leg1.priceEconomy != null && cf.leg2.priceEconomy != null) 
                          cf.leg1.priceEconomy + cf.leg2.priceEconomy else null,
                          
        "priceBusiness" to if (cf.leg1.priceBusiness != null && cf.leg2.priceBusiness != null) 
                           cf.leg1.priceBusiness + cf.leg2.priceBusiness else null,
                           
        "priceFirst" to if (cf.leg1.priceFirst != null && cf.leg2.priceFirst != null) 
                        cf.leg1.priceFirst + cf.leg2.priceFirst else null,
                        
        "leg1FlightId"         to cf.leg1.flightId,
        "leg2FlightId"         to cf.leg2.flightId,
        "leg2ArrivalDayOffset" to cf.leg2.arrivalDayOffset
    )
}