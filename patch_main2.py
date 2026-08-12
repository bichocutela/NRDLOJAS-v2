import re
content = open("app/src/main/java/com/example/MainActivity.kt").read()

pattern = r"""            LaunchedEffect\(latestFirebase, lastNotifiedCode\) \{
                if \(lastNotifiedCode == "___LOADING___"\) return@LaunchedEffect
                
                val dispName = latestFirebase\?\.get\("name"\)\?\.toString\(\)
                val eventId = latestFirebase\?\.get\("timestamp"\)\?\.toString\(\) \?: latestFirebase\?\.get\("code"\)\?\.toString\(\)
                val type = latestFirebase\?\.get\("type"\)\?\.toString\(\) \?: "NEW_PRODUCT"
                val oldName = latestFirebase\?\.get\("oldName"\)\?\.toString\(\) \?: ""

                if \(dispName != null && eventId != null && eventId != lastNotifiedCode\) \{
                    if \(lastNotifiedCode == null\) \{
                        userPreferences\.setLastNotifiedProductCode\(eventId\)
                    \} else \{
                        com\.example\.util\.NotificationHelper\.showProductEventNotification\(context, type, dispName, oldName\)
                        userPreferences\.setLastNotifiedProductCode\(eventId\)
                    \}
                \}
            \}"""

content = re.sub(pattern, "", content)
open("app/src/main/java/com/example/MainActivity.kt", "w").write(content)
