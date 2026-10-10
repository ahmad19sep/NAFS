package com.ahmad.nafs.core.plans

import java.time.LocalDate

interface RecoveryRepository {
    suspend fun load(userId: String): RecoveryLoaded
    suspend fun storeDraft(userId: String, draft: String)
    suspend fun generate(userId: String, intent: RecoveryIntent): RecoveryGenerateResult
    suspend fun select(userId: String, planId: String, index: Int, selected: Boolean): RecoverySnapshot
    suspend fun apply(userId: String, planId: String, today: LocalDate, timezone: String): RecoveryApplyResult
    suspend fun discard(userId: String)
    suspend fun clear(userId: String)
}
interface RecoveryCache {
    suspend fun read(userId: String): RecoverySnapshot?
    suspend fun write(userId: String, snapshot: RecoverySnapshot)
    suspend fun clear(userId: String)
}
fun interface RecoveryRemote { suspend fun generate(userId: String, intent: RecoveryIntent): RecoveryResponse }
interface PlanItemWriter {
    suspend fun checkOnline(userId: String)
    suspend fun inspect(userId: String, proposal: PlanProposal, step: PlanStepState): PlanItemState
    suspend fun create(userId: String, proposal: PlanProposal, step: PlanStepState, timezone: String): Boolean
}
