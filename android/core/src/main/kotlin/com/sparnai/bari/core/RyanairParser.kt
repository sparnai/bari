package com.sparnai.bari.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/** Parses the public Ryanair fare finder responses. Defensive: unknown or missing fields are skipped. */
object RyanairParser {

    /** `farfnd/v4/oneWayFares` → the cheapest fare in the response, or null when there are no flights. */
    fun parseOneWayFares(body: String): Fare? {
        val root = BariJson.parseToJsonElement(body) as? JsonObject ?: return null
        val fares = root["fares"] as? JsonArray ?: return null
        var best: Fare? = null
        for (item in fares) {
            val outbound = (item as? JsonObject)?.get("outbound") as? JsonObject ?: continue
            val priceObj = outbound["price"] as? JsonObject ?: continue
            val value = priceObj["value"].doubleOrNull() ?: continue
            val fare = Fare(
                price = round2(value),
                currency = priceObj["currencyCode"].stringOrNull() ?: "EUR",
                flightNumber = outbound["flightNumber"].stringOrNull() ?: "",
                departure = outbound["departureDate"].stringOrNull(),
                arrival = outbound["arrivalDate"].stringOrNull(),
            )
            if (best == null || fare.price < best.price) best = fare
        }
        return best
    }

    /** `farfnd/v4/oneWayFares` without an arrival airport → (destination IATA, cheapest fare) for every route. */
    fun parseAllFares(body: String): List<Pair<String, Fare>> {
        val root = BariJson.parseToJsonElement(body) as? JsonObject ?: return emptyList()
        val fares = root["fares"] as? JsonArray ?: return emptyList()
        return fares.mapNotNull { item ->
            val outbound = (item as? JsonObject)?.get("outbound") as? JsonObject ?: return@mapNotNull null
            val dest = ((outbound["arrivalAirport"] as? JsonObject)?.get("iataCode")).stringOrNull() ?: return@mapNotNull null
            val priceObj = outbound["price"] as? JsonObject ?: return@mapNotNull null
            val value = priceObj["value"].doubleOrNull() ?: return@mapNotNull null
            dest to Fare(
                price = round2(value),
                currency = priceObj["currencyCode"].stringOrNull() ?: "EUR",
                flightNumber = outbound["flightNumber"].stringOrNull() ?: "",
                departure = outbound["departureDate"].stringOrNull(),
                arrival = outbound["arrivalDate"].stringOrNull(),
                carrier = "Ryanair",
                stops = 0,
            )
        }
    }

    /** `farfnd/v4/oneWayFares/{from}/{to}/cheapestPerDay` → one entry per day of the month. */
    fun parseCheapestPerDay(body: String): List<DayFare> {
        val root = BariJson.parseToJsonElement(body) as? JsonObject ?: return emptyList()
        val outbound = root["outbound"] as? JsonObject ?: return emptyList()
        val fares = outbound["fares"] as? JsonArray ?: return emptyList()
        return fares.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val day = obj["day"].stringOrNull() ?: return@mapNotNull null
            val price = (obj["price"] as? JsonObject)?.get("value").doubleOrNull()
            DayFare(
                day = day,
                price = price?.let { round2(it) },
                departure = obj["departureDate"].stringOrNull(),
                soldOut = obj["soldOut"].booleanOrNull() ?: false,
                unavailable = obj["unavailable"].booleanOrNull() ?: (price == null),
            )
        }
    }

    private fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull
    private fun JsonElement?.doubleOrNull(): Double? = (this as? JsonPrimitive)?.doubleOrNull
    private fun JsonElement?.booleanOrNull(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull
}
