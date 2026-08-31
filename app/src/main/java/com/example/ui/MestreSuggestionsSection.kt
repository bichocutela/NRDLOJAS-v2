package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ProductSuggestion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import com.example.ui.theme.glassSoftShadow

private const val SUGGESTIONS_PAGE_SIZE = 10

@Composable
internal fun MestreSuggestionsSection(
    suggestions: List<ProductSuggestion>,
    showHeader: Boolean = true,
    onStatusChange: suspend (ProductSuggestion, String) -> Unit
) {
    var suggestionFilter by remember { mutableStateOf("all") }
    var suggestionPage by remember { mutableIntStateOf(0) }
    var updatingSuggestionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val coroutineScope = rememberCoroutineScope()

    if (showHeader) {
        MestreSectionHeader(
            title = "Pendências",
            description = "Analise sugestões dos usuários e marque solicitações como corrigidas"
        )
        Spacer(modifier = Modifier.height(6.dp))
    }
    OutlinedCard(modifier = Modifier.fillMaxWidth().glassSoftShadow(MaterialTheme.shapes.medium)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Sugestões dos usuários",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    suggestions.count { it.status == ProductSuggestion.STATUS_PENDING }.toString(),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                listOf(
                    "all" to "Todas",
                    "pending" to "Pendentes",
                    "fixed" to "Corrigidas"
                ).forEach { (filterKey, filterLabel) ->
                    FilterChip(
                        selected = suggestionFilter == filterKey,
                        onClick = {
                            suggestionFilter = filterKey
                            suggestionPage = 0
                        },
                        label = { Text(filterLabel, maxLines = 1) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            val filteredSuggestions = suggestions
                .filter { suggestion ->
                    suggestionFilter == "all" ||
                        (suggestionFilter == "pending" && suggestion.status == ProductSuggestion.STATUS_PENDING) ||
                        (suggestionFilter == "fixed" && suggestion.status == ProductSuggestion.STATUS_FIXED)
                }
                .sortedBy { if (it.status == ProductSuggestion.STATUS_PENDING) 0 else 1 }
            val pagination = calculatePaginationWindow(
                totalItems = filteredSuggestions.size,
                requestedPage = suggestionPage,
                pageSize = SUGGESTIONS_PAGE_SIZE
            )

            LaunchedEffect(suggestionFilter, filteredSuggestions.size) {
                if (suggestionPage != pagination.pageIndex) {
                    suggestionPage = pagination.pageIndex
                }
            }

            if (filteredSuggestions.isEmpty()) {
                Text(
                    when (suggestionFilter) {
                        "pending" -> "Nenhuma pendência no momento."
                        "fixed" -> "Nenhuma sugestão corrigida."
                        else -> "Nenhuma sugestão recebida."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    "Exibindo ${pagination.fromIndex + 1}–${pagination.toIndex} de ${filteredSuggestions.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                filteredSuggestions
                    .subList(pagination.fromIndex, pagination.toIndex)
                    .forEach { suggestion ->
                        val isUpdating = suggestion.id in updatingSuggestionIds
                        SuggestionManagementItem(
                            suggestion = suggestion,
                            isUpdating = isUpdating
                        ) { status ->
                            if (suggestion.id !in updatingSuggestionIds) {
                                updatingSuggestionIds = updatingSuggestionIds + suggestion.id
                                coroutineScope.launch {
                                    try {
                                        onStatusChange(suggestion, status)
                                    } finally {
                                        updatingSuggestionIds = updatingSuggestionIds - suggestion.id
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                if (pagination.pageCount > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { suggestionPage = pagination.pageIndex - 1 },
                            enabled = pagination.pageIndex > 0
                        ) {
                            Text("Anterior")
                        }
                        Text(
                            "Página ${pagination.pageIndex + 1} de ${pagination.pageCount}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        TextButton(
                            onClick = { suggestionPage = pagination.pageIndex + 1 },
                            enabled = pagination.pageIndex < pagination.pageCount - 1
                        ) {
                            Text("Próxima")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MestreSuggestionsPreview(
    suggestions: List<ProductSuggestion>,
    onViewAll: () -> Unit
) {
    val pendingSuggestions = suggestions
        .filter { it.status == ProductSuggestion.STATUS_PENDING }
        .sortedByDescending { it.createdAt }

    MestreSectionHeader(
        title = "Pendências recentes",
        description = "Acompanhe as solicitações que precisam da sua atenção"
    )
    Spacer(modifier = Modifier.height(6.dp))
    OutlinedCard(modifier = Modifier.fillMaxWidth().glassSoftShadow(MaterialTheme.shapes.medium)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sugestões dos usuários", style = MaterialTheme.typography.titleSmall)
                Badge(containerColor = if (pendingSuggestions.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) {
                    Text(pendingSuggestions.size.toString())
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            if (pendingSuggestions.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Nenhuma pendência no momento.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                pendingSuggestions.take(3).forEachIndexed { index, suggestion ->
                    SuggestionPreviewItem(suggestion)
                    if (index < minOf(2, pendingSuggestions.lastIndex)) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
            if (suggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(onClick = onViewAll, modifier = Modifier.align(Alignment.End)) {
                    Text(if (pendingSuggestions.isEmpty()) "Ver histórico" else "Ver todas")
                }
            }
        }
    }
}

@Composable
private fun SuggestionPreviewItem(suggestion: ProductSuggestion) {
    Card(
        modifier = Modifier.fillMaxWidth().glassSoftShadow(MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                suggestion.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "${suggestion.submittedBy} · ${formatSuggestionDate(suggestion.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SuggestionManagementItem(
    suggestion: ProductSuggestion,
    isUpdating: Boolean,
    onStatusChange: (String) -> Unit
) {
    val dateText = formatSuggestionDate(suggestion.createdAt)
    val isFixed = suggestion.status == ProductSuggestion.STATUS_FIXED
    Card(
        modifier = Modifier.fillMaxWidth().glassSoftShadow(MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(suggestion.text, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(3.dp))
            Text("Enviada por: ${suggestion.submittedBy}", style = MaterialTheme.typography.bodySmall)
            Text(dateText, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(6.dp))
            if (isUpdating) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onStatusChange(ProductSuggestion.STATUS_PENDING) },
                    enabled = isFixed && !isUpdating,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Default.Pending, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pendente", maxLines = 1)
                }
                Button(
                    onClick = { onStatusChange(ProductSuggestion.STATUS_FIXED) },
                    enabled = !isFixed && !isUpdating,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Corrigido", maxLines = 1)
                }
            }
        }
    }
}

private fun formatSuggestionDate(timestamp: Long): String = if (timestamp > 0L) {
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(timestamp))
} else {
    "Data não informada"
}
