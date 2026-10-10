package com.ahmad.nafs.screenshots

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ahmad.nafs.core.coach.CoachConversation
import com.ahmad.nafs.core.coach.CoachMessage
import com.ahmad.nafs.core.coach.CoachSendIntent
import com.ahmad.nafs.core.letters.FutureLetter
import com.ahmad.nafs.core.letters.LetterDraft
import com.ahmad.nafs.core.plans.PlanChallenge
import com.ahmad.nafs.core.plans.PlanHabit
import com.ahmad.nafs.core.plans.PlanPreview
import com.ahmad.nafs.core.plans.PlanProposal
import com.ahmad.nafs.core.plans.PlanStepState
import com.ahmad.nafs.core.plans.PlanTask
import com.ahmad.nafs.core.plans.RecoveryPlan
import com.ahmad.nafs.core.plans.RecoveryResponse
import com.ahmad.nafs.core.plans.RecoverySnapshot
import com.ahmad.nafs.core.reports.AiReport
import com.ahmad.nafs.core.reports.ReportCursor
import com.ahmad.nafs.core.reports.ReportFilter
import com.ahmad.nafs.core.reports.ReportSummary
import com.ahmad.nafs.ui.advice.AdviceAction
import com.ahmad.nafs.ui.advice.AdviceActions
import com.ahmad.nafs.ui.advice.AdviceApplyState
import com.ahmad.nafs.ui.advice.AdviceScreen
import com.ahmad.nafs.ui.advice.AdviceState
import com.ahmad.nafs.ui.coach.CoachScreen
import com.ahmad.nafs.ui.coach.CoachUiState
import com.ahmad.nafs.ui.letters.LetterActions
import com.ahmad.nafs.ui.letters.LetterScreen
import com.ahmad.nafs.ui.letters.LetterUiState
import com.ahmad.nafs.ui.letters.LettersScreen
import com.ahmad.nafs.ui.plans.RecoveryActions
import com.ahmad.nafs.ui.plans.RecoveryScreen
import com.ahmad.nafs.ui.plans.RecoveryUiState
import com.ahmad.nafs.ui.reports.ReportDetailScreen
import com.ahmad.nafs.ui.reports.ReportDetailUiState
import com.ahmad.nafs.ui.reports.ReportListScreen
import com.ahmad.nafs.ui.reports.ReportListUiState
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Coach chat, reports, future-self letters, recovery plans and advice rendered from fixed sample state. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi", application = Application::class)
class CoachScreenshotTest : ScreenshotTestBase() {

    // ─── Coach ───────────────────────────────────────────────────────────────

    private val chatId = "4f0c2b1e-6a7d-4c3e-9b1a-2d5e8f7a9c10"

    private val chat = CoachConversation(
        id = chatId,
        userId = SampleData.USER,
        messages = listOf(
            CoachMessage("user", "I skipped my workout twice this week and I feel like I'm slipping. How bad is it really?"),
            CoachMessage(
                "assistant",
                "Honestly? Not as bad as it feels.\n\nYou trained **3 of 5** planned days and kept your reading streak at 12. " +
                    "Two missed workouts are a dip, not a collapse.\n\n- Both misses were on late-meeting days\n" +
                    "- You slept under six hours the night before each one\n\nWhat would make Wednesday's session easier to start?",
            ),
            CoachMessage("user", "Probably laying my kit out the night before and going right after Fajr."),
            CoachMessage(
                "assistant",
                "That's a strong plan. A morning session removes the late-meeting risk entirely. " +
                    "Want to turn this into a small recovery plan with one habit and one task?",
            ),
        ),
        createdAt = "2026-10-08T12:20:00Z",
        updatedAt = "2026-10-08T12:31:00Z",
    )

    private val olderChat = CoachConversation(
        id = "9b2c3d4e-5f60-4a71-8b92-a3b4c5d6e7f8",
        userId = SampleData.USER,
        messages = listOf(
            CoachMessage("user", "Help me plan a calmer Ramadan routine"),
            CoachMessage("assistant", "Let's start with sleep and suhoor, then fit everything else around the prayers."),
        ),
        createdAt = "2026-09-30T12:00:00Z",
        updatedAt = "2026-09-30T12:05:00Z",
    )

