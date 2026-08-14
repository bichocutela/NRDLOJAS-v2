package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.Product
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {
    fun exportProductsToPdf(context: Context, products: List<Product>): String? {
        val pdfDocument = PdfDocument()
        return try {
            val paint = Paint()
            val titlePaint = Paint()

            var pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas: Canvas = page.canvas

            titlePaint.textAlign = Paint.Align.CENTER
            titlePaint.textSize = 18f
            titlePaint.isFakeBoldText = true
            paint.textSize = 12f

            canvas.drawText("Relatório de Inventário - Nordestão", pageInfo.pageWidth / 2f, 50f, titlePaint)

            var yPosition = 90f
            val margin = 50f
            titlePaint.textAlign = Paint.Align.LEFT
            titlePaint.textSize = 14f
            canvas.drawText("Código", margin, yPosition, titlePaint)
            canvas.drawText("Produto", margin + 100f, yPosition, titlePaint)
            canvas.drawText("Categoria", margin + 350f, yPosition, titlePaint)

            yPosition += 30f
            paint.textAlign = Paint.Align.LEFT

            for (product in products) {
                if (yPosition > pageInfo.pageHeight - 50) {
                    pdfDocument.finishPage(page)
                    pageInfo = PdfDocument.PageInfo.Builder(595, 842, pdfDocument.pages.size + 1).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    yPosition = 50f
                }

                val name = if (product.name.length > 30) {
                    product.name.substring(0, 27) + "..."
                } else {
                    product.name
                }

                canvas.drawText(product.code, margin, yPosition, paint)
                canvas.drawText(name, margin + 100f, yPosition, paint)
                canvas.drawText(product.category, margin + 350f, yPosition, paint)
                yPosition += 25f
            }

            pdfDocument.finishPage(page)
            val pdfBytes = ByteArrayOutputStream().use { output ->
                pdfDocument.writeTo(output)
                output.toByteArray()
            }
            saveToDownloads(context, pdfBytes)
        } catch (e: IOException) {
            e.printStackTrace()
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            pdfDocument.close()
        }
    }

    private fun saveToDownloads(context: Context, pdfBytes: ByteArray): String? {
        val fileName = "NRD_Produtos_${SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())}.pdf"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            try {
                resolver.openOutputStream(uri)?.use { output ->
                    output.write(pdfBytes)
                } ?: throw IOException("Não foi possível abrir o arquivo de Downloads")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri.toString()
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists() && !downloadsDir.mkdirs()) return null

            var file = File(downloadsDir, fileName)
            var suffix = 1
            while (file.exists()) {
                file = File(downloadsDir, "NRD_Produtos_${SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())}_$suffix.pdf")
                suffix++
            }
            FileOutputStream(file).use { output -> output.write(pdfBytes) }
            file.absolutePath
        }
    }
}
