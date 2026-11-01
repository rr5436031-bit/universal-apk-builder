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

    private fun norm(s: String) = s.trim().removePrefix("https://").removePrefix("http://")
        .removePrefix("www.").removePrefix("github.com/").trim('/').removeSuffix(".git")

    /** Menerima username/repo dalam bentuk URL lengkap, owner/repo, atau terpisah. */
    fun save(username: String, repo: String, branch: String, newToken: String?) {
        var u = norm(username)
        var r = norm(repo)
        if (u.contains('/')) {
            val p = u.split('/').filter { it.isNotBlank() }
            u = p[0]
            if (r.isBlank() && p.size > 1) r = p[1]
        }
        if (r.contains('/')) {
            val p = r.split('/').filter { it.isNotBlank() }
            if (p.size >= 2) { u = p[0]; r = p[1] } else r = p.firstOrNull().orEmpty()
        }
        prefs.edit().putString("username", u).putString("repo", r)
            .putString("branch", branch.trim().ifBlank { "main" }).apply()
        if (!newToken.isNullOrBlank()) secure.saveToken(newToken)
        _state.value = load()
    }

    fun clearToken() { secure.clearToken(); _state.value = load() }
}
