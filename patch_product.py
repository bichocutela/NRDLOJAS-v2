import re
content = open("app/src/main/java/com/example/data/Product.kt").read()
content = content.replace('Index(value = ["code"])', 'Index(value = ["code"], unique = true)')
open("app/src/main/java/com/example/data/Product.kt", "w").write(content)
