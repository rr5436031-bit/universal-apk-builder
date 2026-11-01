package com.universalapk.builder.storage

import android.content.Context
import com.universalapk.builder.models.AppSettings
import com.universalapk.builder.security.SecureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsStore(context: Context, private val secure: SecureStore) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    private fun load(): AppSettings {
        val t = secure.token()
        return AppSettings(
            username = prefs.getString("username", "").orEmpty(),
            repo = prefs.getString("repo", "").orEmpty(),
            branch = prefs.getString("branch", "main").orEmpty(),
            hasToken = t != null,
            tokenMasked = t?.let { SecureStore.mask(it) } ?: ""
        )
    }

    fun token(): String? = secure.token()

    fun save(username: String, repo: String, branch: String, newToken: String?) {
        var u = username.trim()
        var r = repo.trim()
        if (r.contains("/")) { u = r.substringBefore("/").ifBlank { u }; r = r.substringAfter("/") }
        r = r.removeSuffix(".git")
        prefs.edit().putString("username", u).putString("repo", r)
            .putString("branch", branch.trim().ifBlank { "main" }).apply()
        if (!newToken.isNullOrBlank()) secure.saveToken(newToken)
        _state.value = load()
    }

    fun clearToken() { secure.clearToken(); _state.value = load() }
}
