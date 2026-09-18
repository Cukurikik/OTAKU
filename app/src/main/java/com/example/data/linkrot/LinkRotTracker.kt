package com.example.data.linkrot

import android.content.Context
import android.content.SharedPreferences

/**
 * LinkRotTracker: Melacak jumlah kegagalan berturut-turut (consecutive network/HTTP 404/502/DNS failures)
 * untuk setiap item Library dan sumber konten agar dapat memicu Auto-Migration jika mencapai ambang batas (>= 3).
 */
class LinkRotTracker(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "otakuhub_link_rot_tracker"
        private const val KEY_PREFIX_FAIL_COUNT = "fail_cnt_"
        private const val KEY_PREFIX_FAIL_TIME = "fail_time_"
        private const val KEY_PREFIX_FAIL_REASON = "fail_reason_"
        const val DEFAULT_ROT_THRESHOLD = 3

        @Volatile
        private var instance: LinkRotTracker? = null

        fun getInstance(context: Context): LinkRotTracker {
            return instance ?: synchronized(this) {
                instance ?: LinkRotTracker(context.applicationContext).also { instance = it }
            }
        }
    }

    private fun buildKey(sourceId: String, mangaId: String): String {
        return "${sourceId.trim()}:::${mangaId.trim()}"
    }

    /**
     * Mencatat kegagalan akses pada entri. Menambah counter kegagalan berturut-turut.
     * @return jumlah kegagalan berturut-turut saat ini.
     */
    @Synchronized
    fun recordFailure(sourceId: String, mangaId: String, reason: String = ""): Int {
        val compositeKey = buildKey(sourceId, mangaId)
        val current = prefs.getInt(KEY_PREFIX_FAIL_COUNT + compositeKey, 0)
        val updated = current + 1
        prefs.edit()
            .putInt(KEY_PREFIX_FAIL_COUNT + compositeKey, updated)
            .putLong(KEY_PREFIX_FAIL_TIME + compositeKey, System.currentTimeMillis())
            .putString(KEY_PREFIX_FAIL_REASON + compositeKey, reason)
            .apply()
        return updated
    }

    /**
     * Mencatat keberhasilan akses pada entri. Mereset counter kegagalan ke 0.
     */
    @Synchronized
    fun recordSuccess(sourceId: String, mangaId: String) {
        val compositeKey = buildKey(sourceId, mangaId)
        prefs.edit()
            .remove(KEY_PREFIX_FAIL_COUNT + compositeKey)
            .remove(KEY_PREFIX_FAIL_TIME + compositeKey)
            .remove(KEY_PREFIX_FAIL_REASON + compositeKey)
            .apply()
    }

    /**
     * Mendapatkan jumlah kegagalan berturut-turut pada entri.
     */
    fun getFailureCount(sourceId: String, mangaId: String): Int {
        val compositeKey = buildKey(sourceId, mangaId)
        return prefs.getInt(KEY_PREFIX_FAIL_COUNT + compositeKey, 0)
    }

    /**
     * Mengecek apakah entri terdeteksi mengalami Link Rot (kegagalan >= ambang batas).
     */
    fun isLinkRot(sourceId: String, mangaId: String, threshold: Int = DEFAULT_ROT_THRESHOLD): Boolean {
        return getFailureCount(sourceId, mangaId) >= threshold
    }

    /**
     * Mendapatkan alasan kegagalan terakhir untuk keperluan audit dan logging.
     */
    fun getLastFailureReason(sourceId: String, mangaId: String): String? {
        val compositeKey = buildKey(sourceId, mangaId)
        return prefs.getString(KEY_PREFIX_FAIL_REASON + compositeKey, null)
    }

    /**
     * Membersihkan rekaman entri tertentu (misalnya setelah berhasil dimigrasikan ke sumber baru).
     */
    @Synchronized
    fun clearEntry(sourceId: String, mangaId: String) {
        recordSuccess(sourceId, mangaId)
    }

    /**
     * Mendapatkan daftar semua compositeKey yang saat ini memiliki failure count > 0.
     */
    fun getAllFailingKeys(): Map<String, Int> {
        val all = prefs.all
        val result = mutableMapOf<String, Int>()
        for ((key, value) in all) {
            if (key.startsWith(KEY_PREFIX_FAIL_COUNT) && value is Int && value > 0) {
                val composite = key.removePrefix(KEY_PREFIX_FAIL_COUNT)
                result[composite] = value
            }
        }
        return result
    }

    /**
     * Mereset seluruh rekaman link rot.
     */
    @Synchronized
    fun resetAll() {
        prefs.edit().clear().apply()
    }
}
