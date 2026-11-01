package com.universalapk.builder.workers

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import com.universalapk.builder.app.MainActivity
import com.universalapk.builder.models.BuildRecord
import com.universalapk.builder.models.BuildStatus

object Notifier {
    private const val CH_MON = "build_monitor"
    private const val CH_RES = "build_result"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_MON, "Pemantauan build", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_RES, "Hasil build", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun foreground(ctx: Context, text: String): ForegroundInfo {
        ensureChannels(ctx)
        val n = NotificationCompat.Builder(ctx, CH_MON)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Universal APK Builder")
            .setContentText(text)
            .setOngoing(true)
            .build()
        return ForegroundInfo(1001, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    @SuppressLint("MissingPermission")
    fun result(ctx: Context, rec: BuildRecord) {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        ensureChannels(ctx)
        val open = PendingIntent.getActivity(
            ctx, rec.id.hashCode(),
            Intent(ctx, MainActivity::class.java).putExtra("record", rec.id)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = when (rec.status) {
            BuildStatus.SUCCESS -> "Build berhasil"
            BuildStatus.CANCELLED -> "Build dibatalkan"
            else -> "Build gagal"
        }
        val n = NotificationCompat.Builder(ctx, CH_RES)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(title)
            .setContentText(rec.projectName)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(rec.id.hashCode(), n)
    }
}
