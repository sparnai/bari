package com.sparnai.bari.data

import com.sparnai.bari.core.DayFare
import com.sparnai.bari.core.Fare
import com.sparnai.bari.core.RyanairParser
import com.sparnai.bari.core.Segment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.YearMonth
import java.util.concurrent.TimeUnit

class ApiException(message: String) : IOException(message)

/** Public Ryanair fare finder – no API key needed. */
class RyanairApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
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
        return RyanairParser.parseOneWayFares(get(url))
    }

    /** Cheapest price for every day of [month]. */
    suspend fun cheapestPerDay(origin: String, dest: String, month: YearMonth): List<DayFare> {
        val url = "$base/oneWayFares/$origin/$dest/cheapestPerDay".toHttpUrl().newBuilder()
            .addQueryParameter("outboundMonthOfDate", month.atDay(1).toString())
            .addQueryParameter("currency", "EUR")
            .build()
        return RyanairParser.parseCheapestPerDay(get(url))
    }

    private suspend fun get(url: HttpUrl): String = withContext(Dispatchers.IO) {
        var lastError: Exception = ApiException("Nepavyko gauti kainos")
        for (attempt in 0 until 3) {
            if (attempt > 0) delay(1500L * attempt)
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()
            val response = try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                lastError = e
                continue
            }
            response.use {
                val body = it.body?.string().orEmpty()
                if (it.isSuccessful) return@withContext body
                lastError = ApiException("Ryanair HTTP ${it.code}")
                // Only rate limiting and server errors are worth retrying.
                if (it.code != 429 && it.code < 500) throw lastError
            }
        }
        throw lastError
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
    }
}
