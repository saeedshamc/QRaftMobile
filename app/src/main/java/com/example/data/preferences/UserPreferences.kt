package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.ui.i18n.AppLanguage

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

class UserPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("qraft_prefs", Context.MODE_PRIVATE)

    fun getThemeMode(): AppThemeMode {
        val name = prefs.getString("theme_mode", AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        return try { AppThemeMode.valueOf(name) } catch (e: Exception) { AppThemeMode.SYSTEM }
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun getLanguage(): AppLanguage {
        val code = prefs.getString("app_language", AppLanguage.EN.name) ?: AppLanguage.EN.name
        return try { AppLanguage.valueOf(code) } catch (e: Exception) { AppLanguage.EN }
    }

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString("app_language", language.name).apply()
    }

    fun getLastContentType(): String {
        return prefs.getString("last_content_type", "URL") ?: "URL"
    }

    fun setLastContentType(type: String) {
        prefs.edit().putString("last_content_type", type).apply()
    }

    fun getTotalGeneratedCount(): Int {
        return prefs.getInt("total_generated_count", 0)
    }

    fun incrementGeneratedCount(): Int {
        val count = getTotalGeneratedCount() + 1
        prefs.edit().putInt("total_generated_count", count).apply()
        return count
    }

    fun getCountForType(type: String): Int {
        return prefs.getInt("count_type_$type", 0)
    }

    fun incrementCountForType(type: String) {
        val c = getCountForType(type) + 1
        prefs.edit().putInt("count_type_$type", c).apply()
    }

    fun getLastCloudSyncTime(): Long {
        return prefs.getLong("last_cloud_sync_timestamp", 0L)
    }

    fun setLastCloudSyncTime(timestamp: Long) {
        prefs.edit().putLong("last_cloud_sync_timestamp", timestamp).apply()
    }

    fun getCustomProfilesJson(): String {
        return prefs.getString("custom_design_profiles_json", "[]") ?: "[]"
    }

    fun setCustomProfilesJson(json: String) {
        prefs.edit().putString("custom_design_profiles_json", json).apply()
    }

    fun getAutoCopyScanned(): Boolean {
        return prefs.getBoolean("auto_copy_scanned_qr", true)
    }

    fun setAutoCopyScanned(enabled: Boolean) {
        prefs.edit().putBoolean("auto_copy_scanned_qr", enabled).apply()
    }

    fun getScannerEcoMode(): Boolean {
        return prefs.getBoolean("scanner_eco_mode", true)
    }

    fun setScannerEcoMode(enabled: Boolean) {
        prefs.edit().putBoolean("scanner_eco_mode", enabled).apply()
    }

    fun clearAllStats() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("count_type_") || it == "total_generated_count" || it.startsWith("last_cloud_sync") }.forEach {
            editor.remove(it)
        }
        editor.apply()
    }
}
