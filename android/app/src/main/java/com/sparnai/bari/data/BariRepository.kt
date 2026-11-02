package com.sparnai.bari.data

import android.content.Context
import com.sparnai.bari.core.AppSettings
import com.sparnai.bari.core.AppState
import com.sparnai.bari.core.BariJson
import com.sparnai.bari.core.DayFare
import com.sparnai.bari.core.Fare
import com.sparnai.bari.core.Format
import com.sparnai.bari.core.RouteMath
import com.sparnai.bari.core.RouteOption
import com.sparnai.bari.core.Segment
import com.sparnai.bari.core.SegmentRecord
import com.sparnai.bari.core.TripConfig
import com.sparnai.bari.core.TripMerge
import com.sparnai.bari.core.withError
import com.sparnai.bari.core.withFare
import com.sparnai.bari.core.FareSource
import com.sparnai.bari.core.PriceScope
import com.sparnai.bari.core.TransportMode
import com.sparnai.bari.core.sources.ApiException
import com.sparnai.bari.core.sources.PriceFetcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.time.YearMonth

data class RefreshOutcome(
    val alerts: List<String>,
    val ok: Int,
    val failed: Int,
    val skipped: Boolean = false,
)

/** Single source of truth: trip configuration, price records and settings, persisted as JSON. */
class BariRepository(private val context: Context, private val fetcher: PriceFetcher = PriceFetcher()) {
    private val file = File(context.filesDir, "bari_state.json")
    private val mutex = Mutex()

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    fun defaultTrip(): TripConfig = runCatching {
        context.assets.open("trip.json").bufferedReader().use {
            BariJson.decodeFromString(TripConfig.serializer(), it.readText())
        }
    }.getOrElse { TripConfig(name = "Kelionė") }

    private fun load(): AppState {
        val bundled = defaultTrip()
        val stored = runCatching {
            if (file.exists()) BariJson.decodeFromString(AppState.serializer(), file.readText()) else null
        }.getOrNull() ?: return AppState(trip = bundled, bundledTrip = bundled)
        val base = stored.bundledTrip
        return when {
            base == bundled -> stored
            base == null -> stored.copy(bundledTrip = bundled)
            // A new app version ships an updated trip: apply it without losing the user's own changes.
            else -> stored.copy(trip = TripMerge.merge(stored.trip, base, bundled), bundledTrip = bundled)
        }
    }

