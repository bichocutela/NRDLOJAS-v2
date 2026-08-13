import re

with open("app/src/main/AndroidManifest.xml", "r", encoding="utf-8") as f:
    content = f.read()

# The block to replace
old_activity = """        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.MyApplication">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>"""

new_activities = """        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.MyApplication">
        </activity>

        <activity-alias
            android:name=".MainActivityMulticolor"
            android:targetActivity=".MainActivity"
            android:icon="@drawable/icon_multicolor"
            android:roundIcon="@drawable/icon_multicolor"
            android:exported="true"
            android:enabled="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>

        <activity-alias
            android:name=".MainActivityRed"
            android:targetActivity=".MainActivity"
            android:icon="@drawable/icon_red"
            android:roundIcon="@drawable/icon_red"
            android:exported="true"
            android:enabled="false">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>

        <activity-alias
            android:name=".MainActivityGreen"
            android:targetActivity=".MainActivity"
            android:icon="@drawable/icon_green"
            android:roundIcon="@drawable/icon_green"
            android:exported="true"
            android:enabled="false">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>

        <activity-alias
            android:name=".MainActivityBlue"
            android:targetActivity=".MainActivity"
            android:icon="@drawable/icon_blue"
            android:roundIcon="@drawable/icon_blue"
            android:exported="true"
            android:enabled="false">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>

        <activity-alias
            android:name=".MainActivityOrange"
            android:targetActivity=".MainActivity"
            android:icon="@drawable/icon_orange"
            android:roundIcon="@drawable/icon_orange"
            android:exported="true"
            android:enabled="false">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>

        <activity-alias
            android:name=".MainActivityGold"
            android:targetActivity=".MainActivity"
            android:icon="@drawable/icon_gold"
            android:roundIcon="@drawable/icon_gold"
            android:exported="true"
            android:enabled="false">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>"""

if old_activity in content:
    content = content.replace(old_activity, new_activities)
    with open("app/src/main/AndroidManifest.xml", "w", encoding="utf-8") as f:
        f.write(content)
    print("Success")
else:
    print("Could not find the exact block. Searching with regex...")
    
    # regex approach
    pattern = re.compile(r'<activity\s+android:name="\.MainActivity"[^>]*>.*?<intent-filter>.*?<action android:name="android\.intent\.action\.MAIN"\s*/>.*?<category android:name="android\.intent\.category\.LAUNCHER"\s*/>.*?</intent-filter>.*?</activity>', re.DOTALL)
    
    if pattern.search(content):
        content = pattern.sub(new_activities, content)
        with open("app/src/main/AndroidManifest.xml", "w", encoding="utf-8") as f:
            f.write(content)
        print("Success regex")
    else:
        print("Failed to replace!")

