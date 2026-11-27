# Pasul 3 — MIME-urile și previzualizarea fișierelor din workspace

## De ce acest pas / Why this step

Pașii 1 și 2 au scos deciziile de securitate din `LocalWebServer` și din cele două servere HTTP
externe. A mai rămas o a treia decizie a aceluiași server care este, la rândul ei, o funcție pură:
`Content-Type`-ul cu care workspace-ul servește un fișier.

`LocalWebServer.kt:933` răspunde cu `workspaceMimeTypeForPath(uri)`, iar
`FileManager.kt:375` și `:378` folosesc aceeași funcție plus `workspaceShouldOpenAsDirectPreview`
ca să aleagă între editorul de text și previzualizarea directă (imagine, audio, video, PDF, Word,
Excel). Ambele sunt definite în
`app/src/main/java/com/ai/assistance/operit/ui/features/chat/webview/workspace/WorkspaceFilePreviewSupport.kt`
și lucrează numai cu `String`, deci nu au nevoie de niciun seam nou: pot fi testate așa cum sunt.

Un `Content-Type` greșit nu este doar cosmetic. `text/html` servit pentru un fișier care nu este
HTML înseamnă HTML executat în WebView-ul aplicației, iar `application/octet-stream` pentru o
imagine înseamnă previzualizare pierdută. Tabelul de extensii merită fixat prin teste.

## Ce este acoperit / What is covered

`app/src/test/java/com/ai/assistance/operit/ui/features/chat/webview/workspace/WorkspaceFilePreviewSupportTest.kt`,
JUnit 4, fără mock-uri, 12 teste.

| Test | Regulă fixată |
| :---- | :---- |
| `text and markup extensions resolve to their mime type` | `html`/`htm`, `css`, `js`/`mjs`/`cjs`, `json`, `txt`/`md`/`log`, `xml`, `csv`, `yaml`/`yml` |
| `the extension is matched case-insensitively` | `SHOT.PNG`, `Page.Html`, `CONFIG.JSON` (funcția face `lowercase(Locale.ROOT)`) |
| `image audio and video extensions resolve to their mime type` | cele 9 formate imagine, 7 audio, 7 video din `when` |
| `office documents resolve to their mime type` | `pdf`, `doc`, `docx`, `xls`, `xlsx`, inclusiv șirurile lungi OOXML |
| `a path without a usable extension stays opaque binary` | `LICENSE`, `bin/tool`, `archive.` -> `application/octet-stream` |
| `media and office documents open directly, text files open in the editor` | `workspaceShouldOpenAsDirectPreview`: media și documente -> `true`; `html`, `md`, `json`, `csv` și fișierele fără extensie -> `false` |
| `an explicit mime type wins over the path` | `OpenFileInfo(mimeType = "application/pdf")` cu cale `report.dat` -> `resolvedMimeType == "application/pdf"` |
| `a blank mime type falls back to the path` | `mimeType = ""` (valoarea implicită) -> `workspaceMimeTypeForPath(path)`; `name` implicit este `File(path).name` |
| `html and markdown are recognised by name and ignore case` | `isHtml` pentru `.html`/`.HTM`, `isMarkdown` pentru `.markdown`; cele două nu se suprapun |
| `a plain text file is neither media nor a read-only document` | `text/plain` -> toate predicatele de previzualizare `false` |
| `audio and video are media previews but not documents` | `isMediaPreviewable == true`, `isReadOnlyDocumentPreviewable == false` |
| `word and spreadsheet documents are read-only previews` | `isWordDocument` / `isSpreadsheetDocument` se exclud reciproc, ambele intră în `isReadOnlyPreview` |

## Ce nu se poate testa pe JVM / What stays out of reach

- **Ramura `else` a `when`-ului** apelează `MimeTypeMap.getSingleton().getMimeTypeFromExtension(...)`,
  adică Android. Niciun modul nu configurează `testOptions.unitTests.isReturnDefaultValues`, deci un
  test care ajunge acolo aruncă „Method ... not mocked". Toate fixturele folosesc fie o extensie din
  tabelul explicit, fie o cale fără extensie utilă, care se oprește la verificarea `isBlank()`
  înainte de a intra în `when`.
- **`buildWorkspacePreviewUri`** construiește `android.net.Uri` și citește `LocalWebServer.WORKSPACE_PORT`;
  are nevoie de un instrumented test, nu de unul JVM.
- **Rutarea NanoHTTPD** (ce handler primește o cerere, ordinea prefixelor) rămâne netestată, ca și
  până acum: cere un server pornit.

## Riscuri / Risks

- Testul fixează tabelul exact așa cum este acum. O extensie nouă adăugată în `when` nu sparge niciun
  test; ea trebuie adăugată explicit în testul corespunzător, iar asta este o alegere conștientă:
  acoperirea crește doar când cineva scrie și asserts-urile.
- Dacă într-un PR viitor se activează `testOptions.unitTests.isReturnDefaultValues = true` ca să se
  poată testa ramura `else`, rezultatul devine `application/octet-stream` pentru **orice** extensie
  necunoscută, inclusiv pentru cele pe care `MimeTypeMap` le-ar rezolva corect pe dispozitiv. Un astfel
  de test ar confirma o valoare implicită, nu comportamentul real; de aceea ramura rămâne documentată
  ca netestabilă în loc să fie acoperită artificial.
- `OpenFileInfo` este `@Serializable` și stă în `FileManager.kt`, un fișier cu mult Compose în jur.
  Testul construiește numai data class-ul și citește proprietățile de extensie din
  `WorkspaceFilePreviewSupport.kt`; nu atinge niciun `@Composable`. Dacă `OpenFileInfo` ar primi un
  câmp cu tip Android, testul ar trebui mutat în `androidTest`.

## Livrat / Landed

- `app/src/test/java/com/ai/assistance/operit/ui/features/chat/webview/workspace/WorkspaceFilePreviewSupportTest.kt`
  (nou, 12 teste). Niciun fișier din `app/src/main` nu a fost modificat de acest pas: funcțiile erau
  deja `internal` și deja fără dependențe Android în ramurile testate.
- Suita JVM a fork-ului ajunge la 77 de fișiere și 444 de metode `@Test`.
- Fișierul nu a fost compilat aici (regula fork-ului: fără Gradle local); validarea vine din
  `:app:testCloneDebugUnitTest` în CI.

[DONE]
