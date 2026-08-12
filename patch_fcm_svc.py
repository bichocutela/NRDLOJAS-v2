import re
content = open("app/src/main/java/com/example/util/MyFirebaseMessagingService.kt").read()

content = content.replace('Log.d("FCM", "New token: $token")', '')
open("app/src/main/java/com/example/util/MyFirebaseMessagingService.kt", "w").write(content)
