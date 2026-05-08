package com.flightbooking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for [AirportDAO].
 *
 * Covers [AirportDAO.searchAirport] and [AirportDAO.getLabel] across the
 * following dimensions: result structure, city vs. airport ordering, known
 * seed data, case-insensitive matching, edge-case inputs (empty, whitespace,
 * special characters, very long strings), and SQL-injection resistance.
 *
 * All tests run against the real SQLite database populated by the project seed
 * data. Known seed values used: Leeds/LBA, London/LHR, London/LGW,
 * Amsterdam/AMS, Edinburgh/EDI, Dublin/DUB.
 */
class AirportDAOTest {

    // ── searchAirport: result structure ────────────────────────────────────────

    /**
     * Verifies that every result map from [AirportDAO.searchAirport] contains
     * exactly the three required keys: `"label"`, `"value"`, and `"type"`.
     */
    @Test
    fun testSearchAirportResultMapsHaveRequiredKeys() {
        val results = AirportDAO.searchAirport("London")
        assertTrue(results.isNotEmpty(), "Expected at least one result for 'London'")
        for (entry in results) {
            assertTrue(entry.containsKey("label"), "Missing key 'label' in $entry")
            assertTrue(entry.containsKey("value"), "Missing key 'value' in $entry")
            assertTrue(entry.containsKey("type"), "Missing key 'type' in $entry")
        }
    }

    /**
     * Verifies that the `"type"` field in each result is either `"city"` or `"airport"`.
     */
    @Test
    fun testSearchAirportTypeFieldIsValidEnum() {
        val results = AirportDAO.searchAirport("a")
        for (entry in results) {
            val type = entry["type"]
            assertTrue(
                type == "city" || type == "airport",
                "type must be 'city' or 'airport', got '$type'"
            )
        }
    }

    /**
     * Verifies that city entries appear before airport entries in the result list,
     * matching the two-pass query ordering described in the KDoc.
     */
    @Test
    fun testSearchAirportCitiesAppearBeforeAirports() {
        val results = AirportDAO.searchAirport("London")
        val types = results.map { it["type"] }
        val firstAirportIndex = types.indexOf("airport")
        val lastCityIndex = types.lastIndexOf("city")
        if (firstAirportIndex != -1 && lastCityIndex != -1) {
            assertTrue(
                lastCityIndex < firstAirportIndex,
                "All city results must precede airport results"
            )
        }
    }

    /**
     * Verifies that the list always contains no more than 15 entries
     * (city query LIMIT 5 + airport query LIMIT 10).
     */
    @Test
    fun testSearchAirportResultCountDoesNotExceedLimit() {
        val results = AirportDAO.searchAirport("a")
        assertTrue(results.size <= 15, "Total results must not exceed 15 (5 cities + 10 airports)")
    }

    // ── searchAirport: city results ────────────────────────────────────────────

    /**
     * Verifies that searching for a known city name ("Leeds") returns a city entry
     * whose `"value"` is the bare city name and whose `"label"` contains
     * "(all airports)".
     */
    @Test
    fun testSearchAirportKnownCityReturnsCityEntry() {
        val results = AirportDAO.searchAirport("Leeds")
        val cityEntry = results.find { it["type"] == "city" }
        assertNotNull(cityEntry, "Expected a city entry for 'Leeds'")
        assertEquals("Leeds", cityEntry!!["value"])
        assertTrue(cityEntry["label"]!!.contains("all airports"), "City label must contain 'all airports'")
    }

    /**
     * Verifies that cities with multiple airports (London = LHR + LGW) each generate
     * only a single city entry (DISTINCT in the city query).
     */
    @Test
    fun testSearchAirportMultiAirportCityHasOneCityEntry() {
        val results = AirportDAO.searchAirport("London")
        val cityEntries = results.filter { it["type"] == "city" && it["value"] == "London" }
        assertEquals(1, cityEntries.size, "London should produce exactly one city entry")
    }

    // ── searchAirport: airport results ─────────────────────────────────────────

    /**
     * Verifies that the IATA code "LBA" is found as an airport result and that
     * its label follows the "City - Name (Code)" format.
     */
    @Test
    fun testSearchAirportByIataCodeReturnsAirportEntry() {
        val results = AirportDAO.searchAirport("LBA")
        val airportEntry = results.find { it["type"] == "airport" && it["value"] == "LBA" }
        assertNotNull(airportEntry, "Expected an airport entry for IATA code LBA")
        val label = airportEntry!!["label"]!!
        assertTrue(label.contains("Leeds"), "Label must contain city name")
        assertTrue(label.contains("LBA"), "Label must contain IATA code")
        assertTrue(label.contains("(") && label.contains(")"), "Label must wrap code in parentheses")
    }

