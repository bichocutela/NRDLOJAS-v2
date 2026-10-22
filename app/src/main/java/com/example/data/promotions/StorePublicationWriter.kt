package com.example.data.promotions

/** Publish pages before exposing a revision, then confirm the saved pointer. */
internal class StorePublicationWriter(
    private val write: suspend (String, Map<String, Any?>, Boolean) -> Unit,
    private val read: suspend (String) -> Map<String, Any>
) {
    suspend fun publish(code: String, revision: String, records: List<StorePromotionRecord>, source: String, updatedAt: Long): Map<String, Any> {
        val pages = records.chunked(200)
        pages.forEachIndexed { index, page ->
            write("config/promotion_${code}_${revision}_$index", mapOf("items" to page.map {
                mapOf("code" to it.code, "barcode" to it.barcode, "name" to it.name,
                    "price" to it.price, "previous" to it.previous, "from" to it.from, "to" to it.to)
            }), false)
        }
        write("config/promotion_stores", mapOf(code to mapOf("enabled" to true, "revision" to revision,
            "pages" to pages.size, "count" to records.size, "source" to source, "updatedAt" to updatedAt)), true)
        val saved = read("config/promotion_stores")
        val store = saved[code] as? Map<*, *>
        check(store != null && store["enabled"] == true && store["revision"] == revision &&
            (store["pages"] as? Number)?.toInt() == pages.size &&
            (store["count"] as? Number)?.toInt() == records.size) {
            "Não foi possível confirmar a publicação da loja. Confira a conexão e tente novamente."
        }
        return saved
    }
}
