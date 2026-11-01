package com.universalapk.builder.upload

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.universalapk.builder.github.GitHubApi
import com.universalapk.builder.models.BuildRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

private class CountingInputStream(inp: InputStream) : FilterInputStream(inp) {
    var count = 0L
    override fun read(): Int { val r = super.read(); if (r >= 0) count++; return r }
    override fun read(b: ByteArray, off: Int, len: Int): Int { val r = super.read(b, off, len); if (r > 0) count += r; return r }
}

/**
 * Mengunggah isi ZIP ke repository lewat Git Data API dalam SATU commit.
 * Berkas teks dikirim inline dalam tree (hemat request), berkas biner lewat blob.
 * Hanya MENAMBAH berkas (base_tree dipertahankan): tidak ada file repository yang dihapus.
 */
class UploadManager(private val context: Context, private val api: GitHubApi) {

    companion object {
        const val WORKFLOW_FILE = "universal-build.yml"
        private val ASSETS = listOf(
            "universal-build.yml" to ".github/workflows/universal-build.yml",
            "detect.py" to ".github/universal/detect.py",
            "build.sh" to ".github/universal/build.sh",
            "make_report.py" to ".github/universal/make_report.py"
        )
    }

    private fun asText(b: ByteArray): String? {
        if (b.any { it == 0.toByte() }) return null
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString()
        } catch (e: CharacterCodingException) { null }
    }

    private fun readLimited(zin: ZipInputStream, name: String): ByteArray {
        val bos = ByteArrayOutputStream(); val buf = ByteArray(16384); var size = 0L
        while (true) {
            val r = zin.read(buf); if (r < 0) break
            size += r
            if (size > ZipRules.MAX_FILE_BYTES) throw ZipRejected("File '$name' melebihi batas ${ZipRules.mb(ZipRules.MAX_FILE_BYTES)}.")
            bos.write(buf, 0, r)
        }
        return bos.toByteArray()
    }

    /** @return SHA commit baru. */
    suspend fun upload(uri: Uri, rec: BuildRecord, rootPrefix: String, onProgress: (Int) -> Unit): String =
        withContext(Dispatchers.IO) {
            val head = api.branchHead(rec.branch)
            var tree = api.treeOfCommit(head)
            var pending = JSONArray()
            var pendingBytes = 0L
            var count = 0

            suspend fun flush() {
                if (pending.length() > 0) { tree = api.createTree(tree, pending); pending = JSONArray(); pendingBytes = 0 }
            }
            suspend fun add(path: String, bytes: ByteArray, exec: Boolean) {
                val entry = JSONObject().put("path", path).put("mode", if (exec) "100755" else "100644").put("type", "blob")
                val text = if (bytes.isNotEmpty() && bytes.size <= 1_000_000) asText(bytes) else null
                if (text != null) { entry.put("content", text); pendingBytes += bytes.size }
                else entry.put("sha", api.createBlob(Base64.encodeToString(bytes, Base64.NO_WRAP)))
                pending.put(entry); count++
                if (pending.length() >= 80 || pendingBytes >= 3_000_000) flush()
            }

            val total = rec.zipSize.coerceAtLeast(1)
            val input = context.contentResolver.openInputStream(uri) ?: throw ZipRejected("File ZIP tidak dapat dibuka.")
            input.use { raw ->
                val counting = CountingInputStream(raw)
                ZipInputStream(BufferedInputStream(counting)).use { zin ->
                    var e = zin.nextEntry
                    var last = -1
                    while (e != null) {
                        currentCoroutineContext().ensureActive()
                        val name = ZipRules.cleanName(e.name)
                        if (!e.isDirectory && !ZipRules.skipped(name)) {
                            ZipRules.checkBlocked(name)
                            val rel = name.removePrefix(rootPrefix)
                            if (rel.isNotEmpty()) {
                                val bytes = readLimited(zin, name)
                                add("${rec.projectPath}/$rel", bytes, rel.endsWith("gradlew") || rel.endsWith(".sh"))
                            }
                        }
                        val pct = (counting.count * 90 / total).toInt().coerceIn(0, 90)
                        if (pct != last) { last = pct; onProgress(pct) }
                        e = zin.nextEntry
                    }
                }
            }
            if (count == 0) throw ZipRejected("ZIP tidak berisi file yang dapat diunggah.")

            for ((asset, dest) in ASSETS) {
                val bytes = context.assets.open("universal/$asset").use { it.readBytes() }
                add(dest, bytes, false)
            }
            flush()
            onProgress(94)
            val commit = api.createCommit("Universal APK Builder: upload ${rec.projectName}", tree, head)
            api.updateRef(rec.branch, commit)
            onProgress(100)
            commit
        }
}
