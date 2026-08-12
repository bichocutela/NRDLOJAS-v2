import re
content = open("app/src/main/java/com/example/data/FirebaseService.kt").read()

content = content.replace("""        if (!isFirebaseConfigured()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }""", """        if (!isFirebaseConfigured()) {
            close()
            return@callbackFlow
        }""")

open("app/src/main/java/com/example/data/FirebaseService.kt", "w").write(content)
