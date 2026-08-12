import re
content = open("app/src/main/java/com/example/ui/MainViewModel.kt").read()

pattern = r"(com\.example\.data\.FirebaseService\.observeProducts\(\)\.collect \{ remoteProducts ->\s+)if \(remoteProducts\.isNotEmpty\(\)\) \{(.*?)\s+\}\s+\}"

def repl(m):
    return m.group(1) + m.group(2) + "\n            }"

content = re.sub(pattern, repl, content, flags=re.MULTILINE | re.DOTALL)
open("app/src/main/java/com/example/ui/MainViewModel.kt", "w").write(content)
