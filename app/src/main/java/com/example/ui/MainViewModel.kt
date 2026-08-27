package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.Content
import com.example.api.GenerateContentRequest
import com.example.api.Part
import com.example.api.RetrofitClient
import com.example.data.Product
import com.example.data.CategoryDefinition
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
    
    private var isSyncingTabs = false
    private val _syncMessage = MutableSharedFlow<String>()
    val syncMessage = _syncMessage.asSharedFlow()
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

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
                if (tabsToDelete.isNotEmpty() && !isSyncingTabs) {
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
        val query = _chatInput.value
        if (query.isBlank()) return

        _chatInput.value = ""
        val newMessages = _chatMessages.value.toMutableList()
        newMessages.add(ChatMessage(query, true))
        _chatMessages.value = newMessages

        viewModelScope.launch {
            try {
                val allProducts = repository.searchProductsSync("")
                
                val contextString = allProducts.joinToString("\n") { 
                    "${it.name} (${it.category}) - Código: ${it.code} - Vendido por: ${it.unit}"
                }

                val systemPrompt = """
                    Você é um assistente de um supermercado para ajudar operadores de caixa e repositores a encontrar códigos de produtos.
                    Sempre responda de forma amigável, direta e curta.
                    Quando o usuário perguntar sobre um produto, forneça o código dele usando a lista abaixo.
                    Se o produto não estiver na lista, diga que não encontrou.
                    
                    Lista de produtos:
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
                repository.cleanDuplicates()
                val remoteProducts = FirebaseService.getAllProducts()
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
                
                    val localProducts = repository.getAllProductsSync()
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
                // Ignore
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
        isSyncingTabs = true
        val existingIds = repository.getAllTabs().first().map { it.id }.toSet()
        repository.insertTab(tab)
        val tabs = repository.getAllTabs().first { list -> list.any { it.id !in existingIds } }
        FirebaseService.syncAllDynamicTabs(tabs)
        isSyncingTabs = false
    }

    fun updateTab(tab: com.example.data.DynamicTab) = viewModelScope.launch {
        isSyncingTabs = true
        repository.updateTab(tab)
        val tabs = repository.getAllTabs().first { list -> list.any { it == tab } }
        FirebaseService.syncAllDynamicTabs(tabs)
        isSyncingTabs = false
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
        isSyncingTabs = true
        repository.deleteTab(tab)
        FirebaseService.deleteDynamicTab(tab)
        isSyncingTabs = false
    }


    fun setOnboardingShown() {
        viewModelScope.launch {
            userPreferences.setOnboardingShown(true)
        }
    }
}

data class ChatMessage(val text: String, val isUser: Boolean)
