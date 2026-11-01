package com.universalapk.builder.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController

@Composable
fun HomeScreen(vm: MainViewModel, nav: NavController) {
    val ctx = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val picked by vm.picked.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    var buildType by rememberSaveable { mutableStateOf("debug") }
    var confirm by remember { mutableStateOf(false) }

    fun go() { vm.startBuild(buildType)?.let { nav.navigate("build/$it") } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.pick(uri) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> go() }

    val scan = picked?.scan
    val canBuild = settings.isComplete && scan != null && scan.framework != null && scan.reason == null

    ScreenScaffold("Universal APK Builder", null, actions = {
        IconButton(onClick = { nav.navigate("history") }) { Icon(Icons.AutoMirrored.Filled.List, "Riwayat build") }
        IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Default.Settings, "Pengaturan") }
    }) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AppCard {
                Text("Repository tujuan", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (settings.isComplete) {
                    Text(settings.fullRepo, style = MaterialTheme.typography.titleMedium)
                    Text("Branch ${settings.branch}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Token, username, repository, dan branch belum lengkap.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { nav.navigate("settings") }) { Text("Buka Pengaturan") }
                }
            }

            AppCard {
                Text("Source project (ZIP)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val p = picked
                if (p == null) {
                    Text("Belum ada file dipilih.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LabeledValue("Nama file", p.name)
                    LabeledValue("Ukuran", formatSize(p.size))
                    when {
                        p.scanning -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Memvalidasi ZIP...", style = MaterialTheme.typography.bodyMedium)
                        }
                        p.error != null -> Text(p.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        p.scan != null -> {
                            LabeledValue("Isi", "${p.scan.files} file (${formatSize(p.scan.totalBytes)})")
                            LabeledValue("Framework", p.scan.framework ?: "Tidak terdeteksi")
                            if (p.scan.reason != null) Text(p.scan.reason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                FilledTonalButton(
                    onClick = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Upload ZIP")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Tipe build", color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilterChip(selected = buildType == "debug", onClick = { buildType = "debug" }, label = { Text("Debug") })
                FilterChip(selected = buildType == "release", onClick = { buildType = "release" }, label = { Text("Release") })
            }
            if (buildType == "release") {
                Text(
                    "Release memakai keystore dari GitHub Secrets (KEYSTORE_BASE64). Tanpa itu, APK ditandatangani dengan kunci uji sementara.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { confirm = true }, enabled = canBuild,
                modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)
            ) { Text("BUILD APK", style = MaterialTheme.typography.titleSmall) }

            history.firstOrNull()?.let { last ->
                Text("Build terakhir", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AppCard(Modifier.clickable { nav.navigate("build/${last.id}") }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(last.projectName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        StatusChip(last.status)
                    }
                    Text("${last.framework} - ${last.buildType} - ${formatDate(last.startedAt)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Mulai build?") },
            text = {
                Text(
                    "Project akan diunggah ke folder baru di ${settings.fullRepo} (branch ${settings.branch}) dan file workflow " +
                        "Universal APK Builder ditambahkan/diperbarui. Tidak ada file repository yang dihapus. Tipe build: $buildType."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    val need = Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    if (need) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) else go()
                }) { Text("Mulai") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Batal") } }
        )
    }
}
