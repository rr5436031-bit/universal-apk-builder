package com.universalapk.builder.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.universalapk.builder.app.Graph
import com.universalapk.builder.build.ApkActions
import com.universalapk.builder.models.BuildRecord
import com.universalapk.builder.models.BuildStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BuildScreen(id: String, vm: MainViewModel, nav: NavController) {
    val records by Graph.history.records.collectAsStateWithLifecycle()
    val rec = records.firstOrNull { it.id == id }
    ScreenScaffold(rec?.projectName ?: "Build", { nav.popBackStack() }) { pad ->
        if (rec == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Build tidak ditemukan.") }
        } else {
            Column(
                Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val now by produceState(System.currentTimeMillis(), rec.status) {
                    while (true) { value = System.currentTimeMillis(); delay(1000) }
                }
                AppCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(rec.projectName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        StatusChip(rec.status)
                    }
                    LabeledValue("Framework", rec.framework)
                    LabeledValue("Tipe build", rec.buildType)
                    LabeledValue("Repository", "${rec.repo} (${rec.branch})")
                    LabeledValue("Waktu mulai", formatDate(rec.startedAt))
                    LabeledValue("Durasi", formatDuration(rec.durationMs(now)))
                }
                Crossfade(rec.status, label = "build-state") { st ->
                    when (st) {
                        BuildStatus.SUCCESS -> SuccessCard(rec, now)
                        BuildStatus.FAILED -> FailedCard(rec, nav)
                        BuildStatus.CANCELLED -> AppCard { Text("Build dibatalkan.", color = Brand.warning) }
                        else -> ProgressCard(rec) { vm.cancel(rec.id) }
                    }
                }
                if (rec.activity.isNotEmpty()) ActivityLog(rec)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ProgressCard(rec: BuildRecord, onCancel: () -> Unit) {
    AppCard {
        val uploadText = when {
            rec.status == BuildStatus.WAITING -> "Menunggu"
            rec.status == BuildStatus.UPLOADING -> "${rec.uploadProgress}%"
            else -> "Selesai"
        }
        val wfText = when (rec.status) {
            BuildStatus.QUEUED -> "Antrian GitHub Actions"
            BuildStatus.BUILDING -> rec.currentStep ?: "Berjalan"
            else -> "Belum dimulai"
        }
        LabeledValue("Status upload", uploadText)
        LabeledValue("Status workflow", wfText)
        val color = MaterialTheme.colorScheme.primary
        val track = MaterialTheme.colorScheme.surfaceVariant
        when {
            rec.status == BuildStatus.UPLOADING -> LinearProgressIndicator(
                progress = { rec.uploadProgress / 100f }, modifier = Modifier.fillMaxWidth(), color = color, trackColor = track)
            rec.status == BuildStatus.BUILDING && rec.stepProgress > 0 -> LinearProgressIndicator(
                progress = { rec.stepProgress / 100f }, modifier = Modifier.fillMaxWidth(), color = color, trackColor = track)
            else -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = color, trackColor = track)
        }
        if (rec.status == BuildStatus.BUILDING && rec.stepProgress > 0)
            Text("Progres step workflow: ${rec.stepProgress}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Batalkan build") }
    }
}

@Composable
private fun SuccessCard(rec: BuildRecord, now: Long) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.CheckCircle, null, tint = Brand.success)
            Text("SUCCESS", style = MaterialTheme.typography.titleMedium, color = Brand.success)
        }
        LabeledValue("File APK", rec.apkName ?: "-")
        LabeledValue("Ukuran APK", rec.apkSize?.let { formatSize(it) } ?: "-")
        LabeledValue("Waktu build", formatDuration(rec.durationMs(now)))
        Button(
            onClick = {
                scope.launch {
                    working = "Mengunduh APK..."
                    info = runCatching { ApkActions.download(ctx, rec) { p -> working = "Mengunduh APK... $p%" } }
                        .getOrElse { it.message ?: "Gagal mengunduh APK." }
                    working = null
                }
            },
            enabled = working == null, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)
        ) { Text("DOWNLOAD APK") }
        OutlinedButton(
            onClick = {
                scope.launch {
                    working = "Menyiapkan APK..."
                    runCatching { ApkActions.share(ctx, rec) { p -> working = "Menyiapkan APK... $p%" } }
                        .onFailure { info = it.message ?: "Gagal membagikan APK." }
                    working = null
                }
            },
            enabled = working == null, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)
        ) { Text("SHARE APK") }
        working?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
        info?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun FailedCard(rec: BuildRecord, nav: NavController) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
            Text("FAILED", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        }
        Text(rec.message ?: "Build gagal.", style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = { nav.navigate("error/${rec.id}") },
            modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.background)
        ) { Text("VIEW ERROR") }
    }
}

@Composable
private fun ActivityLog(rec: BuildRecord) {
    AppCard {
        Text("Log aktivitas", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        rec.activity.takeLast(12).forEach {
            Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
