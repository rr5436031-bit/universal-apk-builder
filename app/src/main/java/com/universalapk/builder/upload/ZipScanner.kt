package com.universalapk.builder.upload

import android.content.Context
import android.net.Uri
import com.universalapk.builder.build.ProjectDetector
import com.universalapk.builder.models.ScanResult
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/** Validasi ZIP tanpa mengekstrak ke disk (streaming, hanya menghitung dan membaca berkas konfigurasi kecil). */
object ZipScanner {
    fun scan(context: Context, uri: Uri, zipName: String, zipSize: Long): ScanResult {
        if (zipSize > ZipRules.MAX_ZIP_BYTES)
            throw ZipRejected("Ukuran ZIP ${ZipRules.mb(zipSize)} melebihi batas ${ZipRules.mb(ZipRules.MAX_ZIP_BYTES)}.")
        val paths = ArrayList<String>()
        val snippets = HashMap<String, String>()
        var total = 0L
        var scanned = 0L
        val buf = ByteArray(16384)

        val input = context.contentResolver.openInputStream(uri) ?: throw ZipRejected("File tidak dapat dibuka.")
        input.use { raw ->
            val bin = BufferedInputStream(raw)
            bin.mark(8)
            val magic = ByteArray(4)
            val n = bin.read(magic)
            bin.reset()
            if (n < 4 || magic[0] != 0x50.toByte() || magic[1] != 0x4B.toByte() ||
                magic[2] != 0x03.toByte() || magic[3] != 0x04.toByte()
            ) throw ZipRejected("File bukan ZIP yang valid.")

            ZipInputStream(bin).use { zin ->
                var e = zin.nextEntry
                while (e != null) {
                    val name = ZipRules.cleanName(e.name)
                    if (!e.isDirectory) {
                        val skip = ZipRules.skipped(name)
                        if (!skip) ZipRules.checkBlocked(name)
                        if (!skip && paths.size >= ZipRules.MAX_ENTRIES)
                            throw ZipRejected("Jumlah file melebihi batas ${ZipRules.MAX_ENTRIES}.")
                        val keep = !skip && ZipRules.wanted(name)
                        val bos = if (keep) ByteArrayOutputStream() else null
                        var size = 0L
                        while (true) {
                            val r = zin.read(buf)
                            if (r < 0) break
                            size += r; scanned += r
                            if (scanned > ZipRules.MAX_SCANNED_BYTES) throw ZipRejected("Isi ZIP terlalu besar setelah dekompresi (indikasi zip bomb).")
                            if (!skip) {
                                if (size > ZipRules.MAX_FILE_BYTES)
                                    throw ZipRejected("File '$name' melebihi batas ${ZipRules.mb(ZipRules.MAX_FILE_BYTES)} (GitHub membatasi 100 MB per file).")
                                total += r
                                if (total > ZipRules.MAX_TOTAL_BYTES) throw ZipRejected("Total isi project melebihi ${ZipRules.mb(ZipRules.MAX_TOTAL_BYTES)}.")
                                if (bos != null && size <= 200_000) bos.write(buf, 0, r)
                            }
                        }
                        if (!skip) {
                            paths.add(name)
                            if (bos != null && size <= 200_000) snippets[name] = bos.toString("UTF-8")
                        }
                    }
                    e = zin.nextEntry
                }
            }
        }
        if (paths.isEmpty()) throw ZipRejected("ZIP tidak berisi file yang dapat diunggah.")

        val root = ZipRules.detectRoot(paths)
        val stripped = paths.map { it.removePrefix(root) }
        val strippedSnippets = snippets.mapKeys { it.key.removePrefix(root) }
        val det = ProjectDetector.detect(stripped, strippedSnippets)
        val rawName = root.trimEnd('/').ifBlank { zipName.substringBeforeLast('.') }
        val name = rawName.replace(Regex("[^A-Za-z0-9 _.-]"), "_").trim().ifBlank { "project" }
        return ScanResult(paths.size, total, root, det.framework, det.reason, name)
    }
}
