package com.ahmad.nafs.core.export

/**
 * One table in "Download my data": rows are read where [ownerColumn] equals the user's id or, for tables that
 * belong to another table's rows, where it is one of that [parent] table's exported ids. Row-level security still
 * applies on the server; the filter keeps the export to the user's own rows even if a policy were looser.
 */
data class ExportTable(
    val name: String,
    val label: String,
    val ownerColumn: String = "user_id",
    val parent: String? = null,
    /** A stable order, so paging never skips or repeats a row. */
    val orderColumns: List<String> = listOf("id"),
    /** Values that are credentials rather than records, such as a browser push subscription. */
    val redactedColumns: Set<String> = emptySet(),
    /** Without this table the export isn't worth handing over, so failing to read it fails the export. */
    val required: Boolean = false,
)

/** Every table the app and the web version keep the user's records in (see the SQL in `supabase/`), parents first. */
object ExportTables {
    val all: List<ExportTable> = listOf(
        ExportTable("users", "Profile", ownerColumn = "id", redactedColumns = setOf("push_subscription"), required = true),
        ExportTable("tasks", "Tasks"),
        ExportTable("habits", "Habits"),
        ExportTable("habit_logs", "Habit history"),
        ExportTable("goals", "Goals"),
        ExportTable("goal_milestones", "Goal milestones", ownerColumn = "goal_id", parent = "goals"),
        ExportTable("challenges", "Challenges"),
        ExportTable("challenge_checkins", "Challenge check-ins", ownerColumn = "challenge_id", parent = "challenges"),
        ExportTable(
            "challenge_attempt_history", "Earlier challenge attempts",
            orderColumns = listOf("challenge_id", "attempt", "original_id"),
        ),
        ExportTable("daily_checkins", "Daily check-ins"),
        ExportTable("health_logs", "Health"),
        ExportTable("prayer_logs", "Prayers"),
        ExportTable("quran_log", "Quran journal"),
        ExportTable("daily_logs", "Daily log"),
        ExportTable("dreams", "Dreams & mapping"),
        ExportTable("screentime_logs", "Screen time"),
        ExportTable("app_notes", "Bugs & ideas"),
        ExportTable("coach_notes", "Coach notes"),
        ExportTable("future_self_letters", "Letters to your future self"),
        ExportTable("ai_conversations", "Coach conversations"),
        ExportTable("ai_reports", "Coach reports"),
        ExportTable("report_reviews", "Weekly and monthly reviews"),
    )
}
