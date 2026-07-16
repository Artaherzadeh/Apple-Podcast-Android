package com.alireza.podcasts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingUtilsTest {

    @Test
    fun testIsAppleDomain_valid() {
        assertTrue(RoutingUtils.isAppleDomain("https://apple.com"))
        assertTrue(RoutingUtils.isAppleDomain("https://podcasts.apple.com"))
        assertTrue(RoutingUtils.isAppleDomain("https://appleid.apple.com"))
        assertTrue(RoutingUtils.isAppleDomain("https://idmsa.apple.com"))
        assertTrue(RoutingUtils.isAppleDomain("https://apple-dns.net"))
        assertTrue(RoutingUtils.isAppleDomain("https://sub.apple-dns.net"))
    }

    @Test
    fun testIsAppleDomain_invalid() {
        // HTTP instead of HTTPS
        assertFalse(RoutingUtils.isAppleDomain("http://apple.com"))
        assertFalse(RoutingUtils.isAppleDomain("http://podcasts.apple.com"))
        
        // Phishing domains
        assertFalse(RoutingUtils.isAppleDomain("https://apple.com.evil.example"))
        assertFalse(RoutingUtils.isAppleDomain("https://evilapple.com"))
        assertFalse(RoutingUtils.isAppleDomain("https://apple.com-malicious.com"))
        
        // Invalid or null URLs
        assertFalse(RoutingUtils.isAppleDomain(""))
        assertFalse(RoutingUtils.isAppleDomain("random_string"))
    }

    @Test
    fun testGetRouteType() {
        // Internal Apple WebView navigation
        assertEquals(RoutingUtils.RouteType.INTERNAL_WEBVIEW, RoutingUtils.getRouteType("https://podcasts.apple.com"))
        assertEquals(RoutingUtils.RouteType.INTERNAL_WEBVIEW, RoutingUtils.getRouteType("https://appleid.apple.com"))
        assertEquals(RoutingUtils.RouteType.INTERNAL_WEBVIEW, RoutingUtils.getRouteType("https://apple-dns.net"))

        // External Custom Tab navigation
        assertEquals(RoutingUtils.RouteType.EXTERNAL_CUSTOM_TAB, RoutingUtils.getRouteType("https://google.com"))
        assertEquals(RoutingUtils.RouteType.EXTERNAL_CUSTOM_TAB, RoutingUtils.getRouteType("https://apple.com.evil.example"))
        assertEquals(RoutingUtils.RouteType.EXTERNAL_CUSTOM_TAB, RoutingUtils.getRouteType("http://apple.com"))

        // Deep links
        assertEquals(RoutingUtils.RouteType.DEEP_LINK_INTENT, RoutingUtils.getRouteType("mailto:support@apple.com"))
        assertEquals(RoutingUtils.RouteType.DEEP_LINK_INTENT, RoutingUtils.getRouteType("tel:+1234567890"))
        assertEquals(RoutingUtils.RouteType.DEEP_LINK_INTENT, RoutingUtils.getRouteType("sms:+1234567890"))

        // Blocked local exploits and unknown/custom schemes
        assertEquals(RoutingUtils.RouteType.BLOCKED, RoutingUtils.getRouteType("javascript:alert(1)"))
        assertEquals(RoutingUtils.RouteType.BLOCKED, RoutingUtils.getRouteType("file:///sdcard/passwords.txt"))
        assertEquals(RoutingUtils.RouteType.BLOCKED, RoutingUtils.getRouteType("content://media/external/images/media/1"))
        assertEquals(RoutingUtils.RouteType.BLOCKED, RoutingUtils.getRouteType("intent:#Intent;scheme=http;action=android.intent.action.VIEW;end"))
        assertEquals(RoutingUtils.RouteType.BLOCKED, RoutingUtils.getRouteType("data:text/html,<h1>Hack</h1>"))
        assertEquals(RoutingUtils.RouteType.BLOCKED, RoutingUtils.getRouteType("blob:https://podcasts.apple.com/some-uuid"))
        assertEquals(RoutingUtils.RouteType.BLOCKED, RoutingUtils.getRouteType("market://details?id=com.alireza.podcasts"))
        assertEquals(RoutingUtils.RouteType.BLOCKED, RoutingUtils.getRouteType("myapp://custom-action"))
    }
}
