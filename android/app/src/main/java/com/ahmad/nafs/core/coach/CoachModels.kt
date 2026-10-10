package com.ahmad.nafs.core.coach

import com.ahmad.nafs.core.time.ServerTimestamp
import com.ahmad.nafs.core.network.ApiFailure
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CoachMessage(val role: String, val content: String)

@Serializable
data class CoachConversation(
    val id: String,
    @SerialName("user_id") val userId: String,
    val messages: List<CoachMessage>,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
) {
    val title: String get() = messages.firstOrNull()?.content?.take(80) ?: "Conversation"
}

@Serializable
data class CoachSendIntent(
    val messages: List<CoachMessage>,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("expected_updated_at") val expectedUpdatedAt: String?,
    @SerialName("request_id") val requestId: String = UUID.randomUUID().toString(),
)

@Serializable
data class CoachSnapshot(
    val conversations: List<CoachConversation> = emptyList(),
    val pending: CoachSendIntent? = null,
)

sealed interface CoachLoadResult {
    data class Loaded(val snapshot: CoachSnapshot, val isOffline: Boolean) : CoachLoadResult
    data class Failure(val message: String) : CoachLoadResult
}

sealed interface CoachSendResult {
    data class Saved(val conversation: CoachConversation, val contextPartial: Boolean) : CoachSendResult
    data class Failure(val failure: ApiFailure, val message: String) : CoachSendResult
}

object CoachRules {
    const val MAX_MESSAGES = 80
    const val MAX_USER_LENGTH = 4_000

    /** A refresh must not replace a send or confirmed reply cached while its network read was running. */
    fun refreshed(userId: String, initial: CoachSnapshot?, latest: CoachSnapshot?, remote: List<CoachConversation>): CoachSnapshot {
        val concurrent = latest?.conversations.orEmpty().filter { current ->
            initial?.conversations?.find { it.id == current.id } != current
        }
        val conversations = remote.toMutableList()
        concurrent.forEach { current ->
            val index = conversations.indexOfFirst { it.id == current.id }
            if (index < 0) conversations.add(0, current)
            else if (ServerTimestamp.parse(current.updatedAt) > ServerTimestamp.parse(conversations[index].updatedAt)) conversations[index] = current
        }
        val pending = latest?.pending?.takeUnless { intent -> conversations.any { recovered(userId, intent, it) } }
        return CoachSnapshot(conversations, pending)
    }

    fun messageError(history: List<CoachMessage>, draft: String): String? = when {
        draft.trim().isEmpty() -> "Write a message first."
        draft.trim().length > MAX_USER_LENGTH -> "Keep your message within 4,000 characters."
        history.size >= MAX_MESSAGES - 1 || history.sumOf { it.content.length } + draft.trim().length > 180_000 ->
            "Start a new conversation to keep this chat manageable."
        history.size % 2 != 0 || !validMessages(history) -> "Reload this conversation before sending."
        else -> null
    }

    fun validConversation(userId: String, conversation: CoachConversation): Boolean =
        conversation.userId == userId && validId(conversation.id) &&
            conversation.messages.all { it.role in listOf("user", "assistant") && it.content.length <= 180_000 } &&
            validTime(conversation.createdAt) && validTime(conversation.updatedAt)

    fun validIntent(intent: CoachSendIntent): Boolean =
        validId(intent.conversationId) && validId(intent.requestId) && intent.messages.size % 2 == 1 &&
            intent.messages.isNotEmpty() && intent.messages.size < MAX_MESSAGES && validMessages(intent.messages) &&
            intent.messages.sumOf { it.content.length } <= 180_000 &&
            (intent.expectedUpdatedAt?.let(::validTime) ?: (intent.messages.size == 1))

    fun recovered(userId: String, intent: CoachSendIntent, conversation: CoachConversation): Boolean =
        validIntent(intent) && validConversation(userId, conversation) && conversation.id == intent.conversationId &&
            conversation.messages.size == intent.messages.size + 1 &&
            conversation.messages.dropLast(1) == intent.messages &&
            conversation.messages.last().let { it.role == "assistant" && it.content.isNotBlank() && it.content.length <= 12_000 } &&
            (intent.expectedUpdatedAt == null || runCatching {
                ServerTimestamp.parse(conversation.updatedAt) > ServerTimestamp.parse(intent.expectedUpdatedAt)
            }.getOrDefault(false))

    fun matches(userId: String, intent: CoachSendIntent, conversation: CoachConversation, reply: String): Boolean =
        recovered(userId, intent, conversation) && conversation.messages.last().content == reply

    private fun validMessages(messages: List<CoachMessage>): Boolean = messages.allIndexed { index, message ->
        message.role == (if (index % 2 == 0) "user" else "assistant") && message.content.isNotBlank() &&
            message.content.length <= (if (message.role == "user") MAX_USER_LENGTH else 12_000)
    }

    private fun validId(value: String): Boolean = Regex("^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$").matches(value)
    private fun validTime(value: String): Boolean = runCatching { ServerTimestamp.parse(value); true }.getOrDefault(false)
    private inline fun <T> List<T>.allIndexed(predicate: (Int, T) -> Boolean): Boolean =
        withIndex().all { (index, value) -> predicate(index, value) }
}