    /**
     * Verifies that searching by partial airport name ("Heathrow") returns an airport
     * entry for LHR.
     */
    @Test
    fun testSearchAirportByPartialNameReturnsEntry() {
        val results = AirportDAO.searchAirport("Heathrow")
        val lhr = results.find { it["value"] == "LHR" }
        assertNotNull(lhr, "Partial name 'Heathrow' must match LHR")
    }

    /**
     * Verifies that searching by partial city name ("Amster") returns both a city
     * entry and an airport entry for Amsterdam / AMS.
     */
    @Test
    fun testSearchAirportByPartialCityNameReturnsBothTypes() {
        val results = AirportDAO.searchAirport("Amster")
        val hasCity = results.any { it["type"] == "city" && it["value"] == "Amsterdam" }
        val hasAirport = results.any { it["type"] == "airport" && it["value"] == "AMS" }
        assertTrue(hasCity, "Partial city match should produce a city entry for Amsterdam")
        assertTrue(hasAirport, "Partial city match should produce an airport entry for AMS")
    }

    /**
     * Verifies that airport entry `"value"` fields are IATA codes (not city names).
     */
    @Test
    fun testSearchAirportAirportEntriesHaveIataCodeAsValue() {
        val results = AirportDAO.searchAirport("Dublin")
        val airportEntries = results.filter { it["type"] == "airport" }
        for (entry in airportEntries) {
            val value = entry["value"]!!
            assertEquals(
                3,
                value.length,
                "Airport 'value' must be a 3-character IATA code, got '$value'"
            )
            assertTrue(value.all { it.isLetter() }, "IATA code must be letters only, got '$value'")
        }
    }

    // ── searchAirport: case insensitivity ──────────────────────────────────────

    /**
     * Verifies that [AirportDAO.searchAirport] matches regardless of input case
     * by comparing the result counts for "edinburgh", "EDINBURGH", and "Edinburgh".
     */
    @Test
    fun testSearchAirportIsCaseInsensitive() {
        val lower = AirportDAO.searchAirport("edinburgh")
        val upper = AirportDAO.searchAirport("EDINBURGH")
        val mixed = AirportDAO.searchAirport("Edinburgh")
        assertEquals(lower.size, upper.size, "Lowercase and uppercase queries must return the same count")
        assertEquals(lower.size, mixed.size, "Mixed-case query must return the same count")
    }

    // ── searchAirport: no-match queries ────────────────────────────────────────

    /**
     * Verifies that a query that matches nothing in the database returns an empty list
     * rather than null or an exception.
     */
    @Test
    fun testSearchAirportNoMatchReturnsEmptyList() {
        val results = AirportDAO.searchAirport("zzznomatchzzz")
        assertTrue(results.isEmpty(), "Unrecognised query must return an empty list")
    }

    // ── searchAirport: edge cases ──────────────────────────────────────────────

    /**
     * Verifies that an empty query string does not throw and returns a list
     * (the LIKE '%' pattern matches everything, so results may be non-empty).
     */
    @Test
    fun testSearchAirportEmptyQueryDoesNotThrow() {
        val threw = try {
            AirportDAO.searchAirport("")
            false
        } catch (e: Exception) {
            true
        }
        assertFalse(threw, "Empty query string must not throw an exception")
    }

    /**
     * Verifies that a whitespace-only query does not throw and returns a list.
     */
    @Test
    fun testSearchAirportWhitespaceQueryDoesNotThrow() {
        val threw = try {
            AirportDAO.searchAirport("   ")
            false
        } catch (e: Exception) {
            true
        }
        assertFalse(threw, "Whitespace-only query must not throw")
    }

    /**
     * Verifies that a single-character query returns a non-null list without throwing.
     */
    @Test
    fun testSearchAirportSingleCharQueryDoesNotThrow() {
        val results = AirportDAO.searchAirport("L")
        assertNotNull(results)
    }

    /**
     * Verifies that a very long query string (5 000 characters) does not crash the method.
     */
    @Test
    fun testSearchAirportVeryLongQueryDoesNotCrash() {
        val longQuery = "a".repeat(5000)
        val threw = try {
            AirportDAO.searchAirport(longQuery)
            false
        } catch (e: Exception) {
            true
        }
        assertFalse(threw, "Oversized query must not crash the application")
    }

    /**
     * Verifies that a query containing SQL wildcard characters (`%` and `_`) is treated
     * as a literal string and does not expand unexpectedly. The query must not throw.
     */
    @Test
    fun testSearchAirportWildcardCharactersDoNotThrow() {
        val threw = try {
            AirportDAO.searchAirport("_%_")
            false
        } catch (e: Exception) {
            true
        }
        assertFalse(threw, "Wildcard characters in query must not throw")
    }

