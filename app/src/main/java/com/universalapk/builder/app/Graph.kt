package com.universalapk.builder.app

import android.app.Application
import com.universalapk.builder.github.GitHubApi
import com.universalapk.builder.security.SecureStore
import com.universalapk.builder.storage.HistoryStore
import com.universalapk.builder.storage.SettingsStore

/** Penyedia dependensi sederhana (tanpa framework DI). */
object Graph {
    lateinit var secure: SecureStore
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var history: HistoryStore
        private set

    fun init(app: Application) {
        secure = SecureStore(app)
        settings = SettingsStore(app, secure)
        history = HistoryStore(app)
    }

    fun api(repoFull: String): GitHubApi =
        GitHubApi({ settings.token() }, repoFull.substringBefore('/'), repoFull.substringAfter('/'))
}
