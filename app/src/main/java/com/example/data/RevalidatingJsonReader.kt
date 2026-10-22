package com.example.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request

/** One small shared snapshot per resource; validators avoid downloading unchanged JSON. */
internal class RevalidatingJsonReader<T>(
    private val client: OkHttpClient,
    private val parse: (String) -> T?,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    private val lock = Mutex()
    private var url: String? = null
    private var value: T? = null
    private var etag: String? = null
    private var checkedAt = 0L

    suspend fun read(request: Request, maxAgeMillis: Long): T? = lock.withLock {
        if (url != request.url.toString()) {
            url = request.url.toString(); value = null; etag = null; checkedAt = 0L
        }
        if (value != null && now() - checkedAt in 0 until maxAgeMillis) return@withLock value
        val conditional = request.newBuilder().apply {
            etag?.let { header("If-None-Match", it) }
        }.build()
        client.newCall(conditional).execute().use { response ->
            if (response.code == 304 && value != null) {
                checkedAt = now()
                return@withLock value
            }
            if (!response.isSuccessful) return@withLock null // Preserve previous value/validator for the next attempt.
            val parsed = parse(response.body?.string().orEmpty()) ?: return@withLock null
            value = parsed
            etag = response.header("ETag")
            checkedAt = now()
            parsed
        }
    }
}
