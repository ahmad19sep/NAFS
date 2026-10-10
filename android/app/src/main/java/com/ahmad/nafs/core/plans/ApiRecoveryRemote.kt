package com.ahmad.nafs.core.plans

import com.ahmad.nafs.core.network.ApiFailure
import com.ahmad.nafs.core.network.NafsApiClient
import com.ahmad.nafs.core.network.NafsApiException
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonObject

class ApiRecoveryRemote @Inject constructor(private val api: NafsApiClient) : RecoveryRemote {
    private val json = Json { ignoreUnknownKeys = true }
    override suspend fun generate(userId: String, intent: RecoveryIntent): RecoveryResponse {
        require(RecoveryRules.validIntent(intent))
        val fields = json.encodeToJsonElement(intent).jsonObject.toMutableMap()
        if (!intent.fromChat) fields["mode"] = JsonPrimitive("overcome")
        val response = api.post(userId, if (intent.fromChat) "/api/ai/plan-from-chat" else "/api/ai/plan", JsonObject(fields))
        val result = try { json.decodeFromJsonElement(RecoveryResponse.serializer(), response) }
        catch (_: kotlinx.serialization.SerializationException) { throw NafsApiException(ApiFailure.INVALID_RESPONSE) }
        if (!RecoveryRules.validResponse(intent, result)) throw NafsApiException(ApiFailure.INVALID_RESPONSE)
        return result
    }
}
