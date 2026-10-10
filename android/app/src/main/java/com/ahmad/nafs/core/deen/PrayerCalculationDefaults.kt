package com.ahmad.nafs.core.deen

/** An Aladhan calculation convention: [method] is its method id, [hanafi] selects the later (Hanafi) Asr. */
data class PrayerCalculation(val method: Int, val hanafi: Boolean)

/**
 * The convention most mosques follow in the account's region, used only until the user chooses one in Prayer times.
 * Legacy timezone names that link to the same zones are treated alike.
 */
object PrayerCalculationDefaults {
    private val southAsia = setOf("Asia/Karachi", "Asia/Kolkata", "Asia/Calcutta", "Asia/Dhaka", "Asia/Dacca", "Asia/Kabul")
    private val gulf = setOf("Asia/Riyadh", "Asia/Dubai", "Asia/Qatar", "Asia/Kuwait", "Asia/Bahrain", "Asia/Muscat")
    private val egypt = setOf("Africa/Cairo", "Egypt")
    private val turkey = setOf("Europe/Istanbul", "Asia/Istanbul", "Turkey")
    private val americas = listOf("America/", "US/", "Canada/")

    val KARACHI_HANAFI = PrayerCalculation(method = 1, hanafi = true)
    val ISNA = PrayerCalculation(method = 2, hanafi = false)
    val MUSLIM_WORLD_LEAGUE = PrayerCalculation(method = 3, hanafi = false)
    val UMM_AL_QURA = PrayerCalculation(method = 4, hanafi = false)
    val EGYPT = PrayerCalculation(method = 5, hanafi = false)
    val TURKEY_HANAFI = PrayerCalculation(method = 13, hanafi = true)

    fun forZone(zone: String): PrayerCalculation = when {
        zone in southAsia -> KARACHI_HANAFI
        americas.any { zone.startsWith(it) } -> ISNA
        zone in gulf -> UMM_AL_QURA
        zone in egypt -> EGYPT
        zone in turkey -> TURKEY_HANAFI
        else -> MUSLIM_WORLD_LEAGUE
    }
}
