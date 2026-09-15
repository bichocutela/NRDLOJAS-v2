from pathlib import Path

# 1) Corrige o limite que estava congelando o histórico em catálogos grandes.
store_path = Path('app/src/main/java/com/example/data/PromotionChangeStore.kt')
store = store_path.read_text(encoding='utf-8')
old = 'private const val MAX_SNAPSHOT_ENTRIES = 15_000\n'
new = 'private const val MAX_SNAPSHOT_ENTRIES = 60_000\n'
if old not in store:
    raise SystemExit('limite do snapshot não encontrado')
store = store.replace(old, new, 1)
store_path.write_text(store, encoding='utf-8')

# 2) Faz o balão usar todas as mudanças da loja, sem descartar ALTERADAS/REMOVIDAS.
path = Path('app/src/main/java/com/example/ui/PromotionsScreen.kt')
s = path.read_text(encoding='utf-8')

old = '''    val newestOfferGroups = pendingUpdate?.offerGroups ?: offerGroups\n    val newOfferChangesForSelectedStore = remember(dailyChanges, newestOfferGroups, selectedStore) {\n        dailyChanges\n            .asReversed()\n            .asSequence()\n            .filter { it.type == PromotionChangeType.ADDED }\n            .filter { selectedStore == ALL_STORES_LABEL || it.storeCode == selectedStore }\n            .filter { newestOfferGroups.findOfferForChange(it) != null }\n            .distinctBy { it.stableKey }\n            .toList()\n    }\n\n    if (showNewOffers) {\n        NewOffersDialog(\n            changes = newOfferChangesForSelectedStore,\n            selectedStore = selectedStore,\n            limitedBySafetyCap = dailyChangesLimited,\n            onDismiss = { showNewOffers = false },\n            onOfferClick = { change ->\n'''
new = '''    val newestOfferGroups = pendingUpdate?.offerGroups ?: offerGroups\n    val offerChangesForSelectedStore = remember(dailyChanges, selectedStore) {\n        dailyChanges\n            .asReversed()\n            .asSequence()\n            .filter { selectedStore == ALL_STORES_LABEL || it.storeCode == selectedStore }\n            .distinctBy { it.stableKey }\n            .toList()\n    }\n\n    if (showNewOffers) {\n        NewOffersDialog(\n            changes = offerChangesForSelectedStore,\n            selectedStore = selectedStore,\n            limitedBySafetyCap = dailyChangesLimited,\n            canOpenOffer = { change -> newestOfferGroups.findOfferForChange(change) != null },\n            onDismiss = { showNewOffers = false },\n            onOfferClick = { change ->\n'''
if old not in s:
    raise SystemExit('filtro principal de ofertas novas não encontrado')
s = s.replace(old, new, 1)

old = '''                        NewOffersButton(\n                            changeCount = newOfferChangesForSelectedStore.size,\n                            highlighted = newOfferChangesForSelectedStore.isNotEmpty(),\n'''
new = '''                        NewOffersButton(\n                            changeCount = offerChangesForSelectedStore.size,\n                            highlighted = offerChangesForSelectedStore.isNotEmpty(),\n'''
if old not in s:
    raise SystemExit('contador do botão não encontrado')
s = s.replace(old, new, 1)

# 3) Substitui o diálogo/card antigo por um histórico visual de ENTRADAS, ALTERAÇÕES e SAÍDAS.
start_marker = '''@Composable\nprivate fun NewOffersDialog(\n'''
end_marker = '''private fun PromotionChangeType.displayOrder(): Int = when (this) {\n'''
start = s.find(start_marker)
end = s.find(end_marker)
if start < 0 or end < 0 or end <= start:
    raise SystemExit('bloco NewOffersDialog/PromotionChangeCard não encontrado')

