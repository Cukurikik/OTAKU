#!/bin/bash
awk '
BEGIN { skip = 0 }
/val inputData = Data.Builder/ { skip = 1 }
/val request = OneTimeWorkRequestBuilder/ {
    if (skip == 1) {
        skip = 2
        next
    }
}
/WorkManager.getInstance/ {
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
' app/src/main/java/com/example/data/trackers/KitsuTracker.kt > app/src/main/java/com/example/data/trackers/KitsuTracker.kt.tmp
mv app/src/main/java/com/example/data/trackers/KitsuTracker.kt.tmp app/src/main/java/com/example/data/trackers/KitsuTracker.kt
