import re

with open("app/src/main/AndroidManifest.xml", "r") as f:
    content = f.read()

# 1. Add intent-filter to MainActivity
main_activity_pattern = r'(<activity\s+android:name="\.MainActivity"\s+android:exported="true"\s+android:label="@string/app_name"\s+android:theme="@style/Theme\.MyApplication">)(\s*</activity>)'
main_activity_replacement = r'\1\n            <intent-filter>\n                <action android:name="android.intent.action.MAIN" />\n                <category android:name="android.intent.category.LAUNCHER" />\n            </intent-filter>\n        </activity>'
content = re.sub(main_activity_pattern, main_activity_replacement, content)

# 2. Disable all activity-aliases
alias_pattern = r'(<activity-alias[^>]+android:name="\.MainActivityMulticolor"[^>]*?)android:enabled="true"'
alias_replacement = r'\1android:enabled="false"'
content = re.sub(alias_pattern, alias_replacement, content)

with open("app/src/main/AndroidManifest.xml", "w") as f:
    f.write(content)

