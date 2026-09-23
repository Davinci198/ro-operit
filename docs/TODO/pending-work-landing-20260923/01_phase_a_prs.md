# Faza A — PR-uri pendiente (smallest first)

Language: Română + Engleză. No Chinese.

**Audit 2026-09-23 pe `main` @ `4d39432e`:** A1–A3 sunt **deja implementate** în cod.
Nu se mai deschid PR-uri de cod pentru ele — rămân doar note de status în indexuri
și A4 (arhivare), pe branch-uri docs.

Reguli generale pentru orice PR din fază:

- Branch: conform `docs/doc-src/dev-core/CONTRIBUTING.md` (Create Pull Request).
- **Nimic direct pe `main`** — user directive.
- Fără compilare/test local decât dacă userul cere explicit (CI `pr-check.yml` face verificarea).
- Zero fallback / rollback / degrade code.
- Commit messages în engleză; discuția în PR în engleză.
- La final: marchează pasul `[DONE]` în documentul corespunzător.

---

## A1 — Android 16 foreground notification (`issue-854`) — ALREADY ON MAIN

**Cod:** `gradle/libs.versions.toml` → `coreKtx = "1.18.0"` (commit `767a8652`, #854).
Pasul `01-update-notification-compat.md` are `[DONE]`.

**Rămas:** în `issue-854-.../index.md`: secțiunea PR („待创建”) → notă landed on main +
`[DONE]` pe index. (Documentul istoric rămâne în chineză — nu îl rescriem; politica
RO+EN se aplică documentelor **noi**.)

**PR:** inclus în branch-ul `docs/pending-work-landing`.

---

## A2 — MCP tool argument integrity (`issue-858`) — ALREADY ON MAIN

**Cod:** `MCPToolExecutor.kt` — `validateParameters` → `findArgumentIntegrityViolation`
blochează params cu `U+FFFD`; log: `parameter`, `replacementCount`, `characterOffset`,
`utf8ByteOffset` (fără conținut). Commits `17df7f9e`, `45b17519`. Pas `[DONE]`.

**Rămas:** index → PR note + `[DONE]`.

**PR:** inclus în `docs/pending-work-landing`.

---

## A3 — Pending message queue (`issue-853`) — ALREADY ON MAIN

**Cod:**
- `PendingMessageQueueStore.kt` — state per `chatId` (messages, expanded, auto-dequeue).
- `ChatViewModel.kt` — `pendingMessageQueueStore`, enqueue/remove/restore/setExpanded.
- `AIChatScreen.kt` — citește `pendingMessageQueueStates[chatId]` (nu `remember(chatId)`).
- Test: `app/src/test/.../PendingMessageQueueStoreTest.kt`.

Pași `01_…` / `02_…` au `[DONE]`.

**Rămas:** index → PR note + `[DONE]`.

**PR:** inclus în `docs/pending-work-landing`.

---

## A4 — Archive `[DONE]` plan folders (docs-only PR) — NOT YET

**Nu se combină cu cod** (protocol: docs PR separat). **Branch:** `docs/archive-completed-todos`.

1. `rg -l '\[DONE\]' docs/TODO --glob 'index.md'` → ~33 candidates (excl. planul curent
   pending-work atâta timp cât nu e final).
2. `mkdir -p docs/.Meta/Legacy/TODO` (folderul nu există încă).
3. `git mv docs/TODO/<name> docs/.Meta/Legacy/TODO/<name>` pentru fiecare.
4. **Nu** muța foldere fără `[DONE]` sau cu `give-up_`.
5. Rulează **după** ce 853/854/858 primesc `[DONE]` pe index (pe acest branch).

**PR:** `docs: archive completed TODO folders to Legacy`

---

## Ordine actualizată

```
docs/pending-work-landing   ← acum (plan + status 853/854/858)
docs/archive-completed-todos ← următor (A4)
B1–B4                        ← opțional, după A4
```
