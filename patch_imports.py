import re
content = open("app/src/main/java/com/example/data/FirebaseService.kt").read()

content = content.replace('import okhttp3.MediaType.Companion.toMediaType\n', '')
content = 'import okhttp3.MediaType.Companion.toMediaType\n' + content

open("app/src/main/java/com/example/data/FirebaseService.kt", "w").write(content)
