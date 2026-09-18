#!/bin/bash
awk '
/val title = el.selectFirst\(titleSelector\)\?\.text\(\) \?\: a.attr\("title"\)/ {
    print "        val img = el.selectFirst(imageSelector)"
    print "        val title = el.selectFirst(titleSelector)?.text()?.takeIf { it.isNotBlank() } ?: a.attr(\"title\").takeIf { it.isNotBlank() } ?: img?.attr(\"alt\") ?: a.text()"
    print "        val src = img?.absUrl(\"src\")?.ifEmpty { img.absUrl(\"data-src\") } ?: \"\""
    skip = 1
    next
}
skip && /val img = el.selectFirst\(imageSelector\)/ { next }
skip && /val src = img\?\.absUrl\("src"\)\?\.ifEmpty \{ img\.absUrl\("data-src"\) \} \?\: ""/ {
    skip = 0
    next
}
!skip { print $0 }
' app/src/main/java/com/example/data/sources/drama/base/WpDramaThemeScraper.kt > temp.kt && mv temp.kt app/src/main/java/com/example/data/sources/drama/base/WpDramaThemeScraper.kt
