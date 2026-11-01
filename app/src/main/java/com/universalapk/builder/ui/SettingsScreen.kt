package com.universalapk.builder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController

@Composable
fun SettingsScreen(vm: MainViewModel, nav: NavController) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val testMsg by vm.testMessage.collectAsStateWithLifecycle()
    val testing by vm.testing.collectAsStateWithLifecycle()
    var user by rememberSaveable { mutableStateOf(s.username) }
    var repo by rememberSaveable { mutableStateOf(s.repo) }
    var branch by rememberSaveable { mutableStateOf(s.branch) }
    var token by rememberSaveable { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    ScreenScaffold("Pengaturan", { nav.popBackStack() }) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppCard {
                OutlinedTextField(user, { user = it; saved = false }, label = { Text("Username GitHub (pemilik repo)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(repo, { repo = it; saved = false }, label = { Text("Repository tujuan") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(branch, { branch = it; saved = false }, label = { Text("Branch tujuan") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    token, { token = it; saved = false },
                    label = { Text("GitHub Personal Access Token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = {
                        Text(if (s.hasToken) "Tersimpan: ${s.tokenMasked}. Kosongkan untuk tetap memakainya." else "Belum ada token tersimpan.")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { vm.saveSettings(user, repo, branch, token); token = ""; saved = true }) { Text("Simpan") }
                    OutlinedButton(onClick = { vm.testConnection(user, repo, branch, token) }, enabled = !testing) {
                        Text(if (testing) "Menguji..." else "Uji koneksi")
                    }
                }
                if (s.hasToken) TextButton(onClick = { vm.clearToken() }) { Text("Hapus token tersimpan", color = MaterialTheme.colorScheme.error) }
                if (saved) Text("Pengaturan disimpan.", color = Brand.success, style = MaterialTheme.typography.bodySmall)
                testMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            AppCard {
                Text("Izin token minimum", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Fine-grained token, hanya untuk repository tujuan:\n" +
                        "- Contents: Read and write\n- Actions: Read and write\n- Workflows: Read and write\n- Metadata: Read-only\n\n" +
                        "Token klasik: scope repo dan workflow.\n\n" +
                        "Repository harus sudah punya minimal satu commit dan branch tujuan harus ada. " +
                        "Token disimpan terenkripsi di perangkat dan tidak pernah ditampilkan utuh.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
