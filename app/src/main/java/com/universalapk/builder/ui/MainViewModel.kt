package com.universalapk.builder.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.universalapk.builder.app.Graph
import com.universalapk.builder.github.GitHubApi
import com.universalapk.builder.models.BuildRecord
import com.universalapk.builder.models.BuildStatus
import com.universalapk.builder.models.ScanResult
import com.universalapk.builder.upload.ZipRejected
import com.universalapk.builder.upload.ZipScanner
import com.universalapk.builder.workers.BuildPipelineWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class PickedZip(
    val uri: Uri, val name: String, val size: Long,
    val scan: ScanResult? = null, val error: String? = null, val scanning: Boolean = false
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val settings = Graph.settings.state
    val history = Graph.history.records

    private val _picked = MutableStateFlow<PickedZip?>(null)
    val picked: StateFlow<PickedZip?> = _picked
    private val _test = MutableStateFlow<String?>(null)
    val testMessage: StateFlow<String?> = _test
    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing

    fun pick(uri: Uri) {
        val ctx = getApplication<Application>()
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        var name = "project.zip"
        var size = 0L
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0) name = c.getString(ni) ?: name
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
            }
        }
        _picked.value = PickedZip(uri, name, size, scanning = true)
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { ZipScanner.scan(ctx, uri, name, size) } }
            _picked.value = r.fold(
                { PickedZip(uri, name, size, scan = it) },
                { PickedZip(uri, name, size, error = if (it is ZipRejected) it.message else "Gagal membaca ZIP: ${it.message}") }
            )
        }
    }

    fun clearPicked() { _picked.value = null }

    /** Membuat record riwayat dan menjalankan worker. @return id build. */
    fun startBuild(buildType: String): String? {
        val p = _picked.value ?: return null
        val scan = p.scan ?: return null
        val s = settings.value
        if (!s.isComplete || scan.framework == null) return null
        val id = UUID.randomUUID().toString()
        val slug = scan.projectName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "project" }
        Graph.history.add(
            BuildRecord(
                id = id, projectName = scan.projectName, zipName = p.name, zipSize = p.size,
                framework = scan.framework, buildType = buildType, repo = s.fullRepo, branch = s.branch,
                projectPath = "projects/$slug-${id.take(8)}"
            )
        )
        val req = OneTimeWorkRequestBuilder<BuildPipelineWorker>()
            .setInputData(workDataOf("id" to id, "uri" to p.uri.toString(), "root" to scan.rootPrefix))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(getApplication()).enqueueUniqueWork("build-$id", ExistingWorkPolicy.KEEP, req)
        _picked.value = null
        return id
    }

    fun cancel(id: String) {
        WorkManager.getInstance(getApplication()).cancelUniqueWork("build-$id")
        Graph.history.update(id) {
            if (it.status == BuildStatus.WAITING) it.copy(status = BuildStatus.CANCELLED, finishedAt = System.currentTimeMillis(), message = "Dibatalkan pengguna") else it
        }
    }

    fun saveSettings(user: String, repo: String, branch: String, token: String) =
        Graph.settings.save(user, repo, branch, token.ifBlank { null })

    fun clearToken() = Graph.settings.clearToken()

    fun testConnection(user: String, repo: String, branch: String, tokenInput: String) {
        viewModelScope.launch {
            _testing.value = true
            _test.value = try {
                val tk = tokenInput.ifBlank { Graph.settings.token() } ?: error("Token belum diisi.")
                val norm = { x: String -> x.trim().removePrefix("https://").removePrefix("http://").removePrefix("www.").removePrefix("github.com/").trim('/').removeSuffix(".git") }
                var u = norm(user); var r = norm(repo)
                if (u.contains('/')) { val p = u.split('/').filter { it.isNotBlank() }; u = p[0]; if (r.isBlank() && p.size > 1) r = p[1] }
                if (r.contains('/')) { val p = r.split('/').filter { it.isNotBlank() }; if (p.size >= 2) { u = p[0]; r = p[1] } }
                val api = GitHubApi({ tk }, u, r)
                val info = api.repoInfo()
                api.branchHead(branch.trim().ifBlank { "main" })
                if (info.optJSONObject("permissions")?.optBoolean("push") == false)
                    "Terhubung, tetapi token tidak memiliki izin tulis (push) ke repository ini."
                else "Koneksi berhasil. Repository dan branch ditemukan."
            } catch (e: Exception) { e.message ?: "Koneksi gagal." }
            _testing.value = false
        }
    }
}
