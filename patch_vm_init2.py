import re
content = open("app/src/main/java/com/example/ui/MainViewModel.kt").read()

pattern = r"(viewModelScope\.launch \{\s+repository\.populateInitialDataIfNeeded\(\)\s+syncProductsFromFirebase\(\))(.*?)(private val _searchQuery)"

def repl(m):
    return m.group(1) + "\n        }\n    }\n\n    " + m.group(3)

content = re.sub(pattern, repl, content, flags=re.MULTILINE | re.DOTALL)
open("app/src/main/java/com/example/ui/MainViewModel.kt", "w").write(content)
