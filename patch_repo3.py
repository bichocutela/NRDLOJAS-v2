content = open("app/src/main/java/com/example/data/ProductRepository.kt").read()

# find where unaccent starts
idx = content.find("fun String.unaccent()")

if idx != -1:
    before = content[:idx]
    after = content[idx:]
    if before.strip().endswith("}") == False:
        pass
    
    # Let's just properly rewrite the file since we know exactly what we want.
