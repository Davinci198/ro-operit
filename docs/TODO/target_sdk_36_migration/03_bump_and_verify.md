# Pasul 3 — bump și verificare / the bump and how to verify it

## Condiție prealabilă / Precondition

Pasul [02_system_bar_colours.md](./02_system_bar_colours.md) trebuie să fie deja în același PR sau
într-un PR anterior, verificat pe Android 15/16. Un bump fără el strică o setare existentă a
utilizatorului.

## Schimbările / The edits

```text
app/build.gradle.kts:289                  targetSdk = 34  ->  36
llm/mnn/build.gradle.kts:17               targetSdk = 34  ->  36
llm/llama/build.gradle.kts:15             targetSdk = 34  ->  36
avator/mmd/build.gradle.kts:15            targetSdk = 34  ->  36
avator/fbx/build.gradle.kts:15            targetSdk = 34  ->  36
```

De ce și modulele native: într-un modul Android library `targetSdk` nu ajunge în manifestul final
(al doilea vine din `:app`), dar este folosit de lint și de rulearea locală a testelor din modul, iar
un decalaj față de `:app` produce avertismente care nu mai corespund realității.
`tools/shower/app/build.gradle.kts:16` este deja la 36 și rămâne neschimbat.

Nu se modifică:

- `values-night/themes.xml:15` (`android:statusBarColor`) — rămâne valabil pe Android <= 14; după
  pasul 2 culoarea este desenată oricum și de Compose.
- `app/src/main/assets/templates/android/app/build.gradle.kts:13` (`targetSdk = 35`) — este un
  template pentru aplicațiile generate de utilizator, nu configurația acestui proiect; poate fi
  aliniat separat, fără legătură cu runtime-ul `:app`.
- `minSdk = 26` și `compileSdk = 36`.

## Verificare în CI / CI verification

Atingerea `app/build.gradle.kts` declanșează lane-ul complet Android din
`.github/workflows/pr-check.yml` (assemble + JVM unit tests). Comenzile echivalente local, **doar la
cererea explicită a utilizatorului** (regula fork-ului: CI sau nimic):

```bash
./gradlew :app:assembleCloneDebug
./gradlew :app:testCloneDebugUnitTest
./gradlew :app:assembleStandardDebug
```

Porțile Python nu sunt afectate de un bump de `targetSdk` (`ci/test`, `check_repo_hygiene.py`,
`check_localizations.py`, `check_markdown_links.py`), dar trebuie rulate ca de obicei.

## Verificare pe dispozitiv / Device verification (Android 15 sau 16)

| # | Scenariu | Rezultat așteptat |
| :---- | :---- | :---- |
| 1 | Lansare normală, temă implicită | bară de stare colorată ca înainte, pictograme lizibile |
| 2 | Culoare personalizată a barei de stare (Theme settings) | culoarea aleasă este vizibilă |
| 3 | `statusBarTransparent` + fundal imagine | bară transparentă, conținut sub ea, nicio bandă opacă |
| 4 | Mod imersiv (`statusBarHidden`) | nicio bară, nicio bandă colorată reziduală |
| 5 | Navigare înapoi din ecranele de setări, din chat și din WebView | predictive back funcționează, `onBackPressedDispatcher` din `MainActivity.kt:436` închide/navighează corect |
| 6 | Pornirea `AIForegroundService` și a serviciului de proiecție media | notificările apar, fără `ForegroundServiceStartNotAllowedException` sau `BadForegroundServiceNotificationException` |
| 7 | Instalare pe un dispozitiv cu pagini de 16 KB | aplicația se instalează și încarcă `libstreamnative.so`, `libtoolpkgwasm.so`, `libsherpa-ncnn-jni.so`, `libsherpa-mnn-jni.so` |
| 8 | Export subpack (semnare APK) | identitatea per-dispozitiv din `KeyStoreHelper` se creează și semnează |
| 9 | WebView workspace + `LocalWebServer` | previzualizarea fișierelor și rutele `/api/` cu token funcționează |

## Riscuri rămase / Residual risks

- Metrica textului: Android 15 elimină font padding-ul implicit și activează `elegantTextHeight`
  pentru aplicațiile care țintesc 35+. Este o diferență de câțiva pixeli pe rândurile de text, fără
  cod de corectat; se observă la scenariile 1-4.
- Comportamentele de fundal (oprirea proceselor din cache, limitele `dataSync` de 6 h/24 h) sunt
  deja active la targetSdk 34, deci nu se schimbă aici.
- Dacă un ecran nu își paddează inset-urile și se baza pe scrima platformei, banda desenată la
  pasul 2 acoperă aceeași zonă; orice diferență vizibilă înseamnă că ecranul respectiv depindea de
  o culoare de fundal de fereastră, nu de layout.

## Livrat / Landed

Bump-ul este aplicat în toate cele cinci fișiere, fiecare cu un comentariu în engleză care spune de
ce nivelul este 36 și unde este documentată migrarea:

```text
app/build.gradle.kts          targetSdk = 36
llm/mnn/build.gradle.kts      targetSdk = 36
llm/llama/build.gradle.kts    targetSdk = 36
avator/mmd/build.gradle.kts   targetSdk = 36
avator/fbx/build.gradle.kts   targetSdk = 36
```

`tools/shower/app/build.gradle.kts` era deja la 36, iar template-ul
`app/src/main/assets/templates/android/app/build.gradle.kts` rămâne la 35 (aplicație generată pentru
utilizator, nu acest proiect). Pasul 2 (benzile Compose) este livrat în același branch, deci
condiția prealabilă este îndeplinită.

Niciun build nu a fost rulat aici: `app/build.gradle.kts` este în lista de căi care declanșează
lane-ul complet Android din `pr-check.yml`, deci compilarea și `:app:testCloneDebugUnitTest` vor fi
verificate de CI la primul PR.

## Rămâne deschis / Still open

Tabelul de verificare pe dispozitiv (9 scenarii) și cele două riscuri reziduale de mai sus. Până la
o rulare pe Android 15/16, bump-ul este corect din punct de vedere al configurației, dar nevalidat
vizual.

[DONE] editurile; verificarea pe dispozitiv rămâne deschisă.
