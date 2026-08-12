import re

file_path = "app/src/main/java/com/example/data/FirebaseService.kt"

with open(file_path, "r") as f:
    content = f.read()

ensure_auth = """    private suspend fun ensureAuthenticated() {
        try {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                try {
                    auth.signInWithEmailAndPassword("admin@nrdlojas.com", "nrdlojas").await()
                } catch (e: Exception) {
                    auth.signInAnonymously().await()
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseService", "Auth falhou", e)
        }
    }
"""

if "fun saveProduct" in content:
    content = content.replace("suspend fun saveProduct(product: com.example.data.Product): Boolean {", 
"""suspend fun saveProduct(product: com.example.data.Product): Boolean {
        ensureAuthenticated()""")

    content = content.replace("suspend fun deleteProduct(code: String): Boolean {",
"""suspend fun deleteProduct(code: String): Boolean {
        ensureAuthenticated()""")

    # Insert ensureAuthenticated before saveProduct
    content = content.replace("suspend fun saveProduct", ensure_auth + "\n    suspend fun saveProduct")

    with open(file_path, "w") as f:
        f.write(content)
    print("Patched!")
else:
    print("Not found")
