# Pasul 1 — `WorkspaceAccessPolicy` (LocalWebServer)

## Vechea implementare / Old implementation

Patru decizii stau în membri `private` ai `LocalWebServer`
(`app/src/main/java/com/ai/assistance/operit/ui/features/chat/webview/LocalWebServer.kt`), amestecate
cu construirea răspunsurilor NanoHTTPD:

```kotlin
// :755-767
private fun hasValidApiToken(session: IHTTPSession): Boolean {
    val presented = session.parameters[API_TOKEN_QUERY]?.firstOrNull()
        ?: session.headers.entries.firstOrNull {
            it.key.equals(API_TOKEN_HEADER, ignoreCase = true)
        }?.value
    if (presented == null) {
        return false
    }
    return MessageDigest.isEqual(
        presented.toByteArray(StandardCharsets.UTF_8),
        apiToken.toByteArray(StandardCharsets.UTF_8)
    )
}

// :920-938 (corectat pe acest branch: un literal IPv6 își păstrează parantezele pătrate)
private fun loopbackWorkspaceOrigin(origin: String): String? { ... }

// :791-804
session.headers.forEach { (name, value) ->
    val lower = name.lowercase(Locale.US)
    if (
        lower in setOf("host", "connection", "content-length", "accept-encoding", "cookie") ||
        lower.equals(API_TOKEN_HEADER, ignoreCase = true)
    ) {
        return@forEach
    }
    requestBuilder.addHeader(name, value)
}

// :827-831
response.headers.forEach { (name, value) ->
    val lower = name.lowercase(Locale.US)
    if (lower in setOf("content-length", "content-encoding", "transfer-encoding", "connection")) return@forEach
    nanoResponse.addHeader(name, value)
}
```

`LOOPBACK_ORIGIN_HOSTS` (`:87`) este folosit doar de `loopbackWorkspaceOrigin`, deci se mută odată cu
el.

## Noua implementare / New implementation

Un fișier nou, fără tipuri Android și fără NanoHTTPD:
`app/src/main/java/com/ai/assistance/operit/ui/features/chat/webview/workspace/WorkspaceAccessPolicy.kt`
(pachetul există deja: `WorkspaceConfig.kt`, `WorkspaceManager.kt`,
`WorkspaceFilePreviewSupport.kt`).

```kotlin
package com.ai.assistance.operit.ui.features.chat.webview.workspace

import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

/**
 * Pure access-control rules of the workspace server.
 *
 * LocalWebServer used to take these decisions inside private members, mixed with NanoHTTPD response
 * building. Nothing here touches Android or NanoHTTPD types, so every rule can be covered by a JVM
 * unit test (`:app:testCloneDebugUnitTest`) and the server only delegates.
 */
internal object WorkspaceAccessPolicy {

    /** Origin hosts allowed to read the local workspace cross-origin. */
    private val LOOPBACK_ORIGIN_HOSTS = setOf("localhost", "127.0.0.1", "::1")

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
        val parsed = kotlin.runCatching { URI(origin) }.getOrNull() ?: return null
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
```

## Delegările din `LocalWebServer` / Call sites

```kotlin
// hasValidApiToken (:755-767) devine
private fun hasValidApiToken(session: IHTTPSession): Boolean {
    return WorkspaceAccessPolicy.apiTokenMatches(
        WorkspaceAccessPolicy.presentedApiToken(
            session.parameters[API_TOKEN_QUERY]?.firstOrNull(),
            session.headers,
            API_TOKEN_HEADER
        ),
        apiToken
    )
}

// addCorsHeaders (:911) apelează direct politica; loopbackWorkspaceOrigin și LOOPBACK_ORIGIN_HOSTS
// se șterg din LocalWebServer
private fun Response.addCorsHeaders(origin: String?): Response {
    val allowOrigin = origin?.let {
        WorkspaceAccessPolicy.allowedWorkspaceOrigin(it, WORKSPACE_PORT)
    } ?: return this
    ...
}

// proxy, headere de cerere (:791-804)
session.headers.forEach { (name, value) ->
    if (!WorkspaceAccessPolicy.forwardsProxyRequestHeader(name, API_TOKEN_HEADER)) {
        return@forEach
    }
    requestBuilder.addHeader(name, value)
}

// proxy, headere de răspuns (:827-831)
response.headers.forEach { (name, value) ->
    if (!WorkspaceAccessPolicy.forwardsProxyResponseHeader(name)) {
        return@forEach
    }
    nanoResponse.addHeader(name, value)
}
```

