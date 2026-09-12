from pathlib import Path

path = Path("app/src/main/java/com/example/ui/AcpConsultationScreen.kt")
text = path.read_text(encoding="utf-8")
old = '''    }

    if (configure && canConfigure) {
'''
new = '''        }
    }

    if (configure && canConfigure) {
'''
count = text.count(old)
if count != 1:
    raise SystemExit(f"Expected one scaffold closing block, found {count}")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
print("Fixed AcpConsultationScreen Box/Scaffold closing braces.")
