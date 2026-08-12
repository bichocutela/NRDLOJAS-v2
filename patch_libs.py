import re
content = open("gradle/libs.versions.toml").read()
if "firebase-messaging" not in content:
    content = content.replace('firebase-firestore = { group = "com.google.firebase", name = "firebase-firestore" }', 'firebase-firestore = { group = "com.google.firebase", name = "firebase-firestore" }\nfirebase-messaging = { group = "com.google.firebase", name = "firebase-messaging" }')
    open("gradle/libs.versions.toml", "w").write(content)
