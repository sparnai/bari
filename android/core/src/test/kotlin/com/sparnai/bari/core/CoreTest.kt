package com.sparnai.bari.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.sparnai.bari.core.sources.FlixBusParser
import com.sparnai.bari.core.sources.GoogleFlightsParser
import com.sparnai.bari.core.sources.GoogleFlightsQuery
import com.sparnai.bari.core.sources.TrenitaliaParser
import java.util.Base64
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
        assertEquals((99 + 93 + 50 + 30) * 2.0, r2.total!!, 0.001)
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
        assertTrue(trip.segments.none { it.source == FareSource.MANUAL })
        assertTrue(trip.routes.all { it.direction != Direction.ROUND || it.segments.size == 1 })
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
    fun parsesGoogleFlightsLabels() {
        val html = """
            <div class="x" aria-label="Select flight"></div>
            <div class="JMc5Xc" aria-label="From 254 euros. 1 stop flight with LOT. Leaves Vilnius International Airport at 2:40 PM on Saturday, December 26 and arrives at Bari Karol Wojty&#322;a Airport at 8:20 PM on Saturday, December 26. Total duration 6 hr 40 min. Layover (1 of 1) is a 2 hr 25 min layover at Warsaw Chopin Airport in Warsaw. Select flight"></div>
            <div class="JMc5Xc" aria-label="From 1,203 euros. Nonstop flight with Austrian. Leaves Vienna International Airport at 10:00 AM on Sunday, January 3 and arrives at Vilnius International Airport at 12:45 PM on Sunday, January 3. Total duration 1 hr 45 min.  Select flight"></div>
            <div aria-label="From 449 euros round trip total. 2 stops flight with Lufthansa and ITA. Leaves Vilnius International Airport at 4:00 PM on Saturday, December 26 and arrives at Bari Karol Wojtyla Airport at 11:00 PM on Saturday, December 26. Total duration 7 hr."></div>
        """.trimIndent()
        val flights = GoogleFlightsParser.parse(html, LocalDate.of(2026, 12, 26))
        assertEquals(3, flights.size)
        val lot = flights[0]
        assertEquals(254.0, lot.price, 0.0)
        assertEquals(1, lot.stops)
        assertEquals("LOT", lot.carriers)
        assertEquals("2026-12-26T14:40", lot.departure.toString())
        assertEquals("2026-12-26T20:20", lot.arrival.toString())
        assertEquals(400, lot.durationMinutes)
        val os = flights[1]
        assertEquals(1203.0, os.price, 0.0)
        assertEquals(0, os.stops)
        assertEquals("2027-01-03T10:00", os.departure.toString()) // year rolls over
        val rt = flights[2]
        assertTrue(rt.roundTrip)
        assertEquals(2, rt.stops)
        assertEquals("Lufthansa and ITA", rt.carriers)
        assertEquals("Google Flights prašo sutikti su slapukais", GoogleFlightsParser.problem("<a href=https://consent.google.com/>"))
    }

    @Test
    fun googleQueryEncodesProtobuf() {
        val tfs = GoogleFlightsQuery.tfs(listOf(Triple("VNO", "BRI", "2026-12-26")), listOf("LO"), 1, roundTrip = false)
        val bytes = Base64.getDecoder().decode(tfs)
        assertEquals(0x1A, bytes[0].toInt()) // field 3, length-delimited (flight data)
        val text = String(bytes, Charsets.ISO_8859_1)
        assertTrue(text.contains("2026-12-26") && text.contains("VNO") && text.contains("BRI") && text.contains("LO"))
        // ... ends with trip = one way (field 19 → 0x98 0x01, value 2)
        assertEquals(listOf(0x98, 0x01, 0x02), bytes.takeLast(3).map { it.toInt() and 0xFF })
        val url = GoogleFlightsQuery.searchUrl(Segment("g", "g", "VNO", "BRI", "2026-12-26", source = FareSource.GOOGLE))
        assertTrue(url.startsWith("https://www.google.com/travel/flights?tfs=") && url.endsWith("&hl=en&curr=EUR"))
    }

    @Test
    fun parsesTrenitalia() {
        val locations = """[{"name":"Bari Centrale","displayName":"Bari Centrale","id":830011021,"multistation":false},{"name":"Bari Torre a Mare","id":830011095}]"""
        assertEquals(830011021L to "Bari Centrale", TrenitaliaParser.parseLocations(locations).first())
        val solutions = """{"searchId":"x","solutions":[
          {"solution":{"origin":"Bari Centrale","destination":"Roma Termini","departureTime":"2027-01-03T06:50:00.000+01:00",
            "arrivalTime":"2027-01-03T11:30:00.000+01:00","duration":"4h 40min","status":"SALEABLE",
            "price":{"currency":"€","amount":45.9},"trains":[{"description":"9352","trainCategory":"Frecciarossa","acronym":"FR"}]}},
          {"solution":{"departureTime":"2027-01-03T08:00:00.000+01:00","arrivalTime":"2027-01-03T14:00:00.000+01:00",
            "status":"NOT_SALEABLE","price":null,"trains":[{"description":"1","trainCategory":"Regionale"},{"description":"2","trainCategory":"Intercity"}]}}
        ]}"""
        val options = TrenitaliaParser.parseSolutions(solutions)
        assertEquals(2, options.size)
        assertEquals(45.9, options[0].price!!, 0.0)
        assertEquals("Frecciarossa 9352", options[0].trains)
        assertTrue(options[0].saleable)
        assertFalse(options[1].saleable)
        assertEquals(1, options[1].changes)
    }

    @Test
    fun parsesFlixBus() {
        val cities = """[{"id":"40de8964-8646-11e6-9066-549f350fcb0c","name":"Bari","country":"IT"}]"""
        assertEquals("40de8964-8646-11e6-9066-549f350fcb0c", FlixBusParser.parseCities(cities).first().first)
        val search = """{"trips":[{"date":"2027-01-03","results":{
          "direct:1":{"uid":"a","status":"available","departure":{"date":"2027-01-03T07:15:00+01:00"},"arrival":{"date":"2027-01-03T12:20:00+01:00"},
            "price":{"total":19.99},"legs":[{"means_of_transport":"bus"}]},
          "direct:2":{"uid":"b","status":"full","departure":{"date":"2027-01-03T09:00:00+01:00"},"price":{"total":9.99}},
          "transfer:3":{"uid":"c","status":"available","departure":{"date":"2027-01-03T10:00:00+01:00"},"arrival":{"date":"2027-01-03T18:00:00+01:00"},
            "price":{"total":24.5},"legs":[{},{}]}
        }}]}"""
        val options = FlixBusParser.parseSearch(search)
        assertEquals(2, options.size)
        assertEquals(19.99, options[0].price, 0.0)
        assertEquals(1, options[1].transfers)
    }

    @Test
    fun planPicksCheapestWholeTrip() {
        fun s(id: String, o: String, d: String, date: String) = seg(id, o, d, date)
        val trip = TripConfig(
            segments = listOf(s("A", "VNO", "TRN", "2026-12-26"), s("B", "TRN", "BRI", "2026-12-26"),
                s("C", "BRI", "VIE", "2027-01-03"), s("D", "VIE", "VNO", "2027-01-03"), s("R", "VNO", "BRI", "2026-12-26")),
            routes = listOf(
                RouteOption("o", "Out", listOf("A", "B"), direction = Direction.OUT),
                RouteOption("b", "Back", listOf("C", "D"), direction = Direction.BACK),
                RouteOption("r", "Round", listOf("R"), direction = Direction.ROUND),
            ),
        )
        fun rec(id: String, p: Double) = id to SegmentRecord(fare = Fare(p))
        val cheapCombo = RouteMath.plan(trip, mapOf(rec("A", 100.0), rec("B", 50.0), rec("C", 60.0), rec("D", 90.0), rec("R", 400.0)))
        assertEquals(300.0, cheapCombo.bestTotal!!, 0.0)
        assertFalse(cheapCombo.roundTripWins)
        val cheapRound = RouteMath.plan(trip, mapOf(rec("A", 100.0), rec("B", 50.0), rec("C", 60.0), rec("D", 90.0), rec("R", 250.0)))
        assertEquals(250.0, cheapRound.bestTotal!!, 0.0)
        assertTrue(cheapRound.roundTripWins)
        assertEquals("Roma Termini", Format.city("Roma Termini"))
        assertEquals("Viena", Format.city("vie"))
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
