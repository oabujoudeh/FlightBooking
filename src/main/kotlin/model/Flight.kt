package model
import kotlinx.serialization.Serializable


@Serializable
data class Flight(
    val flight_id: Int,
    val route_id: Int,
    val departure_datetime: String,
    val arrival_datetime: String,
    val departure_terminal: String,
    val arrival_terminal: String,
    val status: String, // delayed; cancelled...
    val price: Double,
    val award_available: Boolean
)