package com.example.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity

object NotificationHelper {
    const val CHANNEL_ID = "library_updates_channel"
    const val CHANNEL_NAME = "Library Updates"
    const val GROUP_KEY = "com.example.LIBRARY_UPDATES"
    const val SUMMARY_NOTIFICATION_ID = 99999

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Notifications for new chapters and episodes of bookmarked items"
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    fun showUpdateNotification(
        context: Context,
        title: String,
        message: String,
        itemId: String,
        sourceId: String
    ) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("action", "open_detail")
            putExtra("itemId", itemId)
            putExtra("sourceId", sourceId)
        }

        val notificationId = (sourceId + itemId).hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setGroup(GROUP_KEY)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    NotificationManagerCompat.from(context).notify(notificationId, notification)
                }
            } else {
                NotificationManagerCompat.from(context).notify(notificationId, notification)
            }
        } catch (e: Exception) {
            // Permission issues
        }
    }

    fun showSummaryNotification(context: Context, summaryText: String) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("action", "open_updates")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            SUMMARY_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summaryNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Pembaruan Library")
            .setContentText(summaryText)
            .setStyle(
                NotificationCompat.InboxStyle()
                    .setSummaryText("Pembaruan Library")
                    .setBigContentTitle("Pembaruan Tersedia")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    NotificationManagerCompat.from(context).notify(SUMMARY_NOTIFICATION_ID, summaryNotification)
                }
            } else {
                NotificationManagerCompat.from(context).notify(SUMMARY_NOTIFICATION_ID, summaryNotification)
            }
        } catch (e: Exception) {
            // Permission issues
        }
    }

    const val AUTO_MIGRATION_CHANNEL_ID = "auto_migration_channel"
    const val AUTO_MIGRATION_CHANNEL_NAME = "Pemulihan Link & Migrasi Otomatis"
    const val AUTO_MIGRATION_NOTIFICATION_ID = 88888

    fun createAutoMigrationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_LOW // Silent notification
            val channel = NotificationChannel(
                AUTO_MIGRATION_CHANNEL_ID,
                AUTO_MIGRATION_CHANNEL_NAME,
                importance
            ).apply {
                description = "Notifikasi senyap saat seri yang mengalami broken link/rot otomatis dipindahkan ke sumber alternatif"
                enableLights(false)
                enableVibration(false)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.getNotificationChannel(AUTO_MIGRATION_CHANNEL_ID) == null) {
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    fun showAutoMigrationSilentNotification(
        context: Context,
        migratedCount: Int,
        details: List<String>
    ) {
        if (migratedCount <= 0) return
        createAutoMigrationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("action", "open_library")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            AUTO_MIGRATION_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val titleText = "$migratedCount Komik/Anime otomatis dimigrasi ke sumber baru"
        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(titleText)
            .setSummaryText("Smart Link Rot Resolver")

        details.take(5).forEach { detail ->
            inboxStyle.addLine(detail)
        }
        if (details.size > 5) {
            inboxStyle.addLine("+ ${details.size - 5} seri lainnya...")
        }

        val notification = NotificationCompat.Builder(context, AUTO_MIGRATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(titleText)
            .setContentText(details.firstOrNull() ?: "$migratedCount seri berhasil dipulihkan dari link rot")
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Silent
            .setSound(null)
            .setVibrate(null)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    NotificationManagerCompat.from(context).notify(AUTO_MIGRATION_NOTIFICATION_ID, notification)
                }
            } else {
                NotificationManagerCompat.from(context).notify(AUTO_MIGRATION_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Permission issues
        }
    }

    const val EXTENSION_UPDATE_CHANNEL_ID = "extension_updates_channel"
    const val EXTENSION_UPDATE_CHANNEL_NAME = "Pembaruan Ekstensi Sumber"
    const val EXTENSION_UPDATE_NOTIFICATION_ID = 77777

    fun createExtensionUpdateChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(
                EXTENSION_UPDATE_CHANNEL_ID,
                EXTENSION_UPDATE_CHANNEL_NAME,
                importance
            ).apply {
                description = "Notifikasi saat ekstensi manga/anime memiliki versi baru di repositori"
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.getNotificationChannel(EXTENSION_UPDATE_CHANNEL_ID) == null) {
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    fun showExtensionUpdateNotification(
        context: Context,
        updatedCount: Int,
        extensionNames: List<String>,
        isDownloaded: Boolean = false
    ) {
        if (updatedCount <= 0) return
        createExtensionUpdateChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("action", "open_extensions")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            EXTENSION_UPDATE_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val titleText = if (isDownloaded) {
            "$updatedCount Ekstensi Siap Dipasang"
        } else {
            "$updatedCount Pembaruan Ekstensi Tersedia"
        }

        val subtitleText = if (isDownloaded) {
            "Paket pembaruan telah diunduh ke cache dan siap diperbarui."
        } else {
            "Versi baru terdeteksi di repositori untuk menjaga 99% uptime sumber."
        }

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(titleText)
            .setSummaryText("Auto-Updater Engine")

        extensionNames.take(5).forEach { name ->
            inboxStyle.addLine("• $name")
        }
        if (extensionNames.size > 5) {
            inboxStyle.addLine("+ ${extensionNames.size - 5} ekstensi lainnya...")
        }

        val notification = NotificationCompat.Builder(context, EXTENSION_UPDATE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(titleText)
            .setContentText(subtitleText)
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    NotificationManagerCompat.from(context).notify(EXTENSION_UPDATE_NOTIFICATION_ID, notification)
                }
            } else {
                NotificationManagerCompat.from(context).notify(EXTENSION_UPDATE_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Permission issues
        }
    }
}

