package com.sparnai.bari.core

import java.time.ZoneId

/** Time zones of the places used in the trip, so journey durations across Vilnius (UTC+2) and Italy (UTC+1) are right. */
object Zones {
    private val byPlace: Map<String, String> = buildMap {
        listOf("VNO", "KUN", "PLQ", "VILNIUS", "KAUNAS").forEach { put(it, "Europe/Vilnius") }
        listOf("RIX", "RIGA").forEach { put(it, "Europe/Riga") }
        put("TLL", "Europe/Tallinn")
        listOf("WAW", "WMI", "KRK", "KTW", "GDN", "POZ", "WRO", "WARSAW").forEach { put(it, "Europe/Warsaw") }
        listOf("BUD", "BUDAPEST").forEach { put(it, "Europe/Budapest") }
        listOf("VIE", "VIENNA").forEach { put(it, "Europe/Vienna") }
        put("BTS", "Europe/Bratislava")
        put("PRG", "Europe/Prague")
        listOf(
            "TRN", "BGY", "MXP", "LIN", "BRI", "BDS", "FCO", "CIA", "ROM", "NAP", "PSA", "BLQ", "VCE", "TSF", "CTA", "PMO",
            "CAG", "SUF", "PSR", "TRS", "GOA", "VRN", "AOI", "CIY", "ROME", "BARI", "MILAN", "TURIN", "NAPLES",
        ).forEach { put(it, "Europe/Rome") }
        listOf("STN", "LTN", "LGW", "LHR").forEach { put(it, "Europe/London") }
        put("DUB", "Europe/Dublin")
        listOf("FRA", "MUC", "BER", "CGN", "HHN", "NUE").forEach { put(it, "Europe/Berlin") }
        listOf("AMS", "EIN").forEach { put(it, "Europe/Amsterdam") }
        put("CRL", "Europe/Brussels")
        put("BVA", "Europe/Paris")
        listOf("BCN", "MAD").forEach { put(it, "Europe/Madrid") }
        put("MLA", "Europe/Malta")
        put("ATH", "Europe/Athens")
        put("CPH", "Europe/Copenhagen")
        listOf("ARN", "NYO").forEach { put(it, "Europe/Stockholm") }
        put("OSL", "Europe/Oslo")
        put("HEL", "Europe/Helsinki")
        listOf("IST", "SAW").forEach { put(it, "Europe/Istanbul") }
    }

    private val italianStations = listOf("roma", "bari", "milano", "napoli", "torino", "firenze", "bologna", "venezia", "brindisi")

    fun of(place: String): ZoneId? {
        byPlace[place.trim().uppercase()]?.let { return ZoneId.of(it) }
        val lower = place.lowercase()
        if (italianStations.any { lower.startsWith(it) }) return ZoneId.of("Europe/Rome")
        return null
    }
}
