package com.universalapk.builder.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.universalapk.builder.storage.Exporter
import org.json.JSONObject

@Composable
private fun rememberErrorJson(id: String): Pair<com.universalapk.builder.models.BuildRecord?, JSONObject?> {
    val records by Graph.history.records.collectAsStateWithLifecycle()
    val rec = records.firstOrNull { it.id == id }
    val json = remember(rec?.errorJson) { rec?.errorJson?.let { runCatching { JSONObject(it) }.getOrNull() } }
    return rec to json
}

private fun JSONObject.str(k: String): String? = if (!has(k) || isNull(k)) null else getString(k)

@Composable
fun ErrorScreen(id: String, nav: NavController) {
    val ctx = LocalContext.current
    val (rec, json) = rememberErrorJson(id)
    var info by remember { mutableStateOf<String?>(null) }
    ScreenScaffold("Detail error", { nav.popBackStack() }) { pad ->
        if (rec == null || json == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Laporan error tidak tersedia.") }
            return@ScreenScaffold
        }
        val err = json.optJSONObject("error")
        val summary = err?.str("summary") ?: "Build failed"
        val message = err?.str("message") ?: "-"
        val file = err?.str("file")
        val line = if (err != null && err.has("line") && !err.isNull("line")) err.optInt("line") else null
        val cause = err?.str("cause")
        val plain = buildString {
            appendLine("Project: ${rec.projectName} (${json.optString("framework")}, ${json.optString("build_type")})")
            appendLine("Summary: $summary")
            appendLine("Message: $message")
            if (file != null) appendLine("File: $file${if (line != null) ":$line" else ""}")
            if (cause != null) appendLine("Penyebab: $cause")
        }
        Column(
            Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AppCard {
                Text(summary, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                Text(message, style = MaterialTheme.typography.bodyMedium)
                if (file != null) LabeledValue("File", file)
                if (line != null) LabeledValue("Baris", line.toString())
                if (cause != null) { LabeledValue("Penyebab", cause) }
            }
            Button(
                onClick = {
                    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("error", plain))
                    info = "Error disalin ke clipboard."
                },
                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)
            ) { Text("COPY ERROR") }
            OutlinedButton(
                onClick = {
                    info = runCatching {
                        val name = "error-${rec.projectName}-run${rec.runNumber ?: 0}.json"
                        Exporter.saveToDownloads(ctx, name, "application/json") { it.write(rec.errorJson!!.toByteArray()) }
                        "JSON tersimpan di Download/UniversalAPK/$name"
                    }.getOrElse { it.message ?: "Gagal menyimpan JSON." }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)
            ) { Text("DOWNLOAD ERROR JSON") }
            OutlinedButton(
                onClick = { nav.navigate("log/${rec.id}") },
                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)
            ) { Text("VIEW BUILD LOG") }
            info?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun LogScreen(id: String, nav: NavController) {
    val (_, json) = rememberErrorJson(id)
    val lines = remember(json) {
        json?.optJSONArray("build_log")?.let { a -> List(a.length()) { a.optString(it) } } ?: emptyList()
    }
    ScreenScaffold("Build log", { nav.popBackStack() }) { pad ->
        if (lines.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Log tidak tersedia.") }
        } else {
            LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(lines) { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface) }
            }
        }
    }
}
