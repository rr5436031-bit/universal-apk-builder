package com.universalapk.builder.build

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.universalapk.builder.app.Graph
import com.universalapk.builder.github.ArtifactDownloader
import com.universalapk.builder.models.BuildRecord
import com.universalapk.builder.storage.Exporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ApkActions {
    private const val MIME = "application/vnd.android.package-archive"

    private suspend fun fetch(ctx: Context, rec: BuildRecord, onProgress: (Int) -> Unit): File =
        ArtifactDownloader(ctx, Graph.api(rec.repo)).apkFile(rec, onProgress)

    suspend fun download(ctx: Context, rec: BuildRecord, onProgress: (Int) -> Unit): String {
        val f = fetch(ctx, rec, onProgress)
        withContext(Dispatchers.IO) {
            Exporter.saveToDownloads(ctx, f.name, MIME) { out -> f.inputStream().use { it.copyTo(out) } }
        }
        return "APK tersimpan di Download/UniversalAPK/${f.name}"
    }

    suspend fun share(ctx: Context, rec: BuildRecord, onProgress: (Int) -> Unit) {
        val f = fetch(ctx, rec, onProgress)
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "Bagikan APK").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
