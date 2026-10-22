import re

with open("app/src/main/AndroidManifest.xml", "r") as f:
    content = f.read()

colors = ["multicolor", "red", "green", "blue", "orange", "gold"]

for color in colors:
    pattern = rf'(<activity-alias android:icon="@mipmap/ic_launcher_{color}")\s*(\n\s*android:targetActivity=".MainActivity")'
    replacement = rf'\1\n            android:roundIcon="@mipmap/ic_launcher_{color}_round"\2'
    content = re.sub(pattern, replacement, content)

with open("app/src/main/AndroidManifest.xml", "w") as f:
    f.write(content)

