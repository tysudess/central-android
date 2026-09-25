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

    /**
     * Pode ser chamado no boot, na abertura do app e ao salvar Configurações.
     * UPDATE preserva o trabalho periódico sem criar duplicatas.
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
            enqueue(
                app,
                NEWS,
                "news",
                config.newsInterval.toLong(),
            )
        } else {
            wm.cancelUniqueWork(NEWS)
        }

        if (config.demands) {
            enqueue(
                app,
                DEMANDS,
                "demands",
                config.demandInterval.toLong(),
            )
        } else {
            wm.cancelUniqueWork(DEMANDS)
        }

        if (
            config.videos &&
            config.videoTimes.isNotEmpty()
        ) {
            // O Worker acorda periodicamente e executa apenas o último horário
            // configurado que já venceu e ainda não foi processado no dia.
            enqueue(
                app,
                VIDEOS,
                "videos",
                15L,
            )
        } else {
            wm.cancelUniqueWork(VIDEOS)
        }
    }

    fun schedule(
        context: Context,
        minutes: Long,
    ) {
        val store = AutomationPreferences(context)
        val old = store.load()

        store.save(
            old.copy(
                general = true,
                news = true,
                newsInterval =
                    minutes.toInt()
                        .coerceAtLeast(15),
            ),
        )

        apply(context)
    }

    fun cancel(context: Context) {
        val wm =
            WorkManager.getInstance(
                context.applicationContext,
            )

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
        val request =
            PeriodicWorkRequestBuilder<MonitoringWorker>(
                minutes.coerceAtLeast(15L),
                TimeUnit.MINUTES,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(
                            NetworkType.CONNECTED,
                        )
                        .build(),
                )
                .setInputData(
                    Data.Builder()
                        .putString(
                            "kind",
                            kind,
                        )
                        .build(),
                )
                .addTag(TAG)
                .addTag("$TAG-$kind")
                .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                uniqueName,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
    }
}
