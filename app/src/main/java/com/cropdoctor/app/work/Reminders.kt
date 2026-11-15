package com.cropdoctor.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cropdoctor.app.MainActivity
import com.cropdoctor.app.R
import java.util.concurrent.TimeUnit

object Reminders {
    const val CHANNEL = "followup"
    const val EXTRA_SCAN = "scanId"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Re-check reminders", NotificationManager.IMPORTANCE_DEFAULT)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    /** Schedules a local notification asking the farmer to re-scan. Works offline, survives reboot (WorkManager). */
    fun schedule(context: Context, scanId: String, delayMillis: Long, title: String, text: String) {
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString("scanId", scanId).putString("title", title).putString("text", text).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("reminder_$scanId", ExistingWorkPolicy.REPLACE, req)
    }

    fun cancel(context: Context, scanId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("reminder_$scanId")
    }
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()
        val scanId = inputData.getString("scanId") ?: return Result.success()
        val open = PendingIntent.getActivity(
            ctx, scanId.hashCode(),
            Intent(ctx, MainActivity::class.java).putExtra(Reminders.EXTRA_SCAN, scanId).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, Reminders.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(inputData.getString("title"))
            .setContentText(inputData.getString("text"))
            .setStyle(NotificationCompat.BigTextStyle().bigText(inputData.getString("text")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(scanId.hashCode(), n)
        return Result.success()
    }
}
