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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.ConcurrentHashMap

data class TrainOption(
    val price: Double?,
    val departure: String?,
    val arrival: String?,
    val trains: String,
    val changes: Int,
    val saleable: Boolean,
)

/** Trenitalia ticket search used by lefrecce.it (no key needed). */
class Trenitalia(private val http: Http) {
    private val base = "https://www.lefrecce.it/Channels.Website.BFF.WEB/website"
    private val stationIds = ConcurrentHashMap<String, Long>()

    suspend fun stationId(name: String): Long {
        name.toLongOrNull()?.let { return it }
        stationIds[name.lowercase()]?.let { return it }
        val url = "$base/locations/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", name)
            .addQueryParameter("limit", "10")
            .build()
        val stations = TrenitaliaParser.parseLocations(http.get(url.toString()))
        val match = stations.firstOrNull { it.second.equals(name, ignoreCase = true) } ?: stations.firstOrNull()
            ?: throw ApiException("Trenitalia: stotis „$name“ nerasta")
        stationIds[name.lowercase()] = match.first
        return match.first
    }

    suspend fun options(segment: Segment): List<TrainOption> {
        val from = stationId(segment.origin)
        val to = stationId(segment.dest)
        val body = buildJsonObject {
            put("departureLocationId", from)
            put("arrivalLocationId", to)
            put("departureTime", "${segment.date}T${segment.timeFrom ?: "00:00"}:00.000")
            put("adults", 1)
            put("children", 0)
            putJsonObject("criteria") {
                put("frecceOnly", false)
                put("regionalOnly", false)
                put("noChanges", false)
                put("order", "DEPARTURE_DATE")
                put("limit", 10)
                put("offset", 0)
            }
            putJsonObject("advancedSearchRequest") { put("bestFare", false) }
        }
        return TrenitaliaParser.parseSolutions(http.postJson("$base/ticket/solutions", body.toString()))
    }

    suspend fun cheapest(segment: Segment): Fare? {
        val to = RouteMath.parseTime(segment.timeTo)
        val best = options(segment)
            .filter { it.saleable && it.price != null }
            .filter { o ->
                val dep = RouteMath.parseLocal(o.departure) ?: return@filter false
                dep.toLocalDate().toString() == segment.date && (to == null || !dep.toLocalTime().isAfter(to))
            }
            .minByOrNull { it.price ?: Double.MAX_VALUE } ?: return null
        return Fare(
            price = round2(best.price ?: return null),
            flightNumber = best.trains,
            departure = best.departure?.take(16),
            arrival = best.arrival?.take(16),
            carrier = "Trenitalia",
            stops = best.changes,
            url = "https://www.lefrecce.it/Channels.Website.WEB/",
        )
    }
}

object TrenitaliaParser {
    fun parseLocations(body: String): List<Pair<Long, String>> {
        val root = BariJson.parseToJsonElement(body)
        val list = (root as? JsonArray) ?: ((root as? JsonObject)?.get("locations") as? JsonArray) ?: return emptyList()
        return list.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = (obj["id"] as? JsonPrimitive)?.longOrNull ?: (obj["id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
                ?: return@mapNotNull null
            val name = obj["name"].str() ?: obj["displayName"].str() ?: return@mapNotNull null
            id to name
        }
    }

    fun parseSolutions(body: String): List<TrainOption> {
        val root = BariJson.parseToJsonElement(body) as? JsonObject ?: return emptyList()
        val solutions = root["solutions"] as? JsonArray ?: return emptyList()
        return solutions.mapNotNull { item ->
            val wrapper = item as? JsonObject ?: return@mapNotNull null
            val s = (wrapper["solution"] as? JsonObject) ?: wrapper
            val price = ((s["price"] as? JsonObject)?.get("amount") as? JsonPrimitive)?.doubleOrNull
            val trains = (s["trains"] as? JsonArray).orEmpty().mapNotNull { t ->
                val train = t as? JsonObject ?: return@mapNotNull null
                val category = train["trainCategory"].str() ?: train["acronym"].str()
                val number = train["description"].str() ?: train["name"].str()
                listOfNotNull(category, number).joinToString(" ").ifBlank { null }
            }
            TrainOption(
                price = price,
                departure = s["departureTime"].str(),
                arrival = s["arrivalTime"].str(),
                trains = trains.joinToString(" + "),
                changes = (trains.size - 1).coerceAtLeast(0),
                saleable = s["status"].str()?.equals("SALEABLE", ignoreCase = true) ?: (price != null),
            )
        }
    }

    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull
}
