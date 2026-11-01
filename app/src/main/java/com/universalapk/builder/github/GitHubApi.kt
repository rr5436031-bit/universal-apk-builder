package com.universalapk.builder.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class GitHubApiException(val code: Int, message: String, val retryAfterSec: Long? = null) : IOException(message)

/**
 * Klien GitHub REST API. Token hanya dipakai di header Authorization;
 * tidak pernah masuk URL, log, atau pesan error.
 */
class GitHubApi(private val token: () -> String?, private val owner: String, private val repo: String) {

    companion object {
        private val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(180, TimeUnit.SECONDS)
                .build()
        }
        private val JSON = "application/json".toMediaType()
    }

    private fun base() = "https://api.github.com/repos/$owner/$repo"

    private fun builder(url: String): Request.Builder {
        val t = token() ?: throw GitHubApiException(401, "Token GitHub belum diatur.")
        return Request.Builder().url(url)
            .header("Authorization", "Bearer $t")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
    }

    private fun toException(resp: Response): GitHubApiException {
        val raw = resp.body?.string().orEmpty()
        val msg = runCatching { JSONObject(raw).optString("message") }.getOrDefault("").ifBlank { resp.message }
        val remaining = resp.header("x-ratelimit-remaining")
        val reset = resp.header("x-ratelimit-reset")?.toLongOrNull()
        val retryAfter = resp.header("retry-after")?.toLongOrNull()
        val limited = resp.code == 403 || resp.code == 429
        return when {
            resp.code == 401 -> GitHubApiException(401, "Token tidak valid atau sudah kedaluwarsa.")
            limited && retryAfter != null ->
                GitHubApiException(resp.code, "Rate limit GitHub (secondary). Coba lagi dalam ${retryAfter}s.", retryAfter)
            limited && remaining == "0" -> {
                val wait = reset?.let { (it - System.currentTimeMillis() / 1000).coerceAtLeast(1) }
                GitHubApiException(resp.code, "Rate limit GitHub tercapai. Reset sekitar ${wait ?: "?"} detik lagi.", wait)
            }
            resp.code == 403 -> GitHubApiException(403, "Akses ditolak: $msg. Periksa izin token (Contents, Actions, Workflows).")
            resp.code == 404 -> GitHubApiException(404, "Tidak ditemukan: $msg. Periksa repository, branch, dan izin token.")
            resp.code == 409 && msg.contains("empty", true) ->
                GitHubApiException(409, "Repository masih kosong. Buat satu commit awal (mis. README) lalu coba lagi.")
            msg.contains("workflow", true) ->
                GitHubApiException(resp.code, "GitHub ${resp.code}: $msg. Token memerlukan izin Workflows (scope 'workflow').")
            else -> GitHubApiException(resp.code, "GitHub ${resp.code}: $msg")
        }
    }

    private suspend fun <T> exec(build: () -> Request, parse: (Response) -> T): T = withContext(Dispatchers.IO) {
        var attempt = 0
        while (true) {
            try {
                return@withContext client.newCall(build()).execute().use { resp ->
                    if (!resp.isSuccessful) throw toException(resp)
                    parse(resp)
                }
            } catch (e: GitHubApiException) {
                val w = e.retryAfterSec
                if (w != null && w <= 120 && attempt < 3) { attempt++; delay(w * 1000) } else throw e
            }
        }
        @Suppress("UNREACHABLE_CODE")
        throw IllegalStateException()
    }

    private suspend fun getJson(path: String): JSONObject =
        exec({ builder(base() + path).get().build() }) { JSONObject(it.body!!.string()) }

    private suspend fun send(method: String, path: String, body: JSONObject?): JSONObject =
        exec({ builder(base() + path).method(method, (body?.toString() ?: "").toRequestBody(JSON)).build() }) {
            val s = it.body?.string().orEmpty()
            if (s.isBlank()) JSONObject() else JSONObject(s)
        }

    suspend fun repoInfo(): JSONObject = getJson("")
    suspend fun branchHead(branch: String): String = getJson("/git/ref/heads/$branch").getJSONObject("object").getString("sha")
    suspend fun treeOfCommit(sha: String): String = getJson("/git/commits/$sha").getJSONObject("tree").getString("sha")

    suspend fun createBlob(b64: String): String =
        send("POST", "/git/blobs", JSONObject().put("content", b64).put("encoding", "base64")).getString("sha")

    suspend fun createTree(baseTree: String, entries: JSONArray): String =
        send("POST", "/git/trees", JSONObject().put("base_tree", baseTree).put("tree", entries)).getString("sha")

    suspend fun createCommit(message: String, tree: String, parent: String): String =
        send("POST", "/git/commits", JSONObject().put("message", message).put("tree", tree)
            .put("parents", JSONArray().put(parent))).getString("sha")

    suspend fun updateRef(branch: String, sha: String) {
        send("PATCH", "/git/refs/heads/$branch", JSONObject().put("sha", sha).put("force", false))
    }

    suspend fun dispatch(workflowFile: String, ref: String, inputs: Map<String, String>) {
        send("POST", "/actions/workflows/$workflowFile/dispatches",
            JSONObject().put("ref", ref).put("inputs", JSONObject(inputs)))
    }

    suspend fun listRuns(workflowFile: String): JSONArray =
        getJson("/actions/workflows/$workflowFile/runs?event=workflow_dispatch&per_page=15").getJSONArray("workflow_runs")

    suspend fun getRun(id: Long): JSONObject = getJson("/actions/runs/$id")
    suspend fun getJobs(runId: Long): JSONArray = getJson("/actions/runs/$runId/jobs?per_page=30").getJSONArray("jobs")
    suspend fun cancelRun(id: Long) { send("POST", "/actions/runs/$id/cancel", null) }
    suspend fun listArtifacts(runId: Long): JSONArray =
        getJson("/actions/runs/$runId/artifacts?per_page=30").getJSONArray("artifacts")

    suspend fun jobLogs(jobId: Long): String =
        exec({ builder(base() + "/actions/jobs/$jobId/logs").get().build() }) { it.body!!.string() }

    /** Redirect ke penyimpanan blob diikuti OkHttp; header Authorization dibuang otomatis di host lain. */
    suspend fun downloadArtifact(id: Long, dest: File, onProgress: (Int) -> Unit) {
        exec({ builder(base() + "/actions/artifacts/$id/zip").get().build() }) { resp ->
            val total = resp.body!!.contentLength()
            resp.body!!.byteStream().use { inp ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(32768); var done = 0L; var last = -1
                    while (true) {
                        val r = inp.read(buf); if (r < 0) break
                        out.write(buf, 0, r); done += r
                        if (total > 0) { val p = (done * 100 / total).toInt(); if (p != last) { last = p; onProgress(p) } }
                    }
                }
            }
        }
    }
}
