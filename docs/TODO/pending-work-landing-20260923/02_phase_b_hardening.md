# Faza B — Hardening incremental (după Faza A)

Language: Română + Engleză. No Chinese.

Toate itemele sunt **citire / verificare întâi**; editare doar dacă lipsește ceva.
Fără build local (CI `pr-check.yml`). Fără fallback code.

---

## B1 — Verificare acoperire path-scoping în `pr-check.yml`

**Fișier:** `.github/workflows/pr-check.yml`

**Pași:**

1. Citește `paths:` / `paths-ignore:` pentru fiecare job/lane.
2. Confirmă că schimbările din Faza A nu sar checks:
   - `gradle/libs.versions.toml` → lane JVM / dependency (A1)
   - `app/src/main/java/**/MCPToolExecutor.kt` → lane app compile/test (A2)
   - `ui/features/chat/**` + tests (A3)
3. Dacă un path relevant e pe `paths-ignore` sau într-un lane care nu-l rulează:
   - notează în `docs/TODO/pending-work-landing-20260923/02_phase_b_hardening.md`
   - propune minimul de edit în workflow (un singur path adăugat)
   - **nu** edita workflow-ul fără confirmarea userului (CI e sensibil)
4. La final: `[DONE]` pe acest capitol din `02_...md`.

**Scope:** citire `pr-check.yml`; editare numai cu OK explicit.

---

## B2 — i18n localization gate pe PR-uri care ating resurse

**Există:** `check_localizations.py` (folosit în planul `romanian_locale`, gate zero errors).

**Pași:**

1. Găsește cum e invocat (workflow existent sau script manual) — `rg check_localizations`.
2. Confirmă că un PR care modifică `app/src/main/res/values*/strings.xml`:
   - rulează gate-ul contra baseline (`--base <upstream sha> --candidate HEAD`)
   - compară **key set** (nu adăugări incomplete de chei)
3. Dacă lipsește pe PR lane: propune pas în `pr-check.yml` (doar pe paths `values*/strings.xml`).
   Prezentare userului înainte de commit.
4. Politică conținut: **RO + EN** sunt limbile prioritare pentru efortul nostru;
   nu ștergem alte locale existente (es/id/ko/ms/pt-rBR) — Don't Break Userspace.
5. `[DONE]` pe capitol.

**Scope:** citire script + workflow; editare minimă cu OK.

---

## B3 — Curățare TODO/FIXME low-density

**Scanare inițială** (count pe fișier, `app/src/main/java`):

| Fișier | Marcaje |
|--------|---------|
| `core/config/FunctionalPrompts.kt` | 4 |
| `ui/features/toolbox/screens/ToolboxScreen.kt` | 1 |
| `ui/features/chat/webview/workspace/editor/language/KotlinSupport.kt` | 1 |
| `ui/features/chat/screens/AIChatScreen.kt` | 1 |
| `ui/features/chat/components/lazy/LazyListState.kt` | 1 |
| `ui/features/chat/components/lazy/LazyLayoutScrollScope.kt` | 1 |
| `ui/features/chat/components/lazy/LazyLayoutItemAnimator.kt` | 1 |
| `data/repository/UIHierarchyManager.kt` | 1 |
| `api/speech/SherpaMnnSpeechProvider.kt` | 1 |

**Pași:**

1. `rg -n 'TODO|FIXME' app/src/main/java -g '*.kt'` → listă completă actuală.
2. Pentru fiecare: e datorie reală sau comentariu istoric?
   - Reală + mică (ex. mesajul TODO din `FunctionalPrompts.kt`): rezolvi **codul real**
     sau elimini marcajul dacă e mort. **Nu** înlocui cu fallback.
   - Mare / riscant: notezi în `02_phase_b_hardening.md` ca backlog, nu rezolvi acum.
3. Un PR `chore:` mic pentru curățarea sigură (maxim câteva fișiere).
4. `[DONE]` pe capitol.

**Scope:** fișierele din tabel + orice TODO mic confirmat; **fără** refactor mare.

---

## B4 — README(RO) sync cu README(E)

**Pași:**

1. `diff` mental / side-by-side: `README.md` (EN) vs `README(RO).md`.
2. Notează secțiuni lipsă / outdated în RO (features, build, CI, contribuire).
3. Actualizează **doar** `README(RO).md` (RO + EN în conținutul tehnic unde e natural).
   **Doc-only PR**, separat de cod.
4. `[DONE]` pe capitol.

**Scope:** `README(RO).md` (opțional `README(E).md` dacă are erari flagrante).

---

## Ordine Faza B

```
B1 (citire CI) → B2 (citire i18n gate) → B3 (curățare marcaje) → B4 (docs RO)
```

- B1/B2 pot rula **în paralel** cu Faza A (sunt read-only).
- B3/B4 **după** Faza A merged, ca să nu competing cu PR-uri de cod.
- Orice edit de workflow: întâi prezentare userului, apoi commit.

## Completion

- B1: `[DONE]` în acest fișier după audit paths
- B2: `[DONE]` — gate pe lane sau „already covered” notat
- B3: `[DONE]` — PR chore merged sau backlog listat
- B4: `[DONE]` — README(RO) syncat
