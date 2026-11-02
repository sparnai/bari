package com.sparnai.bari.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/** Lithuanian formatting helpers (kept free of Android APIs so they are unit-testable). */
object Format {
    /** Non-breaking space keeps "128 €" on one line. */
    const val NBSP = '\u00A0'

    private val lt: Locale = Locale.forLanguageTag("lt-LT")

    private val weekdaysShort = listOf("Pr", "An", "Tr", "Kt", "Pn", "Št", "Sk")
    private val weekdaysLong = listOf(
        "pirmadienis", "antradienis", "trečiadienis", "ketvirtadienis", "penktadienis", "šeštadienis", "sekmadienis",
    )
    private val monthsGenitive = listOf(
        "sausio", "vasario", "kovo", "balandžio", "gegužės", "birželio",
        "liepos", "rugpjūčio", "rugsėjo", "spalio", "lapkričio", "gruodžio",
    )

    private val cities = mapOf(
        "VNO" to "Vilnius", "KUN" to "Kaunas", "PLQ" to "Palanga", "RIX" to "Ryga", "TLL" to "Talinas",
        "TRN" to "Turinas", "BRI" to "Baris", "BDS" to "Brindizis", "VIE" to "Viena", "BGY" to "Bergamas",
        "MXP" to "Milanas", "LIN" to "Milanas", "WAW" to "Varšuva", "WMI" to "Varšuva", "ROM" to "Roma",
        "CIA" to "Roma (Čiampinas)", "FCO" to "Roma (Fiumičinas)", "NAP" to "Neapolis", "FRA" to "Frankfurtas",
        "MUC" to "Miunchenas", "BER" to "Berlynas", "KRK" to "Krokuva", "BLQ" to "Bolonija", "PSA" to "Piza",
        "VCE" to "Venecija", "TSF" to "Trevisas", "BUD" to "Budapeštas", "PRG" to "Praha",
    )

    fun city(iata: String): String = cities[iata.uppercase()] ?: iata.uppercase()

    fun euro(value: Double?): String {
        if (value == null) return "—"
        val text0 = String.format(lt, "%,.2f", value)
        var text = text0.replace('\u202F', NBSP).replace(' ', NBSP)
        if (text.endsWith(",00")) text = text.dropLast(3)
        return "$text$NBSP€"
    }

    /** Signed difference, e.g. "−5 €" / "+12,50 €". */
    fun delta(value: Double): String {
        val sign = if (value < 0) "−" else "+"
        return sign + euro(abs(value))
    }

    fun dateShort(date: LocalDate): String =
        "${weekdaysShort[date.dayOfWeek.value - 1]}, ${monthsGenitive[date.monthValue - 1]} ${date.dayOfMonth} d."

    fun dateShort(iso: String): String = RouteMath.parseDate(iso)?.let { dateShort(it) } ?: iso

    fun dateLong(date: LocalDate): String =
        "${date.year} m. ${monthsGenitive[date.monthValue - 1]} ${date.dayOfMonth} d., ${weekdaysLong[date.dayOfWeek.value - 1]}"

    fun weekdayShort(date: LocalDate): String = weekdaysShort[date.dayOfWeek.value - 1]

    fun time(dt: LocalDateTime?): String = dt?.let { String.format(lt, "%02d:%02d", it.hour, it.minute) } ?: "--:--"

    fun duration(minutes: Long): String {
        val m = abs(minutes)
        val h = m / 60
        val r = m % 60
        return when {
            h > 0 && r > 0 -> "$h val. $r min."
            h > 0 -> "$h val."
            else -> "$r min."
        }
    }

    /** "ką tik", "prieš 5 min.", "prieš 3 val.", "prieš 2 d." */
    fun ago(epochMillis: Long?, now: Long = System.currentTimeMillis()): String {
        if (epochMillis == null) return "dar netikrinta"
        val minutes = (now - epochMillis) / 60_000
        return when {
            minutes < 1 -> "ką tik"
            minutes < 60 -> "prieš $minutes min."
            minutes < 48 * 60 -> "prieš ${minutes / 60} val."
            else -> "prieš ${minutes / (24 * 60)} d."
        }
    }

    fun daysUntil(date: LocalDate, today: LocalDate = LocalDate.now()): Long = ChronoUnit.DAYS.between(today, date)

    fun countdown(date: LocalDate, today: LocalDate = LocalDate.now()): String {
        val days = daysUntil(date, today)
        return when {
            days > 1 -> "Iki kelionės liko $days d."
            days == 1L -> "Kelionė jau rytoj!"
            days == 0L -> "Kelionė šiandien – gero skrydžio!"
            else -> "Kelionė jau prasidėjo"
        }
    }
}
