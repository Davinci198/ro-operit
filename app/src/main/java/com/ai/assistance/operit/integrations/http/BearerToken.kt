package com.ai.assistance.operit.integrations.http

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Bearer authentication shared by the two external HTTP servers.
 *
 * Token extraction and comparison used to be duplicated verbatim in
 * `ExternalChatHttpServer.requireBearerToken` and `WebChatHttpBridge.requireBearerToken`. Nothing
 * here touches Android or NanoHTTPD types, so the rules are covered by JVM unit tests
 * (`:app:testCloneDebugUnitTest`); each server keeps building its own 401 body.
 */
internal object BearerToken {

    private const val AUTHORIZATION_HEADER = "authorization"
    private const val SCHEME_PREFIX = "Bearer "

    /**
     * The token carried by `Authorization: Bearer <token>`, or an empty string when the header is
     * absent or uses another scheme. The header name and the scheme are matched case-insensitively
     * and surrounding whitespace is ignored, exactly as the inlined code did.
     */
    fun fromHeaders(headers: Map<String, String>): String {
        val authorization = headers.entries.firstOrNull {
            it.key.equals(AUTHORIZATION_HEADER, ignoreCase = true)
        }?.value?.trim().orEmpty()
        return if (authorization.startsWith(SCHEME_PREFIX, ignoreCase = true)) {
            authorization.substringAfter(' ').trim()
        } else {
            ""
        }
    }

    /**
     * Constant-time comparison: a plain string comparison returns at the first differing byte, and
     * the timing difference can be used to guess the token byte by byte.
     *
     * Callers reject a blank configured token before asking for this comparison, so an empty pair is
     * never treated as authenticated in practice.
     */
    fun matches(presented: String, expected: String): Boolean {
        return MessageDigest.isEqual(
            presented.toByteArray(StandardCharsets.UTF_8),
            expected.toByteArray(StandardCharsets.UTF_8)
        )
    }
}
