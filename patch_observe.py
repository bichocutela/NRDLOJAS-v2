import re
content = open("app/src/main/java/com/example/data/FirebaseService.kt").read()

pattern = r"(addSnapshotListener \{ snapshot, error ->\s+)(if \(error != null\) \{.*?return@addSnapshotListener\s+\})"

def repl(m):
    return m.group(1) + """if (error != null) {
                    Log.e("FirebaseService", "Error in observeProducts", error)
                    return@addSnapshotListener
                }"""

content = re.sub(pattern, repl, content, flags=re.MULTILINE | re.DOTALL)
open("app/src/main/java/com/example/data/FirebaseService.kt", "w").write(content)
