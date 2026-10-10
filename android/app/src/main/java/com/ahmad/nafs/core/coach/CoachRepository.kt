package com.ahmad.nafs.core.coach

interface CoachRepository {
    suspend fun load(userId: String): CoachLoadResult
    suspend fun send(userId: String, intent: CoachSendIntent): CoachSendResult
    suspend fun discardPending(userId: String)
    suspend fun clear(userId: String)
}
