package com.example.data.scrobbler

import android.content.Context
import com.example.data.SettingsManager
import com.example.ui.screens.refreshMalOAuthToken

/**
 * Secure Credential Store for Auto-Scrobbler Engine.
 * Manages encrypted OAuth2 and API tokens for MyAnimeList, AniList, and Kitsu.
 */
interface SecureCredentialStore {
    fun getMalToken(): String
    fun getMalRefreshToken(): String
    fun getMalExpiresAt(): Long
    fun saveMalToken(accessToken: String, refreshToken: String, expiresInSec: Long)

    fun getAnilistToken(): String
    fun saveAnilistToken(token: String)

    fun getKitsuToken(): String
    fun getKitsuUserId(): String
    fun saveKitsuAuth(token: String, userId: String)

    fun isLogged(platform: ScrobblePlatform): Boolean
    fun logout(platform: ScrobblePlatform)
    fun getAllLoggedPlatforms(): List<ScrobblePlatform>

    suspend fun getOrRefreshMalToken(): String?
}

class SecureCredentialStoreImpl(
    private val context: Context,
    private val settings: SettingsManager
) : SecureCredentialStore {

    private val malClientId = "6114d00ca681b7701d1e15fe11a4987e"

    override fun getMalToken(): String = settings.malToken

    override fun getMalRefreshToken(): String = settings.malRefreshToken

    override fun getMalExpiresAt(): Long = settings.malExpiresAt

    override fun saveMalToken(accessToken: String, refreshToken: String, expiresInSec: Long) {
        settings.malToken = accessToken
        if (refreshToken.isNotEmpty()) {
            settings.malRefreshToken = refreshToken
        }
        if (expiresInSec > 0) {
            settings.malExpiresAt = System.currentTimeMillis() + (expiresInSec * 1000L)
        }
    }

    override fun getAnilistToken(): String = settings.anilistToken

    override fun saveAnilistToken(token: String) {
        settings.anilistToken = token
    }

    override fun getKitsuToken(): String = settings.kitsuToken

    override fun getKitsuUserId(): String = settings.kitsuUserId

    override fun saveKitsuAuth(token: String, userId: String) {
        settings.kitsuToken = token
        settings.kitsuUserId = userId
    }

    override fun isLogged(platform: ScrobblePlatform): Boolean {
        return when (platform) {
            ScrobblePlatform.MY_ANIME_LIST -> getMalToken().isNotEmpty()
            ScrobblePlatform.ANI_LIST -> getAnilistToken().isNotEmpty()
            ScrobblePlatform.KITSU -> getKitsuToken().isNotEmpty()
        }
    }

    override fun logout(platform: ScrobblePlatform) {
        when (platform) {
            ScrobblePlatform.MY_ANIME_LIST -> {
                settings.malToken = ""
                settings.malRefreshToken = ""
                settings.malExpiresAt = 0L
            }
            ScrobblePlatform.ANI_LIST -> {
                settings.anilistToken = ""
            }
            ScrobblePlatform.KITSU -> {
                settings.kitsuToken = ""
                settings.kitsuUserId = ""
            }
        }
    }

    override fun getAllLoggedPlatforms(): List<ScrobblePlatform> {
        return ScrobblePlatform.values().filter { isLogged(it) }
    }

    override suspend fun getOrRefreshMalToken(): String? {
        val currentToken = getMalToken()
        if (currentToken.isEmpty()) return null

        val refreshToken = getMalRefreshToken()
        val expiresAt = getMalExpiresAt()

        // If expires in less than 60 seconds and we have refresh token, refresh it
        if (expiresAt > 0 && expiresAt <= System.currentTimeMillis() + 60_000L && refreshToken.isNotEmpty()) {
            val refreshed = refreshMalOAuthToken(malClientId, refreshToken)
            if (refreshed != null) {
                saveMalToken(refreshed.accessToken, refreshed.refreshToken, refreshed.expiresIn)
                return refreshed.accessToken
            }
        }
        return currentToken
    }
}
