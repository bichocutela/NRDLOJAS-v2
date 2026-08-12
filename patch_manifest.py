import re
content = open("app/src/main/AndroidManifest.xml").read()
service_code = """
        <service
            android:name=".util.MyFirebaseMessagingService"
            android:exported="true">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>
"""
content = content.replace("    </application>", service_code + "    </application>")
open("app/src/main/AndroidManifest.xml", "w").write(content)
