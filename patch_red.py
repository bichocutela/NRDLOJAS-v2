import sys

def patch_file(filename):
    with open(filename, "r") as f:
        content = f.read()

    target = 'initialValue = "red"'
    replacement = 'initialValue = "multicolor"'
    content = content.replace(target, replacement)
    
    target2 = 'initial = "red"'
    replacement2 = 'initial = "multicolor"'
    content = content.replace(target2, replacement2)

    with open(filename, "w") as f:
        f.write(content)

patch_file("app/src/main/java/com/example/MainActivity.kt")
patch_file("app/src/main/java/com/example/ui/SearchScreen.kt")
print("Success")
