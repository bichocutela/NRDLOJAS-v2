from pathlib import Path

path = Path('app/src/main/java/com/example/ui/AppNavGraph.kt')
text = path.read_text(encoding='utf-8')

if 'import androidx.compose.foundation.clickable' not in text:
    text = text.replace('import androidx.compose.foundation.background\n', 'import androidx.compose.foundation.background\nimport androidx.compose.foundation.clickable\n', 1)

start = text.index('@Composable\nfun CategoryItem(')
new = r'''@Composable
fun CategoryItem(
    category: String,
    viewModel: MainViewModel,
    isExpanded: Boolean,
    accentBrush: Brush? = null,
    accentColor: Color? = null,
    onExpandToggle: () -> Unit
) {
    val allProducts by viewModel.allProducts.collectAsState()
    val products = remember(allProducts, category) {
        allProducts.filter { it.category.equals(category, ignoreCase = true) }
            .sortedBy { it.name.lowercase() }
    }
    val glass = rememberGlassVisualStyle(viewModel)
    val shape = RoundedCornerShape(18.dp)
    val glassFillAlpha = (glass.alpha * 0.68f).coerceIn(0.24f, 0.62f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (glass.enabled) 4.dp else 0.dp)
            .clip(shape)
            .then(
                if (glass.enabled) {
                    Modifier
                        .background(glass.fill.copy(alpha = glassFillAlpha))
                        .border(1.dp, glass.border, shape)
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable(onClick = onExpandToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = category,
                style = if (!glass.enabled && accentBrush != null) {
                    MaterialTheme.typography.titleMedium.merge(TextStyle(brush = accentBrush))
                } else MaterialTheme.typography.titleMedium,
                color = when {
                    glass.enabled -> glass.highlight
                    accentBrush != null -> Color.Unspecified
                    else -> (accentColor ?: MaterialTheme.colorScheme.primary)
                }
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Recolher" else "Expandir",
                tint = if (glass.enabled) glass.highlight else (accentColor ?: MaterialTheme.colorScheme.primary)
            )
        }

        if (isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            ) {
                if (products.isEmpty()) {
                    Text(
                        "Nenhum produto nesta categoria.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    products.forEach { product ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = product.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = product.code,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (glass.enabled) glass.highlight else MaterialTheme.colorScheme.primary
                            )
                        }
                        HorizontalDivider(
                            color = if (glass.enabled) glass.border.copy(alpha = 0.28f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}
'''
path.write_text(text[:start] + new, encoding='utf-8')
print('Drawer categories v2 aplicado')
