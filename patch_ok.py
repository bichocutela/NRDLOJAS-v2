import re
content = open("app/src/main/java/com/example/data/FirebaseService.kt").read()

imports = """import okhttp3.MediaType.Companion.toMediaType
"""
content = content.replace("import okhttp3.Request", imports + "import okhttp3.Request")

content = content.replace('okhttp3.MediaType.parse("application/json")', '"application/json".toMediaType()')
open("app/src/main/java/com/example/data/FirebaseService.kt", "w").write(content)
