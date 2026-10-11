package com.example.data.timeclock

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File

object ClockExport {
    fun share(context: Context, month: String, closing: Int, days: List<Workday>, folgas: Set<String>, adjustments: List<ClockAdjustment>, pdf: Boolean) {
        val period = TimeClock.periodDates(month, closing)
        val records = days.filter { it.date in period }.associateBy { it.date }
        val totals = TimeClock.totals(records.values.toList())
        val ledger = adjustments.filter { it.date in period }
        val lines = buildList {
            add("Controle pessoal de ponto - $month")
            add("Periodo: ${TimeClock.displayDate(period.first())} a ${TimeClock.displayDate(period.last())}")
            add("Credito ${TimeClock.duration(totals.credit)} | Debito ${TimeClock.duration(totals.debit)} | Saldo ${TimeClock.duration(totals.balance + ledger.sumOf { it.minutes }, true)}")
            add("Minutos reais. Nao substitui o espelho oficial. Pendencias: ${totals.pending}")
            add("Data;Entrada;Almoco ida;Volta;Saida;Trabalhadas;Previstas;Credito;Debito;Saldo;Ocorrencias;Observacao")
            period.forEach { date ->
                val d = records[date]
                add(listOf(TimeClock.displayDate(date), *(d?.punches ?: List(4) { null }).map { TimeClock.time(it) }.toTypedArray(),
                    d?.workedMinutes?.let { TimeClock.duration(it) } ?: "", d?.let { TimeClock.duration(if (it.dayOff || it.holiday) 0 else it.expectedMinutes) } ?: "",
                    d?.let { TimeClock.duration(it.creditMinutes) } ?: "", d?.let { TimeClock.duration(it.debitMinutes) } ?: "", d?.balanceMinutes?.let { TimeClock.duration(it,true) } ?: "",
                    d?.occurrences()?.joinToString(" / ") ?: if (date in folgas) "Folga" else "Sem registro", d?.note ?: "").joinToString(";") { csv(it) })
                d?.extras?.chunked(2)?.forEach { add("Extra;${TimeClock.displayDate(date)};${TimeClock.time(it[0])};${TimeClock.time(it[1])}") }
            }
            add("Movimentacoes de compensacao (separadas das batidas)")
            ledger.forEach { add("${TimeClock.displayDate(it.date)};${TimeClock.duration(it.minutes, true)};${csv(it.reason)}") }
        }
        val folder = File(context.cacheDir, "clock_exports").apply { mkdirs() }
        val file = File(folder, "ponto-$month.${if (pdf) "pdf" else "csv"}")
        if (pdf) {
            val doc = PdfDocument(); val paint = Paint().apply { textSize = 10f; color = android.graphics.Color.BLACK; isAntiAlias = true }
            // Wrap every line before pagination; no clipped long notes or landscape table columns.
            val wrapped = lines.flatMap { line ->
                val result = mutableListOf<String>(); var remainder = line.replace(';', ' ').replace('\n', ' ')
                while (remainder.length > 0) {
                    val count = paint.breakText(remainder, true, 790f, null).coerceAtLeast(1)
                    result.add(remainder.take(count)); remainder = remainder.drop(count)
                }
                result.ifEmpty { listOf("") }
            }
            try { wrapped.chunked(36).forEachIndexed { index, rows ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(842, 595, index + 1).create())
                rows.forEachIndexed { row, line -> page.canvas.drawText(line, 24f, 32f + row * 14f, paint) }
                page.canvas.drawText("Pagina ${index + 1}", 740f, 580f, paint)
                doc.finishPage(page)
            }; file.outputStream().use { doc.writeTo(it) } } finally { doc.close() }
        } else file.writeText("\uFEFF" + lines.joinToString("\r\n"))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = if (pdf) "application/pdf" else "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Exportar espelho do ponto"))
    }
    private fun csv(value: String): String {
        val safe = if (value.firstOrNull() in listOf('=', '+', '-', '@')) "'" + value else value
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }
}