    /**
     * Verifies that special characters and Unicode in the query do not cause an exception.
     */
    @Test
    fun testSearchAirportSpecialCharactersDoNotCrash() {
        val threw = try {
            AirportDAO.searchAirport("Ö'\";&|{}[]")
            false
        } catch (e: Exception) {
            true
        }
        assertFalse(threw, "Special characters and Unicode must not crash the search")
    }

    // ── searchAirport: SQL injection resistance ────────────────────────────────

    /**
     * Security test — classic `OR '1'='1` injection via [AirportDAO.searchAirport].
     *
     * The parameterised LIKE query must treat the payload as a literal search
     * string. No real airport city or name contains this substring, so the
     * result must be empty.
     */
    @Test
    fun testSearchAirportOrInjectionReturnsEmpty() {
        val injection = "' OR '1'='1"
        val results = AirportDAO.searchAirport(injection)
        assertTrue(results.isEmpty(), "OR injection must not widen the result set")
    }

    /**
     * Security test — UNION SELECT injection via [AirportDAO.searchAirport].
     *
     * A `UNION SELECT` payload must not append rows from other tables to the
     * result list. The result must be empty (no airport matches the payload
     * literally).
     */
    @Test
    fun testSearchAirportUnionInjectionReturnsEmpty() {
        val injection = "' UNION SELECT airport_id, name, city FROM airports --"
        val results = AirportDAO.searchAirport(injection)
        assertTrue(results.isEmpty(), "UNION injection must not leak additional rows")
    }

    /**
     * Security test — DROP TABLE injection via [AirportDAO.searchAirport].
     *
     * After passing a destructive payload the airports table must still be
     * accessible, proving the prepared statement neutralised the payload.
     */
    @Test
    fun testSearchAirportDropTableInjectionDoesNotDestroyTable() {
        val injection = "'; DROP TABLE airports; --"
        AirportDAO.searchAirport(injection)
        val stillWorks = try {
            AirportDAO.searchAirport("Leeds")
            true
        } catch (e: Exception) {
            false
        }
        assertTrue(stillWorks, "airports table must still be accessible after DROP TABLE injection attempt")
    }

    /**
     * Security test — comment-based injection via [AirportDAO.searchAirport].
     *
     * Using `--` to truncate the SQL query must have no effect when the input
     * is bound as a prepared-statement parameter.
     */
    @Test
    fun testSearchAirportCommentInjectionDoesNotThrow() {
        val injection = "Leeds'--"
        val threw = try {
            AirportDAO.searchAirport(injection)
            false
        } catch (e: Exception) {
            true
        }
        assertFalse(threw, "Comment-based injection must not throw")
    }

    // ── getLabel: city match ───────────────────────────────────────────────────

    /**
     * Verifies that [AirportDAO.getLabel] returns the `"City (all airports)"` format
     * when the value exactly matches a city name in the database.
     */
    @Test
    fun testGetLabelForKnownCityReturnsCityFormat() {
        val label = AirportDAO.getLabel("Leeds")
        assertEquals("Leeds (all airports)", label)
    }

    /**
     * Verifies the city-format label for a city that has multiple airports (London).
     */
    @Test
    fun testGetLabelForMultiAirportCityReturnsCityFormat() {
        val label = AirportDAO.getLabel("London")
        assertEquals("London (all airports)", label)
    }

    /**
     * Verifies the city-format label for Amsterdam.
     */
    @Test
    fun testGetLabelForAmsterdamCityReturnsCityFormat() {
        val label = AirportDAO.getLabel("Amsterdam")
        assertEquals("Amsterdam (all airports)", label)
    }

    // ── getLabel: airport ID match ─────────────────────────────────────────────

    /**
     * Verifies that [AirportDAO.getLabel] returns the `"City - Name (Code)"` format
     * when the value is a known IATA airport code.
     */
    @Test
    fun testGetLabelForKnownIataCodeReturnsAirportFormat() {
        val label = AirportDAO.getLabel("LBA")
        assertEquals("Leeds - Leeds Bradford Airport (LBA)", label)
    }

    /**
     * Verifies the airport-format label for LHR (London Heathrow).
     */
    @Test
    fun testGetLabelForLhrReturnsAirportFormat() {
        val label = AirportDAO.getLabel("LHR")
        assertEquals("London - Heathrow Airport (LHR)", label)
    }

