package com.ai.assistance.operit.core.tools.defaultTool.standard

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import okhttp3.Cookie
import okhttp3.HttpUrl

/**
 * Cookie store for the HTTP tools.
 *
 * The previous implementation kept a whole `List<Cookie>` per host name in a map, which discarded
 * everything `Set-Cookie` carries: a cookie scoped to `/admin` was sent to every path on that host, a
 * cookie marked `Secure` was sent over plain `http://`, a later response for the same host replaced
 * the stored cookies wholesale, and expired entries were never removed.
 *
 * Cookies are merged by (name, domain, path) here, and before a request goes out OkHttp's own
 * [Cookie.matches] decides whether each one applies (it handles host-only cookies, `Domain`
 * suffixes, `Path` prefixes and `Secure`). Expired entries are dropped.
 */
internal object HttpCookieStore {

    private data class CookieKey(val name: String, val domain: String, val path: String)

    /** Outer map buckets by cookie domain; the inner map keys a cookie by (name, domain, path). */
    private val bucketsByDomain = ConcurrentHashMap<String, ConcurrentHashMap<CookieKey, Cookie>>()

    /** Store cookies from a response or from the caller, merged: the same cookie is replaced, the rest are kept. */
    fun putAll(cookies: List<Cookie>) {
        cookies.forEach { cookie ->
            val domain = normalizeDomain(cookie.domain)
            val bucket = bucketsByDomain.computeIfAbsent(domain) { ConcurrentHashMap() }
            bucket[CookieKey(cookie.name, domain, cookie.path)] = cookie
        }
        purgeExpired()
    }

    /** Cookies that apply to [url]: OkHttp decides the scope match, expired entries are dropped here. */
    fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return bucketsByDomain.values
                .asSequence()
                .flatMap { bucket -> bucket.values.asSequence() }
                .filter { cookie -> cookie.expiresAt > now && cookie.matches(url) }
                .toList()
    }

    /** Unexpired cookies belonging to [domain]. */
    fun forDomain(domain: String): List<Cookie> {
        val now = System.currentTimeMillis()
        val bucket = bucketsByDomain[normalizeDomain(domain)] ?: return emptyList()
        return bucket.values.filter { cookie -> cookie.expiresAt > now }
    }

    /** Every unexpired cookie. */
    fun all(): List<Cookie> {
        val now = System.currentTimeMillis()
        return bucketsByDomain.values
                .asSequence()
                .flatMap { bucket -> bucket.values.asSequence() }
                .filter { cookie -> cookie.expiresAt > now }
                .toList()
    }

    /** Remove the cookies belonging to [domain]. */
    fun removeForDomain(domain: String) {
        bucketsByDomain.remove(normalizeDomain(domain))
    }

    fun clear() {
        bucketsByDomain.clear()
    }

    private fun purgeExpired() {
        val now = System.currentTimeMillis()
        bucketsByDomain.forEach { (domain, bucket) ->
            bucket.entries.removeAll { (_, cookie) -> cookie.expiresAt <= now }
            if (bucket.isEmpty()) {
                bucketsByDomain.remove(domain, bucket)
            }
        }
    }

    private fun normalizeDomain(domain: String): String {
        return domain.trim().removePrefix(".").lowercase(Locale.US)
    }
}
