package com.ahmad.nafs.core.plans

import com.ahmad.nafs.core.network.ApiFailure
import com.ahmad.nafs.core.network.ApiSessionTokens
import com.ahmad.nafs.core.network.NafsApiException
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class CachedRecoveryRepository @Inject constructor(private val cache: RecoveryCache, private val remote: RecoveryRemote,
    private val items: PlanItemWriter, private val sessions: ApiSessionTokens) : RecoveryRepository {
    private val mutex = Mutex()
    override suspend fun load(userId: String): RecoveryLoaded = mutex.withLock {
        var snapshot = read(userId)
        try {
            account(userId); items.checkOnline(userId); account(userId)
            val preview = snapshot.preview
            if (preview != null) {
                val states = preview.steps.toMutableList()
                for (i in states.indices) {
                    val step = states[i]
                    if (step.applyDate != null && !step.added) {
                        val status = items.inspect(userId, preview.response.recovery.steps[i], step)
                        account(userId)
                        states[i] = step.copy(added = status == PlanItemState.ADDED,
                            error = if (status == PlanItemState.CONFLICT) "This item changed. Open its feature before retrying." else null)
                    }
                }
                val updated = snapshot.copy(preview = preview.copy(steps = states))
                try { write(userId, updated); snapshot = updated }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { return@withLock RecoveryLoaded(updated, false, "Saved items were checked. Reload to finish updating this device.") }
            }
            RecoveryLoaded(snapshot, false)
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { account(userId); RecoveryLoaded(snapshot, true, "Showing your saved plan. Reconnect and reload before generating or adding steps.") }
    }
    override suspend fun storeDraft(userId: String, draft: String) = mutex.withLock {
        require(draft.length <= 180_000)
        val snapshot = read(userId)
        check(snapshot.pending == null && snapshot.preview == null)
        write(userId, snapshot.copy(draft = draft))
    }
    override suspend fun generate(userId: String, intent: RecoveryIntent): RecoveryGenerateResult = mutex.withLock {
        var persisted = false
        try {
            account(userId); require(RecoveryRules.validIntent(intent))
            val snapshot = read(userId)
            check(snapshot.preview == null && (snapshot.pending == null || snapshot.pending == intent))
            write(userId, snapshot.copy(pending = intent)); persisted = true
            account(userId)
            val response = remote.generate(userId, intent)
            account(userId); require(RecoveryRules.validResponse(intent, response))
            val preview = PlanPreview(response, response.recovery.steps.mapIndexed { index, _ ->
                PlanStepState(UUID.nameUUIDFromBytes("nafs-recovery-step-v1:${intent.requestId}:$index".toByteArray(Charsets.UTF_8)).toString()) })
            val saved = snapshot.copy(pending = null, preview = preview)
            // A preview cannot be applied until its stable step IDs are safely on disk.
            write(userId, saved)
            RecoveryGenerateResult.Saved(saved)
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) {
            RecoveryGenerateResult.Failure(when {
                !sessions.isCurrentUser(userId) -> "Sign in again before building a plan."
                !persisted -> "Unable to protect your planning request on this device. Check available storage and retry."
                error is NafsApiException -> message(error.failure)
                else -> "The plan could not be saved or verified. Your request is retained; retry the same request."
            }, persisted)
        }
    }
    override suspend fun select(userId: String, planId: String, index: Int, selected: Boolean): RecoverySnapshot = mutex.withLock {
        val snapshot = read(userId)
        val preview = snapshot.preview ?: error("Missing plan")
        check(preview.id == planId && index in preview.steps.indices && !preview.steps[index].added)
        val steps = preview.steps.mapIndexed { i, step -> if (i == index) step.copy(selected = selected) else step }
        snapshot.copy(preview = preview.copy(steps = steps)).also { write(userId, it) }
    }
    override suspend fun apply(userId: String, planId: String, today: LocalDate, timezone: String): RecoveryApplyResult = mutex.withLock {
        var snapshot = read(userId)
        val preview = snapshot.preview ?: error("Missing plan")
        check(preview.id == planId && snapshot.pending == null)
        account(userId)
        try { items.checkOnline(userId); account(userId) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { return@withLock RecoveryApplyResult(snapshot, "Reconnect and reload before adding these steps.") }
        for (i in preview.steps.indices) {
            val currentPreview = checkNotNull(snapshot.preview)
            var step = currentPreview.steps[i]
            if (!step.selected || step.added) continue
            val proposal = preview.response.recovery.steps[i]
            step = step.copy(applyDate = step.applyDate ?: today.toString(), error = null)
            val retained = snapshot.copy(preview = currentPreview.copy(steps = currentPreview.steps.mapIndexed { index, state -> if (index == i) step else state }))
            try { write(userId, retained); snapshot = retained }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { return@withLock RecoveryApplyResult(snapshot, "Unable to protect this step's retry on your device. No further steps were sent.") }
            try {
                val status = items.inspect(userId, proposal, step)
                account(userId)
                val added = status == PlanItemState.ADDED || (status == PlanItemState.MISSING && items.create(userId, proposal, step, timezone))
                account(userId)
                step = step.copy(added = added, error = if (added) null else if (status == PlanItemState.CONFLICT)
                    "This item changed. Open its feature before retrying." else "The add could not be confirmed. Reload or retry this step.")
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { account(userId); step = step.copy(error = "The add could not be confirmed. Reload or retry this step.") }
            val retainedPreview = checkNotNull(snapshot.preview)
            val updated = snapshot.copy(preview = retainedPreview.copy(steps = retainedPreview.steps.mapIndexed { index, state -> if (index == i) step else state }))
            try { write(userId, updated); snapshot = updated }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                account(userId)
                return@withLock RecoveryApplyResult(updated, "This step was checked, but local progress could not be saved. Reload before continuing.")
            }
        }
        RecoveryApplyResult(snapshot, if (snapshot.preview!!.steps.any { it.selected && !it.added }) "Some steps were not confirmed. Reload or retry only those steps." else "Selected steps added.")
    }
    override suspend fun discard(userId: String) = mutex.withLock { write(userId, read(userId).copy(pending = null, preview = null)) }
    override suspend fun clear(userId: String) = mutex.withLock { cache.clear(userId) }
    private suspend fun read(userId: String): RecoverySnapshot {
        account(userId)
        // An unreadable retry must never be replaced with fresh IDs.
        return cache.read(userId)?.also { account(userId); require(RecoveryRules.validSnapshot(userId, it)) } ?: RecoverySnapshot(userId).also { account(userId) }
    }
    private suspend fun write(userId: String, snapshot: RecoverySnapshot) {
        account(userId); require(RecoveryRules.validSnapshot(userId, snapshot)); cache.write(userId, snapshot); account(userId)
    }
    private fun account(userId: String) = check(sessions.isCurrentUser(userId))
    private fun message(failure: ApiFailure) = when (failure) {
        ApiFailure.AUTHENTICATION -> "Sign in again before building a plan."
        ApiFailure.CONFIGURATION -> "Planning needs a backend update. Your request is retained."
        ApiFailure.CONFLICT -> "The saved conversation changed. Reload Coach before planning from it."
        ApiFailure.IN_PROGRESS -> "This plan is still being generated. Wait a moment and retry the same request."
        ApiFailure.RATE_LIMITED -> "Too many requests. Wait a moment and retry."
        ApiFailure.TIMEOUT, ApiFailure.RESULT_UNCONFIRMED -> "The plan could not be confirmed. Retry the retained request to recover it."
        ApiFailure.INVALID_REQUEST -> "Describe a problem in 3 to 500 characters, or open a saved conversation."
        ApiFailure.INVALID_RESPONSE -> "The backend returned an incompatible plan. Your request is retained; the backend may need an update."
        ApiFailure.NETWORK, ApiFailure.UNAVAILABLE -> "The coach is unavailable. Check your connection and retry the retained request."
    }
}
