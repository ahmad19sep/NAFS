package com.ahmad.nafs.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedProfileEntity::class,
        PendingMutationEntity::class,
        CachedTaskEntity::class,
        TaskCacheMetadataEntity::class,
        CachedHabitEntity::class,
        CachedHabitLogEntity::class,
        HabitCacheMetadataEntity::class,
        CachedGoalEntity::class,
        CachedGoalMilestoneEntity::class,
        GoalCacheMetadataEntity::class,
        CachedChallengeEntity::class,
        CachedChallengeCheckInEntity::class,
        ChallengeCacheMetadataEntity::class,
        CachedHealthProfileEntity::class,
        CachedHealthLogEntity::class,
        HealthCacheMetadataEntity::class,
        CachedDeenLogEntity::class,
        CachedCheckInEntity::class,
        CachedCoachEntity::class,
        CachedReportEntity::class,
        CachedLetterEntity::class,
        CachedRecoveryEntity::class,
        RecordCacheEntity::class,
    ],
    version = 14,
    exportSchema = true,
)
abstract class NafsDatabase : RoomDatabase() {
    abstract fun cachedProfileDao(): CachedProfileDao
    abstract fun pendingMutationDao(): PendingMutationDao
    abstract fun cachedTaskDao(): CachedTaskDao
    abstract fun cachedHabitDao(): CachedHabitDao
    abstract fun cachedGoalDao(): CachedGoalDao
    abstract fun cachedChallengeDao(): CachedChallengeDao
    abstract fun cachedHealthDao(): CachedHealthDao
    abstract fun cachedDeenDao(): CachedDeenDao
    abstract fun cachedCheckInDao(): CachedCheckInDao
    abstract fun cachedCoachDao(): CachedCoachDao
    abstract fun cachedReportDao(): CachedReportDao
    abstract fun cachedLetterDao(): CachedLetterDao
    abstract fun cachedRecoveryDao(): CachedRecoveryDao
    abstract fun recordCacheDao(): RecordCacheDao
}