Importuri noi în `LocalWebServer.kt`:
`com.ai.assistance.operit.ui.features.chat.webview.workspace.WorkspaceAccessPolicy`. Importurile
`java.net.URI`, `java.nio.charset.StandardCharsets`, `java.security.MessageDigest` și `java.util.Locale`
rămân doar dacă mai au alt consumator în fișier (`Locale` da, `MessageDigest`/`StandardCharsets`/`URI`
nu) — se verifică cu `rg` la implementare, nu se lasă importuri moarte.

## Teste / `WorkspaceAccessPolicyTest.kt`

`app/src/test/java/com/ai/assistance/operit/ui/features/chat/webview/workspace/WorkspaceAccessPolicyTest.kt`,
JUnit 4, fără mock-uri (toate funcțiile sunt pure).

| Test | Regulă fixată |
| :---- | :---- |
| `a request with no token is refused` | `presentedApiToken(null, emptyMap(), H) == null`; `apiTokenMatches(null, "x") == false` |
| `the query parameter wins over the header` | `presentedApiToken("q", mapOf(H to "h"), H) == "q"` |
| `the token header name is matched case-insensitively` | `presentedApiToken(null, mapOf("x-operit-workspace-token" to "h"), "X-Operit-Workspace-Token") == "h"` |
| `the right token is accepted and a wrong one is refused` | `apiTokenMatches("a", "a")`, `!apiTokenMatches("a", "b")`, `!apiTokenMatches("a", "aa")` |
| `loopback origins on the workspace port are allowed` | `allowedWorkspaceOrigin("http://127.0.0.1:8093", 8093) == "http://127.0.0.1:8093"`, idem `localhost`, și `http://[::1]:8093` -> `http://[::1]:8093` |
| `an origin without the workspace port is refused` | `allowedWorkspaceOrigin("http://localhost", 8093) == null`, `allowedWorkspaceOrigin("http://localhost:80", 8093) == null` |
| `a non-loopback or non-http origin is refused` | `http://evil.test:8093`, `https://127.0.0.1:8093`, `file:///tmp`, un șir care nu este origin (`"not an origin"`) -> `null` |
| `credentials and hop-by-hop headers are not relayed` | `forwardsProxyRequestHeader` == false pentru `Host`, `Connection`, `Content-Length`, `Accept-Encoding`, `Cookie`, `X-Operit-Workspace-Token`; true pentru `Accept`, `Content-Type`, `User-Agent` |
| `upstream framing headers are not copied back` | `forwardsProxyResponseHeader` == false pentru `content-length`, `Content-Encoding`, `transfer-encoding`, `Connection`; true pentru `content-type` |

## Riscuri / Risks

- Este o mutare, nu o rescriere: dacă un test eșuează sau build-ul pică, cauza este o transcriere
  greșită, ușor de localizat prin diff.
- Comportamentul cu parantezele IPv6 este singura schimbare intenționată față de codul anterior și a
  fost deja livrată pe acest branch în `loopbackWorkspaceOrigin`; testul o fixează.
- `internal` este vizibil din testele JVM ale aceluiași modul (compilarea de test este „friend" a
  celei principale); precedent în repo: `ToolPkgManagerTest` pentru `internal class ToolPkgManager`,
  `HttpCookieStoreTest` pentru `internal object HttpCookieStore`.

## Livrat / Landed

- `app/src/main/java/com/ai/assistance/operit/ui/features/chat/webview/workspace/WorkspaceAccessPolicy.kt`
  (nou) și `app/src/test/java/com/ai/assistance/operit/ui/features/chat/webview/workspace/WorkspaceAccessPolicyTest.kt`
  (nou, 11 teste).
- `LocalWebServer` delegă în 6 locuri: `hasValidApiToken`, verificarea de schemă a proxy-ului,
  filtrele de headere pentru cerere și pentru răspuns, `addCorsHeaders`. `loopbackWorkspaceOrigin` și
  `LOOPBACK_ORIGIN_HOSTS` au fost șterse, la fel importurile devenite nefolosite
  (`java.nio.charset.StandardCharsets`, `java.security.MessageDigest`, `java.util.Locale`).
- O abatere față de plan, adăugată la implementare: și verificarea „doar http/https" a țintei de
  proxy a devenit seam (`isSupportedProxyTarget`), pentru că era a șasea decizie pură din aceeași
  funcție și singura care nu avea acoperire.
- Codul mutat este identic cu cel anterior; singura diferență de comportament rămâne cea cu
  parantezele IPv6, deja livrată înainte de acest pas.
- Kotlin/Gradle nu a fost compilat aici (regula fork-ului): validarea vine din lane-ul Android al
  `pr-check.yml` (`:app:testCloneDebugUnitTest`).

[DONE]
