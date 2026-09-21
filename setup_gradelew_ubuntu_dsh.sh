#!/bin/bash
# setup_gradelew_ubuntu_dsh.sh
# Configurare build local ro-operit pentru Ubuntu/DSH (ARM64) cu 4GB RAM
set -euo pipefail
REPO_ROOT="$(pwd)"
ANDROID_HOME="${ANDROID_HOME:-/home/dsh/Android}"
echo "=== SETUP GRADELEW UBUNTU DSH ==="
echo "Repo: $REPO_ROOT"
echo "Android SDK: $ANDROID_HOME"
echo "[1] Repair NDK clang symlink (ARM64 native)..."
if [ -f "$ANDROID_HOME/ndk/27.0.12077973/toolchains/llvm/prebuilt/linux-x86_64/bin/clang-18" ]; then
    ln -sf clang-18 "$ANDROID_HOME/ndk/27.0.12077973/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" 2>/dev/null || true
    echo "  clang -> clang-18 OK"
fi
echo "[2] Dummy native lib..."
mkdir -p "$REPO_ROOT/app/src/main/jniLibs/arm64-v8a"
echo "dummy" > "$REPO_ROOT/app/src/main/jniLibs/arm64-v8a/liboperit_ripgrep.so"
echo "[3] local.properties..."
echo "sdk.dir=$ANDROID_HOME" > "$REPO_ROOT/local.properties"
echo "[4] gradle.properties (4GB + AAPT2)..."
# Scriere idempotenta: set -e ar opri scriptul daca grep nu gaseste liniile, deci "|| true" e obligatoriu.
# Liniile care nu exista se adauga; liniile existente nu se dubleaza.
grep -q "^org.gradle.jvmargs=" "$REPO_ROOT/gradle.properties" 2>/dev/null || \
    echo "org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8 -XX:MaxMetaspaceSize=1024m" >> "$REPO_ROOT/gradle.properties"
grep -q "^kotlin.daemon.jvmargs=" "$REPO_ROOT/gradle.properties" 2>/dev/null || \
    echo "kotlin.daemon.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8 -XX:MaxMetaspaceSize=768m" >> "$REPO_ROOT/gradle.properties"
grep -q "^org.gradle.workers.max=" "$REPO_ROOT/gradle.properties" 2>/dev/null || \
    echo "org.gradle.workers.max=2" >> "$REPO_ROOT/gradle.properties"
grep -q "^android.aapt2.process.daemon=" "$REPO_ROOT/gradle.properties" 2>/dev/null || \
    echo "android.aapt2.process.daemon=false" >> "$REPO_ROOT/gradle.properties"
grep -q "^android.aapt2FromMavenOverride=" "$REPO_ROOT/gradle.properties" 2>/dev/null || \
    echo "android.aapt2FromMavenOverride=/opt/aapt2-custom/aapt2" >> "$REPO_ROOT/gradle.properties"
echo "[5] Branch cleanup (main only)..."
git checkout main 2>/dev/null || true
git branch | grep -v '^\* main$' | grep -v '^  main$' | xargs -r git branch -D 2>/dev/null || true
echo "=== SETUP COMPLET ==="
echo "Build command: ./gradlew assembleDebug --no-daemon -x lint -x stripCloneDebugDebugSymbols"
