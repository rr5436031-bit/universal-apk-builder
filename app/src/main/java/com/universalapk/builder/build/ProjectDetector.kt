package com.universalapk.builder.build

/** Pratinjau deteksi framework di sisi aplikasi. Deteksi final dan otoritatif dilakukan oleh workflow (detect.py). */
object ProjectDetector {
    data class Detection(val framework: String?, val reason: String?)

    private fun depth(p: String) = p.count { it == '/' }

    fun detect(paths: List<String>, snippets: Map<String, String>): Detection {
        fun named(n: String) = paths.filter { it.substringAfterLast('/') == n && depth(it) <= 3 }

        named("pubspec.yaml").firstOrNull { snippets[it]?.contains("flutter") == true }?.let { return Detection("Flutter", null) }
        if (named("project.godot").isNotEmpty()) return Detection("Godot", null)
        if (paths.any { it.endsWith("ProjectSettings/ProjectVersion.txt") }) return Detection("Unity", null)
        if (snippets.any { (k, v) -> k.endsWith(".csproj") && v.contains("UseMaui") }) return Detection(".NET MAUI", null)

        for (pk in named("package.json")) {
            val t = snippets[pk] ?: continue
            when {
                t.contains("@nativescript/core") || t.contains("\"nativescript\"") -> return Detection("NativeScript", null)
                t.contains("\"expo\"") -> return Detection("Expo", null)
                t.contains("\"react-native\"") -> return Detection("React Native", null)
                t.contains("@capacitor/core") -> return Detection(if (t.contains("@ionic/")) "Ionic" else "Capacitor", null)
                t.contains("cordova") -> return Detection("Cordova", null)
            }
        }
        if (snippets.any { (k, v) -> k.endsWith("config.xml") && v.contains("<widget") && v.contains("cordova", true) })
            return Detection("Cordova", null)

        if (named("settings.gradle").isNotEmpty() || named("settings.gradle.kts").isNotEmpty()) {
            val g = snippets.filterKeys { it.endsWith("build.gradle") || it.endsWith("build.gradle.kts") }.values.joinToString("\n")
            if (g.contains("com.android.application") || g.contains("android.application")) {
                val fw = when {
                    g.contains("externalNativeBuild") -> "C++ NDK"
                    paths.any { it.endsWith(".kt") } -> "Kotlin"
                    else -> "Java"
                }
                return Detection(fw, null)
            }
        }
        return Detection(
            null,
            "Project tidak didukung atau konfigurasi tidak lengkap. Tidak ditemukan penanda: pubspec.yaml (Flutter), " +
                "package.json (React Native/Expo/Capacitor/Ionic/Cordova/NativeScript), settings.gradle + plugin Android, " +
                "project.godot, ProjectSettings (Unity), atau .csproj MAUI."
        )
    }
}
