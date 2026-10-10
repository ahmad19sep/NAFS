package com.ahmad.nafs.screenshots

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ahmad.nafs.core.challenges.Challenge
import com.ahmad.nafs.core.challenges.ChallengeCheckIn
import com.ahmad.nafs.domain.challenges.ChallengeFrequency
import com.ahmad.nafs.domain.challenges.ChallengeStatus
import com.ahmad.nafs.domain.goals.GoalCategory
import com.ahmad.nafs.domain.goals.GoalType
import com.ahmad.nafs.ui.NafsBottomBar
import com.ahmad.nafs.ui.challenges.ChallengeActions
import com.ahmad.nafs.ui.challenges.ChallengeEditorActions
import com.ahmad.nafs.ui.challenges.ChallengeEditorContent
import com.ahmad.nafs.ui.challenges.ChallengeEditorDraft
import com.ahmad.nafs.ui.challenges.ChallengeScreen
import com.ahmad.nafs.ui.challenges.ChallengeUiState
import com.ahmad.nafs.ui.goals.GoalActions
import com.ahmad.nafs.ui.goals.GoalEditorActions
import com.ahmad.nafs.ui.goals.GoalEditorContent
import com.ahmad.nafs.ui.goals.GoalEditorDraft
import com.ahmad.nafs.ui.goals.GoalScreen
import com.ahmad.nafs.ui.goals.GoalUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goals and Challenges rendered with fixed sample state. Images are written only by
 * `./gradlew :app:recordRoborazziDebug`; plain unit test runs skip them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain Application: the real one schedules WorkManager jobs that have no place in a render test.
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi", application = Application::class)
class GoalsChallengesScreenshotTest {

    // ─── Goals ───────────────────────────────────────────────────────────────

    private val goalState = GoalUiState(
        userId = SampleData.USER,
        timezone = "Asia/Karachi",
        today = SampleData.today,
        goals = SampleData.goals,
        habits = SampleData.habits,
        isLoading = false,
    )

    private val goalDraft = GoalEditorDraft(
        title = "Run a 10K under 55 minutes",
        emoji = "🏃",
        description = "Build up from 5K with three runs a week.",
        type = GoalType.QUARTERLY,
        category = GoalCategory.HEALTH,
        deadline = SampleData.today.plusDays(48).toString(),
        hasNumericTarget = true,
        targetValue = "10",
        currentValue = "6.5",
        unit = "km",
        milestones = listOf("Run 5K without stopping", "Run 7.5K", ""),
        linkedHabitIds = listOf("h4"),
    )

    @Test fun goals() = captureScreen("40_goals") {
        Column {
            Box(Modifier.weight(1f)) { GoalScreen(goalState.copy(expandedGoalIds = setOf("g1")), GoalActions()) }
            NafsBottomBar(currentRoute = "goals", onSelect = {}, onQuickAdd = {})
        }
    }

    @Config(qualifiers = "w393dp-h1900dp-xhdpi")
    @Test fun goalsFullList() = captureScreen("41_goals_list") {
        GoalScreen(
            goalState.copy(isOffline = true, expandedGoalIds = setOf("g2"), pendingMilestoneIds = setOf("m6")),
            GoalActions(),
        )
    }

    @Test fun goalsEmpty() = captureScreen("42_goals_empty") {
        GoalScreen(GoalUiState(userId = SampleData.USER, today = SampleData.today, isLoading = false), GoalActions())
    }

