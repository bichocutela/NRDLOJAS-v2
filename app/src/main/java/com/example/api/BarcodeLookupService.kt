package com.example.api

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

/** Resultado de uma consulta externa, mantido somente em memória durante a sessão. */
data class ExternalProductInfo(
    val barcode: String,
    val name: String?,
    val brand: String?,
    val category: String?,
    val quantity: String?,
    val description: String?,
    val imageUrl: String?,
    val source: String
)

sealed interface BarcodeLookupResult {
    data class Found(val product: ExternalProductInfo) : BarcodeLookupResult
    data class NotFound(val barcode: String) : BarcodeLookupResult
    data class Failure(val message: String) : BarcodeLookupResult
}

/**
 * Consulta dados públicos de produto por código de barras.
 *
 * Este serviço usa somente GET e não grava no Room, DataStore, Firebase ou Supabase.
 * O cache e os contadores existem apenas enquanto o processo do app permanece aberto.
 */
object BarcodeLookupService {
    private const val OPEN_PRODUCTS_FACTS_URL =
        "https://world.openproductsfacts.org/api/v2/product/"
    private const val OPEN_FOOD_FACTS_URL =
        "https://world.openfoodfacts.org/api/v3/product/"
    private const val UPCITEMDB_URL =
        "https://api.upcitemdb.com/prod/trial/lookup?upc="

    private const val MIN_INTERVAL_BETWEEN_REQUESTS_MS = 1_200L
    private const val OPEN_FOOD_FACTS_MAX_REQUESTS_PER_MINUTE = 10
    private const val UPCITEMDB_MAX_REQUESTS_PER_SESSION = 20

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
    private val lookupMutex = Mutex()
    private val cache = mutableMapOf<String, BarcodeLookupResult>()
    private val rateLock = Any()
    private val openFoodFactsRequests = ArrayDeque<Long>()
    private var lastRequestAt = 0L
    private var upcitemdbRequestsThisSession = 0

    private enum class Source(val label: String) {
        OPEN_PRODUCTS_FACTS("Open Products Facts"),
        OPEN_FOOD_FACTS("Open Food Facts"),
        UPCITEMDB("UPCitemdb")
    }

    private sealed interface HttpResult {
        data class Body(val value: String) : HttpResult
        data object NotFound : HttpResult
        data object RateLimited : HttpResult
        data class Failed(val message: String) : HttpResult
    }

    suspend fun lookup(rawBarcode: String): BarcodeLookupResult = lookupMutex.withLock {
        val barcode = normalizeBarcode(rawBarcode)
            ?: return@withLock BarcodeLookupResult.Failure(
                "O código detectado não é um EAN/UPC válido."
            )

        cache[barcode]?.let { return@withLock it }

        var lastFailure: String? = null
        for (source in Source.entries) {
            try {
                val httpResult = when (source) {
                    Source.OPEN_PRODUCTS_FACTS -> {
                        getJson(source, "$OPEN_PRODUCTS_FACTS_URL$barcode.json")
                    }

                    Source.OPEN_FOOD_FACTS -> {
                        getJson(source, "$OPEN_FOOD_FACTS_URL$barcode.json")
                    }

                    Source.UPCITEMDB -> {
                        getJson(source, "$UPCITEMDB_URL$barcode")
                    }
                }

                when (httpResult) {
                    is HttpResult.Body -> {
                        val product = when (source) {
                            Source.OPEN_PRODUCTS_FACTS,
                            Source.OPEN_FOOD_FACTS -> parseOpenFacts(
                                body = httpResult.value,
                                barcode = barcode,
                                source = source
                            )

                            Source.UPCITEMDB -> parseUpcitemdb(
                                body = httpResult.value,
                                barcode = barcode,
                                source = source
                            )
                        }

                        if (product != null) {
                            return@withLock BarcodeLookupResult.Found(product).also {
                                cache[barcode] = it
                            }
                        }
                    }

                    HttpResult.NotFound -> Unit
                    HttpResult.RateLimited -> {
                        lastFailure = "${source.label} atingiu o limite gratuito temporariamente."
                    }

                    is HttpResult.Failed -> {
                        lastFailure = "${source.label} indisponível no momento."
                    }
                }
            } catch (limit: SessionLimitReached) {
                lastFailure = limit.message
            } catch (_: Exception) {
                lastFailure = "Não foi possível consultar ${source.label}."
            }
        }

        val result = if (lastFailure != null) {
            BarcodeLookupResult.Failure(
                "$lastFailure Tente novamente mais tarde ou consulte o catálogo interno."
            )
        } else {
            BarcodeLookupResult.NotFound(barcode)
        }
        if (result is BarcodeLookupResult.NotFound) {
            cache[barcode] = result
        }
        result
    }

