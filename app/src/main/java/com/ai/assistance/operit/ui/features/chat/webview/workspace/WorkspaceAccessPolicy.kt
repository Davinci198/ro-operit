package com.ai.assistance.operit.ui.features.chat.webview.workspace

import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

/**
 * Access-control rules of the workspace server.
 *
 * LocalWebServer used to take these decisions inside private members, mixed with NanoHTTPD response
 * building. Nothing here touches Android or NanoHTTPD types, so every rule can be covered by a JVM
 * unit test (`:app:testCloneDebugUnitTest`) and the server only delegates to this object.
 */
internal object WorkspaceAccessPolicy {

    /** Origin hosts allowed to read the local workspace cross-origin. */
    private val LOOPBACK_ORIGIN_HOSTS = setOf("localhost", "127.0.0.1", "::1")

    /** The proxy only fetches plain HTTP(S) targets, so it cannot be used to probe other schemes. */
    private val SUPPORTED_PROXY_SCHEMES = setOf("http", "https")

    /**
     * Hop-by-hop headers that OkHttp manages itself, plus the credentials that must never be relayed:
     * the proxy does not carry the WebView's sessions, so a cross-site request goes out without
     * cookies and without this server's API token.
     */
    private val PROXY_REQUEST_HEADER_DENYLIST =
        setOf("host", "connection", "content-length", "accept-encoding", "cookie")

    /** Upstream response headers that NanoHTTPD recomputes for the response it sends back. */
    private val PROXY_RESPONSE_HEADER_DENYLIST =
        setOf("content-length", "content-encoding", "transfer-encoding", "connection")

    /** The token a request presents: query parameter first, then the header (case-insensitive name). */
    fun presentedApiToken(
        queryToken: String?,
        headers: Map<String, String>,
        tokenHeader: String
    ): String? {
        return queryToken
            ?: headers.entries.firstOrNull { it.key.equals(tokenHeader, ignoreCase = true) }?.value
    }

    /** Constant-time comparison, so the token cannot be inferred from response timings. */
    fun apiTokenMatches(presented: String?, expected: String): Boolean {
        if (presented == null) {
            return false
        }
        return MessageDigest.isEqual(
            presented.toByteArray(StandardCharsets.UTF_8),
            expected.toByteArray(StandardCharsets.UTF_8)
        )
    }

    /**
     * The origin allowed to read the workspace cross-origin, or null when the caller must receive no
     * CORS headers at all - that is how the browser is told it may not read the response.
     */
    fun allowedWorkspaceOrigin(origin: String, workspacePort: Int): String? {
        val parsed = runCatching { URI(origin) }.getOrNull() ?: return null
        if (parsed.scheme != "http") {
            return null
        }
        val host = parsed.host?.lowercase(Locale.US)?.removeSurrounding("[", "]") ?: return null
        if (host !in LOOPBACK_ORIGIN_HOSTS) {
            return null
        }
        // The origin has to spell out the workspace port: an omitted port means port 80, which this
        // server cannot be serving.
        if (parsed.port != workspacePort) {
            return null
        }
        // An IPv6 literal keeps its brackets: the browser compares this header value against the page
        // origin, and that origin is written "http://[::1]:8093".
        val hostLiteral = if (host.contains(":")) "[$host]" else host
        return "http://$hostLiteral:$workspacePort"
    }

    /** Whether the proxy may be asked to fetch [targetUrl] at all. */
    fun isSupportedProxyTarget(targetUrl: String): Boolean {
        val parsed = runCatching { URI(targetUrl) }.getOrNull() ?: return false
        return parsed.scheme in SUPPORTED_PROXY_SCHEMES
    }

    /** Whether the proxy may relay a request header to the third-party host. */
    fun forwardsProxyRequestHeader(name: String, tokenHeader: String): Boolean {
        val lower = name.lowercase(Locale.US)
        return lower !in PROXY_REQUEST_HEADER_DENYLIST &&
            !lower.equals(tokenHeader, ignoreCase = true)
    }

    /** Whether the proxy may copy a header of the upstream response into its own response. */
    fun forwardsProxyResponseHeader(name: String): Boolean {
        return name.lowercase(Locale.US) !in PROXY_RESPONSE_HEADER_DENYLIST
    }
}
