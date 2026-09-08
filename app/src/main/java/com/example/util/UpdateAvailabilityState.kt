package com.example.util

import android.content.Context
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object UpdateAvailabilityState {
    private const val PREFS_NAME = "nrd_update_availability"
    private const val KEY_AVAILABLE = "available"
    private const val KEY_REMOTE_TAG = "remote_tag"
    private const val KEY_LAST_CHECKED_AT = "last_checked_at"

    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _available.value = prefs.getBoolean(KEY_AVAILABLE, false)
            initialized = true
        }
    }

    suspend fun refresh(context: Context): ReleaseCheckResult {
        initialize(context)
        val result = UpdateChecker.checkLatestRelease()
        if (result is ReleaseCheckResult.Success) {
            setFromRemoteTag(context, result.tagName)
        }
        return result
    }

    fun setFromRemoteTag(context: Context, remoteTag: String) {
        initialize(context)
        val isNewer = UpdateChecker.isRemoteVersionNewer(BuildConfig.VERSION_NAME, remoteTag)
        _available.value = isNewer
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AVAILABLE, isNewer)
            .putString(KEY_REMOTE_TAG, remoteTag)
            .putLong(KEY_LAST_CHECKED_AT, System.currentTimeMillis())
            .apply()
    }

    fun clearIfCurrent(context: Context) {
        initialize(context)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val remoteTag = prefs.getString(KEY_REMOTE_TAG, null)
        if (remoteTag != null && !UpdateChecker.isRemoteVersionNewer(BuildConfig.VERSION_NAME, remoteTag)) {
            _available.value = false
            prefs.edit().putBoolean(KEY_AVAILABLE, false).apply()
        }
    }
}
