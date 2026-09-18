#!/bin/bash
awk '
BEGIN { skip = 0 }
/val inputData = androidx.work.Data.Builder/ { skip = 1 }
/val request = androidx.work.OneTimeWorkRequestBuilder/ {
    if (skip == 1) {
        skip = 2
        next
    }
}
/androidx.work.WorkManager.getInstance/ {
    if (skip == 2) {
        skip = 0
        next
    }
}
{
    if (!skip) {
        print $0
    }
}
' app/src/main/java/com/example/data/trackers/AniListTracker.kt > app/src/main/java/com/example/data/trackers/AniListTracker.kt.tmp
mv app/src/main/java/com/example/data/trackers/AniListTracker.kt.tmp app/src/main/java/com/example/data/trackers/AniListTracker.kt
