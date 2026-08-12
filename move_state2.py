import re
content = open("app/src/main/java/com/example/ui/MainViewModel.kt").read()

pattern_to_extract = r"(\n    private val _searchQuery = MutableStateFlow\(\"\"\).*?val latestProductLocal = repository\.latestProductLocal\.stateIn\(viewModelScope, SharingStarted\.WhileSubscribed\(5000\), null\)\n)"

match = re.search(pattern_to_extract, content, re.DOTALL)
if match:
    extracted = match.group(1)
    # remove it from its original place
    content = content.replace(extracted, "\n")
    
    # insert before init {
    insert_pos = content.find("    init {")
    if insert_pos != -1:
        content = content[:insert_pos] + extracted + content[insert_pos:]

open("app/src/main/java/com/example/ui/MainViewModel.kt", "w").write(content)
