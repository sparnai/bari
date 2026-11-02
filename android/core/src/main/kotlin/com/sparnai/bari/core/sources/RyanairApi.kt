package com.sparnai.bari.core.sources

import com.sparnai.bari.core.DayFare
import com.sparnai.bari.core.Fare
import com.sparnai.bari.core.RouteMath
import com.sparnai.bari.core.RyanairParser
import com.sparnai.bari.core.Segment
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.YearMonth

/** Public Ryanair fare finder – no API key needed. */
class RyanairApi(private val http: Http) {
    private val base = "https://services-api.ryanair.com/farfnd/v4"

    /** Cheapest fare for the segment's date and departure time window; null when there are no flights. */
    suspend fun cheapestFare(segment: Segment): Fare? {
        val url = "$base/oneWayFares".toHttpUrl().newBuilder()
            .addQueryParameter("departureAirportIataCode", segment.origin)
            .addQueryParameter("arrivalAirportIataCode", segment.dest)
            .addQueryParameter("outboundDepartureDateFrom", segment.date)
            .addQueryParameter("outboundDepartureDateTo", segment.date)
            .addQueryParameter("outboundDepartureTimeFrom", segment.timeFrom ?: "00:00")
            .addQueryParameter("outboundDepartureTimeTo", segment.timeTo ?: "23:59")
            .addQueryParameter("currency", "EUR")
            .build()
        return RyanairParser.parseOneWayFares(http.get(url.toString()))
            ?.copy(carrier = "Ryanair", stops = 0, url = RouteMath.ryanairUrl(segment.origin, segment.dest, segment.date))
    }

    /** Cheapest fare to every destination served from [origin] on [date] (route discovery). */
    suspend fun faresFrom(origin: String, date: String, timeFrom: String = "00:00"): List<Pair<String, Fare>> {
        val url = "$base/oneWayFares".toHttpUrl().newBuilder()
            .addQueryParameter("departureAirportIataCode", origin)
            .addQueryParameter("outboundDepartureDateFrom", date)
            .addQueryParameter("outboundDepartureDateTo", date)
            .addQueryParameter("outboundDepartureTimeFrom", timeFrom)
            .addQueryParameter("outboundDepartureTimeTo", "23:59")
            .addQueryParameter("currency", "EUR")
            .build()
        return RyanairParser.parseAllFares(http.get(url.toString()))
    }

    /** Cheapest price for every day of [month]. */
    suspend fun cheapestPerDay(origin: String, dest: String, month: YearMonth): List<DayFare> {
        val url = "$base/oneWayFares/$origin/$dest/cheapestPerDay".toHttpUrl().newBuilder()
            .addQueryParameter("outboundMonthOfDate", month.atDay(1).toString())
            .addQueryParameter("currency", "EUR")
            .build()
        return RyanairParser.parseCheapestPerDay(http.get(url.toString()))
    }
}
