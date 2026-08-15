package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import java.util.UUID
import org.json.JSONObject

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppNotification(
    val id: Long,
    val type: String,
    val title: String,
    val body: String,
    val read: Boolean,
    val timestamp: Long
)

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
    val barcodeNumberScale: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[BARCODE_NUMBER_SCALE] ?: 1.0f
    }
    val barcodeTitleScale: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[BARCODE_TITLE_SCALE] ?: 1.0f
    }
    val mostUsedLimit: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MOST_USED_LIMIT] ?: 8
    }
    val carouselIntervalSeconds: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[CAROUSEL_INTERVAL_SECONDS] ?: 5
    }
    val notificationHistory: Flow<List<AppNotification>> = context.dataStore.data.map { preferences ->
        decodeNotifications(preferences[NOTIFICATION_HISTORY_JSON].orEmpty())
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

    val appIcon: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[APP_ICON] ?: "multicolor"
    }

    val appearanceMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[APPEARANCE_MODE] ?: "system"
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
    suspend fun setBarcodeNumberScale(scale: Float) {
        context.dataStore.edit { it[BARCODE_NUMBER_SCALE] = scale.coerceIn(0.8f, 1.6f) }
    }
    suspend fun setBarcodeTitleScale(scale: Float) {
        context.dataStore.edit { it[BARCODE_TITLE_SCALE] = scale.coerceIn(0.8f, 1.5f) }
    }
    suspend fun setMostUsedLimit(limit: Int) {
        context.dataStore.edit { it[MOST_USED_LIMIT] = limit.coerceIn(1, 50) }
    }
    suspend fun setCarouselIntervalSeconds(seconds: Int) {
        context.dataStore.edit { it[CAROUSEL_INTERVAL_SECONDS] = seconds.coerceIn(3, 30) }
    }
    suspend fun addNotification(notification: AppNotification) {
        context.dataStore.edit { preferences ->
            val current = decodeNotifications(preferences[NOTIFICATION_HISTORY_JSON].orEmpty())
            val updated = (listOf(notification) + current).distinctBy { it.id }.take(50)
            preferences[NOTIFICATION_HISTORY_JSON] = encodeNotifications(updated)
        }
    }
    suspend fun markNotificationRead(id: Long) {
        context.dataStore.edit { preferences ->
            val updated = decodeNotifications(preferences[NOTIFICATION_HISTORY_JSON].orEmpty())
                .map { if (it.id == id) it.copy(read = true) else it }
            preferences[NOTIFICATION_HISTORY_JSON] = encodeNotifications(updated)
        }
    }
    suspend fun markAllNotificationsRead() {
        context.dataStore.edit { preferences ->
            val updated = decodeNotifications(preferences[NOTIFICATION_HISTORY_JSON].orEmpty()).map { it.copy(read = true) }
            preferences[NOTIFICATION_HISTORY_JSON] = encodeNotifications(updated)
        }
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

    suspend fun setAppIcon(icon: String) {
        context.dataStore.edit { it[APP_ICON] = icon }
    }

    suspend fun setAppearanceMode(mode: String) {
        context.dataStore.edit { it[APPEARANCE_MODE] = mode }
    }

    suspend fun setOnboardingShown(shown: Boolean) {
        context.dataStore.edit { it[ONBOARDING_SHOWN] = shown }
    }

    suspend fun getOrCreateInstallationId(): String {
        val existing = context.dataStore.data.first()[INSTALLATION_ID]
        if (!existing.isNullOrBlank()) return existing
        val generated = UUID.randomUUID().toString()
        context.dataStore.edit { it[INSTALLATION_ID] = generated }
        return generated
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
        val BARCODE_NUMBER_SCALE = floatPreferencesKey("barcode_number_scale")
        val BARCODE_TITLE_SCALE = floatPreferencesKey("barcode_title_scale")
        val MOST_USED_LIMIT = intPreferencesKey("most_used_limit")
        val CAROUSEL_INTERVAL_SECONDS = intPreferencesKey("carousel_interval_seconds")
        val NOTIFICATION_HISTORY_JSON = stringPreferencesKey("notification_history_json")
        val BANNER_IMAGE_URI = stringPreferencesKey("banner_image_uri")
        val LAST_NOTIFIED_PRODUCT_CODE = stringPreferencesKey("last_notified_product_code")
        val APP_THEME = stringPreferencesKey("app_theme")
        val APP_ICON = stringPreferencesKey("app_icon")
        val APPEARANCE_MODE = stringPreferencesKey("appearance_mode")
        val ONBOARDING_SHOWN = booleanPreferencesKey("onboarding_shown")
        val INSTALLATION_ID = stringPreferencesKey("installation_id")
    }

    private fun encodeNotifications(items: List<AppNotification>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("type", item.type)
                put("title", item.title)
                put("body", item.body)
                put("read", item.read)
                put("timestamp", item.timestamp)
            })
        }
        return array.toString()
    }

    private fun decodeNotifications(json: String): List<AppNotification> = try {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            AppNotification(
                id = item.optLong("id"),
                type = item.optString("type"),
                title = item.optString("title"),
                body = item.optString("body"),
                read = item.optBoolean("read", false),
                timestamp = item.optLong("timestamp")
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}
