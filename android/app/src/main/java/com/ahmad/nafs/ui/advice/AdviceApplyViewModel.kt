package com.ahmad.nafs.ui.advice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.plans.*
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.records.PrivateRecordStore
import com.ahmad.nafs.core.time.AccountDate
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

data class AdviceApplyState(val ready: Boolean = false, val busy: Boolean = false,
    val steps: List<PlanStepState> = emptyList(), val message: String? = null)

@HiltViewModel
class AdviceApplyViewModel @Inject constructor(private val store: PrivateRecordStore,
    private val writer: NativePlanItemWriter) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(AdviceApplyState()); val state = _state.asStateFlow()
    private var key = ""
    private var account = ""
    fun initialize(profile: UserProfile, result: JsonObject) {
        val nextKey = AdviceProposals.key(result)
        if (nextKey == key && profile.userId == account) return
        key = nextKey; account = profile.userId; _state.value = AdviceApplyState()
        val proposals = AdviceProposals.parse(result)
        viewModelScope.launch {
            try {
                val cached = store.read(profile.userId, nextKey)
                val steps = cached?.get("steps")?.let { json.decodeFromJsonElement<List<PlanStepState>>(it) }
                    ?: proposals.map { PlanStepState(UUID.randomUUID().toString(), selected = false) }
                require(steps.size == proposals.size && steps.all { UUID.fromString(it.id).toString() == it.id })
                if (key == nextKey && account == profile.userId) _state.value = AdviceApplyState(ready = true, steps = steps)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { if (key == nextKey) _state.value = AdviceApplyState(message = "Suggestion storage could not be read. Creation is blocked; reload advice.") }
        }
    }
    fun add(profile: UserProfile, result: JsonObject, index: Int) {
        val current = _state.value
        val cacheKey = AdviceProposals.key(result)
        if (!current.ready || current.busy || key != cacheKey || account != profile.userId) return
        val proposal = AdviceProposals.parse(result).getOrNull(index) ?: return
        val previous = current.steps.getOrNull(index) ?: return
        if (previous.added) return
        _state.value = current.copy(busy = true, message = null)
        viewModelScope.launch {
            var steps = current.steps.toMutableList()
            try {
                val frozen = previous.copy(applyDate = previous.applyDate ?: AccountDate.today(profile.timezone).toString())
                steps[index] = frozen
                // Freeze the row UUID, definition identity and date before contacting the server.
                store.write(profile.userId, cacheKey, buildJsonObject { put("steps", json.encodeToJsonElement(steps.toList())) })
                writer.checkOnline(profile.userId)
                when (writer.inspect(profile.userId, proposal, frozen)) {
                    PlanItemState.CONFLICT -> error("Existing row differs")
                    PlanItemState.MISSING -> check(writer.create(profile.userId, proposal, frozen, profile.timezone))
                    PlanItemState.ADDED -> Unit
                }
                steps[index] = frozen.copy(added = true)
                if (key == cacheKey) _state.value = AdviceApplyState(true, true, steps.toList(), "Added ${proposal.title}.")
                store.write(profile.userId, cacheKey, buildJsonObject { put("steps", json.encodeToJsonElement(steps.toList())) })
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                if (key == cacheKey) _state.value = AdviceApplyState(true, true, steps.toList(),
                    if (steps[index].added) "Added to your account; the local receipt could not be saved. Reload to confirm." else "Creation is unconfirmed. Retry checks the same saved item and avoids duplicates. Changed items require inspection.")
            } finally { if (key == cacheKey) _state.value = _state.value.copy(busy = false) }
        }
    }
}
