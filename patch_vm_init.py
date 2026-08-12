import re

content = open("app/src/main/java/com/example/ui/MainViewModel.kt").read()

pattern = r"(val existing = repository\.searchProductsSync\(\"256075\"\)).*?(repository\.insertProducts\(newProducts\)\s*\})"

content = re.sub(pattern, "// Removida inserção manual de produtos (resolvido por sync)", content, flags=re.MULTILINE | re.DOTALL)
open("app/src/main/java/com/example/ui/MainViewModel.kt", "w").write(content)
