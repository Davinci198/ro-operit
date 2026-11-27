# Pasul 2 — culorile barelor de sistem / system bar colours in Compose

## Vechea implementare / Old behaviour

`ui/theme/Theme.kt` alege o culoare pentru bara de stare și una pentru bara de navigare și le
aplică prin API-urile de fereastră:

```kotlin
// Theme.kt:206-217
val statusBarColor = when {
    statusBarTransparent -> Color.Transparent.toArgb()
    useBackgroundImage && backgroundImageUri != null -> Color.Transparent.toArgb()
    useCustomStatusBarColor && customStatusBarColorValue != null -> customStatusBarColorValue!!.toInt()
    else -> colorScheme.primary.toArgb()
}
window.statusBarColor = statusBarColor
insetsController?.isAppearanceLightStatusBars = !isColorLight(Color(statusBarColor))

// Theme.kt:220-239
window.isNavigationBarContrastEnforced = false | true
window.navigationBarColor = android.graphics.Color.TRANSPARENT | colorScheme.background.toArgb()
insetsController?.isAppearanceLightNavigationBars = !darkTheme | !isColorLight(colorScheme.background)
```

Aceste apeluri funcționează azi inclusiv pe Android 15/16, pentru că aplicația țintește SDK 34:
deprecierile se activează în funcție de `targetSdk`, nu de versiunea dispozitivului.

## Ce se strică la targetSdk 35+ / What breaks

Pentru aplicațiile care țintesc SDK 35 sau mai nou, platforma dezactivează:

- `Window.setStatusBarColor`
- `Window.setNavigationBarColor`
- `Window.setNavigationBarDividerColor`
- `Window.isNavigationBarContrastEnforced`

și impune edge-to-edge fără opt-out (`android:windowOptOutEdgeToEdgeEnforcement` este ignorat).
Consecința concretă aici: culoarea personalizată a barei de stare — setare expusă utilizatorului în
`ui/features/settings/screens/theme/ThemeSettingsBasicTab.kt`,
`ThemeSettingsBackgroundTab.kt` și `ThemeSettingsInterfaceTab.kt`, plus
`android:statusBarColor` din `values-night/themes.xml:15` — ar înceta să mai aibă vreun efect, iar
bara de navigare ar rămâne transparentă peste conținut.

`isAppearanceLightStatusBars` / `isAppearanceLightNavigationBars` **continuă să funcționeze**; ele
controlează culoarea pictogramelor, nu fundalul.

## Noua implementare / Intended new behaviour

Culoarea devine conținut desenat de Compose, în aceeași regiune în care platforma desena scrima:
un `Box` de înălțimea inset-ului, suprapus peste `content()`. Comportamentul este identic cu cel
actual — pe Android <= 14 se desenează aceeași culoare de două ori (scrima platformei + `Box`-ul),
pe Android 15+ doar `Box`-ul.

1. Culorile se scot din `SideEffect` în valori calculate în corpul composable-ului, ca să poată fi
   folosite și de desenare, nu doar de API-urile de fereastră.
2. În rădăcina temei (acolo unde `content()` este deja încadrat de `BoxWithConstraints` /
   `Box(Modifier.fillMaxSize())`, `Theme.kt:338-350`), după `content()`:

```kotlin
// Status bar: same colour the window used to paint. Collapses to zero height in immersive mode,
// because WindowInsets.statusBars is empty while the bar is hidden.
Box(
    Modifier
        .fillMaxWidth()
        .windowInsetsTopHeight(WindowInsets.statusBars)
        .background(statusBarColour)
)
// Navigation bar: drawn over the bottom inset, as navigationBarColor used to be.
Box(
    Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .windowInsetsBottomHeight(WindowInsets.navigationBars)
        .background(navigationBarColour)
)
```

3. Importuri noi în `Theme.kt`: `androidx.compose.foundation.layout.WindowInsets`,
   `androidx.compose.foundation.layout.statusBars`,
   `androidx.compose.foundation.layout.navigationBars`,
   `androidx.compose.foundation.layout.windowInsetsTopHeight`,
   `androidx.compose.foundation.layout.windowInsetsBottomHeight`,
   `androidx.compose.foundation.layout.fillMaxWidth`, `androidx.compose.ui.Alignment`.
   (`androidx.compose.foundation.background` și `Box` sunt deja importate.)
