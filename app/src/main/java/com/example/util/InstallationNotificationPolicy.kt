package com.example.util

internal object InstallationNotificationPolicy {
    fun shouldDisplay(isMaster: Boolean, notificationsEnabled: Boolean, installationEnabled: Boolean, eventId: String?): Boolean =
        isMaster && notificationsEnabled && installationEnabled &&
            eventId?.matches(Regex("[0-9a-f]{64}_[0-9]{1,10}")) == true
}
