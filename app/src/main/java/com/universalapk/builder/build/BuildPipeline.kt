package com.universalapk.builder.build

import android.content.Context
import android.net.Uri
import com.universalapk.builder.app.Graph
import com.universalapk.builder.github.ArtifactDownloader
import com.universalapk.builder.github.GitHubApi
import com.universalapk.builder.github.GitHubApiException
import com.universalapk.builder.models.BuildRecord
import com.universalapk.builder.models.BuildStatus
import com.universalapk.builder.upload.UploadManager
import com.universalapk.builder.upload.ZipRejected
import com.universalapk.builder.workers.Notifier
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CancellationException

/** Alur nyata: upload ZIP -> workflow_dispatch -> polling GitHub Actions -> artifact/laporan error. */
class BuildPipeline(private val ctx: Context) {
    private val history = Graph.history
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)

    private fun log(id: String, msg: String) = history.update(id) {
        it.copy(activity = (it.activity + "${clock.format(Date())}  $msg").takeLast(80))
    }

    suspend fun run(id: String, uriStr: String, rootPrefix: String) {
        val rec0 = history.get(id) ?: return
        if (rec0.status.terminal) return
        val api = Graph.api(rec0.repo)
        try {
            if (rec0.runId == null) {
                if (rec0.status != BuildStatus.WAITING) {
                    failLocal(id, "Proses terputus", "Aplikasi dihentikan saat upload/antrian sebelum workflow berjalan. Ulangi build.")
                    return
                }
                history.update(id) { it.copy(status = BuildStatus.UPLOADING) }
                log(id, "Mengunggah source ke ${rec0.repo} (${rec0.branch})")
                var last = -1
                val commit = UploadManager(ctx, api).upload(Uri.parse(uriStr), rec0, rootPrefix) { p ->
                    if (p != last) { last = p; history.update(id) { it.copy(uploadProgress = p) } }
                }
                log(id, "Upload selesai, commit ${commit.take(7)}")
                history.update(id) { it.copy(status = BuildStatus.QUEUED, uploadProgress = 100) }
                val run = dispatchAndFind(api, rec0)
                history.update(id) { it.copy(runId = run.getLong("id"), runNumber = run.optInt("run_number")) }
                log(id, "Workflow dimulai: run #${run.optInt("run_number")}")
            }
            monitor(api, id)
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                history.get(id)?.runId?.let { runCatching { api.cancelRun(it) } }
                history.update(id) { it.copy(status = BuildStatus.CANCELLED, finishedAt = System.currentTimeMillis(), message = "Dibatalkan pengguna") }
                log(id, "Build dibatalkan")
            }
            throw e
        } catch (e: ZipRejected) {
            failLocal(id, "ZIP ditolak", e.message.orEmpty())
        } catch (e: GitHubApiException) {
            failLocal(id, "Kesalahan GitHub API", e.message.orEmpty())
        } catch (e: IOException) {
            failLocal(id, "Koneksi terputus", e.message ?: "Gagal terhubung ke GitHub.")
        } catch (e: Exception) {
            failLocal(id, "Kesalahan tak terduga", "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private suspend fun dispatchAndFind(api: GitHubApi, rec: BuildRecord): JSONObject {
        val inputs = mapOf(
            "project_path" to rec.projectPath, "build_type" to rec.buildType,
            "project_name" to rec.projectName, "client_id" to rec.id
        )
        var attempts = 0
        while (true) {
            try { api.dispatch(UploadManager.WORKFLOW_FILE, rec.branch, inputs); break }
            catch (e: GitHubApiException) {
                // Workflow baru di-commit bisa belum terdaftar beberapa detik.
                if (e.code == 404 && attempts < 6) { attempts++; delay(5000) }
                else if (e.code == 404) throw GitHubApiException(404, "Workflow universal-build.yml tidak ditemukan di branch ${rec.branch}, atau token tanpa izin Actions.")
                else throw e
            }
        }
        repeat(40) {
            delay(3000)
            val runs = api.listRuns(UploadManager.WORKFLOW_FILE)
            for (i in 0 until runs.length()) {
                val r = runs.getJSONObject(i)
                val title = r.optString("display_title").ifBlank { r.optString("name") }
                if (title.contains(rec.id)) return r
            }
        }
        throw GitHubApiException(0, "Run workflow tidak ditemukan setelah dispatch. Periksa tab Actions di GitHub.")
    }

    private suspend fun monitor(api: GitHubApi, id: String) {
        var errors = 0
        while (true) {
            delay(5000)
            val rec = history.get(id) ?: return
            val runId = rec.runId ?: return
            if (System.currentTimeMillis() - rec.startedAt > 95 * 60_000L) {
                failLocal(id, "Workflow timeout", "Build melebihi 95 menit. Periksa tab Actions di GitHub.")
                return
            }
            try {
                val run = api.getRun(runId)
                val jobs = api.getJobs(runId)
                errors = 0
                val steps = jobs.optJSONObject(0)?.optJSONArray("steps")
                val total = steps?.length() ?: 0
                var done = 0
                var current: String? = null
                for (i in 0 until total) {
                    val s = steps!!.getJSONObject(i)
                    when (s.optString("status")) {
                        "completed" -> done++
                        "in_progress" -> if (current == null) current = s.optString("name")
                    }
                }
                val st = if (run.optString("status") == "in_progress") BuildStatus.BUILDING else BuildStatus.QUEUED
                history.update(id) { it.copy(status = st, stepProgress = if (total > 0) done * 100 / total else 0, currentStep = current) }
                if (st != rec.status) log(id, "Status workflow: ${st.label}")
                if (current != null && current != rec.currentStep) log(id, "Step: $current")
                if (run.optString("status") == "completed") { finish(api, id, run, jobs); return }
            } catch (e: GitHubApiException) {
                if (e.code == 401 || e.code == 404 || ++errors >= 30) throw e
            } catch (e: IOException) {
                if (++errors >= 30) throw e
            }
        }
    }

    private suspend fun finish(api: GitHubApi, id: String, run: JSONObject, jobs: JSONArray) {
        val rec = history.get(id) ?: return
        val runId = rec.runId ?: return
        val conclusion = run.optString("conclusion")
        when (conclusion) {
            "success" -> onSuccess(api, rec, runId)
            "cancelled" -> {
                history.update(id) { it.copy(status = BuildStatus.CANCELLED, finishedAt = System.currentTimeMillis(), message = "Workflow dibatalkan") }
                log(id, "Workflow dibatalkan")
            }
            else -> onFailure(api, rec, runId, conclusion, jobs)
        }
        history.get(id)?.let { Notifier.result(ctx, it) }
    }

    private suspend fun onSuccess(api: GitHubApi, rec: BuildRecord, runId: Long) {
        val dl = ArtifactDownloader(ctx, api)
        val arts = api.listArtifacts(runId)
        val apk = dl.find(arts, "apk-$runId", runId)
        if (apk == null) {   // jangan mengklaim sukses tanpa artifact yang benar
            failLocal(rec.id, "Artifact APK tidak ditemukan", "Workflow sukses tetapi artifact apk-$runId tidak ada atau sudah kedaluwarsa.")
            return
        }
        val report = dl.readReport(arts, runId)
        val apkObj = report?.optJSONObject("apk")
        history.update(rec.id) {
            it.copy(
                status = BuildStatus.SUCCESS, finishedAt = System.currentTimeMillis(),
                framework = report?.optString("framework")?.takeIf { f -> f.isNotBlank() } ?: it.framework,
                apkName = apkObj?.optString("name") ?: "app-${it.buildType}.apk",
                apkSize = apkObj?.optLong("size_bytes")?.takeIf { s -> s > 0 } ?: apk.optLong("size_in_bytes")
            )
        }
        log(rec.id, "Build sukses, artifact APK tersedia")
    }

    private suspend fun onFailure(api: GitHubApi, rec: BuildRecord, runId: Long, conclusion: String, jobs: JSONArray) {
        val arts = runCatching { api.listArtifacts(runId) }.getOrNull()
        val report = arts?.let { ArtifactDownloader(ctx, api).readReport(it, runId) }
        var step: String? = null
        var lines = emptyList<String>()
        val job = jobs.optJSONObject(0)
        val ss = job?.optJSONArray("steps")
        if (ss != null) for (i in 0 until ss.length()) {
            val s = ss.getJSONObject(i)
            if (s.optString("conclusion") == "failure") { step = s.optString("name"); break }
        }
        if (job != null) {
            val raw = runCatching { api.jobLogs(job.getLong("id")) }.getOrDefault("")
            lines = raw.lines().map { it.replace(Regex("^\\uFEFF?\\d{4}-\\d\\d-\\d\\dT[\\d:.]+Z "), "") }.filter { it.isNotBlank() }
        }
        val base = if (report != null) ErrorReports.fromReport(report, rec, runId)
                   else ErrorReports.fallback(rec, runId, conclusion, step, lines)
        val json = ErrorReports.enrich(base, step, lines)
        val summary = json.optJSONObject("error")?.optString("summary") ?: "Build failed"
        history.update(rec.id) {
            it.copy(status = BuildStatus.FAILED, finishedAt = System.currentTimeMillis(), message = summary, errorJson = json.toString(2))
        }
        log(rec.id, "Build gagal: $summary")
    }

    private fun failLocal(id: String, summary: String, message: String) {
        log(id, "$summary: $message")
        val rec = history.get(id) ?: return
        val json = ErrorReports.local(rec, summary, message).toString(2)
        history.update(id) { it.copy(status = BuildStatus.FAILED, finishedAt = System.currentTimeMillis(), message = summary, errorJson = json) }
        history.get(id)?.let { Notifier.result(ctx, it) }
    }
}
