# Build aplikasi Universal APK Builder

## Android Studio
Buka folder ini (Ladybug/Koala atau lebih baru, JDK 17). Gradle wrapper jar tidak disertakan; Android Studio memakai `gradle-wrapper.properties` (Gradle 8.9). Untuk CLI, jalankan sekali: `gradle wrapper --gradle-version 8.9`.

    ./gradlew assembleDebug

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Lewat GitHub Actions
Push folder ini ke repository sendiri, lalu jalankan workflow "Build Universal APK Builder" (`.github/workflows/build-app.yml`). Unduh artifact `universal-apk-builder-debug`.

Catatan: ini repository untuk aplikasi itu sendiri; berbeda dari repository tujuan build yang diatur di dalam aplikasi.
