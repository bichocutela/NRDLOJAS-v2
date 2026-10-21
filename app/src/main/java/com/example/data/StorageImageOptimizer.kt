package com.example.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import java.io.ByteArrayOutputStream

/** Keep the original URL and dimensions; only use the platform's lossless codec. */
internal object StorageImageOptimizer {
    data class Upload(val bytes: ByteArray, val mime: String)

    fun optimize(bytes: ByteArray, mime: String): Upload {
        val original = Upload(bytes, mime)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || bytes.size !in 65536..8_388_608) return original
        val signature = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        if (!bytes.copyOfRange(0, 8).contentEquals(signature)) return original
        // Preserve animation, EXIF orientation, high bit depth, and embedded color profiles unchanged.
        var offset = 8
        while (offset + 12 <= bytes.size) {
            val length = (0..3).fold(0L) { value, index -> (value shl 8) or (bytes[offset + index].toLong() and 255) }
            if (length > bytes.size - offset - 12) return original
            val type = String(bytes, offset + 4, 4, Charsets.US_ASCII)
            if (type in setOf("acTL", "iCCP", "eXIf", "cICP")) return original
            if (type == "IHDR" && (length != 13L || bytes[offset + 16].toInt() > 8)) return original
            offset += length.toInt() + 12
        }
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (options.outWidth <= 0 || options.outHeight <= 0 || options.outWidth.toLong() * options.outHeight > 12_000_000) return original
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return original
            try {
                val output = ByteArrayOutputStream()
                if (!bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, output)) return original
                val encoded = output.toByteArray()
                if (encoded.size < bytes.size * 0.9) Upload(encoded, "image/webp") else original
            } finally {
                bitmap.recycle()
            }
        } catch (_: Exception) {
            original
        }
    }
}
