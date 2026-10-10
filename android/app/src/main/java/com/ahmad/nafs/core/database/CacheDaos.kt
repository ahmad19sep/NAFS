package com.ahmad.nafs.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedRecoveryDao {
    @Query("SELECT * FROM cached_recovery WHERE userId = :userId LIMIT 1")
    suspend fun get(userId: String): CachedRecoveryEntity?
    @Upsert suspend fun upsert(record: CachedRecoveryEntity)
    @Query("DELETE FROM cached_recovery WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}

@Dao
interface CachedLetterDao {
    @Query("SELECT * FROM cached_letters WHERE userId = :userId LIMIT 1")
    suspend fun get(userId: String): CachedLetterEntity?
    @Upsert
    suspend fun upsert(record: CachedLetterEntity)
    @Query("DELETE FROM cached_letters WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}

@Dao
interface CachedReportDao {
    @Query("SELECT * FROM cached_reports WHERE userId = :userId LIMIT 1")
    suspend fun get(userId: String): CachedReportEntity?

    @Upsert
    suspend fun upsert(record: CachedReportEntity)

    @Query("DELETE FROM cached_reports WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}

@Dao
interface CachedCoachDao {
    @Query("SELECT * FROM cached_coach WHERE userId = :userId LIMIT 1")
    suspend fun get(userId: String): CachedCoachEntity?

    @Upsert
    suspend fun upsert(record: CachedCoachEntity)

    @Query("DELETE FROM cached_coach WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}

@Dao
interface CachedCheckInDao {
    @Query("SELECT * FROM cached_checkins WHERE userId = :userId AND date = :date LIMIT 1")
    suspend fun get(userId: String, date: String): CachedCheckInEntity?

    @Upsert
    suspend fun upsert(record: CachedCheckInEntity)

    @Query("DELETE FROM cached_checkins WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}

@Dao
interface CachedDeenDao {
    @Query("SELECT * FROM cached_deen_logs WHERE userId = :userId AND date = :date LIMIT 1")
    suspend fun get(userId: String, date: String): CachedDeenLogEntity?

    @Upsert
    suspend fun upsert(log: CachedDeenLogEntity)

    @Query("DELETE FROM cached_deen_logs WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}

@Dao
interface CachedProfileDao {
    @Query("SELECT * FROM cached_profiles WHERE userId = :userId")
    fun observe(userId: String): Flow<CachedProfileEntity?>

    @Query("SELECT * FROM cached_profiles WHERE userId = :userId LIMIT 1")
    suspend fun get(userId: String): CachedProfileEntity?

    @Upsert
    suspend fun upsert(profile: CachedProfileEntity)

    @Query("DELETE FROM cached_profiles WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)
}

@Dao
interface PendingMutationDao {
    @Query(
        """
        SELECT * FROM pending_mutations
        WHERE userId = :userId AND nextAttemptAtEpochMillis <= :nowEpochMillis
        ORDER BY createdAtEpochMillis ASC
        LIMIT :limit
        """,
    )
    suspend fun readyForSync(
        userId: String,
        nowEpochMillis: Long,
        limit: Int,
    ): List<PendingMutationEntity>

    @Upsert
    suspend fun upsert(mutation: PendingMutationEntity)

    @Delete
    suspend fun delete(mutation: PendingMutationEntity)

    @Query("DELETE FROM pending_mutations WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)
}

@Dao
abstract class CachedTaskDao {
    @Query(
        """
        SELECT * FROM cached_tasks
        WHERE userId = :userId AND periodDate >= :startDate
        ORDER BY periodDate DESC, createdAt DESC
        """,
    )
    abstract suspend fun getForUserSince(userId: String, startDate: String): List<CachedTaskEntity>

    @Query("SELECT * FROM task_cache_metadata WHERE userId = :userId LIMIT 1")
    abstract suspend fun getMetadata(userId: String): TaskCacheMetadataEntity?

    @Upsert
    abstract suspend fun upsert(task: CachedTaskEntity)

    @Query("DELETE FROM cached_tasks WHERE id = :taskId AND userId = :userId")
    abstract suspend fun deleteById(userId: String, taskId: String)

    @Query("DELETE FROM cached_tasks WHERE userId = :userId")
    protected abstract suspend fun deleteTasksForUser(userId: String)

    @Query("DELETE FROM task_cache_metadata WHERE userId = :userId")
    protected abstract suspend fun deleteMetadataForUser(userId: String)

    @Upsert
    protected abstract suspend fun upsertAll(tasks: List<CachedTaskEntity>)

    @Upsert
    protected abstract suspend fun upsertMetadata(metadata: TaskCacheMetadataEntity)

    @Transaction
    open suspend fun replaceForUser(
        userId: String,
        tasks: List<CachedTaskEntity>,
        syncedAtEpochMillis: Long,
    ) {
        deleteTasksForUser(userId)
        if (tasks.isNotEmpty()) upsertAll(tasks)
        upsertMetadata(TaskCacheMetadataEntity(userId, syncedAtEpochMillis))
    }

    @Transaction
    open suspend fun clearForUser(userId: String) {
        deleteTasksForUser(userId)
        deleteMetadataForUser(userId)
    }
}

@Dao
abstract class CachedHabitDao {
    @Query("SELECT * FROM cached_habits WHERE userId = :userId ORDER BY sortOrder, name")
    abstract suspend fun getHabits(userId: String): List<CachedHabitEntity>

    @Query("SELECT * FROM cached_habit_logs WHERE userId = :userId AND date >= :startDate")
    abstract suspend fun getLogsSince(userId: String, startDate: String): List<CachedHabitLogEntity>

    @Query("SELECT * FROM habit_cache_metadata WHERE userId = :userId LIMIT 1")
    abstract suspend fun getMetadata(userId: String): HabitCacheMetadataEntity?

    @Upsert
    abstract suspend fun upsertHabit(habit: CachedHabitEntity)

    @Upsert
    abstract suspend fun upsertLog(log: CachedHabitLogEntity)

    /** Drops one archived habit. Its cached logs stay, so a restored habit still shows its history offline. */
    @Query("DELETE FROM cached_habits WHERE userId = :userId AND id = :habitId")
    abstract suspend fun deleteHabit(userId: String, habitId: String)

    @Query("DELETE FROM cached_habits WHERE userId = :userId")
    protected abstract suspend fun deleteHabits(userId: String)

    @Query("DELETE FROM cached_habit_logs WHERE userId = :userId")
    protected abstract suspend fun deleteLogs(userId: String)

    @Query("DELETE FROM habit_cache_metadata WHERE userId = :userId")
    protected abstract suspend fun deleteMetadata(userId: String)

    @Upsert
    protected abstract suspend fun upsertHabits(habits: List<CachedHabitEntity>)

    @Upsert
    protected abstract suspend fun upsertLogs(logs: List<CachedHabitLogEntity>)

    @Upsert
    protected abstract suspend fun upsertMetadata(metadata: HabitCacheMetadataEntity)

    @Transaction
    open suspend fun replaceForUser(
        userId: String,
        habits: List<CachedHabitEntity>,
        logs: List<CachedHabitLogEntity>,
        syncedAtEpochMillis: Long,
    ) {
        deleteHabits(userId)
        deleteLogs(userId)
        if (habits.isNotEmpty()) upsertHabits(habits)
        if (logs.isNotEmpty()) upsertLogs(logs)
        upsertMetadata(HabitCacheMetadataEntity(userId, syncedAtEpochMillis))
    }

    @Transaction
    open suspend fun clearForUser(userId: String) {
        deleteHabits(userId)
        deleteLogs(userId)
        deleteMetadata(userId)
    }
}

@Dao
abstract class CachedGoalDao {
    @Query("SELECT * FROM cached_goals WHERE userId = :userId ORDER BY createdAt DESC")
    abstract suspend fun getGoals(userId: String): List<CachedGoalEntity>

    @Query("SELECT * FROM cached_goal_milestones WHERE userId = :userId ORDER BY createdAt")
    abstract suspend fun getMilestones(userId: String): List<CachedGoalMilestoneEntity>

    @Query("SELECT * FROM goal_cache_metadata WHERE userId = :userId LIMIT 1")
    abstract suspend fun getMetadata(userId: String): GoalCacheMetadataEntity?

    @Upsert
    abstract suspend fun upsertGoal(goal: CachedGoalEntity)

    @Upsert
    abstract suspend fun upsertMilestones(milestones: List<CachedGoalMilestoneEntity>)

    @Query("DELETE FROM cached_goals WHERE id = :goalId AND userId = :userId")
    protected abstract suspend fun deleteGoalRow(userId: String, goalId: String)

    @Query("DELETE FROM cached_goal_milestones WHERE goalId = :goalId AND userId = :userId")
    protected abstract suspend fun deleteMilestonesForGoal(userId: String, goalId: String)

    @Query("DELETE FROM cached_goals WHERE userId = :userId")
    protected abstract suspend fun deleteGoalsForUser(userId: String)

    @Query("DELETE FROM cached_goal_milestones WHERE userId = :userId")
    protected abstract suspend fun deleteMilestonesForUser(userId: String)

    @Query("DELETE FROM goal_cache_metadata WHERE userId = :userId")
    protected abstract suspend fun deleteMetadata(userId: String)

    @Upsert
    protected abstract suspend fun upsertGoals(goals: List<CachedGoalEntity>)

    @Upsert
    protected abstract suspend fun upsertMetadata(metadata: GoalCacheMetadataEntity)

    @Transaction
    open suspend fun replaceForUser(
        userId: String,
        goals: List<CachedGoalEntity>,
        milestones: List<CachedGoalMilestoneEntity>,
        syncedAtEpochMillis: Long,
    ) {
        deleteGoalsForUser(userId)
        deleteMilestonesForUser(userId)
        if (goals.isNotEmpty()) upsertGoals(goals)
        if (milestones.isNotEmpty()) upsertMilestones(milestones)
        upsertMetadata(GoalCacheMetadataEntity(userId, syncedAtEpochMillis))
    }

    @Transaction
    open suspend fun replaceGoal(
        userId: String,
        goal: CachedGoalEntity,
        milestones: List<CachedGoalMilestoneEntity>,
    ) {
        deleteMilestonesForGoal(userId, goal.id)
        upsertGoal(goal)
        if (milestones.isNotEmpty()) upsertMilestones(milestones)
    }

    @Transaction
    open suspend fun deleteGoal(userId: String, goalId: String) {
        deleteMilestonesForGoal(userId, goalId)
        deleteGoalRow(userId, goalId)
    }

    @Transaction
    open suspend fun clearForUser(userId: String) {
        deleteMilestonesForUser(userId)
        deleteGoalsForUser(userId)
        deleteMetadata(userId)
    }
}

@Dao
abstract class CachedChallengeDao {
    @Query("SELECT * FROM cached_challenges WHERE userId = :userId ORDER BY createdAt DESC")
    abstract suspend fun getChallenges(userId: String): List<CachedChallengeEntity>

    @Query("SELECT * FROM cached_challenge_checkins WHERE userId = :userId ORDER BY date DESC")
    abstract suspend fun getCheckIns(userId: String): List<CachedChallengeCheckInEntity>

    @Query("SELECT * FROM challenge_cache_metadata WHERE userId = :userId LIMIT 1")
    abstract suspend fun getMetadata(userId: String): ChallengeCacheMetadataEntity?

    @Upsert
    protected abstract suspend fun upsertChallenges(challenges: List<CachedChallengeEntity>)

    @Upsert
    protected abstract suspend fun upsertCheckIns(checkIns: List<CachedChallengeCheckInEntity>)

    @Upsert
    protected abstract suspend fun upsertMetadata(metadata: ChallengeCacheMetadataEntity)

    @Query("DELETE FROM cached_challenges WHERE userId = :userId")
    protected abstract suspend fun deleteChallengesForUser(userId: String)

    @Query("DELETE FROM cached_challenge_checkins WHERE userId = :userId")
    protected abstract suspend fun deleteCheckInsForUser(userId: String)

    @Query("DELETE FROM challenge_cache_metadata WHERE userId = :userId")
    protected abstract suspend fun deleteMetadata(userId: String)

    @Query("DELETE FROM cached_challenges WHERE id = :challengeId AND userId = :userId")
    protected abstract suspend fun deleteChallengeRow(userId: String, challengeId: String)

    @Query("DELETE FROM cached_challenge_checkins WHERE challengeId = :challengeId AND userId = :userId")
    protected abstract suspend fun deleteCheckInsForChallenge(userId: String, challengeId: String)

    @Transaction
    open suspend fun replaceForUser(
        userId: String,
        challenges: List<CachedChallengeEntity>,
        checkIns: List<CachedChallengeCheckInEntity>,
        syncedAtEpochMillis: Long,
    ) {
        deleteChallengesForUser(userId)
        deleteCheckInsForUser(userId)
        if (challenges.isNotEmpty()) upsertChallenges(challenges)
        if (checkIns.isNotEmpty()) upsertCheckIns(checkIns)
        upsertMetadata(ChallengeCacheMetadataEntity(userId, syncedAtEpochMillis))
    }

    @Transaction
    open suspend fun replaceChallenge(
        userId: String,
        challenge: CachedChallengeEntity,
        checkIns: List<CachedChallengeCheckInEntity>,
    ) {
        deleteCheckInsForChallenge(userId, challenge.id)
        upsertChallenges(listOf(challenge))
        if (checkIns.isNotEmpty()) upsertCheckIns(checkIns)
    }

    @Transaction
    open suspend fun deleteChallenge(userId: String, challengeId: String) {
        deleteCheckInsForChallenge(userId, challengeId)
        deleteChallengeRow(userId, challengeId)
    }

    @Transaction
    open suspend fun clearForUser(userId: String) {
        deleteCheckInsForUser(userId)
        deleteChallengesForUser(userId)
        deleteMetadata(userId)
    }
}

@Dao
abstract class CachedHealthDao {
    @Query("SELECT * FROM cached_health_profiles WHERE userId = :userId LIMIT 1")
    abstract suspend fun getProfile(userId: String): CachedHealthProfileEntity?

    @Query("SELECT * FROM cached_health_logs WHERE userId = :userId AND date = :date LIMIT 1")
    abstract suspend fun getLog(userId: String, date: String): CachedHealthLogEntity?

    @Query("SELECT * FROM health_cache_metadata WHERE userId = :userId LIMIT 1")
    abstract suspend fun getMetadata(userId: String): HealthCacheMetadataEntity?

    @Upsert
    protected abstract suspend fun upsertProfile(profile: CachedHealthProfileEntity)

    @Upsert
    protected abstract suspend fun upsertLog(log: CachedHealthLogEntity)

    @Upsert
    protected abstract suspend fun upsertMetadata(metadata: HealthCacheMetadataEntity)

    @Query("DELETE FROM cached_health_profiles WHERE userId = :userId")
    protected abstract suspend fun deleteProfile(userId: String)

    @Query("DELETE FROM cached_health_logs WHERE userId = :userId")
    protected abstract suspend fun deleteLogs(userId: String)

    @Query("DELETE FROM cached_health_logs WHERE userId = :userId AND date = :date")
    protected abstract suspend fun deleteLog(userId: String, date: String)

    @Query("DELETE FROM health_cache_metadata WHERE userId = :userId")
    protected abstract suspend fun deleteMetadata(userId: String)

    @Transaction
    open suspend fun replace(
        userId: String,
        date: String,
        profile: CachedHealthProfileEntity,
        log: CachedHealthLogEntity?,
        syncedAtEpochMillis: Long,
    ) {
        upsertProfile(profile)
        deleteLog(userId, date)
        if (log != null) upsertLog(log)
        upsertMetadata(HealthCacheMetadataEntity(userId, syncedAtEpochMillis))
    }

    @Transaction
    open suspend fun replaceProfile(profile: CachedHealthProfileEntity) {
        upsertProfile(profile)
    }

    @Transaction
    open suspend fun replaceLog(log: CachedHealthLogEntity) {
        upsertLog(log)
    }

    @Transaction
    open suspend fun clearForUser(userId: String) {
        deleteLogs(userId)
        deleteProfile(userId)
        deleteMetadata(userId)
    }
}

