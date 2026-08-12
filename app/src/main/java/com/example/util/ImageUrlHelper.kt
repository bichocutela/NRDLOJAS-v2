package com.example.util

object ImageUrlHelper {
    fun normalizeUrl(url: String): String {
        var cleanUrl = url.trim()
        val fileRegex = Regex("""drive\.google\.com/file/d/([^/]+)/?""")
        val openRegex = Regex("""drive\.google\.com/open\?id=([^&]+)""")
        val ucRegex = Regex("""drive\.google\.com/uc\?.*id=([^&]+)""")
        val driveLinkRegex = Regex("""drive\.google\.com/thumbnail\?id=([^&]+)""")
        
        val fileMatch = fileRegex.find(cleanUrl)
        if (fileMatch != null) {
            val id = fileMatch.groupValues[1]
            return "https://lh3.googleusercontent.com/d/$id"
        }
        
        val openMatch = openRegex.find(cleanUrl)
        if (openMatch != null) {
            val id = openMatch.groupValues[1]
            return "https://lh3.googleusercontent.com/d/$id"
        }

        val ucMatch = ucRegex.find(cleanUrl)
        if (ucMatch != null) {
            val id = ucMatch.groupValues[1]
            return "https://lh3.googleusercontent.com/d/$id"
        }
        
        val driveLinkMatch = driveLinkRegex.find(cleanUrl)
        if (driveLinkMatch != null) {
            val id = driveLinkMatch.groupValues[1]
            return "https://lh3.googleusercontent.com/d/$id"
        }
        
        return cleanUrl
    }
}
