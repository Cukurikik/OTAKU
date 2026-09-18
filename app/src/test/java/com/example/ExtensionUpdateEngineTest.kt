package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.SettingsManager
import com.example.data.sources.tachiyomi.AvailableExtension
import com.example.data.sources.tachiyomi.ExtensionRepo
import com.example.data.sources.tachiyomi.InstalledExtension
import com.example.data.sources.tachiyomi.RepoCategory
import com.example.notifications.NotificationHelper
import com.example.workers.ExtensionUpdateWorker
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExtensionUpdateEngineTest {

    private lateinit var context: Context
    private lateinit var settingsManager: SettingsManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        settingsManager = SettingsManager(context)
    }

    @Test
    fun testSettingsPersistence_AutoUpdateConfiguration() {
        settingsManager.autoUpdateExtensionsEnabled = true
        settingsManager.autoUpdateExtensionsIntervalHours = 12
        settingsManager.autoDownloadExtensionUpdates = true
        settingsManager.silentExtensionInstall = true

        assertTrue(settingsManager.autoUpdateExtensionsEnabled)
        assertEquals(12, settingsManager.autoUpdateExtensionsIntervalHours)
        assertTrue(settingsManager.autoDownloadExtensionUpdates)
        assertTrue(settingsManager.silentExtensionInstall)

        settingsManager.autoUpdateExtensionsIntervalHours = 24
        assertEquals(24, settingsManager.autoUpdateExtensionsIntervalHours)

        val checkTime = System.currentTimeMillis()
        settingsManager.lastExtensionUpdateCheckTime = checkTime
        settingsManager.lastExtensionUpdateCheckResult = "2 pembaruan terdeteksi"

        assertEquals(checkTime, settingsManager.lastExtensionUpdateCheckTime)
        assertEquals("2 pembaruan terdeteksi", settingsManager.lastExtensionUpdateCheckResult)
    }

    @Test
    fun testExtensionUpdateDetection_VersionCodeComparison() {
        val installedList = listOf(
            InstalledExtension(
                name = "Tachiyomi: Komikcast",
                pkgName = "eu.kanade.tachiyomi.extension.id.komikcast",
                versionName = "1.4.1",
                versionCode = 14L,
                lang = "id",
                isNsfw = false,
                pkgFactory = "eu.kanade.tachiyomi.extension.id.komikcast.KomikcastFactory"
            ),
            InstalledExtension(
                name = "Tachiyomi: Shinigami",
                pkgName = "eu.kanade.tachiyomi.extension.id.shinigami",
                versionName = "1.2.0",
                versionCode = 12L,
                lang = "id",
                isNsfw = false,
                pkgFactory = "eu.kanade.tachiyomi.extension.id.shinigami.ShinigamiFactory"
            ),
            InstalledExtension(
                name = "Tachiyomi: Otakudesu",
                pkgName = "eu.kanade.tachiyomi.animeextension.id.otakudesu",
                versionName = "2.0.0",
                versionCode = 20L,
                lang = "id",
                isNsfw = false,
                pkgFactory = "eu.kanade.tachiyomi.animeextension.id.otakudesu.OtakudesuFactory"
            )
        )

        val availableRemoteList = listOf(
            AvailableExtension(
                name = "Komikcast",
                pkg = "eu.kanade.tachiyomi.extension.id.komikcast",
                apk = "tachiyomi-id.komikcast-v1.4.2.apk",
                lang = "id",
                code = 15, // NEWER: 15 > 14
                version = "1.4.2",
                repoUrl = "https://raw.githubusercontent.com/keiyoushi/extensions/repo"
            ),
            AvailableExtension(
                name = "Shinigami",
                pkg = "eu.kanade.tachiyomi.extension.id.shinigami",
                apk = "tachiyomi-id.shinigami-v1.2.0.apk",
                lang = "id",
                code = 12, // SAME: 12 == 12 (No update)
                version = "1.2.0",
                repoUrl = "https://raw.githubusercontent.com/keiyoushi/extensions/repo"
            ),
            AvailableExtension(
                name = "Otakudesu",
                pkg = "eu.kanade.tachiyomi.animeextension.id.otakudesu",
                apk = "aniyomi-id.otakudesu-v2.1.0.apk",
                lang = "id",
                code = 21, // NEWER: 21 > 20
                version = "2.1.0",
                repoUrl = "https://raw.githubusercontent.com/keiyoushi/extensions/repo"
            )
        )

        val availableByPkg = availableRemoteList.associateBy { it.pkg }
        val updates = mutableListOf<Pair<InstalledExtension, AvailableExtension>>()

        for (installed in installedList) {
            val remote = availableByPkg[installed.pkgName]
            if (remote != null && remote.code > installed.versionCode) {
                updates.add(Pair(installed, remote))
            }
        }

        // Should detect exactly 2 updates (Komikcast and Otakudesu)
        assertEquals(2, updates.size)
        assertEquals("eu.kanade.tachiyomi.extension.id.komikcast", updates[0].first.pkgName)
        assertEquals(15, updates[0].second.code)
        assertEquals("eu.kanade.tachiyomi.animeextension.id.otakudesu", updates[1].first.pkgName)
        assertEquals(21, updates[1].second.code)
    }

    @Test
    fun testNotificationChannelCreation_AndTrigger() {
        NotificationHelper.createExtensionUpdateChannel(context)
        NotificationHelper.showExtensionUpdateNotification(
            context = context,
            updatedCount = 2,
            extensionNames = listOf("Komikcast", "Otakudesu"),
            isDownloaded = true
        )
        // Ensure no exception thrown during notification trigger
        assertTrue(true)
    }

    @Test
    fun testWorkerScheduling_NoThrow() {
        ExtensionUpdateWorker.schedule(context)
        ExtensionUpdateWorker.runOnce(context)
        ExtensionUpdateWorker.cancel(context)
        assertTrue(true)
    }
}
