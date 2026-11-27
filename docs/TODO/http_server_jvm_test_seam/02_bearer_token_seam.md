# Pasul 2 — `BearerToken` (serverele HTTP externe)

## Vechea implementare / Old implementation

Aceeași regulă este scrisă de două ori, identic, în
`app/src/main/java/com/ai/assistance/operit/integrations/http/ExternalChatHttpServer.kt`
(`requireBearerToken`, `:375-410`) și în
`app/src/main/java/com/ai/assistance/operit/integrations/http/WebChatHttpBridge.kt`
(`requireBearerToken`, `:2536-2568`):

```kotlin
val expectedToken = preferences.getBearerToken().trim()
if (expectedToken.isBlank()) {
    return jsonResponse(... "Bearer token not configured" ...)
}

val authorization = session.headers.entries.firstOrNull {
    it.key.equals("authorization", ignoreCase = true)
}?.value?.trim().orEmpty()

val actualToken = if (authorization.startsWith("Bearer ", ignoreCase = true)) {
    authorization.substringAfter(' ').trim()
} else {
    ""
}

// Constant-time comparison: a plain string comparison returns at the first differing byte,
// and the timing difference can be used to guess the token byte by byte
return if (MessageDigest.isEqual(
        actualToken.toByteArray(StandardCharsets.UTF_8),
        expectedToken.toByteArray(StandardCharsets.UTF_8)
    )
) {
    null
} else {
    jsonResponse(Response.Status.UNAUTHORIZED, ...)
}
```

Doar corpul răspunsului de 401 diferă între cele două servere (`ExternalChatResult` versus
`WebErrorResponse`), deci acea parte rămâne la fiecare apelant.

## Noua implementare / New implementation

Un fișier nou în același pachet:
`app/src/main/java/com/ai/assistance/operit/integrations/http/BearerToken.kt`.

```kotlin
package com.ai.assistance.operit.integrations.http

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Bearer authentication shared by the two external HTTP servers.
 *
 * Extraction and comparison used to be duplicated verbatim in
 * `ExternalChatHttpServer.requireBearerToken` and `WebChatHttpBridge.requireBearerToken`. Nothing
 * here touches Android or NanoHTTPD types, so the rules are covered by JVM unit tests; each server
 * keeps its own 401 body.
 */
internal object BearerToken {

    private const val AUTHORIZATION_HEADER = "authorization"
    private const val SCHEME_PREFIX = "Bearer "

    /**
     * The token carried by `Authorization: Bearer <token>`, or an empty string when the header is
     * absent or uses another scheme. The header name and the scheme are matched case-insensitively
     * and surrounding whitespace is ignored, as before.
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
```

## Delegările / Call sites

În ambele fișiere, blocul de mai sus devine:

```kotlin
val expectedToken = preferences.getBearerToken().trim()
if (expectedToken.isBlank()) {
    return jsonResponse(... "Bearer token not configured" ...)   // neschimbat, per server
}

return if (BearerToken.matches(BearerToken.fromHeaders(session.headers), expectedToken)) {
    null
} else {
    jsonResponse(Response.Status.UNAUTHORIZED, ...)              // neschimbat, per server
}
```

`session.headers` este `Map<String, String>` în NanoHTTPD 2.3.1, deci semnatura helper-ului se
potrivește ambelor servere fără conversii. Importurile `java.nio.charset.StandardCharsets` și
`java.security.MessageDigest` se șterg din fișierele apelante dacă nu mai au alt consumator
(de verificat cu `rg` la implementare).

## Teste / `BearerTokenTest.kt`

`app/src/test/java/com/ai/assistance/operit/integrations/http/BearerTokenTest.kt`, JUnit 4, fără
mock-uri.

| Test | Regulă fixată |
| :---- | :---- |
| `the token is read from a bearer header` | `fromHeaders(mapOf("Authorization" to "Bearer abc")) == "abc"` |
| `the header name and the scheme are case-insensitive` | `"authorization"` / `"AUTHORIZATION"`, `"bearer abc"` / `"BEARER abc"` -> `"abc"` |
| `surrounding whitespace is ignored` | `"  Bearer   abc  "` -> `"abc"` (comportamentul actual: `substringAfter(' ').trim()`) |
| `another scheme yields no token` | `"Basic dXNlcjpwYXNz"` -> `""` |
| `a missing header yields no token` | `emptyMap()` -> `""` |
| `the matching token is accepted` | `matches("abc", "abc") == true` |
| `a wrong or partial token is refused` | `matches("abc", "abd")`, `matches("ab", "abc")`, `matches("", "abc")` -> `false` |
| `an empty pair compares equal, which is why the blank check stays upstream` | `matches("", "") == true` |

## Riscuri / Risks

- Extrage doar cod deja identic în două locuri; dacă cele două versiuni diverg între timp, diff-ul
  îl arată imediat.
- Testul pentru `"  Bearer   abc  "` fixează comportamentul **actual** (`substringAfter(' ')` taie de
  la primul spațiu, apoi `trim()`), nu un comportament ideal; dacă se dorește o normalizare diferită,
  ea devine o schimbare explicită, cu testul actualizat în același PR.
- `WebChatHttpBridge` și `ExternalChatHttpServer` rămân singurele porți de intrare pentru token: nicio
  altă rută nu primește autentificare prin acest helper.

## Livrat / Landed

- `app/src/main/java/com/ai/assistance/operit/integrations/http/BearerToken.kt` (nou) și
  `app/src/test/java/com/ai/assistance/operit/integrations/http/BearerTokenTest.kt` (nou, 7 teste).
- Ambele servere au acum două linii în loc de blocul duplicat:
  `val actualToken = BearerToken.fromHeaders(session.headers)` și
  `if (BearerToken.matches(actualToken, expectedToken))`. Verificarea „token neconfigurat" și corpul
  răspunsului 401 au rămas la fiecare server, neschimbate.
- `import java.security.MessageDigest` a fost scos din ambele fișiere (rămăsese nefolosit);
  `java.nio.charset.StandardCharsets` a rămas, pentru că mai are alți consumatori în fiecare fișier.
- Niciunul dintre fișiere nu a fost compilat aici (regula fork-ului); validarea vine din
  `:app:testCloneDebugUnitTest` în CI.

[DONE]
