package com.ahmad.nafs.ui.records

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.network.NafsApiClient
import com.ahmad.nafs.core.records.*
import com.ahmad.nafs.core.time.AccountDate
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

data class RecordUiState(val feature: RecordFeature = RecordFeature.LOG, val rows: List<JsonObject> = emptyList(),
    val values: Map<String, String> = emptyMap(), val date: String = "", val editing: Boolean = false,
    val hasMore: Boolean = false, val loading: Boolean = true, val busy: Boolean = false, val offline: Boolean = false, val pending: Boolean = false,
    val error: String? = null, val message: String? = null)

@HiltViewModel
class RecordViewModel @Inject constructor(private val repository: RecordRepository, private val store: PrivateRecordStore,
    private val api: NafsApiClient, private val uploads: RecordMediaUploader, private val sync: RecordSyncScheduler) : ViewModel() {
    private val _state = MutableStateFlow(RecordUiState()); val state = _state.asStateFlow()
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var account = ""; private var zone = "UTC"; private var snapshot = RecordSnapshot()
    private var draftJob: Job? = null
    private var storageReadable = true
    private var refreshed = false
    private fun cacheKey() = "records/${_state.value.feature.name}"
    fun initialize(userId: String, timezone: String, feature: RecordFeature) {
        if (account == userId && zone == timezone && _state.value.feature == feature) return
        account = userId; zone = timezone; _state.value = RecordUiState(feature = feature)
        viewModelScope.launch {
            try {
                snapshot = store.read(userId, cacheKey())?.let { json.decodeFromJsonElement<RecordSnapshot>(it) } ?: RecordSnapshot()
                require(snapshot.rows.all { RecordRules.text(it, "user_id") == userId })
                snapshot.pending?.let { require(it.feature == feature.name && RecordRules.validId(it.id)) }
                publish()
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { storageReadable = false; _state.update { it.copy(error = "Retry storage could not be read. Writes are blocked to protect retained changes.") } }
            reload()
        }
    }
    private suspend fun persist() { check(storageReadable); store.write(account, cacheKey(), json.encodeToJsonElement(snapshot) as JsonObject) }
    private fun publish() {
        _state.update { it.copy(rows = snapshot.rows, values = snapshot.draft.orEmpty(), date = snapshot.date.orEmpty(),
            editing = snapshot.draft != null, pending = snapshot.pending != null) }
    }
    fun reload() {
        if (_state.value.busy) return
        val userId = account; val feature = _state.value.feature
        _state.update { it.copy(loading = true) }
        viewModelScope.launch { mutex.withLock {
            try {
                val rows = repository.load(userId, feature)
                if (account != userId) return@withLock
                snapshot.pending?.let { intent ->
                    val receipt = store.read(userId, "receipt/${feature.name}/${intent.id}")
                    if (receipt?.get("intent") == json.encodeToJsonElement(intent) && receipt["saved"] is JsonObject) {
                        snapshot = snapshot.copy(pending = null, draft = null, editingId = null, date = null, expected = null)
                        _state.update { it.copy(message = "Queued entry synced to your account.") }
                    }
                }
                snapshot = snapshot.copy(rows = rows)
                _state.update { it.copy(hasMore = feature.daily && rows.size == 90) }
                // Exact replay is available even when a reload cannot prove the saved result.
                if (storageReadable) persist()
                refreshed = true
                publish(); _state.update { it.copy(loading = false, offline = false, error = if (storageReadable) null else it.error) }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { _state.update { it.copy(loading = false, offline = true, error = "Unable to refresh. Saved offline records and drafts are retained.") } }
        } }
    }
    fun loadMore() {
        if (_state.value.busy || _state.value.loading || !_state.value.hasMore) return
        val before = snapshot.rows.minOfOrNull { RecordRules.text(it, "date") } ?: return
        val userId = account; val feature = _state.value.feature
        _state.update { it.copy(busy = true) }
        viewModelScope.launch { mutex.withLock {
            try {
                val older = repository.load(userId, feature, before)
                snapshot = snapshot.copy(rows = (snapshot.rows + older).distinctBy { RecordRules.text(it, "id") })
                publish(); _state.update { it.copy(hasMore = older.size == 90, offline = false) }; persist()
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { _state.update { it.copy(error = "Earlier records could not be loaded. Retry when connected.") } }
            finally { _state.update { it.copy(busy = false) } }
        } }
    }
    fun edit(row: JsonObject? = null) {
        if (_state.value.busy || snapshot.pending != null || !storageReadable) return
        val feature = _state.value.feature
        val day = AccountDate.today(zone).toString()
        val actual = row ?: if (feature == RecordFeature.DREAM) snapshot.rows.firstOrNull() else if (feature.daily) snapshot.rows.find { RecordRules.text(it, "date") == day } else null
        val values = feature.fields.associate { field ->
            val value = if (field.kind == FieldKind.APPS) (actual?.get(field.key) as? JsonArray)?.joinToString("\n") {
                val item = it as JsonObject; "${RecordRules.text(item, "app")}: ${RecordRules.text(item, "minutes")}"
            }.orEmpty() else RecordRules.text(actual, field.key)
            field.key to (if (actual == null) field.initial else value)
        } + listOf("screenshot_url", "photo_urls").associateWith { key -> actual?.get(key)?.toString().orEmpty() }
        snapshot = snapshot.copy(draft = values, editingId = RecordRules.text(actual, "id").ifBlank { UUID.randomUUID().toString() },
            date = if (feature.daily) RecordRules.text(actual, "date").ifBlank { day } else null, expected = actual)
        publish(); flushDraft()
    }
    fun change(key: String, value: String) {
        if (_state.value.busy || snapshot.pending != null) return
        val field = _state.value.feature.fields.find { it.key == key } ?: return
        if (value.length > if (field.kind == FieldKind.APPS) 5000 else maxOf(field.max.toInt(), 80)) return
        snapshot = snapshot.copy(draft = snapshot.draft.orEmpty() + (key to value)); publish()
        draftJob?.cancel(); draftJob = viewModelScope.launch { delay(300); persistSafely() }
    }
    fun flushDraft() { draftJob?.cancel(); viewModelScope.launch { persistSafely() } }
    private suspend fun persistSafely() = mutex.withLock {
        try { persist() } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { _state.update { it.copy(error = "Draft could not be saved on this device. Keep this screen open and retry.") } }
    }
    fun closeEditor() {
        if (_state.value.busy || snapshot.pending != null) return
        snapshot = snapshot.copy(draft = null, editingId = null, date = null, expected = null)
        publish(); flushDraft()
    }
    fun save() {
        if (_state.value.busy || !storageReadable || snapshot.draft == null) return
        draftJob?.cancel()
        val userId = account; val feature = _state.value.feature
        _state.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch { mutex.withLock {
            var persisted = snapshot.pending != null
            try {
                var intent = snapshot.pending
                if (intent == null) {
                    val day = snapshot.date?.let(LocalDate::parse) ?: AccountDate.today(zone)
                    var patch = RecordRules.patch(feature, snapshot.draft.orEmpty(), AccountDate.today(zone))
                    if (feature == RecordFeature.LOG) {
                        patch = repository.enrichLog(userId, day, patch)
                        snapshot.draft?.get("photo_urls")?.takeIf { it.isNotBlank() }?.let { patch = JsonObject(patch + ("photo_urls" to json.parseToJsonElement(it))) }
                    }
                    if (feature == RecordFeature.SCREEN) snapshot.draft?.get("screenshot_url")?.takeIf { it.isNotBlank() }?.let { patch = JsonObject(patch + ("screenshot_url" to json.parseToJsonElement(it))) }
                    intent = RecordIntent(feature.name, checkNotNull(snapshot.editingId), snapshot.date, patch, snapshot.expected)
                    snapshot = snapshot.copy(pending = intent)
                }
                persist(); persisted = true; publish()
                val saved = repository.save(userId, intent)
                if (account != userId) return@withLock
                snapshot = snapshot.copy(rows = (listOf(saved) + snapshot.rows.filterNot { RecordRules.text(it, "id") == RecordRules.text(saved, "id") }).take(90),
                    pending = null, draft = null, editingId = null, date = null, expected = null)
                publish()
                val cached = runCatching { persist() }.isSuccess
                _state.update { it.copy(message = if (cached) "Saved to your account." else "Saved to your account. The offline copy could not be updated; reload before leaving.", offline = false) }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: IllegalArgumentException) { _state.update { it.copy(error = error.message ?: "Check the form.") } }
            catch (_: Exception) { _state.update { it.copy(error = if (persisted) "Save is awaiting confirmation. It will retry when connected while this account is signed in. Reload to inspect the result, or retry the same request." else "Unable to prepare this save. Check your connection and device storage, then retry.") } }
            finally {
                if (account == userId) {
                    if (persisted && snapshot.pending != null) sync.enqueue(userId, feature)
                    _state.update { it.copy(busy = false) }
                }
            }
        } }
    }
    fun discardPending() {
        if (_state.value.busy || _state.value.offline || !refreshed || snapshot.pending == null) return
        // Keep the original UUID. A later save still recovers any late committed insert safely.
        snapshot = snapshot.copy(pending = null, expected = snapshot.rows.find { RecordRules.text(it, "id") == snapshot.editingId })
        publish(); flushDraft()
    }
    fun addPhoto(uri: Uri) {
        if (_state.value.busy || snapshot.pending != null || snapshot.draft == null) return
        val feature = _state.value.feature
        if (feature !in setOf(RecordFeature.LOG, RecordFeature.SCREEN)) return
        if (feature == RecordFeature.LOG) {
            val photos = snapshot.draft?.get("photo_urls")?.takeIf { it.isNotBlank() && it != "null" }?.let { json.parseToJsonElement(it) as? JsonArray }
            if ((photos?.size ?: 0) >= 6) { _state.update { it.copy(error = "Use at most six photos.") }; return }
        }
        _state.update { it.copy(busy = true) }
        viewModelScope.launch { mutex.withLock {
            try {
                val url = uploads.upload(account, if (feature == RecordFeature.LOG) "log-photos" else "screentime-shots", uri)
                val key = if (feature == RecordFeature.LOG) "photo_urls" else "screenshot_url"
                val value = if (feature == RecordFeature.LOG) {
                    val current = snapshot.draft?.get(key)?.takeIf { it.isNotBlank() && it != "null" }?.let { json.parseToJsonElement(it) as JsonArray } ?: JsonArray(emptyList())
                    require(current.size < 6) { "Use at most six photos." }; JsonArray(current + JsonPrimitive(url)).toString()
                } else JsonPrimitive(url).toString()
                snapshot = snapshot.copy(draft = snapshot.draft.orEmpty() + (key to value)); persist(); publish()
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { _state.update { it.copy(error = "Photo was not attached. Check your connection, storage setup, and photo access.") } }
            finally { _state.update { it.copy(busy = false) } }
        } }
    }
    fun analyzeScreen() {
        if (_state.value.busy || _state.value.feature != RecordFeature.SCREEN || snapshot.draft != null) return
        val row = snapshot.rows.find { RecordRules.text(it, "date") == AccountDate.today(zone).toString() } ?: return
        val userId = account
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch { mutex.withLock {
            try {
                val payload = buildJsonObject { put("manualData", buildJsonObject {
                    put("totalMins", row.getValue("total_mins")); put("apps", row["apps"] ?: JsonArray(emptyList()))
                }); put("date", RecordRules.text(row, "date")) }
                val digest = java.security.MessageDigest.getInstance("SHA-256").digest(payload.toString().toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
                val key = "screen-analysis/$digest"
                val cached = store.read(userId, key)
                val request = cached?.get("request") as? JsonObject ?: buildJsonObject { put("requestId", UUID.randomUUID().toString()); put("action", "screen-analysis"); put("payload", payload) }
                val expected = cached?.get("expected") as? JsonObject ?: row
                var result = cached?.get("result") as? JsonObject
                if (result == null) {
                    store.write(userId, key, buildJsonObject { put("request", request); put("expected", expected) })
                    result = api.post(userId, "/api/ai/native", request)["result"] as? JsonObject ?: error("Invalid response")
                    store.write(userId, key, buildJsonObject { put("request", request); put("expected", expected); put("result", result) })
                }
                val summary = RecordRules.text(result, "summary"); require(summary.isNotBlank() && summary.length <= 10000)
                _state.update { it.copy(message = summary) }
                val intent = RecordIntent(RecordFeature.SCREEN.name, RecordRules.text(row, "id"), RecordRules.text(row, "date"), buildJsonObject { put("ai_summary", summary) }, expected)
                val saved = repository.save(userId, intent)
                snapshot = snapshot.copy(rows = snapshot.rows.map { if (RecordRules.text(it, "id") == intent.id) saved else it })
                publish(); persist()
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { _state.update { it.copy(error = "Analysis or its save could not be confirmed. The exact request and any generated result are retained. Reload and retry; changed records are protected.") } }
            finally { _state.update { it.copy(busy = false) } }
        } }
    }
}
