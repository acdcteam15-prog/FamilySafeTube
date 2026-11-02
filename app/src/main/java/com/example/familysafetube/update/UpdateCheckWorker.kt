package com.example.familysafetube.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.familysafetube.AppGraph
import com.example.familysafetube.MainActivity
import com.example.familysafetube.R
import java.util.concurrent.TimeUnit

/** Twice a day (on any network): looks for a new build and posts one notification per build. */
class UpdateCheckWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val updates = AppGraph.updates
        if (!updates.autoCheck) return Result.success()
        val state = updates.check()
        val info = (state as? UpdateState.Available)?.info ?: (state as? UpdateState.Ready)?.info
        if (info != null) {
            // Unmetered network only: fetch the new build now so installing it later is one tap.
            var ready = state is UpdateState.Ready
            if (UpdatePolicy.shouldAutoDownload(updates.autoDownload, updates.canInstall(), isMetered(), ready)) {
                ready = updates.download(info) is UpdateState.Ready
            }
            if (UpdatePolicy.shouldNotify(info.build, updates.skippedBuild.value, updates.notifiedBuild)) {
                notify(info, ready)
                updates.notifiedBuild = info.build
            }
        }
        // A failed check (offline, rate limit) is not worth retrying: the next run is in 12 hours.
        return Result.success()
    }

    private fun isMetered(): Boolean =
        appContext.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered != false

    private fun notify(info: ReleaseInfo, ready: Boolean) {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted || !NotificationManagerCompat.from(appContext).areNotificationsEnabled()) return

        val manager = appContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, appContext.getString(R.string.update_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
        )
        // Tapping opens the app, which then asks for the parental PIN before showing the update screen.
        val open = Intent(appContext, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_UPDATES, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(appContext, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val app = appContext.getString(R.string.app_name)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setLargeIcon(BitmapFactory.decodeResource(appContext.resources, R.drawable.logo))
            .setContentTitle(
                appContext.getString(if (ready) R.string.update_notif_ready_title else R.string.update_notif_available_title, app, info.versionName)
            )
            .setContentText(
                appContext.getString(
                    when {
                        ready -> R.string.update_notif_ready_text
                        info.required -> R.string.update_notif_required_text
                        else -> R.string.update_notif_text
                    }
                )
            )
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "app_updates"
        private const val NOTIFICATION_ID = 7001
        private const val WORK_NAME = "update-check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(UpdatePolicy.BACKGROUND_CHECK_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
