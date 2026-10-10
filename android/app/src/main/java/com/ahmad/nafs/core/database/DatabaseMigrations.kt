package com.ahmad.nafs.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS cached_recovery (userId TEXT NOT NULL PRIMARY KEY, payloadCiphertext TEXT NOT NULL, payloadIv TEXT NOT NULL)")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS cached_letters (userId TEXT NOT NULL PRIMARY KEY, payloadCiphertext TEXT NOT NULL, payloadIv TEXT NOT NULL)",
        )
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS cached_reports (userId TEXT NOT NULL PRIMARY KEY, payloadCiphertext TEXT NOT NULL, payloadIv TEXT NOT NULL)",
        )
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS cached_coach (userId TEXT NOT NULL PRIMARY KEY, payloadCiphertext TEXT NOT NULL, payloadIv TEXT NOT NULL)",
        )
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cached_checkins (
                userId TEXT NOT NULL, date TEXT NOT NULL,
                payloadCiphertext TEXT NOT NULL, payloadIv TEXT NOT NULL,
                PRIMARY KEY(userId, date)
            )
            """.trimIndent(),
        )
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE cached_profiles ADD COLUMN deenEnabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cached_deen_logs (
                userId TEXT NOT NULL, date TEXT NOT NULL,
                payloadCiphertext TEXT NOT NULL, payloadIv TEXT NOT NULL,
                PRIMARY KEY(userId, date)
            )
            """.trimIndent(),
        )
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE cached_profiles ADD COLUMN avatarUrl TEXT DEFAULT NULL",
        )
        db.execSQL(
            "ALTER TABLE cached_profiles ADD COLUMN onboardingComplete INTEGER NOT NULL DEFAULT 0",
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cached_tasks (
                id TEXT NOT NULL PRIMARY KEY,
                userId TEXT NOT NULL,
                title TEXT NOT NULL,
                note TEXT,
                type TEXT NOT NULL,
                priority TEXT NOT NULL,
                status TEXT NOT NULL,
                periodDate TEXT NOT NULL,
                dueTime TEXT,
                completedAt TEXT,
                createdAt TEXT NOT NULL,
                updatedAt TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_tasks_userId ON cached_tasks (userId)")
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_cached_tasks_userId_type_periodDate
            ON cached_tasks (userId, type, periodDate)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS task_cache_metadata (
                userId TEXT NOT NULL PRIMARY KEY,
                lastSyncedAtEpochMillis INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cached_habits (
                id TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL, name TEXT NOT NULL,
                emoji TEXT NOT NULL, type TEXT NOT NULL, targetValue REAL NOT NULL,
                unit TEXT NOT NULL, timeTargetMinutes INTEGER NOT NULL, subjectName TEXT,
                subjectTotal INTEGER, subjectPosition INTEGER NOT NULL, subjectUnit TEXT,
                scheduleKind TEXT NOT NULL, scheduleDays TEXT, weeklyTarget INTEGER,
                isPaused INTEGER NOT NULL, sortOrder INTEGER NOT NULL,
                currentStreak INTEGER NOT NULL, longestStreak INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_habits_userId ON cached_habits (userId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_habits_userId_sortOrder ON cached_habits (userId, sortOrder)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cached_habit_logs (
                userId TEXT NOT NULL, habitId TEXT NOT NULL, date TEXT NOT NULL,
                completed INTEGER NOT NULL, value REAL NOT NULL,
                durationMinutes INTEGER NOT NULL, notes TEXT,
                PRIMARY KEY(userId, habitId, date)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_habit_logs_userId_date ON cached_habit_logs (userId, date)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS habit_cache_metadata (userId TEXT NOT NULL PRIMARY KEY, lastSyncedAtEpochMillis INTEGER NOT NULL)",
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cached_goals (
                id TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL, title TEXT NOT NULL,
                emoji TEXT NOT NULL, description TEXT, type TEXT NOT NULL, category TEXT,
                startDate TEXT NOT NULL, deadline TEXT, storedProgressPercent INTEGER NOT NULL,
                targetValue REAL, currentValue REAL, unit TEXT, linkedHabitIds TEXT NOT NULL,
                createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_goals_userId ON cached_goals (userId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_goals_userId_createdAt ON cached_goals (userId, createdAt)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cached_goal_milestones (
                id TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL, goalId TEXT NOT NULL,
                title TEXT NOT NULL, done INTEGER NOT NULL, doneAt TEXT, targetDate TEXT,
                createdAt TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_goal_milestones_userId ON cached_goal_milestones (userId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_goal_milestones_goalId ON cached_goal_milestones (goalId)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS goal_cache_metadata (userId TEXT NOT NULL PRIMARY KEY, lastSyncedAtEpochMillis INTEGER NOT NULL)",
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cached_challenges (
                id TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL, title TEXT NOT NULL,
                emoji TEXT NOT NULL, description TEXT NOT NULL, frequency TEXT NOT NULL,
                durationDays INTEGER NOT NULL, startDate TEXT NOT NULL,
                requiresPhoto INTEGER NOT NULL, sadqaAmount REAL, sadqaCurrency TEXT NOT NULL,
                currentStreak INTEGER NOT NULL, longestStreak INTEGER NOT NULL,
                status TEXT NOT NULL, restartCount INTEGER NOT NULL, lastRestartAt TEXT,
                createdAt TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_challenges_userId ON cached_challenges (userId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_challenges_userId_createdAt ON cached_challenges (userId, createdAt)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cached_challenge_checkins (
                id TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL, challengeId TEXT NOT NULL,
                date TEXT NOT NULL, completed INTEGER NOT NULL, photoUrl TEXT,
                sadqaPaid INTEGER NOT NULL, createdAt TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_challenge_checkins_userId ON cached_challenge_checkins (userId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_challenge_checkins_challengeId ON cached_challenge_checkins (challengeId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_challenge_checkins_userId_date ON cached_challenge_checkins (userId, date)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS challenge_cache_metadata (userId TEXT NOT NULL PRIMARY KEY, lastSyncedAtEpochMillis INTEGER NOT NULL)",
        )
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cached_health_profiles (
                userId TEXT NOT NULL PRIMARY KEY,
                payloadCiphertext TEXT NOT NULL, payloadIv TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cached_health_logs (
                userId TEXT NOT NULL, date TEXT NOT NULL, id TEXT NOT NULL,
                payloadCiphertext TEXT NOT NULL, payloadIv TEXT NOT NULL,
                createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL,
                PRIMARY KEY(userId, date)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_cached_health_logs_userId
            ON cached_health_logs (userId)
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS health_cache_metadata (userId TEXT NOT NULL PRIMARY KEY, lastSyncedAtEpochMillis INTEGER NOT NULL)",
        )
    }
}
