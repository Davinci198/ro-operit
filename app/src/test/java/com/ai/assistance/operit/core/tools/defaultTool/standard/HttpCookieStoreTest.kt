package com.ai.assistance.operit.core.tools.defaultTool.standard

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Scope tests for the cookie store used by the HTTP tools.
 *
 * The store only holds OkHttp [Cookie] values and lets [Cookie.matches] decide the scope, so these
 * tests pin the behaviour the previous host-keyed map broke: Path, Domain, Secure and expiry.
 */
class HttpCookieStoreTest {

    @Before
    fun resetStore() {
        HttpCookieStore.clear()
    }

    private fun cookie(
        name: String,
        value: String,
        domain: String,
        path: String = "/",
        secure: Boolean = false,
        expiresAt: Long = Long.MAX_VALUE
    ): Cookie {
        val builder =
            Cookie.Builder()
                .name(name)
                .value(value)
                .hostOnlyDomain(domain)
                .path(path)
                .expiresAt(expiresAt)
        if (secure) {
            builder.secure()
        }
        return builder.build()
    }

    private fun namesFor(url: String): Set<String> {
        return HttpCookieStore.loadForRequest(url.toHttpUrl()).map { it.name }.toSet()
    }

    @Test
    fun `a cookie scoped to a path is not sent to a sibling path`() {
        HttpCookieStore.putAll(listOf(cookie("session", "abc", "example.com", path = "/admin")))

        assertEquals(setOf("session"), namesFor("http://example.com/admin/users"))
        assertTrue(namesFor("http://example.com/public").isEmpty())
    }

    @Test
    fun `a secure cookie is not sent over plain http`() {
        HttpCookieStore.putAll(listOf(cookie("token", "s3cret", "example.com", secure = true)))

        assertEquals(setOf("token"), namesFor("https://example.com/"))
        assertTrue(namesFor("http://example.com/").isEmpty())
    }

    @Test
    fun `a cookie stays on its own domain`() {
        HttpCookieStore.putAll(listOf(cookie("session", "abc", "example.com")))

        assertEquals(setOf("session"), namesFor("http://example.com/"))
        assertTrue(namesFor("http://other.test/").isEmpty())
    }

    @Test
    fun `a host-only cookie is not sent to a subdomain`() {
        HttpCookieStore.putAll(listOf(cookie("session", "abc", "example.com")))

        assertTrue(namesFor("http://sub.example.com/").isEmpty())
    }

    @Test
    fun `a later response for the same domain keeps the cookies stored before it`() {
        HttpCookieStore.putAll(listOf(cookie("first", "1", "example.com")))
        HttpCookieStore.putAll(listOf(cookie("second", "2", "example.com")))

        assertEquals(setOf("first", "second"), namesFor("http://example.com/"))
    }

    @Test
    fun `the same cookie is replaced instead of duplicated`() {
        HttpCookieStore.putAll(listOf(cookie("sid", "old", "example.com", path = "/app")))
        HttpCookieStore.putAll(listOf(cookie("sid", "new", "example.com", path = "/app")))

        val stored = HttpCookieStore.forDomain("example.com")
        assertEquals(1, stored.size)
        assertEquals("new", stored.single().value)
    }

    @Test
    fun `an expired cookie is dropped by every accessor`() {
        val alreadyExpired = System.currentTimeMillis() - 60_000L
        HttpCookieStore.putAll(
            listOf(
                cookie("gone", "1", "example.com", expiresAt = alreadyExpired),
                cookie("kept", "2", "example.com")
            )
        )

        assertEquals(setOf("kept"), namesFor("http://example.com/"))
        assertEquals(setOf("kept"), HttpCookieStore.forDomain("example.com").map { it.name }.toSet())
        assertEquals(setOf("kept"), HttpCookieStore.all().map { it.name }.toSet())
    }

    @Test
    fun `domain lookup ignores case and a leading dot`() {
        HttpCookieStore.putAll(listOf(cookie("session", "abc", "example.com")))

        assertEquals(1, HttpCookieStore.forDomain(".EXAMPLE.com").size)
    }

    @Test
    fun `removing one domain leaves the other domains alone`() {
        HttpCookieStore.putAll(
            listOf(cookie("a", "1", "example.com"), cookie("b", "2", "other.test"))
        )

        HttpCookieStore.removeForDomain("example.com")

        assertTrue(HttpCookieStore.forDomain("example.com").isEmpty())
        assertEquals(setOf("b"), namesFor("http://other.test/"))
    }

    @Test
    fun `clear empties the store`() {
        HttpCookieStore.putAll(listOf(cookie("session", "abc", "example.com")))

        HttpCookieStore.clear()

        assertTrue(HttpCookieStore.all().isEmpty())
        assertTrue(namesFor("http://example.com/").isEmpty())
    }

    @Test
    fun `a cookie parsed from a Set-Cookie header keeps its scope`() {
        val parsed =
            Cookie.parse(
                "https://example.com/admin".toHttpUrl(),
                "session=abc; Domain=example.com; Path=/admin; Secure"
            )
        assertNotNull(parsed)
        HttpCookieStore.putAll(listOf(requireNotNull(parsed)))

        assertEquals(setOf("session"), namesFor("https://example.com/admin/users"))
        assertTrue(namesFor("https://example.com/public").isEmpty())
        assertTrue(namesFor("http://example.com/admin").isEmpty())
    }
}
