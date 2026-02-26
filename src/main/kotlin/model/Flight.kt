package modle
import java.time.LocalDateTime

data class Flight(
    val flight_id: Int,
    val route_id: Int,
    val departure_datetime: LocalDateTime,
    val arrival_datetime: LocalDateTime,
    val departure_terminal: String,
    val arrival_terminal: String,
    val status: String, // delayed; cancelled...
    val price: Double,
    val award_available: Boolean
)