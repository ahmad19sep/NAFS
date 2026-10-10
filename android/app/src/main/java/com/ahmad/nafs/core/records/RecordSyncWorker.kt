package com.ahmad.nafs.core.records

import android.content.Context
import androidx.work.*
import com.ahmad.nafs.core.network.SupabaseApiSessionTokens
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RecordSyncDependencies {
    fun repository(): RecordRepository
    fun store(): PrivateRecordStore
    fun sessions(): SupabaseApiSessionTokens
}

@Singleton
class RecordSyncScheduler @Inject constructor(@ApplicationContext context: Context) {
    private val work = WorkManager.getInstance(context)
    fun enqueue(userId: String, feature: RecordFeature) {
        val request = OneTimeWorkRequestBuilder<RecordSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf("user" to userId, "feature" to feature.name))
            .addTag("records-$userId").build()
        work.enqueueUniqueWork("records-$userId-${feature.name}", ExistingWorkPolicy.KEEP, request)
    }
    fun reconcile(userId: String) { RecordFeature.entries.forEach { enqueue(userId, it) } }
    suspend fun cancel(userId: String) = withContext(Dispatchers.IO) { work.cancelAllWorkByTag("records-$userId").result.get(); Unit }
}

/** WorkManager contains identifiers only; the frozen payload remains encrypted in Room. */
class RecordSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val user = inputData.getString("user") ?: return Result.failure()
        val feature = inputData.getString("feature")?.let { runCatching { RecordFeature.valueOf(it) }.getOrNull() } ?: return Result.failure()
        val dependencies = EntryPointAccessors.fromApplication(applicationContext, RecordSyncDependencies::class.java)
        if (!dependencies.sessions().isCurrentUser(user)) return Result.success()
        val store = dependencies.store()
        val json = Json { ignoreUnknownKeys = true }
        try {
            val snapshot = store.read(user, "records/${feature.name}")?.let { json.decodeFromJsonElement<RecordSnapshot>(it) } ?: return Result.success()
            val intent = snapshot.pending ?: return Result.success()
            require(intent.feature == feature.name && RecordRules.validId(intent.id))
            val encoded = json.encodeToJsonElement(intent)
            val receiptKey = "receipt/${feature.name}/${intent.id}"
            if (store.read(user, receiptKey)?.get("intent") == encoded) return Result.success()
            val saved = dependencies.repository().save(user, intent)
            if (!dependencies.sessions().isCurrentUser(user)) return Result.success()
            // A receipt never overwrites an editor or its newer draft. The UI reconciles it.
            store.write(user, receiptKey, buildJsonObject { put("intent", encoded); put("saved", saved) })
            return Result.success()
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { return if (runAttemptCount < 6) Result.retry() else Result.failure() }
    }
}
