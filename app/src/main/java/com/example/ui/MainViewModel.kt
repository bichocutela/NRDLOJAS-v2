package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.Content
import com.example.api.GenerateContentRequest
import com.example.api.Part
import com.example.api.RetrofitClient
import com.example.data.Product
import com.example.data.FirebaseService
import com.example.data.ProductRepository
import com.example.data.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

    private val _aiProductDetails = MutableStateFlow<String?>(null)
    val aiProductDetails = _aiProductDetails.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading = _isAiLoading.asStateFlow()


    private val _newProductsCount = MutableStateFlow(0)
    val newProductsCount: StateFlow<Int> = _newProductsCount.asStateFlow()

    val favorites = repository.favorites.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val mostUsed = repository.mostUsed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history = repository.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allProducts = repository.allProducts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val productsCountByCategory = repository.productsCountByCategory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val latestProductLocal = repository.latestProductLocal.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    init {
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

    fun toggleFavorite(product: Product) {
        viewModelScope.launch {
            repository.toggleFavorite(product)
        }
    }

    fun updateChatInput(input: String) {
        _chatInput.value = input
    }


    fun consultProductInfoAi(product: Product) {
        viewModelScope.launch {
            _isAiLoading.value = true
            _aiProductDetails.value = null
            try {
                val prompt = "Forneça informações detalhadas sobre o produto de supermercado: ${product.name} (Categoria: ${product.category}). Inclua dicas de uso, armazenamento ou curiosidades. Seja breve e informativo."
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt))))
                )
                val response = RetrofitClient.service.generateContent(BuildConfig.GEMINI_API_KEY, request)
                _aiProductDetails.value = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "Informações não disponíveis."
            } catch (e: Throwable) {
                _aiProductDetails.value = "Erro ao buscar informações: ${e.message}"
            } finally {
                _isAiLoading.value = false
            }
        }
    }
    
    fun clearAiProductDetails() {
        _aiProductDetails.value = null
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

    suspend fun checkDuplicateCode(code: String, currentId: Int? = null): Product? {
        val normalizedCode = code.trim()
        val existingProduct = repository.getProductByCodeSync(normalizedCode)
        if (existingProduct != null && existingProduct.id != currentId) {
            return existingProduct
        }
        return null
    }

    suspend fun updateProductSuspend(oldProduct: Product, newProduct: Product): Boolean {
        var finalProduct = newProduct
        finalProduct = finalProduct.copy(id = oldProduct.id)
        
        val normalizedCode = newProduct.code.trim()
        finalProduct = finalProduct.copy(code = normalizedCode)
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
            name = name,
            searchName = name.lowercase().replace(Regex("[áàâã]"), "a").replace(Regex("[éèê]"), "e").replace(Regex("[íìî]"), "i").replace(Regex("[óòôõ]"), "o").replace(Regex("[úùû]"), "u").replace(Regex("[ç]"), "c"),
            category = category,
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
                
                    val localProducts = repository.getAllProductsSync()
                    val remoteIds = remoteProducts.map { it.code }.toSet()
                    val toDelete = localProducts.filter { it.code !in remoteIds }
                    
                    if (toDelete.isNotEmpty()) {
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
