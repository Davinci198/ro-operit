---
title: Pending Work Landing (RO + EN only)
repo: https://github.com/Davinci198/ro-operit
base: 4d39432e
status: in_progress
language_policy: Romanian and English only — no Chinese content in this plan or in new docs we write
---

# Plan de implementare — ce putem adăuga / termina în ro-operit

Implementation plan for the `Davinci198/ro-operit` fork.
All new documents and comments we write: **Română + Engleză numai** (no Chinese).

## 1. Stare de pornire / Baseline

- Branch `main` @ `4d39432e`, fork independent de AAswordman (ref: `Davinci198/fix-operit`).
- Module: `:app`, `:terminal` (submodule), `:mnn`, `:llama`, `:mmd`, `:fbx`, `:showerclient`, `:quickjs`.
- Compose + ObjectBox, flavors `clone` / `standard`.
- CI: `pr-check.yml` (path-scoped lanes), `release-clone.yml` (manual, signed APK).
- Locale-uri prezente: `values` (zh source), `values-en`, `values-ro`, `values-es`, `values-id`, `values-ko`, `values-ms`, `values-pt-rBR`.
- Romanian locale: **[DONE]** (`docs/TODO/romanian_locale`, 7195 chei, gate zero errors).
- `docs/TODO/`: 56 planuri; unele marcate `[DONE]` încă n mutate în `docs/.Meta/Legacy/TODO/`.

## 2. Politică de conținut / Content policy

- **Fără chineză** în documentele, comentariile și mesajele noi generate de noi.
- Limbile suportate pentru efortul nostru: **română + engleză**.
- Locale-urile de resurse existente (es/id/ko/ms/pt-rBR) **nu se șterg** fără cerere explicită a utilizatorului (Don't Break Userspace).
- Cod/commentarii în engleză; discuții cu utilizatorul în română.

## 3. Ce implementăm / What we implement

### Faza A — PR-uri pendiente din planuri existente (smallest first)

| # | Plan | Status pe `main` @ `4d39432e` | Efort rămas |
|---|------|--------------------------------|-------------|
| A1 | `issue-854-android16-foreground-notification` | **Livrat** — `coreKtx = "1.18.0"` în `libs.versions.toml` (`767a8652`, #854). Pasul din plan marcat `[DONE]`. Alias vechi `coreKtxVersion = "1.12.0"` (`core-ktx`) e **nefolosit** de `:app` (folosește `androidx-core-ktx`). | doar index: note „landed on main” + `[DONE]` |
| A2 | `issue-858-mcp-tool-argument-integrity` | **Livrat** — `MCPToolExecutor.validateParameters` blochează `U+FFFD` (`17df7f9e`, `45b17519`). Log: param name, count, char/UTF-8 offset — fără conținut. Pas `[DONE]`. | doar index: note + `[DONE]` |
| A3 | `issue-853-pending-message-queue` | **Livrat** — `PendingMessageQueueStore` pe `ChatViewModel` per `chatId`; `AIChatScreen` citește din VM; test `PendingMessageQueueStoreTest.kt`. Pași `[DONE]`. | doar index: note + `[DONE]` |
| A4 | `[DONE]` cleanup | **Nu încă** — `docs/.Meta/Legacy/TODO/` lipsește; ~33 planuri cu `[DONE]` în `index.md` stau în `docs/TODO/`. | `git mkdir` + `git mv` pe **branch separat** `docs/archive-completed-todos` |

### Faza B — rbD / hardening incremental

| # | Task | De ce | Notes |
|---|------|-------|-------|
| B1 | CI: verify `pr-check.yml` lanes acoperă `gradle/libs.versions.toml` și `MCPToolExecutor.kt` (paths) | A1/A2 să nu sară checks | doar citit; edit doar dacă lipsește |
| B2 | i18n gate pe PR-uri care ating `values*/strings.xml` | Să nu intre chei incomplete | `check_localizations.py` existent |
| B3 | Curățare TODO/FIXME low-hotspot (`FunctionalPrompts.kt` 4 marcatouri) | Datorie mică, izolată | fără fallback code (interzis) |
| B4 | Docs: README(RO) sync cu README(E) dacă diverg | Onboarding RO | doc-only PR |

### Faza C — opțional / only if user asks

- `github_oauth_broker`: mutate BuildConfig secret → Worker broker. **Backend în alt repo** (`assistance_web`), deploy ordonat backend→Android. Nu începem fără cerere explicită.
- Feature-uri noi i18n (es/id/ko/…): **în afara scopului** — userul vrea doar RO + EN pentru efortul nostru.
- Build/test pe telefon: **interzis** (regulă fork: CI sau nimic; user nu a cerut build).

## 4. Reguli de execuție (fork AGENTS.md + home AGENTS.md)

1. **Nu** compilăm/testăm decât la cererea explicită a userului.
2. **Zero** cod de fallback / rollback / degrade / „if no then”.
3. Branch naming conform `docs/doc-src/dev-core/CONTRIBUTING.md` (Create Pull Request).
4. PR-uri mici, un plan = un PR unde e posibil; docs separate de cod.
5. Terminăm fiecare pas cu `[DONE]` în docul de plan; la final, `git mv` folderul → `docs/.Meta/Legacy/TODO/`.
6. Chei/secrete: **niciodată** în cod sau repo (NVIA/Cline keys stau în `~/.memory/MEMORY.md`).
7. Interzis: `adb` input/reboot; editare fișiere cu Python (pin); build Gradle pe Termux.
8. Comunicare: RO cu userul, EN în cod/commits; **fără text chinez** în ce scriem.

## 5. Ordine de lucaru sugerată / Suggested order

```
A1–A3: deja pe main (verificat 2026-09-23)
  └→ docs: marchează 853/854/858 index [DONE] + note landed   [THIS BRANCH]
A4: archive [DONE] folders → docs/.Meta/Legacy/TODO/          [NEXT BRANCH]
B1–B4: după A4, dacă userul dorește
```

- Nimic direct pe `main` — totul pe branch → push → `pr-check` green → review user → merge.

## 6. Rezultat scontat / Expected result

- Toate planurile pendibile (853/854/858) au PR merged pe `main`.
- Folderele `[DONE]` arhivate; `docs/TODO/` curat pentru planuri viitoare.
- Niciun conținut chinez nou; docs noi RO+EN.
- CI verde pe toate PR-urile; fără build local.

## Steps

1. [01_phase_a_prs.md](./01_phase_a_prs.md) — A1..A4 detaliat
2. [02_phase_b_hardening.md](./02_phase_b_hardening.md) — CI/i18n/docs

## PR

- **This branch** `docs/pending-work-landing`: plan + index notes 853/854/858.
- **Next** `docs/archive-completed-todos`: A4 arhivare (separat, conform protocolului).
- B1–B4: PR-uri separate ulterioare.

## Completion

- [DONE] A1–A3 verificate pe `main` (cod + pași de plan)
- [DONE] plan salvat 2026-09-23
- [ ] index 853/854/858 marcat `[DONE]` pe acest branch
- [ ] A4 archive pe branch separat
- [ ] B1–B4 (opțional)
