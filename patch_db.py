import re
content = open("app/src/main/java/com/example/data/AppDatabase.kt").read()
content = content.replace('version = 4', 'version = 5')
open("app/src/main/java/com/example/data/AppDatabase.kt", "w").write(content)
