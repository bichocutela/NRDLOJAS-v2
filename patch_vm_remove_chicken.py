import re
content = open("app/src/main/java/com/example/ui/MainViewModel.kt").read()

pattern = r"(val existing = repository\.searchProductsSync\(\"256075\"\)\s*if \(existing\.isEmpty\(\)\) \{.*?\n\s*\})"

def repl(m):
    return ""

content = re.sub(pattern, repl, content, flags=re.MULTILINE | re.DOTALL)
open("app/src/main/java/com/example/ui/MainViewModel.kt", "w").write(content)
