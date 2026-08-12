import re
content = open("app/src/main/java/com/example/ui/MainViewModel.kt").read()

pattern = r"\} else \{\s+repository\.updateProduct\(finalProduct\)\s+_syncMessage\.emit\(\"Atualizado apenas localmente\"\)\s+return true\s+\}"

def repl(m):
    return """} else {
            _syncMessage.emit("Nuvem não configurada. Não foi possível atualizar.")
            return false
        }"""

content = re.sub(pattern, repl, content, flags=re.MULTILINE | re.DOTALL)
open("app/src/main/java/com/example/ui/MainViewModel.kt", "w").write(content)
