import re
content = open("app/src/main/java/com/example/data/ProductRepository.kt").read()

pattern = r"(suspend fun populateInitialDataIfNeeded\(\) \{)(.*?)(^\})"

def repl(m):
    return m.group(1) + "\n        // Removido para forçar o download da nuvem (instalação nova)\n    }"

content = re.sub(pattern, repl, content, flags=re.MULTILINE | re.DOTALL)
open("app/src/main/java/com/example/data/ProductRepository.kt", "w").write(content)
