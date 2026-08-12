package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class UserPreferences(private val context: Context) {
    val vibrateOnClick: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[VIBRATE_ON_CLICK] ?: true
    }
    val vibrateOnFound: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[VIBRATE_ON_FOUND] ?: true
    }
    val largeText: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[LARGE_TEXT] ?: false
    }
    val boldOutline: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[BOLD_OUTLINE] ?: false
    }
    val uppercaseBold: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[UPPERCASE_BOLD] ?: false
    }
    val fontScale: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[FONT_SCALE] ?: 1.0f
    }
    val bannerImageUri: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[BANNER_IMAGE_URI]
    }
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NOTIFICATIONS_ENABLED] ?: true
    }
    val notificationsProductAddedEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NOTIFICATIONS_PRODUCT_ADDED_ENABLED] ?: true
    }

    val notificationsCodeChangedEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NOTIFICATIONS_CODE_CHANGED_ENABLED] ?: true
    }

    val lastNotifiedProductCode: Flow<String?> = context.dataStore.data.map { it[LAST_NOTIFIED_PRODUCT_CODE] }
    
    val appTheme: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[APP_THEME] ?: "multicolor"
    }

    val onboardingShown: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ONBOARDING_SHOWN] ?: false
    }

    suspend fun setVibrateOnClick(enabled: Boolean) {
        context.dataStore.edit { it[VIBRATE_ON_CLICK] = enabled }
    }
    suspend fun setVibrateOnFound(enabled: Boolean) {
        context.dataStore.edit { it[VIBRATE_ON_FOUND] = enabled }
    }
    suspend fun setLargeText(enabled: Boolean) {
        context.dataStore.edit { it[LARGE_TEXT] = enabled }
    }
    suspend fun setBoldOutline(enabled: Boolean) {
        context.dataStore.edit { it[BOLD_OUTLINE] = enabled }
    }
    suspend fun setUppercaseBold(enabled: Boolean) {
        context.dataStore.edit { it[UPPERCASE_BOLD] = enabled }
    }
    suspend fun setFontScale(scale: Float) {
        context.dataStore.edit { it[FONT_SCALE] = scale }
    }
    suspend fun setBannerImageUri(uri: String?) {
        context.dataStore.edit { if (uri == null) it.remove(BANNER_IMAGE_URI) else it[BANNER_IMAGE_URI] = uri }
    }
    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    }
    suspend fun setNotificationsProductAddedEnabled(enabled: Boolean) {
        context.dataStore.edit { it[NOTIFICATIONS_PRODUCT_ADDED_ENABLED] = enabled }
    }

    suspend fun setNotificationsCodeChangedEnabled(enabled: Boolean) {
        context.dataStore.edit { it[NOTIFICATIONS_CODE_CHANGED_ENABLED] = enabled }
    }

    suspend fun setLastNotifiedProductCode(code: String) {
        context.dataStore.edit { it[LAST_NOTIFIED_PRODUCT_CODE] = code }
    }

    suspend fun setAppTheme(theme: String) {
        context.dataStore.edit { it[APP_THEME] = theme }
    }

    suspend fun setOnboardingShown(shown: Boolean) {
        context.dataStore.edit { it[ONBOARDING_SHOWN] = shown }
    }

    companion object {
        val VIBRATE_ON_CLICK = booleanPreferencesKey("vibrate_on_click")
        val VIBRATE_ON_FOUND = booleanPreferencesKey("vibrate_on_found")
        val LARGE_TEXT = booleanPreferencesKey("large_text")
        val BOLD_OUTLINE = booleanPreferencesKey("bold_outline")
        val UPPERCASE_BOLD = booleanPreferencesKey("uppercase_bold")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val NOTIFICATIONS_PRODUCT_ADDED_ENABLED = booleanPreferencesKey("notifications_product_added_enabled")
        val NOTIFICATIONS_CODE_CHANGED_ENABLED = booleanPreferencesKey("notifications_code_changed_enabled")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val BANNER_IMAGE_URI = stringPreferencesKey("banner_image_uri")
        val LAST_NOTIFIED_PRODUCT_CODE = stringPreferencesKey("last_notified_product_code")
        val APP_THEME = stringPreferencesKey("app_theme")
        val ONBOARDING_SHOWN = booleanPreferencesKey("onboarding_shown")
    }
}
