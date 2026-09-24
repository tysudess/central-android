package br.com.centralmidia.android.automation

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import br.com.centralmidia.android.core.AutomationPreferences
import java.util.concurrent.TimeUnit

object MonitoringScheduler {
    const val TAG = "central-monitoring"
    private const val NEWS = "central-monitoring-news"
    private const val DEMANDS = "central-monitoring-demands"
    private const val VIDEOS = "central-monitoring-videos"

    fun apply(context: Context) {
        val config = AutomationPreferences(context).load()
        val wm = WorkManager.getInstance(context)

        wm.cancelUniqueWork(NEWS)
        wm.cancelUniqueWork(DEMANDS)
        wm.cancelUniqueWork(VIDEOS)

        if (!config.general) return

        if (config.news) {
            enqueue(context, NEWS, "news", config.newsInterval.toLong())
        }
        if (config.demands) {
            enqueue(context, DEMANDS, "demands", config.demandInterval.toLong())
        }
        if (config.videos && config.videoTimes.isNotEmpty()) {
            enqueue(context, VIDEOS, "videos", 15L)
        }
    }

    fun schedule(context: Context, minutes: Long) {
        val store = AutomationPreferences(context)
        val old = store.load()
        store.save(
            old.copy(
                general = true,
                news = true,
                newsInterval = minutes.toInt().coerceAtLeast(15),
            ),
        )
        apply(context)
    }

    fun cancel(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(NEWS)
        wm.cancelUniqueWork(DEMANDS)
        wm.cancelUniqueWork(VIDEOS)
        wm.cancelAllWorkByTag(TAG)
    }

    private fun enqueue(
        context: Context,
        uniqueName: String,
        kind: String,
        minutes: Long,
    ) {
        val request = PeriodicWorkRequestBuilder<MonitoringWorker>(
            minutes.coerceAtLeast(15L),
            TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInputData(
                Data.Builder()
                    .putString("kind", kind)
                    .build(),
            )
            .addTag(TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            uniqueName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
