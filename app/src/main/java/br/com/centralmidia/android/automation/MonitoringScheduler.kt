package br.com.centralmidia.android.automation

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import br.com.centralmidia.android.core.AutomationPreferences
import java.util.concurrent.TimeUnit

object MonitoringScheduler {
    const val TAG = "central-monitoring"

    private const val NEWS = "central-monitoring-news"
    private const val DEMANDS = "central-monitoring-demands"
    private const val VIDEOS = "central-monitoring-videos"

    private const val NEWS_ONCE = "$NEWS-immediate"
    private const val DEMANDS_ONCE = "$DEMANDS-immediate"
    private const val VIDEOS_ONCE = "$VIDEOS-immediate"

    /**
     * Mantém tarefas periódicas persistentes e dispara uma execução inicial
     * quando o processo volta, após o boot ou ao aplicar configurações. A
     * execução inicial evita esperar todo o primeiro intervalo para começar.
     */
    fun apply(context: Context) {
        val app = context.applicationContext
        val config = AutomationPreferences(app).load()
        val wm = WorkManager.getInstance(app)

        if (!config.general) {
            cancel(app)
            return
        }

        if (config.news) {
            enqueue(app, NEWS, "news", config.newsInterval.toLong())
            enqueueImmediate(app, NEWS_ONCE, "news")
        } else {
            wm.cancelUniqueWork(NEWS)
            wm.cancelUniqueWork(NEWS_ONCE)
        }

        if (config.demands) {
            enqueue(app, DEMANDS, "demands", config.demandInterval.toLong())
            enqueueImmediate(app, DEMANDS_ONCE, "demands")
        } else {
            wm.cancelUniqueWork(DEMANDS)
            wm.cancelUniqueWork(DEMANDS_ONCE)
        }

        if (config.videos && config.videoTimes.isNotEmpty()) {
            // O Worker verifica o horário vencido mais recente e evita repetir
            // um horário que já tenha sido concluído no dia corrente.
            enqueue(app, VIDEOS, "videos", 15L)
            enqueueImmediate(app, VIDEOS_ONCE, "videos")
        } else {
            wm.cancelUniqueWork(VIDEOS)
            wm.cancelUniqueWork(VIDEOS_ONCE)
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
        val wm = WorkManager.getInstance(context.applicationContext)
        wm.cancelUniqueWork(NEWS)
        wm.cancelUniqueWork(DEMANDS)
        wm.cancelUniqueWork(VIDEOS)
        wm.cancelUniqueWork(NEWS_ONCE)
        wm.cancelUniqueWork(DEMANDS_ONCE)
        wm.cancelUniqueWork(VIDEOS_ONCE)
        wm.cancelAllWorkByTag(TAG)
    }

    private fun enqueue(
        context: Context,
        uniqueName: String,
        kind: String,
        minutes: Long,
    ) {
        val request =
            PeriodicWorkRequestBuilder<MonitoringWorker>(
                minutes.coerceAtLeast(15L),
                TimeUnit.MINUTES,
            )
                .setConstraints(networkConstraints())
                .setInputData(
                    Data.Builder()
                        .putString("kind", kind)
                        .build(),
                )
                .addTag(TAG)
                .addTag("$TAG-$kind")
                .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            uniqueName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun enqueueImmediate(
        context: Context,
        uniqueName: String,
        kind: String,
    ) {
        val request =
            OneTimeWorkRequestBuilder<MonitoringWorker>()
                .setConstraints(networkConstraints())
                .setInputData(
                    Data.Builder()
                        .putString("kind", kind)
                        .build(),
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30L,
                    TimeUnit.SECONDS,
                )
                .addTag(TAG)
                .addTag("$TAG-$kind")
                .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueName,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun networkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
}
