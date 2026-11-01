package com.universalapk.builder.github

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.universalapk.builder.models.BuildRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

class ArtifactDownloader(private val ctx: Context, private val api: GitHubApi) {

    /** Mencari artifact milik run yang tepat (nama + workflow_run.id), bukan artifact lama. */
    fun find(arts: JSONArray, name: String, runId: Long): JSONObject? {
        for (i in 0 until arts.length()) {
            val a = arts.getJSONObject(i)
            if (a.optString("name") == name && !a.optBoolean("expired") &&
                a.optJSONObject("workflow_run")?.optLong("id") == runId
            ) return a
        }
        return null
    }

    suspend fun readReport(arts: JSONArray, runId: Long): JSONObject? = withContext(Dispatchers.IO) {
        val a = find(arts, "report-$runId", runId) ?: return@withContext null
        val tmp = File(ctx.cacheDir, "report-$runId.zip")
        try {
            api.downloadArtifact(a.getLong("id"), tmp) {}
            ZipInputStream(tmp.inputStream().buffered()).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    if (e.name.substringAfterLast('/') == "report.json") return@withContext JSONObject(z.readBytes().toString(Charsets.UTF_8))
                    e = z.nextEntry
                }
            }
            null
        } catch (e: Exception) { null } finally { tmp.delete() }
    }

    /** Mengunduh artifact APK milik run ini dan mengekstrak APK ke penyimpanan privat aplikasi. */
    suspend fun apkFile(rec: BuildRecord, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val runId = rec.runId ?: throw IllegalStateException("Build belum memiliki run workflow.")
        val dir = File(ctx.filesDir, "apks/$runId")
        dir.listFiles { f -> f.extension == "apk" }?.let { existing ->
            existing.firstOrNull { it.name == rec.apkName }?.let { return@withContext it }
            existing.firstOrNull()?.let { return@withContext it }
        }
        val art = find(api.listArtifacts(runId), "apk-$runId", runId)
            ?: throw IllegalStateException("Artifact APK tidak ditemukan atau sudah kedaluwarsa (retensi 14 hari).")
        val tmp = File(ctx.cacheDir, "apk-$runId.zip")
        try {
            api.downloadArtifact(art.getLong("id"), tmp, onProgress)
            dir.mkdirs()
            val out = ArrayList<File>()
            ZipInputStream(tmp.inputStream().buffered()).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    val n = e.name.substringAfterLast('/')           // hanya nama berkas: aman dari Zip Slip
                    if (!e.isDirectory && n.endsWith(".apk") && n.isNotBlank()) {
                        val f = File(dir, n)
                        f.outputStream().use { z.copyTo(it) }
                        out.add(f)
                    }
                    e = z.nextEntry
                }
            }
            out.firstOrNull { it.name == rec.apkName } ?: out.firstOrNull()
                ?: throw IllegalStateException("Artifact tidak berisi berkas APK.")
        } finally { tmp.delete() }
    }
}
