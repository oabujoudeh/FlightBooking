# API documentation

Base URL: 'http://localhost:8080/api'
All endpoints are relative to this base URL.


## 1. Search flights

End point: /search
Method: GET
Description: Search for available flights between two cities on a given date.
----------------------------------------
Query Parameters:

departureCity   (string)  Required
    The city where the flight departs.

arrivalCity     (string)  Required
    The destination city.

date            (string)  Required
    The departure date in format YYYY-MM-DD.
----------------------------------------
Example Request:
GET /api/search?departureCity=Manchester&arrivalCity=London&date=2026-03-20
----------------------------------------
Example Response:

[
  {
    "flightId": 1,
    "flightNumber": "EA101",
    "departureCity": "Leeds",
    "arrivalCity": "London",
    "departureTerminal": "T1",
    "arrivalTerminal": "T2",
    "departureDate": "2026-02-28",
    "departureTime": "07:00:00",
    "arrivalTime": "08:30:00",
    "price": 90.00
  },
  {
    "flightId": 2,
    "flightNumber": "EA102",
    "departureCity": "Leeds",
    "arrivalCity": "Paris",
    "departureTerminal": "T1",
    "arrivalTerminal": "T2",
    "departureDate": "2026-02-28",
    "departureTime": "08:30:00",
    "arrivalTime": "10:30:00",
    "price": 120.00
  }
]
