package com.example.data.promotions

import android.os.SystemClock
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.Source
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CancellationException

/** Only public promotion configuration, never user grants or sessions. */
internal object PromotionConfigCache {
    private data class Entry(val data: Map<String, Any>, val fetchedAt: Long)
    private val entries = mutableMapOf<String, Entry>()
    private val gate = Mutex()
    private const val TTL_MILLIS = 5 * 60_000L

    fun remember(document: DocumentReference, data: Map<String, Any>) {
        synchronized(entries) { entries[document.path] = Entry(data.toMap(), SystemClock.elapsedRealtime()) }
    }

    suspend fun read(document: DocumentReference, forceRefresh: Boolean = false): Map<String, Any> = gate.withLock {
        require(document.path in setOf("config/promotion_stores", "config/acpOfferValidity"))
        val cached = synchronized(entries) { entries[document.path] }
        if (!forceRefresh && cached != null && SystemClock.elapsedRealtime() - cached.fetchedAt < TTL_MILLIS) {
            return@withLock cached.data
        }
        // A failed read does not extend the TTL or replace a valid configuration with defaults.
        val data = try {
            document.get(Source.SERVER).await().data.orEmpty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Public display configuration can be reused; access/session checks stay on the server.
            cached?.let { return@withLock it.data }
            val local = try { document.get(Source.CACHE).await() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            return@withLock local?.data ?: throw failure
        }
        remember(document, data)
        data
    }
}