    @Test fun coachChat() = captureScreen("80_coach_chat") {
        CoachScreen(
            CoachUiState(conversations = listOf(chat, olderChat), selectedId = chatId, isLoading = false),
            {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
        )
    }

    @Test fun coachEmpty() = captureScreen("80_coach_empty") {
        CoachScreen(CoachUiState(isLoading = false), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }

    @Test fun coachRetry() = captureScreen("80_coach_retry") {
        CoachScreen(
            CoachUiState(
                conversations = listOf(chat),
                selectedId = chatId,
                pending = CoachSendIntent(chat.messages + CoachMessage("user", "Can we add a short stretching habit too?"), chatId, chat.updatedAt),
                isLoading = false,
                pendingReloaded = true,
                error = "Your last message is retained. Retry the same request or reload to recover its reply.",
            ),
            {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
        )
    }

    // ─── Reports ─────────────────────────────────────────────────────────────

    private fun report(index: Int, type: String, generatedAt: String, weekStart: String? = null) = ReportSummary(
        id = "00000000-0000-4000-8000-${index.toString().padStart(12, '0')}",
        userId = SampleData.USER,
        type = type,
        weekStart = weekStart,
        generatedAt = generatedAt,
        modelUsed = "nafs-coach",
    )

    private val reports = listOf(
        report(5, "pull", "2026-10-10T16:30:00Z"),
        report(4, "tribunal", "2026-10-05T15:00:00Z", weekStart = "2026-09-28"),
        report(3, "letter_reply", "2026-10-02T09:10:00Z"),
        report(2, "gap", "2026-09-27T18:45:00Z"),
        report(1, "growth", "2026-09-20T08:00:00Z", weekStart = "2026-09-14"),
    )

    @Test fun reportsList() = captureScreen("81_reports_list") {
        ReportListScreen(
            ReportListUiState(reports = reports, next = ReportCursor(reports.last().generatedAt, reports.last().id), isLoading = false),
            SampleData.profile.timezone, {}, {}, {}, {}, {},
        )
    }

    @Test fun reportsEmpty() = captureScreen("81_reports_empty") {
        ReportListScreen(ReportListUiState(filter = ReportFilter.TRIBUNAL, isLoading = false), SampleData.profile.timezone, {}, {}, {}, {}, {})
    }

    @Test fun reportDetail() = captureScreen("81_reports_detail") {
        val summary = reports[1]
        ReportDetailScreen(
            ReportDetailUiState(
                report = AiReport(
                    id = summary.id, userId = summary.userId, type = summary.type, weekStart = summary.weekStart,
                    contentMd = "## The verdict\nYou kept **4 of 6** promises this week. That is real progress, but the two you broke " +
                        "were the ones that matter most to you.\n\n## What went well\n- Fajr on time six days out of seven\n" +
                        "- Reading streak extended to 12 days\n- Every high-priority task was finished\n\n## What slipped\n" +
                        "1. Two workouts missed after late meetings\n2. Screen time crept above three hours on Thursday\n\n" +
                        "> Discipline is choosing between what you want now and what you want most.\n\n### Next week\n" +
                        "Lay out your kit the night before, and protect the hour after Fajr.",
                    generatedAt = summary.generatedAt, modelUsed = summary.modelUsed,
                ),
                isLoading = false,
            ),
            SampleData.profile.timezone, {}, {},
        )
    }

    // ─── Letters ─────────────────────────────────────────────────────────────

    private fun letter(index: Int, content: String, writtenAt: String, target: String, delivered: String? = null, reply: String? = null) =
        FutureLetter(
            id = "10000000-0000-4000-8000-${index.toString().padStart(12, '0')}",
            userId = SampleData.USER,
            content = content,
            writtenAt = writtenAt,
            targetDate = target,
            deliveredAt = delivered,
            aiReplyText = reply,
        )

    private val repliedLetter = letter(
        1,
        "Dear future me, I hope you finally kept the promise to pray Fajr on time. I hope you are kinder to Ammi, " +
            "and that you stopped scrolling past midnight. If you haven't, start again today. You always can.",
        "2026-03-01T19:30:00Z", "2026-09-01", delivered = "2026-09-02T06:00:00Z",
        reply = "You wrote this in March, worried about **Fajr** and late nights.\n\n" +
            "- You've prayed Fajr on time 23 of the last 30 days\n- Your screen time after midnight fell by half\n\n" +
            "You kept more of this promise than you think. Keep going.",
    )

    private val letters = listOf(
        letter(3, "Six months from now I want to be running 10K without stopping and reading every night before sleep.",
            "2026-10-01T18:00:00Z", "2027-04-01"),
        letter(2, "By now the career change should be real. Remember why you started: more time at home, more meaning at work.",
            "2026-04-02T20:15:00Z", "2026-10-02"),
        repliedLetter,
    )

    @Test fun lettersArchive() = captureScreen("82_letters_archive") {
        LettersScreen(LetterUiState(today = SampleData.today, letters = letters, isLoading = false), SampleData.profile, LetterActions())
    }

    @Test fun lettersEmpty() = captureScreen("82_letters_empty") {
        LettersScreen(LetterUiState(today = SampleData.today, isLoading = false), SampleData.profile, LetterActions())
    }

    @Test fun lettersEditor() = captureScreen("82_letters_editor") {
        LettersScreen(
            LetterUiState(
                screen = LetterScreen.EDITOR,
                today = SampleData.today,
                letters = letters,
                draft = LetterDraft(
                    "10000000-0000-4000-8000-000000000009", SampleData.USER,
                    "Dear future me,\n\nRight now I'm trying to wake up for Fajr without the snooze button. " +
                        "I hope by the time you read this it feels normal.",
                    SampleData.today.plusMonths(6).toString(),
                ),
                draftSaved = true,
                isLoading = false,
            ),
            SampleData.profile,
            LetterActions(),
        )
    }

    @Test fun letterReply() = captureScreen("82_letters_reply") {
        LettersScreen(
            LetterUiState(screen = LetterScreen.DETAIL, today = SampleData.today, letters = letters, selected = repliedLetter, isLoading = false),
            SampleData.profile,
            LetterActions(),
        )
    }

    // ─── Recovery plan ───────────────────────────────────────────────────────

    private val plan = PlanPreview(
        response = RecoveryResponse(
            recovery = RecoveryPlan(
                problem = "Workouts keep slipping after late meetings",
                approach = "Move training to the morning, make starting effortless, and use a short streak to rebuild momentum.",
                steps = listOf(
                    PlanProposal(
                        "habit", "Morning workout", "🏋️",
                        "Training right after Fajr avoids the late-meeting days that broke your last two sessions.",
                        habit = PlanHabit(type = "duration", minutes = 30, schedule = "weekdays", days = listOf("mon", "wed", "fri")),
                    ),
                    PlanProposal(
                        "task", "Lay out gym kit tonight", "🎒",
                        "Removing the first decision makes the morning start almost automatic.",
                        task = PlanTask(priority = "medium", dueTime = "21:30"),
                    ),
                    PlanProposal(
                        "challenge", "Seven strong mornings", "🔥",
                        "A short, visible streak rebuilds trust with yourself quickly.",
                        challenge = PlanChallenge(frequency = "daily", duration = 7, requiresPhoto = false),
                    ),
                ),
            ),
            provider = "anthropic",
            sourceDate = "2026-10-10",
            requestId = "20000000-0000-4000-8000-000000000001",
        ),
        steps = listOf(
            PlanStepState("20000000-0000-4000-8000-000000000011", selected = true),
            PlanStepState("20000000-0000-4000-8000-000000000012", selected = true, applyDate = "2026-10-10", added = true),
            PlanStepState("20000000-0000-4000-8000-000000000013", selected = false),
        ),
    )

    @Test fun recoveryPreview() = captureScreen("83_plans_preview") {
        RecoveryScreen(
            RecoveryUiState(snapshot = RecoverySnapshot(SampleData.USER, preview = plan), today = SampleData.today,
                timezone = SampleData.profile.timezone, isLoading = false, draftSaved = true),
            SampleData.USER, fromChatAvailable = false, actions = RecoveryActions(),
        )
    }

    @Test fun recoveryProblem() = captureScreen("83_plans_problem") {
        RecoveryScreen(
            RecoveryUiState(snapshot = RecoverySnapshot(SampleData.USER, draft = "I keep skipping workouts when meetings run late."),
                today = SampleData.today, timezone = SampleData.profile.timezone, isLoading = false, draftSaved = true),
            SampleData.USER, fromChatAvailable = true, actions = RecoveryActions(),
        )
    }

    // ─── Advice ──────────────────────────────────────────────────────────────

    private val adviceResult = buildJsonObject {
        put("starter_pack", buildJsonObject {
            put("summary", "Start smaller than you think: two pages after Isha, every day, before any phone time.")
            put("suggested_tasks", buildJsonArray {
                add(buildJsonObject { put("title", "Put tonight's book on your pillow"); put("priority", "low") })
            })
            put("suggested_habits", buildJsonArray {
                add(buildJsonObject { put("name", "Read two pages"); put("emoji", "📖"); put("type", "boolean") })
            })
        })
    }

    @Test fun advice() = captureScreen("84_advice_result") {
        AdviceScreen(
            AdviceState(
                action = AdviceAction.HABIT,
                rows = listOf(
                    buildJsonObject { put("id", "h1"); put("name", "Read") },
                    buildJsonObject { put("id", "h2"); put("name", "Drink water") },
                ),
                selectedId = "h1",
                result = adviceResult,
            ),
            AdviceApplyState(
                ready = true,
                steps = listOf(
                    PlanStepState("30000000-0000-4000-8000-000000000001", selected = false),
                    PlanStepState("30000000-0000-4000-8000-000000000002", selected = false, applyDate = "2026-10-10", added = true),
                ),
            ),
            reviewMode = false,
            actions = AdviceActions(),
        )
    }

    @Test fun adviceEmpty() = captureScreen("84_advice_empty") {
        AdviceScreen(AdviceState(action = AdviceAction.CHALLENGE), AdviceApplyState(), reviewMode = false, actions = AdviceActions())
    }
}
