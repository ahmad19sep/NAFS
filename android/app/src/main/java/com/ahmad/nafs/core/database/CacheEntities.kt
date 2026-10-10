package com.ahmad.nafs.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "cached_recovery")
data class CachedRecoveryEntity(
    @PrimaryKey val userId: String,
    val payloadCiphertext: String,
    val payloadIv: String,
)

@Entity(tableName = "cached_letters")
data class CachedLetterEntity(
    @PrimaryKey val userId: String,
    val payloadCiphertext: String,
    val payloadIv: String,
)

@Entity(tableName = "cached_reports")
data class CachedReportEntity(
    @PrimaryKey val userId: String,
    val payloadCiphertext: String,
    val payloadIv: String,
)

@Entity(tableName = "cached_coach")
data class CachedCoachEntity(
    @PrimaryKey val userId: String,
    val payloadCiphertext: String,
    val payloadIv: String,
)

@Entity(tableName = "cached_profiles")
data class CachedProfileEntity(
    @PrimaryKey val userId: String,
    val name: String,
    val email: String,
    val timezone: String,
    val avatarUrl: String?,
    val onboardingComplete: Boolean,
    val serverUpdatedAt: String?,
    val cachedAtEpochMillis: Long,
    @androidx.room.ColumnInfo(defaultValue = "0") val deenEnabled: Boolean = false,
)

@Entity(tableName = "cached_deen_logs", primaryKeys = ["userId", "date"])
data class CachedDeenLogEntity(
    val userId: String,
    val date: String,
    val payloadCiphertext: String,
    val payloadIv: String,
)

@Entity(tableName = "cached_checkins", primaryKeys = ["userId", "date"])
data class CachedCheckInEntity(
    val userId: String,
    val date: String,
    val payloadCiphertext: String,
    val payloadIv: String,
)

@Entity(
    tableName = "pending_mutations",
    indices = [Index("userId"), Index("nextAttemptAtEpochMillis")],
)
data class PendingMutationEntity(
    @PrimaryKey val requestId: String,
    val userId: String,
    val operation: String,
    val payloadJson: String,
    val createdAtEpochMillis: Long,
    val attemptCount: Int = 0,
    val nextAttemptAtEpochMillis: Long = createdAtEpochMillis,
    val lastError: String? = null,
)

@Entity(
    tableName = "cached_tasks",
    indices = [Index("userId"), Index(value = ["userId", "type", "periodDate"])],
)
data class CachedTaskEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val note: String?,
    val type: String,
    val priority: String,
    val status: String,
    val periodDate: String,
    val dueTime: String?,
    val completedAt: String?,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(tableName = "task_cache_metadata")
data class TaskCacheMetadataEntity(
    @PrimaryKey val userId: String,
    val lastSyncedAtEpochMillis: Long,
)

@Entity(
    tableName = "cached_habits",
    indices = [Index("userId"), Index(value = ["userId", "sortOrder"])],
)
data class CachedHabitEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val emoji: String,
    val type: String,
    val targetValue: Double,
    val unit: String,
    val timeTargetMinutes: Int,
    val subjectName: String?,
    val subjectTotal: Int?,
    val subjectPosition: Int,
    val subjectUnit: String?,
    val scheduleKind: String,
    val scheduleDays: String?,
    val weeklyTarget: Int?,
    val isPaused: Boolean,
    val sortOrder: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    /** "HH:MM", or null for no reminder. Added in version 15. */
    val reminderTime: String? = null,
)

@Entity(
    tableName = "cached_habit_logs",
    primaryKeys = ["userId", "habitId", "date"],
    indices = [Index(value = ["userId", "date"])],
)
data class CachedHabitLogEntity(
    val userId: String,
    val habitId: String,
    val date: String,
    val completed: Boolean,
    val value: Double,
    val durationMinutes: Int,
    val notes: String?,
)

@Entity(tableName = "habit_cache_metadata")
data class HabitCacheMetadataEntity(
    @PrimaryKey val userId: String,
    val lastSyncedAtEpochMillis: Long,
)

@Entity(
    tableName = "cached_goals",
    indices = [Index("userId"), Index(value = ["userId", "createdAt"])],
)
data class CachedGoalEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val emoji: String,
    val description: String?,
    val type: String,
    val category: String?,
    val startDate: String,
    val deadline: String?,
    val storedProgressPercent: Int,
    val targetValue: Double?,
    val currentValue: Double?,
    val unit: String?,
    val linkedHabitIds: String,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(
    tableName = "cached_goal_milestones",
    indices = [Index("userId"), Index("goalId")],
)
data class CachedGoalMilestoneEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val goalId: String,
    val title: String,
    val done: Boolean,
    val doneAt: String?,
    val targetDate: String?,
    val createdAt: String,
)

@Entity(tableName = "goal_cache_metadata")
data class GoalCacheMetadataEntity(
    @PrimaryKey val userId: String,
    val lastSyncedAtEpochMillis: Long,
)

@Entity(
    tableName = "cached_challenges",
    indices = [Index("userId"), Index(value = ["userId", "createdAt"])],
)
data class CachedChallengeEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val emoji: String,
    val description: String,
    val frequency: String,
    val durationDays: Int,
    val startDate: String,
    val requiresPhoto: Boolean,
    val sadqaAmount: Double?,
    val sadqaCurrency: String,
    val currentStreak: Int,
    val longestStreak: Int,
    val status: String,
    val restartCount: Int,
    val lastRestartAt: String?,
    val createdAt: String,
)

@Entity(
    tableName = "cached_challenge_checkins",
    indices = [Index("userId"), Index("challengeId"), Index(value = ["userId", "date"])],
)
data class CachedChallengeCheckInEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val challengeId: String,
    val date: String,
    val completed: Boolean,
    val photoUrl: String?,
    val sadqaPaid: Boolean,
    val createdAt: String,
)

@Entity(tableName = "challenge_cache_metadata")
data class ChallengeCacheMetadataEntity(
    @PrimaryKey val userId: String,
    val lastSyncedAtEpochMillis: Long,
)

@Entity(tableName = "cached_health_profiles")
data class CachedHealthProfileEntity(
    @PrimaryKey val userId: String,
    val payloadCiphertext: String,
    val payloadIv: String,
)

@Entity(
    tableName = "cached_health_logs",
    primaryKeys = ["userId", "date"],
    indices = [Index("userId")],
)
data class CachedHealthLogEntity(
    val userId: String,
    val date: String,
    val id: String,
    val payloadCiphertext: String,
    val payloadIv: String,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(tableName = "health_cache_metadata")
data class HealthCacheMetadataEntity(
    @PrimaryKey val userId: String,
    val lastSyncedAtEpochMillis: Long,
)

