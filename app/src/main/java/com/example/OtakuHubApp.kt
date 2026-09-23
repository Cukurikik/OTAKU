package com.example

import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers


import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.room.Room
import com.example.data.db.AppDatabase
import com.example.data.ContentRepository
import com.example.data.sources.tachiyomi.ExtensionManager

import coil.ImageLoaderFactory
import coil.ImageLoader

class OtakuHubApp : Application(), ImageLoaderFactory {
    lateinit var database: AppDatabase
    lateinit var repository: ContentRepository
    
    lateinit var trackerManager: com.example.data.trackers.TrackerManager
    lateinit var pendingSyncQueue: com.example.data.sync.PendingSyncQueue

    val settingsManager: com.example.data.SettingsManager by lazy { com.example.data.SettingsManager(this) }
    val secureCredentialStore: com.example.data.scrobbler.SecureCredentialStore by lazy {
        com.example.data.scrobbler.SecureCredentialStoreImpl(this, settingsManager)
    }
    val scrobblerEngine: com.example.data.scrobbler.ScrobblerEngine by lazy {
        com.example.data.scrobbler.ScrobblerEngine(this, secureCredentialStore, trackerManager)
    }
    val twoWaySyncEngine: com.example.data.scrobbler.TwoWaySyncEngine by lazy {
        com.example.data.scrobbler.TwoWaySyncEngine(this, secureCredentialStore, trackerManager)
    }

    companion object {
        lateinit var instance: OtakuHubApp
            private set
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            val action = intent?.action
            val dataString = intent?.dataString ?: return
            if (dataString.startsWith("package:eu.kanade.tachiyomi.extension.") || 
                dataString.startsWith("package:com.example.extension.") ||
                dataString.startsWith("package:eu.kanade.tachiyomi.aniyomi.") ||
                dataString.startsWith("package:eu.kanade.tachiyomi.animeextension.") ||
                dataString.startsWith("package:xyz.nulldev.tachiyomi.extension.")) {
                reloadDynamicExtensions()
                ExtensionManager.notifyPackageChanged()
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return CoilConfig(this).newImageLoader()
    }

    override fun onCreate() {
        super.onCreate()
        com.example.util.WebViewHelper.initWebViewEnvironment(this)
        try { com.example.data.NetworkModule.userAgent = android.webkit.WebSettings.getDefaultUserAgent(this) } catch(e: Exception) {}
        instance = this

        val dbName = "otaku-hub-db"
        com.example.data.db.DatabaseSecurityManager.migratePlaintextDatabaseIfNeeded(this, dbName)
        val sqlCipherFactory = com.example.data.db.DatabaseSecurityManager.createSupportFactory(this)

        val dbBuilder = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, dbName
        ).addMigrations(
            com.example.data.db.AppDatabase.MIGRATION_1_2, 
            com.example.data.db.AppDatabase.MIGRATION_2_3, 
            com.example.data.db.AppDatabase.MIGRATION_3_4,
            com.example.data.db.AppDatabase.MIGRATION_4_5,
            com.example.data.db.AppDatabase.MIGRATION_5_6,
            com.example.data.db.AppDatabase.MIGRATION_6_7,
            com.example.data.db.AppDatabase.MIGRATION_7_8,
            com.example.data.db.AppDatabase.MIGRATION_8_9, 
            AppDatabase.MIGRATION_9_10, 
            AppDatabase.MIGRATION_10_11,
            AppDatabase.MIGRATION_11_12,
            AppDatabase.MIGRATION_12_13,
            com.example.data.db.AppDatabase.MIGRATION_13_14,
            com.example.data.db.AppDatabase.MIGRATION_14_15,
            com.example.data.db.AppDatabase.MIGRATION_15_16,
            com.example.data.db.AppDatabase.MIGRATION_16_17
        )

        if (sqlCipherFactory != null) {
            dbBuilder.openHelperFactory(sqlCipherFactory)
        }

        database = dbBuilder.build()
        com.example.data.ProfileManager.init(this)
        repository = ContentRepository(com.example.data.NetworkModule.okHttpClient)
        trackerManager = com.example.data.trackers.TrackerManager(this, com.example.data.SettingsManager(this))
        pendingSyncQueue = com.example.data.sync.PendingSyncQueue(database.pendingSyncsDao(), trackerManager, this)
        com.example.data.sync.PendingSyncQueue.registerNetworkMonitor(this) { pendingSyncQueue }
        
        // Inisialisasi Firebase & Firebase App Check (Play Integrity dengan fallback Debug)
        try {
            if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(this)
            }
            val firebaseAppCheck = com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
            try {
                firebaseAppCheck.installAppCheckProviderFactory(
                    com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory.getInstance()
                )
            } catch (_: Exception) {
                try {
                    firebaseAppCheck.installAppCheckProviderFactory(
                        com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory.getInstance()
                    )
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            android.util.Log.w("OtakuHubApp", "Firebase AppCheck init warning: ${e.message}")
        }

        // Inisialisasi Firebase untuk sinkronisasi
        com.example.data.sync.CloudSyncManager.initialize(this, com.example.data.SettingsManager(this))
        
        com.example.data.downloads.ExoDownloadManager.getManager(this)
            .addListener(com.example.data.downloads.MediaDownloadListener(this))

        // Inisialisasi Dynamic Source OTA Configuration
        com.example.data.sources.dynamic.DynamicSourceConfigManager.init(this)
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                com.example.data.sources.dynamic.DynamicSourceConfigManager.syncRemoteConfigs(this@OtakuHubApp)
            } catch (_: Exception) {}
        }

