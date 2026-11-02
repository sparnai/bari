package com.sparnai.bari.core.sources

import com.sparnai.bari.core.Fare
import com.sparnai.bari.core.RouteMath
import com.sparnai.bari.core.Segment
import com.sparnai.bari.core.round2
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale

/** One result row from a Google Flights results page. */
data class GoogleFlight(
    val price: Double,
    val roundTrip: Boolean,
    val stops: Int?,
    val carriers: String?,
    val departure: LocalDateTime?,
    val arrival: LocalDateTime?,
    val durationMinutes: Int?,
)

/**
 * Google Flights has no public API. We open the same results page a browser would (English, EUR)
 * and read the accessibility labels of the result rows, e.g.
 * "From 136 euros. Nonstop flight with Ryanair. Leaves Vilnius International Airport at 6:55 AM on
 * Saturday, December 26 and arrives at Turin Airport at 8:35 AM on Saturday, December 26. Total duration 2 hr 40 min."
 */
class GoogleFlights(private val http: Http) {

    suspend fun cheapest(segment: Segment): Fare? {
        val url = GoogleFlightsQuery.searchUrl(segment)
        val html = http.get(url, headers = GoogleFlightsQuery.HEADERS)
        val all = GoogleFlightsParser.parse(html, RouteMath.parseDate(segment.date) ?: LocalDate.now())
        if (all.isEmpty()) {
            GoogleFlightsParser.problem(html)?.let { throw ApiException(it) }
            return null
        }
        val from = RouteMath.parseTime(segment.timeFrom)
        val to = RouteMath.parseTime(segment.timeTo)
        val matching = all.filter { f ->
            val dep = f.departure?.toLocalTime()
            (segment.maxStops == null || f.stops == null || f.stops <= segment.maxStops) &&
                (segment.maxHours == null || f.durationMinutes == null || f.durationMinutes <= segment.maxHours * 60) &&
                (dep == null || ((from == null || !dep.isBefore(from)) && (to == null || !dep.isAfter(to))))
        }
        val best = matching.minByOrNull { it.price } ?: return null
        return best.toFare(url)
    }

    private fun GoogleFlight.toFare(url: String) = Fare(
        price = price,
        flightNumber = carriers.orEmpty(),
        departure = departure?.format(ISO_MINUTE),
        arrival = arrival?.format(ISO_MINUTE),
        carrier = carriers,
        stops = stops,
        url = url,
    )

    companion object {
        private val ISO_MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    }
}

/** Builds Google Flights search links (the `tfs` parameter is a base64 protobuf, as used by the website). */
object GoogleFlightsQuery {
    val HEADERS = mapOf(
        // Skip the EU cookie consent interstitial.
        "Cookie" to "SOCS=CAI; CONSENT=PENDING+987",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.9",
    )

    fun searchUrl(segment: Segment): String {
        val legs = buildList {
            add(Triple(segment.origin, segment.dest, segment.date))
            segment.returnDate?.let { add(Triple(segment.dest, segment.origin, it)) }
        }
        val tfs = tfs(legs, segment.airlines, segment.maxStops, roundTrip = segment.returnDate != null)
        return "https://www.google.com/travel/flights?tfs=${URLEncoder.encode(tfs, "UTF-8")}&hl=en&curr=EUR"
    }

    /** Encodes the search as Google's `Info` protobuf message. */
    fun tfs(legs: List<Triple<String, String, String>>, airlines: List<String>, maxStops: Int?, roundTrip: Boolean): String {
        val info = Proto().apply {
            for ((from, to, date) in legs) {
                message(3) {
                    string(2, date)
                    if (maxStops != null) int(5, maxStops)
                    airlines.forEach { string(6, it.uppercase(Locale.ROOT)) }
                    message(13) { string(2, from.uppercase(Locale.ROOT)) }
                    message(14) { string(2, to.uppercase(Locale.ROOT)) }
                }
            }
            int(8, 1) // passengers: one adult
            int(9, 1) // seat: economy
            int(19, if (roundTrip) 1 else 2) // trip: round trip / one way
        }
        return Base64.getEncoder().encodeToString(info.bytes())
    }

    private class Proto {
        private val out = ByteArrayOutputStream()

        fun bytes(): ByteArray = out.toByteArray()

        private fun varint(value: Long) {
            var v = value
            while (v and 0x7FL.inv() != 0L) {
                out.write(((v and 0x7F) or 0x80).toInt())
                v = v ushr 7
            }
            out.write(v.toInt())
        }

        private fun tag(field: Int, wireType: Int) = varint(((field shl 3) or wireType).toLong())

        fun int(field: Int, value: Int) {
            tag(field, 0)
            varint(value.toLong())
        }

