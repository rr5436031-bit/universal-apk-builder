package com.universalapk.builder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.universalapk.builder.app.Graph
import com.universalapk.builder.build.ApkActions
import com.universalapk.builder.models.BuildStatus
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val records by Graph.history.records.collectAsStateWithLifecycle()
    var info by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    ScreenScaffold("Riwayat build", { nav.popBackStack() }) { pad ->
        if (records.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Belum ada riwayat build.") }
        } else {
            LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { info?.let { Text(it, style = MaterialTheme.typography.bodySmall) } }
                items(records, key = { it.id }) { r ->
                    AppCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(r.projectName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            StatusChip(r.status)
                        }
                        LabeledValue("Framework", r.framework)
                        LabeledValue("Tanggal", formatDate(r.startedAt))
                        LabeledValue("Durasi", formatDuration(r.durationMs(System.currentTimeMillis())))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { nav.navigate("build/${r.id}") }) { Text("Detail") }
                            if (r.status == BuildStatus.SUCCESS) Button(
                                enabled = !busy,
                                onClick = {
                                    scope.launch {
                                        busy = true; info = "Mengunduh APK ${r.projectName}..."
                                        info = runCatching { ApkActions.download(ctx, r) {} }.getOrElse { it.message ?: "Gagal mengunduh APK." }
                                        busy = false
                                    }
                                }
                            ) { Text("Unduh APK") }
                            if (r.status == BuildStatus.FAILED && r.errorJson != null)
                                OutlinedButton(onClick = { nav.navigate("error/${r.id}") }) { Text("Laporan error") }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}