    private suspend fun update(transform: (AppState) -> AppState): AppState = mutex.withLock {
        val next = transform(_state.value)
        _state.value = next
        withContext(Dispatchers.IO) {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(BariJson.encodeToString(AppState.serializer(), next))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
        next
    }

    /** Checks every Ryanair segment and returns notification lines for price drops. */
    suspend fun refreshAll(): RefreshOutcome {
        if (!_refreshing.compareAndSet(expect = false, update = true)) {
            return RefreshOutcome(emptyList(), 0, 0, skipped = true)
        }
        try {
            val snapshot = _state.value
            // Buses / trains that are switched off are not checked (and do not notify).
            val allowed = snapshot.settings.allowedModes
            val toCheck = snapshot.trip.segments.filter { !it.isManual && it.mode in allowed }
            // Google Flights is a web page, not an API: ask one page at a time, politely.
            val apiGate = Semaphore(3)
            val pageGate = Semaphore(1)
            val results: List<Pair<Segment, Result<Fare?>>> = coroutineScope {
                toCheck.map { segment ->
                    async {
                        val gate = if (segment.source == FareSource.GOOGLE) pageGate else apiGate
                        gate.withPermit {
                            if (segment.source == FareSource.GOOGLE) delay(800)
                            segment to runCatching { fetcher.fetch(segment) }
                        }
                    }
                }.awaitAll()
            }
            val now = System.currentTimeMillis()
            val alerts = mutableListOf<String>()
            var ok = 0
            var failed = 0
            update { st ->
                val bestBefore = RouteMath.plan(
                    st.trip, st.records, st.settings.priceScope, st.settings.excludedRoutes, st.settings.allowedModes,
                ).bestTotal
                val records = st.records.toMutableMap()
                for ((segment, result) in results) {
                    // The segment may have been edited or removed while we were waiting for the network.
                    if (st.trip.segments.none { it == segment }) continue
                    val before = records[segment.id]
                    val fare = result.getOrNull()
                    val error = result.exceptionOrNull()
                    records[segment.id] = when {
                        fare != null -> {
                            alerts += RouteMath.alertsFor(segment, before, fare, st.settings, st.trip.passengers)
                            ok++
                            (before ?: SegmentRecord()).withFare(fare, now, segment.signature)
                        }
                        error != null -> {
                            failed++
                            (before ?: SegmentRecord()).withError(readable(error), now, segment.signature)
                        }
                        else -> {
                            failed++
                            val what = if (segment.mode == TransportMode.FLIGHT) "skrydžių" else "reisų"
                            (before ?: SegmentRecord()).withError("Šią dieną tinkamų $what nerasta", now, segment.signature)
                        }
                    }
                }
                val bestAfter = RouteMath.plan(
                    st.trip, records, st.settings.priceScope, st.settings.excludedRoutes, st.settings.allowedModes,
                ).bestTotal
                if (alerts.isNotEmpty() && bestAfter != null) {
                    val suffix = if (bestBefore != null && bestAfter < bestBefore) " (${Format.delta(bestAfter - bestBefore)})" else ""
                    val who = if (st.trip.passengers > 1) " (${Format.passengers(st.trip.passengers)})" else ""
                    val scope = if (st.settings.priceScope == PriceScope.FLIGHTS) " (tik skrydžiai)" else ""
                    alerts += "💶 Pigiausia kelionė į abi puses$scope: ${Format.euro(bestAfter)}$who$suffix"
                }
                st.copy(records = records, lastRefresh = now)
            }
            return RefreshOutcome(alerts, ok, failed)
        } finally {
            _refreshing.value = false
        }
    }

    suspend fun cheapestAround(segment: Segment, days: Long = 3): List<DayFare> {
        val date = RouteMath.parseDate(segment.date) ?: return emptyList()
        val from = date.minusDays(days)
        val to = date.plusDays(days)
        val months = listOf(YearMonth.from(from), YearMonth.from(to)).distinct()
        return months.flatMap { fetcher.ryanair.cheapestPerDay(segment.origin, segment.dest, it) }
            .filter { day -> RouteMath.parseDate(day.day)?.let { !it.isBefore(from) && !it.isAfter(to) } == true }
            .distinctBy { it.day }
            .sortedBy { it.day }
    }

    /** Adds or replaces a segment. [previousId] is the id being edited (null for a new segment). */
    suspend fun saveSegment(segment: Segment, previousId: String?) {
        update { st ->
            val segments = st.trip.segments.toMutableList()
            val index = segments.indexOfFirst { it.id == (previousId ?: segment.id) }
            if (index >= 0) segments[index] = segment else segments += segment
            val records = st.records.toMutableMap()
            if (previousId != null && previousId != segment.id) {
                records.remove(previousId)?.let { records[segment.id] = it }
            }
            if (segment.isManual) {
                val fare = RouteMath.manualFare(segment)
                val current = records[segment.id]
                if (fare != null && current?.fare?.price != fare.price) {
                    records[segment.id] = (current ?: SegmentRecord()).withFare(fare, System.currentTimeMillis(), segment.signature)
                }
            }
            val routes = if (previousId != null && previousId != segment.id) {
                st.trip.routes.map { r -> r.copy(segments = r.segments.map { if (it == previousId) segment.id else it }) }
            } else {
                st.trip.routes
            }
            st.copy(trip = st.trip.copy(segments = segments, routes = routes), records = records)
        }
    }

    suspend fun deleteSegment(id: String) {
        update { st ->
            st.copy(
                trip = st.trip.copy(
                    segments = st.trip.segments.filterNot { it.id == id },
                    routes = st.trip.routes.map { r -> r.copy(segments = r.segments.filterNot { it == id }) },
                ),
                records = st.records - id,
            )
        }
    }

    suspend fun saveRoute(route: RouteOption) {
        update { st ->
            val routes = st.trip.routes.toMutableList()
            val index = routes.indexOfFirst { it.id == route.id }
            if (index >= 0) routes[index] = route else routes += route
            st.copy(trip = st.trip.copy(routes = routes))
        }
    }

    suspend fun deleteRoute(id: String) {
        update { st -> st.copy(trip = st.trip.copy(routes = st.trip.routes.filterNot { it.id == id })) }
    }

    suspend fun setPassengers(count: Int) {
        update { st -> st.copy(trip = st.trip.copy(passengers = count.coerceIn(1, 9))) }
    }

    suspend fun setTripName(name: String) {
        update { st -> st.copy(trip = st.trip.copy(name = name.ifBlank { st.trip.name })) }
    }

    suspend fun updateSettings(settings: AppSettings) {
        update { st -> st.copy(settings = settings) }
    }

    suspend fun resetTrip() {
        val fresh = defaultTrip()
        update { st -> AppState(trip = fresh, settings = st.settings, bundledTrip = fresh) }
    }

    /** Puts back default segments and routes the user deleted; keeps everything else. Returns how many routes came back. */
    suspend fun restoreMissingDefaults(): Int {
        val bundled = defaultTrip()
        var restored = 0
        update { st ->
            val segIds = st.trip.segments.map { it.id }.toSet()
            val routeIds = st.trip.routes.map { it.id }.toSet()
            val segments = st.trip.segments + bundled.segments.filter { it.id !in segIds }
            val newRoutes = bundled.routes.filter { it.id !in routeIds }
            restored = newRoutes.size
            st.copy(trip = st.trip.copy(segments = segments, routes = st.trip.routes + newRoutes))
        }
        return restored
    }

    suspend fun clearHistory() {
        update { st ->
            st.copy(records = st.records.mapValues { (_, r) ->
                r.copy(history = r.history.takeLast(1), previousPrice = null, changedAt = null, lowest = r.fare?.price, highest = r.fare?.price)
            })
        }
    }

    private fun readable(error: Throwable): String = when (error) {
        is ApiException -> error.message ?: "Klaida"
        is java.net.UnknownHostException -> "Nėra interneto ryšio"
        is java.net.SocketTimeoutException -> "Baigėsi laukimo laikas"
        is java.io.IOException -> "Tinklo klaida: ${error.message ?: error.javaClass.simpleName}"
        is kotlinx.serialization.SerializationException -> "Netikėtas atsakymo formatas"
        else -> error.message ?: error.javaClass.simpleName
    }
}
