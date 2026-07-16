package com.alireza.podcasts

import java.util.Locale

object RoutingUtils {

    enum class RouteType {
        INTERNAL_WEBVIEW,
        EXTERNAL_CUSTOM_TAB,
        DEEP_LINK_INTENT,
        BLOCKED
    }

    fun getRouteType(urlString: String?): RouteType {
        if (urlString.isNullOrBlank()) return RouteType.BLOCKED

        val cleanedUrl = urlString.trim()
        val lowerUrl = cleanedUrl.lowercase(Locale.US)

        // 1. Process mailto: and tel: and other standard deep links first (they do not have an HTTPS scheme by design)
        if (lowerUrl.startsWith("mailto:") || 
            lowerUrl.startsWith("tel:") || 
            lowerUrl.startsWith("sms:") || 
            lowerUrl.startsWith("geo:")) {
            return RouteType.DEEP_LINK_INTENT
        }

        // 2. Block sensitive or unknown schemes
        if (lowerUrl.startsWith("javascript:") || 
            lowerUrl.startsWith("file:") || 
            lowerUrl.startsWith("content:") || 
            lowerUrl.startsWith("intent:")) {
            return RouteType.BLOCKED
        }

        // 3. Enforce HTTP/HTTPS only for internal webview or external custom tab
        if (!lowerUrl.startsWith("http://") && !lowerUrl.startsWith("https://")) {
            return RouteType.BLOCKED
        }

        // 4. Apply HTTPS check and exact-host suffix rules for Apple domains
        return if (isAppleDomain(cleanedUrl)) {
            RouteType.INTERNAL_WEBVIEW
        } else {
            RouteType.EXTERNAL_CUSTOM_TAB
        }
    }

    fun isAppleDomain(urlString: String): Boolean {
        return try {
            val url = java.net.URL(urlString)
            val host = url.host ?: return false
            val scheme = url.protocol ?: return false

            // Enforce HTTPS only
            val isHttps = scheme.equals("https", ignoreCase = true)
            if (!isHttps) return false

            val lowerHost = host.lowercase(Locale.US)

            // Strict exact match or subdomain suffix checks
            val isAppleHost = lowerHost == "apple.com" || lowerHost.endsWith(".apple.com")
            val isAppleDnsHost = lowerHost == "apple-dns.net" || lowerHost.endsWith(".apple-dns.net")

            isAppleHost || isAppleDnsHost
        } catch (e: Exception) {
            false
        }
    }
}
