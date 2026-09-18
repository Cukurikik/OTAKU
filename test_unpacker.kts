import java.util.regex.Pattern

fun e(c: Int, a: Int): String {
    val radix = if (a <= 62) {
        if (a <= 36) 36 else 62
    } else a
    var res = ""
    var value = c
    do {
        val digit = value % radix
        res = (if (digit > 35) (digit + 29).toChar().toString() else digit.toString(36)) + res
        value /= radix
    } while (value > 0)
    return res
}

fun unpack(packedJS: String): String? {
    val pRegex = Regex("""\}\('([^']*)',.*?(\d+),.*?(\d+),.*?'([^']*)'\.split\('\|'\).*?""")
    val match = pRegex.find(packedJS) ?: return null
    
    val p = match.groupValues[1]
    val a = match.groupValues[2].toIntOrNull() ?: return null
    val c = match.groupValues[3].toIntOrNull() ?: return null
    val kStr = match.groupValues[4]
    val k = kStr.split("|")
    
    var unpacked = p
    for (i in c - 1 downTo 0) {
        if (k.getOrNull(i)?.isNotEmpty() == true) {
            val searchRegex = Regex("\\b" + e(i, a) + "\\b")
            unpacked = unpacked.replace(searchRegex, k[i])
        }
    }
    return unpacked
}

val packed = """eval(function(p,a,c,k,e,d){e=function(c){return c.toString(36)};if(!''.replace(/^/,String)){while(c--){d[c.toString(a)]=k[c]||c.toString(a)}k=[function(e){return d[e]}];e=function(){return'\\w+'};c=1};while(c--){if(k[c]){p=p.replace(new RegExp('\\b'+e(c)+'\\b','g'),k[c])}}return p}('0 1="2://3.4.5/6/7.8";',9,9,'var|url|https|video|domain|com|stream|movie|mp4'.split('|'),0,{}))"""
println(unpack(packed))
