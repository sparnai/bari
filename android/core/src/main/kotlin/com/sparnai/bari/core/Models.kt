package com.sparnai.bari.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Shared JSON configuration (same format as config/trip.json). */
val BariJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
}

/** Where the price of a segment comes from. Every source is checked automatically. */
@Serializable
enum class FareSource {
    /** Ryanair public fare finder API. */
    @SerialName("ryanair")
    RYANAIR,

    /** Google Flights results page – any airline (Lufthansa, Wizz Air, LOT, Austrian, ITA, airBaltic…). */
    @SerialName("google")
    GOOGLE,

    /** Trenitalia (lefrecce.it) – Italian trains, e.g. Bari ↔ Roma. */
    @SerialName("trenitalia")
    TRENITALIA,

    /** FlixBus public search API – buses. */
    @SerialName("flixbus")
    FLIXBUS,

    /** Legacy fixed price; no longer offered in the app. */
    @SerialName("manual")
    MANUAL,
}

enum class TransportMode { FLIGHT, TRAIN, BUS }

val FareSource.mode: TransportMode
    get() = when (this) {
        FareSource.TRENITALIA -> TransportMode.TRAIN
        FareSource.FLIXBUS -> TransportMode.BUS
        else -> TransportMode.FLIGHT
    }

val FareSource.title: String
    get() = when (this) {
        FareSource.RYANAIR -> "Ryanair"
        FareSource.GOOGLE -> "Google Flights"
        FareSource.TRENITALIA -> "Trenitalia"
        FareSource.FLIXBUS -> "FlixBus"
        FareSource.MANUAL -> "Fiksuota kaina"
    }

/** One leg that is tracked. */
@Serializable
data class Segment(
    val id: String,
    val label: String,
    /** IATA code for flights, station name for trains (e.g. "Roma Termini"), city name for buses. */
    val origin: String,
    val dest: String,
    /** yyyy-MM-dd */
    val date: String,
    val source: FareSource = FareSource.RYANAIR,
    val carrier: String? = null,
    /** Earliest departure time to consider (HH:mm). */
    val timeFrom: String? = null,
    /** Latest departure time to consider (HH:mm). */
    val timeTo: String? = null,
    /** Notify when the price falls to this value or lower. */
    val targetPrice: Double? = null,
    /** Google Flights: only these airlines (IATA codes, e.g. ["OS"], ["LO", "W6"]). Empty = any airline. */
    val airlines: List<String> = emptyList(),
    /** Google Flights: maximum number of stops (0 = nonstop). Null = any. */
    val maxStops: Int? = null,
    /** Google Flights: return date for a round-trip ticket (price is the round-trip total). */
    val returnDate: String? = null,
    /** Google Flights: ignore itineraries longer than this many hours. */
    val maxHours: Int? = null,
    // Legacy manual legs
    val price: Double? = null,
    val flight: String? = null,
    /** HH:mm */
    val departure: String? = null,
    /** HH:mm */
    val arrival: String? = null,
) {
    val isManual: Boolean get() = source == FareSource.MANUAL
    val mode: TransportMode get() = source.mode

    /** Changes to these fields mean the tracked fare is a different product, so history restarts. */
    val signature: String
        get() = listOf(origin, dest, date, timeFrom, timeTo, source, airlines.joinToString(","), maxStops, returnDate, maxHours)
            .joinToString("-")
}

@Serializable
enum class Direction {
    /** Vilnius → destination */
    @SerialName("out")
    OUT,

    /** Destination → Vilnius */
    @SerialName("back")
    BACK,

    /** One ticket covering both directions. */
    @SerialName("round")
    ROUND,
}

/** A journey = ordered legs in one direction (or a round-trip ticket). */
@Serializable
data class RouteOption(
    val id: String,
    val name: String,
    val segments: List<String>,
    val direction: Direction = Direction.ROUND,
    /** Extra cost per person (airport transfer, …). */
    val extraCost: Double = 0.0,
    val extraCostLabel: String? = null,
    val note: String? = null,
)

@Serializable
data class TripConfig(
    val name: String = "Kelionė",
    val currency: String = "EUR",
    val passengers: Int = 1,
    val segments: List<Segment> = emptyList(),
    val routes: List<RouteOption> = emptyList(),
)

