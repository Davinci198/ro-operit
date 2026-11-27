# Pasul 1 — audit de pregătire / readiness audit

Scop: stabilește, doar din cod și configurație, ce se schimbă la `targetSdk = 36` și ce este deja
pregătit. Nicio comandă Gradle nu a fost rulată (regula fork-ului: CI sau nimic).

## Deja pregătit / Already 36-ready

| Domeniu | Dovezi | Concluzie |
| :---- | :---- | :---- |
| Edge-to-edge | `ui/theme/Theme.kt:193` apelează deja `WindowCompat.setDecorFitsSystemWindows(window, false)`; `values/themes.xml` și `values-night/themes.xml` setează `android:windowLayoutInDisplayCutoutMode = shortEdges`; inset-urile sunt consumate în `ui/main/components/AppContent.kt`, `ui/features/websession/browser/WebSessionBrowserScreen.kt`, `ui/common/composedsl/ToolPkgComposeDslScreen.kt` și în dialoguri | Android 15 impune edge-to-edge pentru targetSdk 35+; aplicația este deja în acest mod |
| Predictive back | `AndroidManifest.xml:91` declară `android:enableOnBackInvokedCallback="true"`; `ui/main/MainActivity.kt:436` folosește `onBackPressedDispatcher.addCallback`; nu există `Activity.onBackPressed()` suprascris și nici `KEYCODE_BACK` în `onKeyDown` pentru navigare (singurele apariții `KEYCODE_BACK` sunt în `PhoneAgent.kt`/`AccessibilityUITools.kt`, adică automatizare, nu navigare) | La targetSdk 36 predictive back este implicit; nu există cod legacy de back care să înceteze să mai fie apelat |
| Foreground services | `AndroidManifest.xml` declară `foregroundServiceType` pentru toate serviciile (`:318` dataSync, `:324` mediaProjection, `:341` dataSync\|microphone\|specialUse, `:392` specialUse) și permisiunile `FOREGROUND_SERVICE_*` corespunzătoare (`:15`, `:16`, `:17`, `:31`, `:56`, `:57`) | Cerința Android 14 (deja activă la targetSdk 34) rămâne satisfăcută |
| Notificări Android 16 | `coreKtx = "1.18.0"` în `gradle/libs.versions.toml`; planul `docs/TODO/issue-854-android16-foreground-notification/` este `[DONE]` | `BadForegroundServiceNotificationException` de pe Android 16 este deja tratat |
| 16 KB page size | `-Wl,-z,max-page-size=16384` în `app/src/main/cpp/CMakeLists.txt` (funcția `enable_16kb_page_alignment`, aplicată la `streamnative` și `toolpkgwasm`), `llm/llama/CMakeLists.txt:44`, `llm/mnn/CMakeLists.txt:109-112`, `avator/mmd/CMakeLists.txt:104`, `avator/fbx/CMakeLists.txt:42`, `quickjs/src/main/cpp/CMakeLists.txt:82`; `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON` în `llm/mnn`, `llm/llama`, `avator/mmd`, `avator/fbx`, `avator/dragonbones` | Singura bibliotecă nativă precompilată din repo, `llm/mnn/src/main/jniLibs/arm64-v8a/libsherpa-mnn-jni.so`, are `p_align = 0x4000` în toate segmentele `PT_LOAD` (verificat prin citirea header-elor ELF); `app/src/main/jniLibs/` conține doar `.keep` |
| Lanț de build | AGP 8.13.2, Kotlin 2.2.21 (`gradle/libs.versions.toml:2-3`), Gradle 8.13 (`gradle/wrapper/gradle-wrapper.properties:4`), NDK 27.0.12077973 (`gradle.properties`, `operit.ndkVersion`) | AGP >= 8.5.1 este cerut pentru împachetarea aliniată la 16 KB; versiunile de aici sunt mult peste |
| Orientare / redimensionare | Manifestul nu declară `screenOrientation` și are `android:resizeableActivity="true"` (`:85`) | Android 16 ignoră restricțiile de orientare pe ecrane mari; aici nu există restricții de ignorat |

## Modificare pregătitoare livrată în acest pas / Prerequisite landed here

`:app` era singurul modul nativ care **nu** trecea `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON`.
`app/src/main/cpp/CMakeLists.txt` aliniază manual `streamnative` și `toolpkgwasm`, dar construiește
și ținte third-party partajate (`ncnn`, `sherpa-ncnn-jni`), care sub NDK 27 (fanionul este OFF
implicit; devine implicit ON abia în NDK 28) rămâneau cu segmente de 4 KB.

Acum `app/build.gradle.kts` adaugă:

```kotlin
arguments += listOf("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
```

Este o schimbare de build fără efect de comportament pe dispozitive cu pagini de 4 KB; pe
dispozitive cu pagini de 16 KB face ca toate bibliotecile native construite de `:app` să se poată
încărca.

## Blocaj / What blocks the bump

Culorile barelor de sistem. Detalii și soluție în [02_system_bar_colours.md](./02_system_bar_colours.md).

Pe scurt: `Window.setStatusBarColor`, `Window.setNavigationBarColor` și
`Window.isNavigationBarContrastEnforced` sunt dezactivate pentru aplicațiile care țintesc SDK 35+.
`ui/theme/Theme.kt` le folosește ca mecanism principal de afișare a unei culori alese de utilizator
(`:206-217` status bar, `:220-239` navigation bar), iar setarea este expusă în
`ui/features/settings/screens/theme/ThemeSettings{Basic,Background,Interface}Tab.kt`. Fără o
redesenare în Compose, bump-ul ar strica o funcționalitate existentă pe Android 15/16 — de aceea
pasul 2 trebuie să precedă pasul 3.

## Verificat, fără impact / Checked, no impact

- `android:requestLegacyExternalStorage="true"` (`AndroidManifest.xml:90`) este ignorat începând cu
  API 30; nu se schimbă la 36.
- `android:usesCleartextTraffic="true"` și `networkSecurityConfig` rămân valabile; politica este
  documentată în `SECURITY.md`.
- Permisiunile de stocare și comportamentul `WorkspaceDocumentsProvider` nu depind de targetSdk 36.
- Testele JVM existente (`app/src/test/`, 73 de fișiere) nu ating API-uri dependente de targetSdk.

[DONE]
