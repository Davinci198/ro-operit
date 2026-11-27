# APK Reverse Toolkit Runtime Resources

This directory keeps the generated runtime layout and docs. The `.jar` artifacts are build outputs of `build_runtime_android_resources.ps1`, and they **are** tracked by Git: `examples/apktool/.gitignore` ignores `*.jar` everywhere in this tree except `resources/apktool/*.jar`, so the runtime ships inside the packaged toolpkg.

> **Pending rebuild (signing identity).** `app/src/main/java/com/ai/assistance/operit/core/subpack/KeyStoreHelper.kt` no longer exposes `getOrCreateKeystore(Context)`; the app now generates a per-device signing identity through `getOrCreateSigningIdentity(Context)`, which returns a `SubpackSigningIdentity` (keystore file, type, store password, alias, key password) instead of a file with the hardcoded `android` / `androidkey` credentials. `runtime_helper/src/main/java/com/operit/apkreverse/runtime/ApkReverseHelperFacade.java` has been updated to read those values, but the checked-in `apk-reverse-helper-runtime-android.jar` still contains the old call, so `sign_mode: debug` fails with `NoSuchMethodException` until the jar is regenerated. Run `examples/apktool/build_runtime_android_resources.ps1` (Windows PowerShell + JDK) and repackage the toolpkg resources after changing the helper sources.

- Runtime jars:
  - `apktool-runtime-android.jar`
  - `android-framework.jar`
  - `jadx-runtime-android.jar`
  - `apk-reverse-helper-runtime-android.jar`
- Load mode: `ToolPkg.readResource(...)` + `Java.loadJar(..., { childFirstPrefixes: [...] })`
- Runtime policy: no CLI entrypoints, no terminal subprocesses, no `runJar`; JS only coordinates parameters, resource extraction, and result shaping
- Packaging rule: every runtime jar must be an Android-loadable dex-jar containing `classes.dex`
- Expected layers:
  - `apktool-runtime-android.jar` carries `brut.androlib.*` plus the Android-only `prebuilt/android/aapt2`
  - `jadx-runtime-android.jar` carries the headless JADX APK pipeline, patches `jadx.core.dex.visitors.SaveCode` to avoid newer JDK-only `PrintWriter(File, Charset)`, and removes desktop GUI, `jadx-script`, `java-convert`, `aab-input`, `java-input`, and `raung` payload during packaging
  - `apk-reverse-helper-runtime-android.jar` carries the stable helper facade and native-analysis helpers
- Resource generation is handled by `examples/apktool/build_runtime_android_resources.ps1`
- `android-framework.jar` remains a standalone resource and is no longer duplicated inside `apktool-runtime-android.jar`
- Packaging trims the old desktop runtime payload instead of keeping Windows / Linux / macOS `aapt2` binaries around
- If `jadx-runtime-android.jar` or `apk-reverse-helper-runtime-android.jar` are missing, related tools should fail clearly instead of falling back to terminal execution