@Serializable
data class Fare(
    val price: Double,
    val currency: String = "EUR",
    /** Flight / train number(s), e.g. "FR8365", "Frecciarossa 9352". */
    val flightNumber: String = "",
    /** Local time at the departure point, ISO yyyy-MM-ddTHH:mm[:ss] */
    val departure: String? = null,
    /** Local time at the arrival point. */
    val arrival: String? = null,
    val carrier: String? = null,
    val stops: Int? = null,
    /** Page where this fare can be booked / seen. */
    val url: String? = null,
)

@Serializable
data class PricePoint(val t: Long, val p: Double)

@Serializable
data class SegmentRecord(
    val signature: String? = null,
    val fare: Fare? = null,
    /** Price before the most recent change. */
    val previousPrice: Double? = null,
    val changedAt: Long? = null,
    val lowest: Double? = null,
    val highest: Double? = null,
    val history: List<PricePoint> = emptyList(),
    val lastChecked: Long? = null,
    val lastError: String? = null,
)

/** What the journey totals include. */
@Serializable
enum class PriceScope {
    /** Flights, trains, buses and transfers between stations and airports. */
    @SerialName("full")
    FULL,

    /** Only plane tickets. */
    @SerialName("flights")
    FLIGHTS,
}

/** What "best" means when ranking journeys. */
@Serializable
enum class Priority {
    @SerialName("cheapest")
    CHEAPEST,

    @SerialName("fastest")
    FASTEST,
}

@Serializable
data class AppSettings(
    /** 0 = background checks off. */
    val checkIntervalHours: Int = 6,
    val notifyOnDrop: Boolean = true,
    val minDropEuro: Double = 1.0,
    val notifyOnTarget: Boolean = true,
    val priceScope: PriceScope = PriceScope.FULL,
    /** Route ids left out of the "cheapest trip" comparison (still listed, greyed out). */
    val excludedRoutes: List<String> = emptyList(),
    /** Plan with buses / trains. When off, journeys using them are left out and their prices are not checked. */
    val includeBuses: Boolean = true,
    val includeTrains: Boolean = true,
    val priority: Priority = Priority.CHEAPEST,
) {
    /** Transport modes the trip may use (flights are always allowed). */
    val allowedModes: Set<TransportMode>
        get() = buildSet {
            add(TransportMode.FLIGHT)
            if (includeBuses) add(TransportMode.BUS)
            if (includeTrains) add(TransportMode.TRAIN)
        }
}

@Serializable
data class AppState(
    val version: Int = 2,
    val trip: TripConfig = TripConfig(),
    val records: Map<String, SegmentRecord> = emptyMap(),
    val settings: AppSettings = AppSettings(),
    val lastRefresh: Long? = null,
    /** The bundled config/trip.json this state was last synced with (see [TripMerge]). */
    val bundledTrip: TripConfig? = null,
)

data class DayFare(
    val day: String,
    val price: Double?,
    val departure: String?,
    val soldOut: Boolean,
    val unavailable: Boolean,
)

const val MAX_HISTORY = 400

fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0

/** Applies a successful price check to a record. */
fun SegmentRecord.withFare(fare: Fare, now: Long, signature: String): SegmentRecord {
    val base = if (this.signature != null && this.signature != signature) SegmentRecord() else this
    val old = base.fare?.price
    val changed = old != null && old != fare.price
    return base.copy(
        signature = signature,
        fare = fare,
        previousPrice = if (changed) old else base.previousPrice,
        changedAt = if (changed) now else base.changedAt,
        lowest = minOf(base.lowest ?: fare.price, fare.price),
        highest = maxOf(base.highest ?: fare.price, fare.price),
        history = (base.history + PricePoint(now, fare.price)).takeLast(MAX_HISTORY),
        lastChecked = now,
        lastError = null,
    )
}

/** A failed check keeps the last known fare so drops are still detected next time. */
fun SegmentRecord.withError(message: String, now: Long, signature: String): SegmentRecord {
    val base = if (this.signature != null && this.signature != signature) SegmentRecord() else this
    return base.copy(signature = signature, lastChecked = now, lastError = message)
}
