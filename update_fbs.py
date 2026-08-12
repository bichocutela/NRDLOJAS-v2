import re
content = open("app/src/main/java/com/example/data/FirebaseService.kt").read()
content = content.replace('val firebaseToken = "bypass-token"', 'val firebaseToken = "nrdlojas"')
open("app/src/main/java/com/example/data/FirebaseService.kt", "w").write(content)
