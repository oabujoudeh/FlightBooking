package com.flightbooking

/**
 * Stores the seat setup for one cabin section.
 *
 * @property seatClass Cabin class identifier, e.g. "first", "business", "economy".
 * @property rows Row numbers occupied by this cabin.
 * @property layout Seat groups per side of the aisle, e.g. `[["A","B"], ["C","D","E","F"], ["G","H"]]` for 2-4-2.
 */
data class CabinConfig(
    val seatClass: String,
    val rows: IntRange,
    val layout: List<List<String>>,
)

/**
 * Stores the layout details for one deck of the plane.
 *
 * @property deckName Human-readable deck label, e.g. "Main Deck" or "Upper Deck".
 * @property cabins Ordered list of cabin sections on this deck.
 * @property exitRows Row numbers that are designated emergency-exit rows.
 * @property bassinetRows Row numbers where bassinet positions are available.
 */
data class DeckConfig(
    val deckName: String,
    val cabins: List<CabinConfig>,
    val exitRows: Set<Int>,
    val bassinetRows: Set<Int>,
)

/**
 * Stores the seat layout setup for an aircraft.
 *
 * @property decks Ordered list of decks, lower deck first.
 */
data class AircraftConfig(
    val decks: List<DeckConfig>,
)

/**
 * Registry of predefined seat-map configurations keyed by aircraft type name.
 *
 * Falls back to the Boeing 737 configuration for unknown aircraft types.
 */
