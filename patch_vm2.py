import re
content = open("app/src/main/java/com/example/ui/MainViewModel.kt").read()

pattern = r"""            if \(success\) \{
                repository\.deleteProduct\(product\)
                _syncMessage\.emit\("Produto excluído com sucesso\."\)
                return true
            \}"""

replacement = """            if (success) {
                repository.deleteProduct(product)
                com.example.data.FirebaseService.publishProductEvent("PRODUCT_DELETED", product.name, null, product.code)
                _syncMessage.emit("Produto excluído com sucesso.")
                return true
            }"""

content = re.sub(pattern, replacement, content)
open("app/src/main/java/com/example/ui/MainViewModel.kt", "w").write(content)
