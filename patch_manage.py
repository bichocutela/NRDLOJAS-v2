import sys

with open("app/src/main/java/com/example/ui/ManageProductsScreen.kt", "r") as f:
    content = f.read()

content = content.replace("import com.example.data.Product", "import com.example.data.Product\nimport kotlinx.coroutines.launch\n")

with open("app/src/main/java/com/example/ui/ManageProductsScreen.kt", "w") as f:
    f.write(content)
