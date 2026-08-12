import re

content = open("app/src/main/java/com/example/data/ProductRepository.kt").read()

pattern = r"(suspend fun populateInitialDataIfNeeded\(\) \{)(.*?)(^\})"
def repl(m):
    body = m.group(2)
    new_body = """
        val prefs = context.getSharedPreferences("nrd_prefs", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean("has_populated_initial_data", false)) {
            return
        }
""" + body + """
        prefs.edit().putBoolean("has_populated_initial_data", true).apply()
"""
    return m.group(1) + new_body + m.group(3)

content = re.sub(pattern, repl, content, flags=re.MULTILINE | re.DOTALL)
open("app/src/main/java/com/example/data/ProductRepository.kt", "w").write(content)
