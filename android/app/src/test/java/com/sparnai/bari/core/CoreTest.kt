package com.sparnai.bari.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class CoreTest {
    private val nb = Format.NBSP

    private val oneWay = """
        {"fares":[
          {"outbound":{"departureAirport":{"iataCode":"TRN"},"arrivalAirport":{"iataCode":"BRI"},
           "departureDate":"2026-12-26T21:40:00","arrivalDate":"2026-12-26T23:20:00",
           "price":{"value":82.0,"valueMainUnit":"82","currencyCode":"EUR","currencySymbol":"€"},
           "flightNumber":"FR264","previousPrice":null},"summary":{"price":{"value":82.0}},"inbound":null},
          {"outbound":{"departureDate":"2026-12-26T10:20:00","arrivalDate":"2026-12-26T12:00:00",
           "price":{"value":93.49,"currencyCode":"EUR"},"flightNumber":"FR6180"}},
          {"outbound":{"price":null}}
        ],"nextPage":null,"size":2}
    """.trimIndent()

    private val perDay = """
        {"outbound":{"fares":[
          {"day":"2026-12-25","arrivalDate":null,"departureDate":null,"price":null,"soldOut":false,"unavailable":true},
          {"day":"2026-12-26","arrivalDate":"2026-12-26T08:35:00","departureDate":"2026-12-26T06:55:00",
           "price":{"value":128.0,"currencyCode":"EUR"},"soldOut":false,"unavailable":false}
        ],"minFare":null,"maxFare":null}}
    """.trimIndent()

    private fun seg(id: String, o: String, d: String, date: String = "2026-12-26", target: Double? = null) =
        Segment(id = id, label = "$o → $d", origin = o, dest = d, date = date, targetPrice = target)

    @Test
    fun parsesCheapestOneWayFare() {
        val fare = RyanairParser.parseOneWayFares(oneWay)
        assertNotNull(fare)
        assertEquals(82.0, fare!!.price, 0.001)
        assertEquals("FR264", fare.flightNumber)
        assertEquals("2026-12-26T21:40:00", fare.departure)
        assertNull(RyanairParser.parseOneWayFares("""{"fares":[]}"""))
    }

    @Test
    fun parsesCheapestPerDay() {
        val days = RyanairParser.parseCheapestPerDay(perDay)
        assertEquals(2, days.size)
        assertTrue(days[0].unavailable)
        assertNull(days[0].price)
        assertEquals(128.0, days[1].price!!, 0.001)
    }

    @Test
    fun formatsLithuanian() {
        assertEquals("128$nb€", Format.euro(128.0))
        assertEquals("93,50$nb€", Format.euro(93.5))
        assertEquals("1${nb}234,50$nb€", Format.euro(1234.5))
        assertEquals("1 val. 45 min.", Format.duration(105))
        assertEquals("Št, gruodžio 26 d.", Format.dateShort(LocalDate.of(2026, 12, 26)))
        assertEquals("Iki kelionės liko 54 d.", Format.countdown(LocalDate.of(2026, 12, 26), LocalDate.of(2026, 11, 2)))
    }

    @Test
    fun recordKeepsHistoryAndPreviousPrice() {
        val s = seg("A", "VNO", "TRN")
        var r = SegmentRecord().withFare(Fare(128.0), 1, s.signature)
        r = r.withFare(Fare(128.0), 2, s.signature)
        assertNull(r.previousPrice)
        r = r.withFare(Fare(99.0), 3, s.signature)
        assertEquals(128.0, r.previousPrice!!, 0.0)
        r = r.withError("HTTP 503", 4, s.signature)
        assertEquals(99.0, r.fare!!.price, 0.0)
        assertEquals(3, r.history.size)
        assertEquals(99.0, r.lowest!!, 0.0)
        assertEquals(128.0, r.highest!!, 0.0)
        // A changed segment (new date) starts a fresh record.
        val moved = s.copy(date = "2026-12-27")
        val fresh = r.withFare(Fare(50.0), 5, moved.signature)
        assertNull(fresh.previousPrice)
        assertEquals(1, fresh.history.size)
    }

    @Test
    fun routeTotalsAndLayovers() {
        val a = seg("A", "VNO", "TRN")
        val b = seg("B", "TRN", "BRI")
        val m = Segment(
            id = "M", label = "Rankinis", origin = "BRI", dest = "VNO", date = "2027-01-03",
            source = FareSource.MANUAL, price = 50.0, departure = "23:30", arrival = "01:10",
        )
        val trip = TripConfig(
            passengers = 2,
            segments = listOf(a, b, m),
            routes = listOf(
                RouteOption("r2", "Su rankiniu", listOf("A", "B", "M"), extraCost = 30.0),
                RouteOption("r1", "A+B", listOf("A", "B")),
                RouteOption("r3", "Trūksta", listOf("A", "X")),
            ),
        )
        val records = mapOf(
            "A" to SegmentRecord(signature = a.signature, fare = Fare(99.0, departure = "2026-12-26T06:55:00", arrival = "2026-12-26T08:35:00"), previousPrice = 128.0),
            "B" to SegmentRecord(signature = b.signature, fare = Fare(93.0, departure = "2026-12-26T10:20:00", arrival = "2026-12-26T12:00:00")),
        )
        val all = RouteMath.summarizeAll(trip, records)
        assertEquals(listOf("r1", "r2", "r3"), all.map { it.route.id })
        val r1 = all[0]
        assertEquals(384.0, r1.total!!, 0.001)
        assertEquals(442.0, r1.previousTotal!!, 0.001)
        assertEquals(105L, r1.layovers[0]!!.minutes)
        assertEquals(LayoverLevel.TIGHT, r1.layovers[0]!!.level)
        val r2 = all[1]
        assertEquals((99 + 93 + 50) * 2 + 30.0, r2.total!!, 0.001)
        assertEquals("2027-01-04T01:10", r2.legs[2].fare!!.arrival)
        assertEquals(LayoverLevel.STAY, r2.layovers[1]!!.level)
        assertNull(all[2].total)
        assertFalse(all[2].missing.isEmpty())
    }

    @Test
    fun layoverLevels() {
        fun leg(id: String, o: String, d: String, dep: String, arr: String) = Leg(
            segment = seg(id, o, d), fare = Fare(1.0, departure = dep, arrival = arr), previousPrice = null,
            departure = RouteMath.parseLocal(dep), arrival = RouteMath.parseLocal(arr),
        )
        val toTrn = leg("A", "VNO", "TRN", "2026-12-26T06:55", "2026-12-26T08:35")
        val evening = leg("B", "TRN", "BRI", "2026-12-26T21:40", "2026-12-26T23:20")
        assertEquals(LayoverLevel.LONG, RouteMath.layover(toTrn, evening)!!.level)
        val toBgy = leg("C", "BRI", "BGY", "2027-01-02T23:25", "2027-01-03T00:55")
        val home = leg("D", "BGY", "VNO", "2027-01-03T06:10", "2027-01-03T09:40")
        assertEquals(LayoverLevel.OVERNIGHT, RouteMath.layover(toBgy, home)!!.level)
        val late = leg("E", "TRN", "BRI", "2026-12-26T08:00", "2026-12-26T09:00")
        assertEquals(LayoverLevel.IMPOSSIBLE, RouteMath.layover(toTrn, late)!!.level)
        val ok = leg("F", "TRN", "BRI", "2026-12-26T11:30", "2026-12-26T13:00")
        assertEquals(LayoverLevel.OK, RouteMath.layover(toTrn, ok)!!.level)
    }

    @Test
    fun alertsOnDropAndTarget() {
        val s = seg("A", "VNO", "TRN", target = 100.0)
        val before = SegmentRecord(signature = s.signature, fare = Fare(128.0), lowest = 110.0)
        val alerts = RouteMath.alertsFor(s, before, Fare(99.0), AppSettings())
        assertEquals(2, alerts.size)
        assertTrue(alerts[0].startsWith("📉"))
        assertTrue(alerts[0].contains("žemiausia"))
        assertTrue(alerts[1].startsWith("🎯"))
        // Small drops below the threshold are ignored, target already reached earlier is not repeated.
        val small = RouteMath.alertsFor(s, before.copy(fare = Fare(99.5)), Fare(99.0), AppSettings(minDropEuro = 1.0))
        assertTrue(small.isEmpty())
        // First check ever: only target alert.
        assertEquals(1, RouteMath.alertsFor(s, null, Fare(90.0), AppSettings()).size)
    }

    @Test
    fun sharedTripConfigParses() {
        val candidates = listOf(File("../../config/trip.json"), File("../config/trip.json"), File("config/trip.json"))
        val file = candidates.firstOrNull { it.exists() } ?: return
        val trip = BariJson.decodeFromString(TripConfig.serializer(), file.readText())
        assertTrue(trip.segments.isNotEmpty())
        val ids = trip.segments.map { it.id }.toSet()
        assertEquals("segment ids must be unique", trip.segments.size, ids.size)
        trip.routes.forEach { route -> route.segments.forEach { assertTrue("${route.id} → $it", it in ids) } }
        trip.segments.forEach { assertNotNull("bad date ${it.date}", RouteMath.parseDate(it.date)) }
        assertTrue(trip.segments.any { it.source == FareSource.MANUAL })
    }

    @Test
    fun tripMergeKeepsUserChanges() {
        val a = seg("A", "VNO", "TRN")
        val b = seg("B", "TRN", "BRI")
        val c = seg("C", "BRI", "VIE")
        val old = TripConfig(name = "Senas", segments = listOf(a, b, c), routes = listOf(RouteOption("r", "R", listOf("A", "B"))))
        val new = TripConfig(
            name = "Naujas",
            segments = listOf(a.copy(timeFrom = "10:00"), b.copy(date = "2026-12-27"), seg("D", "VIE", "VNO")),
            routes = listOf(RouteOption("r", "R2", listOf("A", "B", "D"))),
        )
        // User edited A's target price, deleted C, and added their own segment U.
        val user = old.copy(segments = listOf(a.copy(targetPrice = 50.0), b, seg("U", "BRI", "NAP")))
        val merged = TripMerge.merge(user, old, new)
        assertEquals("Naujas", merged.name)
        assertEquals(listOf("A", "B", "U", "D"), merged.segments.map { it.id })
        assertEquals(50.0, merged.segments[0].targetPrice!!, 0.0) // user edit kept
        assertNull(merged.segments[0].timeFrom)
        assertEquals("2026-12-27", merged.segments[1].date) // untouched → updated
        assertEquals("R2", merged.routes.single().name)
        // Removed upstream + untouched → dropped; deleted by user → stays deleted.
        val merged2 = TripMerge.merge(old, old, new.copy(segments = new.segments + c))
        assertTrue(merged2.segments.none { it.id == "C" && it != c })
    }

    @Test
    fun stateRoundTrip() {
        val state = AppState(
            trip = TripConfig(segments = listOf(seg("A", "VNO", "TRN"))),
            records = mapOf("A" to SegmentRecord(fare = Fare(10.0), history = listOf(PricePoint(1, 10.0)))),
        )
        val text = BariJson.encodeToString(AppState.serializer(), state)
        assertEquals(state, BariJson.decodeFromString(AppState.serializer(), text))
    }
}
