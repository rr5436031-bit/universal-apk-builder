#!/usr/bin/env bash
# Menjalankan build APK sesuai framework hasil deteksi.
set -euo pipefail

WS="${GITHUB_WORKSPACE:-$PWD}"
OUT="$WS/out"
STAMP="${RUNNER_TEMP:-/tmp}/build.stamp"
BT="${BUILD_TYPE:-debug}"
CAP="$(tr '[:lower:]' '[:upper:]' <<< "${BT:0:1}")${BT:1}"
mkdir -p "$OUT"
[ -f "$STAMP" ] || touch "$STAMP"
echo "[build] framework=$FRAMEWORK tipe=$BT root=$PROJECT_ROOT"

sdk_prepare() {
  # Memakai Android SDK bawaan runner ubuntu-latest (tanpa action pihak ketiga).
  export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
  local sdkm bt
  sdkm="$(command -v sdkmanager || ls "$ANDROID_HOME"/cmdline-tools/*/bin/sdkmanager 2>/dev/null | sort -V | tail -1 || true)"
  bt="$(ls -d "$ANDROID_HOME"/build-tools/* 2>/dev/null | sort -V | tail -1 || true)"
  [ -n "$bt" ] && export PATH="$bt:$PATH"
  if [ -n "$sdkm" ]; then
    yes | "$sdkm" --licenses >/dev/null 2>&1 || true
    if [ "${NEED_NDK:-false}" = "true" ]; then
      echo "[build] Memasang NDK dan CMake"
      "$sdkm" --install "ndk;26.1.10909125" "cmake;3.22.1" >/dev/null
    fi
  else
    echo "[build] PERINGATAN: sdkmanager tidak ditemukan di $ANDROID_HOME"
  fi
}

gradle_run() {
  ( cd "$1"
    if [ -f gradlew ] && [ -f gradle/wrapper/gradle-wrapper.jar ]; then
      chmod +x gradlew
      ./gradlew --no-daemon --console=plain "assemble$CAP"
    else
      echo "[build] gradle wrapper tidak lengkap, memakai Gradle dari runner: $(gradle --version | grep '^Gradle' || true)"
      gradle --no-daemon --console=plain "assemble$CAP"
    fi )
}

npm_install() {
  if [ -f package-lock.json ]; then npm ci --no-audit --no-fund
  elif [ -f yarn.lock ]; then corepack enable && yarn install
  elif [ -f pnpm-lock.yaml ]; then corepack enable && pnpm install
  else npm install --no-audit --no-fund; fi
}

collect() {
  local n=0 f b
  while IFS= read -r f; do
    n=$((n+1)); b="$(basename "$f")"
    [ -e "$OUT/$b" ] && b="$n-$b"
    cp "$f" "$OUT/$b"
    echo "[build] APK ditemukan: $f"
  done < <(find "$1" -type f -name '*.apk' -newer "$STAMP" \
            -not -path '*/node_modules/*' -not -path '*/intermediates/*' \
            -not -path '*/.gradle/*' -not -name '*androidTest*' 2>/dev/null)
  if [ "$n" -eq 0 ]; then echo "[build] Tidak ada APK baru yang dihasilkan oleh build"; exit 1; fi
}

