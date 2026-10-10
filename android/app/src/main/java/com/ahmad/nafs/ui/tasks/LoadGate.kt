package com.ahmad.nafs.ui.tasks

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Remembers the load in flight and what it is for, so a second request for the same key (opening a screen
 * asks once from `initialize` and again from the resume effect) is ignored instead of starting a duplicate.
 * A request for a different key (another user, timezone or day) cancels the older load and starts afresh.
 */
internal class LoadGate<K : Any> {
    private var job: Job? = null
    private var key: K? = null

    fun isRunning(key: K): Boolean = job?.isActive == true && this.key == key

    fun launch(scope: CoroutineScope, key: K, block: suspend CoroutineScope.() -> Unit): Job {
        job?.cancel()
        this.key = key
        return scope.launch(block = block).also { job = it }
    }
}

/**
 * Applies a fresh load without undoing local changes made while it was in flight: for each id in [keepLocal],
 * the local copy wins, and an item removed here stays removed even if the older response still has it.
 */
internal fun <T> mergeLoaded(
    loaded: List<T>,
    local: List<T>,
    keepLocal: Set<String>,
    id: (T) -> String,
): List<T> {
    if (keepLocal.isEmpty()) return loaded
    val localById = local.associateBy(id)
    return loaded.filterNot { id(it) in keepLocal } + keepLocal.mapNotNull { localById[it] }
}
