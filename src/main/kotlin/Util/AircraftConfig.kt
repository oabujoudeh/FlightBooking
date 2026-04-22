package com.flightbooking

/**
* Stores the seat setup for one cabin section.
*/
data class CabinConfig(
    val seatClass: String,
    val rows: IntRange,
    val layout: List<List<String>>  // e.g. [["A","B"], ["C","D","E","F"], ["G","H"]] is 2-4-2
)

/**
* Stores the layout details for one deck of the plane.
*/
data class DeckConfig(
    val deckName: String,
    val cabins: List<CabinConfig>,
    val exitRows: Set<Int>,
    val bassinetRows: Set<Int>
)

/**
* Stores the seat layout setup for an aircraft.
*/
data class AircraftConfig(
    val decks: List<DeckConfig>
)



object AircraftConfigs {

    val configs: Map<String, AircraftConfig> = mapOf(

        // single-aisle
        "Airbus A320" to AircraftConfig(decks = listOf(DeckConfig(
            deckName = "Main Deck",
            exitRows = setOf(12, 13),
            bassinetRows = setOf(1),
            cabins = listOf(
                CabinConfig("business", 1..4,   layout = listOf(listOf("A","C"), listOf("D","F"))), // 2-2
                CabinConfig("economy",  5..30,  layout = listOf(listOf("A","B","C"), listOf("D","E","F")))  // 3-3
            )
        ))),

        "Airbus A321" to AircraftConfig(decks = listOf(DeckConfig(
            deckName = "Main Deck",
            exitRows = setOf(14, 15),
            bassinetRows = setOf(1),
            cabins = listOf(
                CabinConfig("business", 1..5,   layout = listOf(listOf("A","B","C"), listOf("D","E","F"))), // 3-3
                CabinConfig("economy",  6..35,  layout = listOf(listOf("A","B","C"), listOf("D","E","F")))  // 3-3
            )
        ))),

        "Boeing 737" to AircraftConfig(decks = listOf(DeckConfig(
            deckName = "Main Deck",
            exitRows = setOf(13, 14),
            bassinetRows = setOf(1),
            cabins = listOf(
                CabinConfig("business", 1..4,   layout = listOf(listOf("A","B","C"), listOf("D","E","F"))), // 3-3
                CabinConfig("economy",  5..32,  layout = listOf(listOf("A","B","C"), listOf("D","E","F")))  // 3-3
            )
        ))),

        "Boeing 757" to AircraftConfig(decks = listOf(DeckConfig(
            deckName = "Main Deck",
            exitRows = setOf(15, 16),
            bassinetRows = setOf(1),
            cabins = listOf(
                CabinConfig("business", 1..5,   layout = listOf(listOf("A","B","C"), listOf("D","E","F"))), // 3-3
                CabinConfig("economy",  6..36,  layout = listOf(listOf("A","B","C"), listOf("D","E","F")))  // 3-3
            )
        ))),

        "Embraer E175" to AircraftConfig(decks = listOf(DeckConfig(
            deckName = "Main Deck",
            exitRows = setOf(9, 10),
            bassinetRows = setOf(1),
            cabins = listOf(
                CabinConfig("business", 1..3,   layout = listOf(listOf("A"), listOf("C"))), // 1-1
                CabinConfig("economy",  4..20,  layout = listOf(listOf("A","B"), listOf("C","D")))  // 2-2
            )
        ))),

        // wide-body
        "Airbus A330" to AircraftConfig(decks = listOf(DeckConfig(
            deckName = "Main Deck",
            exitRows = setOf(15, 16, 30),
            bassinetRows = setOf(1, 6),
            cabins = listOf(
                CabinConfig("first",    1..4,   layout = listOf(listOf("A","C"), listOf("G","K"))),               // 2-2
                CabinConfig("business", 5..12,  layout = listOf(listOf("A","C"), listOf("D","G"), listOf("J","K"))), // 2-2-2
                CabinConfig("economy",  13..40, layout = listOf(listOf("A","C"), listOf("D","E","F","G"), listOf("H","J"))) // 2-4-2
            )
        ))),

        "Boeing 767" to AircraftConfig(decks = listOf(DeckConfig(
            deckName = "Main Deck",
            exitRows = setOf(16, 17, 28),
            bassinetRows = setOf(1, 5),
            cabins = listOf(
                CabinConfig("first",    1..3,   layout = listOf(listOf("A","C"), listOf("G","K"))),               // 2-2
                CabinConfig("business", 4..10,  layout = listOf(listOf("A","C"), listOf("D","G"), listOf("J","K"))), // 2-2-2
                CabinConfig("economy",  11..38, layout = listOf(listOf("A","C"), listOf("D","E","F"), listOf("G","J"))) // 2-3-2
            )
        ))),

        "Boeing 787" to AircraftConfig(decks = listOf(DeckConfig(
            deckName = "Main Deck",
            exitRows = setOf(15, 16, 28),
            bassinetRows = setOf(1, 4),
            cabins = listOf(
                CabinConfig("first",    1..3,   layout = listOf(listOf("A","C"), listOf("G","K"))),               // 2-2
                CabinConfig("business", 4..11,  layout = listOf(listOf("A","C"), listOf("D","G"), listOf("J","K"))), // 2-2-2
                CabinConfig("economy",  12..38, layout = listOf(listOf("A","B","C"), listOf("D","E","F","G"), listOf("H","J","K"))) // 3-3-3
            )
        ))),

        "Boeing 777" to AircraftConfig(decks = listOf(DeckConfig(
            deckName = "Main Deck",
            exitRows = setOf(17, 18, 30),
            bassinetRows = setOf(1, 5),
            cabins = listOf(
                CabinConfig("first",    1..4,   layout = listOf(listOf("A"), listOf("D","G"), listOf("K"))),        // 1-2-1
                CabinConfig("business", 5..13,  layout = listOf(listOf("A","C"), listOf("D","G"), listOf("J","K"))), // 2-2-2
                CabinConfig("economy",  14..42, layout = listOf(listOf("A","B","C"), listOf("D","E","F","G"), listOf("H","J","K"))) // 3-4-3
            )
        ))),

        // double-decker
        "Boeing 747" to AircraftConfig(decks = listOf(
            DeckConfig(
                deckName = "Main Deck",
                exitRows = setOf(18, 19, 32),
                bassinetRows = setOf(1, 6),
                cabins = listOf(
                    CabinConfig("first",    1..5,   layout = listOf(listOf("A"), listOf("D","G"), listOf("K"))),        // 1-2-1
                    CabinConfig("business", 6..14,  layout = listOf(listOf("A","C"), listOf("D","G"), listOf("J","K"))), // 2-2-2
                    CabinConfig("economy",  15..45, layout = listOf(listOf("A","B","C"), listOf("D","E","F","G"), listOf("H","J","K"))) // 3-4-3
                )
            ),
            DeckConfig(
                deckName = "Upper Deck",
                exitRows = setOf(5),
                bassinetRows = emptySet(),
                cabins = listOf(
                    CabinConfig("business", 1..10, layout = listOf(listOf("A","C"), listOf("D","G"))) // 2-2
                )
            )
        )),

        "Airbus A380" to AircraftConfig(decks = listOf(
            DeckConfig(
                deckName = "Main Deck",
                exitRows = setOf(15, 28),
                bassinetRows = setOf(1, 6),
                cabins = listOf(
                    CabinConfig("first",    1..5,   layout = listOf(listOf("A"), listOf("D","G"), listOf("K"))),        // 1-2-1
                    CabinConfig("business", 6..15,  layout = listOf(listOf("A","C"), listOf("D","G"), listOf("J","K"))), // 2-2-2
                    CabinConfig("economy",  16..50, layout = listOf(listOf("A","B","C"), listOf("D","E","F","G"), listOf("H","J","K"))) // 3-4-3
                )
            ),
            DeckConfig(
                deckName = "Upper Deck",
                exitRows = setOf(10),
                bassinetRows = emptySet(),
                cabins = listOf(
                    CabinConfig("business", 1..20, layout = listOf(listOf("A","C"), listOf("D","G"))) // 2-2
                )
            )
        ))
    )

    fun getConfig(aircraftType: String): AircraftConfig {
        return configs[aircraftType] ?: configs["Boeing 737"]!!
    }
}