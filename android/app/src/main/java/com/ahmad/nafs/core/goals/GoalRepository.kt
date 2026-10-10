package com.ahmad.nafs.core.goals

interface GoalRepository {
    suspend fun load(userId: String): GoalLoadResult
    suspend fun create(intent: SaveGoalIntent): GoalActionResult
    suspend fun update(intent: SaveGoalIntent): GoalActionResult
    suspend fun setMilestoneDone(
        userId: String,
        goalId: String,
        milestoneId: String,
        done: Boolean,
    ): GoalActionResult
    suspend fun setCurrentValue(userId: String, goalId: String, value: Double): GoalActionResult
    suspend fun delete(userId: String, goalId: String): GoalActionResult
    suspend fun clear(userId: String)
}
