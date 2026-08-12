package com.example.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.example.data.Product
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object PdfExporter {
    fun exportProductsToPdf(context: Context, products: List<Product>): String? {
        val pdfDocument = PdfDocument()
        val paint = Paint()
        val titlePaint = Paint()

        var pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
        var page = pdfDocument.startPage(pageInfo)
        var canvas: Canvas = page.canvas

        titlePaint.textAlign = Paint.Align.CENTER
        titlePaint.textSize = 18f
        titlePaint.isFakeBoldText = true

        paint.textSize = 12f

        canvas.drawText("Relatório de Inventário - Nordestão", pageInfo.pageWidth / 2f, 50f, titlePaint)

        var yPosition = 90f
        val margin = 50f

        canvas.drawText("Código", margin, yPosition, titlePaint.apply { textAlign = Paint.Align.LEFT; textSize = 14f })
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
            
            // Truncate name if too long
            var name = product.name
            if (name.length > 30) {
                name = name.substring(0, 27) + "..."
            }

            canvas.drawText(product.code, margin, yPosition, paint)
            canvas.drawText(name, margin + 100f, yPosition, paint)
            canvas.drawText(product.category, margin + 350f, yPosition, paint)

            yPosition += 25f
        }

        pdfDocument.finishPage(page)

        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val file = File(downloadsDir, "Inventario_Nordestao_${System.currentTimeMillis()}.pdf")

        return try {
            pdfDocument.writeTo(FileOutputStream(file))
            file.absolutePath
        } catch (e: IOException) {
            e.printStackTrace()
            null
        } finally {
            pdfDocument.close()
        }
    }
}
