---
title: targetSdk 36 migration
repo: https://github.com/Davinci198/ro-operit
base: 018641b9
status: implemented on this branch; device verification on Android 15/16 still open
language_policy: Romanian and English only — no Chinese content in this plan
---

# Migrare targetSdk 34 -> 36 / targetSdk 36 migration

## Situația actuală / Current state

- `:app` declară `compileSdk = 36` (`app/build.gradle.kts:250`) dar `targetSdk = 34`
  (`app/build.gradle.kts:289`). Modulele native declară și ele `targetSdk = 34`
  (`llm/mnn:17`, `llm/llama:15`, `avator/mmd:15`, `avator/fbx:15`), în timp ce
  `tools/shower/app:16` este deja la 36, iar template-ul de aplicație Android livrat în assets
  (`app/src/main/assets/templates/android/app/build.gradle.kts:13`) este la 35.
- Un decalaj `compileSdk` > `targetSdk` înseamnă că aplicația rulează pe Android 15/16 cu
  comportamentul de compatibilitate al lui Android 14: nu primește nici beneficiile, nici semnalele
  de testare ale platformei noi, iar politicile de publicare (Play cere targetSdk 35+ pentru
  aplicații și actualizări noi) nu sunt îndeplinite.

## Intentie / Goal

Aduce `targetSdk` la 36 pentru `:app` și pentru modulele native, **fără** a pierde funcționalități
vizibile. Singurul blocaj real identificat de audit este colorarea barelor de sistem
(`window.statusBarColor` / `window.navigationBarColor` devin no-op pentru aplicațiile care țintesc
SDK 35+), care este o setare expusă utilizatorului în `ThemeSettings*Tab.kt`.

## Scope

| Fișier | Rol |
| :---- | :---- |
| `app/build.gradle.kts` | `targetSdk` + argumentele CMake (16 KB page size) |
| `llm/mnn/build.gradle.kts`, `llm/llama/build.gradle.kts`, `avator/mmd/build.gradle.kts`, `avator/fbx/build.gradle.kts` | `targetSdk` al modulelor native |
| `app/src/main/java/com/ai/assistance/operit/ui/theme/Theme.kt` | desenarea culorii barelor de sistem în Compose |
| `app/src/main/res/values-night/themes.xml` | `android:statusBarColor` rămâne (valabil pe Android <= 14) |

Nu fac parte din scope: `minSdk`, `compileSdk`, flavor-urile, semnarea, workflow-urile.

## Reguli respectate / Rules honoured

- Fork-ul nu rulează build/test local: verificarea se face în CI sau pe dispozitiv, la cererea
  explicită a utilizatorului (`docs/TODO/pending-work-landing-20260923/index.md`, secțiunea 4).
- Fără cod de fallback/degradare: culoarea barelor se desenează efectiv, nu se „încearcă" două căi.
- Comentariile și documentele noi: engleză (cod) și română + engleză (planuri).

## Steps

1. [01_readiness_audit.md](./01_readiness_audit.md) — ce este deja pregătit pentru 36 și ce nu
2. [02_system_bar_colours.md](./02_system_bar_colours.md) — blocajul: culorile barelor de sistem
3. [03_bump_and_verify.md](./03_bump_and_verify.md) — bump-ul propriu-zis și lista de verificare

## PR

- Auditul și singura modificare pregătitoare (fanionul CMake pentru 16 KB în `:app`) fac parte din
  branch-ul curent de hardening.
- Pașii 02 și 03 se livrează împreună, într-un PR separat, după o rulare pe Android 15/16.

## Completion

- [DONE] 01 audit de pregătire (citit cod, manifest, resurse, CMake, lockfiles)
- [DONE] 01 fanion CMake `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON` pentru `:app`
- [DONE] 02 culorile barelor de sistem desenate în Compose (`ui/theme/Theme.kt`)
- [DONE] 03 `targetSdk = 36` în `:app` și în cele 4 module native
- [ ] 03 verificare pe Android 15/16 (dispozitiv sau emulator) — singurul pas rămas
- [ ] mutarea folderului în `docs/.META/Legacy/TODO/` după verificare, într-un PR separat