replacement = r'''@Composable
private fun NewOffersDialog(
    changes: List<PromotionChange>,
    selectedStore: String,
    limitedBySafetyCap: Boolean,
    canOpenOffer: (PromotionChange) -> Boolean,
    onDismiss: () -> Unit,
    onOfferClick: (PromotionChange) -> Unit
) {
    val groupedChanges = remember(changes, selectedStore) {
        if (selectedStore != ALL_STORES_LABEL) {
            listOf(selectedStore to changes)
        } else {
            changes
                .groupBy { it.storeCode.ifBlank { UNKNOWN_STORE_LABEL } }
                .toList()
                .sortedBy { (storeCode, _) ->
                    if (storeCode == UNKNOWN_STORE_LABEL) storeCode.lowercase()
                    else StoreCatalog.nameFor(storeCode).lowercase()
                }
        }
    }
    val currentStoreLabel = if (selectedStore == ALL_STORES_LABEL) {
        "Todas as lojas"
    } else {
        StoreCatalog.nameFor(selectedStore)
    }
    val addedCount = changes.count { it.type == PromotionChangeType.ADDED }
    val changedCount = changes.count { it.type == PromotionChangeType.CHANGED }
    val removedCount = changes.count { it.type == PromotionChangeType.REMOVED }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val dialogShape = RoundedCornerShape(20.dp)
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.9f)
                .glassSoftShadow(dialogShape),
            shape = dialogShape,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ofertas novas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            if (selectedStore == ALL_STORES_LABEL) {
                                "Entraram, foram alteradas ou saíram hoje, organizadas por loja"
                            } else {
                                "Loja atual: $currentStoreLabel"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar alterações de ofertas")
                    }
                }

                if (limitedBySafetyCap) {
                    Text(
                        "O catálogo ultrapassou o limite de segurança do histórico. As alterações que conseguiram ser registradas continuam listadas abaixo.",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Text(
                    "${changes.size} mudança(s) hoje • $currentStoreLabel",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Entraram: $addedCount  •  Alteradas: $changedCount  •  Saíram: $removedCount",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                if (changes.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.LocalOffer, contentDescription = null, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (selectedStore == ALL_STORES_LABEL) {
                                "Nenhuma mudança de oferta registrada hoje."
                            } else {
                                "Nenhuma mudança de oferta registrada para $currentStoreLabel hoje."
                            }
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Quando uma oferta entrar, mudar ou sair, ela aparecerá aqui automaticamente.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        groupedChanges.forEach { (storeCode, storeChanges) ->
                            item(key = "change-store-$storeCode") {
                                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)) {
                                    Text(
                                        if (storeCode == UNKNOWN_STORE_LABEL) UNKNOWN_STORE_LABEL else StoreCatalog.nameFor(storeCode),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (storeCode != UNKNOWN_STORE_LABEL) {
                                        Text(
                                            "Loja ${storeCode.padStart(4, '0')} • ${storeChanges.size} mudança(s)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            items(storeChanges, key = { it.stableKey }) { change ->
                                PromotionChangeCard(
                                    change = change,
                                    canOpen = canOpenOffer(change),
                                    onOfferClick = onOfferClick
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PromotionChangeCard(
    change: PromotionChange,
    canOpen: Boolean,
    onOfferClick: (PromotionChange) -> Unit
) {
    val cardShape = RoundedCornerShape(14.dp)
    val oldValidity = listOfNotNull(
        change.oldValidFrom.toDisplayDate(),
        change.oldValidTo.toDisplayDate()
    ).joinToString(" até ")
    val newValidity = listOfNotNull(
        change.newValidFrom.toDisplayDate(),
        change.newValidTo.toDisplayDate()
    ).joinToString(" até ")
    val validity = when (change.type) {
        PromotionChangeType.ADDED -> newValidity
        PromotionChangeType.CHANGED -> {
            if (change.validityChanged && oldValidity.isNotBlank() && newValidity.isNotBlank() && oldValidity != newValidity) {
                "$oldValidity → $newValidity"
            } else {
                newValidity.ifBlank { oldValidity }
            }
        }
        PromotionChangeType.REMOVED -> oldValidity
    }
    val priceText = when (change.type) {
        PromotionChangeType.ADDED -> "Preço: ${change.newOfferPrice ?: "não informado"}"
        PromotionChangeType.CHANGED -> {
            val oldPrice = change.oldOfferPrice
            val newPrice = change.newOfferPrice
            if (!oldPrice.isNullOrBlank() && !newPrice.isNullOrBlank() && oldPrice != newPrice) {
                "Preço: $oldPrice → $newPrice"
            } else {
                "Preço: ${newPrice ?: oldPrice ?: "não informado"}"
            }
        }
        PromotionChangeType.REMOVED -> "Último preço: ${change.oldOfferPrice ?: "não informado"}"
    }
    val validToForImage = if (change.type == PromotionChangeType.REMOVED) change.oldValidTo else change.newValidTo
    val badgeContainerColor = when (change.type) {
        PromotionChangeType.ADDED -> MaterialTheme.colorScheme.primaryContainer
        PromotionChangeType.CHANGED -> MaterialTheme.colorScheme.tertiaryContainer
        PromotionChangeType.REMOVED -> MaterialTheme.colorScheme.errorContainer
    }
    val badgeContentColor = when (change.type) {
        PromotionChangeType.ADDED -> MaterialTheme.colorScheme.onPrimaryContainer
        PromotionChangeType.CHANGED -> MaterialTheme.colorScheme.onTertiaryContainer
        PromotionChangeType.REMOVED -> MaterialTheme.colorScheme.onErrorContainer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .glassSoftShadow(cardShape)
            .then(if (canOpen) Modifier.clickable { onOfferClick(change) } else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = cardShape
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Top) {
            if (!change.imageUrl.isNullOrBlank()) {
                ProductImage(
                    imageUrl = change.imageUrl,
                    contentDescription = change.productName,
                    modifier = Modifier.size(76.dp),
                    onClick = { if (canOpen) onOfferClick(change) },
                    validTo = validToForImage
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    color = badgeContainerColor,
                    contentColor = badgeContentColor,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        change.type.displayLabel(),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    change.productName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${StoreCatalog.labelFor(change.storeCode)} • código ${change.productCode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    priceText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (change.type == PromotionChangeType.REMOVED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                if (change.type == PromotionChangeType.CHANGED) {
                    val reason = when {
                        change.priceChanged && change.validityChanged -> "Alteração: preço/condição e validade"
                        change.priceChanged -> "Alteração: preço/condição"
                        change.validityChanged -> "Alteração: validade"
                        else -> "Alteração: dados da oferta"
                    }
                    Text(
                        reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (validity.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (change.type == PromotionChangeType.REMOVED) "Validade anterior: $validity" else "Validade: $validity",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    when {
                        canOpen -> "Toque para abrir esta oferta"
                        change.type == PromotionChangeType.REMOVED -> "Esta oferta saiu do catálogo"
                        else -> "Esta oferta não está disponível no catálogo atual"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (canOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

'''
s = s[:start] + replacement + s[end:]

old = '''private fun PromotionChangeType.displayLabel(): String = when (this) {\n    PromotionChangeType.ADDED -> "ADICIONADO"\n    PromotionChangeType.CHANGED -> "ALTERADO"\n    PromotionChangeType.REMOVED -> "EXCLUÍDO"\n}\n'''
new = '''private fun PromotionChangeType.displayLabel(): String = when (this) {\n    PromotionChangeType.ADDED -> "ENTROU EM OFERTA"\n    PromotionChangeType.CHANGED -> "OFERTA ALTERADA"\n    PromotionChangeType.REMOVED -> "SAIU DA OFERTA"\n}\n'''
if old not in s:
    raise SystemExit('labels das mudanças não encontrados')
s = s.replace(old, new, 1)

path.write_text(s, encoding='utf-8')
