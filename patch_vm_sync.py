import re
content = open("app/src/main/java/com/example/ui/MainViewModel.kt").read()

pattern = r"(val remoteProducts = com\.example\.data\.FirebaseService\.getAllProducts\(\)\s+)if \(remoteProducts\.isNotEmpty\(\)\) \{(.*?)\s+\}\s+\} catch"

def repl(m):
    return m.group(1) + m.group(2) + "\n            } catch"

content = re.sub(pattern, repl, content, flags=re.MULTILINE | re.DOTALL)
open("app/src/main/java/com/example/ui/MainViewModel.kt", "w").write(content)
