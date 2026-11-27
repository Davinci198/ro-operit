package com.ai.assistance.operit.ui.features.chat.webview.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rules behind the workspace server's `/api/` gate, its CORS allowlist and its proxy headers.
 *
 * These are the decisions `SECURITY.md` documents for `LocalWebServer`; the server itself only
 * delegates here, so the whole policy is testable on the JVM without Android or a running socket.
 */
class WorkspaceAccessPolicyTest {

    private val tokenHeader = "X-Operit-Workspace-Token"
    private val workspacePort = 8093

    @Test
    fun `a request that presents no token is refused`() {
        assertNull(WorkspaceAccessPolicy.presentedApiToken(null, emptyMap(), tokenHeader))
        assertFalse(WorkspaceAccessPolicy.apiTokenMatches(null, "expected"))
    }

    @Test
    fun `the query parameter wins over the header`() {
        assertEquals(
            "fromQuery",
            WorkspaceAccessPolicy.presentedApiToken(
                "fromQuery",
                mapOf(tokenHeader to "fromHeader"),
                tokenHeader
            )
        )
    }

    @Test
    fun `the token header name is matched case-insensitively`() {
        assertEquals(
            "fromHeader",
            WorkspaceAccessPolicy.presentedApiToken(
                null,
                mapOf("x-operit-workspace-token" to "fromHeader"),
                tokenHeader
            )
        )
    }

    @Test
    fun `only the exact token is accepted`() {
        assertTrue(WorkspaceAccessPolicy.apiTokenMatches("expected", "expected"))
        assertFalse(WorkspaceAccessPolicy.apiTokenMatches("Expected", "expected"))
        assertFalse(WorkspaceAccessPolicy.apiTokenMatches("expecte", "expected"))
        assertFalse(WorkspaceAccessPolicy.apiTokenMatches("expectedd", "expected"))
    }

    @Test
    fun `loopback origins on the workspace port are allowed`() {
        assertEquals(
            "http://127.0.0.1:8093",
            WorkspaceAccessPolicy.allowedWorkspaceOrigin("http://127.0.0.1:8093", workspacePort)
        )
        assertEquals(
            "http://localhost:8093",
            WorkspaceAccessPolicy.allowedWorkspaceOrigin("http://localhost:8093", workspacePort)
        )
        // An IPv6 literal keeps its brackets, because that is how the browser writes the origin.
        assertEquals(
            "http://[::1]:8093",
            WorkspaceAccessPolicy.allowedWorkspaceOrigin("http://[::1]:8093", workspacePort)
        )
    }

    @Test
    fun `an origin that does not spell out the workspace port is refused`() {
        assertNull(WorkspaceAccessPolicy.allowedWorkspaceOrigin("http://localhost", workspacePort))
        assertNull(WorkspaceAccessPolicy.allowedWorkspaceOrigin("http://localhost:80", workspacePort))
        assertNull(WorkspaceAccessPolicy.allowedWorkspaceOrigin("http://127.0.0.1:8094", workspacePort))
    }

    @Test
    fun `a non-loopback or non-http origin is refused`() {
        assertNull(WorkspaceAccessPolicy.allowedWorkspaceOrigin("http://evil.test:8093", workspacePort))
        assertNull(WorkspaceAccessPolicy.allowedWorkspaceOrigin("https://127.0.0.1:8093", workspacePort))
        assertNull(WorkspaceAccessPolicy.allowedWorkspaceOrigin("file:///tmp", workspacePort))
        assertNull(WorkspaceAccessPolicy.allowedWorkspaceOrigin("not an origin", workspacePort))
    }

    @Test
    fun `credentials and hop-by-hop headers are not relayed by the proxy`() {
        listOf("host", "Connection", "content-length", "Accept-Encoding", "COOKIE").forEach {
            assertFalse(WorkspaceAccessPolicy.forwardsProxyRequestHeader(it, tokenHeader))
        }
        assertFalse(WorkspaceAccessPolicy.forwardsProxyRequestHeader(tokenHeader, tokenHeader))
        assertFalse(
            WorkspaceAccessPolicy.forwardsProxyRequestHeader(
                tokenHeader.lowercase(),
                tokenHeader
            )
        )
    }

    @Test
    fun `ordinary request headers are relayed by the proxy`() {
        listOf("Accept", "content-type", "User-Agent", "Authorization").forEach {
            assertTrue(WorkspaceAccessPolicy.forwardsProxyRequestHeader(it, tokenHeader))
        }
    }

    @Test
    fun `upstream framing headers are not copied into the proxied response`() {
        listOf("content-length", "Content-Encoding", "transfer-encoding", "Connection").forEach {
            assertFalse(WorkspaceAccessPolicy.forwardsProxyResponseHeader(it))
        }
        assertTrue(WorkspaceAccessPolicy.forwardsProxyResponseHeader("content-type"))
    }

    @Test
    fun `the proxy only fetches http and https targets`() {
        assertTrue(WorkspaceAccessPolicy.isSupportedProxyTarget("https://example.com/a?b=c"))
        assertTrue(WorkspaceAccessPolicy.isSupportedProxyTarget("http://example.com"))
        assertFalse(WorkspaceAccessPolicy.isSupportedProxyTarget("ftp://example.com/file"))
        assertFalse(WorkspaceAccessPolicy.isSupportedProxyTarget("file:///sdcard/Download"))
        assertFalse(WorkspaceAccessPolicy.isSupportedProxyTarget("/relative/path"))
        assertFalse(WorkspaceAccessPolicy.isSupportedProxyTarget("not a url"))
        assertFalse(WorkspaceAccessPolicy.isSupportedProxyTarget(""))
    }
}
