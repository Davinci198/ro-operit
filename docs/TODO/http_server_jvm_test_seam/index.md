---
title: JVM test seam for the HTTP servers
repo: https://github.com/Davinci198/ro-operit
base: 018641b9
status: implemented on this branch; compile and test run happen in CI (no local build)
language_policy: Romanian and English only — no Chinese content in this plan
---

# Teste JVM pentru serverele HTTP / JVM test seam for the HTTP servers

## Situația actuală / Current state

| Metrică | Valoare | Sursă |
| :---- | :---- | :---- |
| Fișiere Kotlin în `app/src/main` | 1097 | `find app/src/main -name '*.kt'` |
| Teste JVM (`app/src/test`) | 74 de fișiere la momentul planului, 77 de fișiere și 444 de metode `@Test` după pașii 01-03 | rulează în CI prin `:app:testCloneDebugUnitTest` (`.github/workflows/pr-check.yml:405`) |
| Teste instrumentate (`app/src/androidTest`) | 37 `.kt` + 29 probe `.js` | **niciun workflow nu le rulează** (fără emulator în CI) |
| Teste pentru `LocalWebServer`, `ExternalChatHttpServer`, `WebChatHttpBridge` | 0 | singurele porți de rețea ale aplicației |

Cele trei componente expun exact deciziile de securitate documentate în `SECURITY.md`: bind pe
loopback, token per proces, listă albă de origin-uri CORS, filtrarea headerelor la proxy și
comparația constant-time a bearer token-ului. Ele sunt astăzi neverificate automat, iar singura
cale de testare existentă (`androidTest`) nu rulează niciodată în CI.

## De ce nu se pot testa direct / Why they are not testable as they stand

1. **Membri privați.** `LocalWebServer.hasValidApiToken` (`:755-767`), `addCorsHeaders` (`:910-918`),
   `loopbackWorkspaceOrigin` (`:920-935`) și filtrele de headere pentru proxy (`:792-803` pentru
   cerere, `:829` pentru răspuns) sunt
   `private`; constructorul clasei este `private`, iar `getInstance` (`:107-125`) apelează
   `Environment.getExternalStoragePublicDirectory`, care într-un test JVM aruncă „Method ... not
   mocked" (niciun modul nu configurează `testOptions.unitTests.isReturnDefaultValues`).
2. **Logica este amestecată cu NanoHTTPD și Android.** Aceleași reguli stau în corpul unor funcții
   care construiesc `Response`, scriu headere și apelează `AppLogger`.
3. **Duplicare.** Blocul „extrage `Authorization: Bearer` + compară constant-time" apare identic în
   `ExternalChatHttpServer.kt:387-404` și `WebChatHttpBridge.kt:2545-2562`; un test ar trebui scris
   de două ori pentru aceeași regulă.

## Strategia aleasă / Chosen strategy

Extragerea deciziilor în obiecte `internal` **fără tipuri Android**, apoi testarea lor ca teste JVM
obișnuite. Nu se adaugă nicio dependență nouă:

- `junit 4.13.2`, `mockito-core 5.2.0`, `mockito-kotlin 5.1.0`, `kotlinx-coroutines-test 1.7.3`
  (`gradle/libs.versions.toml:6,68,69,67`) sunt deja pe classpath-ul de test;
- `org.nanohttpd:nanohttpd:2.3.1` este `implementation` (`app/build.gradle.kts:555`), deci
  `IHTTPSession` poate fi mock-at dacă e nevoie;
- nu introducem Robolectric: ar adăuga o dependență de test grea, cu descărcări de artefacte Android
  la rulare, ceea ce contrazice politica de dependențe din `SECURITY.md`.

Regula de aur a extragerii: **codul mutat este identic**, nu rescris. Fiecare helper primește un test
care fixează comportamentul actual, iar apelantul devine o delegare de o linie. Astfel un build eșuat
sau o regresie ar însemna o greșeală de transcriere, nu o schimbare de semantică.

## Scope

| Fișier | Rol |
| :---- | :---- |
| `app/src/main/java/com/ai/assistance/operit/ui/features/chat/webview/workspace/WorkspaceAccessPolicy.kt` | nou: token, origin, headere proxy |
| `app/src/main/java/com/ai/assistance/operit/integrations/http/BearerToken.kt` | nou: header `Authorization` + comparație constant-time |
| `LocalWebServer.kt`, `ExternalChatHttpServer.kt`, `WebChatHttpBridge.kt` | delegare către helperi, fără schimbare de comportament |
| `app/src/test/java/.../workspace/WorkspaceAccessPolicyTest.kt` | teste JVM |
| `app/src/test/java/.../integrations/http/BearerTokenTest.kt` | teste JVM |
| `app/src/test/java/.../workspace/WorkspaceFilePreviewSupportTest.kt` | teste JVM: `Content-Type`-ul servit de `LocalWebServer` și alegerea editor/previzualizare |

## Steps

1. [01_workspace_access_policy.md](./01_workspace_access_policy.md) — token, CORS și proxy pentru
   `LocalWebServer`
2. [02_bearer_token_seam.md](./02_bearer_token_seam.md) — bearer token-ul partajat de cele două
   servere HTTP externe
3. [03_workspace_mime_and_preview.md](./03_workspace_mime_and_preview.md) — tabelul de extensii care
   decide `Content-Type` și previzualizarea directă

## Ce nu face acest plan / Out of scope

- Nu adaugă emulator în CI. Rularea `androidTest` rămâne documentată în
  `docs/doc-src/dev-core/CONTRIBUTING.md` ca verificare locală
  (`./gradlew :app:connectedCloneDebugAndroidTest`).
- Nu testează rutarea NanoHTTPD sau injectarea de HTML; acelea au nevoie de un server pornit și de un
  context Android real. MIME-urile sunt acoperite de pasul 03 doar pentru extensiile din tabelul
  explicit: ramura `else` apelează `MimeTypeMap` și rămâne netestabilă pe JVM.
- Nu crește artificial numărul de teste: fiecare test corespunde unei reguli de securitate sau unei
  decizii de compatibilitate deja livrate.

## Completion

- [DONE] 01 `WorkspaceAccessPolicy` + `WorkspaceAccessPolicyTest` (11 teste)
- [DONE] 02 `BearerToken` + `BearerTokenTest` (7 teste)
- [DONE] 03 `WorkspaceFilePreviewSupportTest` (12 teste) pentru tabelul de extensii care decide
  `Content-Type` și previzualizarea directă; fără nicio modificare în `app/src/main`
- [ ] rulare `:app:testCloneDebugUnitTest` în CI verde (niciun build local, conform regulii fork-ului)
- [ ] mutarea folderului în `docs/.META/Legacy/TODO/` într-un PR separat
