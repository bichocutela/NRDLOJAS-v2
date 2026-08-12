import re
content = open("app/src/main/java/com/example/data/FirebaseService.kt").read()

pattern = r"""    suspend fun publishProductEvent\(type: String, productName: String, oldName: String\? = null, productCode: String\) \{
        if \(\!isFirebaseConfigured\(\)\) return
        try \{
            val firestore = FirebaseFirestore\.getInstance\(\)
            firestore\.collection\("latest_product"\)\.document\("latest"\)
                \.set\(mapOf\(
                    "type" to type,
                    "name" to productName,
                    "oldName" to \(oldName \?: ""\),
                    "code" to productCode,
                    "timestamp" to System\.currentTimeMillis\(\)
                \)\)\.await\(\)
        \} catch \(e: Exception\) \{
            Log\.e\("FirebaseService", "Error publishing product event", e\)
        \}
    \}"""

replacement = """    suspend fun publishProductEvent(type: String, productName: String, oldName: String? = null, productCode: String) {
        if (!isFirebaseConfigured()) return
        try {
            val supabaseUrl = BuildConfig.SUPABASE_URL
            val supabaseKey = BuildConfig.SUPABASE_ANON_KEY
            if (supabaseUrl.isEmpty() || supabaseKey.isEmpty()) {
                Log.e("FirebaseService", "Supabase not configured, cannot send FCM")
                return
            }

            val title = if (type == "NEW_PRODUCT") "Novo produto adicionado" else "Produto atualizado"
            val text = when (type) {
                "NEW_PRODUCT" -> "$productName foi adicionado ao aplicativo."
                "CODE_CHANGED" -> "O código de $productName foi atualizado."
                "NAME_CHANGED" -> "${oldName ?: ""} agora aparece como $productName."
                "INFO_CHANGED" -> "As informações de $productName foram atualizadas."
                "PRODUCT_DELETED" -> "$productName foi excluído."
                else -> "$productName foi atualizado."
            }

            val json = org.json.JSONObject()
            json.put("title", title)
            json.put("body", text)
            json.put("topic", "products")

            val requestBody = okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/json"), json.toString())
            val request = okhttp3.Request.Builder()
                .url("$supabaseUrl/functions/v1/send-fcm")
                .post(requestBody)
                .addHeader("Authorization", "Bearer $supabaseKey")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e("FirebaseService", "Error calling send-fcm: ${response.code} ${response.message} ${response.body?.string()}")
            } else {
                Log.d("FirebaseService", "FCM sent successfully")
            }
        } catch (e: Exception) {
            Log.e("FirebaseService", "Error publishing product event via FCM", e)
        }
    }"""

content = re.sub(pattern, replacement, content, flags=re.DOTALL)
open("app/src/main/java/com/example/data/FirebaseService.kt", "w").write(content)