        fun string(field: Int, value: String) = raw(field, value.toByteArray(Charsets.UTF_8))

        fun message(field: Int, build: Proto.() -> Unit) = raw(field, Proto().apply(build).bytes())

        private fun raw(field: Int, data: ByteArray) {
            tag(field, 2)
            varint(data.size.toLong())
            out.write(data)
        }
    }
}

object GoogleFlightsParser {
    private val ariaLabel = Regex("aria-label=\"([^\"]*)\"")
    private const val SP = "[\\s\\u00A0\\u202F]*"
    private val price = Regex("(?:From )?([0-9][0-9,]*(?:\\.[0-9]+)?)\\s+euros?(\\s+round trip total)?", RegexOption.IGNORE_CASE)
    private val stops = Regex("(Nonstop|(\\d+) stops?) flight", RegexOption.IGNORE_CASE)
    private val carriers = Regex("flight with (.+?)\\.(?:\\s|$)")
    private val leaves = Regex("Leaves .+? at (\\d{1,2}:\\d{2})$SP([AP]M) on (?:\\w+, )?(\\w+) (\\d{1,2})", RegexOption.IGNORE_CASE)
    private val arrives = Regex("arrives at .+? at (\\d{1,2}:\\d{2})$SP([AP]M) on (?:\\w+, )?(\\w+) (\\d{1,2})", RegexOption.IGNORE_CASE)
    private val duration = Regex("Total duration (?:(\\d+) hr)?\\s*(?:(\\d+) min)?", RegexOption.IGNORE_CASE)

    fun parse(html: String, searchDate: LocalDate): List<GoogleFlight> =
        ariaLabel.findAll(html)
            .map { unescape(it.groupValues[1]) }
            .filter { it.contains("euro", ignoreCase = true) && it.contains("Leaves", ignoreCase = true) }
            .mapNotNull { parseLabel(it, searchDate) }
            .distinctBy { Triple(it.price, it.departure, it.carriers) }
            .toList()

    fun parseLabel(label: String, searchDate: LocalDate): GoogleFlight? {
        val p = price.find(label) ?: return null
        val value = p.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val stopMatch = stops.find(label)
        val stopCount = when {
            stopMatch == null -> null
            stopMatch.groupValues[1].equals("Nonstop", ignoreCase = true) -> 0
            else -> stopMatch.groupValues[2].toIntOrNull()
        }
        val d = duration.find(label)
        val minutes = d?.let {
            val h = it.groupValues[1].toIntOrNull() ?: 0
            val m = it.groupValues[2].toIntOrNull() ?: 0
            (h * 60 + m).takeIf { total -> total > 0 }
        }
        return GoogleFlight(
            price = round2(value),
            roundTrip = p.groupValues[2].isNotBlank(),
            stops = stopCount,
            carriers = carriers.find(label)?.groupValues?.get(1)?.trim(),
            departure = leaves.find(label)?.let { dateTime(it, searchDate) },
            arrival = arrives.find(label)?.let { dateTime(it, searchDate) },
            durationMinutes = minutes,
        )
    }

    /** Explains why a page had no results (consent wall, bot check), or null. */
    fun problem(html: String): String? = when {
        html.contains("consent.google", ignoreCase = true) || html.contains("Before you continue", ignoreCase = true) ->
            "Google Flights prašo sutikti su slapukais"
        html.contains("unusual traffic", ignoreCase = true) || html.contains("/sorry/", ignoreCase = true) ->
            "Google Flights laikinai riboja užklausas"
        else -> null
    }

    private fun dateTime(m: MatchResult, searchDate: LocalDate): LocalDateTime? {
        val (hm, ampm, monthName, day) = m.destructured
        val h12 = hm.substringBefore(':').toIntOrNull() ?: return null
        val minute = hm.substringAfter(':').toIntOrNull() ?: return null
        val hour = h12 % 12 + if (ampm.equals("PM", ignoreCase = true)) 12 else 0
        val time = runCatching { LocalTime.of(hour, minute) }.getOrNull() ?: return null
        val month = runCatching { Month.valueOf(monthName.uppercase(Locale.ROOT)) }.getOrNull() ?: return null
        var date = runCatching { LocalDate.of(searchDate.year, month, day.toInt()) }.getOrNull() ?: return null
        if (date.isBefore(searchDate.minusDays(7))) date = date.plusYears(1)
        return date.atTime(time)
    }

    private val numericEntity = Regex("&#(x?)([0-9a-fA-F]+);")

    private fun unescape(s: String): String =
        numericEntity.replace(s) { m ->
            val code = m.groupValues[2].toIntOrNull(if (m.groupValues[1].isNotEmpty()) 16 else 10)
            code?.let { String(Character.toChars(it)) } ?: m.value
        }
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
}
