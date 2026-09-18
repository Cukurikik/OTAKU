package com.example.data.sync

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.example.data.SettingsManager
import com.example.OtakuHubApp
import com.example.data.models.HistoryItem
import com.example.data.models.LibraryEntry
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object CloudSyncManager {

    private const val TAG = "CloudSyncManager"
    var isInitialized = false

    suspend fun getAppCheckToken(forceRefresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        try {
            if (FirebaseApp.getApps(OtakuHubApp.instance).isEmpty()) return@withContext null
            val appCheck = FirebaseAppCheck.getInstance()
            val tokenResult = appCheck.getAppCheckToken(forceRefresh).await()
            tokenResult?.token
        } catch (e: Exception) {
            Log.w(TAG, "AppCheck token async fetch error: ${e.message}")
            null
        }
    }

    fun getAppCheckTokenSync(forceRefresh: Boolean = false): String? {
        return try {
            if (FirebaseApp.getApps(OtakuHubApp.instance).isEmpty()) return null
            val appCheck = FirebaseAppCheck.getInstance()
            val task = appCheck.getAppCheckToken(forceRefresh)
            Tasks.await(task, 3, TimeUnit.SECONDS)?.token
        } catch (e: Exception) {
            Log.w(TAG, "AppCheck token sync fetch error: ${e.message}")
            null
        }
    }

    fun initialize(context: Context, settingsManager: SettingsManager) {
        val projectId = settingsManager.firebaseProjectId
        val appId = settingsManager.firebaseAppId
        val apiKey = settingsManager.firebaseApiKey
        
        if (projectId.isBlank() || appId.isBlank() || apiKey.isBlank()) {
            Log.w(TAG, "Firebase credentials are empty, skipping initialization.")
            isInitialized = false
            return
        }
        
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setProjectId(projectId)
                    .setApplicationId(appId)
                    .setApiKey(apiKey)
                    .build()
                FirebaseApp.initializeApp(context, options)
            }
            isInitialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase: ${e.message}")
            isInitialized = false
        }
    }

    suspend fun signInWithGoogle(context: Context): FirebaseUser? = withContext(Dispatchers.IO) {
        if (!isInitialized) throw Exception("Firebase belum diinisialisasi")
        
        val settingsManager = SettingsManager(context)
        val webClientId = settingsManager.googleWebClientId
        if (webClientId.isEmpty()) throw Exception("Google Web Client ID kosong")

        val credentialManager = CredentialManager.create(context)
        
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is GoogleIdTokenCredential) {
                val idToken = credential.idToken
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = FirebaseAuth.getInstance().signInWithCredential(authCredential).await()
                return@withContext authResult.user
            } else {
                throw Exception("Unexpected credential type: ${credential.type}")
            }
        } catch (e: androidx.credentials.exceptions.GetCredentialException) {
            Log.e(TAG, "Google Sign-In failed due to GetCredentialException", e)
            throw Exception("Google Sign-In gagal. Pastikan SHA-1 terdaftar di Firebase Console. (${e.message})")
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            throw e
        }
    }

    suspend fun syncNow(context: Context) = withContext(Dispatchers.IO) {
        if (!isInitialized) throw Exception("Firebase belum diinisialisasi")
        val user = FirebaseAuth.getInstance().currentUser ?: throw Exception("Belum login. Silakan login terlebih dahulu.")
        
        val db = FirebaseFirestore.getInstance()
        val localDb = OtakuHubApp.instance.database
        
        // --- 1. SINKRONISASI LIBRARY ---
        val libraryRef = db.collection("users").document(user.uid).collection("library")
        val localLibrary = localDb.libraryDao().getAllEntriesSync(com.example.data.ProfileManager.currentProfileIdValue)
        val localLibraryMap = localLibrary.associateBy { "${it.sourceId}_${it.mangaId}".replace("/", "_") }

        // Fetch cloud library first
        val cloudLibrarySnapshot = libraryRef.get().await()
        val cloudLibraryMap = mutableMapOf<String, LibraryEntry>()
        for (doc in cloudLibrarySnapshot.documents) {
            val cloudEntry = doc.toObject(LibraryEntry::class.java)
            if (cloudEntry != null) {
                cloudLibraryMap[doc.id] = cloudEntry
            }
        }

        // Resolve conflicts: Last-Write-Wins
        val allLibraryKeys = localLibraryMap.keys + cloudLibraryMap.keys
        for (key in allLibraryKeys) {
            val localEntry = localLibraryMap[key]
            val cloudEntry = cloudLibraryMap[key]

            if (localEntry != null && cloudEntry != null) {
                if (localEntry.lastUpdated > cloudEntry.lastUpdated) {
                    libraryRef.document(key).set(localEntry).await()
                } else if (cloudEntry.lastUpdated > localEntry.lastUpdated) {
                    localDb.libraryDao().insertLibraryEntry(cloudEntry)
                }
            } else if (localEntry != null) {
                libraryRef.document(key).set(localEntry).await()
            } else if (cloudEntry != null) {
                localDb.libraryDao().insertLibraryEntry(cloudEntry)
            }
        }

        // --- 2. SINKRONISASI HISTORY ---
        val historyRef = db.collection("users").document(user.uid).collection("history")
        val localHistory = localDb.historyDao().getAllHistorySync(com.example.data.ProfileManager.currentProfileIdValue)
        val localHistoryMap = localHistory.associateBy { "${it.sourceId}_${it.id}".replace("/", "_") }
        
        // Fetch cloud history first
        val cloudHistorySnapshot = historyRef.get().await()
        val cloudHistoryMap = mutableMapOf<String, HistoryItem>()
        for (doc in cloudHistorySnapshot.documents) {
            val cloudEntry = doc.toObject(HistoryItem::class.java)
            if (cloudEntry != null) {
                cloudHistoryMap[doc.id] = cloudEntry
            }
        }

        // Resolve conflicts: Last-Write-Wins
        val allHistoryKeys = localHistoryMap.keys + cloudHistoryMap.keys
        for (key in allHistoryKeys) {
            val localEntry = localHistoryMap[key]
            val cloudEntry = cloudHistoryMap[key]

            if (localEntry != null && cloudEntry != null) {
                if (localEntry.lastUpdated > cloudEntry.lastUpdated) {
                    historyRef.document(key).set(localEntry).await()
                } else if (cloudEntry.lastUpdated > localEntry.lastUpdated) {
                    localDb.historyDao().insertHistory(cloudEntry)
                }
            } else if (localEntry != null) {
                historyRef.document(key).set(localEntry).await()
            } else if (cloudEntry != null) {
                localDb.historyDao().insertHistory(cloudEntry)
            }
        }
    }
}
