#!/bin/bash
awk '
BEGIN { in_do_work = 0 }
/override suspend fun doWork\(\): Result \{/ {
    in_do_work = 1
    print "    override suspend fun doWork(): Result {"
    print "        val db = (applicationContext as com.example.OtakuHubApp).database"
    print "        val pendingSyncs = db.pendingSyncsDao().getAllSyncs()"
    print "        "
    print "        if (pendingSyncs.isEmpty()) return Result.success()"
    print "        "
    print "        val settingsManager = SettingsManager(applicationContext)"
    print "        var hasFailures = false"
    print "        "
    print "        for (sync in pendingSyncs) {"
    print "            val historyItem = db.historyDao().getHistoryItemSync(sync.profileId, sync.itemId)"
    print "            val isAnime = historyItem?.type == ContentType.ANIME || historyItem?.type == ContentType.DRAMA"
    print "            "
    print "            val result = when (sync.trackerId) {"
    print "                \"kitsu\" -> syncKitsu(sync.remoteId, isAnime, sync.progress, settingsManager)"
    print "                \"anilist\" -> syncAnilist(sync.remoteId, sync.progress, settingsManager)"
    print "                \"myanimelist\" -> syncMal(sync.remoteId, isAnime, sync.progress, settingsManager)"
    print "                else -> Result.failure()"
    print "            }"
    print "            "
    print "            if (result == Result.success() || result == Result.failure()) {"
    print "                db.pendingSyncsDao().delete(sync.id)"
    print "            } else {"
    print "                hasFailures = true"
    print "            }"
    print "        }"
    print "        "
    print "        return if (hasFailures) Result.retry() else Result.success()"
    print "    }"
    next
}
/private suspend fun syncAnilist/ {
    in_do_work = 0
}
{
    if (!in_do_work) {
        print $0
    }
}
' app/src/main/java/com/example/workers/TrackerSyncWorker.kt > app/src/main/java/com/example/workers/TrackerSyncWorker.kt.tmp
mv app/src/main/java/com/example/workers/TrackerSyncWorker.kt.tmp app/src/main/java/com/example/workers/TrackerSyncWorker.kt