    /**
     * Verifies the airport-format label for AMS (Amsterdam Schiphol).
     */
    @Test
    fun testGetLabelForAmsReturnsAirportFormat() {
        val label = AirportDAO.getLabel("AMS")
        assertEquals("Amsterdam - Amsterdam Airport Schiphol (AMS)", label)
    }

    /**
     * Verifies that the returned airport label always contains the IATA code
     * wrapped in parentheses.
     */
    @Test
    fun testGetLabelAirportFormatContainsCodeInParentheses() {
        val label = AirportDAO.getLabel("EDI")
        assertTrue(label.contains("(EDI)"), "Label must wrap code in parentheses, got: $label")
    }

    /**
     * Verifies that the returned airport label contains a dash separator between
     * city and airport name.
     */
    @Test
    fun testGetLabelAirportFormatContainsDashSeparator() {
        val label = AirportDAO.getLabel("DUB")
        assertTrue(label.contains(" - "), "Airport label must contain ' - ' separator, got: $label")
    }

    // ── getLabel: fallback behaviour ───────────────────────────────────────────

    /**
     * Verifies that [AirportDAO.getLabel] returns the original input unchanged when
     * neither an airport ID nor a city name matches.
     */
    @Test
    fun testGetLabelForUnknownValueReturnsFallback() {
        val unknown = "ZZZNOMATCH"
        val label = AirportDAO.getLabel(unknown)
        assertEquals(unknown, label, "Unknown value must be returned as-is")
    }

    /**
     * Verifies that an empty string input returns an empty string (fallback path).
     */
    @Test
    fun testGetLabelEmptyStringReturnsFallback() {
        val label = AirportDAO.getLabel("")
        assertEquals("", label, "Empty string must fall through to the fallback")
    }

    /**
     * Verifies that a lowercase city name does not match (the query uses exact equality,
     * not LIKE) and returns the original value as the fallback.
     */
    @Test
    fun testGetLabelLowercaseCityReturnsFallback() {
        val label = AirportDAO.getLabel("leeds")
        assertEquals("leeds", label, "Lowercase city must not match exact equality; fallback expected")
    }

    /**
     * Verifies that a lowercase IATA code does not match and falls back to the original value.
     */
    @Test
    fun testGetLabelLowercaseIataReturnsFallback() {
        val label = AirportDAO.getLabel("lba")
        assertEquals("lba", label, "Lowercase IATA code must not match; fallback expected")
    }

    /**
     * Verifies that a numeric string returns the original value (no match in DB).
     */
    @Test
    fun testGetLabelNumericStringReturnsFallback() {
        val label = AirportDAO.getLabel("12345")
        assertEquals("12345", label)
    }

    // ── getLabel: SQL injection resistance ────────────────────────────────────

    /**
     * Security test — classic `OR '1'='1` injection via [AirportDAO.getLabel].
     *
     * The prepared statement binds the payload as a literal string. No airport ID
     * or city name equals this payload, so the fallback value must be returned.
     */
    @Test
    fun testGetLabelOrInjectionReturnsFallback() {
        val injection = "' OR '1'='1"
        val label = AirportDAO.getLabel(injection)
        assertEquals(injection, label, "OR injection must not match any row; fallback expected")
    }

    /**
     * Security test — UNION SELECT injection via [AirportDAO.getLabel].
     *
     * The payload must be treated as a literal search value and must not append
     * data from other tables.
     */
    @Test
    fun testGetLabelUnionInjectionReturnsFallback() {
        val injection = "' UNION SELECT airport_id, name, city FROM airports LIMIT 1 --"
        val label = AirportDAO.getLabel(injection)
        assertEquals(injection, label, "UNION injection must not return data from the DB")
    }

    /**
     * Security test — DROP TABLE injection via [AirportDAO.getLabel].
     *
     * After passing a destructive payload the airports table must still be
     * accessible.
     */
    @Test
    fun testGetLabelDropTableInjectionDoesNotDestroyTable() {
        val injection = "'; DROP TABLE airports; --"
        AirportDAO.getLabel(injection)
        val stillWorks = try {
            AirportDAO.getLabel("Leeds")
            true
        } catch (e: Exception) {
            false
        }
        assertTrue(stillWorks, "airports table must survive a DROP TABLE injection attempt via getLabel")
    }

    /**
     * Security test — very long input via [AirportDAO.getLabel].
     *
     * An oversized string must not crash the server or corrupt the database.
     */
    @Test
    fun testGetLabelVeryLongInputDoesNotCrash() {
        val longValue = "X".repeat(5000)
        val threw = try {
            AirportDAO.getLabel(longValue)
            false
        } catch (e: Exception) {
            true
        }
        assertFalse(threw, "Oversized input must not crash getLabel")
    }
}
