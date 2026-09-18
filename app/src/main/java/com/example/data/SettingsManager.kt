package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("otakuhub_settings", Context.MODE_PRIVATE)

    private val securePrefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "otakuhub_secure_settings",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Throwable) {
            android.util.Log.w(
                "SettingsManager",
                "AndroidKeyStore is unavailable (${e.javaClass.simpleName}: ${e.message}). Falling back to AES EncryptedSharedPreferences."
            )
            com.example.data.security.AesEncryptedPreferences.create(context, "otakuhub_secure_fallback")
        }
    }

    init {
        // Migrate existing plaintext tokens to EncryptedSharedPreferences if they exist
        val keysToMigrate = listOf(
            "mal_token", "mal_refresh_token", "anilist_token", 
            "kitsu_token", "kitsu_user_id", "firebase_project_id", 
            "firebase_app_id", "firebase_api_key", "google_web_client_id"
        )
        
        var needsCommit = false
        val prefsEditor = prefs.edit()
        val securePrefsEditor = securePrefs.edit()

        for (key in keysToMigrate) {
            if (prefs.contains(key)) {
                val value = prefs.getString(key, null)
                if (value != null) {
                    securePrefsEditor.putString(key, value)
                }
                prefsEditor.remove(key)
                needsCommit = true
            }
        }
        
        if (prefs.contains("mal_expires_at")) {
            val expiresAt = prefs.getLong("mal_expires_at", 0L)
            securePrefsEditor.putLong("mal_expires_at", expiresAt)
            prefsEditor.remove("mal_expires_at")
            needsCommit = true
        }

        if (needsCommit) {
            securePrefsEditor.apply()
            prefsEditor.apply()
        }
    }

    var hubMode: String
        get() = prefs.getString("hub_mode", "COMIC") ?: "COMIC"
        set(value) = prefs.edit().putString("hub_mode", value).apply()

    var isPagedMode: Boolean
        get() = prefs.getBoolean("is_paged_mode", false)
        set(value) = prefs.edit().putBoolean("is_paged_mode", value).apply()

    var isRtl: Boolean
        get() = prefs.getBoolean("is_rtl", false)
        set(value) = prefs.edit().putBoolean("is_rtl", value).apply()

    var cropScale: String
        get() = prefs.getString("crop_scale", "FIT") ?: "FIT"
        set(value) = prefs.edit().putString("crop_scale", value).apply()

    var isIncognito: Boolean
        get() = prefs.getBoolean("is_incognito", false)
        set(value) = prefs.edit().putBoolean("is_incognito", value).apply()

    var isDynamicColor: Boolean
        get() = prefs.getBoolean("is_dynamic_color", true)
        set(value) = prefs.edit().putBoolean("is_dynamic_color", value).apply()

    var isDoublePageMode: Boolean
        get() = prefs.getBoolean("is_double_page_mode", false)
        set(value) = prefs.edit().putBoolean("is_double_page_mode", value).apply()

    var separateCoverPage: Boolean
        get() = prefs.getBoolean("separate_cover_page", true)
        set(value) = prefs.edit().putBoolean("separate_cover_page", value).apply()

    var animeShaderMode: String
        get() = prefs.getString("anime_shader_mode", "ANIME_HDR") ?: "ANIME_HDR"
        set(value) = prefs.edit().putString("anime_shader_mode", value).apply()

    var animeShaderIntensity: Float
        get() = prefs.getFloat("anime_shader_intensity", 1.0f)
        set(value) = prefs.edit().putFloat("anime_shader_intensity", value).apply()

    var backgroundColor: String
        get() = prefs.getString("background_color", "BLACK") ?: "BLACK"
        set(value) = prefs.edit().putString("background_color", value).apply()

    var customBrightness: Float
        get() = prefs.getFloat("custom_brightness", -1f)
        set(value) = prefs.edit().putFloat("custom_brightness", value).apply()

    var darkMode: Int // 0 = System, 1 = Light, 2 = Dark
        get() = prefs.getInt("dark_mode", 0)
        set(value) = prefs.edit().putInt("dark_mode", value).apply()

    var autoDownloadNext: Int
        get() = prefs.getInt("auto_download_next", 0)
        set(value) = prefs.edit().putInt("auto_download_next", value).apply()

    var autoDownloadOnlyOnWifi: Boolean
        get() = prefs.getBoolean("auto_download_only_on_wifi", true)
        set(value) = prefs.edit().putBoolean("auto_download_only_on_wifi", value).apply()

    var backgroundAudio: Boolean
        get() = prefs.getBoolean("background_audio", false)
        set(value) = prefs.edit().putBoolean("background_audio", value).apply()

    var autoDeleteRead: Boolean
        get() = prefs.getBoolean("auto_delete_read", false)
        set(value) = prefs.edit().putBoolean("auto_delete_read", value).apply()

    var playerSpeed: Float
        get() = prefs.getFloat("player_speed", 1.0f)
        set(value) = prefs.edit().putFloat("player_speed", value).apply()

    var subtitleFontSize: Float
        get() = prefs.getFloat("subtitle_font_size", 18f)
        set(value) = prefs.edit().putFloat("subtitle_font_size", value).apply()

    var subtitleTextColor: Int
        get() = prefs.getInt("subtitle_text_color", android.graphics.Color.WHITE)
        set(value) = prefs.edit().putInt("subtitle_text_color", value).apply()

    var subtitleBgColor: Int
        get() = prefs.getInt("subtitle_bg_color", android.graphics.Color.argb(160, 0, 0, 0))
        set(value) = prefs.edit().putInt("subtitle_bg_color", value).apply()

    var libraryUpdateInterval: Int
        get() = prefs.getInt("library_update_interval", 6)
        set(value) = prefs.edit().putInt("library_update_interval", value).apply()

    var autoBackupInterval: Int
        get() = prefs.getInt("auto_backup_interval", 0)
        set(value) = prefs.edit().putInt("auto_backup_interval", value).apply()

    var autoMigrationEnabled: Boolean
        get() = prefs.getBoolean("auto_migration_enabled", true)
        set(value) = prefs.edit().putBoolean("auto_migration_enabled", value).apply()

    var autoMigrationIntervalHours: Int
        get() = prefs.getInt("auto_migration_interval_hours", 24)
        set(value) = prefs.edit().putInt("auto_migration_interval_hours", value).apply()

    var autoUpdateExtensionsEnabled: Boolean
        get() = prefs.getBoolean("auto_update_extensions_enabled", true)
        set(value) = prefs.edit().putBoolean("auto_update_extensions_enabled", value).apply()

    var autoUpdateExtensionsIntervalHours: Int
        get() = prefs.getInt("auto_update_extensions_interval_hours", 12)
        set(value) = prefs.edit().putInt("auto_update_extensions_interval_hours", value).apply()

    var autoDownloadExtensionUpdates: Boolean
        get() = prefs.getBoolean("auto_download_extension_updates", true)
        set(value) = prefs.edit().putBoolean("auto_download_extension_updates", value).apply()

    var silentExtensionInstall: Boolean
        get() = prefs.getBoolean("silent_extension_install", true)
        set(value) = prefs.edit().putBoolean("silent_extension_install", value).apply()

    var lastExtensionUpdateCheckTime: Long
        get() = prefs.getLong("last_extension_update_check_time", 0L)
        set(value) = prefs.edit().putLong("last_extension_update_check_time", value).apply()

    var lastExtensionUpdateCheckResult: String
        get() = prefs.getString("last_extension_update_check_result", "") ?: ""
        set(value) = prefs.edit().putString("last_extension_update_check_result", value).apply()

    var malToken: String
        get() = securePrefs.getString("mal_token", "") ?: ""
        set(value) = securePrefs.edit().putString("mal_token", value).apply()

    var malRefreshToken: String
        get() = securePrefs.getString("mal_refresh_token", "") ?: ""
        set(value) = securePrefs.edit().putString("mal_refresh_token", value).apply()

    var malExpiresAt: Long
        get() = securePrefs.getLong("mal_expires_at", 0L)
        set(value) = securePrefs.edit().putLong("mal_expires_at", value).apply()

    var anilistToken: String
        get() = securePrefs.getString("anilist_token", "") ?: ""
        set(value) = securePrefs.edit().putString("anilist_token", value).apply()

    var kitsuToken: String
        get() = securePrefs.getString("kitsu_token", "") ?: ""
        set(value) = securePrefs.edit().putString("kitsu_token", value).apply()

    var kitsuUserId: String
        get() = securePrefs.getString("kitsu_user_id", "") ?: ""
        set(value) = securePrefs.edit().putString("kitsu_user_id", value).apply()


    var firebaseProjectId: String
        get() = securePrefs.getString("firebase_project_id", "") ?: ""
        set(value) = securePrefs.edit().putString("firebase_project_id", value).apply()

    var firebaseAppId: String
        get() = securePrefs.getString("firebase_app_id", "") ?: ""
        set(value) = securePrefs.edit().putString("firebase_app_id", value).apply()

    var firebaseApiKey: String
        get() = securePrefs.getString("firebase_api_key", "") ?: ""
        set(value) = securePrefs.edit().putString("firebase_api_key", value).apply()

    var googleWebClientId: String
        get() = securePrefs.getString("google_web_client_id", "") ?: ""
        set(value) = securePrefs.edit().putString("google_web_client_id", value).apply()

    var syncEnabled: Boolean
        get() = prefs.getBoolean("sync_enabled", false)
        set(value) = prefs.edit().putBoolean("sync_enabled", value).apply()
    var orientationLock: Int
        get() = prefs.getInt("orientation_lock", -1) // -1 = Unspecified, 1 = Portrait, 0 = Landscape
        set(value) = prefs.edit().putInt("orientation_lock", value).apply()

    var cropBorders: Boolean
        get() = prefs.getBoolean("crop_borders", false)
        set(value) = prefs.edit().putBoolean("crop_borders", value).apply()

    var downloadStorageUri: String
        get() = prefs.getString("download_storage_uri", "") ?: ""
        set(value) = prefs.edit().putString("download_storage_uri", value).apply()

    var galleryIsolationEnabled: Boolean
        get() = prefs.getBoolean("gallery_isolation_enabled", true)
        set(value) = prefs.edit().putBoolean("gallery_isolation_enabled", value).apply()

    var fileObfuscationEnabled: Boolean
        get() = prefs.getBoolean("file_obfuscation_enabled", true)
        set(value) = prefs.edit().putBoolean("file_obfuscation_enabled", value).apply()

    var autoSkipOpEd: Boolean
        get() = prefs.getBoolean("auto_skip_op_ed", false)
        set(value) = prefs.edit().putBoolean("auto_skip_op_ed", value).apply()

    var audioOnlyMode: Boolean
        get() = prefs.getBoolean("audio_only_mode", false)
        set(value) = prefs.edit().putBoolean("audio_only_mode", value).apply()

    var autoPipOnHome: Boolean
        get() = prefs.getBoolean("auto_pip_on_home", true)
        set(value) = prefs.edit().putBoolean("auto_pip_on_home", value).apply()

    var enableSmartPanel: Boolean
        get() = prefs.getBoolean("enable_smart_panel", false)
        set(value) = prefs.edit().putBoolean("enable_smart_panel", value).apply()

    var autoTrackerSync: Boolean
        get() = prefs.getBoolean("auto_tracker_sync", true)
        set(value) = prefs.edit().putBoolean("auto_tracker_sync", value).apply()

    var isWebCompanionEnabled: Boolean
        get() = prefs.getBoolean("is_web_companion_enabled", false)
        set(value) = prefs.edit().putBoolean("is_web_companion_enabled", value).apply()

    var webCompanionPort: Int
        get() = prefs.getInt("web_companion_port", 8080)
        set(value) = prefs.edit().putInt("web_companion_port", value).apply()
}