4. Apelurile `window.statusBarColor` / `window.navigationBarColor` /
   `isNavigationBarContrastEnforced` se păstrează: sunt singura cale pe Android <= 14, unde
   `Box`-urile desenează aceeași culoare. Nu se adaugă niciun branch de tip fallback — aceeași
   valoare este aplicată pe ambele căi.

## De ce funcționează fără regresie / Why this is not a regression

- Ecranele care deja își paddează conținutul (`AppContent.kt`, `WebSessionBrowserScreen.kt`,
  `ToolPkgComposeDslScreen.kt`, dialogurile) nu își schimbă layout-ul: banda desenată acoperă exact
  zona pe care platforma o acoperea înainte.
- Modul imersiv (`statusBarHidden`, `Theme.kt:196-200`) ascunde bara, deci inset-ul este 0 și
  `Box`-ul are înălțime zero — nu este nevoie de o condiție suplimentară.
- Cazurile cu fundal imagine/transparent produc `Color.Transparent`, deci banda este invizibilă,
  exact ca scrima transparentă de azi.
- Ferestrele de dialog au propriul `Window`; tema nu le desenează benzile, la fel ca înainte.

## Scope

- `app/src/main/java/com/ai/assistance/operit/ui/theme/Theme.kt` (singurul fișier modificat)
- fără schimbări de resurse, de preferințe sau de text localizat: setările existente
  (`custom_status_bar_color`, `status_bar_transparent`, `status_bar_hidden`) își păstrează sensul

## Verificare necesară / Verification required

Rulează doar pe dispozitiv sau emulator (fork-ul nu face build local):

1. Android 15/16, temă cu culoare personalizată a barei de stare → culoarea este vizibilă,
   pictogramele au contrastul corect.
2. Android 15/16, `statusBarTransparent` activ → bară transparentă, conținutul se vede sub ea.
3. Android 15/16, fundal imagine + `statusBarHidden` → mod imersiv, nicio bandă colorată.
4. Android 14 sau mai vechi → aspect identic cu build-ul actual la targetSdk 34.
5. Rotire și tastatură deschisă (`imePadding`) → benzile rămân lipite de marginile corecte.

## Livrat / Landed

Codul acestui pas este în `app/src/main/java/com/ai/assistance/operit/ui/theme/Theme.kt`:

- două valori calculate înainte de `SideEffect` — `statusBarBandColour` și `navigationBarBandColour`
  — care reproduc exact alegerea anterioară (immersiv, transparent, fundal imagine, culoare
  personalizată, altfel `colorScheme.primary`; respectiv transparent cu fundal imagine, altfel
  `colorScheme.background`);
- `SideEffect` folosește aceleași valori pentru `window.statusBarColor`,
  `window.navigationBarColor` și pentru `isAppearanceLight*`, deci fereastra și banda desenată nu pot
  diverge;
- după `MaterialTheme(... content = content)`, în același `BoxWithConstraintsScope`, două `Box`-uri:
  unul cu `windowInsetsTopHeight(WindowInsets.statusBars)`, unul cu
  `align(Alignment.BottomCenter)` + `windowInsetsBottomHeight(WindowInsets.navigationBars)`;
- importuri noi: `WindowInsets`, `statusBars`, `navigationBars`, `windowInsetsTopHeight`,
  `windowInsetsBottomHeight`, `fillMaxWidth`, `Alignment`.

Efecte conexe:

- `customStatusBarColorValue!!` a dispărut (smart cast pe un `val` local), deci poarta de Kotlin
  smells rămâne silențioasă și repo-ul are un `!!` mai puțin;
- la targetSdk 34 aspectul este identic: platforma desenează scrima, iar banda desenează aceeași
  culoare în aceeași zonă;
- benzile nu au `pointerInput`, deci nu interceptează atingerile.

Codul nu a fost compilat aici (regula fork-ului: CI sau nimic), iar lista de verificare de mai sus
rămâne de făcut pe un dispozitiv Android 15/16.

[DONE] codul; verificarea pe dispozitiv rămâne deschisă.
