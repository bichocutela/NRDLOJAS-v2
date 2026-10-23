package com.example.data

import com.example.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class NordestaoStore(val id: Long, val name: String, val identifier: String, val businessHours: String,
    val isOpen: Boolean?, val capacity: Int?, val street: String, val number: String, val complement: String,
    val neighborhood: String, val city: String, val state: String, val zipCode: String,
    val latitude: Double?, val longitude: Double?, val phones: List<String>) {
    val address: String get() = listOf(listOf(street, number).filter { it.isNotBlank() }.joinToString(", "),
        complement, neighborhood, listOf(city, state).filter { it.isNotBlank() }.joinToString(" / "),
        zipCode.takeIf { it.isNotBlank() }?.let { "CEP $it" }.orEmpty()).filter { it.isNotBlank() }.joinToString("\n")
    val mapUrl: String? get() {
        val query = if (latitude != null && longitude != null) "$latitude,$longitude" else {
            if (street.isBlank() && city.isBlank()) return null
            "$name, $address"
        }
        return "https://www.google.com/maps/search/?api=1&query=" + URLEncoder.encode(query, "UTF-8")
    }
    fun matches(query: String): Boolean {
        fun normalize(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        return normalize("$name $city $neighborhood $street").contains(normalize(query.trim()))
    }
}
data class NordestaoStoresSnapshot(val stores: List<NordestaoStore>, val fetchedAt: String)
object NordestaoStoresCodec {
    fun decode(raw: String): NordestaoStoresSnapshot {
        val root = JSONObject(raw)
        val array = root.getJSONArray("stores")
        val seen = mutableSetOf<Long>()
        val stores = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val id = item.getLong("id"); val name = item.getString("name").trim()
            require(id > 0 && name.isNotBlank() && seen.add(id)) { "Unidade inválida" }
            val a = item.getJSONObject("address")
            fun coordinate(key: String, max: Double) = if(a.isNull(key)) null else a.optDouble(key).takeIf { it.isFinite() && kotlin.math.abs(it) <= max }
            val lat = coordinate("latitude", 90.0); val lon = coordinate("longitude", 180.0)
            val validLocation = lat != null && lon != null && (lat != 0.0 || lon != 0.0)
            val phones = item.getJSONArray("phones").let { p -> (0 until p.length()).map { p.getString(it) }.filter { it.matches(Regex("[0-9]{10,13}")) }.distinct() }
            NordestaoStore(id, name, item.optString("identifier"), item.optString("businessHours"),
                (item.opt("isOpen") as? Boolean), if (item.isNull("capacity")) null else item.optInt("capacity").takeIf { it >= 0 },
                a.optString("street"), a.optString("number"), a.optString("complement"), a.optString("neighborhood"),
                a.optString("city"), a.optString("state"), a.optString("zipCode"), if(validLocation) lat else null, if(validLocation) lon else null, phones)
        }.sortedWith(compareBy<NordestaoStore> { it.name.lowercase(Locale.ROOT) }.thenBy { it.id })
        return NordestaoStoresSnapshot(stores, root.getString("fetchedAt"))
    }
}
object NordestaoStoresRepository {
    private val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS).followRedirects(false).build()
    suspend fun load(): NordestaoStoresSnapshot = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/nrd-stores")
            .header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY).header("Accept", "application/json").build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if(continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching { response.use {
                    if (!it.isSuccessful) throw IOException("Não foi possível atualizar as lojas")
                    NordestaoStoresCodec.decode(it.body?.string().orEmpty())
                } }
                if(continuation.isActive) result.fold({ continuation.resume(it) }, { continuation.resumeWithException(it) })
            }
        })
    }
}
