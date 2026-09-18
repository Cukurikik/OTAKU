package com.example.data.sources.anime.extractors

import java.util.regex.Pattern

object JsPackerUnpacker {

    /**
     * Unpacks a JavaScript code obfuscated with Dean Edwards' packer.
     * Supports nested / recursive unpacking up to 5 layers.
     */
    fun unpack(packedCode: String): String? {
        if (!containsPackedJs(packedCode)) return null
        
        var current = packedCode
        var totalUnpacked = 0
        
        while (totalUnpacked < 5) {
            val unp = unpackSingle(current) ?: break
            current = unp
            totalUnpacked++
            if (!containsPackedJs(current)) {
                break
            }
        }
        
        return if (totalUnpacked > 0) current else null
    }

    private fun containsPackedJs(code: String): Boolean {
        return code.contains("eval(function(p,a,c,k,e,", ignoreCase = true) ||
               code.contains("eval(function(p,a,c,k,e,r)", ignoreCase = true) ||
               code.contains("eval(function(p,a,c,k,e,d)", ignoreCase = true) ||
               Regex("""eval\s*\(\s*function\s*\(\s*p\s*,\s*a\s*,\s*c\s*,\s*k""").containsMatchIn(code)
    }

    private fun unpackSingle(packedCode: String): String? {
        // Pattern 1: Standard .split('|') with single or double quotes
        val pattern1 = Pattern.compile(
            """eval\s*\(\s*function\s*\(\s*p\s*,\s*a\s*,\s*c\s*,\s*k\s*,\s*e\s*,\s*[rd]\s*\)[\s\S]*?\}\s*\(\s*(['"][\s\S]*?['"])\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(['"][\s\S]*?['"])\.split\s*\(\s*['"]\|['"]\s*\)""",
            Pattern.DOTALL
        )
        val matcher1 = pattern1.matcher(packedCode)
        if (matcher1.find()) {
            val rawP = matcher1.group(1) ?: return null
            val p = stripQuotes(rawP)
            val a = matcher1.group(2)?.toIntOrNull() ?: return null
            val c = matcher1.group(3)?.toIntOrNull() ?: return null
            val rawK = matcher1.group(4) ?: return null
            val kStr = stripQuotes(rawK)
            val k = kStr.split("|")
            return unpackInternal(p, a, c, k)
        }

        // Pattern 2: k as direct array literal ['a', 'b', ...]
        val pattern2 = Pattern.compile(
            """eval\s*\(\s*function\s*\(\s*p\s*,\s*a\s*,\s*c\s*,\s*k\s*,\s*e\s*,\s*[rd]\s*\)[\s\S]*?\}\s*\(\s*(['"][\s\S]*?['"])\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*\[([\s\S]*?)\]""",
            Pattern.DOTALL
        )
        val matcher2 = pattern2.matcher(packedCode)
        if (matcher2.find()) {
            val rawP = matcher2.group(1) ?: return null
            val p = stripQuotes(rawP)
            val a = matcher2.group(2)?.toIntOrNull() ?: return null
            val c = matcher2.group(3)?.toIntOrNull() ?: return null
            val arrayContent = matcher2.group(4) ?: ""
            val k = arrayContent.split(",").map { stripQuotes(it.trim()) }
            return unpackInternal(p, a, c, k)
        }

        // Pattern 3: Fallback flexible pattern
        val pattern3 = Pattern.compile(
            """\}\s*\(\s*['"](.*?)['"]\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*['"](.*?)['"]\.split\s*\(\s*['"]\|['"]\s*\)""",
            Pattern.DOTALL
        )
        val matcher3 = pattern3.matcher(packedCode)
        if (matcher3.find()) {
            val p = matcher3.group(1) ?: return null
            val a = matcher3.group(2)?.toIntOrNull() ?: return null
            val c = matcher3.group(3)?.toIntOrNull() ?: return null
            val kStr = matcher3.group(4) ?: return null
            val k = kStr.split("|")
            return unpackInternal(p, a, c, k)
        }

        return null
    }

    private fun stripQuotes(str: String): String {
        var s = str.trim()
        if ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\""))) {
            s = s.substring(1, s.length - 1)
        }
        return s
    }

    private fun unpackInternal(p: String, a: Int, c: Int, k: List<String>): String {
        var result = p
        
        fun e(c: Int, a: Int): String {
            val first = if (c < a) "" else e(c / a, a)
            val second = c % a
            val secondStr = if (second > 35) {
                (second + 29).toChar().toString()
            } else {
                second.toString(36)
            }
            return first + secondStr
        }

        for (i in c - 1 downTo 0) {
            val word = e(i, a)
            val replacement = if (k.size > i && k[i].isNotEmpty()) k[i] else word
            val search = "\\b" + word + "\\b"
            result = result.replace(Regex(search), java.util.regex.Matcher.quoteReplacement(replacement))
        }
        
        // Unescape escaped slashes and quotes in unpacked result
        result = result.replace("\\/", "/").replace("\\\"", "\"").replace("\\'", "'")
        
        return result
    }
}
