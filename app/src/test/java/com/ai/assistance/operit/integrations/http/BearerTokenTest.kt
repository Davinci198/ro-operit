package com.ai.assistance.operit.integrations.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bearer authentication shared by `ExternalChatHttpServer` and `WebChatHttpBridge`.
 *
 * Both servers reject a blank configured token before they compare anything, so these tests cover
 * the extraction rules and the constant-time comparison they both rely on.
 */
class BearerTokenTest {

    @Test
    fun `the token is read from a bearer header`() {
        assertEquals("abc123", BearerToken.fromHeaders(mapOf("Authorization" to "Bearer abc123")))
    }

    @Test
    fun `the header name and the scheme are matched case-insensitively`() {
        assertEquals("abc", BearerToken.fromHeaders(mapOf("authorization" to "Bearer abc")))
        assertEquals("abc", BearerToken.fromHeaders(mapOf("AUTHORIZATION" to "bearer abc")))
        assertEquals("abc", BearerToken.fromHeaders(mapOf("Authorization" to "BEARER abc")))
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals("abc", BearerToken.fromHeaders(mapOf("Authorization" to "   Bearer abc   ")))
        assertEquals("abc", BearerToken.fromHeaders(mapOf("Authorization" to "Bearer   abc")))
    }

    @Test
    fun `another scheme or a missing header yields no token`() {
        assertEquals("", BearerToken.fromHeaders(mapOf("Authorization" to "Basic dXNlcjpwYXNz")))
        assertEquals("", BearerToken.fromHeaders(mapOf("Authorization" to "Bearer")))
        assertEquals("", BearerToken.fromHeaders(mapOf("Authorization" to "")))
        assertEquals("", BearerToken.fromHeaders(emptyMap()))
    }

    @Test
    fun `the configured token is accepted and anything else is refused`() {
        assertTrue(BearerToken.matches("abc123", "abc123"))
        assertFalse(BearerToken.matches("abc124", "abc123"))
        assertFalse(BearerToken.matches("abc12", "abc123"))
        assertFalse(BearerToken.matches("abc1234", "abc123"))
        assertFalse(BearerToken.matches("", "abc123"))
    }

    @Test
    fun `an empty pair compares equal, which is why the blank check stays upstream`() {
        assertTrue(BearerToken.matches("", ""))
    }

    @Test
    fun `a header with no token is refused against a configured one`() {
        val expected = "abc123"
        val presented = BearerToken.fromHeaders(mapOf("Authorization" to "Bearer "))

        assertEquals("", presented)
        assertFalse(BearerToken.matches(presented, expected))
    }
}
