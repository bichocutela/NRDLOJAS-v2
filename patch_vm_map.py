import re
content = open("app/src/main/java/com/example/ui/MainViewModel.kt").read()

def repl(m):
    return """val missingOrUpdated = remoteProducts.mapNotNull { remote ->
                        val local = localProducts.find { it.code == remote.code }
                        if (local == null) {
                            remote
                        } else if (local.name != remote.name || local.imageUrl != remote.imageUrl || local.category != remote.category || local.unit != remote.unit) {
                            remote.copy(id = local.id, searchCount = local.searchCount, lastSearchedAt = local.lastSearchedAt, isFavorite = local.isFavorite)
                        } else {
                            null
                        }
                    }"""

pattern = r"val missingOrUpdated = remoteProducts\.filter \{ remote ->.*?local == null \|\| local\.name != remote\.name.*?local\.unit != remote\.unit\s*\}"

content = re.sub(pattern, repl, content, flags=re.MULTILINE | re.DOTALL)
open("app/src/main/java/com/example/ui/MainViewModel.kt", "w").write(content)
