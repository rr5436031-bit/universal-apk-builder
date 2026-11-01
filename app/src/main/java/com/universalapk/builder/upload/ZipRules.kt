package com.universalapk.builder.upload

class ZipRejected(message: String) : Exception(message)

object ZipRules {
    const val MAX_ZIP_BYTES = 200L * 1024 * 1024
    const val MAX_FILE_BYTES = 50L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 600L * 1024 * 1024
    const val MAX_SCANNED_BYTES = 3L * 1024 * 1024 * 1024
    const val MAX_ENTRIES = 20000

    private val SKIP_DIRS = setOf(".git", "node_modules", ".gradle", ".idea", "__MACOSX")
    private val BLOCKED_EXT = setOf("exe", "bat", "cmd", "scr", "msi", "com", "vbs", "ps1")

    /** Menolak path berbahaya (Zip Slip) dan menormalkan pemisah. */
    fun cleanName(raw: String): String {
        val n = raw.replace('\\', '/').removePrefix("./")
        if (n.contains('\u0000') || n.startsWith("/") || Regex("^[A-Za-z]:").containsMatchIn(n) ||
            n.split('/').any { it == ".." }
        ) throw ZipRejected("Entri ZIP tidak aman (path traversal / Zip Slip): $raw")
        return n
    }

    fun skipped(path: String) = path.split('/').any { it in SKIP_DIRS } || path.endsWith(".DS_Store")

    fun checkBlocked(path: String) {
        if (path.substringAfterLast('.', "").lowercase() in BLOCKED_EXT)
            throw ZipRejected("File berbahaya diblokir: $path")
    }

    fun wanted(path: String): Boolean {
        val b = path.substringAfterLast('/')
        return b in setOf("package.json", "pubspec.yaml", "build.gradle", "build.gradle.kts",
            "settings.gradle", "settings.gradle.kts", "config.xml") || b.endsWith(".csproj")
    }

    /** Jika semua file berada dalam satu folder teratas, folder itu dilepas agar file build ada di root project. */
    fun detectRoot(paths: List<String>): String {
        val tops = paths.map { it.substringBefore('/', "") }.toSet()
        return if (tops.size == 1 && tops.first().isNotEmpty()) tops.first() + "/" else ""
    }

    fun mb(b: Long) = "%.1f MB".format(b / 1048576.0)
}
