package com.sparnai.bari.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Shared JSON configuration (same format as config/trip.json used by the cloud tracker). */
val BariJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
}

@Serializable
enum class FareSource {
    @SerialName("ryanair")
    RYANAIR,

    @SerialName("manual")
    MANUAL,
}

/** One leg that is tracked. Ryanair legs are checked automatically, manual legs hold a typed-in price. */
@Serializable
data class Segment(
    val id: String,
    val label: String,
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
    // Manual legs
    val price: Double? = null,
    val flight: String? = null,
    /** HH:mm */
    val departure: String? = null,
    /** HH:mm */
    val arrival: String? = null,
) {
    val isManual: Boolean get() = source == FareSource.MANUAL

    /** Changes to these fields mean the tracked fare is a different product, so history restarts. */
    val signature: String get() = "$origin-$dest-$date-$timeFrom-$timeTo-$source"
}

@Serializable
data class RouteOption(
    val id: String,
    val name: String,
    val segments: List<String>,
    /** Extra cost for the whole group (hotel, transfer...). */
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
    val flightNumber: String = "",
    /** Local time at the departure airport, ISO yyyy-MM-ddTHH:mm[:ss] */
    val departure: String? = null,
    /** Local time at the arrival airport. */
    val arrival: String? = null,
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

@Serializable
data class AppSettings(
    /** 0 = background checks off. */
    val checkIntervalHours: Int = 6,
    val notifyOnDrop: Boolean = true,
    val minDropEuro: Double = 1.0,
    val notifyOnTarget: Boolean = true,
)

@Serializable
data class AppState(
    val version: Int = 1,
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
