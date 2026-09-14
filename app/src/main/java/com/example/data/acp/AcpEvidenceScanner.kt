package com.example.data.acp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class AcpEvidenceProgress(
    val phase: String, val pages: Int, val records: Int, val matches: Int,
    val unparsed: Int, val groupsAvailable: Boolean, val historyChanged: Boolean
) {
    val finished: Boolean get() = phase == "done"
}

/** A diagnostic, never a pricing source. Only exact code/EAN matches are retained. */
internal class AcpEvidenceScanner(
    private val read: suspend (String, List<Pair<String, String>>) -> JSONObject,
    private val store: AcpStorage,
    private val pause: suspend () -> Unit = { delay(300) }
) {
    private fun key(target: String): String {
        require(target.matches(Regex("[0-9]{1,20}"))) { "Informe um código ou EAN válido." }
        return "evidence-scan-v1-$target"
    }

    private fun load(target: String): JSONObject? = store.read(key(target))?.let { JSONObject(it) }

    private fun newState(target: String) = JSONObject()
        .put("target", target).put("phase", "groups").put("groupPage", 0)
        .put("pages", 0).put("records", 0).put("unparsed", 0)
        .put("groupsAvailable", true).put("historyChanged", false)
        .put("matches", JSONArray()).put("groups", JSONArray()).put("startedAtMillis", System.currentTimeMillis())

    private fun progress(state: JSONObject) = AcpEvidenceProgress(
        state.getString("phase"), state.getInt("pages"), state.getInt("records"),
        state.getJSONArray("matches").length(), state.getInt("unparsed"),
        state.getBoolean("groupsAvailable"), state.getBoolean("historyChanged")
    )

    suspend fun progress(target: String): AcpEvidenceProgress? = withContext(Dispatchers.IO) {
        load(target)?.let(::progress)
    }

    suspend fun report(target: String): String? = withContext(Dispatchers.IO) {
        load(target)?.let { state ->
            JSONObject().put("diagnostic", "NRD ACP read-only targeted evidence")
                .put("endpoint", "TemplatePrintLog/all")
                .put("additionalEndpoint", "ProductGroup/all")
                .put("format", "Matched product excerpts; not complete HTTP responses or current offers")
                .put("response", state).toString(2)
        }
    }

    /** Resumes after the last fully saved page. No catalog cache reads or product writes. */
    suspend fun scan(target: String, onProgress: (AcpEvidenceProgress) -> Unit) = withContext(Dispatchers.IO) {
        var state = load(target) ?: newState(target)
        onProgress(progress(state))
        while (state.getString("phase") != "done") {
            currentCoroutineContext().ensureActive()
            if (state.getInt("pages") >= 3000) throw AcpFailure("Limite da investigação atingido. Salve os resultados parciais.")
            val next = JSONObject(state.toString())
            if (next.getString("phase") == "groups") {
                val index = next.getInt("groupPage")
                val parameters = listOf("pageSize" to "10", "pageIndex" to index.toString())
                val root = try { read("ProductGroup/all", parameters) }
                catch (failure: AcpFailure) {
                    // Permission/server failure in groups must not hide the independent history.
                    next.put("groupsAvailable", false).put("groupError", failure.message)
                        .put("phase", "history")
                    null
                }
                if (root != null) {
                    validatePage(root, index, next, "groupSignature")
                    val rows = root.getJSONArray("items")
                    for (i in 0 until rows.length()) {
                        val group = rows.optJSONObject(i)
                        val products = group?.optJSONArray("products")
                        if (group == null || products == null) { unparsed(next); continue }
                        next.getJSONArray("groups").put(JSONObject()
                            .put("pageIndex", index).put("metadata", without(group, "products"))
                            .put("productCount", products.length()))
                        for (j in 0 until products.length()) {
                            val product = products.optJSONObject(j)
                            if (product == null) { unparsed(next); continue }
                            if (matches(product, target)) add(next, JSONObject()
                                .put("endpoint", "ProductGroup/all").put("pageIndex", index)
                                .put("group", without(group, "products"))
                                .put("productPath", "items[$i].products[$j]").put("product", product))
                        }
                    }
                    counted(next, rows.length())
                    next.put("groupPage", index + 1)
                    if (!hasNext(root)) next.put("phase", "history")
                }
            } else {
                // Ascending server order avoids the descending-sort failure seen in ACP.
                // Start at the last page of that order to inspect recent prints first.
                if (!next.has("historyPage")) {
                    val initial = read("TemplatePrintLog/all", historyParameters(0))
                    validatePage(initial, 0, next, "historyProbeSignature")
                    next.put("historyTotalAtStart", initial.getInt("totalCount"))
                        .put("historyPagesAtStart", initial.getInt("totalPages"))
                        .put("historyPage", (initial.getInt("totalPages") - 1).coerceAtLeast(0))
                }
                val index = next.getInt("historyPage")
                val root = read("TemplatePrintLog/all", historyParameters(index))
                validatePage(root, index, next, "historySignature")
                if (root.getInt("totalCount") != next.getInt("historyTotalAtStart")) next.put("historyChanged", true)
                val rows = root.getJSONArray("items")
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i)
                    if (row == null) { unparsed(next); continue }
                    val excerpts = historyMatches(row, target, index)
                    if (excerpts == null) unparsed(next)
                    else excerpts.forEach { add(next, it) }
                }
                counted(next, rows.length())
                next.put("historyPage", index - 1)
                if (index == 0) next.put("phase", "done")
            }
            currentCoroutineContext().ensureActive()
            next.put("savedAtMillis", System.currentTimeMillis())
            val encoded = next.toString()
            if (encoded.toByteArray(Charsets.UTF_8).size > 8_000_000) {
                throw AcpFailure("O resultado atingiu o limite de tamanho. Salve os dados parciais.")
            }
            store.write(key(target), encoded)
            state = next
            onProgress(progress(state))
            if (state.getString("phase") != "done") pause()
        }
    }

    private fun historyParameters(index: Int) = listOf("pageSize" to "25", "pageIndex" to index.toString(),
        "orderByDescending" to "false", "profileIdToBeDesconsidered" to "1")

    private fun validatePage(root: JSONObject, index: Int, state: JSONObject, signatureKey: String) {
        val items = root.optJSONArray("items") ?: throw AcpFailure("Formato de paginação não reconhecido. Coleta parcial preservada.")
        if (root.optInt("pageIndex", -1) != index || root.optInt("totalPages", -1) < 0 ||
            root.optInt("pageSize", 0) <= 0 || root.optInt("totalCount", -1) < 0) {
            throw AcpFailure("A ACP retornou paginação inesperada. Coleta parcial preservada.")
        }
        val signature = (0 until items.length()).map { items.optJSONObject(it)?.opt("id") }.joinToString(",")
        if (items.length() > 0 && state.optString(signatureKey) == signature) {
            throw AcpFailure("A ACP repetiu uma página. Coleta parcial preservada.")
        }
        if (items.length() == 0 && (index < root.getInt("totalPages") - 1 || (index == 0 && root.getInt("totalCount") > 0))) {
            throw AcpFailure("A ACP retornou uma página vazia antes do fim. Coleta parcial preservada.")
        }
        state.put(signatureKey, signature)
    }

    private fun hasNext(root: JSONObject) = root.getInt("pageIndex") + 1 < root.getInt("totalPages")
    private fun counted(state: JSONObject, count: Int) {
        state.put("pages", state.getInt("pages") + 1).put("records", state.getInt("records") + count)
    }
    private fun unparsed(state: JSONObject) { state.put("unparsed", state.getInt("unparsed") + 1) }
    private fun add(state: JSONObject, excerpt: JSONObject) { state.getJSONArray("matches").put(excerpt) }

    companion object {
        internal fun matches(product: JSONObject, target: String): Boolean =
            listOf("code", "barCode").any { key -> !product.isNull(key) && product.opt(key)?.toString()?.trim() == target }

        private fun without(source: JSONObject, vararg excluded: String) = JSONObject().also { out ->
            source.keys().forEach { key -> if (key !in excluded) out.put(key, source.get(key)) }
        }

        /** null means unreadable, not "product absent". Field names come from the observed dataLog. */
        internal fun historyMatches(row: JSONObject, target: String, pageIndex: Int): List<JSONObject>? {
            val data = when (val raw = row.opt("dataLog")) {
                is JSONObject -> raw
                is String -> runCatching { JSONObject(raw) }.getOrNull()
                else -> null
            } ?: return null
            val pages = data.optJSONArray("composedTemplates") ?: return null
            val excerpts = mutableListOf<JSONObject>()
            for (p in 0 until pages.length()) {
                val page = pages.optJSONObject(p) ?: return null
                val products = page.optJSONArray("items") ?: return null
                for (i in 0 until products.length()) {
                    val product = products.optJSONObject(i) ?: return null
                    if (matches(product, target)) excerpts += JSONObject()
                        .put("endpoint", "TemplatePrintLog/all").put("pageIndex", pageIndex)
                        .put("record", without(row, "dataLog", "user"))
                        .put("template", data.opt("template") ?: JSONObject.NULL)
                        .put("page", without(page, "items"))
                        .put("productPath", "dataLog.composedTemplates[$p].items[$i]")
                        .put("product", product)
                }
            }
            return excerpts
        }
    }
}
