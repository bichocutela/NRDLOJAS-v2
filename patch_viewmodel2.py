import sys

with open("app/src/main/java/com/example/ui/MainViewModel.kt", "r") as f:
    content = f.read()

target = """        suspend fun addProductSuspend(name: String, code: String, category: String, unit: String, imageUrl: String? = null): Boolean {
        val existingProduct = repository.getProductByCodeSync(code)
        if (existingProduct != null) {
            _syncMessage.emit("Código já cadastrado\\n\\nJá existe um produto utilizando o código $code:\\n${existingProduct.name}")
            return false
        }"""

replacement = """        suspend fun addProductSuspend(name: String, code: String, category: String, unit: String, imageUrl: String? = null): Boolean {
        val normalizedCode = code.trim()
        val existingProduct = repository.getProductByCodeSync(normalizedCode)
        if (existingProduct != null) {
            _syncMessage.emit("Código já cadastrado\\n\\nJá existe um produto utilizando o código $normalizedCode:\\n${existingProduct.name}")
            return false
        }"""

content = content.replace(target, replacement)

# Replace 'code = code' with 'code = normalizedCode'
target2 = """        val product = Product(
            code = code,
            name = name,"""

replacement2 = """        val product = Product(
            code = normalizedCode,
            name = name,"""

content = content.replace(target2, replacement2)

with open("app/src/main/java/com/example/ui/MainViewModel.kt", "w") as f:
    f.write(content)
