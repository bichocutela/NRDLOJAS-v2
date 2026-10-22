package com.example.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Linha validada parcialmente durante a prévia da importação. */
data class ProductImportRow(
    val name: String,
    val code: String,
    val category: String,
    val unit: String,
    val imageUrl: String? = null,
    val lineNumber: Int
)

data class ProductImportResult(
    val rows: List<ProductImportRow>,
    val errors: List<String>,
    val delimiter: Char,
    val totalDataRows: Int
)

data class ProductImportCommitResult(
    val importedCount: Int,
    val skippedRows: Int,
    val errors: List<String>
)

object ProductImportParser {
    private const val MAX_ROWS = 500

    suspend fun parse(context: Context, uri: Uri): ProductImportResult = withContext(Dispatchers.IO) {
        try {
            val lines = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                reader.readLines()
            } ?: return@withContext ProductImportResult(emptyList(), listOf("Não foi possível ler o arquivo selecionado."), ';', 0)

            val nonEmptyLines = lines.filter { it.isNotBlank() }
            if (nonEmptyLines.isEmpty()) {
                return@withContext ProductImportResult(emptyList(), listOf("A planilha está vazia."), ';', 0)
            }

            val header = nonEmptyLines.first().removePrefix("\uFEFF")
            val delimiter = detectDelimiter(header)
            val columns = parseLine(header, delimiter).map { normalizeHeader(it) }
            val nameIndex = findColumn(columns, "nome", "produto", "name")
            val codeIndex = findColumn(columns, "codigo", "cod", "code")
            val categoryIndex = findColumn(columns, "categoria", "category", "grupo")
            val unitIndex = findColumn(columns, "unidade", "unit", "und")
            val imageIndex = findColumn(columns, "imagem", "image", "imageurl", "urlimagem")
            val headerErrors = buildList {
                if (nameIndex == null) add("A coluna nome é obrigatória.")
                if (codeIndex == null) add("A coluna codigo é obrigatória.")
                if (categoryIndex == null) add("A coluna categoria é obrigatória.")
            }
            if (headerErrors.isNotEmpty()) {
                return@withContext ProductImportResult(emptyList(), headerErrors, delimiter, 0)
            }

            val dataLines = nonEmptyLines.drop(1)
            val errors = mutableListOf<String>()
            val rows = mutableListOf<ProductImportRow>()
            dataLines.take(MAX_ROWS).forEachIndexed { offset, line ->
                val lineNumber = offset + 2
                val values = parseLine(line, delimiter)
                fun valueAt(index: Int?) = index?.let { values.getOrNull(it)?.trim().orEmpty() }.orEmpty()
                val name = valueAt(nameIndex)
                val code = valueAt(codeIndex)
                val category = valueAt(categoryIndex)
                val unit = valueAt(unitIndex).ifBlank { "UN" }
                val imageUrl = valueAt(imageIndex).takeIf { it.isNotBlank() }
                when {
                    name.isBlank() -> errors += "Linha $lineNumber: nome vazio."
                    code.isBlank() -> errors += "Linha $lineNumber: código vazio."
                    category.isBlank() -> errors += "Linha $lineNumber: categoria vazia."
                    else -> rows += ProductImportRow(name, code, category, unit, imageUrl, lineNumber)
                }
            }
            if (dataLines.size > MAX_ROWS) {
                errors += "Apenas as primeiras $MAX_ROWS linhas foram carregadas para a prévia."
            }
            ProductImportResult(rows, errors, delimiter, dataLines.size)
        } catch (e: IOException) {
            ProductImportResult(emptyList(), listOf("Não foi possível ler a planilha."), ';', 0)
        } catch (e: Exception) {
            ProductImportResult(emptyList(), listOf("Formato de planilha não reconhecido."), ';', 0)
        }
    }

    private fun detectDelimiter(header: String): Char {
        return listOf(';', '\t', ',').maxByOrNull { delimiter -> header.count { it == delimiter } } ?: ';'
    }

    private fun normalizeHeader(value: String): String = ProductStandards.searchNameFrom(value)
        .replace(Regex("[^a-z0-9]"), "")

    private fun findColumn(columns: List<String>, vararg names: String): Int? {
        val accepted = names.map { normalizeHeader(it) }.toSet()
        return columns.indexOfFirst { it in accepted }.takeIf { it >= 0 }
    }

    private fun parseLine(line: String, delimiter: Char): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index += 1
                }
                char == '"' -> quoted = !quoted
                char == delimiter && !quoted -> {
                    values += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index += 1
        }
        values += current.toString()
        return values
    }
}
