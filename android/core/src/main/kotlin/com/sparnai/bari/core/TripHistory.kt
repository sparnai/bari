package com.sparnai.bari.core

/**
 * Rebuilds how the price of the whole trip (and of each journey) changed over time from the per-segment histories:
 * at every check time each segment costs its last known price, and the plan is computed as it would have been then.
 */
object TripHistory {
    private const val MAX_POINTS = 240

    /** All moments a price was recorded, oldest first. */
    fun checkTimes(records: Map<String, SegmentRecord>): List<Long> =
        records.values.flatMap { r -> r.history.map { it.t } }.distinct().sorted().takeLast(MAX_POINTS)

    /** Records as they were at time [t]: each segment's last recorded price at or before [t]. */
    fun recordsAt(t: Long, records: Map<String, SegmentRecord>): Map<String, SegmentRecord> =
        records.mapNotNull { (id, r) ->
            val price = r.history.priceAt(t) ?: return@mapNotNull null
            val fare = r.fare?.copy(price = price) ?: Fare(price = price)
            id to r.copy(fare = fare, previousPrice = null)
        }.toMap()

    /** Last price recorded at or before [t] (history is in time order). */
    private fun List<PricePoint>.priceAt(t: Long): Double? {
        var lo = 0
        var hi = size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (this[mid].t <= t) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return if (found >= 0) this[found].p else null
    }

    /** Price of the best whole trip (best outbound + best return) at every check. */
    fun bestTotal(
        trip: TripConfig,
        records: Map<String, SegmentRecord>,
        settings: AppSettings,
    ): List<PricePoint> = checkTimes(records).mapNotNull { t ->
        val plan = RouteMath.plan(
            trip, recordsAt(t, records), settings.priceScope, settings.excludedRoutes, settings.allowedModes, settings.priority,
        )
        plan.bestTotal?.let { PricePoint(t, it) }
    }.dropConsecutiveDuplicates()

    /** Total price of one journey at every check. */
    fun route(
        route: RouteOption,
        trip: TripConfig,
        records: Map<String, SegmentRecord>,
        scope: PriceScope,
    ): List<PricePoint> {
        val ids = route.segments.toSet()
        val mine = records.filterKeys { it in ids }
        return checkTimes(mine).mapNotNull { t ->
            RouteMath.summarize(route, trip, recordsAt(t, mine), scope).total?.let { PricePoint(t, it) }
        }.dropConsecutiveDuplicates()
    }

    /** Keeps the first and last point of every flat stretch so step charts stay exact but light. */
    private fun List<PricePoint>.dropConsecutiveDuplicates(): List<PricePoint> =
        filterIndexed { i, p -> i == 0 || i == lastIndex || p.p != this[i - 1].p || p.p != this[i + 1].p }
}
