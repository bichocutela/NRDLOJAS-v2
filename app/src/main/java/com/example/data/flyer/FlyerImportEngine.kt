package com.example.data.flyer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.data.acp.AcpApi
import com.example.data.acp.AcpProduct
import com.example.data.acp.AcpSearchField
import com.example.data.acp.searchProducts
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.math.max

internal data class FlyerAnalysisResult(
    val name: String,
    val validFrom: String?,
    val validTo: String?,
    val offers: List<FlyerOffer>,
    val warnings: List<String>,
    val sourceType: String,
    val sourceLabel: String
) {
    val confirmedCount: Int get() = offers.count { it.reviewed && it.matchStatus == FlyerMatchStatus.CONFIRMED }
    val reviewCount: Int get() = offers.count { !it.reviewed && it.matchStatus != FlyerMatchStatus.UNRESOLVED }
    val unresolvedCount: Int get() = offers.count { it.matchStatus == FlyerMatchStatus.UNRESOLVED }
}

internal object FlyerImportEngine {
    private const val MAX_FILE_BYTES = 25L * 1024L * 1024L
    private const val MAX_PDF_PAGES = 24
    private const val TARGET_PDF_WIDTH = 1440

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .callTimeout(55, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeUri(context: Context, uri: Uri): FlyerAnalysisResult = withContext(Dispatchers.IO) {
        val label = sourceLabel(context, uri)
        val blocks = extractBlocks(context, uri)
        analyzeBlocks(context, label, "gallery", label, blocks)
    }

    suspend fun analyzeDriveLink(context: Context, rawUrl: String): FlyerAnalysisResult = withContext(Dispatchers.IO) {
        val normalized = normalizeDriveUrl(rawUrl)
            ?: throw IllegalArgumentException("Use um link compartilhado válido do Google Drive.")
        val temp = downloadDriveFile(context, normalized)
        try {
            val uri = Uri.fromFile(temp)
            val label = temp.name.substringAfter('_').ifBlank { "Encarte do Drive" }
            val blocks = extractBlocks(context, uri)
            analyzeBlocks(context, label, "drive", "Google Drive", blocks)
        } finally {
            temp.delete()
        }
    }

    private suspend fun analyzeBlocks(
        context: Context,
        sourceName: String,
        sourceType: String,
        sourceLabel: String,
        blocks: List<FlyerTextBlock>
    ): FlyerAnalysisResult {
        if (blocks.isEmpty()) throw IllegalArgumentException("Não foi possível ler texto no encarte.")
        val parsed = FlyerOfferParser.parse(sourceName, blocks)
        val warnings = parsed.warnings.toMutableList()
        val enriched = enrichWithAcp(context, parsed.offers, warnings)
        return FlyerAnalysisResult(
            name = parsed.name,
            validFrom = parsed.validFrom,
            validTo = parsed.validTo,
            offers = enriched,
            warnings = warnings.distinct(),
            sourceType = sourceType,
            sourceLabel = sourceLabel
        )
    }

    private suspend fun enrichWithAcp(
        context: Context,
        offers: List<FlyerOffer>,
        warnings: MutableList<String>
    ): List<FlyerOffer> {
        val candidatesForAcp = offers.filter {
            it.scope == FlyerOfferScope.PRODUCT &&
                it.type != FlyerOfferType.FLYER_PRICE &&
                it.confidence >= 0.75 &&
                it.sourceDescription.isNotBlank()
        }
        if (candidatesForAcp.isEmpty()) return offers

        val api = AcpApi(context.applicationContext)
        val authenticated = runCatching {
            if (!api.restoreSession()) api.confirmAccess()
            true
        }.getOrElse {
            warnings += "As ofertas foram lidas, mas a ACP não pôde ser usada para confirmar os produtos agora."
            false
        }
        if (!authenticated) return offers

        val resolved = mutableMapOf<String, FlyerOffer>()
        for (offer in candidatesForAcp) {
            val match = runCatching { resolveProduct(api, offer.sourceDescription) }.getOrNull()
            if (match == null) {
                resolved[offer.id] = offer.copy(matchStatus = FlyerMatchStatus.REVIEW)
                continue
            }
            val (product, score, runnerUp) = match
            val safe = score >= 0.72 && (runnerUp == null || score - runnerUp >= 0.08)
            if (!safe) {
                resolved[offer.id] = offer.copy(
                    confidence = max(offer.confidence, score),
                    matchStatus = FlyerMatchStatus.REVIEW
                )
                continue
            }

            resolved[offer.id] = offer.copy(
                confidence = max(offer.confidence, score),
                matchStatus = FlyerMatchStatus.CONFIRMED,
                productCodes = listOfNotNull(product.code.takeIf { it.isNotBlank() }),
                barcodes = listOfNotNull(product.barcode.takeIf { it.isNotBlank() }),
                matchedProductName = product.description
            )
        }
        return offers.map { resolved[it.id] ?: it }
    }

    private data class MatchResult(val product: AcpProduct, val score: Double, val runnerUp: Double?)

    private suspend fun resolveProduct(api: AcpApi, description: String): MatchResult? {
        val queries = descriptionQueries(description)
        val all = linkedMapOf<String, AcpProduct>()
        for (query in queries) {
            val page = runCatching { api.searchProducts(AcpSearchField.DESCRIPTION, query, null, 0) }.getOrNull() ?: continue
            page.items.forEach { product -> all["${product.code}|${product.barcode}|${product.id}"] = product }
            if (all.size >= 20) break
        }
        if (all.isEmpty()) return null
        val scored = all.values.map { it to productScore(description, it.description) }
            .sortedByDescending { it.second }
        val best = scored.firstOrNull() ?: return null
        return MatchResult(best.first, best.second, scored.getOrNull(1)?.second)
    }

    private fun descriptionQueries(description: String): List<String> {
        val clean = description
            .replace(Regex("(?i)\\b(leve|pague|desconto|segunda|unidade|cashback|cada)\\b.*"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(120)
        val tokens = normalizeText(clean).split(' ')
            .filter { it.length >= 3 && it !in STOP_WORDS }
        return buildList {
            if (clean.length >= 4) add(clean)
            if (tokens.size >= 3) add(tokens.take(4).joinToString(" "))
            tokens.sortedByDescending(String::length).take(2).forEach(::add)
        }.map { it.trim() }.filter { it.length >= 3 }.distinct().take(4)
    }

    private fun productScore(source: String, candidate: String): Double {
        val sourceTokens = normalizeText(source).split(' ').filter { it.length >= 2 && it !in STOP_WORDS }.toSet()
        val candidateTokens = normalizeText(candidate).split(' ').filter { it.length >= 2 && it !in STOP_WORDS }.toSet()
        if (sourceTokens.isEmpty() || candidateTokens.isEmpty()) return 0.0
        val intersection = sourceTokens.intersect(candidateTokens).size.toDouble()
        val coverage = intersection / sourceTokens.size
        val jaccard = intersection / sourceTokens.union(candidateTokens).size
        val sourceNumbers = Regex("\\d+(?:[.,]\\d+)?").findAll(source).map { it.value.replace(',', '.') }.toSet()
        val candidateNumbers = Regex("\\d+(?:[.,]\\d+)?").findAll(candidate).map { it.value.replace(',', '.') }.toSet()
        val numeric = when {
            sourceNumbers.isEmpty() -> 0.5
            sourceNumbers.any { it in candidateNumbers } -> 1.0
            else -> 0.0
        }
        return (coverage * 0.62 + jaccard * 0.28 + numeric * 0.10).coerceIn(0.0, 1.0)
    }

    private suspend fun extractBlocks(context: Context, uri: Uri): List<FlyerTextBlock> {
        val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
        val name = sourceLabel(context, uri).lowercase()
        return if (mime.contains("pdf") || name.endsWith(".pdf") || isPdf(context, uri)) {
            extractPdfBlocks(context, uri)
        } else {
            val bitmap = decodeImage(context, uri)
                ?: throw IllegalArgumentException("A imagem do encarte não pôde ser aberta.")
            try { recognizeBitmap(bitmap, 1) } finally { bitmap.recycle() }
        }
    }

    private suspend fun extractPdfBlocks(context: Context, uri: Uri): List<FlyerTextBlock> {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalArgumentException("O PDF não pôde ser aberto.")
        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount <= 0) throw IllegalArgumentException("O PDF está vazio.")
                if (renderer.pageCount > MAX_PDF_PAGES) throw IllegalArgumentException("O encarte excede o limite de $MAX_PDF_PAGES páginas.")
                val result = mutableListOf<FlyerTextBlock>()
                for (index in 0 until renderer.pageCount) {
                    renderer.openPage(index).use { page ->
                        val scale = (TARGET_PDF_WIDTH.toFloat() / page.width.coerceAtLeast(1)).coerceIn(1f, 2.5f)
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        try {
                            val matrix = android.graphics.Matrix().apply { postScale(scale, scale) }
                            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            result += recognizeBitmap(bitmap, index + 1)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
                return result
            }
        }
    }

    private suspend fun recognizeBitmap(bitmap: Bitmap, page: Int): List<FlyerTextBlock> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            result.textBlocks.mapNotNull { block ->
                val box = block.boundingBox ?: return@mapNotNull null
                FlyerTextBlock(
                    page = page,
                    text = block.text,
                    left = box.left,
                    top = box.top,
                    right = box.right,
                    bottom = box.bottom,
                    pageWidth = bitmap.width,
                    pageHeight = bitmap.height
                )
            }
        } finally {
            recognizer.close()
        }
    }

    private fun decodeImage(context: Context, uri: Uri): Bitmap? {
        val bytes = readLimited(context, uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 2200 || bounds.outHeight / sample > 3000) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun readLimited(context: Context, uri: Uri): ByteArray {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("O arquivo não pôde ser lido.")
        return stream.use { input ->
            val buffer = ByteArray(8192)
            val output = java.io.ByteArrayOutputStream()
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_FILE_BYTES) throw IllegalArgumentException("O encarte excede o limite de 25 MB.")
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

    private fun isPdf(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val header = ByteArray(5)
            input.read(header) == 5 && String(header, Charsets.US_ASCII) == "%PDF-"
        } ?: false
    }.getOrDefault(false)

    private fun sourceLabel(context: Context, uri: Uri): String {
        if (uri.scheme == "file") return uri.lastPathSegment.orEmpty().ifBlank { "Encarte" }
        return runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull().orEmpty().ifBlank { uri.lastPathSegment.orEmpty().ifBlank { "Encarte" } }
    }

    private fun normalizeDriveUrl(raw: String): String? {
        val value = raw.trim()
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() != "https") return null
        val host = uri.host?.lowercase().orEmpty()
        if (host !in setOf("drive.google.com", "docs.google.com")) return null
        val idFromPath = Regex("/file/d/([A-Za-z0-9_-]+)").find(uri.path.orEmpty())?.groupValues?.getOrNull(1)
        val idFromQuery = uri.rawQuery.orEmpty().split('&')
            .mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 } }
            .firstOrNull { it[0] == "id" }?.get(1)
        val id = idFromPath ?: idFromQuery
        return if (!id.isNullOrBlank()) "https://drive.google.com/uc?export=download&id=$id" else value
    }

    private fun downloadDriveFile(context: Context, url: String): File {
        val request = Request.Builder().url(url).header("User-Agent", "NRDLOJAS/1.0").get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalArgumentException("Não foi possível baixar o arquivo do Drive (HTTP ${response.code}).")
            val length = response.body?.contentLength() ?: -1L
            if (length > MAX_FILE_BYTES) throw IllegalArgumentException("O encarte excede o limite de 25 MB.")
            val contentType = response.header("Content-Type").orEmpty().lowercase()
            if (contentType.contains("text/html")) throw IllegalArgumentException("O link do Drive não está liberado para download. Compartilhe o arquivo com acesso pelo link.")
            val ext = when {
                contentType.contains("pdf") -> ".pdf"
                contentType.contains("png") -> ".png"
                contentType.contains("webp") -> ".webp"
                else -> ".jpg"
            }
            val output = File.createTempFile("flyer_drive_", ext, context.cacheDir)
            val body = response.body ?: throw IllegalArgumentException("O Drive respondeu sem arquivo.")
            body.byteStream().use { input ->
                FileOutputStream(output).use { out ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_FILE_BYTES) {
                            output.delete()
                            throw IllegalArgumentException("O encarte excede o limite de 25 MB.")
                        }
                        out.write(buffer, 0, read)
                    }
                }
            }
            // Some Drive links do not return a useful MIME type. Fix extension from magic bytes.
            if (ext != ".pdf" && output.inputStream().use { input ->
                    val h = ByteArray(5); input.read(h) == 5 && String(h, Charsets.US_ASCII) == "%PDF-"
                }
            ) {
                val pdf = File(output.parentFile, output.nameWithoutExtension + ".pdf")
                if (output.renameTo(pdf)) return pdf
            }
            return output
        }
    }

    private val STOP_WORDS = setOf(
        "com", "sem", "para", "das", "dos", "de", "da", "do", "em", "ou", "pct", "cx", "tb", "gfa", "lta", "cada"
    )
}
