package com.sparnai.bari.core.sources

import com.sparnai.bari.core.BariJson
import com.sparnai.bari.core.Fare
import com.sparnai.bari.core.RouteMath
import com.sparnai.bari.core.Segment
import com.sparnai.bari.core.round2
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

data class BusOption(
    val price: Double,
    val departure: String?,
    val arrival: String?,
    val transfers: Int,
)

/** FlixBus public search API (used by flixbus.com, no key needed). */
class FlixBus(private val http: Http) {
    private val base = "https://global.api.flixbus.com"
    private val cityIds = ConcurrentHashMap<String, String>()
    private val dmy = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    suspend fun cityId(name: String): String {
        if (name.length == 36 && name.count { it == '-' } == 4) return name // already a city UUID
        cityIds[name.lowercase()]?.let { return it }
        val url = "$base/search/autocomplete/cities".toHttpUrl().newBuilder()
            .addQueryParameter("q", name)
            .addQueryParameter("lang", "en")
            .addQueryParameter("flixbus_cities_only", "false")
            .addQueryParameter("stations", "false")
            .addQueryParameter("popular_stations", "false")
            .build()
        val cities = FlixBusParser.parseCities(http.get(url.toString()))
        val match = cities.firstOrNull { it.second.equals(name, ignoreCase = true) } ?: cities.firstOrNull()
            ?: throw ApiException("FlixBus: miestas „$name“ nerastas")
        cityIds[name.lowercase()] = match.first
        return match.first
    }

    suspend fun cheapest(segment: Segment): Fare? {
        val from = cityId(segment.origin)
        val to = cityId(segment.dest)
        val date = RouteMath.parseDate(segment.date) ?: return null
        val url = "$base/search/service/v4/search".toHttpUrl().newBuilder()
            .addQueryParameter("from_city_id", from)
            .addQueryParameter("to_city_id", to)
            .addQueryParameter("departure_date", date.format(dmy))
            .addQueryParameter("products", "{\"adult\":1}")
            .addQueryParameter("currency", "EUR")
            .addQueryParameter("locale", "en")
            .addQueryParameter("search_by", "cities")
            .addQueryParameter("include_after_midnight_rides", "1")
            .build()
        val fromTime = RouteMath.parseTime(segment.timeFrom)
        val toTime = RouteMath.parseTime(segment.timeTo)
        val best = FlixBusParser.parseSearch(http.get(url.toString()))
            .filter { o ->
                val dep = RouteMath.parseLocal(o.departure) ?: return@filter false
                val t = dep.toLocalTime()
                dep.toLocalDate() == date && (fromTime == null || !t.isBefore(fromTime)) && (toTime == null || !t.isAfter(toTime))
            }
            .minByOrNull { it.price } ?: return null
        return Fare(
            price = best.price,
            flightNumber = "FlixBus" + if (best.transfers > 0) " (${best.transfers} persėd.)" else "",
            departure = best.departure?.take(16),
            arrival = best.arrival?.take(16),
            carrier = "FlixBus",
            stops = best.transfers,
            url = "https://shop.flixbus.com/search?departureCity=$from&arrivalCity=$to&rideDate=${date.format(dmy)}&adult=1",
        )
    }
}

object FlixBusParser {
    fun parseCities(body: String): List<Pair<String, String>> {
        val root = BariJson.parseToJsonElement(body)
        val list = (root as? JsonArray) ?: ((root as? JsonObject)?.get("cities") as? JsonArray) ?: return emptyList()
        return list.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = obj["id"].str() ?: obj["uuid"].str() ?: return@mapNotNull null
            val name = obj["name"].str() ?: return@mapNotNull null
            id to name
        }
    }

    fun parseSearch(body: String): List<BusOption> {
        val root = BariJson.parseToJsonElement(body) as? JsonObject ?: return emptyList()
        val trips = root["trips"] as? JsonArray ?: return emptyList()
        return trips.flatMap { trip ->
            val results = (trip as? JsonObject)?.get("results")
            val items: List<JsonElement> = when (results) {
                is JsonObject -> results.values.toList()
                is JsonArray -> results.toList()
                else -> emptyList()
            }
            items.mapNotNull { r ->
                val obj = r as? JsonObject ?: return@mapNotNull null
                if (obj["status"].str()?.equals("available", ignoreCase = true) == false) return@mapNotNull null
                val price = ((obj["price"] as? JsonObject)?.get("total") as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
                val legs = (obj["legs"] as? JsonArray)?.size ?: 1
                BusOption(
                    price = round2(price),
                    departure = ((obj["departure"] as? JsonObject)?.get("date")).str(),
                    arrival = ((obj["arrival"] as? JsonObject)?.get("date")).str(),
                    transfers = (legs - 1).coerceAtLeast(0),
                )
            }
        }
    }

    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull
}
