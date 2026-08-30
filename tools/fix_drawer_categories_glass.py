from pathlib import Path

path = Path('app/src/main/java/com/example/ui/AppNavGraph.kt')
text = path.read_text(encoding='utf-8')
start = text.index('@Composable\nfun CategoryItem(')
# CategoryItem is the last function in this file today; replace to EOF safely.
old = text[start:]
new = '''@Composable
fun CategoryItem(
    category: String,
    viewModel: MainViewModel,
    isExpanded: Boolean,
    accentBrush: Brush? = null,
    accentColor: Color? = null,
    onExpandToggle: () -> Unit
) {
    val productsFlow = remember(category) { viewModel.getProductsByCategory(category) }
    val products by if (isExpanded) {
        productsFlow.collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList()) }
    }
    val glass = rememberGlassVisualStyle(viewModel)
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (glass.enabled) 4.dp else 0.dp)
            .clip(shape)
            .then(
                if (glass.enabled) Modifier
                    .background(glass.fill.copy(alpha = glass.alpha), shape)
                    .border(1.dp, glass.border, shape)
                else Modifier
            )
    ) {
        TextButton(
            onClick = onExpandToggle,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            colors = ButtonDefaults.textButtonColors(
                containerColor = Color.Transparent,
                contentColor = if (glass.enabled) glass.highlight else (accentColor ?: MaterialTheme.colorScheme.primary)
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        else -> LocalContentColor.current
                    }
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Recolher" else "Expandir",
                    tint = if (glass.enabled) glass.highlight else (accentColor ?: LocalContentColor.current)
                )
            }
        }

        if (isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            ) {
                if (products.isEmpty()) {
                    Text(
                        "Carregando...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
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
                            color = if (glass.enabled) glass.border.copy(alpha = 0.45f)
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
print('Categorias do drawer restauradas com Glass recortado')
