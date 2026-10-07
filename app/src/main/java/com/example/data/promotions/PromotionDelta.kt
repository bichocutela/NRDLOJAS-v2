package com.example.data.promotions

import org.json.JSONArray
import org.json.JSONObject

internal data class PromotionDelta(val revision: String, val count: Int, val changed: List<CachedAcpRow>, val removed: List<String>) {
    companion object {
        fun parse(root: JSONObject): PromotionDelta {
            val revision = root.getString("revision")
            require(Regex("[a-f0-9]{64}").matches(revision)) { "Versão de ofertas inválida." }
            val count = root.getInt("count")
            require(count in 0..10_000)
            val changed = root.getJSONArray("items").let { array -> (0 until array.length()).map { index ->
                val row = array.getJSONObject(index)
                val id = row.getString("id"); val hash = row.getString("hash")
                require(id.isNotBlank() && id.length <= 512 && Regex("[a-f0-9]{64}").matches(hash))
                CachedAcpRow(id, hash, row.getJSONObject("item").toString())
            } }
            val removed = root.getJSONArray("removedIds").let { array -> (0 until array.length()).map { array.getString(it) } }
            require(changed.map { it.id }.distinct().size == changed.size && removed.distinct().size == removed.size)
            require(changed.none { it.id in removed })
            return PromotionDelta(revision, count, changed, removed)
        }
    }
    fun applyTo(previous: List<CachedAcpRow>): List<CachedAcpRow> {
        val rows = previous.associateBy { it.id }.toMutableMap()
        removed.forEach(rows::remove)
        changed.forEach { rows[it.id] = it }
        require(rows.size == count) { "Sincronização de ofertas incompleta. Mantendo a lista anterior." }
        return rows.values.toList()
    }
}

internal fun offerIdentity(store: String, code: String): String = "$store|$code"
internal fun addedOfferIds(previous: Set<String>, current: Set<String>, initialized: Boolean): Set<String> =
    if (initialized) current - previous else emptySet()
