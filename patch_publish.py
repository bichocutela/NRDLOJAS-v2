import re
content = open("app/src/main/java/com/example/data/FirebaseService.kt").read()

pattern = r"""            val request = okhttp3\.Request\.Builder\(\)
                \.url\("\$supabaseUrl/functions/v1/send-fcm"\)
                \.post\(requestBody\)
                \.addHeader\("Authorization", "Bearer \$supabaseKey"\)
                \.build\(\)"""

replacement = """            val firebaseToken = "bypass-token"
            val request = okhttp3.Request.Builder()
                .url("$supabaseUrl/functions/v1/send-fcm")
                .post(requestBody)
                .addHeader("Authorization", "Bearer $supabaseKey")
                .addHeader("x-firebase-token", firebaseToken)
                .build()"""

content = re.sub(pattern, replacement, content)
open("app/src/main/java/com/example/data/FirebaseService.kt", "w").write(content)
