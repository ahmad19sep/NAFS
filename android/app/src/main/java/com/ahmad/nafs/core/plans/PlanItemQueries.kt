package com.ahmad.nafs.core.plans

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal object PlanItemQueries {
    suspend fun online(client: SupabaseClient, userId: String): Boolean {
        val rows = client.from("users").select(Columns.list("id")) { filter { eq("id", userId) }; limit(1) }.decodeList<JsonObject>()
        return (rows.singleOrNull()?.get("id") as? JsonPrimitive)?.contentOrNull == userId
    }
    suspend fun inspect(client: SupabaseClient, userId: String, proposal: PlanProposal, step: PlanStepState): PlanItemState {
        require(RecoveryRules.validStep(proposal))
        val table = when (proposal.kind) { "task" -> "tasks"; "habit" -> "habits"; else -> "challenges" }
        val columns = when (proposal.kind) {
            "task" -> "id,user_id,title,note,type,priority,period_date,due_time,deleted_at"
            "habit" -> "id,user_id,name,emoji,type,target_value,unit,time_target_mins,schedule_kind,schedule_days,is_active"
            else -> "id,user_id,title,emoji,description,frequency,duration_days,start_date,requires_photo,sadqa_amount"
        }
        val rows = client.from(table).select(Columns.raw(columns)) {
            filter { eq("user_id", userId); eq("id", step.id) }; limit(1)
        }.decodeList<JsonObject>()
        check(rows.size <= 1)
        return rows.singleOrNull()?.let { if (PlanItemRules.matches(userId, proposal, step, it)) PlanItemState.ADDED else PlanItemState.CONFLICT } ?: PlanItemState.MISSING
    }
}
