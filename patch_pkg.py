import re
content = open("app/src/main/java/com/example/data/FirebaseService.kt").read()

content = content.replace('import okhttp3.MediaType.Companion.toMediaType\npackage com.example.data\n', 'package com.example.data\nimport okhttp3.MediaType.Companion.toMediaType\n')
open("app/src/main/java/com/example/data/FirebaseService.kt", "w").write(content)
