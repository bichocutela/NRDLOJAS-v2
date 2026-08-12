import re

file_path = "app/src/main/java/com/example/data/FirebaseService.kt"

with open(file_path, "r") as f:
    content = f.read()

if "fun syncAllProducts" in content:
    content = content.replace("suspend fun syncAllProducts(products: List<com.example.data.Product>) {", 
"""suspend fun syncAllProducts(products: List<com.example.data.Product>) {
        ensureAuthenticated()""")
    
    with open(file_path, "w") as f:
        f.write(content)
    print("Patched sync!")
else:
    print("Not found")
