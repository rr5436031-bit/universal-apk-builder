package com.universalapk.builder.models

import org.json.JSONArray
import org.json.JSONObject

enum class BuildStatus(val label: String, val terminal: Boolean) {
    WAITING("Waiting", false),
    UPLOADING("Uploading", false),
    QUEUED("Queued", false),
    BUILDING("Building", false),
    SUCCESS("Success", true),
    FAILED("Failed", true),
    CANCELLED("Cancelled", true)
}

data class AppSettings(
    val username: String = "",
    val repo: String = "",
    val branch: String = "main",
    val hasToken: Boolean = false,
    val tokenMasked: String = ""
) {
    val isComplete get() = hasToken && username.isNotBlank() && repo.isNotBlank() && branch.isNotBlank()
    val fullRepo get() = "$username/$repo"
}

/** Hasil validasi ZIP + deteksi framework awal (pratinjau; deteksi final dilakukan workflow). */
data class ScanResult(
    val files: Int,
    val totalBytes: Long,
    val rootPrefix: String,
    val framework: String?,
    val reason: String?,
    val projectName: String
)

data class BuildRecord(
    val id: String,
    val projectName: String,
    val zipName: String,
    val zipSize: Long,
    val framework: String,
    val buildType: String,
    val repo: String,
    val branch: String,
    val projectPath: String,
    val status: BuildStatus = BuildStatus.WAITING,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val runId: Long? = null,
    val runNumber: Int? = null,
    val uploadProgress: Int = 0,
    val stepProgress: Int = 0,
    val currentStep: String? = null,
    val activity: List<String> = emptyList(),
    val message: String? = null,
    val apkName: String? = null,
    val apkSize: Long? = null,
    val errorJson: String? = null
) {
    fun durationMs(now: Long) = (finishedAt ?: now) - startedAt

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("projectName", projectName); put("zipName", zipName); put("zipSize", zipSize)
        put("framework", framework); put("buildType", buildType); put("repo", repo); put("branch", branch)
        put("projectPath", projectPath); put("status", status.name); put("startedAt", startedAt)
        put("finishedAt", finishedAt ?: JSONObject.NULL); put("runId", runId ?: JSONObject.NULL)
        put("runNumber", runNumber ?: JSONObject.NULL); put("uploadProgress", uploadProgress)
        put("stepProgress", stepProgress); put("currentStep", currentStep ?: JSONObject.NULL)
        put("activity", JSONArray(activity)); put("message", message ?: JSONObject.NULL)
        put("apkName", apkName ?: JSONObject.NULL); put("apkSize", apkSize ?: JSONObject.NULL)
        put("errorJson", errorJson ?: JSONObject.NULL)
    }

    companion object {
        private fun JSONObject.str(k: String): String? = if (!has(k) || isNull(k)) null else getString(k)
        private fun JSONObject.lng(k: String): Long? = if (!has(k) || isNull(k)) null else getLong(k)

        fun fromJson(o: JSONObject) = BuildRecord(
            id = o.getString("id"),
            projectName = o.getString("projectName"),
            zipName = o.getString("zipName"),
            zipSize = o.getLong("zipSize"),
            framework = o.getString("framework"),
            buildType = o.getString("buildType"),
            repo = o.getString("repo"),
            branch = o.getString("branch"),
            projectPath = o.getString("projectPath"),
            status = runCatching { BuildStatus.valueOf(o.getString("status")) }.getOrDefault(BuildStatus.FAILED),
            startedAt = o.getLong("startedAt"),
            finishedAt = o.lng("finishedAt"),
            runId = o.lng("runId"),
            runNumber = o.lng("runNumber")?.toInt(),
            uploadProgress = o.optInt("uploadProgress"),
            stepProgress = o.optInt("stepProgress"),
            currentStep = o.str("currentStep"),
            activity = o.optJSONArray("activity")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList(),
            message = o.str("message"),
            apkName = o.str("apkName"),
            apkSize = o.lng("apkSize"),
            errorJson = o.str("errorJson")
        )
    }
}
