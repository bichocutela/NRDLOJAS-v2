package com.example.data.flyer

import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Small durable checkpoints, never bitmap snapshots or a second copy of the whole document. */
internal class OrderImportCheckpoint(private val directory: File) {
    init { directory.mkdirs() }

    private fun read(name: String): String? = runCatching {
        AtomicFile(File(directory, name)).openRead().bufferedReader().use { it.readText() }
    }.getOrNull()

    private fun write(name: String, value: String) {
        val file = AtomicFile(File(directory, name))
        val stream = file.startWrite()
        try {
            stream.write(value.toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (failure: Throwable) {
            file.failWrite(stream)
            throw failure
        }
    }

    fun page(index: Int): List<FlyerTextBlock>? = read("page_$index.json")?.let { raw ->
        runCatching {
            val array = JSONArray(raw)
            List(array.length()) { i ->
                val b = array.getJSONObject(i)
                FlyerTextBlock(b.getInt("page"), b.getString("text"), b.getInt("left"),
                    b.getInt("top"), b.getInt("right"), b.getInt("bottom"),
                    b.getInt("pageWidth"), b.getInt("pageHeight"))
            }
        }.getOrNull()
    }

    fun savePage(index: Int, blocks: List<FlyerTextBlock>) {
        write("page_$index.json", JSONArray().apply {
            blocks.forEach { b -> put(JSONObject().apply {
                put("page", b.page); put("text", b.text); put("left", b.left); put("top", b.top)
                put("right", b.right); put("bottom", b.bottom)
                put("pageWidth", b.pageWidth); put("pageHeight", b.pageHeight)
            }) }
        }.toString())
    }

    fun analysis(): FlyerAnalysisResult? = read("analysis.json")?.let {
        runCatching { VisualMixReviewStore.decodeAnalysis(it) }.getOrNull()
    }
    fun saveAnalysis(result: FlyerAnalysisResult) = write("analysis.json", VisualMixReviewStore.encodeAnalysis(result))
    fun resolved(id: String): FlyerOffer? = read("resolved_$id.json")?.let {
        runCatching { VisualMixReviewStore.decodeAnalysis(it).offers.single() }.getOrNull()
    }
    fun saveResolved(offer: FlyerOffer) = write("resolved_${offer.id}.json",
        VisualMixReviewStore.encodeAnalysis(FlyerAnalysisResult("", null, null, listOf(offer), emptyList(), "", "")))
}