    @Config(qualifiers = "w393dp-h1900dp-xhdpi")
    @Test fun goalEditor() = captureScreen("43_goals_editor") {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            GoalEditorContent(goalDraft, goalState.copy(editor = goalDraft), GoalEditorActions())
        }
    }

    @Test fun goalsLoading() = captureScreen("44_goals_loading") {
        GoalScreen(GoalUiState(userId = SampleData.USER, today = SampleData.today), GoalActions())
    }

    // ─── Challenges ──────────────────────────────────────────────────────────

    private val challengeState = ChallengeUiState(
        userId = SampleData.USER,
        timezone = "Asia/Karachi",
        today = SampleData.today,
        challenges = GoalsChallengesSamples.all,
        isLoading = false,
    )

    private val challengeDraft = ChallengeEditorDraft(
        title = "Fajr in congregation",
        emoji = "🌅",
        description = "Start every day with the people of the masjid, not with my phone.",
        frequency = ChallengeFrequency.DAILY,
        durationDays = "40",
        requiresPhoto = false,
        sadqaAmount = "500",
        sadqaCurrency = "PKR",
    )

    @Test fun challenges() = captureScreen("50_challenges") {
        ChallengeScreen(challengeState, ChallengeActions())
    }

    @Config(qualifiers = "w393dp-h2400dp-xhdpi")
    @Test fun challengesFullList() = captureScreen("51_challenges_list") {
        ChallengeScreen(
            challengeState.copy(
                selectedStatus = null,
                actionError = "That check-in didn't save. Check your connection and try again.",
                pendingChallengeIds = setOf("c3"),
            ),
            ChallengeActions(),
        )
    }

    @Test fun challengesEmpty() = captureScreen("52_challenges_empty") {
        ChallengeScreen(ChallengeUiState(userId = SampleData.USER, today = SampleData.today, isLoading = false), ChallengeActions())
    }

    @Config(qualifiers = "w393dp-h1300dp-xhdpi")
    @Test fun challengeEditor() = captureScreen("53_challenges_editor") {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            ChallengeEditorContent(challengeDraft, challengeState.copy(editor = challengeDraft), ChallengeEditorActions())
        }
    }

    @Test fun challengesLoadError() = captureScreen("54_challenges_load_error") {
        ChallengeScreen(
            ChallengeUiState(
                userId = SampleData.USER,
                today = SampleData.today,
                isLoading = false,
                loadError = "We couldn't reach NAFS. Check your connection and try again.",
            ),
            ChallengeActions(),
        )
    }
}

/** Sample challenges: two daily, one weekly with photo proof (all active) and one completed. */
private object GoalsChallengesSamples {
    private val today = SampleData.today

    private fun checkIns(challengeId: String, daysAgo: List<Long>, withPhoto: Boolean = false) = daysAgo.map { days ->
        ChallengeCheckIn(
            id = "$challengeId-$days",
            challengeId = challengeId,
            date = today.minusDays(days),
            completed = true,
            photoUrl = if (withPhoto) "https://example.com/proof/$challengeId-$days.jpg" else null,
            sadqaPaid = false,
            createdAt = "2026-10-01T00:00:00Z",
        )
    }

    private fun challenge(
        id: String,
        title: String,
        emoji: String,
        description: String,
        frequency: ChallengeFrequency,
        durationDays: Int,
        startedDaysAgo: Long,
        streak: Int,
        best: Int,
        checkIns: List<ChallengeCheckIn>,
        requiresPhoto: Boolean = false,
        sadqa: Double? = null,
        status: ChallengeStatus = ChallengeStatus.ACTIVE,
        restarts: Int = 0,
    ) = Challenge(
        id = id,
        userId = SampleData.USER,
        title = title,
        emoji = emoji,
        description = description,
        frequency = frequency,
        durationDays = durationDays,
        startDate = today.minusDays(startedDaysAgo),
        requiresPhoto = requiresPhoto,
        sadqaAmount = sadqa,
        sadqaCurrency = "PKR",
        currentStreak = streak,
        longestStreak = best,
        status = status,
        restartCount = restarts,
        lastRestartAt = if (restarts > 0) "2026-10-05T00:00:00Z" else null,
        checkIns = checkIns,
        createdAt = "2026-09-01T00:00:00Z",
    )

    val all = listOf(
        challenge(
            "c1", "Fajr in congregation", "🌅", "Start every day with the people of the masjid, not with my phone.",
            ChallengeFrequency.DAILY, durationDays = 40, startedDaysAgo = 11, streak = 11, best = 11,
            checkIns = checkIns("c1", (1L..11L).toList()), sadqa = 500.0,
        ),
        challenge(
            "c2", "No social media before noon", "📵", "Mornings are for deep work. Scrolling can wait until lunch.",
            ChallengeFrequency.DAILY, durationDays = 30, startedDaysAgo = 5, streak = 6, best = 9,
            checkIns = checkIns("c2", (0L..5L).toList()), restarts = 1,
        ),
        challenge(
            "c3", "Weekly long run", "🏃", "A photo of my watch after every long run keeps me honest.",
            ChallengeFrequency.WEEKLY, durationDays = 84, startedDaysAgo = 30, streak = 4, best = 4,
            checkIns = checkIns("c3", listOf(7L, 14L, 21L, 28L), withPhoto = true), requiresPhoto = true,
        ),
        challenge(
            "c4", "30 nights of Quran", "📖", "One page after Isha, every night, until the habit carried itself.",
            ChallengeFrequency.DAILY, durationDays = 30, startedDaysAgo = 45, streak = 30, best = 30,
            checkIns = checkIns("c4", (15L..44L).toList()), status = ChallengeStatus.COMPLETED,
        ),
    )
}
