package com.ahmad.nafs.core.letters

import com.ahmad.nafs.core.network.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject

class SupabaseLetterRemote @Inject constructor(private val provider: SupabaseClientProvider) : LetterRemote {
    override suspend fun list(userId: String, cursor: LetterCursor?) = LetterQueries.list(checkNotNull(provider.client), userId, cursor)
    override suspend fun get(userId: String, letterId: String) = LetterQueries.get(checkNotNull(provider.client), userId, letterId)
    override suspend fun create(intent: LetterCreateIntent) = LetterQueries.create(checkNotNull(provider.client), intent)
}

internal object LetterQueries {
    private val columns = Columns.list("id", "user_id", "content", "written_at", "target_deliver_date", "delivered_at", "ai_reply_text")
    suspend fun list(client: SupabaseClient, userId: String, cursor: LetterCursor?): List<FutureLetter> =
        client.from("future_self_letters").select(columns) {
            filter {
                eq("user_id", userId)
                cursor?.let {
                    or { lt("written_at", it.writtenAt); and { eq("written_at", it.writtenAt); lt("id", it.id) } }
                }
            }
            order("written_at", Order.DESCENDING)
            order("id", Order.DESCENDING)
            limit((LetterRules.PAGE_SIZE + 1).toLong())
        }.decodeList<FutureLetter>()

    suspend fun get(client: SupabaseClient, userId: String, letterId: String): FutureLetter? {
        val rows = client.from("future_self_letters").select(columns) {
            filter { eq("user_id", userId); eq("id", letterId) }
            limit(1)
        }.decodeList<FutureLetter>()
        require(rows.size <= 1)
        return rows.singleOrNull()
    }

    suspend fun create(client: SupabaseClient, intent: LetterCreateIntent): FutureLetter =
        client.from("future_self_letters").insert(intent) { select(columns) }.decodeSingle<FutureLetter>()
}