        reloadDynamicExtensions()

        // Register package installation/uninstallation listener dynamically
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)
        try {
            com.example.notifications.NotificationHelper.createNotificationChannel(this)
            com.example.notifications.NotificationHelper.createAutoMigrationChannel(this)
            com.example.notifications.NotificationHelper.createExtensionUpdateChannel(this)
            com.example.workers.LibraryUpdateWorker.schedule(this)
            com.example.workers.AutoMigrationWorker.schedule(this)
            com.example.workers.ExtensionUpdateWorker.schedule(this)
            com.example.workers.CacheJanitorWorker.schedule(this)
            com.example.data.scrobbler.ScrobbleSyncWorker.schedulePeriodic(this, 15L)
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                try {
                    com.example.storage.StoragePathManager.applyNoMediaToAll(this@OtakuHubApp)
                } catch (_: Exception) {}
                com.example.workers.CacheJanitor.performCleanup(this@OtakuHubApp, database)
            }
        } catch (e: Exception) {
            // Ignore in test environments
        }
    }

fun reloadDynamicExtensions() {
        val staticSources = listOf(
            com.example.data.sources.anilist.AniListSource(this), com.example.data.sources.local.LocalContentSource(this),
            com.example.data.sources.torrent.TorrentSource(this),
            
            
            
            
            
            
            com.example.data.sources.mangadex.MangaDexSource()
        ) + listOf(
            com.example.data.sources.anime.specific.KuramanimeScraper(this),
            com.example.data.sources.anime.specific.AnimasuScraper(this),
            com.example.data.sources.anime.specific.OtakudesuScraper(this),
            com.example.data.sources.anime.specific.KusonimeScraper(this),
            com.example.data.sources.anime.specific.MeownimeScraper(this),
            com.example.data.sources.anime.specific.AnoboyScraper(this),
            com.example.data.sources.anime.specific.AnichinScraper(this),
            com.example.data.sources.anime.specific.KazefuriScraper(this),
            
            // AnimeStream Theme Scrapers
            com.example.data.sources.anime.specific.AnimasuMovieScraper(this),
            com.example.data.sources.anime.specific.NimegamiScraper(this),
            com.example.data.sources.anime.specific.AnimeMoviesScraper(this),
            com.example.data.sources.anime.specific.StuckNimeScraper(this),
            com.example.data.sources.anime.specific.YlNimeScraper(this),
            com.example.data.sources.anime.specific.AnimeKuIndoScraper(this),
            com.example.data.sources.anime.specific.WibukuScraper(this),
            com.example.data.sources.anime.specific.MyAnimeKuScraper(this),
            com.example.data.sources.anime.specific.KuronimeScraper(this),
            com.example.data.sources.anime.specific.AnimeIndoScraper(this),
            com.example.data.sources.anime.specific.AnimasuIDScraper(this),
            com.example.data.sources.anime.specific.Anime154Scraper(this),
            com.example.data.sources.anime.specific.MeownimeNetScraper(this),
            com.example.data.sources.anime.specific.NontonAnimeIDScraper(this),
            com.example.data.sources.anime.specific.OtakupoiScraper(this),
            com.example.data.sources.anime.specific.AnimeKuInfoScraper(this),
            com.example.data.sources.anime.specific.MiranimeScraper(this),
            com.example.data.sources.anime.specific.GomunimeScraper(this),
            com.example.data.sources.anime.specific.AlqanimeScraper(this),
            com.example.data.sources.anime.specific.NimeIndoScraper(this),
            com.example.data.sources.anime.specific.SubnimeScraper(this),

            com.example.data.sources.drama.specific.VikiScraper(this),
            com.example.data.sources.drama.specific.LicilScraper(this),
            com.example.data.sources.drama.specific.LokalfilmScraper(this),
            com.example.data.sources.drama.specific.JucelebrityScraper(this)
        ) + com.example.data.sources.drama.DramaDirectSourceCatalog.sources
            .filter { it.sourceId !in listOf("viki", "licil", "lokalfilm", "jucelebrity") }
            .map { config ->
            com.example.data.sources.drama.GenericDramaScraper(this, config)
        } + com.example.data.sources.direct.ComicDirectSourceCatalog.createSources(this)

        repository.sources = staticSources
        
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val dynamicSources = mutableListOf<com.example.data.sources.ContentSource>()
            try {
                val installed = com.example.data.sources.tachiyomi.ExtensionManager.getInstalledExtensions(this@OtakuHubApp)
                for (ext in installed) {
                    val loaded = com.example.data.sources.tachiyomi.ExtensionManager.loadExtensionSources(this@OtakuHubApp, ext)
                    dynamicSources.addAll(loaded)
                }
                repository.sources = staticSources + dynamicSources
            } catch (e: Exception) {
                // e.printStackTrace()
            }
        }
        
        try {
            com.example.workers.AutoBackupWorker.schedule(this)
        } catch (e: Exception) {
            // Ignore in test environments
        }
    }
}
