import re
content = open("app/src/main/java/com/example/data/FirebaseService.kt").read()
# Revert the second occurrence of "nrdlojas" which is in uploadImage
parts = content.split('val firebaseToken = "nrdlojas"')
if len(parts) == 3:
    content = parts[0] + 'val firebaseToken = "nrdlojas"' + parts[1] + 'val firebaseToken = "bypass-token"' + parts[2]
    open("app/src/main/java/com/example/data/FirebaseService.kt", "w").write(content)