    private suspend fun getJson(source: Source, url: String): HttpResult {
        waitForRequestSlot(source)
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .header(
                "User-Agent",
                "NRDLOJAS/${BuildConfig.VERSION_NAME} (barcode-lookup)"
            )
            .build()

        return withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    when {
                        response.code == 404 -> HttpResult.NotFound
                        response.code == 429 -> HttpResult.RateLimited
                        !response.isSuccessful -> HttpResult.Failed("HTTP ${response.code}")
                        body.isBlank() -> HttpResult.Failed("resposta vazia")
                        else -> HttpResult.Body(body)
                    }
                }
            } catch (exception: Exception) {
                HttpResult.Failed(exception.message ?: "erro de rede")
            }
        }
    }

    private suspend fun waitForRequestSlot(source: Source) {
        while (true) {
            val waitMs: Long
            synchronized(rateLock) {
                val now = System.currentTimeMillis()
                while (openFoodFactsRequests.isNotEmpty() &&
                    now - openFoodFactsRequests.first() >= 60_000L
                ) {
                    openFoodFactsRequests.removeFirst()
                }

                if (source == Source.UPCITEMDB &&
                    upcitemdbRequestsThisSession >= UPCITEMDB_MAX_REQUESTS_PER_SESSION
                ) {
                    throw SessionLimitReached(
                        "O limite preventivo da consulta UPCitemdb nesta sessão foi atingido."
                    )
                }

                val globalWait = (lastRequestAt + MIN_INTERVAL_BETWEEN_REQUESTS_MS - now)
                    .coerceAtLeast(0L)
                val openFoodFactsWait = if (
                    source == Source.OPEN_FOOD_FACTS &&
                    openFoodFactsRequests.size >= OPEN_FOOD_FACTS_MAX_REQUESTS_PER_MINUTE
                ) {
                    (openFoodFactsRequests.first() + 60_000L - now).coerceAtLeast(0L)
                } else {
                    0L
                }
                waitMs = maxOf(globalWait, openFoodFactsWait)

                if (waitMs == 0L) {
                    lastRequestAt = now
                    if (source == Source.OPEN_FOOD_FACTS) {
                        openFoodFactsRequests.addLast(now)
                    }
                    if (source == Source.UPCITEMDB) {
                        upcitemdbRequestsThisSession += 1
                    }
                }
            }

            if (waitMs == 0L) return
            delay(waitMs)
        }
    }

    private fun parseOpenFacts(
        body: String,
        barcode: String,
        source: Source
    ): ExternalProductInfo? {
        val root = json.parseToJsonElement(body).jsonObject
        if (root["status"]?.jsonPrimitive?.intOrNull != 1) return null
        val product = root["product"]?.jsonObject ?: return null

        val result = ExternalProductInfo(
            barcode = barcode,
            name = product.stringValue(
                "product_name",
                "product_name_pt",
                "product_name_en"
            ),
            brand = product.stringValue("brands", "brands_tags"),
            category = product.stringValue(
                "categories",
                "categories_tags",
                "categories_old"
            ),
            quantity = product.stringValue("quantity", "product_quantity"),
            description = product.stringValue(
                "generic_name",
                "generic_name_pt",
                "ingredients_text",
                "ingredients_text_pt"
            ),
            imageUrl = product.stringValue(
                "image_front_url",
                "image_front_small_url",
                "image_url"
            ).onlyHttps(),
            source = source.label
        )

        return result.takeIf {
            !it.name.isNullOrBlank() ||
                !it.brand.isNullOrBlank() ||
                !it.category.isNullOrBlank() ||
                !it.description.isNullOrBlank() ||
                !it.imageUrl.isNullOrBlank()
        }
    }

    private fun parseUpcitemdb(
        body: String,
        barcode: String,
        source: Source
    ): ExternalProductInfo? {
        val root = json.parseToJsonElement(body).jsonObject
        val item = root["items"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null

        val imageUrl = item["images"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.firstOrNull()
            .onlyHttps()
        val result = ExternalProductInfo(
            barcode = barcode,
            name = item.stringValue("title", "description"),
            brand = item.stringValue("brand"),
            category = item.stringValue("category"),
            quantity = item.stringValue("weight", "dimension"),
            description = item.stringValue("model", "asin"),
            imageUrl = imageUrl,
            source = source.label
        )

        return result.takeIf {
            !it.name.isNullOrBlank() ||
                !it.brand.isNullOrBlank() ||
                !it.category.isNullOrBlank() ||
                !it.imageUrl.isNullOrBlank()
        }
    }

    private fun JsonObject.stringValue(vararg keys: String): String? = keys
        .asSequence()
        .mapNotNull { key ->
            when (val value = this[key]) {
                is JsonPrimitive -> value.contentOrNull
                is JsonArray -> value
                    .mapNotNull { item -> item.jsonPrimitive.contentOrNull }
                    .joinToString(", ")
                    .takeIf { it.isNotBlank() }
                else -> null
            }
        }
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }

    private fun String?.onlyHttps(): String? = this
        ?.trim()
        ?.takeIf { it.startsWith("https://", ignoreCase = true) }

    private fun normalizeBarcode(value: String): String? {
        val digits = value.filter(Char::isDigit)
        return digits.takeIf { it.length in 8..14 }
    }

    private class SessionLimitReached(message: String) : Exception(message)
}
