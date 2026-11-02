package com.sparnai.bari.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

enum class AdviceLevel { WAIT, GOOD_TIME, BUY_NOW }

data class BuyAdvice(
    val level: AdviceLevel,
    val headline: String,
    /** Expected best moment to buy, e.g. "dabar – lapkričio 21 d." */
    val forecast: String,
    val details: List<String>,
    /** Last day in the usual best-price window. */
    val buyBy: LocalDate,
    /** €/day over the last week of the best-trip price (null when there is too little history). */
    val trendPerDay: Double?,
)

/**
 * When to buy. Air fares cannot be predicted exactly; this combines well-known patterns
 * (low-cost fares mostly rise as departure nears, holidays sell out early) with the trend of your own price history.
 */
object PriceAdvice {
    private val zone: ZoneId = ZoneId.of("Europe/Vilnius")

    fun isPeak(date: LocalDate): Boolean {
        val md = date.monthValue * 100 + date.dayOfMonth
        return md >= 1215 || md <= 107 || date.monthValue in 7..8
    }

    /** Black Friday: the day after the fourth Thursday of November. */
    fun blackFriday(year: Int): LocalDate =
        LocalDate.of(year, 11, 1).with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.THURSDAY)).plusDays(1)

    /** Linear-regression slope in €/day over the points of the last [days] days; null when there is too little data. */
    fun trendPerDay(series: List<PricePoint>, now: Long, days: Int = 7): Double? {
        val from = now - days * 86_400_000L
        val pts = series.filter { it.t >= from }
        if (pts.size < 3 || pts.last().t - pts.first().t < 36 * 3_600_000L) return null
        val xs = pts.map { (it.t - pts.first().t) / 86_400_000.0 }
        val ys = pts.map { it.p }
        val mx = xs.average()
        val my = ys.average()
        val den = xs.sumOf { (it - mx) * (it - mx) }
        if (den == 0.0) return null
        return round2(xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / den)
    }

    fun forecast(departure: LocalDate, series: List<PricePoint>, now: Long = System.currentTimeMillis()): BuyAdvice {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(today, departure)
        val peak = isPeak(departure)
        val windowStart = departure.minusDays(if (peak) 84 else 56)
        val windowEnd = departure.minusDays(if (peak) 35 else 21)
        val trend = trendPerDay(series, now)
        val current = series.lastOrNull()?.p
        val lowest = series.minOfOrNull { it.p }
        val rising = trend != null && current != null && trend > maxOf(1.0, current * 0.004)
        val falling = trend != null && current != null && trend < -maxOf(1.0, current * 0.004)
        val details = mutableListOf<String>()

        details += "Iki išvykimo liko $days d." + if (peak) " Tai šventinis laikotarpis – pigiausi bilietai išperkami anksčiausiai." else ""
        if (trend != null) {
            details += when {
                rising -> "Per paskutinę savaitę visa kelionė brango vidutiniškai ${Format.euro(abs(trend))} per dieną."
                falling -> "Per paskutinę savaitę visa kelionė pigo vidutiniškai ${Format.euro(abs(trend))} per dieną."
                else -> "Per paskutinę savaitę kaina beveik nesikeitė."
            }
        } else {
            details += "Kainų tendencijai nustatyti reikia bent 2 dienų istorijos – prognozė kol kas remiasi bendrais dėsningumais."
        }
        if (current != null && lowest != null && series.size > 1) {
            details += if (current <= lowest * 1.02) {
                "Dabartinė kaina (${Format.euro(current)}) yra žemiausia arba beveik žemiausia, kokią matėme."
            } else {
                "Dabartinė kaina ${Format.euro(current)}, žemiausia matyta – ${Format.euro(lowest)}."
            }
        }
        val bf = blackFriday(today.year)
        if (!today.isAfter(bf) && bf.isBefore(departure.minusDays(14))) {
            details += "${dayMonth(bf)} (Black Friday) Ryanair ir Wizz Air dažnai skelbia akcijas – verta patikrinti" +
                if (bf.isAfter(windowEnd)) ", bet laukti rizikinga: tai jau po įprasto geriausio pirkimo laiko." else "."
        }

        val level = when {
            today.isAfter(windowEnd) || days <= 21 -> AdviceLevel.BUY_NOW
            rising -> AdviceLevel.BUY_NOW
            today.isBefore(windowStart) && !rising -> AdviceLevel.WAIT
            else -> AdviceLevel.GOOD_TIME
        }
        val headline = when (level) {
            AdviceLevel.BUY_NOW -> if (rising && !today.isAfter(windowEnd)) {
                "Pirkite dabar – kainos kyla"
            } else {
                "Pirkite kuo greičiau – artėjant išvykimui kainos paprastai tik kyla"
            }
            AdviceLevel.GOOD_TIME -> if (falling) {
                "Geras metas pirkti; kainos šiuo metu krenta, todėl galima palaukti kelias dienas, bet ne ilgiau nei iki ${dayMonth(windowEnd)}"
            } else {
                "Geras metas pirkti – rekomenduojama nupirkti iki ${dayMonth(windowEnd)}"
            }
            AdviceLevel.WAIT -> "Dar anksti – kainos dažnai dar keičiasi, geriausias pirkimo laikas ${dayMonth(windowStart)} – ${dayMonth(windowEnd)}"
        }
        val forecast = when (level) {
            AdviceLevel.BUY_NOW -> "Tikėtina, kad pigiau nebebus – geriausia kaina greičiausiai yra dabar."
            AdviceLevel.GOOD_TIME -> "Geriausios kainos tikėtinos dabar – ${dayMonth(windowEnd)}; vėliau jos paprastai kyla, o paskutines 2–3 savaites – sparčiausiai."
            AdviceLevel.WAIT -> "Geriausios kainos tikėtinos ${dayMonth(windowStart)} – ${dayMonth(windowEnd)}."
        }
        return BuyAdvice(level, headline, forecast, details, windowEnd, trend)
    }

    /** General tips on when tickets are cheapest. */
    val tips: List<String> = listOf(
        "Ryanair ir Wizz Air kainos dažniausiai mažiausios pardavimo pradžioje ir kyla, kol pildosi lėktuvas; staigiausiai – paskutines 2–3 savaites.",
        "Kalėdoms ir Naujiesiems metams bilietus geriausia pirkti 6–12 savaičių iš anksto: pigiausi bilietai išperkami pirmiausia.",
        "Pigiau dažnai skristi antradieniais, trečiadieniais ir šeštadieniais, brangiausia – penktadieniais ir sekmadieniais.",
        "Ankstyvo ryto ir vėlyvo vakaro reisai paprastai pigesni nei dienos metu.",
        "Bagažą pirkite kartu su bilietu – vėliau ar oro uoste jis kainuoja kelis kartus daugiau.",
        "Atskirais bilietais skrendant per kitą oro uostą, palikite bent 2–3 val. persėdimui: praleidus reisą, aviakompanija nekompensuoja.",
        "Trenitalia pigiausi „Economy“ ir „Super Economy“ bilietai pasirodo prieš kelis mėnesius ir išparduodami – traukinį pirkite anksti.",
        "FlixBus kainos kyla artėjant kelionei ir pildantis autobusui – pirkite, kai tik planas aiškus.",
        "Kaina nepriklauso nuo to, kiek kartų žiūrite puslapį – tai mitas; bet kainos keičiasi kelis kartus per dieną, todėl stebėkite.",
        "Kainų šuolis po akcijos dažnai būna laikinas, bet artėjant išvykimui nukritimų vis mažiau – nelaukite paskutinės savaitės.",
    )

    private fun dayMonth(date: LocalDate): String = Format.dayMonth(date)
}
