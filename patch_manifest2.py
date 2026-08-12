import re
content = open("app/src/main/AndroidManifest.xml").read()

content = content.replace('android:name=".util.MyFirebaseMessagingService"\n            android:exported="true"', 'android:name=".util.MyFirebaseMessagingService"\n            android:exported="false"')
open("app/src/main/AndroidManifest.xml", "w").write(content)
