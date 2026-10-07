package com.example.data.promotions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Coalesce repeated requests, preserving one manual refresh after an active silent check. */
internal class PromotionSyncRequests(
    private val scope: CoroutineScope,
    private val sync: suspend (Boolean) -> Unit
) {
    private var requestedJob: Job? = null
    private var requestedInteractive = false

    @Synchronized
    fun request(interactive: Boolean = false): Job {
        val existing = requestedJob?.takeIf { it.isActive }
        if (existing != null && (!interactive || requestedInteractive)) return existing
        requestedInteractive = interactive
        return scope.launch {
            existing?.join()
            sync(interactive)
        }.also { requestedJob = it }
    }
}
