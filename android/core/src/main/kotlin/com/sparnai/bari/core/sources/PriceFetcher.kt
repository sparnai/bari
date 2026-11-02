package com.sparnai.bari.core.sources

import com.sparnai.bari.core.Fare
import com.sparnai.bari.core.FareSource
import com.sparnai.bari.core.RouteMath
import com.sparnai.bari.core.Segment

/** Fetches the current price of any segment from its source. */
class PriceFetcher(val http: Http = Http()) {
    val ryanair = RyanairApi(http)
    val google = GoogleFlights(http)
    val trenitalia = Trenitalia(http)
    val flixbus = FlixBus(http)

    suspend fun fetch(segment: Segment): Fare? = when (segment.source) {
        FareSource.RYANAIR -> ryanair.cheapestFare(segment)
        FareSource.GOOGLE -> google.cheapest(segment)
        FareSource.TRENITALIA -> trenitalia.cheapest(segment)
        FareSource.FLIXBUS -> flixbus.cheapest(segment)
        FareSource.MANUAL -> RouteMath.manualFare(segment)
    }
}
