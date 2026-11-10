package com.example.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.net.URI
import java.util.Locale

/**
 * Optional Firebase metadata source. Not connected to the live screen until
 * a trusted publisher and per-installation routing are ready.
 * PDF and cover bytes remain hosted on the existing provider CDN.
 */
internal object FirebaseEncartesSource {
    suspend fun load(): List<StoreEncarte> {
        val document = FirebaseFirestore.getInstance().collection("config")
            .document("encartes_public").get().await()
        require(document.exists()) { "Encartes ainda não publicados no Firebase" }
        val raw = document.getString("payload") ?: error("Encartes indisponíveis")
        require(raw.length <= 250_000) { "Metadados de encartes excedem o limite" }
        val array = JSONObject(raw).getJSONArray("tabloids")
        require(array.length() <= 200) { "Quantidade de encartes inválida" }
        return (0 until array.length()).mapNotNull { index ->
            val item = array.getJSONObject(index)
            val leaflet = StoreEncarte(
                item.getLong("id"), item.getString("name"),
                item.getString("startDate"), item.getString("endDate"),
                item.getString("uri"), item.getString("image")
            )
            leaflet.takeIf {
                it.id > 0 && it.isActive() &&
                    isTrustedAsset(it.pdf, ".pdf") && isTrustedAsset(it.image, ".png")
            }
        }.distinctBy { it.id }
            .sortedWith(compareByDescending<StoreEncarte> { it.startDate }.thenBy { it.id })
    }

    private fun isTrustedAsset(raw: String, suffix: String): Boolean = runCatching {
        val uri = URI(raw)
        uri.scheme == "https" &&
            uri.host == "datalakeidever.blob.core.windows.net" &&
            uri.userInfo == null && uri.port == -1 &&
            uri.path.lowercase(Locale.ROOT).endsWith(suffix)
    }.getOrDefault(false)
}
