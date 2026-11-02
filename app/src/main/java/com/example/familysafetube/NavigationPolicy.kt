package com.example.familysafetube

import java.net.URI

/**
 * Decides whether the YouTube-mode WebView may navigate to a URL.
 *
 * Programmatic loads (our own loadUrl) never reach this check. It only sees redirects and
 * taps made inside the page, which is exactly where a child could wander off the curated list.
 */
object NavigationPolicy {

    private fun hostMatches(host: String, domain: String) =
        host == domain || host.endsWith(".$domain")

    private val SHORTS_ID = Regex("""^/shorts/([A-Za-z0-9_-]{11})(?:[/?#].*)?$""")

    fun isAllowed(url: String, hasGesture: Boolean, allowedIds: Set<String>): Boolean {
        val uri = try { URI(url) } catch (_: Exception) { return false }
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase() ?: return false

        val isYoutube = hostMatches(host, "youtube.com")
        val isGoogle  = hostMatches(host, "google.com")
        if (!isYoutube && !isGoogle) return false

        // Redirects the page performs by itself (e.g. cookie-consent hops) are fine as long as
        // they stay on YouTube/Google hosts.
        if (!hasGesture) return true

        // The user tapped something. Consent / account hosts must stay usable (the parent may
        // need to accept a consent screen once)...
        if (host.startsWith("consent.") || host.startsWith("accounts.")) return true

        // ...but on youtube.com only the curated Shorts are reachable.
        if (!isYoutube) return false
        val id = SHORTS_ID.find(uri.rawPath ?: "")?.groupValues?.get(1) ?: return false
        return id in allowedIds
    }
}
