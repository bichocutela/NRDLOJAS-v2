import sys

with open("app/src/main/java/com/example/ui/MainViewModel.kt", "r") as f:
    content = f.read()

target = "    fun getProductsByCategory(category: String) = repository.getProductsByCategory(category)"

replacement = """    fun getProductsByCategory(category: String) = repository.getProductsByCategory(category)

    suspend fun checkDuplicateCode(code: String, currentId: Int? = null): Product? {
        val normalizedCode = code.trim()
        val existingProduct = repository.getProductByCodeSync(normalizedCode)
        if (existingProduct != null && existingProduct.id != currentId) {
            return existingProduct
        }
        return null
    }"""

content = content.replace(target, replacement)

# We also should apply code.trim() inside addProductSuspend and updateProductSuspend
# updateProductSuspend
target_update = "if (oldProduct.code != newProduct.code) {"
replacement_update = """val normalizedCode = newProduct.code.trim()
        finalProduct = finalProduct.copy(code = normalizedCode)
        if (oldProduct.code != normalizedCode) {"""
content = content.replace(target_update, replacement_update)

# Replace newProduct.code with normalizedCode inside the if block
target_check = """            android.util.Log.d("ProductSync", "Verificando se o novo código já existe: ${newProduct.code}")
            val existingProduct = repository.getProductByCodeSync(newProduct.code)
            if (existingProduct != null && existingProduct.id != oldProduct.id) {
                android.util.Log.e("ProductSync", "Código já existe: ${newProduct.code}")
                _syncMessage.emit("Código já cadastrado\\n\\nO código ${newProduct.code} já pertence ao produto:\\n${existingProduct.name}")
                return false
            }"""
replacement_check = """            android.util.Log.d("ProductSync", "Verificando se o novo código já existe: $normalizedCode")
            val existingProduct = repository.getProductByCodeSync(normalizedCode)
            if (existingProduct != null && existingProduct.id != oldProduct.id) {
                android.util.Log.e("ProductSync", "Código já existe: $normalizedCode")
                _syncMessage.emit("Código já cadastrado\\n\\nO código $normalizedCode já pertence ao produto:\\n${existingProduct.name}")
                return false
            }"""
content = content.replace(target_check, replacement_check)

with open("app/src/main/java/com/example/ui/MainViewModel.kt", "w") as f:
    f.write(content)

