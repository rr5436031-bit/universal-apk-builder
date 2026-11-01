package com.universalapk.builder.build

import com.universalapk.builder.models.BuildRecord
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Membentuk JSON error terstruktur dari data build nyata. */
object ErrorReports {
    private fun JSONObject.s(k: String): String? = if (!has(k) || isNull(k)) null else getString(k)
    private fun JSONObject.i(k: String): Int? = if (!has(k) || isNull(k)) null else optInt(k)

    private fun obj(
        project: String, framework: String, buildType: String, runId: Long?, runNumber: Int?,
        summary: String, message: String, file: String?, line: Int?, cause: String?, log: List<String>
    ): JSONObject = JSONObject().apply {
        put("status", "failed")
        put("project_name", project)
        put("framework", framework)
        put("build_type", buildType)
        put("workflow", JSONObject()
            .put("run_id", runId?.toString() ?: JSONObject.NULL)
            .put("run_number", runNumber ?: JSONObject.NULL))
        put("error", JSONObject()
            .put("summary", summary).put("message", message)
            .put("file", file ?: JSONObject.NULL).put("line", line ?: JSONObject.NULL)
            .put("cause", cause ?: JSONObject.NULL))
        put("build_log", JSONArray(log))
        put("timestamp", Instant.now().toString())
    }

    /** Kegagalan sebelum/di luar workflow (upload, koneksi, dispatch, timeout). */
    fun local(rec: BuildRecord, summary: String, message: String) =
        obj(rec.projectName, rec.framework, rec.buildType, rec.runId, rec.runNumber, summary, message, null, null, null, rec.activity)

    /** Dari report.json yang dibuat workflow. */
    fun fromReport(report: JSONObject, rec: BuildRecord, runId: Long): JSONObject {
        val e = report.optJSONObject("error")
        val a = report.optJSONArray("build_log")
        val log = if (a != null) List(a.length()) { a.optString(it) } else emptyList()
        return obj(
            rec.projectName, report.optString("framework", rec.framework).ifBlank { rec.framework },
            report.optString("build_type", rec.buildType), runId, rec.runNumber,
            e?.s("summary") ?: "Build failed", e?.s("message") ?: "", e?.s("file"), e?.i("line"), e?.s("cause"), log
        )
    }

    /** Jika report.json tidak ada (mis. gagal sebelum step laporan), pakai data job/log GitHub yang nyata. */
    fun fallback(rec: BuildRecord, runId: Long, conclusion: String, step: String?, logLines: List<String>): JSONObject {
        val errLine = logLines.firstOrNull { Regex("(?i)\\b(error|exception|failed)\\b").containsMatchIn(it) }
        val summary = if (conclusion == "timed_out") "Workflow timeout" else "Build failed"
        val message = buildString {
            append("Workflow berakhir dengan status '$conclusion'")
            if (step != null) append(" pada step \"$step\"")
            append(".")
            if (errLine != null) append(" ").append(errLine.trim())
        }
        return obj(rec.projectName, rec.framework, rec.buildType, runId, rec.runNumber, summary, message, null, null, null, logLines.takeLast(300))
    }
}
