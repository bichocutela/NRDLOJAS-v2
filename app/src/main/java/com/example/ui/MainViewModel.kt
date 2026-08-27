package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.Content
import com.example.api.GenerateContentRequest
import com.example.api.Part
import com.example.api.RetrofitClient
import com.example.data.Product
import com.example.data.AssistantSettings
import com.example.data.CategoryDefinition
import com.example.data.CatalogSnapshot
import com.example.data.ProductImportCommitResult
import com.example.data.ProductImportRow
import com.example.data.FirebaseService
import com.example.data.HomeSettings
import com.example.data.ProductRepository
import com.example.data.ProductStandards
import com.example.data.RemoteHomeSettings
import com.example.data.UserPreferences
import com.example.util.FcmTopicSubscription
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainViewModel(private val repository: ProductRepository, val userPreferences: UserPreferences) : ViewModel() {
    val authRepository = com.example.data.AuthRepository()


    private val _latestProduct = MutableStateFlow<Map<String, Any>?>(null)
    val latestProduct = _latestProduct.asStateFlow()
    
    private val _isSyncingTabs = MutableStateFlow(false)
    val isSyncingTabs: StateFlow<Boolean> = _isSyncingTabs.asStateFlow()
    private val _syncMessage = MutableSharedFlow<String>()
    val syncMessage = _syncMessage.asSharedFlow()
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()
    private val _catalogSnapshots = MutableStateFlow<List<CatalogSnapshot>>(emptyList())
    val catalogSnapshots: StateFlow<List<CatalogSnapshot>> = _catalogSnapshots.asStateFlow()
    private val _isLoadingCatalogHistory = MutableStateFlow(false)
    val isLoadingCatalogHistory: StateFlow<Boolean> = _isLoadingCatalogHistory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _chatInput = MutableStateFlow("")
    val chatInput = _chatInput.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages = _chatMessages.asStateFlow()


    private val _newProductsCount = MutableStateFlow(0)
    val newProductsCount: StateFlow<Int> = _newProductsCount.asStateFlow()

    private val remoteHomeSettings = FirebaseService.observeHomeSettings()
        .onStart { emit(RemoteHomeSettings()) }
        .catch { emit(RemoteHomeSettings()) }

    val homeSettings: StateFlow<HomeSettings> = combine(
        remoteHomeSettings,
        userPreferences.mostUsedLimit,
        userPreferences.carouselIntervalSeconds
    ) { remote, localMostUsedLimit, localCarouselIntervalSeconds ->
        HomeSettings(
            showCategories = remote.showCategories ?: true,
            showMostUsed = remote.showMostUsed ?: true,
            showHistory = remote.showHistory ?: true,
            showFavorites = remote.showFavorites ?: true,
            mostUsedLimit = (remote.mostUsedLimit ?: localMostUsedLimit).coerceIn(1, 50),
            carouselIntervalSeconds = (remote.carouselIntervalSeconds ?: localCarouselIntervalSeconds).coerceIn(3, 30)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeSettings())

    private val remoteCategories = FirebaseService.observeCategories()
        .onStart { emit(CategoryDefinition.defaults) }
        .catch { emit(CategoryDefinition.defaults) }

    private val remoteAssistantSettings = FirebaseService.observeAssistantSettings()
        .onStart { emit(AssistantSettings()) }
        .catch { emit(AssistantSettings()) }

    val assistantSettings: StateFlow<AssistantSettings> = remoteAssistantSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AssistantSettings())

    val categoryDefinitions: StateFlow<List<CategoryDefinition>> = remoteCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoryDefinition.defaults)

    val activeCategoryNames: StateFlow<List<String>> = categoryDefinitions
        .map { definitions ->
            definitions
                .filter { it.isActive }
                .sortedWith(compareBy<CategoryDefinition> { it.displayOrder }.thenBy { it.name })
                .map { it.name }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoryDefinition.defaults.map { it.name })

    val favorites = repository.favorites.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val mostUsed = homeSettings
        .map { it.mostUsedLimit }
        .distinctUntilChanged()
        .flatMapLatest { repository.mostUsed(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history = repository.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val notificationHistory = userPreferences.notificationHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allProducts = repository.allProducts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val productsCountByCategory = repository.productsCountByCategory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val latestProductLocal = repository.latestProductLocal.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    init {
        viewModelScope.launch {
            val installationId = userPreferences.getOrCreateInstallationId()
            val notificationsEnabled = userPreferences.notificationsEnabled.first()
            FcmTopicSubscription.reconcileSuggestionTopic(notificationsEnabled, installationId)
        }
        viewModelScope.launch {
            FirebaseService.observeDynamicTabs().collect { remoteTabs ->
                val localTabs = repository.getAllTabs().first()
                remoteTabs.forEach { remoteTab ->
                    val localTab = localTabs.find { it.id == remoteTab.id }
                    if (localTab != remoteTab) {
                        repository.insertTab(remoteTab)
                    }
                }
                val remoteIds = remoteTabs.map { it.id }.toSet()
                val tabsToDelete = localTabs.filter { it.id !in remoteIds }
                if (tabsToDelete.isNotEmpty() && !_isSyncingTabs.value) {
                    tabsToDelete.forEach { repository.deleteTab(it) }
                }
            }
        }
        viewModelScope.launch {
            FirebaseService.observeBannerUrl().collect { url ->
                if (url != null) {
                    userPreferences.setBannerImageUri(url)
                }
            }
        }
        viewModelScope.launch {
            FirebaseService.observeLatestProduct().collect {
                _latestProduct.value = it
            }
        }
        viewModelScope.launch {
            FirebaseService.observeProducts().collect { remoteProducts ->
                
                    val localProducts = repository.getAllProductsSync()
                    val remoteIds = remoteProducts.map { it.code }.toSet()
                    val toDelete = localProducts.filter { it.code !in remoteIds }
                    
                    if (toDelete.isNotEmpty() && !_isSyncing.value) {
                        repository.deleteProducts(toDelete)
                    }
                    
                    val missingOrUpdated = remoteProducts.mapNotNull { remote ->
                        val local = localProducts.find { it.code == remote.code }
                        if (local == null) {
                            remote
                        } else if (local.name != remote.name || local.imageUrl != remote.imageUrl || local.category != remote.category || local.unit != remote.unit) {
                            remote.copy(id = local.id, searchCount = local.searchCount, lastSearchedAt = local.lastSearchedAt, isFavorite = local.isFavorite)
                        } else {
                            null
                        }
                    }
                    if (missingOrUpdated.isNotEmpty() && !_isSyncing.value) {
                        repository.insertProducts(missingOrUpdated)
            }
            }
        }
        viewModelScope.launch {
            repository.populateInitialDataIfNeeded()
            syncProductsFromFirebase()
        }
    }

    
    val searchResults: StateFlow<List<Product>> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(emptyList())
            } else {
                repository.searchProducts(query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun onProductSearched(product: Product) {
        viewModelScope.launch {
            repository.registerSearch(product)
        }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }

    fun markNotificationRead(id: Long) {
        viewModelScope.launch { userPreferences.markNotificationRead(id) }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch { userPreferences.markAllNotificationsRead() }
    }

    fun toggleFavorite(product: Product) {
        viewModelScope.launch {
            repository.toggleFavorite(product)
        }
    }

    fun updateChatInput(input: String) {
        _chatInput.value = input
    }


    fun sendChatMessage() {
        val query = _chatInput.value.trim()
        if (query.isBlank()) return

        _chatInput.value = ""
        val newMessages = _chatMessages.value.toMutableList()
        newMessages.add(ChatMessage(query, true))
        _chatMessages.value = newMessages

        viewModelScope.launch {
            try {
                val settings = assistantSettings.value
                if (!settings.enabled) {
                    val disabledMessages = _chatMessages.value.toMutableList()
                    disabledMessages.add(ChatMessage("O Assistente IA está desativado pelo Mestre.", false))
                    _chatMessages.value = disabledMessages
                    return@launch
                }

                val relatedProducts = repository.searchProductsSync(query)
                    .take(settings.maxContextProducts.coerceIn(5, 50))
                val contextString = relatedProducts.joinToString("\n") {
                    "${it.name} (${it.category}) - Código: ${it.code} - Vendido por: ${it.unit}"
                }.ifBlank { "Nenhum produto relacionado foi encontrado no catálogo." }

                val scopeInstruction = if (settings.catalogOnly) {
                    "Responda somente com base no catálogo abaixo. Não invente códigos e diga quando não encontrar."
                } else {
                    "Priorize o catálogo abaixo. Quando a pergunta não for sobre produtos, responda de forma breve e deixe claro quando não houver informação no catálogo."
                }
                val systemPrompt = """
                    Você é um assistente de um supermercado para ajudar operadores de caixa e repositores a encontrar códigos de produtos.
                    Sempre responda de forma amigável, direta e curta.
                    $scopeInstruction

                    Produtos mais relacionados à pergunta:
                    $contextString
                """.trimIndent()

                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = query)))),
                    systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
                )
                
                val response = RetrofitClient.service.generateContent(BuildConfig.GEMINI_API_KEY, request)
                val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "Desculpe, não entendi."
                
                val updatedMessages = _chatMessages.value.toMutableList()
                updatedMessages.add(ChatMessage(responseText, false))
                _chatMessages.value = updatedMessages

            } catch (e: Throwable) {
                val updatedMessages = _chatMessages.value.toMutableList()
                updatedMessages.add(ChatMessage("Erro ao conectar com a IA: ${e.message}", false))
                _chatMessages.value = updatedMessages
            }
        }
    }

    fun getProductsByCategory(category: String) = repository.getProductsByCategory(category)
    fun searchProducts(query: String) = repository.searchProducts(query)
    fun searchProductsByCategory(category: String, query: String) = repository.searchProductsByCategory(category, query)

    suspend fun addCategory(name: String): Boolean {
        val cleanName = name.trim().replace(Regex("\\s+"), " ")
        if (cleanName.isBlank()) {
            _syncMessage.emit("Informe um nome para a categoria.")
            return false
        }
        if (categoryDefinitions.value.any { it.name.equals(cleanName, ignoreCase = true) }) {
            _syncMessage.emit("Essa categoria já existe.")
            return false
        }
        val existingIds = categoryDefinitions.value.map { it.id }.toSet()
        val baseId = ProductStandards.categoryId(cleanName)
        var id = baseId
        var suffix = 2
        while (id in existingIds) {
            id = "$baseId-$suffix"
            suffix += 1
        }
        val updated = categoryDefinitions.value + CategoryDefinition(
            id = id,
            name = cleanName,
            displayOrder = categoryDefinitions.value.size,
            isActive = true
        )
        return saveCategoryDefinitions(updated, "Categoria adicionada para todos os usuários.")
    }

    suspend fun renameCategory(category: CategoryDefinition, newName: String): Boolean {
        val cleanName = newName.trim().replace(Regex("\\s+"), " ")
        if (cleanName.isBlank()) {
            _syncMessage.emit("Informe um nome para a categoria.")
            return false
        }
        if (categoryDefinitions.value.any { it.id != category.id && it.name.equals(cleanName, ignoreCase = true) }) {
            _syncMessage.emit("Essa categoria já existe.")
            return false
        }
        if (category.name == cleanName) return true

        val productsToRename = repository.getProductsByCategory(category.name).first()
        val updated = categoryDefinitions.value.map {
            if (it.id == category.id) it.copy(name = cleanName) else it
        }
        val saved = FirebaseService.saveCategories(updated)
        if (!saved) {
            _syncMessage.emit("Não foi possível salvar o novo nome da categoria.")
            return false
        }
        val renamed = FirebaseService.renameProductsCategory(category.name, cleanName)
        if (!renamed) {
            FirebaseService.saveCategories(categoryDefinitions.value)
            _syncMessage.emit("Não foi possível atualizar os produtos dessa categoria.")
            return false
        }
        productsToRename.forEach { product ->
            repository.updateProduct(product.copy(category = cleanName))
        }
        _syncMessage.emit("Categoria renomeada para todos os usuários.")
        return true
    }

    suspend fun setCategoryActive(category: CategoryDefinition, isActive: Boolean): Boolean {
        if (!isActive && categoryDefinitions.value.count { it.isActive } <= 1) {
            _syncMessage.emit("Mantenha pelo menos uma categoria ativa.")
            return false
        }
        val updated = categoryDefinitions.value.map {
            if (it.id == category.id) it.copy(isActive = isActive) else it
        }
        return saveCategoryDefinitions(
            updated,
            if (isActive) "Categoria ativada para todos os usuários." else "Categoria ocultada da seleção."
        )
    }

    suspend fun moveCategory(category: CategoryDefinition, direction: Int): Boolean {
        val ordered = categoryDefinitions.value.sortedWith(compareBy<CategoryDefinition> { it.displayOrder }.thenBy { it.name }).toMutableList()
        val index = ordered.indexOfFirst { it.id == category.id }
        val targetIndex = index + direction
        if (index < 0 || targetIndex !in ordered.indices) return false
        val moved = ordered.removeAt(index)
        ordered.add(targetIndex, moved)
        return saveCategoryDefinitions(ordered, "Ordem das categorias atualizada para todos.")
    }

    private suspend fun saveCategoryDefinitions(categories: List<CategoryDefinition>, successMessage: String): Boolean {
        val ordered = categories.mapIndexed { index, category -> category.copy(displayOrder = index) }
        if (ordered.none { it.isActive }) {
            _syncMessage.emit("Mantenha pelo menos uma categoria ativa.")
            return false
        }
        val saved = FirebaseService.saveCategories(ordered)
        _syncMessage.emit(if (saved) successMessage else "Não foi possível salvar as categorias.")
        return saved
    }

    suspend fun updateSelectedProductsCategory(products: List<Product>, category: String): Boolean {
        val cleanCategory = category.trim()
        if (products.isEmpty() || cleanCategory !in activeCategoryNames.value) {
            _syncMessage.emit("Selecione produtos e uma categoria ativa.")
            return false
        }
        val saved = FirebaseService.updateProductsCategory(products.map { it.code }, cleanCategory)
        if (!saved) {
            _syncMessage.emit("Não foi possível alterar os produtos selecionados.")
            return false
        }
        repository.insertProducts(products.map { it.copy(category = cleanCategory) })
        _syncMessage.emit("${products.size} produto(s) atualizado(s) com sucesso.")
        return true
    }

    suspend fun deleteSelectedProducts(products: List<Product>): Boolean {
        if (products.isEmpty()) {
            _syncMessage.emit("Selecione pelo menos um produto.")
            return false
        }
        val deleted = FirebaseService.deleteProducts(products.map { it.code })
        if (!deleted) {
            _syncMessage.emit("Não foi possível excluir os produtos selecionados.")
            return false
        }
        repository.deleteProducts(products)
        _syncMessage.emit("${products.size} produto(s) excluído(s) com sucesso.")
        return true
    }

    suspend fun importProducts(rows: List<ProductImportRow>): ProductImportCommitResult {
        if (rows.isEmpty()) {
            return ProductImportCommitResult(0, 0, listOf("Nenhuma linha válida para importar."))
        }
        val activeCategories = activeCategoryNames.value
        val activeCategoryByKey = activeCategories.associateBy { ProductStandards.searchNameFrom(it) }
        val existingCodes = repository.getAllProductsSync().map { it.code.trim() }.toSet()
        val seenCodes = mutableSetOf<String>()
        val errors = mutableListOf<String>()
        val candidates = rows.mapNotNull { row ->
            val code = row.code.trim()
            val category = activeCategoryByKey[ProductStandards.searchNameFrom(row.category.trim())]
            when {
                code in existingCodes -> {
                    errors += "Linha ${row.lineNumber}: código $code já existe e foi ignorado."
                    null
                }
                !seenCodes.add(code) -> {
                    errors += "Linha ${row.lineNumber}: código $code repetido na própria planilha."
                    null
                }
                category == null -> {
                    errors += "Linha ${row.lineNumber}: categoria '${row.category}' não está ativa."
                    null
                }
                else -> Product(
                    code = code,
                    name = ProductStandards.normalizeProductName(row.name.trim()),
                    searchName = ProductStandards.searchNameFrom(row.name.trim()),
                    category = category,
                    unit = row.unit.trim().ifBlank { "UN" },
                    imageUrl = row.imageUrl?.trim()?.takeIf { it.isNotBlank() }
                )
            }
        }
        if (candidates.isEmpty()) {
            return ProductImportCommitResult(0, rows.size, errors.ifEmpty { listOf("Nenhum produto novo foi encontrado.") })
        }
        val saved = FirebaseService.saveProductsBatch(candidates)
        if (!saved) {
            return ProductImportCommitResult(0, rows.size, errors + "Não foi possível publicar os produtos na nuvem.")
        }
        repository.insertProducts(candidates)
        _newProductsCount.value += candidates.size
        _syncMessage.emit("${candidates.size} produto(s) importado(s) com sucesso.")
        return ProductImportCommitResult(candidates.size, rows.size - candidates.size, errors)
    }

    suspend fun checkDuplicateCode(code: String, currentId: Int? = null): Product? {
        val normalizedCode = code.trim()
        val existingProduct = repository.getProductByCodeSync(normalizedCode)
        if (existingProduct != null && existingProduct.id != currentId) {
            return existingProduct
        }
        return null
    }

    suspend fun updateProductSuspend(oldProduct: Product, newProduct: Product): Boolean {
        val normalizedCode = newProduct.code.trim()
        val requestedCategory = newProduct.category.trim()
        if (requestedCategory != oldProduct.category && requestedCategory !in activeCategoryNames.value) {
            _syncMessage.emit("Selecione uma categoria ativa para alterar a categoria do produto.")
            return false
        }
        val normalizedName = ProductStandards.normalizeProductName(newProduct.name)
        val finalProductCategory = if (requestedCategory == oldProduct.category) oldProduct.category else requestedCategory
        var finalProduct = newProduct.copy(
            id = oldProduct.id,
            code = normalizedCode,
            name = normalizedName,
            searchName = ProductStandards.searchNameFrom(normalizedName),
            category = finalProductCategory
        )
        if (oldProduct.code != normalizedCode) {
            android.util.Log.d("ProductSync", "Verificando se o novo código já existe: $normalizedCode")
            val existingProduct = repository.getProductByCodeSync(normalizedCode)
            if (existingProduct != null && existingProduct.id != oldProduct.id) {
                android.util.Log.e("ProductSync", "Código já existe: $normalizedCode")
                _syncMessage.emit("Código já cadastrado\n\nO código $normalizedCode já pertence ao produto:\n${existingProduct.name}")
                return false
            }
        }

        if (newProduct.imageUrl?.startsWith("content://") == true) {
            android.util.Log.d("ProductSync", "Iniciando upload de imagem para alteração: ${newProduct.code}")
            val uri = android.net.Uri.parse(newProduct.imageUrl)
            val url = FirebaseService.uploadImageToStorage(uri, "products/${newProduct.code}_${System.currentTimeMillis()}.jpg")
            if (url != null) {
                android.util.Log.d("ProductSync", "Upload sucesso: $url")
                finalProduct = finalProduct.copy(imageUrl = url)
            } else {
                android.util.Log.e("ProductSync", "Upload falhou para: ${newProduct.code}")
                _syncMessage.emit("Não foi possível enviar a foto. Tente novamente.")
                return false
            }
        }

        if (FirebaseService.isFirebaseConfigured()) {
            android.util.Log.d("ProductSync", "Iniciando save Firestore para edição: ${finalProduct.code}")
            val saveSuccess = FirebaseService.saveProduct(finalProduct)
            if (saveSuccess) {
                if (oldProduct.code != finalProduct.code) {
                    android.util.Log.d("ProductSync", "Código alterado de ${oldProduct.code} para ${finalProduct.code}. Excluindo antigo.")
                    val deleteSuccess = FirebaseService.deleteProduct(oldProduct.code)
                    if (!deleteSuccess) {
                        android.util.Log.e("ProductSync", "Erro ao excluir documento antigo: ${oldProduct.code}. Iniciando rollback.")
                        val rollbackSuccess = FirebaseService.deleteProduct(finalProduct.code)
                        if (rollbackSuccess) {
                            android.util.Log.e("ProductSync", "Rollback com sucesso para: ${finalProduct.code}")
                            _syncMessage.emit("Não foi possível alterar o código. Tente novamente.")
                        } else {
                            android.util.Log.e("ProductSync", "Erro crítico: rollback falhou para: ${finalProduct.code}")
                            _syncMessage.emit("Erro ao concluir a alteração do código. Tente novamente.")
                        }
                        return false
                    }
                }
                
                android.util.Log.d("ProductSync", "Atualizando Room: ${finalProduct.code}")
                repository.updateProduct(finalProduct)
                
                if (oldProduct.code != finalProduct.code) {
                    android.util.Log.d("ProductSync", "Publicando evento: CODE_CHANGED")
                    FirebaseService.publishProductEvent("CODE_CHANGED", finalProduct.name, null, finalProduct.code)
                }
                _syncMessage.emit("Produto atualizado na nuvem!")
                return true
            } else {
                android.util.Log.e("ProductSync", "Save Firestore falhou para edição: ${finalProduct.code}")
                _syncMessage.emit("Erro ao atualizar produto na nuvem.")
                return false
            }
        } else {
            _syncMessage.emit("Nuvem não configurada. Não foi possível atualizar.")
            return false
        }
    }
    suspend fun removeProductImage(product: Product): Boolean {
        val updatedProduct = product.copy(imageUrl = null)
        if (FirebaseService.isFirebaseConfigured()) {
            val success = FirebaseService.saveProduct(updatedProduct)
            if (success) {
                repository.updateProduct(updatedProduct)
                FirebaseService.publishProductEvent("INFO_CHANGED", updatedProduct.name, product.name, updatedProduct.code)
                _syncMessage.emit("Foto removida com sucesso.")
                return true
            } else {
                _syncMessage.emit("Não foi possível remover a foto.")
                return false
            }
        } else {
            _syncMessage.emit("Não foi possível remover a foto. Verifique a conexão e tente novamente.")
            return false
        }
    }

    suspend fun deleteProductSuspend(product: Product): Boolean {
        if (FirebaseService.isFirebaseConfigured()) {
            val success = FirebaseService.deleteProduct(product.code)
            if (success) {
                repository.deleteProduct(product)
                FirebaseService.publishProductEvent("PRODUCT_DELETED", product.name, null, product.code)
                _syncMessage.emit("Produto excluído com sucesso.")
                return true
            } else {
                _syncMessage.emit("Não foi possível excluir o produto. Tente novamente.")
                return false
            }
        } else {
            _syncMessage.emit("Não foi possível excluir o produto. Verifique a conexão e tente novamente.")
            return false
        }
    }

    fun updateProduct(oldProduct: Product, newProduct: Product) {
        viewModelScope.launch {
            updateProductSuspend(oldProduct, newProduct)
        }
    }

        suspend fun addProductSuspend(name: String, code: String, category: String, unit: String, imageUrl: String? = null): Boolean {
        val normalizedCode = code.trim()
        val normalizedCategory = category.trim()
        if (normalizedCategory !in activeCategoryNames.value) {
            _syncMessage.emit("Selecione uma categoria ativa para adicionar o produto.")
            return false
        }
        val normalizedName = ProductStandards.normalizeProductName(name)
        val existingProduct = repository.getProductByCodeSync(normalizedCode)
        if (existingProduct != null) {
            _syncMessage.emit("Código já cadastrado\n\nJá existe um produto utilizando o código $normalizedCode:\n${existingProduct.name}")
            return false
        }
        var finalImageUrl = imageUrl
        if (imageUrl?.startsWith("content://") == true) {
            android.util.Log.d("ProductSync", "Iniciando upload de imagem para $code")
            val uri = android.net.Uri.parse(imageUrl)
            val url = FirebaseService.uploadImageToStorage(uri, "products/${code}_${System.currentTimeMillis()}.jpg")
            if (url != null) {
                android.util.Log.d("ProductSync", "Upload sucesso: $url")
                finalImageUrl = url
            } else {
                android.util.Log.e("ProductSync", "Upload falhou para $code")
                _syncMessage.emit("Não foi possível enviar a foto. Tente novamente.")
                return false
            }
        }
        val product = Product(
            code = normalizedCode,
            name = normalizedName,
            searchName = ProductStandards.searchNameFrom(normalizedName),
            category = normalizedCategory,
            unit = unit,
            imageUrl = finalImageUrl
        )
        if (FirebaseService.isFirebaseConfigured()) {
            android.util.Log.d("ProductSync", "Iniciando save Firestore para novo produto: $code")
            val success = FirebaseService.saveProduct(product)
            if (success) {
                android.util.Log.d("ProductSync", "Save Firestore sucesso, atualizando Room: $code")
                repository.insertProduct(product)
                FirebaseService.publishProductEvent(
                    "NEW_PRODUCT",
                    product.name,
                    null,
                    product.code
                )
                _syncMessage.emit("Produto adicionado na nuvem!")
                _newProductsCount.value += 1
                return true
            } else {
                android.util.Log.e("ProductSync", "Save Firestore falhou para: $code")
                _syncMessage.emit("Erro ao salvar produto na nuvem.")
                return false
            }
        } else {
            _syncMessage.emit("Não foi possível publicar o produto. Verifique a conexão e tente novamente.")
            return false
        }
    }
    
    fun addProduct(name: String, code: String, category: String, unit: String, imageUrl: String? = null) {
        viewModelScope.launch {
            addProductSuspend(name, code, category, unit, imageUrl)
        }
    }
    
    fun refreshCatalogHistory() {
        viewModelScope.launch {
            _isLoadingCatalogHistory.value = true
            try {
                _catalogSnapshots.value = FirebaseService.getCatalogSnapshots()
            } finally {
                _isLoadingCatalogHistory.value = false
            }
        }
    }

    fun createCatalogSnapshot() {
        viewModelScope.launch {
            _isLoadingCatalogHistory.value = true
            try {
                val snapshot = FirebaseService.createCatalogSnapshot()
                if (snapshot != null) {
                    _catalogSnapshots.value = listOf(snapshot) + _catalogSnapshots.value
                    _syncMessage.emit("Snapshot criado com ${snapshot.productCount} produto(s).")
                } else {
                    _syncMessage.emit("Não foi possível criar o snapshot do catálogo.")
                }
            } finally {
                _isLoadingCatalogHistory.value = false
            }
        }
    }

    fun restoreCatalogSnapshot(snapshotId: String) {
        viewModelScope.launch {
            _isLoadingCatalogHistory.value = true
            try {
                val result = FirebaseService.restoreCatalogSnapshot(snapshotId)
                result.restoredProducts?.let { repository.insertProducts(it) }
                if (result.success) {
                    val restoredCodes = result.restoredProducts.orEmpty().map { it.code }.toSet()
                    val currentProducts = repository.getAllProductsSync()
                    val staleProducts = currentProducts.filter { it.code !in restoredCodes }
                    if (staleProducts.isNotEmpty()) {
                        repository.deleteProducts(staleProducts)
                    }
                }
                _syncMessage.emit(result.message)
                if (result.success) {
                    _catalogSnapshots.value = FirebaseService.getCatalogSnapshots()
                }
            } finally {
                _isLoadingCatalogHistory.value = false
            }
        }
    }

    fun syncProductsFromFirebase() {
        viewModelScope.launch {
            _isSyncing.value = true
            if (!FirebaseService.isFirebaseConfigured()) {
                val msg = FirebaseService.lastError ?: "Configuração ausente."
                _syncMessage.emit("Nuvem não configurada: $msg")
                _isSyncing.value = false
                return@launch
            }
            try {
                val localProducts = repository.getAllProductsSync()
                val remoteProducts = FirebaseService.getAllProducts()
                if (remoteProducts.isEmpty() && localProducts.isNotEmpty()) {
                    _syncMessage.emit("Sincronização interrompida: a nuvem retornou um catálogo vazio. Os dados locais foram preservados.")
                    return@launch
                }
                repository.cleanDuplicates()
                val recognizedCategories = (categoryDefinitions.value.map { it.name } + ProductStandards.officialCategories).toSet()
                val legacyProducts = remoteProducts.filter { it.category !in recognizedCategories }
                val legacyCounts = legacyProducts.groupingBy { it.category.ifBlank { "(vazia)" } }.eachCount()
                if (legacyProducts.isNotEmpty()) {
                    android.util.Log.i("ProductMigration", "Categorias antes: $legacyCounts; total=${remoteProducts.size}")
                    legacyProducts.forEach { product ->
                        FirebaseService.saveProduct(product.copy(category = "Mercearia"))
                    }
                }
                val migratedProducts = remoteProducts.map { product ->
                    if (product.category !in recognizedCategories) product.copy(category = "Mercearia") else product
                }
                android.util.Log.i(
                    "ProductMigration",
                    "Categorias depois: ${migratedProducts.groupingBy { it.category }.eachCount()}; total=${migratedProducts.size}; migrados=${legacyProducts.size}"
                )
                
                    val remoteIds = migratedProducts.map { it.code }.toSet()
                    val toDelete = localProducts.filter { it.code !in remoteIds }
                    
                    if (toDelete.isNotEmpty()) {
                        repository.deleteProducts(toDelete)
                    }
                    
                    val missingOrUpdated = migratedProducts.mapNotNull { remote ->
                        val local = localProducts.find { it.code == remote.code }
                        if (local == null) {
                            remote
                        } else if (local.name != remote.name || local.imageUrl != remote.imageUrl || local.category != remote.category || local.unit != remote.unit) {
                            remote.copy(id = local.id, searchCount = local.searchCount, lastSearchedAt = local.lastSearchedAt, isFavorite = local.isFavorite)
                        } else {
                            null
                        }
                    }
                    if (missingOrUpdated.isNotEmpty()) {
                        repository.insertProducts(missingOrUpdated)
                    }
            } catch (e: Exception) {
                _syncMessage.emit("Não foi possível sincronizar o catálogo. Os dados locais foram preservados.")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun clearNewProductsCount() {
        _newProductsCount.value = 0
    }
    val dynamicTabs: kotlinx.coroutines.flow.StateFlow<List<com.example.data.DynamicTab>> = repository.getAllTabs()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    fun insertTab(tab: com.example.data.DynamicTab) = viewModelScope.launch {
        if (_isSyncingTabs.value) return@launch
        _isSyncingTabs.value = true
        try {
            repository.insertTab(tab.copy(displayOrder = repository.getAllTabs().first().size))
            val saved = FirebaseService.syncAllDynamicTabs(repository.getAllTabs().first())
            _syncMessage.emit(if (saved) "Aba criada para todos os usuários." else "A aba foi criada localmente, mas não foi publicada na nuvem.")
        } finally {
            _isSyncingTabs.value = false
        }
    }

    fun updateTab(tab: com.example.data.DynamicTab) = viewModelScope.launch {
        if (_isSyncingTabs.value) return@launch
        _isSyncingTabs.value = true
        try {
            repository.updateTab(tab)
            val saved = FirebaseService.syncAllDynamicTabs(repository.getAllTabs().first())
            _syncMessage.emit(if (saved) "Aba atualizada para todos os usuários." else "A aba foi atualizada localmente, mas não foi publicada na nuvem.")
        } finally {
            _isSyncingTabs.value = false
        }
    }

    fun moveTab(tab: com.example.data.DynamicTab, direction: Int) = viewModelScope.launch {
        if (_isSyncingTabs.value) return@launch
        val current = repository.getAllTabs().first()
            .sortedWith(compareBy<com.example.data.DynamicTab> { it.displayOrder }.thenBy { it.id })
        val index = current.indexOfFirst { it.id == tab.id }
        val targetIndex = index + direction
        if (index < 0 || targetIndex !in current.indices) return@launch

        _isSyncingTabs.value = true
        try {
            val reordered = current.toMutableList().apply {
                val moved = removeAt(index)
                add(targetIndex, moved)
            }.mapIndexed { order, item -> item.copy(displayOrder = order) }
            reordered.forEach { repository.updateTab(it) }
            val saved = FirebaseService.syncAllDynamicTabs(reordered)
            _syncMessage.emit(if (saved) "Ordem das abas atualizada para todos." else "A ordem foi atualizada localmente, mas não foi publicada na nuvem.")
        } finally {
            _isSyncingTabs.value = false
        }
    }

    fun deleteProduct(product: Product) {
        viewModelScope.launch {
            repository.deleteProduct(product)
            if (FirebaseService.isFirebaseConfigured()) {
                FirebaseService.deleteProduct(product.code)
                _syncMessage.emit("Produto excluído na nuvem!")
            }
        }
    }

    fun deleteTab(tab: com.example.data.DynamicTab) = viewModelScope.launch {
        if (_isSyncingTabs.value) return@launch
        _isSyncingTabs.value = true
        try {
            val deleted = FirebaseService.deleteDynamicTab(tab)
            if (deleted) {
                repository.deleteTab(tab)
            }
            _syncMessage.emit(if (deleted) "Aba excluída para todos os usuários." else "Não foi possível excluir a aba na nuvem; os dados foram preservados.")
        } finally {
            _isSyncingTabs.value = false
        }
    }


    fun setOnboardingShown() {
        viewModelScope.launch {
            userPreferences.setOnboardingShown(true)
        }
    }
}

data class ChatMessage(val text: String, val isUser: Boolean)
