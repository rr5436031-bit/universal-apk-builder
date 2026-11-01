package com.universalapk.builder.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.universalapk.builder.build.BuildPipeline

/** Menjalankan pipeline (upload -> dispatch -> monitor) sebagai foreground work agar tetap jalan saat app di background. */
class BuildPipelineWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo = Notifier.foreground(applicationContext, "Memproses build")

    override suspend fun doWork(): Result {
        val id = inputData.getString("id") ?: return Result.failure()
        val uri = inputData.getString("uri") ?: return Result.failure()
        runCatching { setForeground(Notifier.foreground(applicationContext, "Memproses build")) }
        BuildPipeline(applicationContext).run(id, uri, inputData.getString("root").orEmpty())
        return Result.success()
    }
}