object AircraftConfigs {
    val configs: Map<String, AircraftConfig> =
        mapOf(
            // ── Single-aisle ────────────────────────────────────────────────────
            // Business rows 1-5 (2-2), Economy rows 6-30 (3-3)
            "Airbus A320" to
                AircraftConfig(
                    decks =
                        listOf(
                            DeckConfig(
                                deckName = "Main Deck",
                                exitRows = setOf(12, 13),
                                bassinetRows = setOf(6),
                                cabins =
                                    listOf(
                                        CabinConfig("business", 1..5, layout = listOf(listOf("A", "C"), listOf("D", "F"))), // 2-2
                                        CabinConfig("economy", 6..30, layout = listOf(listOf("A", "B", "C"), listOf("D", "E", "F"))), // 3-3
                                    ),
                            ),
                        ),
                ),
            // Business rows 1-5 (2-2), Economy rows 6-35 (3-3)
            "Airbus A321" to
                AircraftConfig(
                    decks =
                        listOf(
                            DeckConfig(
                                deckName = "Main Deck",
                                exitRows = setOf(14, 15),
                                bassinetRows = setOf(6),
                                cabins =
                                    listOf(
                                        CabinConfig("business", 1..5, layout = listOf(listOf("A", "C"), listOf("D", "F"))), // 2-2
                                        CabinConfig("economy", 6..35, layout = listOf(listOf("A", "B", "C"), listOf("D", "E", "F"))), // 3-3
                                    ),
                            ),
                        ),
                ),
            // Business rows 1-5 (2-2), Economy rows 6-32 (3-3)
            "Boeing 737" to
                AircraftConfig(
                    decks =
                        listOf(
                            DeckConfig(
                                deckName = "Main Deck",
                                exitRows = setOf(13, 14),
                                bassinetRows = setOf(6),
                                cabins =
                                    listOf(
                                        CabinConfig("business", 1..5, layout = listOf(listOf("A", "C"), listOf("D", "F"))), // 2-2
                                        CabinConfig("economy", 6..32, layout = listOf(listOf("A", "B", "C"), listOf("D", "E", "F"))), // 3-3
                                    ),
                            ),
                        ),
                ),
            // Business rows 1-3 (2-2), Economy rows 4-20 (2-2)
            "Embraer E175" to
                AircraftConfig(
                    decks =
                        listOf(
                            DeckConfig(
                                deckName = "Main Deck",
                                exitRows = setOf(9, 10),
                                bassinetRows = setOf(6),
                                cabins =
                                    listOf(
                                        CabinConfig("business", 1..3, layout = listOf(listOf("A", "B"), listOf("C", "D"))), // 2-2
                                        CabinConfig("economy", 4..20, layout = listOf(listOf("A", "B"), listOf("C", "D"))), // 2-2
                                    ),
                            ),
                        ),
                ),
            // ── Wide-body ────────────────────────────────────────────────────────
            // Business rows 1-5 (2-2), Economy rows 6-36 (3-3)
            "Boeing 757" to
                AircraftConfig(
                    decks =
                        listOf(
                            DeckConfig(
                                deckName = "Main Deck",
                                exitRows = setOf(15, 16),
                                bassinetRows = setOf(1),
                                cabins =
                                    listOf(
                                        CabinConfig("business", 1..5, layout = listOf(listOf("A", "C"), listOf("D", "F"))), // 2-2
                                        CabinConfig("economy", 6..36, layout = listOf(listOf("A", "B", "C"), listOf("D", "E", "F"))), // 3-3
                                    ),
                            ),
                        ),
                ),
            // Business rows 1-12 (2-2-2), Economy rows 13-40 (2-4-2)
            "Airbus A330" to
                AircraftConfig(
                    decks =
                        listOf(
                            DeckConfig(
                                deckName = "Main Deck",
                                exitRows = setOf(15, 16, 30),
                                bassinetRows = setOf(1, 6),
                                cabins =
                                    listOf(
                                        CabinConfig(
                                            "business",
                                            1..12,
                                            layout = listOf(listOf("A", "C"), listOf("D", "G"), listOf("J", "K")),
                                        ), // 2-2-2
                                        CabinConfig(
                                            "economy",
                                            13..40,
                                            layout = listOf(listOf("A", "C"), listOf("D", "E", "F", "G"), listOf("H", "J")),
                                        ), // 2-4-2
                                    ),
                            ),
                        ),
                ),
            // Business rows 1-10 (2-2-2), Economy rows 11-38 (2-3-2)
            "Boeing 767" to
                AircraftConfig(
                    decks =
                        listOf(
                            DeckConfig(
                                deckName = "Main Deck",
                                exitRows = setOf(16, 17, 28),
                                bassinetRows = setOf(1, 5),
                                cabins =
                                    listOf(
                                        CabinConfig(
                                            "business",
                                            1..10,
                                            layout = listOf(listOf("A", "C"), listOf("D", "G"), listOf("J", "K")),
                                        ), // 2-2-2
                                        CabinConfig(
                                            "economy",
                                            11..38,
                                            layout = listOf(listOf("A", "C"), listOf("D", "E", "F"), listOf("G", "J")),
                                        ), // 2-3-2
                                    ),
                            ),
                        ),
                ),
            // Business rows 1-11 (2-2-2), Economy rows 12-38 (3-3-3)
            "Boeing 787" to
                AircraftConfig(
                    decks =
                        listOf(
                            DeckConfig(
                                deckName = "Main Deck",
                                exitRows = setOf(15, 16, 28),
                                bassinetRows = setOf(1, 4),
                                cabins =
                                    listOf(
                                        CabinConfig(
                                            "business",
                                            1..11,
                                            layout = listOf(listOf("A", "C"), listOf("D", "G"), listOf("J", "K")),
                                        ), // 2-2-2
                                        CabinConfig(
                                            "economy",
                                            12..38,
                                            layout = listOf(listOf("A", "B", "C"), listOf("D", "E", "F", "G"), listOf("H", "J", "K")),
                                        ), // 3-3-3
                                    ),
                            ),
                        ),
                ),
            // First rows 1-4 (1-2-1), Business rows 5-13 (2-2-2), Economy rows 14-42 (3-4-3)
            "Boeing 777" to
                AircraftConfig(
                    decks =
                        listOf(
                            DeckConfig(
                                deckName = "Main Deck",
                                exitRows = setOf(17, 18, 30),
                                bassinetRows = setOf(1, 5),
                                cabins =
                                    listOf(
                                        CabinConfig("first", 1..4, layout = listOf(listOf("A"), listOf("D", "G"), listOf("K"))), // 1-2-1
                                        CabinConfig(
                                            "business",
                                            5..13,
                                            layout = listOf(listOf("A", "C"), listOf("D", "G"), listOf("J", "K")),
                                        ), // 2-2-2
                                        CabinConfig(
                                            "economy",
                                            14..42,
                                            layout = listOf(listOf("A", "B", "C"), listOf("D", "E", "F", "G"), listOf("H", "J", "K")),
                                        ), // 3-4-3
                                    ),
                            ),
                        ),
                ),
            "Boeing 747" to
                AircraftConfig(
                    decks =
                        listOf(
                            DeckConfig(
                                deckName = "Main Deck",
                                exitRows = setOf(18, 19, 32),
                                bassinetRows = setOf(1, 6),
                                cabins =
                                    listOf(
                                        CabinConfig("first", 1..5, layout = listOf(listOf("A"), listOf("D", "G"), listOf("K"))), // 1-2-1
                                        CabinConfig(
                                            "business",
                                            6..14,
                                            layout = listOf(listOf("A", "C"), listOf("D", "G"), listOf("J", "K")),
                                        ), // 2-2-2
                                        CabinConfig(
                                            "economy",
                                            15..45,
                                            layout = listOf(listOf("A", "B", "C"), listOf("D", "E", "F", "G"), listOf("H", "J", "K")),
                                        ), // 3-4-3
                                    ),
                            ),
                            DeckConfig(
                                deckName = "Upper Deck",
                                exitRows = setOf(5),
                                bassinetRows = emptySet(),
                                cabins =
                                    listOf(
                                        CabinConfig("business", 1..10, layout = listOf(listOf("A", "C"), listOf("D", "G"))), // 2-2
                                    ),
                            ),
                        ),
                ),
            /* Main: First rows 1-5 (1-2-1), Business rows 6-15 (2-2-2), Economy rows 16-50 (3-4-3)
             * Upper: Business rows 1-20 (2-2) */
            "Airbus A380" to
                AircraftConfig(
                    decks =
                        listOf(
                            DeckConfig(
                                deckName = "Main Deck",
                                exitRows = setOf(15, 28),
                                bassinetRows = setOf(1, 6),
                                cabins =
                                    listOf(
                                        CabinConfig("first", 1..5, layout = listOf(listOf("A"), listOf("D", "G"), listOf("K"))), // 1-2-1
                                        CabinConfig(
                                            "business",
                                            6..15,
                                            layout = listOf(listOf("A", "C"), listOf("D", "G"), listOf("J", "K")),
                                        ), // 2-2-2
                                        CabinConfig(
                                            "economy",
                                            16..50,
                                            layout = listOf(listOf("A", "B", "C"), listOf("D", "E", "F", "G"), listOf("H", "J", "K")),
                                        ), // 3-4-3
                                    ),
                            ),
                            DeckConfig(
                                deckName = "Upper Deck",
                                exitRows = setOf(10),
                                bassinetRows = emptySet(),
                                cabins =
                                    listOf(
                                        CabinConfig("business", 1..20, layout = listOf(listOf("A", "C"), listOf("D", "G"))), // 2-2
                                    ),
                            ),
                        ),
                ),
        )

    /**
     * Returns the [AircraftConfig] for the given [aircraftType].
     *
     * Falls back to the Boeing 737 configuration if [aircraftType] is not recognised.
     *
     * @param aircraftType Aircraft model name, e.g. "Airbus A320".
     * @return The matching [AircraftConfig], or the Boeing 737 default.
     */
    fun getConfig(aircraftType: String): AircraftConfig = configs[aircraftType] ?: configs["Boeing 737"]!!
}
