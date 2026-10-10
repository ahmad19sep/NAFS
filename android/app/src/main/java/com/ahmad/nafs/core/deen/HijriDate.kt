package com.ahmad.nafs.core.deen

import android.content.Context
import android.icu.util.Calendar
import android.icu.util.IslamicCalendar
import android.icu.util.TimeZone
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** A day in the Umm al-Qura Hijri calendar. [month] runs from 1 (Muharram) to 12 (Dhu al-Hijjah). */
data class HijriDay(val day: Int, val month: Int, val year: Int) {
    val monthName: String get() = HijriDate.monthNames[month - 1]
    val monthArabic: String get() = HijriDate.arabicMonthNames[month - 1]

    /** For example "28 Rabiʻ al-Thani 1448 AH". */
    val label: String get() = "$day $monthName $year AH"

    /** The same date in Arabic with Arabic-Indic digits, for an Amiri label: "٢٨ ربيع الآخر ١٤٤٨ هـ". */
    val arabicLabel: String get() = "${HijriDate.arabicDigits(day)} $monthArabic ${HijriDate.arabicDigits(year)} هـ"
}

/**
 * Hijri dates from the Umm al-Qura calendar (ICU, available from API 24). The day follows the civil date in the
 * account timezone, like the web app; a −1/0/+1 adjustment covers countries whose moon sighting differs.
 */
object HijriDate {
    val adjustments: IntRange = -1..1

    val monthNames = listOf(
        "Muharram", "Safar", "Rabiʻ al-Awwal", "Rabiʻ al-Thani", "Jumada al-Ula", "Jumada al-Akhirah",
        "Rajab", "Shaʻban", "Ramadan", "Shawwal", "Dhu al-Qiʻdah", "Dhu al-Hijjah",
    )

    val arabicMonthNames = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة",
    )

    /** The Umm al-Qura date for the civil [date], shifted by [adjustmentDays] for local moon sighting. */
    fun of(date: LocalDate, adjustmentDays: Int = 0): HijriDay {
        val calendar = IslamicCalendar(TimeZone.getTimeZone("UTC"), Locale.ENGLISH)
        calendar.calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        // Midday UTC of the civil date, set after the calculation type so the fields are computed with it.
        calendar.timeInMillis = date.plusDays(adjustmentDays.toLong()).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        return HijriDay(
            day = calendar.get(Calendar.DAY_OF_MONTH),
            month = calendar.get(Calendar.MONTH) + 1,
            year = calendar.get(Calendar.YEAR),
        )
    }

    /** "28 Rabiʻ al-Thani 1448 AH" for [date] with the user's [adjustmentDays]. */
    fun format(date: LocalDate, adjustmentDays: Int = 0): String = of(date, adjustmentDays).label

    internal fun arabicDigits(value: Int): String =
        value.toString().map { if (it in '0'..'9') '٠' + (it - '0') else it }.joinToString("")
}

/** The Hijri adjustment each account chose on this device: −1, 0 or +1 day. */
@Singleton
class HijriPreferences @Inject constructor(@ApplicationContext private val context: Context) {
    private val changes = MutableStateFlow(0)

    fun adjustment(userId: String): Int = read(context, userId)

    fun setAdjustment(userId: String, days: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putInt(key(userId), days.coerceIn(HijriDate.adjustments)) }
        changes.update { it + 1 }
    }

    /** The current adjustment, then every change, so a screen that stays open follows edits made elsewhere. */
    fun adjustments(userId: String): Flow<Int> = changes.map { read(context, userId) }.distinctUntilChanged()

    companion object {
        private const val FILE = "hijri_preferences"
        private fun key(userId: String) = "$userId/adjustment"

        fun read(context: Context, userId: String): Int =
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(key(userId), 0).coerceIn(HijriDate.adjustments)
    }
}
