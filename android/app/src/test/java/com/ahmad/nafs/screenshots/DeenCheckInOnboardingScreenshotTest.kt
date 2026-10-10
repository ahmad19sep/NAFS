package com.ahmad.nafs.screenshots

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ahmad.nafs.core.checkin.CheckInTask
import com.ahmad.nafs.core.checkin.DailyCheckIn
import com.ahmad.nafs.core.deen.DeenExtraRules
import com.ahmad.nafs.core.deen.PrayerLog
import com.ahmad.nafs.ui.checkin.CheckInActions
import com.ahmad.nafs.ui.checkin.CheckInScreen
import com.ahmad.nafs.ui.checkin.CheckInUiState
import com.ahmad.nafs.ui.deen.DeenActions
import com.ahmad.nafs.ui.deen.DeenScreen
import com.ahmad.nafs.ui.deen.DeenUiState
import com.ahmad.nafs.ui.deen.PrayerTimesActions
import com.ahmad.nafs.ui.deen.PrayerTimesScreen
import com.ahmad.nafs.ui.deen.PrayerTimesState
import com.ahmad.nafs.ui.onboarding.OnboardingScreen
import com.ahmad.nafs.ui.onboarding.OnboardingUiState
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Deen, Prayer times, Daily check-in and Onboarding rendered from fixed sample state. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi", application = Application::class)
class DeenCheckInOnboardingScreenshotTest {

    // ─── Deen ────────────────────────────────────────────────────────────────

    private val prayerLog = PrayerLog(
        id = "prayers-1", userId = SampleData.USER, date = SampleData.today.toString(),
        fajr = 2, dhuhr = 1, asr = 2, maghrib = 0, isha = 0,
        extras = listOf(
            DeenExtraRules.patch(
                DeenExtraRules.patch(DeenExtraRules.newPrayer("Tahajjud"), "done", JsonPrimitive(true)),
                "emoji", JsonPrimitive("🌙"),
            ),
            DeenExtraRules.newPrayer("Witr"),
            buildJsonObject {
                put("name", "Dhikr after salah")
                put("type", "count")
                put("emoji", "📿")
                put("score_weight", 1)
                put("target", 100)
                put("value", 33)
            },
            buildJsonObject {
                put("name", "Sunnah of Fajr")
                put("status", 1)
            },
        ),
    )

    @Test fun deenToday() = captureScreen("70_deen_today") {
        // One unsaved change, so the pinned save bar shows.
        DeenScreen(SampleData.profile, DeenUiState(draft = prayerLog.copy(maghrib = 1), saved = prayerLog, isLoading = false), DeenActions())
    }

    @Test fun deenNewDay() = captureScreen("70_deen_new_day") {
        DeenScreen(
            SampleData.profile,
            DeenUiState(draft = PrayerLog("prayers-2", SampleData.USER, SampleData.today.toString()), isLoading = false),
            DeenActions(),
        )
    }

    @Test fun deenConflict() = captureScreen("70_deen_conflict") {
        DeenScreen(
            SampleData.profile,
            DeenUiState(draft = prayerLog.copy(isha = 2), saved = prayerLog, isLoading = false, hasConflict = true),
            DeenActions(),
        )
    }

    @Test fun deenFaithOff() = captureScreen("70_deen_faith_off") {
        DeenScreen(SampleData.profile.copy(deenEnabled = false), DeenUiState(isLoading = false), DeenActions())
    }

    // ─── Prayer times ────────────────────────────────────────────────────────

    private val prayerNow = ZonedDateTime.of(2026, 10, 10, 15, 10, 0, 0, ZoneId.of("Asia/Karachi"))

    private val prayerTimes = buildJsonObject {
        put("date", SampleData.today.toString())
        put("zone", "Asia/Karachi")
        put("city", "Lahore")
        put("country", "Pakistan")
        put("method", 1)
        put("hanafi", true)
        put("times", buildJsonObject {
            put("Fajr", "04:52")
            put("Dhuhr", "11:58")
            put("Asr", "15:52")
            put("Maghrib", "17:45")
            put("Isha", "19:02")
        })
    }

    @Test fun prayerTimesToday() = captureScreen("71_prayer_times_today") {
        PrayerTimesScreen(
            PrayerTimesState(city = "Lahore", country = "Pakistan", data = prayerTimes, cached = true, area = "Lahore"),
            prayerNow,
            "Asia/Karachi",
            PrayerTimesActions(),
        )
    }

    @Test fun prayerTimesEmpty() = captureScreen("71_prayer_times_empty") {
        PrayerTimesScreen(
            PrayerTimesState(error = "Location was not allowed. Enter a city below, or allow location for NAFS in Android settings."),
            prayerNow,
            "Asia/Karachi",
            PrayerTimesActions(),
        )
    }

    // ─── Daily check-in ──────────────────────────────────────────────────────

    private val checkIn = DailyCheckIn(
        id = "checkin-1", userId = SampleData.USER, date = SampleData.today.toString(),
        tasks = listOf(
            CheckInTask("Finish the quarterly report draft", done = true),
            CheckInTask("Call Ammi after Maghrib"),
            CheckInTask("30 minutes of Quran after Fajr", done = true),
            CheckInTask("Review Bilal's pull request"),
        ),
        updatedAt = "2026-10-10T06:00:00Z",
    )

    private val reviewed = checkIn.copy(
        eveningText = "Got the report out before lunch and kept the Quran slot. Lost the afternoon to meetings, so the PR review " +
            "slipped again. Felt calmer than yesterday.",
        aiVerdict = "You protected the two things that matter most and finished the hardest task early. The PR review has slipped " +
            "twice now: book a 30-minute slot before Dhuhr tomorrow and treat it like a meeting.",
        updatedAt = "2026-10-10T18:30:00Z",
    )

    @Test fun checkInMorning() = captureScreen("72_checkin_morning") {
        CheckInScreen(
            SampleData.profile,
            CheckInUiState(draft = checkIn, saved = checkIn, isLoading = false, newTask = "Walk for 20 minutes"),
            CheckInActions(),
        )
    }

    @Test fun checkInEvening() = captureScreen("72_checkin_evening") {
        CheckInScreen(
            SampleData.profile,
            CheckInUiState(draft = reviewed, saved = reviewed, isLoading = false, isEvening = true, generatedVersion = reviewed.updatedAt),
            CheckInActions(),
        )
    }

    @Test fun checkInEmpty() = captureScreen("72_checkin_empty") {
        CheckInScreen(
            SampleData.profile,
            CheckInUiState(
                draft = DailyCheckIn("checkin-2", SampleData.USER, SampleData.today.toString(), updatedAt = "2026-10-10T06:00:00Z"),
                isLoading = false,
            ),
            CheckInActions(),
        )
    }

    // ─── Onboarding ──────────────────────────────────────────────────────────

    @Test fun onboardingWelcome() = captureScreen("08_onboarding_welcome") {
        OnboardingScreen(OnboardingUiState(userId = SampleData.USER, step = 1, name = "Ahmad"), {}, {}, {}, {}, {})
    }

    @Test fun onboardingHabits() = captureScreen("08_onboarding_habits") {
        OnboardingScreen(
            OnboardingUiState(userId = SampleData.USER, step = 2, name = "Ahmad", selectedHabitKeys = setOf("reading", "quran-reading", "water")),
            {}, {}, {}, {}, {},
        )
    }
}
