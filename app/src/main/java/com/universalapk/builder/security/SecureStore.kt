package com.universalapk.builder.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Menyimpan token GitHub terenkripsi (Android Keystore + EncryptedSharedPreferences). */
class SecureStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveToken(token: String) = prefs.edit().putString(KEY, token.trim()).apply()
    fun token(): String? = prefs.getString(KEY, null)?.takeIf { it.isNotBlank() }
    fun clearToken() = prefs.edit().remove(KEY).apply()

    companion object {
        private const val KEY = "gh_token"

        /** Token tidak pernah ditampilkan utuh. */
        fun mask(t: String): String =
            if (t.length <= 8) "\u2022\u2022\u2022\u2022" else t.take(4) + "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022" + t.takeLast(4)
    }
}