sign_unsigned() {
  shopt -s nullglob
  local files=("$OUT"/*unsigned*.apk)
  [ "${#files[@]}" -gt 0 ] || return 0
  local bt; bt="$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)"
  local ks ka
  if [ -n "${KEYSTORE_BASE64:-}" ]; then
    echo "$KEYSTORE_BASE64" | base64 -d > "$WS/release.jks"
    ks="$WS/release.jks"; ka="${KEY_ALIAS:-}"
    export KSP="${KEYSTORE_PASSWORD:-}"; export KP="${KEY_PASSWORD:-$KSP}"
    echo "[sign] Menandatangani dengan keystore dari GitHub Secrets"
  else
    ks="$WS/test.jks"; ka="test"; export KSP="android"; export KP="android"
    keytool -genkeypair -keystore "$ks" -storepass android -keypass android -alias test \
      -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Universal APK Builder Test" >/dev/null 2>&1
    echo "[sign] PERINGATAN: KEYSTORE_BASE64 tidak diatur. Release APK ditandatangani dengan kunci uji sementara (bukan untuk Play Store)."
  fi
  local f aligned signed
  for f in "${files[@]}"; do
    aligned="${f/unsigned/aligned}"; signed="${f/unsigned/signed}"
    "$bt/zipalign" -f -p 4 "$f" "$aligned"
    "$bt/apksigner" sign --ks "$ks" --ks-key-alias "$ka" --ks-pass env:KSP --key-pass env:KP --out "$signed" "$aligned"
    rm -f "$f" "$aligned" "$signed.idsig"
    echo "[sign] APK ditandatangani: $(basename "$signed")"
  done
}

godot_build() {
  local gv
  gv="$(grep -oE 'config/features=PackedStringArray\("[0-9]+\.[0-9]+' project.godot | grep -oE '[0-9]+\.[0-9]+' | head -1 || true)"
  if [ -z "$gv" ] || [ "${gv%%.*}" != "4" ]; then
    echo "[build] Hanya Godot 4.x yang didukung (versi terdeteksi: ${gv:-tidak diketahui})"; exit 1
  fi
  [ -f export_presets.cfg ] || { echo "[build] export_presets.cfg tidak ada. Buat preset export Android di editor Godot dan sertakan di ZIP."; exit 1; }
  local preset
  preset="$(python3 - <<'PY'
import re
t = open("export_presets.cfg").read()
for m in re.finditer(r'\[preset\.\d+\]\s+name="([^"]+)"\s+platform="([^"]+)"', t):
    if m.group(2) == "Android":
        print(m.group(1)); break
PY
)"
  [ -n "$preset" ] || { echo "[build] Preset export Android tidak ditemukan di export_presets.cfg"; exit 1; }
  local base="https://github.com/godotengine/godot/releases/download/${gv}-stable"
  curl -fsSL -o /tmp/godot.zip "$base/Godot_v${gv}-stable_linux.x86_64.zip"
  curl -fsSL -o /tmp/templates.tpz "$base/Godot_v${gv}-stable_export_templates.tpz"
  mkdir -p /tmp/godot "$HOME/.local/share/godot/export_templates/${gv}.stable" "$HOME/.config/godot"
  unzip -q -o /tmp/godot.zip -d /tmp/godot
  unzip -q -o /tmp/templates.tpz -d /tmp/tpl
  cp -r /tmp/tpl/templates/* "$HOME/.local/share/godot/export_templates/${gv}.stable/"
  printf '[gd_resource type="EditorSettings" format=3]\n[resource]\nexport/android/android_sdk_path = "%s"\nexport/android/java_sdk_path = "%s"\n' "$ANDROID_HOME" "$JAVA_HOME" > "$HOME/.config/godot/editor_settings-4.tres"
  cp "$HOME/.config/godot/editor_settings-4.tres" "$HOME/.config/godot/editor_settings-${gv}.tres"
  keytool -genkeypair -keystore /tmp/debug.keystore -storepass android -keypass android -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug" >/dev/null 2>&1
  export GODOT_ANDROID_KEYSTORE_DEBUG_PATH=/tmp/debug.keystore GODOT_ANDROID_KEYSTORE_DEBUG_USER=androiddebugkey GODOT_ANDROID_KEYSTORE_DEBUG_PASSWORD=android
  mkdir -p export_out
  local exe; exe="$(ls /tmp/godot/Godot_v*linux.x86_64 | head -1)"; chmod +x "$exe"
  "$exe" --headless --import || true
  "$exe" --headless "--export-$BT" "$preset" "$PWD/export_out/app.apk"
}

sdk_prepare

case "$FRAMEWORK" in
  android-kotlin|android-java|android-ndk)
    gradle_run "$WS/$PROJECT_ROOT" ;;
  flutter)
    cd "$WS/$PROJECT_ROOT"
    if [ -f scripts/bootstrap-android.sh ]; then
      echo "[build] Menjalankan scripts/bootstrap-android.sh milik project"
      sudo ln -sfn "$PWD" /work
      touch "$HOME/.wp-env"
      bash scripts/bootstrap-android.sh
    elif [ ! -d android ]; then
      echo "[build] Folder android/ tidak ada, dibuat dengan flutter create"
      pname="$(grep -m1 '^name:' pubspec.yaml | awk '{print $2}')"
      flutter create --platforms android --org com.example --project-name "$pname" .
    fi
    flutter pub get
    DEFS=""
    if [ -f env.json ]; then DEFS="--dart-define-from-file=env.json"; fi
    flutter build apk "--$BT" $DEFS ;;
  react-native|expo)
    cd "$WS/$PROJECT_ROOT"; npm_install
    if [ ! -d android ]; then
      if [ "$FRAMEWORK" = "expo" ]; then npx --yes expo prebuild --platform android --no-install
      else echo "[build] Folder android tidak ada di project React Native"; exit 1; fi
    fi
    gradle_run "$PWD/android" ;;
  capacitor)
    cd "$WS/$PROJECT_ROOT"; npm_install; npm run build
    [ -d android ] || npx cap add android
    npx cap sync android
    gradle_run "$PWD/android" ;;
  cordova)
    cd "$WS/$PROJECT_ROOT"
    [ -f package.json ] && npm_install
    [ -d platforms/android ] || npx --yes cordova platform add android
    npx --yes cordova build android "--$BT" ;;
  nativescript)
    cd "$WS/$PROJECT_ROOT"; npm_install
    if [ "$BT" = "release" ]; then npx --yes -p nativescript ns build android --release
    else npx --yes -p nativescript ns build android; fi ;;
  maui)
    cd "$WS/$PROJECT_ROOT"
    cs="$(ls ./*.csproj | head -1)"
    tfm="$(grep -oE 'net[0-9.]+-android' "$cs" | head -1)"
    dotnet workload install maui-android
    if [ "$BT" = "release" ]; then dotnet publish "$cs" -f "$tfm" -c Release -p:AndroidPackageFormat=apk
    else dotnet build "$cs" -f "$tfm" -c Debug -p:AndroidPackageFormat=apk; fi ;;
  godot)
    cd "$WS/$PROJECT_ROOT"; godot_build ;;
  unity)
    echo "[build] Unity sudah dibangun oleh step game-ci" ;;
  *)
    echo "[build] Framework tidak didukung: $FRAMEWORK"; exit 1 ;;
esac

if [ "$FRAMEWORK" = "unity" ]; then collect "$WS/build"; else collect "$WS/$PROJECT_ROOT"; fi
sign_unsigned
echo "[build] Selesai. Hasil: $(ls "$OUT")"
